# Third-party notices

This product bundles the third-party components listed below. Each component remains the property of its
respective owner and is used under its own license. The exact versions recorded here are the ones shipped in
the packaged application image.

The application's own source code is licensed under the MIT license (see `LICENSE`). That does not change the
license of any bundled component: each entry below keeps its own terms.

## Application libraries

| Component | Version | License | Notes |
| --- | --- | --- | --- |
| JavaFX | 21 | GPL-2.0 with Classpath Exception | Graphics, controls and FXML modules (`*-win.jar`) |
| Eclipse Temurin OpenJDK runtime | 21.0.12.1 | GPL-2.0 with Classpath Exception | Reduced runtime produced by `jlink`; license texts ship in `runtime/legal/` |
| Jackson (core, databind, annotations) | 2.17.3 | Apache-2.0 | JSON parsing for platform metadata |
| SLF4J API | 2.0.16 | MIT | Logging facade |
| Logback (classic, core) | 1.5.15 | EPL-1.0 or LGPL-2.1 (dual licensed) | Local log files |

## Bundled executables

| Component | Version | License | Recorded provenance |
| --- | --- | --- | --- |
| yt-dlp.exe | 2026.08.19 | Unlicense (public domain) | `tools/README.md` (SHA-256 recorded) |
| ffmpeg.exe | n8.1.3 win64 GPL build | GPL-3.0 | `tools/README.md` (SHA-256 recorded) |

The executables are not stored in version control. They are copied into the application image during
packaging only when present under `tools/` with a provenance record.

## FFmpeg licensing obligation

The bundled FFmpeg build was configured with `--enable-gpl --enable-version3` and is therefore distributed
under **GPL-3.0**, independently of the MIT license that covers this application. The application starts
FFmpeg as a separate process and does not link against it, which is normally treated as mere aggregation, but
redistribution of the bundled binary still requires that the receiver can obtain the corresponding FFmpeg
source and the full GPL-3.0 license text.

Two compliant options exist:

1. Ship the GPL-3.0 license text with the application image and provide the corresponding FFmpeg source
   (or a written offer for it), or
2. Replace the GPL build with an LGPL build of FFmpeg, which removes the source-offer obligation.

This decision is tracked as an open item in `doc/repository-standard.md`.

## Java runtime

The reduced runtime created by `jlink` already contains the OpenJDK license texts under
`<application>/runtime/legal/`. Keep that directory intact when redistributing the application image.
