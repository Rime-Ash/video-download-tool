<#
构建 Windows 安装包。

流程：
  1. 调用 build-windows.ps1 生成应用镜像（packaging\windows\output\VideoDownloader）；
  2. 用 Inno Setup 把镜像编译成单个安装程序，默认输出到 D:\Download；
  3. 安装程序自带卸载程序，并在安装向导中提供安装目录选择页与桌面快捷方式复选框。

用法：
  .\utils\build-installer.ps1
  .\utils\build-installer.ps1 -OutputDirectory 'D:\Download'
  .\utils\build-installer.ps1 -SkipAppImage        # 复用已有镜像，只重编安装包
#>
param(
    [string]$OutputDirectory = 'D:\Download',
    [string]$IsccPath = 'C:\tools\Inno\ISCC.exe',
    [string]$AppVersion = '0.1.0',
    [switch]$SkipAppImage
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $SkipAppImage) {
    & (Join-Path $PSScriptRoot 'build-windows.ps1')
    if ($LASTEXITCODE -ne 0) { throw '应用镜像构建失败。' }
}

$appImage = Join-Path $projectRoot 'packaging\windows\output\VideoDownloader'
if (-not (Test-Path (Join-Path $appImage 'VideoDownloader.exe') -PathType Leaf)) {
    throw "未找到应用镜像：$appImage（请先运行 utils\build-windows.ps1）"
}

if (-not (Test-Path $IsccPath -PathType Leaf)) {
    throw "未找到 Inno Setup 编译器：$IsccPath（可用 -IsccPath 指定 ISCC.exe 位置）"
}

$installerScript = Join-Path $projectRoot 'packaging\windows\installer.iss'
if (-not (Test-Path $installerScript -PathType Leaf)) {
    throw "未找到安装脚本：$installerScript"
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

& $IsccPath "/DMyAppVersion=$AppVersion" "/DOutputDir=$OutputDirectory" $installerScript
if ($LASTEXITCODE -ne 0) { throw '安装包编译失败。' }

$installer = Join-Path $OutputDirectory "VideoDownloader-$AppVersion-win64-setup.exe"
if (-not (Test-Path $installer -PathType Leaf)) {
    throw "编译完成但未找到安装包：$installer"
}

$size = (Get-Item $installer).Length
Write-Output ''
Write-Output '安装包已生成：'
Write-Output ("  {0}  ({1:N1} MB)" -f $installer, ($size / 1MB))
Write-Output ''
Write-Output '安装向导包含：目标目录选择页、桌面快捷方式复选框（默认勾选）。'
Write-Output '卸载程序：控制面板“应用和功能”中的 Video Downloader，或安装目录下的 unins000.exe。'
