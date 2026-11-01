#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdbool.h>
#include <string.h>
#include <sys/epoll.h>
#include <unistd.h>
#define TAG "VpnCoreNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global JNI references
static JavaVM *g_jvm = NULL;
static jobject g_vpn_service_obj = NULL;
static jmethodID g_method_is_domain_malicious = NULL;
static jmethodID g_method_on_packet_routed = NULL;
static jmethodID g_method_protect_socket = NULL;

// Native thread control
static pthread_t tun_thread;
static volatile bool is_vpn_running = false;

// Simplified structures for parsing
struct Ipv4Header {
    uint8_t ihl_version;
    uint8_t tos;
    uint16_t tot_len;
    uint16_t id;
    uint16_t frag_off;
    uint8_t ttl;
    uint8_t protocol;
    uint16_t check;
    uint32_t saddr;
    uint32_t daddr;
};

struct TcpHeader {
    uint16_t source;
    uint16_t dest;
    uint32_t seq;
    uint32_t ack_seq;
    uint16_t flags;
    uint16_t window;
    uint16_t check;
    uint16_t urg_ptr;
};

// Threat Hook
bool evaluate_threat(uint32_t ip, uint16_t port) {
    // Advanced Threat Engine integration
    return false; // Stub
}

// Checksum helper
uint16_t checksum(void *b, int len) {
    uint16_t *buf = (uint16_t*)b;
    unsigned int sum = 0;
    for (sum = 0; len > 1; len -= 2) sum += *buf++;
    if (len == 1) sum += *(unsigned char *)buf;
    sum = (sum >> 16) + (sum & 0xFFFF);
    sum += (sum >> 16);
    return ~sum;
}

// 1. JNI Protect Callback
bool jni_protect_socket(int socket_fd, JNIEnv* env) {
    if (!g_vpn_service_obj || !g_method_protect_socket || !env) return false;
    jboolean res = (*env)->CallBooleanMethod(env, g_vpn_service_obj, g_method_protect_socket, socket_fd);
    return res == JNI_TRUE;
}

// Implement tun_read_loop natively with epoll and zero-allocation
void *tun_read_loop(void *arg) {
    int tun_fd = (int)(intptr_t)arg;
    
    int epoll_fd = epoll_create1(0);
    if (epoll_fd < 0) {
        LOGE("Failed to create epoll instance");
        return NULL;
    }

    struct epoll_event event;
    event.events = EPOLLIN;
    event.data.fd = tun_fd;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, tun_fd, &event) < 0) {
        LOGE("Failed to add tun_fd to epoll");
        close(epoll_fd);
        return NULL;
    }

    struct epoll_event events[1];
    
    // ZERO ALLOCATION: Allocate buffer once outside the hot-loop
    uint8_t buffer[65535];

    LOGI("Native epoll hot-loop started on FD: %d", tun_fd);

    // Attach thread to JVM for callbacks if needed
    JNIEnv *env = attach_current_thread();

    while (is_vpn_running) {
        // Block efficiently (0% CPU idle)
        int n = epoll_wait(epoll_fd, events, 1, 1000); 
        
        if (n > 0) {
            if (events[0].events & EPOLLIN) {
                ssize_t length = read(tun_fd, buffer, sizeof(buffer));
                if (length > 0) {
                    struct Ipv4Header *ip = (struct Ipv4Header *)buffer;
                    if ((ip->ihl_version >> 4) == 4) { // IPv4
                        int ip_len = (ip->ihl_version & 0x0F) * 4;
                        
                        // 3. Threat Interception & TCP RST Synthesis
                        if (evaluate_threat(ip->daddr, 0)) {
                            if (ip->protocol == 6) { // TCP
                                struct TcpHeader *tcp = (struct TcpHeader *)(buffer + ip_len);
                                // Swap IPs and Ports
                                uint32_t tmp_ip = ip->saddr; ip->saddr = ip->daddr; ip->daddr = tmp_ip;
                                uint16_t tmp_port = tcp->source; tcp->source = tcp->dest; tcp->dest = tmp_port;
                                
                                // Synthesize RST ACK
                                tcp->ack_seq = htonl(ntohl(tcp->seq) + 1);
                                tcp->seq = 0;
                                tcp->flags = htons(0x5014); // Header length 20, flags ACK + RST
                                
                                // Recalculate checksums
                                ip->check = 0;
                                ip->check = checksum(ip, ip_len);
                                tcp->check = 0; // Simplified
                                
                                write(tun_fd, buffer, length); // Inject RST back to client app
                            }
                            continue; // Drop packet
                        }
                        
                        // 2. Integrating NetGuard's NAT Logic & Outbound Socket Protection
                        if (ip->protocol == 6 || ip->protocol == 17) {
                            int out_fd = socket(AF_INET, (ip->protocol == 6) ? SOCK_STREAM : SOCK_DGRAM, 0);
                            if (out_fd >= 0) {
                                // CRITICAL: Protect socket BEFORE connect/sendto
                                if (jni_protect_socket(out_fd, env)) {
                                    // Pass to NetGuard process_packet logic or connect natively
                                    // connect(out_fd, ...);
                                }
                                close(out_fd);
                            }
                        }
                    }
                } else if (length < 0) {
                    LOGE("Error reading from tun_fd");
                }
            }
        } else if (n < 0) {
            LOGE("epoll_wait failed");
            break;
        }
    }

    close(epoll_fd);
    close(tun_fd); // Close the detached FD since Kotlin no longer manages it
    detach_current_thread();
    
    LOGI("Native epoll hot-loop exited");
    return NULL;
}

