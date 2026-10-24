#include <jni.h>
#include <android/log.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <dlfcn.h>
#include <cerrno>
#include <climits>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <atomic>
#include <string>
#include <vector>

#define LOG_TAG "Tar-JNI"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::atomic<unsigned long long> gTempSerial{0};

void freeArguments(std::vector<char *> &argv) {
    for (char *argument : argv) free(argument);
    argv.clear();
}

void closeFd(int &fd) {
    if (fd < 0) return;
    close(fd);
    fd = -1;
}

// Java strings are UTF-16. U+0000 and unpaired surrogates are rejected so the
// bytes passed to open/exec are standard UTF-8, including 4-byte supplementary
// characters. BMP code points other than U+0000 keep the same bytes as before.
bool copyString(JNIEnv *env, jstring value, std::string &out) {
    if (value == nullptr) return false;
    const jsize length = env->GetStringLength(value);
    if (length < 0) return false;
    std::vector<jchar> units;
    if (length > 0) units.resize(static_cast<size_t>(length));
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return false;
    if (length > 0) {
        memcpy(units.data(), chars, sizeof(jchar) * static_cast<size_t>(length));
    }
    env->ReleaseStringChars(value, chars);

    std::string encoded;
    encoded.reserve(static_cast<size_t>(length));
    for (jsize index = 0; index < length;) {
        const uint32_t unit = units[static_cast<size_t>(index++)];
        if (unit == 0) return false;
        uint32_t codePoint;
        if (unit >= 0xD800u && unit <= 0xDBFFu) {
            if (index >= length) return false;
            const uint32_t low = units[static_cast<size_t>(index++)];
            if (low < 0xDC00u || low > 0xDFFFu) return false;
            codePoint = 0x10000u + (((unit - 0xD800u) << 10) | (low - 0xDC00u));
        } else if (unit >= 0xDC00u && unit <= 0xDFFFu) {
            return false;
        } else {
            codePoint = unit;
        }
        if (codePoint > 0x10FFFFu) return false;
        if (codePoint <= 0x7Fu) {
            encoded.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FFu) {
            encoded.push_back(static_cast<char>(0xC0u | (codePoint >> 6)));
            encoded.push_back(static_cast<char>(0x80u | (codePoint & 0x3Fu)));
        } else if (codePoint <= 0xFFFFu) {
            encoded.push_back(static_cast<char>(0xE0u | (codePoint >> 12)));
            encoded.push_back(static_cast<char>(0x80u | ((codePoint >> 6) & 0x3Fu)));
            encoded.push_back(static_cast<char>(0x80u | (codePoint & 0x3Fu)));
        } else {
            encoded.push_back(static_cast<char>(0xF0u | (codePoint >> 18)));
            encoded.push_back(static_cast<char>(0x80u | ((codePoint >> 12) & 0x3Fu)));
            encoded.push_back(static_cast<char>(0x80u | ((codePoint >> 6) & 0x3Fu)));
            encoded.push_back(static_cast<char>(0x80u | (codePoint & 0x3Fu)));
        }
    }
    out = std::move(encoded);
    return true;
}

// Absolute path of the tar executable beside this library.
// Android packages only lib*.so, so the executable is installed as libtar-cli.so.
bool resolveTarCli(std::string &tarCli) {
    Dl_info info;
    memset(&info, 0, sizeof(info));
    if (dladdr(reinterpret_cast<const void *>(&resolveTarCli), &info) == 0
            || info.dli_fname == nullptr || info.dli_fname[0] == '\0') {
        return false;
    }
    std::string soPath(info.dli_fname);
    if (soPath.empty() || soPath[0] != '/') {
        char resolved[4096];
        if (realpath(info.dli_fname, resolved) == nullptr) return false;
        soPath = resolved;
    }
    const std::string::size_type slash = soPath.rfind('/');
    if (slash == std::string::npos || soPath[0] != '/') return false;
    tarCli.assign(soPath, 0, slash + 1);
    tarCli.append("libtar-cli.so");
    return true;
}

