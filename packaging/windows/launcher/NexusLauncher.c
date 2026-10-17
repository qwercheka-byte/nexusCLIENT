/*
 * NexusLauncher.exe - native Windows starter for NEXUS Launcher.
 *
 * Starts "<exe dir>\runtime\bin\javaw.exe -jar <exe dir>\nexus-launcher.jar [args]" without a console window.
 * If the bundled runtime is missing it falls back to %JAVA_HOME%\bin\javaw.exe and then to javaw.exe from PATH.
 * The jar stays a separate file next to the exe, so the built-in auto-updater can replace it.
 *
 * Build (MinGW-w64, also works on Linux):
 *   x86_64-w64-mingw32-windres NexusLauncher.rc -O coff -o NexusLauncher.res
 *   x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -o NexusLauncher.exe NexusLauncher.c NexusLauncher.res
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

#define JAR_NAME L"nexus-launcher.jar"
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

/* Finds javaw.exe: bundled runtime, then JAVA_HOME, then PATH. Returns 0 if nothing was found. */
static int find_java(const wchar_t *dir, wchar_t *out, DWORD size) {
    _snwprintf(out, size, L"%ls\\runtime\\bin\\javaw.exe", dir);
    out[size - 1] = 0;
    if (file_exists(out)) return 1;

    wchar_t home[MAX_PATH];
    DWORD n = GetEnvironmentVariableW(L"JAVA_HOME", home, MAX_PATH);
    if (n > 0 && n < MAX_PATH) {
        size_t len = wcslen(home);
        while (len > 0 && (home[len - 1] == L'\\' || home[len - 1] == L'/')) home[--len] = 0;
        _snwprintf(out, size, L"%ls\\bin\\javaw.exe", home);
        out[size - 1] = 0;
        if (file_exists(out)) return 1;
    }

    wchar_t *file_part;
    if (SearchPathW(NULL, L"javaw.exe", NULL, size, out, &file_part) > 0) return 1;
    return 0;
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

    wchar_t jar[MAX_PATH + 32];
    _snwprintf(jar, MAX_PATH + 32, L"%ls\\" JAR_NAME, dir);
    jar[MAX_PATH + 31] = 0;
    if (!file_exists(jar)) {
        error_box(L"Не найден файл nexus-launcher.jar рядом с NexusLauncher.exe.\n"
                  L"Распакуйте архив целиком или переустановите NEXUS Launcher.",
                  L"nexus-launcher.jar was not found next to NexusLauncher.exe.\n"
                  L"Extract the whole archive or reinstall NEXUS Launcher.", jar);
        return 1;
    }

    wchar_t java[MAX_PATH];
    if (!find_java(dir, java, MAX_PATH)) {
        error_box(L"Не найдена Java: нет папки runtime рядом с программой, а Java 21 не установлена.\n"
                  L"Распакуйте архив целиком или переустановите NEXUS Launcher.",
                  L"Java was not found: there is no \"runtime\" folder next to the program and Java 21 is not installed.\n"
                  L"Extract the whole archive or reinstall NEXUS Launcher.", NULL);
        return 1;
    }

    static wchar_t cmd[CMD_MAX];
    const wchar_t *extra = skip_program_name(GetCommandLineW());
    int len = _snwprintf(cmd, CMD_MAX, L"\"%ls\" -jar \"%ls\"%ls%ls",
                         java, jar, *extra ? L" " : L"", extra);
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

    /* If Java dies right away (broken runtime, damaged jar) tell the user instead of silently doing nothing. */
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
