#include <jni.h>
#include <android/log.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <sys/syscall.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "Tar-JNI"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern int main(int argc, char **argv);

namespace {
// Prepare all JNI-owned data before fork: the child must never re-enter the VM.
int runTar(JNIEnv *env, jstring inputPath, int inputFd, jstring outputPath,
           jstring errorPath, jobjectArray arguments) {
    auto copyString = [env](jstring value) {
        const char *chars = env->GetStringUTFChars(value, nullptr);
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    };
    const std::string in = inputPath == nullptr ? "" : copyString(inputPath);
    const std::string out = copyString(outputPath);
    const std::string err = copyString(errorPath);
    const jsize argc = env->GetArrayLength(arguments);
    std::vector<char *> argv;
    argv.reserve(argc + 1);
    for (jsize i = 0; i < argc; ++i) {
        auto argument = (jstring) env->GetObjectArrayElement(arguments, i);
        argv.push_back(strdup(copyString(argument).c_str()));
        env->DeleteLocalRef(argument);
    }
    argv.push_back(nullptr);
    auto freeArguments = [&argv] { for (char *argument : argv) free(argument); };

    int outFd = open(out.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    int errFd = open(err.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (outFd == -1 || errFd == -1) {
        ALOGE("Failed to open STDOUT/STDERR files.");
        if (outFd != -1) close(outFd);
        if (errFd != -1) close(errFd);
        freeArguments();
        return -1;
    }

    // Capture inherited descriptors before fork; /proc enumeration and JNI
    // calls must stay in the parent. This is also the fallback on older kernels
    // without close_range. The tar child needs only standard descriptors.
    std::vector<int> inherited;
    DIR *directory = opendir("/proc/self/fd");
    if (directory == nullptr) {
        close(outFd);
        close(errFd);
        freeArguments();
        return -1;
    }
    while (dirent *entry = readdir(directory)) {
        char *end = nullptr;
        long fd = strtol(entry->d_name, &end, 10);
        if (*end == '\0' && fd > STDERR_FILENO) inherited.push_back((int) fd);
    }
    closedir(directory);

    pid_t pid = fork();
    if (pid == 0) {
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        if (getppid() == 1) _exit(127);
        if (dup2(outFd, STDOUT_FILENO) == -1 || dup2(errFd, STDERR_FILENO) == -1) {
            _exit(127);
        }
        close(outFd);
        close(errFd);
        int childInput = inputFd;
        if (inputPath != nullptr) childInput = open(in.c_str(), O_RDONLY | O_CLOEXEC);
        if ((inputPath != nullptr || inputFd >= 0) && childInput == -1) _exit(127);
        if (childInput >= 0) {
            if (dup2(childInput, STDIN_FILENO) == -1) _exit(127);
            if (childInput != STDIN_FILENO) close(childInput);
        }
        // In particular, never retain a pipe writer inherited from another
        // operation: it would prevent EOF after the producer fails.
        bool closed = false;
#ifdef __NR_close_range
        closed = syscall(__NR_close_range, 3u, ~0u, 0u) == 0;
#endif
        if (!closed) for (int fd : inherited) close(fd);
        _exit(main(argc, argv.data()));
    }
    close(outFd);
    close(errFd);
    freeArguments();
    if (pid == -1) {
        ALOGE("Failed to fork.");
        return -1;
    }
    int status;
    pid_t waited;
    do {
        waited = waitpid(pid, &status, 0);
    } while (waited == -1 && errno == EINTR);
    if (waited == -1 || !WIFEXITED(status)) return -1;
    return WEXITSTATUS(status);
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