// Invoked automatically when System.loadLibrary("vpn-core") is called
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    LOGI("JNI_OnLoad completed successfully.");
    return JNI_VERSION_1_6;
}

// Optimized helper to attach current thread to JVM without leaking
static JNIEnv *attach_current_thread() {
    JNIEnv *env;
    int getEnvStat = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != 0) {
            LOGE("Failed to attach current thread");
            return NULL;
        }
    } else if (getEnvStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return NULL;
    }
    return env;
}

// Helper to detach current thread
static void detach_current_thread() {
    (*g_jvm)->DetachCurrentThread(g_jvm);
}

// --- Kotlin to C++ --- //

JNIEXPORT void JNICALL
Java_com_protctus_app_services_ProtctusVpnService_startNativeEngine(JNIEnv *env, jobject thiz, jint fd) {
    if (is_vpn_running) {
        LOGE("VPN is already running natively.");
        return;
    }

    LOGI("Starting native VPN on FD: %d", fd);

    // Cache the Java object and methods for callbacks
    g_vpn_service_obj = (*env)->NewGlobalRef(env, thiz);
    
    jclass cls = (*env)->GetObjectClass(env, g_vpn_service_obj);
    g_method_protect_socket = (*env)->GetMethodID(env, cls, "jniProtectSocket", "(I)Z");
    
    // Optional legacy bindings if present in ProtctusVpnService
    g_method_is_domain_malicious = (*env)->GetMethodID(env, cls, "jniIsDomainMalicious", "(Ljava/lang/String;)Z");
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    
    g_method_on_packet_routed = (*env)->GetMethodID(env, cls, "jniOnPacketRouted", "(ILjava/lang/String;ILjava/lang/String;II)V");
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    if (!g_method_protect_socket) {
        LOGE("Failed to cache JNI protectSocket callback.");
        // We can continue, but sockets won't be protected. Loop might happen.
    }

    is_vpn_running = true;

    // Start the TUN reading loop in a new native thread
    int tun_fd = fd;
    if (pthread_create(&tun_thread, NULL, tun_read_loop, (void *)(intptr_t)tun_fd) != 0) {
        LOGE("Failed to create TUN read thread.");
        is_vpn_running = false;
    }
}

JNIEXPORT void JNICALL
Java_com_protctus_app_services_ProtctusVpnService_stopNativeEngine(JNIEnv *env, jobject thiz) {
    LOGI("Stopping native VPN.");
    is_vpn_running = false; // The tun_read_loop should check this flag and exit cleanly

    if (g_vpn_service_obj != NULL) {
        (*env)->DeleteGlobalRef(env, g_vpn_service_obj);
        g_vpn_service_obj = NULL;
    }
}

// --- C++ to Kotlin Callbacks (Exported for dns.c and pcap.c) --- //

/**
 * Called synchronously by dns.c. 
 * Must be lightning fast.
 */
bool jni_call_is_domain_malicious(const char *domain) {
    if (!is_vpn_running || !g_vpn_service_obj || !g_method_is_domain_malicious) return false;

    JNIEnv *env = attach_current_thread();
    if (!env) return false;

    jstring jDomain = (*env)->NewStringUTF(env, domain);
    jboolean is_malicious = (*env)->CallBooleanMethod(env, g_vpn_service_obj, g_method_is_domain_malicious, jDomain);
    (*env)->DeleteLocalRef(env, jDomain);

    // Note: Do not detach thread if we are in the main TUN read loop to avoid huge overhead.
    // Assuming tun_read_loop attached once at the start.

    return is_malicious == JNI_TRUE;
}

/**
 * Called asynchronously by pcap.c for telemetry.
 */
void jni_call_on_packet_routed(int protocol, const char *src_ip, int src_port, const char *dst_ip, int dst_port, int uid) {
    if (!is_vpn_running || !g_vpn_service_obj || !g_method_on_packet_routed) return;

    JNIEnv *env = attach_current_thread();
    if (!env) return;

    jstring jSrcIp = (*env)->NewStringUTF(env, src_ip);
    jstring jDstIp = (*env)->NewStringUTF(env, dst_ip);

    (*env)->CallVoidMethod(env, g_vpn_service_obj, g_method_on_packet_routed, 
                           protocol, jSrcIp, src_port, jDstIp, dst_port, uid);

    (*env)->DeleteLocalRef(env, jSrcIp);
    (*env)->DeleteLocalRef(env, jDstIp);
}
