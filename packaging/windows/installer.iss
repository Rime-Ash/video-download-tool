; Video Downloader —— Windows 安装包脚本（Inno Setup 7）
;
; 由 utils\build-installer.ps1 调用，不直接手工运行。
; 输出目录通过命令行传入：ISCC.exe /DOutputDir=D:\Download installer.iss
;
; 安装向导行为：
;   1. 显示"选择目标位置"页面，可修改安装目录；
;   2. 显示"选择附加任务"页面，桌面快捷方式默认勾选，可取消；
;   3. 自动生成卸载程序（控制面板"应用和功能"中可见，安装目录下为 unins000.exe）。

#ifndef MyAppVersion
  #define MyAppVersion "0.1.1"
#endif

#ifndef OutputDir
  #define OutputDir "output"
#endif

#define MyAppName "Video Downloader"
#define MyAppPublisher "3447347190"
#define MyAppExeName "VideoDownloader.exe"
#define MyAppSourceDir "output\VideoDownloader"

[Setup]
AppId={{B7C4E2A1-9F3D-4A62-8E15-2C7D6A0B4F31}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Video Downloader
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=admin
OutputDir={#OutputDir}
OutputBaseFilename=VideoDownloader-{#MyAppVersion}-win64-setup
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
Source: "{#MyAppSourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
