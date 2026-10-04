# Video Downloader

Windows Java 21 / JavaFX 21 desktop application for downloading publicly available videos that the user is legally authorized to save.

## Scope and compliance

The application is restricted to the following final host domains:

- `bilibili.com`
- `b23.tv`
- `douyin.com`
- `v.douyin.com`
- `iesdouyin.com`

The application must not bypass DRM, authentication, access controls, paywalls, watermarks, copyright metadata, or platform security mechanisms. Playlist traversal, batch collection, account crawling, and automated bulk downloads are out of scope.

The compliance statement is part of the application itself: it is shown permanently in the settings tab,
below the environment check, and no separate startup confirmation dialog is used.

## Technology

- Java 21
- JavaFX 21
- Gradle
- yt-dlp for metadata inspection and downloads
- FFmpeg for media stream merging when required
- Jackson for JSON parsing
- SLF4J and Logback for logging
- JUnit 5 and Mockito for tests
- `jpackage` for Windows packaging

## Project layout

```text
README.md               Entry point: scope, build, run and layout
CHANGELOG.md            Release history
LICENSE                 MIT license (Copyright (c) 2026 3447347190)
THIRD-PARTY-NOTICES.md  Versions and licenses of bundled third-party components
doc/                    Architecture, security, packaging and file-standard documents
prototype/              Product prototype material
project/frontend/       JavaFX application module
project/backend/        Models, services, engines, utilities, exceptions
utils/                  Build, verification and file-standard scripts
tools/                  Local yt-dlp.exe / ffmpeg.exe (never committed; provenance in tools/README.md)
packaging/              Windows packaging documentation and scripts
```

The canonical rules for what belongs where, file naming, generated output and privacy checks are documented
in [doc/repository-standard.md](doc/repository-standard.md). Run `utils\verify-project.ps1` to check the tree
against them.

## Implemented features

- Compliance statement shown in the settings tab below the environment check, not as a startup dialog.
- Link inspection (title, uploader, duration, thumbnail, available formats) with an allowlist enforced on the
  initial host and on the final host after redirects.
- Format selection with an automatic `bestvideo+bestaudio` strategy for video-only streams, and a merge step
  performed by FFmpeg through `--ffmpeg-location`.
- Download queue with per-task progress, speed, remaining time, state and error message, plus start, pause,
  resume, cancel, retry (three attempts with exponential backoff) and clear-completed actions.
- Image post (图文/图集) support: a `/note/` link is inspected through the platform web API and every image of
  that single post is saved into its own folder (`<下载目录>/<标题>/01.jpg`, `02.jpg`, ...). Existing files are
  skipped, so a paused or repeated task resumes instead of downloading again.
- Live photos (动图/实况) are saved together with their motion part: a live photo produces both `01.jpg` and
  `01.mp4` (H.264/AAC, usually 720p, a few seconds). A post may contain several live photos; each one is
  downloaded, while still images produce only the picture file.
- Completion actions: open the file, open its directory and copy its path. The finished file is located by
  scanning the download directory for the unique base name, because the extension is decided by yt-dlp.
- Settings for download directory, concurrency limit, rate limit, HTTP/HTTPS proxy, cookie file, default
  format strategy, yt-dlp path and FFmpeg path, persisted as JSON.
- Environment detection for Java, yt-dlp, FFmpeg, the download directory and free disk space, with actionable
  warnings when something is missing.
- Logging to `%LOCALAPPDATA%/NZSK/VideoDownloader/logs/` with rolling files; cookie values and full command
  lines are never logged.

See [doc/status.md](doc/status.md) for the current stage-by-stage progress report.

Privacy handling (what is stored locally, what is never logged, and the repository hygiene rules) is
documented in [doc/security.md](doc/security.md).

## Ports and database

This is a local JavaFX desktop application.

```text
Frontend service port: not applicable
Backend service port: not applicable
Database: not used
Database account: not applicable
Database password: not applicable
```

There are no HTTP frontend/backend services and no database connection settings.

