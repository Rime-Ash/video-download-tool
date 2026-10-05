# 项目进度

更新时间：2026-09-27

## 总体状态

第一、二阶段已完成；第三阶段（JavaFX UI、下载队列、yt-dlp/FFmpeg 集成、配置、日志、异常处理、Windows 打包）
主体已完成；第四阶段自检已执行编译、单元测试与应用镜像打包。

```text
阶段一  项目结构 / Gradle 依赖 / 分层架构 / 接口与模型设计     已完成
阶段二  各模块实现 + 单元测试（引擎、服务、工具、异常）        已完成
阶段三  JavaFX UI / 队列 / yt-dlp / FFmpeg / 配置 / 日志 / 打包 已完成，待真实链接联调
阶段四  编译 / 单元测试 / 打包自检                             进行中（真实下载与取消恢复需人工联调）
```

## 已完成内容

### 分层与模型

- `project/frontend`：JavaFX 界面（下载页、设置页、合规声明、环境提示），仅负责展示与交互。
- `project/backend`：`model` 不可变 record、`service` 服务接口与实现、`engine` 进程封装、`util` 安全与路径工具、
  `exception` 分级业务异常。
- UI 通过 `AppContext` 组合根获取全部服务；界面代码中不出现 `ProcessBuilder`、文件写入或 yt-dlp 调用。

### 安全与合规

- 合规声明常驻显示在“设置”页的环境检测下方，启动时不再弹窗确认。
- URL 校验：仅 HTTP/HTTPS、初始域名与跳转后最终域名双重白名单校验、拒绝带 userinfo 的地址、拒绝相似域名。
- 文件名清洗：Windows 非法字符、控制字符、保留设备名、结尾空格与句点、长度上限。
- 路径安全：`normalize()` 后校验仍在下载目录内，自动生成不冲突文件名（如"标题 (1)"）。
- 命令构造全部使用参数列表，禁止 `cmd /c`、`powershell -Command`；`DefaultProcessExecutor` 二次拒绝 Shell 包装器参数。
- Cookie 仅在调用 yt-dlp 时以 `--cookies` 引用，不复制、不打印、不写入日志、不上传。

### 下载链路

- 解析：`yt-dlp -J --no-playlist` + Jackson 解析标题、作者、时长、封面与格式列表（分辨率、编码、大小）。
- 下载：`--newline --progress-template` 结构化进度、`--windows-filenames --trim-filenames 150 --no-overwrites --continue`、
  `--ffmpeg-location` 指定合并工具、`-f` 选择清晰度；完成后按下发文件名扫描下载目录确定最终文件（扩展名可能因合并而变）。
- 队列：并发上限可动态调整，支持开始、暂停、继续、取消、重试（指数退避，最多 3 次）、清空已完成。
- 完成动作：打开文件、打开所在目录、复制文件路径。

### 配置、日志与环境

- 配置：`%APPDATA%/NZSK/VideoDownloader/config.json`，含下载目录、并发数、限速、代理、Cookie 路径、默认清晰度策略、
  yt-dlp 与 FFmpeg 路径。
- 日志：`%LOCALAPPDATA%/NZSK/VideoDownloader/logs/`，按 2 MB 滚动，保留 14 天、总量上限 50 MB。
- 环境检测：Java 版本、yt-dlp、FFmpeg、下载目录可写、磁盘剩余空间；缺失时给出可操作提示。

### 测试

```text
30 个测试类 / 108 个用例，全部通过（gradlew build：后端 28 类 / 105 用例，前端 2 类 / 3 用例）
```

覆盖 URL 白名单与协议校验、跳转后域名校验、文件名清洗与长度、路径穿越与重名、命令参数构造与选项注入防护、
yt-dlp JSON 解析、进度解析、输出文件定位、失败重试与退避、暂停/继续/取消、并发上限、配置读写、环境检测、
工具路径搜索、组合根接线以及 JavaFX 视图构建冒烟测试。

### 图文/图集下载（2026-10-04）

