#include <jni.h>
#include <string.h>
#include <android/log.h>
#include "pinyinime.h"

#define LOG_TAG "PinyinJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace ime_pinyin;

#define CAND_BUF_LEN 256
static char16 cand_buf[CAND_BUF_LEN];

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeOpenDecoder(
        JNIEnv *env, jclass clazz, jstring fn_sys_dict, jstring fn_usr_dict) {
    const char *sys_dict = env->GetStringUTFChars(fn_sys_dict, nullptr);
    const char *usr_dict = fn_usr_dict ? env->GetStringUTFChars(fn_usr_dict, nullptr) : nullptr;

    bool result = im_open_decoder(sys_dict, usr_dict);

    env->ReleaseStringUTFChars(fn_sys_dict, sys_dict);
    if (usr_dict) {
        env->ReleaseStringUTFChars(fn_usr_dict, usr_dict);
    }
    return static_cast<jboolean>(result);
}

JNIEXPORT void JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeCloseDecoder(
        JNIEnv *env, jclass clazz) {
    im_close_decoder();
}

JNIEXPORT void JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeResetSearch(
        JNIEnv *env, jclass clazz) {
    im_reset_search();
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeSearch(
        JNIEnv *env, jclass clazz, jstring py_str) {
    if (!py_str) return 0;
    const char *py = env->GetStringUTFChars(py_str, nullptr);
    size_t len = strlen(py);
    size_t cand_count = im_search(py, len);
    env->ReleaseStringUTFChars(py_str, py);
    return static_cast<jint>(cand_count);
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeAddLetter(
        JNIEnv *env, jclass clazz, jbyte ch) {
    char c = static_cast<char>(ch);
    size_t decoded_len = 0;
    const char *current_py = im_get_sps_str(&decoded_len);
    char new_buf[256];
    size_t current_len = current_py ? strlen(current_py) : 0;
    if (current_len + 1 >= sizeof(new_buf)) return 0;
    if (current_len > 0) {
        memcpy(new_buf, current_py, current_len);
    }
    new_buf[current_len] = c;
    new_buf[current_len + 1] = '\0';
    return static_cast<jint>(im_search(new_buf, current_len + 1));
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeDelSearch(
        JNIEnv *env, jclass clazz) {
    size_t decoded_len = 0;
    const char *py = im_get_sps_str(&decoded_len);
    if (!py || strlen(py) == 0) return 0;
    return static_cast<jint>(im_delsearch(strlen(py) - 1, false, false));
}

JNIEXPORT jstring JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeGetPyStr(
        JNIEnv *env, jclass clazz, jboolean decoded_only) {
    size_t decoded_len = 0;
    const char *py = im_get_sps_str(&decoded_len);
    if (!py) return env->NewStringUTF("");
    if (decoded_only) {
        char buf[256];
        size_t len = decoded_len < 255 ? decoded_len : 255;
        strncpy(buf, py, len);
        buf[len] = '\0';
        return env->NewStringUTF(buf);
    }
    return env->NewStringUTF(py);
}

JNIEXPORT jstring JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeGetCandidate(
        JNIEnv *env, jclass clazz, jint index) {
    char16 *cand = im_get_candidate(static_cast<size_t>(index), cand_buf, CAND_BUF_LEN);
    if (!cand) return nullptr;
    return env->NewString(reinterpret_cast<const jchar*>(cand), utf16_strlen(cand));
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeChoose(
        JNIEnv *env, jclass clazz, jint index) {
    return static_cast<jint>(im_choose(static_cast<size_t>(index)));
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeGetFixedLen(
        JNIEnv *env, jclass clazz) {
    return static_cast<jint>(im_get_fixed_len());
}

JNIEXPORT jint JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeCancelLastChoice(
        JNIEnv *env, jclass clazz) {
    return static_cast<jint>(im_cancel_last_choice());
}

JNIEXPORT void JNICALL
Java_com_leanbitlab_leantype_cjk_plugin_PinyinDecoderJni_nativeFlushCache(
        JNIEnv *env, jclass clazz) {
    im_flush_cache();
}

} // extern "C"
