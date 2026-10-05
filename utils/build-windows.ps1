$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
# 不写死具体版本或本机路径：先看 JAVA_HOME 与 PATH，再按通配符匹配常见安装位置。
$javaHomeCandidates = @($env:JAVA_HOME)
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($javaCommand) {
    $javaHomeCandidates += (Split-Path -Parent (Split-Path -Parent $javaCommand.Source))
}
$javaHomeCandidates += Get-ChildItem -Directory -ErrorAction SilentlyContinue -Path @(
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium\jdk-21*'),
    (Join-Path $env:ProgramFiles 'Java\jdk-21*'),
    (Join-Path $env:ProgramFiles 'Microsoft\jdk-21*')
) | Select-Object -ExpandProperty FullName
$javaHome = $javaHomeCandidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ 'bin\jpackage.exe')) -and (Test-Path (Join-Path $_ 'bin\jlink.exe'))
} | Select-Object -First 1
$javaHome = if ($javaHome) { $javaHome } else { $env:JAVA_HOME }
$javaPackageTool = Join-Path $javaHome 'bin\jpackage.exe'
$javaLinkTool = Join-Path $javaHome 'bin\jlink.exe'
$env:JAVA_HOME = $javaHome
$env:Path = "$(Join-Path $javaHome 'bin');$env:Path"
$wrapper = Join-Path $projectRoot 'gradlew.bat'
$frontendBuild = Join-Path $projectRoot 'project\frontend\build'
$inputDirectory = Join-Path $frontendBuild 'jpackage-input'
$runtimeDirectory = Join-Path $frontendBuild 'runtime'
$outputDirectory = Join-Path $projectRoot 'packaging\windows\output'
$toolsDirectory = Join-Path $projectRoot 'tools'
$iconPath = Join-Path $projectRoot 'packaging\windows\icon.ico'

if (-not (Test-Path $javaPackageTool -PathType Leaf)) {
    throw "jpackage.exe was not found below JAVA_HOME: $javaHome"
}
if (-not (Test-Path $javaLinkTool -PathType Leaf)) {
    throw "jlink.exe was not found below JAVA_HOME: $javaHome"
}
if (-not (Test-Path $wrapper -PathType Leaf)) {
    throw 'gradlew.bat is missing.'
}

$appImageDirectory = Join-Path $outputDirectory 'VideoDownloader'
if (Test-Path $appImageDirectory -PathType Container) {
    # jpackage refuses to overwrite an existing application image directory.
    Remove-Item -LiteralPath $appImageDirectory -Recurse -Force
}

& $wrapper ':project:frontend:installDist' '--no-daemon'
if ($LASTEXITCODE -ne 0) {
    throw 'Gradle installDist failed.'
}

if (Test-Path $inputDirectory) {
    Remove-Item $inputDirectory -Recurse -Force
}
if (Test-Path $runtimeDirectory) {
    Remove-Item $runtimeDirectory -Recurse -Force
}
New-Item $inputDirectory -ItemType Directory -Force | Out-Null
New-Item $outputDirectory -ItemType Directory -Force | Out-Null

$installDirectory = Join-Path $frontendBuild 'install\frontend'
if (-not (Test-Path $installDirectory -PathType Container)) {
    throw "Gradle installDist output was not found: $installDirectory"
}
Copy-Item (Join-Path $installDirectory '*') $inputDirectory -Recurse -Force

# Do NOT copy the tools into the jpackage input directory: that would create a second copy under
# app/tools which the application finds first and which users never see. The tools are copied once,
# next to the launcher, after packaging (see the end of this script).

& $javaLinkTool `
    '--add-modules' 'java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,jdk.crypto.ec,jdk.unsupported' `
    '--strip-debug' '--no-header-files' '--no-man-pages' `
    '--output' $runtimeDirectory
if ($LASTEXITCODE -ne 0) {
    throw 'jlink failed.'
}

$packageArguments = @(
    '--type', 'app-image',
    '--name', 'VideoDownloader',
    '--app-version', '0.1.1',
    '--vendor', 'NZSK',
    '--description', 'Compliant personal video downloader',
    '--input', $inputDirectory,
    '--main-jar', 'lib\frontend-0.1.1.jar',
    '--main-class', 'com.nzsk.videodownloader.ui.VideoDownloaderLauncher',
    '--runtime-image', $runtimeDirectory,
    '--dest', $outputDirectory
)
if (Test-Path $iconPath -PathType Leaf) {
    $packageArguments += @('--icon', $iconPath)
} else {
    Write-Output "未找到图标文件，将使用默认图标: $iconPath（可运行 utils\make-icon.ps1 生成）"
}

& $javaPackageTool @packageArguments
if ($LASTEXITCODE -ne 0) {
    throw 'jpackage app-image creation failed.'
}

# Copies the tools next to the launcher. This is the only copy inside the application image: the
# application searches upward from app/lib, so <image>\tools is the directory it resolves, and
# replacing the files here takes effect immediately.
if (Test-Path (Join-Path $toolsDirectory 'yt-dlp.exe')) {
    New-Item (Join-Path $outputDirectory 'VideoDownloader\tools') -ItemType Directory -Force | Out-Null
    Copy-Item (Join-Path $toolsDirectory 'yt-dlp.exe') (Join-Path $outputDirectory 'VideoDownloader\tools\yt-dlp.exe') -Force
}
if (Test-Path (Join-Path $toolsDirectory 'ffmpeg.exe')) {
    New-Item (Join-Path $outputDirectory 'VideoDownloader\tools') -ItemType Directory -Force | Out-Null
    Copy-Item (Join-Path $toolsDirectory 'ffmpeg.exe') (Join-Path $outputDirectory 'VideoDownloader\tools\ffmpeg.exe') -Force
}

# 许可证与第三方声明随镜像分发，便于履行 FFmpeg/JavaFX 等组件的许可义务。
$imageDirectory = Join-Path $outputDirectory 'VideoDownloader'
foreach ($noticeFile in @('LICENSE', 'THIRD-PARTY-NOTICES.md')) {
    $noticeSource = Join-Path $projectRoot $noticeFile
    if (Test-Path $noticeSource -PathType Leaf) {
        Copy-Item -LiteralPath $noticeSource -Destination (Join-Path $imageDirectory $noticeFile) -Force
    } else {
        Write-Output "提示：未找到 $noticeFile，镜像将缺少对应声明。"
    }
}

Write-Output "Windows application image created: $imageDirectory"
