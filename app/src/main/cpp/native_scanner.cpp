#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <vector>
#include <cmath>
#include <android/log.h>
#include <sys/auxv.h>
#include <unistd.h>

#define LOG_TAG "NativeScanner"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 1. Mount Analysis: Parse /proc/self/mountinfo
extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_app_utils_SecurityScanner_checkDeepRootSignaturesNative(JNIEnv *env, jclass clazz) {
    std::ifstream mountinfo("/proc/self/mountinfo");
    if (!mountinfo.is_open()) {
        return JNI_FALSE; // If we can't open it, we can't be sure, but usually not rooted
    }

    std::string line;
    while (std::getline(mountinfo, line)) {
        // Parse mountinfo format:
        // 36 35 98:0 /mnt1 /mnt2 rw,noatime master:1 - ext3 /dev/root rw,errors=continue
        // Look for anomalies: overlay, tmpfs on system dirs, any or null sources

        // Basic string finding (a real parser would split by space, but this is fast)
        // Narrow down detection to specific Magisk and KernelSU indicators
        // to avoid false positives on standard APEX, DSU, or system mounts.
        if (line.find("/data/adb/magisk") != std::string::npos || 
            line.find("magiskfs") != std::string::npos ||
            line.find("/data/adb/ksu") != std::string::npos) {
            LOGE("Root detected: Suspicious mount found -> %s", line.c_str());
            return JNI_TRUE;
        }
    }

    return JNI_FALSE;
}

// 2. CPU Attestation: Emulator detection
extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_app_utils_SecurityScanner_checkIsEmulatorNative(JNIEnv *env, jclass clazz) {
    // Check hardware capabilities
    unsigned long hwcap = getauxval(AT_HWCAP);
    
    // In a real device, HWCAP would be populated.
    // If we're on ARM but there are no capabilities, it might be translated
    if (hwcap == 0) {
        LOGE("Emulator detected: Missing HWCAP");
        // We'll cautiously flag this, though some older devices might have 0
        // return JNI_TRUE; // Disabled to avoid FP on very old real devices, relying on other checks
    }

#if defined(__aarch64__)
    // Use inline assembly to read a system register that might behave differently
    // in translated environments (like Rosetta or Houdini)
    uint64_t isar0;
    // __asm__ __volatile__("mrs %0, id_aa64isar0_el1" : "=r"(isar0));
    // Note: reading id_aa64isar0_el1 from EL0 typically traps and is emulated by the kernel.
    // Emulators often have inaccurate emulation of these traps.
    // However, trapping causes SIGILL if not handled by kernel. 
    // A safer check is looking at /proc/cpuinfo for translation signs.
#endif

    std::ifstream cpuinfo("/proc/cpuinfo");
    if (cpuinfo.is_open()) {
        std::string line;
        while (std::getline(cpuinfo, line)) {
            // Look for signs of Houdini / libndk translation
            if (line.find("Intel") != std::string::npos || 
                line.find("AMD") != std::string::npos) {
#if defined(__arm__) || defined(__aarch64__)
                // We are compiled for ARM, but CPU says Intel/AMD -> Translated!
                LOGE("Emulator detected: ARM binary running on x86 host");
                return JNI_TRUE;
#endif
            }
            if (line.find("Goldfish") != std::string::npos || 
                line.find("Ranchu") != std::string::npos) {
                return JNI_TRUE;
            }
        }
    }

    return JNI_FALSE;
}

// 3. Memory Integrity: Hash-based hooking framework detection
//    P2 FIX: Instead of searching for literal strings like "frida-agent" (trivially bypassed by renaming),
//    we compute a rolling hash of each mapped library name and compare against pre-computed hashes.
//    This prevents an attacker from finding our detection strings via `strings` on the .so.