- 新增 `ImagePostClient`（`DefaultImagePostClient`）：用平台 web 详情接口解析单条图文作品，逐张下载图片。
- 新增 `DouyinImagePostParser`：解析图片列表、优先选用非 webp 地址、按媒体 CDN 白名单过滤。
- 新增 `NetscapeCookieStore`：按目标域名从 Netscape Cookie 文件构造请求头，值不写日志。
- 下载队列抽象出 `DownloadJob`，视频与图集共用排队、进度、暂停、取消、重试、清空已完成。
- 界面：解析 `/note/` 链接后展示标题/作者/图片数量与首图预览，"加入下载队列"按图集入库，
  进度显示 "n / N 张"，完成后可"打开文件（文件夹）/打开所在目录/复制路径"。
- 实测：用真实链接解析到 2 张 1920×1080 图片并成功下载（有效 JPEG，文件头校验通过）；
  界面队列显示"图集 2 张 / 100% / 已完成"。
- 动图（实况照片）支持：解析 `images[].video`，优先取 H.264 播放地址，与静态图一起保存为 `01.jpg` + `01.mp4`。
  实测该作品 2 张图片均为动图，下载得到 01.jpg/01.mp4/02.jpg/02.mp4，
  mp4 为 H.264+AAC、1280×720、3.43 秒与 3.63 秒，与接口返回的 duration 完全一致。

### 抖音视频浏览器兜底与进度修复（2026-10-05）

- 新增 `DouyinVideoClient`（`DefaultDouyinVideoClient`）：yt-dlp 解析抖音视频失败时，用本机 Edge/Chrome
  以无头模式渲染一次公开页面（独立临时 profile，不读取用户浏览器数据），由 `DouyinVideoPageParser`
  读出标题、作者、封面与可播放地址，再由 `DouyinVideoDownloadJob` 直接下载所选地址。
  该路径不计算签名、不处理或绕过任何验证挑战，只读取用户在浏览器中能正常打开的同一页面。
- 新增 `MediaCdnHosts`：图片与视频共用的媒体 CDN 白名单，`ImageCdnHosts` 改为委托实现，行为不变。
- 新增 `BrowserLocator`：按标准安装变量与 `PATH` 查找 `msedge.exe`/`chrome.exe`，找不到时给出明确提示。
- 下载队列新增 `DOUYIN_VIDEO` 任务类型与 `addDouyinVideoTask`；下载页复用同一张格式表展示兜底来源，
  "加入下载队列"按兜底任务入库，进度、暂停/取消、重试与普通视频一致。
- 修复进度始终为 0%：`--progress-template` 的 `download:` 前缀是 yt-dlp 的模板类型选择器，输出中并不出现，
  原解析器因此永不匹配。模板改为带 `vd-progress:` 标记，`ProgressParser` 按标记解析，`NA` 字段显示为未知。
- 修复配置自愈：配置中记录的 yt-dlp/FFmpeg 路径指向已卸载或移动的旧安装目录时，启动时自动回退到程序目录
  中的工具（`AppPaths.preferredToolPath`），不再出现"未找到 yt-dlp / FFmpeg"却无法自恢复的情况。
- 实测（真实链接）：yt-dlp 返回 HTTP 403 后走兜底，约 23 秒解析出 1 个作品（标题、作者、封面、3 个可下载源），
  下载得到 456 KB 的 MP4，ffmpeg 校验为 H.264 + AAC、576×1024、6.67 秒。
- 实测（B 站对照）：应用自身的下载队列完整跑通 `bestvideo+bestaudio/best` + FFmpeg 合并，产出 21.5 MB MP4。
- 单元测试：新增解析器、客户端（含本地 HTTP 服务）、CDN 白名单、浏览器定位、工具路径回退与队列兜底任务用例，
  后端用例总数 103（含原有 82 个）。

### 运行时验证（2026-09-27）

- `tools/yt-dlp.exe` 2026.08.19 与 `tools/ffmpeg.exe` n8.1.3（GPL 构建）已就位，版本、SHA-256 与许可证记录在
  `tools/README.md`。
- 真实链接单次解析验证：`yt-dlp -J --no-playlist` 成功返回标题、作者、时长与 15 个可用格式。
- 打包后的 `VideoDownloader.exe` 启动正常，合规声明可在“设置”页查看。

### 链接兼容性（2026-09-27）

