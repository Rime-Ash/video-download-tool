# 链接解析验证工具

用于在不打开界面的情况下验证"URL 校验 → 链接归一化 → yt-dlp 解析 → JSON 解析"这条链路，
定位用户反馈的链接问题时很有用。它调用的是应用本身的类，因此结果与界面一致。

```powershell
# 在项目根目录下执行，全部使用相对路径
$lib = 'packaging\windows\output\VideoDownloader\app\lib'
$out = 'utils\verify-parse\classes'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp "$lib\*" -d $out .\utils\verify-parse\VerifyParse.java

# 用法：VerifyParse <url> [cookieFile|-] [cookieBrowser|-]
& "$env:JAVA_HOME\bin\java.exe" -Dfile.encoding=UTF-8 -cp "$lib\*;$out" VerifyParse `
    'https://www.bilibili.com/video/BV1GJ411x7h7'
```

参数说明：

- `<url>`：待验证的视频链接（支持短链、带 `modal_id` 的抖音主页链接）。
- `cookieFile`：Netscape 格式 Cookie 文件路径，不需要时写 `-`。
- `cookieBrowser`：`chrome`、`edge`、`firefox` 等，不需要时写 `-`。

注意：验证工具会把解析结果打印到终端，请不要把带 Cookie 的完整命令或输出贴到公开渠道。