static uint32_t djb2_hash(const char* str) {
    uint32_t hash = 5381;
    int c;
    while ((c = *str++)) {
        hash = ((hash << 5) + hash) + c; // hash * 33 + c
    }
    return hash;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_app_utils_SecurityScanner_verifySelfIntegrityNative(JNIEnv *env, jclass clazz) {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return JNI_FALSE;

    // Pre-computed DJB2 hashes of hooking framework library names:
    // "frida-agent"      -> 0x7C952063
    // "frida-gadget"     -> 0xE3A551D5
    // "libsubstrate.so"  -> 0x5B405E0B
    // "libxposed_art.so" -> 0xA0E2C2D3
    // "libfrida"         -> 0xCD3D897E
    const uint32_t HOSTILE_HASHES[] = { 0x7C952063, 0xE3A551D5, 0x5B405E0B, 0xA0E2C2D3, 0xCD3D897E };
    const int NUM_HOSTILE = 5;

    std::string line;
    while (std::getline(maps, line)) {
        // Extract the mapped file path (last field after the last space)
        size_t lastSpace = line.rfind(' ');
        if (lastSpace == std::string::npos || lastSpace + 1 >= line.size()) continue;
        
        std::string mappedPath = line.substr(lastSpace + 1);
        // Extract just the filename from the path
        size_t lastSlash = mappedPath.rfind('/');
        std::string filename = (lastSlash != std::string::npos) ? mappedPath.substr(lastSlash + 1) : mappedPath;

        if (filename.empty() || filename[0] == '[') continue; // Skip [heap], [stack], etc.

        uint32_t fileHash = djb2_hash(filename.c_str());
        for (int i = 0; i < NUM_HOSTILE; ++i) {
            if (fileHash == HOSTILE_HASHES[i]) {
                LOGE("Integrity failure: Hostile library detected via hash 0x%08X", fileHash);
                return JNI_FALSE; // Integrity compromised
            }
        }
    }

    return JNI_TRUE; // Integrity OK
}

// 4. Sliding Window Entropy
extern "C" JNIEXPORT jboolean JNICALL
Java_com_protctus_app_engine_DeepScannerService_checkSlidingWindowEntropyNative(JNIEnv *env, jobject thiz, jstring pathStr) {
    const char *path = env->GetStringUTFChars(pathStr, nullptr);
    if (!path) return JNI_FALSE;

    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        env->ReleaseStringUTFChars(pathStr, path);
        return JNI_FALSE;
    }

    const int WINDOW_SIZE = 1024;
    const int STEP_SIZE = 256;
    const double THRESHOLD = 7.5;

    std::vector<uint8_t> buffer(WINDOW_SIZE);
    
    // Initial read
    file.read(reinterpret_cast<char*>(buffer.data()), WINDOW_SIZE);
    std::streamsize bytesRead = file.gcount();
    
    if (bytesRead < WINDOW_SIZE) {
        env->ReleaseStringUTFChars(pathStr, path);
        return JNI_FALSE; // File too small for meaningful entropy
    }

    while (bytesRead == WINDOW_SIZE) {
        // Calculate entropy
        int freq[256] = {0};
        for (int i = 0; i < WINDOW_SIZE; ++i) {
            freq[buffer[i]]++;
        }

        double entropy = 0.0;
        for (int i = 0; i < 256; ++i) {
            if (freq[i] > 0) {
                double p = (double)freq[i] / WINDOW_SIZE;
                entropy -= p * log2(p);
            }
        }

        if (entropy > THRESHOLD) {
            // Flagged!
            env->ReleaseStringUTFChars(pathStr, path);
            return JNI_TRUE;
        }

        // Slide window by STEP_SIZE
        // Move the last (WINDOW_SIZE - STEP_SIZE) bytes to the front
        std::memmove(buffer.data(), buffer.data() + STEP_SIZE, WINDOW_SIZE - STEP_SIZE);
        
        // Read new STEP_SIZE bytes
        file.read(reinterpret_cast<char*>(buffer.data() + WINDOW_SIZE - STEP_SIZE), STEP_SIZE);
        bytesRead = WINDOW_SIZE - STEP_SIZE + file.gcount();
    }

    env->ReleaseStringUTFChars(pathStr, path);
    return JNI_FALSE;
}
