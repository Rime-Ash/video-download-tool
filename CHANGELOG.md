# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows semantic versioning.

## [Unreleased]

## [0.1.1] - 2026-10-05

### Changed

- The compliance statement is no longer a startup confirmation dialog. It is shown permanently in the
  settings tab, below the environment check, and the application opens the main window directly.
- Image and video media URLs now share one CDN allowlist (`MediaCdnHosts`); `ImageCdnHosts` delegates to it,
  so the image post path keeps its previous behaviour.
- The application image now keeps a single copy of yt-dlp and FFmpeg, in `<application>/tools` next to the
  launcher. The redundant `app/tools` copy was removed, cutting the installed footprint from about 439 MB to
  about 265 MB and the installer from about 150 MB to about 90 MB.
- License changed from a proprietary placeholder to the MIT license (`Copyright (c) 2026 3447347190`), matching
  the convention of the account's other public repositories.

### Added

- Douyin browser fallback for videos: when yt-dlp cannot read a Douyin link (Douyin answers unsigned API
  requests with HTTP 403), the public page is rendered once in a local headless Edge/Chrome and the playable
  addresses of that page are downloaded directly. New `DouyinVideoClient` / `DefaultDouyinVideoClient`,
  `DouyinVideoPageParser`, `DouyinVideoDownloadJob`, `BrowserLocator` and the `DOUYIN_VIDEO` queue kind; the
  download page shows the resolved sources and queues them like any other video.
- Repository file standard (`doc/repository-standard.md`) covering layout, naming, generated output and
  privacy rules.
- `THIRD-PARTY-NOTICES.md` with component versions, licenses and the FFmpeg GPL obligation.
- `CHANGELOG.md`, `.gitattributes` and `.editorconfig`.
- `utils/verify-project.ps1` now verifies the standard layout and rejects privacy hazards such as committed
  configuration, logs, cookie exports and local absolute paths.
- Windows installer build: `packaging/windows/installer.iss` plus `utils/build-installer.ps1`, producing a
  single-file Inno Setup installer with a destination-directory page, an optional desktop shortcut and a
  registered uninstaller.

### Fixed

- A media title longer than the file-name limit was cut at a fixed offset, which could split a surrogate pair
  (an emoji) and produce a file name Windows cannot encode. Truncation now stops at a code point boundary and
  falls back to `download` when nothing usable remains.
- A tool path stored in the user configuration pointed at an installation directory that a later reinstall or
  uninstall had removed, so the application reported both tools as missing while a usable copy shipped next to
  it. A configured path is still used while the file exists, and is otherwise replaced by the tool resolved
  next to the running application.
- Download progress stayed at 0% until completion. `--progress-template` reads the text before the first
  colon (`download:`) as the template type selector and never prints it, so `ProgressParser` never matched a
  line. The template now carries a `vd-progress:` marker inside the template text and uses yt-dlp's
  human readable `_str` values, so the queue shows the transferred size, speed and remaining time instead of
  raw byte counts; unknown `NA`/`N/A` fields are left empty.

### Security

- Re-verified the source tree, documentation, scripts and packaged image for personal data (user name, host
  name, local absolute paths, account identifiers, exported cookies). None were found.

### Removed

- Vestigial placeholder directories `database/` (the application uses no database) and the empty root
  `downloads/` directory. The related checks in `utils/verify-project.ps1` were removed with them.

## [0.1.0] - 2026-09-27

### Added

- JavaFX desktop application for downloading publicly available, legally savable videos from the allowlisted
  Bilibili and Douyin hosts, with a download queue, per-task progress and retry.
- yt-dlp based inspection and download, FFmpeg based stream merging, URL allowlist validation, file name
  sanitization and path containment checks.
- Image post (图文/图集) support.
- Settings, environment detection, rotating logs and a Windows application image built with `jpackage`.
