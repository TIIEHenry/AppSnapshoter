#include <jni.h>
#include <string>
#include <ftw.h>
#include <sys/stat.h>
#include <climits>
#include <fts.h>
#include <android/log.h>
#include <cerrno>
#include <cstdint>

#define LOG_TAG "NativeLib"
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

bool AppendStandardUtf8(std::string *out, uint32_t code_point) {
    if (code_point <= 0x7F) {
        out->push_back((char) code_point);
    } else if (code_point <= 0x7FF) {
        out->push_back((char) (0xC0 | (code_point >> 6)));
        out->push_back((char) (0x80 | (code_point & 0x3F)));
    } else if (code_point <= 0xFFFF) {
        out->push_back((char) (0xE0 | (code_point >> 12)));
        out->push_back((char) (0x80 | ((code_point >> 6) & 0x3F)));
        out->push_back((char) (0x80 | (code_point & 0x3F)));
    } else if (code_point <= 0x10FFFF) {
        out->push_back((char) (0xF0 | (code_point >> 18)));
        out->push_back((char) (0x80 | ((code_point >> 12) & 0x3F)));
        out->push_back((char) (0x80 | ((code_point >> 6) & 0x3F)));
        out->push_back((char) (0x80 | (code_point & 0x3F)));
    } else {
        return false;
    }
    return true;
}

// Lone surrogates and U+0000 are rejected. BMP code points use the same bytes as modified UTF-8.
bool Utf16ToStandardUtf8(const jchar *chars, jsize length, std::string *out) {
    std::string encoded;
    encoded.reserve((size_t) length);
    for (jsize index = 0; index < length;) {
        const uint32_t unit = chars[index++];
        uint32_t code_point;
        if (unit >= 0xD800 && unit <= 0xDBFF) {
            if (index >= length) {
                return false;
            }
            const uint32_t low = chars[index];
            if (low < 0xDC00 || low > 0xDFFF) {
                return false;
            }
            ++index;
            code_point = 0x10000 + (((unit - 0xD800) << 10) | (low - 0xDC00));
        } else if (unit >= 0xDC00 && unit <= 0xDFFF) {
            return false;
        } else {
            code_point = unit;
        }
        if (code_point == 0 || !AppendStandardUtf8(&encoded, code_point)) {
            return false;
        }
    }
    *out = std::move(encoded);
    return true;
}

class ReleasedStringChars {
public:
    ReleasedStringChars(JNIEnv *env, jstring value)
            : env_(env), value_(value), chars_(env->GetStringChars(value, nullptr)) {}

    ~ReleasedStringChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringChars(value_, chars_);
        }
    }

    const jchar *get() const { return chars_; }

    ReleasedStringChars(const ReleasedStringChars &) = delete;
    ReleasedStringChars &operator=(const ReleasedStringChars &) = delete;

private:
    JNIEnv *env_;
    jstring value_;
    const jchar *chars_;
};

bool JStringToStandardUtf8(JNIEnv *env, jstring value, std::string *out) {
    out->clear();
    ReleasedStringChars chars(env, value);
    if (chars.get() == nullptr) {
        return false;
    }
    const jsize length = env->GetStringLength(value);
    if (env->ExceptionCheck() || !Utf16ToStandardUtf8(chars.get(), length, out)) {
        out->clear();
        return false;
    }
    return true;
}

void AppendModifiedUtf8(std::string *out, uint32_t code_point) {
    if (code_point == 0) {
        out->push_back((char) 0xC0);
        out->push_back((char) 0x80);
        return;
    }
    if (code_point >= 0x10000) {
        const uint32_t adjusted = code_point - 0x10000;
        AppendModifiedUtf8(out, 0xD800 + ((adjusted >> 10) & 0x3FF));
        AppendModifiedUtf8(out, 0xDC00 + (adjusted & 0x3FF));
        return;
    }
    AppendStandardUtf8(out, code_point);
}

bool IsUtf8Continuation(unsigned char byte) {
    return (byte & 0xC0) == 0x80;
}

