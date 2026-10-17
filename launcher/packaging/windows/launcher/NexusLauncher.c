/*
 * "Nexus Launcher.exe" - native Windows starter for NEXUS Launcher.
 *
 * Starts "<exe dir>\runtime\bin\javaw.exe -cp <exe dir>\app dev.nexus.launcher.Main [args]" without a console window.
 *   runtime\  Java 21 runtime image with the JavaFX modules (made with jlink)
 *   app\      launcher classes and resources as plain files (no jar files)
 * The built-in auto-updater swaps app\ with a new version (app.new -> app, old one kept as app.old until the
 * swap succeeded). If an update was interrupted and app\ is missing, the starter restores app.old.
 *
 * Build (MinGW-w64, also works on Linux):
 *   x86_64-w64-mingw32-windres NexusLauncher.rc -O coff -o NexusLauncher.res
 *   x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -o "Nexus Launcher.exe" NexusLauncher.c NexusLauncher.res
 */
#define WIN32_LEAN_AND_MEAN
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <wchar.h>

#define MAIN_CLASS L"dev.nexus.launcher.Main"
#define MAIN_CLASS_FILE L"dev\\nexus\\launcher\\Main.class"
#define CMD_MAX 32768

static int is_russian(void) {
    return PRIMARYLANGID(GetUserDefaultUILanguage()) == LANG_RUSSIAN;
}

static void error_box(const wchar_t *ru, const wchar_t *en, const wchar_t *detail) {
    wchar_t text[2048];
    _snwprintf(text, 2048, L"%ls%ls%ls", is_russian() ? ru : en, detail ? L"\n\n" : L"", detail ? detail : L"");
    text[2047] = 0;
    MessageBoxW(NULL, text, L"NEXUS Launcher", MB_OK | MB_ICONERROR);
}

static int file_exists(const wchar_t *path) {
    DWORD a = GetFileAttributesW(path);
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

static int dir_exists(const wchar_t *path) {
    DWORD a = GetFileAttributesW(path);
    return a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY);
}

/* True when <dir>\<sub> contains the launcher main class. */
static int has_main_class(const wchar_t *dir, const wchar_t *sub) {
    wchar_t path[MAX_PATH + 96];
    _snwprintf(path, MAX_PATH + 96, L"%ls\\%ls\\" MAIN_CLASS_FILE, dir, sub);
    path[MAX_PATH + 95] = 0;
    return file_exists(path);
}

/* Skips argv[0] of a Windows command line (quoted or not) and the following spaces. */
static const wchar_t *skip_program_name(const wchar_t *cmd) {
    if (*cmd == L'"') {
        cmd++;
        while (*cmd && *cmd != L'"') cmd++;
        if (*cmd == L'"') cmd++;
    } else {
        while (*cmd && *cmd != L' ' && *cmd != L'\t') cmd++;
    }
    while (*cmd == L' ' || *cmd == L'\t') cmd++;
    return cmd;
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR args, int show) {
    (void) inst; (void) prev; (void) args; (void) show;

    wchar_t dir[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        error_box(L"Не удалось определить папку программы (слишком длинный путь?).",
                  L"Cannot determine the program folder (path too long?).", NULL);
        return 1;
    }
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;

    wchar_t app[MAX_PATH + 8];
    _snwprintf(app, MAX_PATH + 8, L"%ls\\app", dir);
    app[MAX_PATH + 7] = 0;
    if (!has_main_class(dir, L"app") && has_main_class(dir, L"app.old")) {
        /* an update was interrupted between "app -> app.old" and "app.new -> app": go back to the old version */
        wchar_t old[MAX_PATH + 16];
        _snwprintf(old, MAX_PATH + 16, L"%ls\\app.old", dir);
        old[MAX_PATH + 15] = 0;
        if (dir_exists(app)) RemoveDirectoryW(app);
        MoveFileW(old, app);
    }
    if (!has_main_class(dir, L"app")) {
        error_box(L"Не найдена папка app рядом с Nexus Launcher.exe.\n"
                  L"Распакуйте архив целиком или переустановите NEXUS Launcher.",
                  L"The app folder was not found next to Nexus Launcher.exe.\n"
                  L"Extract the whole archive or reinstall NEXUS Launcher.", app);
        return 1;
    }

    wchar_t java[MAX_PATH + 32];
    _snwprintf(java, MAX_PATH + 32, L"%ls\\runtime\\bin\\javaw.exe", dir);
    java[MAX_PATH + 31] = 0;
    if (!file_exists(java)) {
        error_box(L"Не найдена встроенная Java (runtime\\bin\\javaw.exe).\n"
                  L"Распакуйте архив целиком или переустановите NEXUS Launcher.",
                  L"The bundled Java runtime (runtime\\bin\\javaw.exe) was not found.\n"
                  L"Extract the whole archive or reinstall NEXUS Launcher.", java);
        return 1;
    }

    static wchar_t cmd[CMD_MAX];
    const wchar_t *extra = skip_program_name(GetCommandLineW());
    int len = _snwprintf(cmd, CMD_MAX, L"\"%ls\" -cp \"%ls\" " MAIN_CLASS L"%ls%ls",
                         java, app, *extra ? L" " : L"", extra);
    if (len < 0 || len >= CMD_MAX) {
        error_box(L"Слишком длинная командная строка.", L"The command line is too long.", NULL);
        return 1;
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof si);
    si.cb = sizeof si;
    ZeroMemory(&pi, sizeof pi);
    if (!CreateProcessW(java, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        wchar_t detail[MAX_PATH + 64];
        _snwprintf(detail, MAX_PATH + 64, L"%ls\n(error %lu)", java, GetLastError());
        detail[MAX_PATH + 63] = 0;
        error_box(L"Не удалось запустить Java.", L"Cannot start Java.", detail);
        return 1;
    }

    /* If Java dies right away (broken runtime, damaged app folder) tell the user instead of silently doing nothing. */
    DWORD code = STILL_ACTIVE;
    if (WaitForSingleObject(pi.hProcess, 4000) == WAIT_OBJECT_0 && GetExitCodeProcess(pi.hProcess, &code) && code != 0) {
        wchar_t detail[64];
        _snwprintf(detail, 64, L"exit code %lu", code);
        detail[63] = 0;
        error_box(L"NEXUS Launcher завершился с ошибкой сразу после запуска.\n"
                  L"Подробности — в файле logs\\launcher.log в папке %APPDATA%\\.nexus-launcher.",
                  L"NEXUS Launcher exited with an error right after start.\n"
                  L"See logs\\launcher.log in %APPDATA%\\.nexus-launcher.", detail);
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return code == STILL_ACTIVE ? 0 : (int) code;
}
