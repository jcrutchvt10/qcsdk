/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifdef _WIN32

#include "find_java.h"
#include <shlobj.h>

extern bool gDebug;

// Check whether we can find $PATH/java.exe
static bool checkPath(CPath *inOutPath) {
    inOutPath->addPath("java.exe");

    bool result = false;
    PVOID oldWow64Value = disableWow64FsRedirection();
    if (inOutPath->fileExists()) {
        // Make sure we can actually run "java -version".
        CString cmd;
        cmd.setf("\"%s\" -version", inOutPath->cstr());
        int code = execWait(cmd.cstr());
        result = (code == 0);
    }

    revertWow64FsRedirection(oldWow64Value);
    return result;
}

// Check whether we can find $PATH/bin/java.exe
static bool checkBinPath(CPath *inOutPath) {
    inOutPath->addPath("bin");
    return checkPath(inOutPath);
}

// Search java.exe in the environment
bool findJavaInEnvPath(CPath *outJavaPath) {
    SetLastError(0);

    const char* envPath = getenv("JAVA_HOME");
    if (envPath != NULL) {
        CPath p(envPath);
        if (checkBinPath(&p)) {
            if (gDebug) msgBox("Java found via JAVA_HOME: %s", p.cstr());
            *outJavaPath = p;
            return true;
        }
    }

    envPath = getenv("PATH");
    if (!envPath) return false;

    CArray<CString> *paths = CString(envPath).split(';');
    for(int i = 0; i < paths->size(); i++) {
        CPath p((*paths)[i].cstr());
        if (checkPath(&p)) {
            if (gDebug) msgBox("Java found via env PATH: %s", p.cstr());
            *outJavaPath = p;
            delete paths;
            return true;
        }
    }

    delete paths;
    return false;
}

// --------------

bool getRegValue(const char *keyPath, const char *keyName, REGSAM access, CString *outValue) {
    HKEY key;
    LSTATUS status = RegOpenKeyExA(
        HKEY_LOCAL_MACHINE,         // hKey
        keyPath,                    // lpSubKey
        0,                          // ulOptions
        KEY_READ | access,          // samDesired,
        &key);                      // phkResult
    if (status == ERROR_SUCCESS) {

        LSTATUS ret = ERROR_MORE_DATA;
        DWORD size = 4096; // MAX_PATH is 260, so 4 KB should be good enough
        char* buffer = (char*) malloc(size);

        while (ret == ERROR_MORE_DATA && size < (1<<16) /*64 KB*/) {
            ret = RegQueryValueExA(
                key,                // hKey
                keyName,            // lpValueName
                NULL,               // lpReserved
                NULL,               // lpType
                (LPBYTE) buffer,    // lpData
                &size);             // lpcbData

            if (ret == ERROR_MORE_DATA) {
                size *= 2;
                buffer = (char*) realloc(buffer, size);
            } else {
                buffer[size] = 0;
            }
        }

        if (ret != ERROR_MORE_DATA) outValue->set(buffer);

        free(buffer);
        RegCloseKey(key);

        return (ret != ERROR_MORE_DATA);
    }

    return false;
}

bool exploreJavaRegistry(const char *entry, REGSAM access, CPath *outJavaPath) {

    // Let's visit HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Runtime Environment [CurrentVersion]
    CPath subKey("SOFTWARE\\JavaSoft\\");
    subKey.addPath(entry);

    CString currVersion;
    if (getRegValue(subKey.cstr(), "CurrentVersion", access, &currVersion)) {
        // CurrentVersion should be something like "1.7".
        // We want to read HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Runtime Environment\1.7 [JavaHome]
        subKey.addPath(currVersion);
        CPath javaHome;
        if (getRegValue(subKey.cstr(), "JavaHome", access, &javaHome)) {
            if (checkBinPath(&javaHome)) {
                *outJavaPath = javaHome;
                return true;
            }
        }
    }

    return false;
}

bool findJavaInRegistry(CPath *outJavaPath) {
    // We'll do the registry test 3 times: first using the default mode,
    // then forcing the use of the 32-bit registry then forcing the use of
    // 64-bit registry. On Windows 2k, the 2 latter will fail since the
    // flags are not supported. On a 32-bit OS the 64-bit is obviously
    // useless and the 2 first test should be equivalent so we just
    // need the first case.

    // Check the JRE first, then the JDK.
    if (exploreJavaRegistry("Java Runtime Environment", 0, outJavaPath) ||
            exploreJavaRegistry("Java Development Kit", 0, outJavaPath)) {
        return true;
    }

    // Check the real sysinfo state (not the one hidden by WOW64) for x86
    SYSTEM_INFO sysInfo;
    GetNativeSystemInfo(&sysInfo);

    if (sysInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) {
        if (exploreJavaRegistry("Java Runtime Environment", KEY_WOW64_32KEY, outJavaPath) ||
                exploreJavaRegistry("Java Development Kit", KEY_WOW64_32KEY, outJavaPath)) {
            return true;
        }

        if (exploreJavaRegistry("Java Runtime Environment", KEY_WOW64_64KEY, outJavaPath) ||
                exploreJavaRegistry("Java Development Kit", KEY_WOW64_64KEY, outJavaPath)) {
            return true;
        }
    }

    return false;
}

