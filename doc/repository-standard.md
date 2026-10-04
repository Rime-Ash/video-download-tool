# 仓库与文件规范

本文件是本项目文件组织的唯一权威标准：目录结构、命名规则、生成物位置、第三方二进制策略与隐私要求。
新增文件前先对照本文件，提交前运行 `utils\verify-project.ps1` 自动核对。

## 1. 目录结构

```text
视频下载器/
├── README.md                 项目入口：范围、构建、运行、目录说明
├── CHANGELOG.md              版本变更记录
├── LICENSE                   自有许可证声明
├── THIRD-PARTY-NOTICES.md    第三方组件版本、许可证与义务
├── .gitignore                排除生成物、日志、Cookie 导出、第三方二进制
├── .gitattributes            换行符、文本编码与二进制标记
├── .editorconfig             编辑器统一格式
├── build.gradle              根构建脚本（仅声明公共配置）
├── settings.gradle           模块包含关系
├── gradle/                   Gradle Wrapper（必须提交，保证构建可复现）
├── project/
│   ├── backend/              模型、服务、引擎、工具、异常（无界面依赖）
│   └── frontend/             JavaFX 界面与组合根
├── doc/                      架构、安全、打包、进度与本文件
├── prototype/                产品原型材料
├── database/                 保留目录，本项目不使用数据库
├── utils/                    构建、校验、图标与手工验证脚本
├── tools/                    本地 yt-dlp.exe / ffmpeg.exe（不提交，只提交 README.md 溯源记录）
└── packaging/                Windows 打包脚本、图标与输出目录
```

规则：

- 源码只放 `project/**/src` 下，界面代码不得直接调用 `ProcessBuilder`、写文件或调用 yt-dlp。
- 每一个顶层目录都必须有职责说明，新增顶层目录必须同步更新本文件与 `README.md`。
- 空目录不提交（Git 不跟踪空目录）；确实需要保留的用 `README.md` 占位并说明原因。

## 2. 命名规则

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| Java 类文件 | 与公开类名一致，PascalCase | `CommandBuilder.java` |
| 接口与实现 | 接口用名词，实现加 `Default` 前缀 | `YtDlpClient` / `DefaultYtDlpClient` |
| Java 包 | 全小写，`com.nzsk.videodownloader.<层>` | `...videodownloader.engine` |
| 测试类 | 被测类名 + `Test` | `CommandBuilderTest.java` |
| 文档 | 全小写，连字符分隔，`.md` | `repository-standard.md` |
| 脚本 | 全小写，连字符分隔，动作在前 | `build-windows.ps1` |
| 资源 | 全小写，连字符分隔 | `icon.png` |

禁止：中文文件名、空格文件名、无扩展名文件、`新建文档`、`test2`、`final版` 一类名称。

## 3. 文档要求

- 面向用户的说明写在 `README.md`；设计决策写在 `doc/`；版本变化写在 `CHANGELOG.md`。
- 涉及安全或隐私的改动必须同步更新 `doc/security.md`。
- 涉及打包、依赖或第三方二进制的改动必须同步更新 `doc/packaging.md` 与 `THIRD-PARTY-NOTICES.md`。
- 文档中的路径示例使用占位符（`%APPDATA%`、`<application>`），不得写入本机绝对路径。

## 4. 严禁提交的内容

以下内容属于运行时数据或隐私数据，必须留在项目树之外：

```text
config.json                 用户配置（下载目录、工具路径、Cookie 文件路径）
logs/  *.log                运行日志
cookies*.txt  *cookie*.json 浏览器导出的 Cookie
*.lnk  *.bat.local          含本机绝对路径的快捷方式或本地脚本
build/  project/*/build/    构建产物
.gradle/  .idea/  *.iml     Gradle 缓存与 IDE 元数据
packaging/windows/output/   打包镜像（含完整 JRE，体积大且可重建）
tools/*.exe  tools/*.zip    第三方二进制（仅保留溯源记录）
```

用户运行数据固定在项目树之外：

- 配置：`%APPDATA%/NZSK/VideoDownloader/config.json`
- 日志：`%LOCALAPPDATA%/NZSK/VideoDownloader/logs/`
- 默认下载目录：`%USERPROFILE%/Downloads/VideoDownloader/`

Cookie 文件只被就地引用（`--cookies`），不复制、不打印、不写入日志。发布前请自行确认导出的 Cookie
文件本身没有被一并拷贝到交付介质中。

## 5. 生成物与清理

| 生成物 | 位置 | 是否交付 |
| --- | --- | --- |
| 编译输出 | `project/*/build/` | 否 |
| Gradle 缓存 | `.gradle/` | 否 |
| 打包镜像 | `packaging/windows/output/VideoDownloader/` | 是（交付物） |
| 日志与配置 | `%APPDATA%` / `%LOCALAPPDATA%` | 否 |

清理方式：删除 `build/`、`project/*/build/`、`.gradle/`。打包镜像可随时由
`utils\build-windows.ps1` 重建；重建会先删除旧镜像目录。

## 6. 第三方二进制策略

- 不提交任何第三方可执行文件或压缩包，只提交 `tools/README.md` 中的来源记录。
- 每个二进制必须记录：名称、精确版本、来源 URL、SHA-256、许可证、获取日期。
- 许可证义务变化时同步更新 `THIRD-PARTY-NOTICES.md`；FFmpeg 的 GPL 构建义务见该文件。
- 不自动下载二进制；由发布者从官方发布页获取并放入 `tools/` 后再打包。

## 7. 隐私要求

项目树内不得出现：

- 本机用户名、计算机名、盘符绝对路径（如 `C:\Users\<name>\...`）；
- 真实账号 ID、真实视频 ID、真实分享链接；
- Cookie 内容、令牌、代理凭据；
- 用户下载的视频或图片。

测试数据一律使用合成值，例如账号用 `MS4wLjABAAAAsample`，视频号用 `7300000000000000000`。

应用侧约束（详见 `doc/security.md`）：日志只记录通用信息与异常类名，不记录 URL、标题、Cookie 路径、
代理凭据与完整命令行；应用不联网上传任何本地数据。

## 8. 自动核对

提交前运行：

```powershell
.\utils\verify-project.ps1
```

脚本会检查：必需目录与文件是否存在、是否混入禁提交内容、文本文件是否出现本机绝对路径与用户名。
校验失败时会列出具体文件并返回非零退出码。

## 9. 发布前检查清单

1. `.\gradlew.bat build` 通过，全部测试绿色。
2. `.\utils\verify-project.ps1` 通过。
3. 重新运行 `utils\build-windows.ps1` 生成镜像，确保源码与镜像一致（镜像不得早于源码修改时间）。
4. 镜像目录内不含 `config.json`、`logs/`、Cookie 文件与任何用户下载内容。
5. `THIRD-PARTY-NOTICES.md` 与 `tools/README.md` 的版本、校验和、许可证已更新。
6. 已确认 `LICENSE` 的著作权人与授权条款。

## 10. 待确认事项（需要项目所有者决策）

| 事项 | 说明 | 默认状态 |
| --- | --- | --- |
| 自有许可证 | 已确定为 MIT，与账号其他仓库一致；著作权人 `3447347190` | 已确认 |
| FFmpeg 许可证 | 现附带 GPL-3.0 构建；若不便履行源码提供义务，应改用 LGPL 构建 | 待确认 |
| `database/` 目录 | 项目不使用数据库，仅作占位；如无需保留可连同 `verify-project.ps1` 的检查项一并删除 | 保留 |
| 根目录 `downloads/` | 空目录，与默认下载目录无关；可删除 | 保留 |
