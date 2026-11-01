#include <jni.h>
#include <android/log.h>
#include <yara.h>
#include <time.h>
#include <string>
#include <vector>

#define TAG "YaraEngineCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static YR_RULES* g_rules = nullptr;
static bool g_initialized = false;

struct ScanContext {
    std::vector<std::string> matches;
    struct timespec start_time;
};

int yara_callback(YR_SCAN_CONTEXT* context, int message, void* message_data, void* user_data) {
    ScanContext* scan_ctx = static_cast<ScanContext*>(user_data);

    if (message == CALLBACK_MSG_RULE_MATCHING) {
        YR_RULE* rule = (YR_RULE*)message_data;
        scan_ctx->matches.push_back(rule->identifier);
        return CALLBACK_CONTINUE;
    }

    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    
    double elapsed = (now.tv_sec - scan_ctx->start_time.tv_sec) + 
                     (now.tv_nsec - scan_ctx->start_time.tv_nsec) / 1e9;

    if (elapsed > 3.0) {
        LOGE("YARA Scan Timeout triggered (%.2fs)! Aborting to prevent DoS.", elapsed);
        return CALLBACK_ABORT;
    }

    return CALLBACK_CONTINUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_yaracore_YaraScanner_initYaraEngine(JNIEnv *env, jobject thiz, jstring compiledRulesPath) {
    if (g_initialized && g_rules != nullptr) return JNI_TRUE;

    if (yr_initialize() != ERROR_SUCCESS) return JNI_FALSE;

    const char *rules_path = env->GetStringUTFChars(compiledRulesPath, nullptr);
    int result = yr_rules_load(rules_path, &g_rules);
    env->ReleaseStringUTFChars(compiledRulesPath, rules_path);

    if (result != ERROR_SUCCESS) {
        yr_finalize();
        return JNI_FALSE;
    }

    g_initialized = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_protctus_yaracore_YaraScanner_scanMemory(JNIEnv *env, jobject thiz, jbyteArray buffer, jint size) {
    jclass stringClass = env->FindClass("java/lang/String");
    
    if (!g_initialized || g_rules == nullptr) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    // JNI Array Copy Mitigation: Use GetPrimitiveArrayCritical
    jboolean isCopy;
    void* native_buffer = env->GetPrimitiveArrayCritical(buffer, &isCopy);
    if (native_buffer == nullptr) {
        LOGE("Failed to get primitive array critical.");
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    ScanContext scan_ctx;
    clock_gettime(CLOCK_MONOTONIC, &scan_ctx.start_time);

    int scan_result = yr_rules_scan_mem(
        g_rules,
        (uint8_t*)native_buffer,
        size,
        0, 
        yara_callback,
        &scan_ctx,
        0 
    );

    // CRITICAL: Must safely release critical section
    env->ReleasePrimitiveArrayCritical(buffer, native_buffer, JNI_ABORT);

    jobjectArray jMatches = env->NewObjectArray(scan_ctx.matches.size(), stringClass, nullptr);
    for (size_t i = 0; i < scan_ctx.matches.size(); ++i) {
        jstring jStr = env->NewStringUTF(scan_ctx.matches[i].c_str());
        env->SetObjectArrayElement(jMatches, i, jStr);
        env->DeleteLocalRef(jStr);
    }

    return jMatches;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_protctus_yaracore_YaraScanner_scanFile(JNIEnv *env, jobject thiz, jstring filePath) {
    jclass stringClass = env->FindClass("java/lang/String");
    
    if (!g_initialized || g_rules == nullptr) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    const char *native_path = env->GetStringUTFChars(filePath, nullptr);
    if (native_path == nullptr) return env->NewObjectArray(0, stringClass, nullptr);

    ScanContext scan_ctx;
    clock_gettime(CLOCK_MONOTONIC, &scan_ctx.start_time);

    int scan_result = yr_rules_scan_file(
        g_rules,
        native_path,
        0, 
        yara_callback,
        &scan_ctx,
        0 
    );

    env->ReleaseStringUTFChars(filePath, native_path);

    jobjectArray jMatches = env->NewObjectArray(scan_ctx.matches.size(), stringClass, nullptr);
    for (size_t i = 0; i < scan_ctx.matches.size(); ++i) {
        jstring jStr = env->NewStringUTF(scan_ctx.matches[i].c_str());
        env->SetObjectArrayElement(jMatches, i, jStr);
        env->DeleteLocalRef(jStr);
    }

    return jMatches;
}

extern "C" JNIEXPORT void JNICALL
Java_com_protctus_yaracore_YaraScanner_destroyYaraEngine(JNIEnv *env, jobject thiz) {
    if (g_rules != nullptr) {
        yr_rules_destroy(g_rules);
        g_rules = nullptr;
    }
    
    if (g_initialized) {
        yr_finalize();
        g_initialized = false;
    }
}
