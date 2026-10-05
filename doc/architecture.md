# Architecture

## Dependency direction

```text
frontend UI -> service interfaces -> engine/util implementations
backend model and interfaces do not depend on JavaFX
```

The JavaFX layer handles presentation and user interaction only. It must not call `ProcessBuilder`, `Files`, `HttpClient`, yt-dlp, or FFmpeg directly.

## Packages

```text
com.nzsk.videodownloader
├── engine
├── exception
├── model
├── service
└── util
```

The frontend module uses `com.nzsk.videodownloader.ui`.

The UI module contains the views plus a single composition root, `ui.AppContext`. The views only call the
context and backend interfaces; no view creates a process, writes a file or builds a yt-dlp command.

## Component overview

```text
VideoDownloaderApplication (JavaFX entry point)
└── AppContext (composition root: config, validator, clients, queue, file service)
    ├── MainView
    │   ├── DownloadPane  (inspect, format selection, queue table, completion actions)
    │   └── SettingsPane  (configuration form, environment report)
    ├── DefaultUrlValidator + HttpRedirectResolver
    ├── DefaultYtDlpClient -> CommandBuilder / ProgressParser / DownloadOutputParser
    ├── DefaultImagePostClient -> DouyinImagePostParser / NetscapeCookieStore
    ├── DefaultDouyinVideoClient -> DouyinVideoPageParser / BrowserLocator
    ├── DownloadQueue (concurrency gate, retry, pause/resume/cancel)
    ├── JsonConfigManager (AppConfig persistence)
    └── DefaultLocalFileService (open file or directory with the desktop shell)
```

## Threading

Parsing and downloads execute in JavaFX `Task` or `Service` instances. UI updates use property binding or `Platform.runLater`. The JavaFX Application Thread must never wait for an external process or filesystem operation.

The queue keeps its own worker threads; the views poll a snapshot of the task list on a JavaFX `Timeline`
so that no long-running work happens on the application thread.

## Download jobs

`DownloadQueue` stores a `DownloadJob` per entry, so videos and image posts share queueing, progress,
pause, cancel and retry:

```text
DownloadJob
├── VideoDownloadJob       -> YtDlpClient (yt-dlp process)
├── ImagePostDownloadJob   -> ImagePostClient (HTTP images of one post)
└── DouyinVideoDownloadJob -> DouyinVideoClient (browser fallback, HTTP media file)
```

`DownloadTask` therefore carries a kind, a display name, a one line summary (format selector or "图集 N 张"),
the state, progress and the produced output path.

Image posts are inspected through the platform web detail API by `DefaultImagePostClient`, parsed by
`DouyinImagePostParser` and downloaded one by one. `NetscapeCookieStore` builds the Cookie header for the
platform host only; values are never logged.

Douyin rejects plain API requests that its own JavaScript has not signed, which is why yt-dlp reports that
fresh cookies are needed. When a Douyin video fails there, `DefaultDouyinVideoClient` renders the public page
once with a local headless Edge/Chrome (throw-away profile directory), `DouyinVideoPageParser` reads the
playable addresses out of the returned markup, and `DouyinVideoDownloadJob` downloads the chosen address over
HTTP. No signature is computed and no verification challenge is solved; the fallback only reads the page a
user could open themselves. The parser treats the markup as untrusted input and keeps only URLs on the media
CDN allowlist.

yt-dlp JSON is parsed by `YtDlpJsonParser` into immutable `VideoInfo` and `FormatInfo` records. The parser has no network or process responsibilities.

Download progress is parsed from the structured `--progress-template` output by `ProgressParser`. The final
media file is located by `DownloadedFileLocator`, which scans the download directory for the unique base name
(the extension is decided by yt-dlp and can change when streams are merged). The resolved path is stored on
the finished `DownloadTask` so the UI can open the file, open its directory or copy the path.

## Process execution

External commands are represented as `List<String>` and passed to `ProcessBuilder`. Shell wrappers such as `cmd /c` and `powershell -Command` are prohibited. Both stdout and stderr are consumed continuously. `ProcessOutputListener` exposes lines to the owning engine when JSON or progress parsing is required; the default executor drains output without logging its content.

`CommandBuilder` sanitizes the base file name, keeps the resolved output path inside the configured download
directory, and appends only validated configuration values (`--limit-rate`, `--proxy`, `--cookies`,
`--ffmpeg-location`).

## Configuration and paths

`AppPaths` resolves bundled tools, the configuration file, the log directory and the default download
directory without hard-coding machine specific paths. A tool path from the configuration is kept while that
file exists and otherwise replaced by the tool discovered next to the running application, so a removed or
renamed installation directory does not leave the application without yt-dlp or FFmpeg. `LogManager` creates
the log directory and hands out loggers; log files never contain cookie values or full command lines.
