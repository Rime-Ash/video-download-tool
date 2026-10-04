# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows semantic versioning.

## [Unreleased]

### Changed

- The compliance statement is no longer a startup confirmation dialog. It is shown permanently in the
  settings tab, below the environment check, and the application opens the main window directly.
- License changed from a proprietary placeholder to the MIT license (`Copyright (c) 2026 3447347190`), matching
  the convention of the account's other public repositories.

### Added

- Repository file standard (`doc/repository-standard.md`) covering layout, naming, generated output and
  privacy rules.
- `THIRD-PARTY-NOTICES.md` with component versions, licenses and the FFmpeg GPL obligation.
- `CHANGELOG.md`, `.gitattributes` and `.editorconfig`.
- `utils/verify-project.ps1` now verifies the standard layout and rejects privacy hazards such as committed
  configuration, logs, cookie exports and local absolute paths.
- Windows installer build: `packaging/windows/installer.iss` plus `utils/build-installer.ps1`, producing a
  single-file Inno Setup installer with a destination-directory page, an optional desktop shortcut and a
  registered uninstaller.

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
