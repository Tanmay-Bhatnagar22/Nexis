; Nexis - File Integrity & Host Intrusion Detection Monitor
; Inno Setup Script
; Generates self-contained Windows installer for Nexis

#define MyAppName "Nexis"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Tanmay Bhatnagar"
#define MyAppURL "https://github.com/Tanmay-Bhatnagar22/Nexis"
#define MyAppExeName "Nexis.exe"

[Setup]
AppId={{D5B67C92-6F71-4A6E-9F93-7C384B42F2E1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; Destination directory defaults to Program Files (or local AppData if non-elevated)
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes

; Output settings
OutputDir=..\installer-output
OutputBaseFilename=Nexis-Setup
SetupIconFile=..\packaging\Nexis.ico
Compression=lzma2/max
SolidCompression=yes

; Privileges & Architecture
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog commandline

; Appearance & Uninstaller
WizardStyle=modern
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Nexis - File Integrity & Host Intrusion Detection Monitor
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Complete application image from Phase 6/7
Source: "..\dist\Nexis\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Application icon asset
Source: "..\packaging\Nexis.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\Nexis.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\Nexis.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Parameters: "--help"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