// Exclusive temp in the same directory as finalPath. O_EXCL plus a pid and a
// monotonic suffix avoids clobbering an existing file. The final path is not opened.
int createExclusiveTemp(const std::string &finalPath, std::string &tempPath) {
    const std::string::size_type slash = finalPath.rfind('/');
    std::string dir;
    if (slash == std::string::npos) {
        dir = ".";
    } else if (slash == 0) {
        dir = "/";
    } else {
        dir = finalPath.substr(0, slash);
    }
    for (int attempt = 0; attempt < 10000; ++attempt) {
        const unsigned long long serial = gTempSerial.fetch_add(1, std::memory_order_relaxed);
        std::string candidate = dir;
        if (candidate.empty() || candidate.back() != '/') candidate.push_back('/');
        candidate.append(".tar-tmp.");
        candidate.append(std::to_string(static_cast<long long>(getpid())));
        candidate.push_back('.');
        candidate.append(std::to_string(serial));
        if (candidate == finalPath) continue;
        const int fd = open(candidate.c_str(), O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0644);
        if (fd >= 0) {
            tempPath = std::move(candidate);
            return fd;
        }
        if (errno != EEXIST) return -1;
    }
    return -1;
}

// Duplicate onto an fd >= 3 before any dup2 onto 0/1/2. Does not close fd.
int raiseFd(int fd) {
    const int raised = fcntl(fd, F_DUPFD_CLOEXEC, 3);
    if (raised < 3) {
        if (raised >= 0) close(raised);
        return -1;
    }
    return raised;
}

// Child-only. Async-signal-safe. Do not use close_range: older Android zygote
// seccomp policies can kill the child with SIGSYS rather than return ENOSYS.
// exec does not replace this loop: write ends opened by other threads may lack
// O_CLOEXEC.
void closeInheritedAboveStderr() {
    rlim_t highest = 65535;
    struct rlimit nofile;
    if (getrlimit(RLIMIT_NOFILE, &nofile) == 0 && nofile.rlim_cur != RLIM_INFINITY) {
        highest = static_cast<rlim_t>(nofile.rlim_cur);
    }
    if (highest < 3) return;
    rlim_t fd = 3;
    for (;;) {
        if (fd > static_cast<rlim_t>(INT_MAX)) return;
        close(static_cast<int>(fd));
        if (fd >= highest) return;
        ++fd;
    }
}

// Rename the archive temp onto the output only after a zero exit. Rename the
// error temp only when it is non-empty. Rename failure unlinks that temp.
int publishResults(bool archiveOk, int exitCode,
                   const std::string &outTemp, const std::string &outPath,
                   const std::string &errTemp, const std::string &errPath) {
    bool renameFailed = false;
    if (!outTemp.empty()) {
        if (archiveOk) {
            if (rename(outTemp.c_str(), outPath.c_str()) != 0) {
                unlink(outTemp.c_str());
                renameFailed = true;
            }
        } else {
            unlink(outTemp.c_str());
        }
    }
    if (!errTemp.empty()) {
        struct stat st;
        memset(&st, 0, sizeof(st));
        if (stat(errTemp.c_str(), &st) == 0 && st.st_size > 0) {
            if (rename(errTemp.c_str(), errPath.c_str()) != 0) {
                unlink(errTemp.c_str());
                renameFailed = true;
            }
        } else {
            unlink(errTemp.c_str());
        }
    }
    if (renameFailed) return -1;
    return exitCode;
}

