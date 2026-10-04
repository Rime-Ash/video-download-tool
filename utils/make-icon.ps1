<#
生成应用图标：

1. project/frontend/src/main/resources/icon.png  —— JavaFX 窗口/任务栏图标（256x256）
2. packaging/windows/icon.ico                    —— jpackage 打包 exe 时使用的多尺寸 ICO

源图默认取项目根目录的 tubiao.png，等比缩放居中，不会拉伸变形。
#>
param(
    [string]$SourcePng = '',
    [string]$ResourcePng = '',
    [string]$IcoPath = '',
    [int[]]$Sizes = @(16, 24, 32, 48, 64, 128, 256)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ResourcePng)) {
    $ResourcePng = Join-Path $projectRoot 'project\frontend\src\main\resources\icon.png'
}
if ([string]::IsNullOrWhiteSpace($IcoPath)) {
    $IcoPath = Join-Path $projectRoot 'packaging\windows\icon.ico'
}
if ([string]::IsNullOrWhiteSpace($SourcePng)) {
    # 优先使用项目根目录的原始设计图；缺失时退回已生成的窗口图标，保证脚本始终可用
    $preferred = Join-Path $projectRoot 'tubiao.png'
    $SourcePng = if (Test-Path -LiteralPath $preferred -PathType Leaf) { $preferred } else { $ResourcePng }
}

if (-not (Test-Path -LiteralPath $SourcePng -PathType Leaf)) {
    throw "找不到源图: $SourcePng（请放回 tubiao.png 或用 -SourcePng 指定）"
}
Write-Output "源图: $SourcePng"

function New-ScaledBitmap {
    param([System.Drawing.Image]$Source, [int]$Size)

    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

        $scale = [Math]::Min($Size / $Source.Width, $Size / $Source.Height)
        $width = [int][Math]::Round($Source.Width * $scale)
        $height = [int][Math]::Round($Source.Height * $scale)
        $x = [int](($Size - $width) / 2)
        $y = [int](($Size - $height) / 2)
        $graphics.DrawImage($Source, (New-Object System.Drawing.Rectangle($x, $y, $width, $height)))
    } finally {
        $graphics.Dispose()
    }
    return $bitmap
}

# 生成 ICO 中的单个图像块：BITMAPINFOHEADER + BGRA 像素 + AND 掩码
function Get-IcoImageBytes {
    param([System.Drawing.Bitmap]$Bitmap)

    $width = $Bitmap.Width
    $height = $Bitmap.Height
    $stream = New-Object System.IO.MemoryStream
    $writer = New-Object System.IO.BinaryWriter($stream)
    try {
        $writer.Write([int]40)
        $writer.Write([int]$width)
        $writer.Write([int]($height * 2))
        $writer.Write([int16]1)
        $writer.Write([int16]32)
        $writer.Write([int]0)
        $writer.Write([int]($width * $height * 4))
        $writer.Write([int]0)
        $writer.Write([int]0)
        $writer.Write([int]0)
        $writer.Write([int]0)

        for ($y = $height - 1; $y -ge 0; $y--) {
            for ($x = 0; $x -lt $width; $x++) {
                $pixel = $Bitmap.GetPixel($x, $y)
                $writer.Write([byte]$pixel.B)
                $writer.Write([byte]$pixel.G)
                $writer.Write([byte]$pixel.R)
                $writer.Write([byte]$pixel.A)
            }
        }

        $maskRowBytes = [int][Math]::Ceiling($width / 8.0)
        $padding = (4 - ($maskRowBytes % 4)) % 4
        for ($y = 0; $y -lt $height; $y++) {
            for ($i = 0; $i -lt $maskRowBytes; $i++) { $writer.Write([byte]0) }
            for ($i = 0; $i -lt $padding; $i++) { $writer.Write([byte]0) }
        }
        $writer.Flush()
        # 逗号运算符避免 PowerShell 把字节数组展开成独立元素
        return ,$stream.ToArray()
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

# 先读入内存，避免源图与输出文件相同时被文件占用
$sourceStream = New-Object System.IO.MemoryStream(,[System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $SourcePng).Path))
$source = [System.Drawing.Image]::FromStream($sourceStream)
try {
    $images = @()
    foreach ($size in $Sizes) {
        $bitmap = New-ScaledBitmap -Source $source -Size $size
        try {
            $images += [pscustomobject]@{ Size = $size; Bytes = (Get-IcoImageBytes -Bitmap $bitmap) }
        } finally {
            $bitmap.Dispose()
        }
    }

    # 资源图标：256x256 PNG，供 JavaFX 窗口使用
    $resourceDirectory = Split-Path -Parent $ResourcePng
    New-Item -ItemType Directory -Force -Path $resourceDirectory | Out-Null
    $resourceBitmap = New-ScaledBitmap -Source $source -Size 256
    try {
        $resourceBitmap.Save($ResourcePng, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $resourceBitmap.Dispose()
    }

    # ICO：6 字节文件头 + 每个尺寸 16 字节目录项 + 图像数据
    $icoDirectory = Split-Path -Parent $IcoPath
    New-Item -ItemType Directory -Force -Path $icoDirectory | Out-Null
    $fileStream = [System.IO.File]::Create($IcoPath)
    $binaryWriter = New-Object System.IO.BinaryWriter($fileStream)
    try {
        $binaryWriter.Write([int16]0)
        $binaryWriter.Write([int16]1)
        $binaryWriter.Write([int16]$images.Count)

        $offset = 6 + (16 * $images.Count)
        foreach ($image in $images) {
            $dimension = if ($image.Size -ge 256) { [byte]0 } else { [byte]$image.Size }
            $binaryWriter.Write($dimension)
            $binaryWriter.Write($dimension)
            $binaryWriter.Write([byte]0)
            $binaryWriter.Write([byte]0)
            $binaryWriter.Write([int16]1)
            $binaryWriter.Write([int16]32)
            $binaryWriter.Write([int]$image.Bytes.Length)
            $binaryWriter.Write([int]$offset)
            $offset += $image.Bytes.Length
        }
        foreach ($image in $images) {
            $binaryWriter.Write($image.Bytes)
        }
        $binaryWriter.Flush()
    } finally {
        $binaryWriter.Dispose()
        $fileStream.Dispose()
    }
} finally {
    $source.Dispose()
    $sourceStream.Dispose()
}

$ico = New-Object System.Drawing.Icon($IcoPath)
Write-Output "已生成窗口图标: $ResourcePng"
Write-Output "已生成应用图标: $IcoPath ($($Sizes -join ', ') 像素，默认尺寸 $($ico.Width)x$($ico.Height))"
$ico.Dispose()
