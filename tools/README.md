# Third-party tools

Do not commit third-party executable files until their source, exact version, checksum, and license have been
recorded here.

## Recorded releases

```text
Name:            yt-dlp.exe
Version:         2026.08.19
Source URL:      https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe
SHA-256:         66674953FE251B89F4D08C5F0E35E0728679BD67AB3D7D05C0562AF101DD3E7A
License:         Unlicense (public domain)
Acquisition date: 2026-09-27
```

```text
Name:            ffmpeg.exe
Version:         n8.1.3-2-g45e8e0a3ff-20260927 (FFmpeg 8.1.3 branch, win64 GPL build)
Source URL:      https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-09-27-13-04/ffmpeg-n8.1.3-2-g45e8e0a3ff-win64-gpl-8.1.zip
SHA-256 (exe):   70E41E6EC467070D8A156D0B6E9E9DBD716B3025D6C9019C9D776A9B20F60A6D
SHA-256 (zip):   16C5956A513EC236ED8B5786920662AB5A4A94E4EC553A8AEBCF161439EF5416
License:         GPL v3 (build configured with --enable-gpl --enable-version3)
Acquisition date: 2026-09-27
```

BtbN FFmpeg-Builds is one of the Windows binary providers linked from the official FFmpeg download page.
Only `bin/ffmpeg.exe` is kept in this directory; `ffprobe.exe` and `ffplay.exe` are not required by the
application.

Required release record template for additional binaries:

```text
Name:
Version:
Source URL:
SHA-256:
License:
Acquisition date:
```

Expected files for local development:

```text
tools/yt-dlp.exe
tools/ffmpeg.exe
```

The application looks for these files next to the program first and in the repository `tools` directory as a
fallback. Both paths can be overridden in the settings page; the chosen paths are stored in the user
configuration file. Never commit binaries of unknown origin.
