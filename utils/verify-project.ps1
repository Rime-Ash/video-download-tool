<#
项目文件规范与隐私校验。

检查内容：
1. 必需目录与标准文件是否存在；
2. 项目树内是否混入运行时数据（config.json、logs、Cookie 导出、快捷方式）；
3. 文本文件中是否出现本机绝对路径、用户名或计算机名；
4. 打包镜像内是否混入用户数据。

全部通过返回 0；发现问题返回 1 并列出具体文件。
#>
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

$requiredDirectories = @(
    'doc',
    'prototype',
    'project/frontend',
    'project/backend',
    'utils',
    'tools',
    'packaging'
)

$requiredFiles = @(
    'README.md',
    'CHANGELOG.md',
    'LICENSE',
    'THIRD-PARTY-NOTICES.md',
    '.gitignore',
    '.gitattributes',
    '.editorconfig',
    'build.gradle',
    'settings.gradle',
    'gradlew',
    'gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties',
    'doc/architecture.md',
    'doc/security.md',
    'doc/packaging.md',
    'doc/repository-standard.md',
    'tools/README.md',
    'packaging/windows/icon.ico'
)

foreach ($directory in $requiredDirectories) {
    if (-not (Test-Path $directory -PathType Container)) {
        $errors.Add("缺少必需目录：$directory")
    }
}

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file -PathType Leaf)) {
        $errors.Add("缺少必需文件：$file")
    }
}

# 运行时数据与人为产物不得出现在项目树内。
$forbiddenNamePatterns = @(
    'config.json',
    'cookies*.txt',
    '*cookie*.json',
    '*.lnk',
    '*.bat.local',
    '*.log',
    '*.zip'
)

$scanRoots = @('doc', 'prototype', 'project', 'utils', 'tools', 'packaging')
foreach ($root in $scanRoots) {
    if (-not (Test-Path $root -PathType Container)) { continue }
    foreach ($pattern in $forbiddenNamePatterns) {
        Get-ChildItem -Path $root -Recurse -Force -File -Filter $pattern -ErrorAction SilentlyContinue |
            ForEach-Object {
                $relative = $_.FullName.Substring($projectRoot.Length + 1)
                $errors.Add("禁止提交的文件：$relative")
            }
    }
    Get-ChildItem -Path $root -Recurse -Force -Directory -Filter 'logs' -ErrorAction SilentlyContinue |
        ForEach-Object {
            $relative = $_.FullName.Substring($projectRoot.Length + 1)
            $errors.Add("禁止提交的目录：$relative")
        }
}

# 生成物正常存在，但不应进入版本库。
foreach ($generated in @('build', '.gradle', 'project/backend/build', 'project/frontend/build')) {
    if (Test-Path $generated) {
        $warnings.Add("存在生成物（已由 .gitignore 排除，交付前建议清理）：$generated")
    }
}

# 文本内容扫描：本机绝对路径、当前用户名与计算机名。
# 模式由片段拼接而成，避免脚本自身命中；repository-standard.md 属于规则文档，单独豁免。
# 生成物目录（build/、.gradle/、打包镜像）命中时只告警，因为它们不进入版本库，但交付前必须清理。
$driveUserPattern = 'C:' + '\' + 'Users' + '\'
$absoluteUserPath = [regex]::Escape($driveUserPattern)
$ruleDocumentSkip = @('doc/repository-standard.md')
$generatedPrefixes = @(
    'build/',
    '.gradle/',
    'project/backend/build/',
    'project/frontend/build/',
    'packaging/windows/output/'
)
$generatedHits = New-Object System.Collections.Generic.HashSet[string]

$textExtensions = @(
    '.md', '.java', '.gradle', '.ps1', '.xml', '.json', '.properties',
    '.txt', '.cfg', '.bat', '.cmd', '.yml', '.yaml', '.html', '.css'
)

$identityTokens = @()
if ($env:USERNAME) { $identityTokens += $env:USERNAME }
if ($env:COMPUTERNAME) { $identityTokens += $env:COMPUTERNAME }
$identityTokens = $identityTokens | Where-Object { $_ -and $_.Length -ge 3 } | Select-Object -Unique

$textFiles = Get-ChildItem -Path $scanRoots -Recurse -Force -File -ErrorAction SilentlyContinue |
    Where-Object { $textExtensions -contains $_.Extension.ToLowerInvariant() }

foreach ($file in $textFiles) {
    $relative = $file.FullName.Substring($projectRoot.Length + 1)
    $relativeForMatch = $relative.Replace('\', '/')
    $skip = $false
    foreach ($prefix in $ruleDocumentSkip) {
        if ($relativeForMatch.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) { $skip = $true }
    }
    if ($skip) { continue }

    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if ([string]::IsNullOrEmpty($content)) { continue }

    $hits = New-Object System.Collections.Generic.List[string]
    if ($content -match $absoluteUserPath) {
        $hits.Add('本机用户绝对路径')
    }
    foreach ($token in $identityTokens) {
        if ($content.Contains($token)) {
            $hits.Add("本机用户名/计算机名 [$token]")
        }
    }
    if ($hits.Count -eq 0) { continue }

    $generatedPrefix = $null
    foreach ($prefix in $generatedPrefixes) {
        if ($relativeForMatch.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $generatedPrefix = $prefix
            break
        }
    }
    if ($generatedPrefix) {
        $generatedHits.Add($generatedPrefix) | Out-Null
    } else {
        foreach ($hit in $hits) {
            $errors.Add("$hit：$relative")
        }
    }
}

foreach ($prefix in $generatedHits) {
    $warnings.Add("生成物目录 $prefix 内含本机用户名/路径，交付前必须清理（已由 .gitignore 排除，不会进入版本库）")
}

# 打包镜像内不得包含用户配置、日志或 Cookie。
$imageRoot = 'packaging/windows/output/VideoDownloader'
if (Test-Path $imageRoot -PathType Container) {
    Get-ChildItem -Path $imageRoot -Recurse -Force -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(config\.json|cookies.*\.txt)$' -or $_.DirectoryName -match '\\logs($|\\)' } |
        ForEach-Object {
            $relative = $_.FullName.Substring($projectRoot.Length + 1)
            $errors.Add("打包镜像内混入用户数据：$relative")
        }
}

if ($warnings.Count -gt 0) {
    Write-Output '提示：'
    foreach ($warning in $warnings) { Write-Output "  - $warning" }
}

if ($errors.Count -gt 0) {
    Write-Output ''
    Write-Output '文件规范校验未通过：'
    foreach ($item in $errors) { Write-Output "  ✗ $item" }
    Write-Output ''
    Write-Output "共 $($errors.Count) 项问题，详见 doc/repository-standard.md。"
    exit 1
}

Write-Output '文件规范校验通过：目录完整、无禁提交内容、未发现本机隐私信息。'
exit 0
