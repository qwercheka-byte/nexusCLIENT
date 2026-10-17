; NEXUS Launcher installer (NSIS 3, Unicode). Built by ./gradlew installerWin, which passes:
;   /DVERSION=1.0.1  /DSTAGE=<folder with NexusLauncher.exe, nexus-launcher.jar, runtime\, LICENSE>  /DOUTFILE=<setup.exe>
;
; Installs for the current user only (no administrator rights needed) into %LOCALAPPDATA%\Programs\NEXUS Launcher,
; so the built-in auto-updater can replace nexus-launcher.jar without UAC.
; Games, accounts and settings live in %APPDATA%\.nexus-launcher and are NOT touched by (un)installing,
; unless the user explicitly agrees during uninstallation.

; 64-bit installer: the launcher ships an x64 Java runtime anyway
Target amd64-unicode
ManifestDPIAware true
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 64

!ifndef VERSION
  !define VERSION "0.0.0"
!endif
!ifndef STAGE
  !error "STAGE is not defined"
!endif
!ifndef OUTFILE
  !define OUTFILE "NexusLauncher-${VERSION}-windows-x64-setup.exe"
!endif

!define APP_NAME "NEXUS Launcher"
!define APP_EXE "NexusLauncher.exe"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\NexusLauncher"

Name "${APP_NAME}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\NEXUS Launcher"
InstallDirRegKey HKCU "${UNINST_KEY}" "InstallLocation"
BrandingText "${APP_NAME} ${VERSION}"

VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1033 "ProductName" "${APP_NAME}"
VIAddVersionKey /LANG=1033 "FileDescription" "${APP_NAME} Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1033 "CompanyName" "NEXUS"
VIAddVersionKey /LANG=1033 "LegalCopyright" "MIT License"

!include "MUI2.nsh"
!include "LogicLib.nsh"

!define MUI_ICON "nexus.ico"
!define MUI_UNICON "nexus.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "installer-side.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "installer-side.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "installer-header.bmp"
!define MUI_HEADERIMAGE_UNBITMAP "installer-header.bmp"
!define MUI_ABORTWARNING
!define MUI_COMPONENTSPAGE_NODESC

!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "$(RunNow)"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${STAGE}\LICENSE"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; the first language is the fallback; NSIS picks the system language automatically when it is in the list
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Russian"

LangString RunNow ${LANG_ENGLISH} "Start NEXUS Launcher"
LangString RunNow ${LANG_RUSSIAN} "Запустить NEXUS Launcher"
LangString SecMain ${LANG_ENGLISH} "NEXUS Launcher (required)"
LangString SecMain ${LANG_RUSSIAN} "NEXUS Launcher (обязательно)"
LangString SecDesktop ${LANG_ENGLISH} "Desktop shortcut"
LangString SecDesktop ${LANG_RUSSIAN} "Ярлык на рабочем столе"
LangString SecStart ${LANG_ENGLISH} "Start menu shortcut"
LangString SecStart ${LANG_RUSSIAN} "Ярлык в меню Пуск"
LangString StillRunning ${LANG_ENGLISH} "NEXUS Launcher is running. Close it (and the games started from it) and press Retry."
LangString StillRunning ${LANG_RUSSIAN} "NEXUS Launcher запущен. Закройте его (и запущенные из него игры) и нажмите «Повторить»."
LangString AskData ${LANG_ENGLISH} "Also delete the launcher data?$\n$\n$APPDATA\.nexus-launcher$\n$\nThis folder contains your instances (worlds, mods, screenshots), accounts and settings. Choose No to keep them."
LangString AskData ${LANG_RUSSIAN} "Удалить также данные лаунчера?$\n$\n$APPDATA\.nexus-launcher$\n$\nВ этой папке ваши сборки (миры, моды, скриншоты), аккаунты и настройки. Нажмите «Нет», чтобы сохранить их."

; Waits until no running launcher/game uses the installation. Java opens jars and runs javaw.exe without
; FILE_SHARE_DELETE, so renaming fails while they are in use - this is exactly what an overwrite would break.
!macro TRY_RENAME PATH
  ${If} ${FileExists} "${PATH}"
    ClearErrors
    Rename "${PATH}" "${PATH}.nexuscheck"
    ${If} ${Errors}
      StrCpy $1 "busy"
    ${Else}
      Rename "${PATH}.nexuscheck" "${PATH}"
    ${EndIf}
  ${EndIf}
!macroend
!macro WAIT_UNLOCKED
  ${Do}
    StrCpy $1 ""
    !insertmacro TRY_RENAME "$INSTDIR\nexus-launcher.jar"
    !insertmacro TRY_RENAME "$INSTDIR\runtime"
    ${If} $1 == ""
      ${Break}
    ${EndIf}
    MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "$(StillRunning)" /SD IDCANCEL IDRETRY +2
    Quit
  ${Loop}
!macroend

Section "$(SecMain)" SecMainId
  SectionIn RO
  SetShellVarContext current
  !insertmacro WAIT_UNLOCKED
  SetOutPath "$INSTDIR"
  ; a fresh runtime on every (re)install: no stale files from an older JRE
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\nexus-launcher.jar.old"
  File "${STAGE}\${APP_EXE}"
  File "${STAGE}\nexus-launcher.jar"
  File "${STAGE}\LICENSE"
  File /r "${STAGE}\runtime"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "NEXUS"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE},0"
  WriteRegStr HKCU "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "${UNINST_KEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegStr HKCU "${UNINST_KEY}" "URLInfoAbout" "https://github.com/qwercheka-byte/nexusCLIENT"
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoRepair" 1
  ; approximate size in KB for "Apps & features"
  WriteRegDWORD HKCU "${UNINST_KEY}" "EstimatedSize" 170000
SectionEnd

Section "$(SecStart)" SecStartId
  SetShellVarContext current
  CreateShortCut "$SMPROGRAMS\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
SectionEnd

Section "$(SecDesktop)" SecDesktopId
  SetShellVarContext current
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  !insertmacro WAIT_UNLOCKED
  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}.lnk"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\${APP_EXE}"
  Delete "$INSTDIR\nexus-launcher.jar"
  Delete "$INSTDIR\nexus-launcher.jar.old"
  Delete "$INSTDIR\LICENSE"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${UNINST_KEY}"
  ${If} ${FileExists} "$APPDATA\.nexus-launcher\*.*"
    MessageBox MB_YESNO|MB_ICONQUESTION|MB_DEFBUTTON2 "$(AskData)" /SD IDNO IDNO +2
    RMDir /r "$APPDATA\.nexus-launcher"
  ${EndIf}
SectionEnd
