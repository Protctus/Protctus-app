#include <jni.h>
#include <string>
#include <vector>
#include <sys/system_properties.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <android/log.h>
#include <fstream>
#include <sstream>

#define LOG_TAG "ChakravyuhNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Helper function to check if a file exists
bool fileExists(const std::string& path) {
    return access(path.c_str(), F_OK) != -1;
}

// LAYER 2: Root & Su Binaries
bool isRooted() {
    std::vector<std::string> rootPaths = {
        "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
        "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
        "/sbin/magisk", "/data/adb/magisk"
    };

    for (const auto& path : rootPaths) {
        if (fileExists(path)) {
            LOGI("Root detection triggered at %s", path.c_str());
            return true;
        }
    }

    char buildTags[PROP_VALUE_MAX];
    if (__system_property_get("ro.build.tags", buildTags) > 0) {
        std::string tags(buildTags);
        if (tags.find("test-keys") != std::string::npos) {
            LOGI("Root detection triggered by test-keys");
            return true;
        }
    }
    return false;
}

// LAYER 3: Emulator & VM Detection
bool isEmulator() {
    char fingerprint[PROP_VALUE_MAX];
    char model[PROP_VALUE_MAX];

    __system_property_get("ro.build.fingerprint", fingerprint);
    __system_property_get("ro.product.model", model);

    std::string fpStr(fingerprint);
    std::string modelStr(model);

    // Only basic checks - avoid excessive blocking
    if (fpStr.find("generic") == 0 || fpStr.find("unknown") == 0 ||
        modelStr.find("google_sdk") != std::string::npos ||
        modelStr.find("Emulator") != std::string::npos ||
        modelStr.find("Android SDK built for x86") != std::string::npos) {
        
        LOGI("Emulator detection triggered");
        return true;
    }
    return false;
}

// LAYER 4: Debugger Detection (Ptrace)
bool isDebuggerAttached() {
    std::ifstream statusFile("/proc/self/status");
    if (statusFile.is_open()) {
        std::string line;
        while (std::getline(statusFile, line)) {
            if (line.compare(0, 10, "TracerPid:") == 0) {
                std::istringstream iss(line.substr(10));
                int tracerPid;
                if (iss >> tracerPid) {
                    if (tracerPid > 0) {
                        LOGI("Debugger attach detected via TracerPid %d", tracerPid);
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

// LAYER 5: Frida & Xposed Instrumentation (Maps)
bool isFridaOrXposedRunning() {
    std::ifstream mapsFile("/proc/self/maps");
    if (mapsFile.is_open()) {
        std::string line;
        while (std::getline(mapsFile, line)) {
            if (line.find("frida") != std::string::npos || 
                line.find("xposed") != std::string::npos ||
                line.find("edxposed") != std::string::npos) {
                LOGI("Instrumentation detected via /proc/self/maps");
                return true; // Detected instrumentation via loaded libs
            }
        }
    }
    return false;
}

// Check Installer and Dex implicitly from Context within JNI (Layer 6 & 7 equivalent)
// We will call back into JVM to check installer to keep it simple, or just return false
bool isInstallerValid(JNIEnv* env, jobject context) {
    if (!context) return true; // fallback
    
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerId = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID getPackageNameId = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");

    jobject pm = env->CallObjectMethod(context, getPackageManagerId);
    jstring packageName = (jstring) env->CallObjectMethod(context, getPackageNameId);

    jclass pmClass = env->GetObjectClass(pm);
    jmethodID getInstallerPackageNameId = env->GetMethodID(pmClass, "getInstallerPackageName", "(Ljava/lang/String;)Ljava/lang/String;");

    jstring installerStr = (jstring) env->CallObjectMethod(pm, getInstallerPackageNameId, packageName);
    
    if (installerStr == nullptr) {
        // No installer (sideloaded)
        return false;
    }

    const char* installerChars = env->GetStringUTFChars(installerStr, nullptr);
    std::string installer(installerChars);
    env->ReleaseStringUTFChars(installerStr, installerChars);

    if (installer == "com.android.vending") {
        return true;
    }

    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_app_security_ChakravyuhNative_verifyEnvironment(JNIEnv* env, jobject thiz, jobject context, jboolean isDebugBuild) {
    // Run all checks
    bool root = isRooted();
    bool emulator = isEmulator();
    bool debugger = isDebuggerAttached();
    bool frida = isFridaOrXposedRunning();
    
    // ALWAYS block active reverse engineering — even in debug builds
    if (debugger) {
        LOGE("SECURITY VIOLATION: Debugger attached!");
        return JNI_TRUE;
    }

    if (frida) {
        LOGE("SECURITY VIOLATION: Dynamic Instrumentation Detected!");
        return JNI_TRUE;
    }
    
    // P0 FIX: In RELEASE builds, root and emulator are security violations.
    // In DEBUG builds, allow them for developer testing.
    if (isDebugBuild == JNI_FALSE) {
        if (root) {
            LOGE("SECURITY VIOLATION [RELEASE]: Device is rooted.");
            return JNI_TRUE;
        }
        if (emulator) {
            LOGE("SECURITY VIOLATION [RELEASE]: Running on emulator.");
            return JNI_TRUE;
        }
    } else {
        if (root) LOGI("Device is Rooted — allowed in DEBUG build.");
        if (emulator) LOGI("Device is Emulator — allowed in DEBUG build.");
    }

    return JNI_FALSE;
}