bool ModifiedUtf8FromStandard(const std::string &utf8, std::string *out) {
    std::string encoded;
    for (size_t index = 0; index < utf8.size();) {
        const auto lead = (unsigned char) utf8[index];
        uint32_t code_point;
        size_t width;
        if (lead <= 0x7F) {
            code_point = lead;
            width = 1;
        } else if ((lead & 0xE0) == 0xC0 && lead >= 0xC2 && index + 1 < utf8.size() &&
                   IsUtf8Continuation((unsigned char) utf8[index + 1])) {
            code_point = ((lead & 0x1F) << 6) | ((unsigned char) utf8[index + 1] & 0x3F);
            width = 2;
        } else if ((lead & 0xF0) == 0xE0 && index + 2 < utf8.size() &&
                   IsUtf8Continuation((unsigned char) utf8[index + 1]) &&
                   IsUtf8Continuation((unsigned char) utf8[index + 2])) {
            code_point = ((lead & 0x0F) << 12) |
                         (((unsigned char) utf8[index + 1] & 0x3F) << 6) |
                         ((unsigned char) utf8[index + 2] & 0x3F);
            width = 3;
            if (code_point < 0x800 || (code_point >= 0xD800 && code_point <= 0xDFFF)) {
                return false;
            }
        } else if ((lead & 0xF8) == 0xF0 && lead <= 0xF4 && index + 3 < utf8.size() &&
                   IsUtf8Continuation((unsigned char) utf8[index + 1]) &&
                   IsUtf8Continuation((unsigned char) utf8[index + 2]) &&
                   IsUtf8Continuation((unsigned char) utf8[index + 3])) {
            code_point = ((lead & 0x07) << 18) |
                         (((unsigned char) utf8[index + 1] & 0x3F) << 12) |
                         (((unsigned char) utf8[index + 2] & 0x3F) << 6) |
                         ((unsigned char) utf8[index + 3] & 0x3F);
            width = 4;
            if (code_point < 0x10000 || code_point > 0x10FFFF) {
                return false;
            }
        } else {
            return false;
        }
        index += width;
        AppendModifiedUtf8(&encoded, code_point);
    }
    *out = std::move(encoded);
    return true;
}

void ThrowTreeSizeFailure(JNIEnv *env, const std::string &path, int error) {
    const std::string message =
            "Failed to calculate tree size for '" + path + "', errno=" + std::to_string(error);
    std::string modified;
    if (!ModifiedUtf8FromStandard(message, &modified)) {
        modified = "Failed to calculate tree size, errno=" + std::to_string(error);
    }
    jclass clazz = env->FindClass("java/io/IOException");
    if (clazz == nullptr) {
        return;
    }
    env->ThrowNew(clazz, modified.c_str());
    env->DeleteLocalRef(clazz);
}

}

namespace NativeNS {
    /**
     * https://cs.android.com/android/platform/superproject/+/android-15.0.0_r23:frameworks/native/cmds/installd/utils.cpp;l=467
     */
    int calculate_tree_size(const std::string &path, int64_t *size) {
        FTS *fts;
        FTSENT *p = nullptr;
        int64_t matchedSize = 0;
        char *argv[] = {(char *) path.c_str(), nullptr};
        if (!(fts = fts_open(argv, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, nullptr))) {
            const int open_errno = errno;
            if (open_errno != ENOENT) {
                ALOGE("Failed to fts_open '%s'", path.c_str());
            }
            errno = open_errno;
            return -1;
        }
        errno = 0;
        bool failed = false;
        while ((p = fts_read(fts)) != nullptr) {
            switch (p->fts_info) {
                case FTS_D:
                case FTS_DEFAULT:
                case FTS_F:
                case FTS_SL:
                case FTS_SLNONE:
                    matchedSize += (p->fts_statp->st_blocks * 512);
                    break;
                case FTS_DNR:
                case FTS_ERR:
                case FTS_NS:
                    if (p->fts_errno != 0) {
                        errno = p->fts_errno;
                    }
                    failed = true;
                    break;
                default:
                    break;
            }
            if (failed) {
                break;
            }
        }
        if (!failed && p == nullptr && errno != 0) {
            failed = true;
        }
        const int saved_errno = errno;
        fts_close(fts);
        if (failed) {
            errno = saved_errno;
            return -1;
        }
        *size += matchedSize;
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_nota_android_io_NativeFileSystem_calculateTreeSize(JNIEnv *env, jobject, jstring path) {
    std::string path_utf8;
    if (!JStringToStandardUtf8(env, path, &path_utf8)) {
        if (!env->ExceptionCheck()) {
            ThrowTreeSizeFailure(env, "<invalid UTF-16>", EINVAL);
        }
        return 0;
    }
    int64_t total_size = 0;
    const int rc = NativeNS::calculate_tree_size(path_utf8, &total_size);
    const int error = errno;
    if (rc != 0) {
        ThrowTreeSizeFailure(env, path_utf8, error);
        return 0;
    }
    return total_size;
}

extern "C" JNIEXPORT jint JNICALL
Java_nota_android_io_NativeFileSystem_getUid(JNIEnv *env, jobject, jstring path) {
    std::string path_utf8;
    if (!JStringToStandardUtf8(env, path, &path_utf8)) {
        return -1;
    }
    struct stat file_stat{};
    if (stat(path_utf8.c_str(), &file_stat) == -1) {
        return -1;
    }
    return (jint) file_stat.st_uid;
}

extern "C" JNIEXPORT jint JNICALL
Java_nota_android_io_NativeFileSystem_getGid(JNIEnv *env, jobject, jstring path) {
    std::string path_utf8;
    if (!JStringToStandardUtf8(env, path, &path_utf8)) {
        return -1;
    }
    struct stat file_stat{};
    if (stat(path_utf8.c_str(), &file_stat) == -1) {
        return -1;
    }
    return (jint) file_stat.st_gid;
}
