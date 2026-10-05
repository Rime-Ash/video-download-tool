# Security and Compliance

## URL validation

Only HTTP and HTTPS are accepted. Short-link redirects are resolved before the final host is checked against the exact allowlist. Host suffix checks must not allow lookalike domains such as `evil-bilibili.com`.

## File safety

User-controlled names are sanitized for Windows-invalid characters, control characters, reserved device names, trailing spaces, and trailing periods. The normalized output path must remain below the configured download directory.

## Credentials and cookies

Cookies are used only for the user's existing browser-equivalent access. Cookie paths and command arguments are never logged. The application does not upload cookies or copy them to temporary directories.

Two sources are supported:

- A Netscape-format cookie file chosen in the settings page, referenced in place with `--cookies`.
- A local browser profile read by yt-dlp with `--cookies-from-browser <browser>`. This is preferred when a
  platform (for example Douyin) requires fresh cookies even for public videos. Chromium based browsers lock
  their cookie database while running, so the browser must be fully closed for the read to succeed.
  Chromium 127 and later additionally protect cookies with app-bound encryption (the `Local State` file
  contains `app_bound_encrypted_key`), which yt-dlp cannot decrypt; the application recognises both failure
  modes and tells the user to export a cookie file or to use Firefox instead.

Only the browser name and an optional profile are passed; `CommandBuilder` validates the value and never
returns raw cookie data to the caller.

## Content restrictions

The application does not implement DRM bypass, authentication bypass, paywall bypass, watermark removal, copyright metadata removal, playlist traversal, account crawling, or batch collection.

## Image posts (图文/图集)

- Only the single post whose link was pasted is read; account pages, playlists and bulk collection remain out
  of scope, exactly as for videos.
- The image list is requested through the platform web API with the user's own exported cookies. When the
  platform answers with a captcha interstitial the request is retried a few times with a short delay and is
  never solved or bypassed; if it keeps failing the user is told to verify in the browser.
- Every image URL must point at a known media CDN (`douyinpic.com`, `byteimg.com`, `amemv.com`, `365yg.com`,
  ...). URLs from any other host are dropped during parsing and rejected again before the download starts, so
  a crafted response cannot make the application fetch an arbitrary address.
- Images are written through a temporary `.part` file inside the target folder, existing files are skipped
  (resume behaviour), and single images larger than 60 MB are refused.
- Live photo motion videos (动图) are handled by the same rules: the play address must be on the media CDN
  allowlist (`douyinvod.com` included), and the video is fetched only for the post the user pasted.

## Douyin video fallback (浏览器兜底)

- The fallback exists because Douyin answers unsigned API requests with HTTP 403. It renders the public
  video page once in a local headless Edge/Chrome and reads the playable addresses the page already contains.
- No signature is calculated, no verification challenge is solved and no access restriction is bypassed: the
  fallback reads exactly the page a user can open in their own browser, for the single link they pasted.
- The browser always runs with a throw-away profile directory (`--user-data-dir` inside the system temporary
  directory) that is deleted afterwards. The user's browser profile, history and cookie database are never
  read, copied or uploaded; the optional cookie file is not passed to the browser at all.
- The dumped markup is untrusted input. Only media URLs on the CDN allowlist are kept, and the same allowlist
  is checked again before the download starts, so a manipulated page cannot redirect the download to another
  host.
- The media file is written through a temporary `.part` file inside the configured download directory,
  existing files are skipped (resume behaviour), and one file is limited to 8 GB.
- Only one video is resolved per link; account pages, playlists and bulk collection remain out of scope.

## Local data and repository hygiene

- The application writes configuration to `%APPDATA%/NZSK/VideoDownloader/config.json` (download directory, tool
  paths and an optional cookie file path) and rotating logs to `%LOCALAPPDATA%/NZSK/VideoDownloader/logs/`.
  Both locations are outside the project tree.
- Logs record only generic messages and exception class names. URLs, video titles, cookie paths, proxy
  credentials and command lines are never logged; `DefaultProcessExecutor` drains yt-dlp output without
  logging it, and `CommandBuilder` never returns cookie values to callers.
- The repository itself contains no personal data: no account names, host names, absolute user paths, video or
  account ids, or exported cookies. Generated output, caches, logs, cookie exports and shortcuts are excluded
  by `.gitignore`.
- Third-party executables under `tools/` are excluded from version control; only their provenance record in
  `tools/README.md` is tracked.

## Repository hygiene review (2026-10-04)

The full source tree, documentation, scripts, configuration resources and the packaged application image were
re-checked for personal data:

```text
user name / computer name        not found
absolute local paths             not found (only generic placeholders and standard install locations)
real account or video ids        not found (test fixtures use synthetic values)
exported cookies / tokens        not found inside the project tree
config.json / logs inside tree   not found
```

Verification is reproducible: run `utils/verify-project.ps1`, which fails when a forbidden file, a local user
path or the current user/computer name appears anywhere in the tree. The authoritative rules are in
`doc/repository-standard.md`, and third-party license obligations are in `THIRD-PARTY-NOTICES.md`.

Note: the user's own cookie export, configuration and logs live outside the project tree
(`%APPDATA%` / `%LOCALAPPDATA%`). They are never part of a source snapshot or the application image, but they
must not be copied into a release package manually either.