- 抖音分享链接（`v.douyin.com`）跳转后若落到账号主页，会自动用 `modal_id`/`vid` 参数还原为
  `https://www.douyin.com/video/<id>`；没有视频 id 的账号主页与 B 站空间页/合集页会被明确拒绝，避免越出单视频范围。
- 抖音当前对公开视频也强制要求 Cookie（yt-dlp 报 `Fresh cookies ... are needed`），因此设置页新增
  "从浏览器读取 Cookie"（`--cookies-from-browser`），与 Cookie 文件二选一，浏览器 Cookie 优先。
- 解析失败时会根据 yt-dlp 输出给出可操作提示（需要 Cookie / 链接不是单视频 / 需要更新 yt-dlp / 网络超时），
  且不会回显原始输出内容。
- 实测本机 Edge 的 `Local State` 含 `app_bound_encrypted_key`（Chromium 应用绑定加密），
  `--cookies-from-browser edge` 必定失败；程序会明确提示改用导出的 Cookie 文件或 Firefox。

## 待人工确认

1. 真实链接联调：需自备 `tools/yt-dlp.exe` 与 `tools/ffmpeg.exe`，验证解析、下载、合并与暂停续传。
2. 安装包：当前产出 `--type app-image`；如需 `msi/exe` 安装包，需要在装有 WiX 的机器上改用 `--type exe`。
3. 第三方二进制的版本、校验和与许可证记录：`tools/README.md` 保留待填写模板。
4. 界面细节走查：封面加载、长标题折行、主题配色等视觉打磨。

## 已知限制

- 纯音频抽取未单独提供入口，音频格式与视频格式共用同一套选择逻辑。
- 更换 yt-dlp 路径后，已排队但未完成的任务需要重新开始。
- 抖音兜底解析需要本机安装 Microsoft Edge 或 Google Chrome，且单次解析约需 20 秒（渲染完整页面）。
- 界面文案目前仅中文。

## 隐私自查（2026-10-04）

```text
源码 / 文档 / 打包镜像  未发现用户名、主机名、本机绝对路径、账号 ID 或 Cookie 数据
```

- 已移除测试代码中残留的真实抖音视频号与账号 ID，改用合成样例数据。
- 已移除 `.github/modernize` 下的钩子脚本（该脚本会把终端命令以 JSONL 记录到项目内，属于隐私隐患）。
- 已移除项目根目录中带本机绝对路径的快捷方式。
- 新增根目录 `.gitignore`，排除构建产物、缓存、日志、Cookie 导出、快捷方式与第三方可执行文件。

### 文件规范整理（2026-10-04）

- 新增 `doc/repository-standard.md`：目录结构、命名规则、生成物位置、第三方二进制策略与隐私要求的唯一标准。
- 新增商业级仓库标准文件：`LICENSE`、`THIRD-PARTY-NOTICES.md`、`CHANGELOG.md`、`.gitattributes`、`.editorconfig`。
- `utils/verify-project.ps1` 升级为规范闸门：校验必需目录与文件、拒绝 `config.json`/日志/Cookie/快捷方式、
  扫描文本中的本机绝对路径与当前用户名/计算机名、检查打包镜像是否混入用户数据，失败返回非零退出码。
- `utils/build-windows.ps1` 移除写死的 JDK 绝对路径，改为 `JAVA_HOME`/`PATH`/常见安装目录自动探测；
  打包时把 `LICENSE` 与 `THIRD-PARTY-NOTICES.md` 复制进镜像根目录。
- `README.md` 与 `doc/packaging.md` 同步修正：合规声明改为设置页常驻（非启动弹窗），图标源图缺失时的回退行为。

### 复核结果（2026-10-04）

```text
源码 / 文档 / 脚本 / 资源 / 打包镜像
  未发现用户名、计算机名、本机绝对路径、真实账号或视频 ID、Cookie 数据
  打包镜像内无 config.json、logs、Cookie 或用户下载内容
```

许可证已按账号其他仓库的约定确定为 MIT（`Copyright (c) 2026 3447347190`）。占位目录 `database/` 与空的
根目录 `downloads/` 已删除。其余待确认事项记录在 `doc/repository-standard.md` 第 10 节：FFmpeg 的 GPL
构建义务（或改用 LGPL 构建）。