// Prepare every JNI-owned byte before fork. The child only rearranges
// descriptors and execs tar-cli; it must not re-enter the VM, call main, or free.
int runTar(JNIEnv *env, jstring inputPath, int inputFd, jstring outputPath,
           jstring errorPath, jobjectArray arguments) {
    std::string tarCli;
    if (!resolveTarCli(tarCli)) {
        ALOGE("Failed to resolve tar-cli.");
        return -1;
    }

    std::string in;
    std::string out;
    std::string err;
    std::vector<char *> argv;
    if (inputPath != nullptr && !copyString(env, inputPath, in)) return -1;
    if (!copyString(env, outputPath, out) || !copyString(env, errorPath, err)) return -1;
    if (arguments == nullptr || env->ExceptionCheck()) {
        freeArguments(argv);
        return -1;
    }
    const jsize argc = env->GetArrayLength(arguments);
    if (env->ExceptionCheck()) {
        freeArguments(argv);
        return -1;
    }
    argv.reserve(static_cast<size_t>(argc) + 1);
    for (jsize i = 0; i < argc; ++i) {
        jstring argument = (jstring) env->GetObjectArrayElement(arguments, i);
        if (env->ExceptionCheck()) {
            freeArguments(argv);
            return -1;
        }
        std::string text;
        if (!copyString(env, argument, text)) {
            if (!env->ExceptionCheck() && argument != nullptr) env->DeleteLocalRef(argument);
            freeArguments(argv);
            return -1;
        }
        if (argument != nullptr) env->DeleteLocalRef(argument);
        char *copy = strdup(text.c_str());
        if (copy == nullptr) {
            freeArguments(argv);
            return -1;
        }
        argv.push_back(copy);
    }
    argv.push_back(nullptr);

    int ownedInput = -1;
    if (inputPath != nullptr) {
        ownedInput = open(in.c_str(), O_RDONLY | O_CLOEXEC);
        if (ownedInput == -1) {
            ALOGE("Failed to open input file.");
            freeArguments(argv);
            return -1;
        }
    }

    std::string outTemp;
    std::string errTemp;
    int outRaw = -1;
    int errRaw = -1;
    int raisedOut = -1;
    int raisedErr = -1;
    int raisedIn = -1;
    auto fail = [&]() -> int {
        closeFd(ownedInput);
        closeFd(outRaw);
        closeFd(errRaw);
        closeFd(raisedOut);
        closeFd(raisedErr);
        closeFd(raisedIn);
        if (!outTemp.empty()) unlink(outTemp.c_str());
        if (!errTemp.empty()) unlink(errTemp.c_str());
        freeArguments(argv);
        return -1;
    };

    outRaw = createExclusiveTemp(out, outTemp);
    if (outRaw == -1) {
        ALOGE("Failed to open STDOUT/STDERR files.");
        return fail();
    }
    errRaw = createExclusiveTemp(err, errTemp);
    if (errRaw == -1) {
        ALOGE("Failed to open STDOUT/STDERR files.");
        return fail();
    }

    raisedOut = raiseFd(outRaw);
    if (raisedOut == -1) {
        ALOGE("Failed to move output fd above standard streams.");
        return fail();
    }
    closeFd(outRaw);
    raisedErr = raiseFd(errRaw);
    if (raisedErr == -1) {
        ALOGE("Failed to move error fd above standard streams.");
        return fail();
    }
    closeFd(errRaw);

    const bool hasInput = inputPath != nullptr || inputFd >= 0;
    if (hasInput) {
        const int source = inputPath != nullptr ? ownedInput : inputFd;
        raisedIn = raiseFd(source);
        if (raisedIn == -1) {
            ALOGE("Failed to move input fd above standard streams.");
            return fail();
        }
        if (inputPath != nullptr) closeFd(ownedInput);
    }

    const char *tarCliPath = tarCli.c_str();
    char **childArgv = argv.data();
    char **envp = environ;
    const pid_t pid = fork();
    if (pid == 0) {
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        if (getppid() == 1) _exit(127);
        if (hasInput && dup2(raisedIn, STDIN_FILENO) == -1) _exit(127);
        if (dup2(raisedOut, STDOUT_FILENO) == -1) _exit(127);
        if (dup2(raisedErr, STDERR_FILENO) == -1) _exit(127);
        closeInheritedAboveStderr();
        execve(tarCliPath, childArgv, envp);
        _exit(127);
    }

    closeFd(raisedOut);
    closeFd(raisedErr);
    closeFd(raisedIn);
    freeArguments(argv);
    if (pid < 0) {
        ALOGE("Failed to fork.");
        if (!outTemp.empty()) unlink(outTemp.c_str());
        if (!errTemp.empty()) unlink(errTemp.c_str());
        return -1;
    }

    int status = 0;
    pid_t waited;
    do {
        waited = waitpid(pid, &status, 0);
    } while (waited == -1 && errno == EINTR);
    int exitCode = -1;
    bool archiveOk = false;
    if (waited == pid && WIFEXITED(status)) {
        exitCode = WEXITSTATUS(status);
        archiveOk = exitCode == 0;
    }
    return publishResults(archiveOk, exitCode, outTemp, out, errTemp, err);
}

}

extern "C" JNIEXPORT jint JNICALL
Java_tiiehenry_android_compress_zstd_TarJNI_callCli(
        JNIEnv *env, jobject, jstring inputPath, jstring outputPath,
        jstring errorPath, jobjectArray arguments) {
    return runTar(env, inputPath, -1, outputPath, errorPath, arguments);
}

extern "C" JNIEXPORT jint JNICALL
Java_tiiehenry_android_compress_zstd_TarJNI_callCliWithInputFd(
        JNIEnv *env, jobject, jint inputFd, jstring outputPath,
        jstring errorPath, jobjectArray arguments) {
    if (inputFd < 0) return -1;
    return runTar(env, nullptr, inputFd, outputPath, errorPath, arguments);
}
