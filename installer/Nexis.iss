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
VersionInfoDescription=Nexis - File Integrity & Host Intrusion Detection Monitor (Release 1.0.0-final-icon)
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion={#MyAppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
; Complete application image from Phase 6/7
Source: "..\dist\Nexis\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Application icon asset
Source: "..\packaging\Nexis.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; Dedicated Start Menu program group in user's profile: Programs\Nexis\Nexis.lnk (interactive console)
Name: "{userprograms}\{#MyAppName}\{#MyAppName}"; Filename: "{cmd}"; Parameters: "/k """"{app}\{#MyAppExeName}"""""; WorkingDir: "{app}"; IconFilename: "{app}\Nexis.ico"
; Desktop shortcut on user's actual Desktop (interactive console)
Name: "{userdesktop}\{#MyAppName}"; Filename: "{cmd}"; Parameters: "/k """"{app}\{#MyAppExeName}"""""; WorkingDir: "{app}"; IconFilename: "{app}\Nexis.ico"

[UninstallDelete]
; Clean up the user program group directory if empty after shortcut removal
Type: dirifempty; Name: "{userprograms}\{#MyAppName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Parameters: "--help"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
