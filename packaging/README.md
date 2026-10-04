# Packaging

Windows packaging uses `jpackage` and a Java 21 runtime image. See `doc/packaging.md` for the required layout and licensing record.

Build from a Windows PowerShell session with Java 21 configured:

```powershell
$env:JAVA_HOME = '<path to a JDK 21 installation>'   # 例如 Eclipse Temurin 21
.\utils\build-windows.ps1
```

When `JAVA_HOME` is not set, the script locates a JDK 21 installation through `PATH` and the common Eclipse
Temurin / Microsoft OpenJDK directories. No machine-specific path is hard-coded.

The script creates an application image under `packaging/windows/output/VideoDownloader`. It copies `tools/yt-dlp.exe` and `tools/ffmpeg.exe` only when those files exist; unknown or unrecorded binaries are never downloaded automatically.

`LICENSE` and `THIRD-PARTY-NOTICES.md` are copied into the image root so the redistributed application carries
its own license and third-party notices.

## Installer

`utils\build-installer.ps1` builds the application image and then compiles a single-file Windows installer with
Inno Setup (`packaging\windows\installer.iss`):

```powershell
.\utils\build-installer.ps1
.\utils\build-installer.ps1 -OutputDirectory 'D:\Download'
.\utils\build-installer.ps1 -SkipAppImage
```

The installer shows a destination-directory page, offers a desktop shortcut checkbox (checked by default), and
registers an uninstaller in "Apps & features" (also available as `unins000.exe` inside the install directory).
Output defaults to `D:\Download\VideoDownloader-<version>-win64-setup.exe`; the Inno Setup compiler is expected
at `C:\tools\Inno\ISCC.exe` and can be overridden with `-IsccPath`.