## Runtime paths

- Bundled tools: `<application-directory>/tools/yt-dlp.exe` and `<application-directory>/tools/ffmpeg.exe`
- User configuration: `%APPDATA%/NZSK/VideoDownloader/config.json`
- User logs: `%LOCALAPPDATA%/NZSK/VideoDownloader/logs/`
- Default download directory: `%USERPROFILE%/Downloads/VideoDownloader/`
- Cookie files: referenced in place only; never copied to temporary storage or written to logs

## Build and test

Use a JDK 21 installation and run:

```text
gradlew.bat test
gradlew.bat build
gradlew.bat :project:frontend:run
```

Unit tests use Mockito with the subclass mock maker, selected in
`project/backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`, because the inline mock
maker requires a JVM agent that is not available in every build environment.

## How to run

Packaged application image (no Java installation required on the target machine):

```text
packaging\windows\output\VideoDownloader\VideoDownloader.exe
```

Development run from the repository (uses the JDK 21 toolchain and the JavaFX plugin):

```text
gradlew.bat :project:frontend:run
```

First-run steps:

1. Review the environment report. `tools/yt-dlp.exe` and `tools/ffmpeg.exe` ship with the image, so both should
   be reported as ready; the settings tab can point at other copies if needed.
2. Paste a `bilibili.com`, `b23.tv` or `douyin.com` link and press "解析视频".
3. Pick a format (video-only formats are automatically combined with the best audio stream) and press
   "加入下载队列".
4. Watch progress in the queue; when a task completes, use "打开文件", "打开所在目录" or "复制文件路径".

The compliance statement is always readable in the settings tab, below the environment check.

Cookies are needed by some platforms (Douyin requires fresh cookies even for public videos). Choose one of:

- "从浏览器读取 Cookie" in the settings tab: yt-dlp reads your local browser profile
  (`--cookies-from-browser chrome|edge|firefox|...`). The browser must be fully closed so its cookie database
  is not locked. Edge and Chrome 127+ protect cookies with app-bound encryption, which yt-dlp cannot decrypt;
  for those browsers export a cookie file instead (the application detects this case and explains it).
- A Netscape-format cookie file exported from your own browser, referenced in place with `--cookies`.

Douyin profile links (`/user/...`) are either rewritten to the single video they point at (when the link
carries `modal_id` or `vid`) or refused with an explanation; account pages and playlists stay out of scope.

Image posts need cookies as well, because the platform web API answers an anonymous request with a captcha
page. Configure the exported cookie file (browser based reading does not work for Edge and Chrome 127+ because
of app-bound encryption) and keep "从浏览器读取 Cookie" set to "(不使用)".

## Application icon

The icon is generated from a single source image. The optional design master is `tubiao.png` in the project
root; when it is absent the script falls back to the already generated `icon.png`, so the repository never
depends on an untracked master file:

```text
utils\make-icon.ps1                   # regenerates both files below
gradlew.bat :project:frontend:run     # development run picks up icon.png automatically
```

```text
project/frontend/src/main/resources/icon.png   window and taskbar icon (256x256 PNG)
packaging/windows/icon.ico                     executable icon (16/24/32/48/64/128/256 multi-size ICO)
```

Replace `tubiao.png` (or pass `-SourcePng <path>`) and re-run `utils\make-icon.ps1`, then rebuild the
application image so the executable picks up the new icon.

The repository intentionally does not contain third-party executable binaries. Obtain yt-dlp and FFmpeg from their official project releases, record exact versions and licenses in `tools/README.md`, and then place the files under `tools/` for local packaging.

## Development stages

1. Project skeleton, architecture, interfaces, and immutable models.
2. Security utilities, process execution, configuration, parsing, progress, retry, and queue services with tests.
3. JavaFX UI, environment checks, yt-dlp/FFmpeg integration, logging, and packaging.
4. Full compile, unit, concurrency, cancellation, path-security, failure-mode, and Windows packaging verification.