// --------------

static bool checkProgramFiles(CPath *outJavaPath) {

    char programFilesPath[MAX_PATH + 1];
    HRESULT result = SHGetFolderPathA(
        NULL,                       // hwndOwner
        CSIDL_PROGRAM_FILES,        // nFolder
        NULL,                       // hToken
        SHGFP_TYPE_CURRENT,         // dwFlags
        programFilesPath);          // pszPath
    if (FAILED(result)) return false;

    CPath path(programFilesPath);
    path.addPath("Java");

    // Do we have a C:\\Program Files\\Java directory?
    if (!path.dirExists()) return false;

    CPath glob(path);
    glob.addPath("j*");

    bool found = false;
    WIN32_FIND_DATAA findData;
    HANDLE findH = FindFirstFileA(glob.cstr(), &findData);
    if (findH == INVALID_HANDLE_VALUE) return false;
    do {
        if ((findData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
            CPath temp(path);
            temp.addPath(findData.cFileName);
            // Check C:\\Program Files[x86]\\Java\\{jdk,jre}*\\bin\\java.exe
            if (checkBinPath(&temp)) {
                found = true;
                *outJavaPath = temp;
            }
        }
    } while (!found && FindNextFileA(findH, &findData) != 0);
    FindClose(findH);

    return found;
}

bool findJavaInProgramFiles(CPath *outJavaPath) {
    // Check the C:\\Program Files (x86) directory
    // With WOW64 fs redirection in place by default, we should get the x86
    // version on a 64-bit OS since this app is a 32-bit itself.
    if (checkProgramFiles(outJavaPath)) return true;

    // Check the real sysinfo state (not the one hidden by WOW64) for x86
    SYSTEM_INFO sysInfo;
    GetNativeSystemInfo(&sysInfo);

    if (sysInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) {
        // On a 64-bit OS, try again by disabling the fs redirection so
        // that we can try the real C:\\Program Files directory.
        PVOID oldWow64Value = disableWow64FsRedirection();
        bool found = checkProgramFiles(outJavaPath);
        revertWow64FsRedirection(oldWow64Value);
        return found;
    }

    return false;
}

// --------------

static bool getJavaVersion(CPath &javaPath, CString *version) {
    bool result = false;

    // Run "java -version".
    // TODO: capture output to string.
    CString cmd;
    cmd.setf("\"%s\" -version", javaPath.cstr());

    SECURITY_ATTRIBUTES saAttr;
    STARTUPINFO           startup;
    PROCESS_INFORMATION   pinfo;

    // Want to inherit pipe handle
    ZeroMemory(&saAttr, sizeof(saAttr));
    saAttr.nLength = sizeof(SECURITY_ATTRIBUTES); 
    saAttr.bInheritHandle = TRUE; 
    saAttr.lpSecurityDescriptor = NULL; 

    // Create pipe for stdout
    HANDLE stdoutPipeRd, stdoutPipeWt;
    if (!CreatePipe(
            &stdoutPipeRd,      // hReadPipe,
            &stdoutPipeWt,      // hWritePipe,
            &saAttr,            // lpPipeAttributes,
            0)) {               // nSize (0=default buffer size)
        displayLastError("CreatePipe failed: ");
        return false;
    }


    ZeroMemory(&pinfo, sizeof(pinfo));

    ZeroMemory(&startup, sizeof(startup));
    startup.cb          = sizeof(startup);
    startup.dwFlags     = STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE|SW_MINIMIZE;

    int ret = CreateProcessA(
            NULL,                                       /* program path */
            (LPSTR) cmd,                                /* command-line */
            NULL,                  /* process handle is not inheritable */
            NULL,                   /* thread handle is not inheritable */
            TRUE,                          /* yes, inherit some handles */
            CREATE_NO_WINDOW,                /* we don't want a console */
            NULL,                     /* use parent's environment block */
            NULL,                    /* use parent's starting directory */
            &startup,                 /* startup info, i.e. std handles */
            &pinfo);

    if (ret) {
        WaitForSingleObject(pinfo.hProcess, INFINITE);

        DWORD exitCode;
        if (GetExitCodeProcess(pinfo.hProcess, &exitCode)) {
            // this should not return STILL_ACTIVE (259)
            result = exitCode == 0;
        }
        CloseHandle(pinfo.hProcess);
        CloseHandle(pinfo.hThread);
    }
    CloseHandle(stdoutPipeRd);
    CloseHandle(stdoutPipeWt);

    if (result) {
        // TODO
        // Parse output of "java -version".
        // It should be something like:
        //   java version "1.6.0_29"
        // (including the quotes.)
    }

    return result;
}


#endif /* _WIN32 */
