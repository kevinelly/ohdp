#ifndef PayloadDir
  #error PayloadDir is required
#endif
#ifndef OutputDir
  #error OutputDir is required
#endif
#ifndef AppVersion
  #define AppVersion "1.0.12"
#endif

[Setup]
AppId={{B956C8A9-B173-4F90-B275-5196D5E36101}
AppName=北海职业卫生数据处理平台
AppVersion={#AppVersion}
AppPublisher=BHFYZX
DefaultDirName={localappdata}\Programs\BHFYZX
DefaultGroupName=北海职业卫生数据处理平台
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#OutputDir}
OutputBaseFilename=BHFYZX-Setup-{#AppVersion}-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableWelcomePage=yes
DisableDirPage=no
UsePreviousTasks=no
DisableProgramGroupPage=yes
DisableReadyPage=no
UninstallDisplayIcon={app}\BHFYZX.exe
CloseApplications=no
RestartApplications=no
SetupLogging=yes
VersionInfoVersion={#AppVersion}.0

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Default.isl,ChineseSimplified.isl"

[Messages]
ReadyLabel1=点击“安装”即可安装北海职业卫生数据处理平台。
ReadyLabel2a=已包含运行环境和本地数据库，无需另外安装 Java、MySQL 或 Docker。安装完成后即可打开使用。
FinishedLabel=安装完成。管理员账号 admin，默认密码 Test@345。首次启动会按安装选项初始化测试数据。

[Tasks]
Name: "sampledata"; Description: "导入线上版本的测试数据（可在启动窗口导入或删除）"; GroupDescription: "测试数据："

[INI]
Filename: "{app}\installation.ini"; Section: "Setup"; Key: "ImportSample"; String: "{code:GetImportChoice}"
Filename: "{app}\installation.ini"; Section: "Setup"; Key: "RequestId"; String: "{code:GetRequestId}"

[UninstallDelete]
Type: files; Name: "{app}\installation.ini"

[Files]
Source: "{#PayloadDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userdesktop}\北海职业卫生数据处理平台"; Filename: "{app}\BHFYZX.exe"; WorkingDir: "{app}"
Name: "{group}\北海职业卫生数据处理平台"; Filename: "{app}\BHFYZX.exe"; WorkingDir: "{app}"
Name: "{group}\使用说明"; Filename: "{app}\使用说明.txt"
Name: "{group}\停止系统"; Filename: "{app}\BHFYZX.exe"; Parameters: "--stop"
Name: "{group}\卸载"; Filename: "{uninstallexe}"

[Run]
Filename: "{app}\BHFYZX.exe"; Description: "立即启动北海职业卫生数据处理平台"; Flags: nowait postinstall skipifsilent

[Code]
var
  RequestId: String;

function InitializeSetup(): Boolean;
begin
  RequestId := GetDateTimeString('yyyymmddhhnnss', '-', ':');
  Result := True;
end;

function GetImportChoice(Param: String): String;
begin
  if WizardIsTaskSelected('sampledata') then Result := '1' else Result := '0';
end;

function GetRequestId(Param: String): String;
begin
  Result := RequestId;
end;

function StopInstalledApp(): Boolean;
var
  ResultCode: Integer;
  Launcher: String;
begin
  Launcher := ExpandConstant('{app}\BHFYZX.exe');
  Result := True;
  if FileExists(Launcher) then
    Result := Exec(Launcher, '--stop', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  PreviousDir: String;
  ResultCode: Integer;
begin
  Result := '';
  if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{B956C8A9-B173-4F90-B275-5196D5E36101}_is1', 'Inno Setup: App Path', PreviousDir) then
    if (CompareText(PreviousDir, ExpandConstant('{app}')) <> 0) and FileExists(PreviousDir + '\BHFYZX.exe') then
      if not Exec(PreviousDir + '\BHFYZX.exe', '--stop', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
      begin
        Result := '请先停止已安装的系统，再更改安装路径。';
        Exit;
      end;
  if not StopInstalledApp() then
    Result := '系统仍在保存数据，请在托盘菜单选择“停止并退出”后重试安装。';
end;

function InitializeUninstall(): Boolean;
begin
  Result := StopInstalledApp();
  if not Result then
    MsgBox('系统仍在保存数据，请停止系统后重试卸载。', mbError, MB_OK);
end;
