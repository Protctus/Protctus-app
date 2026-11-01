#include <jni.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include <atomic>
#include <vector>
#include <string>
#include <android/log.h>
#include <unordered_map>
#include <fcntl.h>
#include <chrono>

#define TAG "PhishNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<bool> g_running(false);
static int g_tun_fd = -1;
static int g_upstream_udp_fd = -1;
static JavaVM* g_jvm = nullptr;
static jobject g_vpn_service_ref = nullptr;

// DNS Header Structure
struct DnsHeader {
    uint16_t id;
    uint16_t flags;
    uint16_t qd_count;
    uint16_t an_count;
    uint16_t ns_count;
    uint16_t ar_count;
};

// Helper to protect upstream socket (called once)
bool protect_socket(JNIEnv* env, int fd) {
    jclass clazz = env->GetObjectClass(g_vpn_service_ref);
    jmethodID mid = env->GetMethodID(clazz, "jniProtectSocket", "(I)Z");
    if (mid == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(clazz);
        return false;
    }
    jboolean res = env->CallBooleanMethod(g_vpn_service_ref, mid, fd);
    env->DeleteLocalRef(clazz);
    return res == JNI_TRUE;
}

static jmethodID g_mid_is_malicious = nullptr;

struct CacheEntry {
    bool is_malicious;
    uint64_t timestamp_ms;
};
static std::unordered_map<std::string, CacheEntry> g_domain_cache;

// Synchronous JNI check using provided env (Fast path)
bool is_malicious_sync(const char* domain, JNIEnv* env) {
    std::string dom_str(domain);
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();

    auto it = g_domain_cache.find(dom_str);
    if (it != g_domain_cache.end()) {
        if (now - it->second.timestamp_ms < 300000) { // 5 minutes TTL
            return it->second.is_malicious;
        } else {
            g_domain_cache.erase(it);
        }
    }

    if (g_mid_is_malicious == nullptr) {
        jclass clazz = env->GetObjectClass(g_vpn_service_ref);
        g_mid_is_malicious = env->GetMethodID(clazz, "jniIsMalicious", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(clazz);
    }
    
    if (g_mid_is_malicious == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }

    jstring jdomain = env->NewStringUTF(domain);
    jboolean res = env->CallBooleanMethod(g_vpn_service_ref, g_mid_is_malicious, jdomain);
    env->DeleteLocalRef(jdomain);
    
    bool malicious = (res == JNI_TRUE);
    
    // Prevent unbounded cache growth
    if (g_domain_cache.size() > 5000) {
        g_domain_cache.clear();
    }
    g_domain_cache[dom_str] = {malicious, static_cast<uint64_t>(now)};
    
    return malicious;
}

// Checksum calculation for IP/UDP/TCP
uint16_t checksum(uint16_t *buf, int nwords) {
    uint32_t sum;
    for (sum = 0; nwords > 0; nwords--) sum += *buf++;
    sum = (sum >> 16) + (sum & 0xffff);
    sum += (sum >> 16);
    return (uint16_t)(~sum);
}

// Compute pseudo-header checksum for TCP
uint16_t tcp_checksum(struct iphdr* ip, struct tcphdr* tcp, int tcp_len) {
    uint32_t sum = 0;
    uint16_t* src = (uint16_t*)&ip->saddr;
    uint16_t* dst = (uint16_t*)&ip->daddr;
    sum += src[0]; sum += src[1];
    sum += dst[0]; sum += dst[1];
    sum += htons(IPPROTO_TCP);
    sum += htons(tcp_len);
    
    uint16_t* t = (uint16_t*)tcp;
    for (int i = 0; i < tcp_len / 2; i++) sum += t[i];
    if (tcp_len % 2) sum += ((uint8_t*)tcp)[tcp_len - 1] << 8;
    
    sum = (sum >> 16) + (sum & 0xffff);
    sum += (sum >> 16);
    return (uint16_t)(~sum);
}

struct DnsSession {
    uint32_t saddr;
    uint16_t sport;
    uint64_t created_at_ms; // Timestamp for TTL-based eviction
};

/**
 * High-Performance DNS Shield Loop
 * Optimizations:
 * 1. epoll handles both TUN and upstream UDP concurrently.
 * 2. Non-blocking recvfrom prevents DNS timeouts.
 * 3. Connection tracking routes DNS responses to correct apps.
 */
static void* vpn_dns_loop(void* arg) {
    JNIEnv* env;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) {
        LOGE("Failed to attach DNS loop thread to JVM");
        return nullptr;
    }

    // Set upstream socket to non-blocking
    int flags = fcntl(g_upstream_udp_fd, F_GETFL, 0);
    fcntl(g_upstream_udp_fd, F_SETFL, flags | O_NONBLOCK);

    int epoll_fd = epoll_create1(0);
    struct epoll_event ev;
    
    ev.events = EPOLLIN;
    ev.data.fd = g_tun_fd;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, g_tun_fd, &ev);

    ev.events = EPOLLIN;
    ev.data.fd = g_upstream_udp_fd;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, g_upstream_udp_fd, &ev);

    uint8_t buffer[2048];
    struct sockaddr_in upstream_addr;
    upstream_addr.sin_family = AF_INET;
    upstream_addr.sin_port = htons(53);
    upstream_addr.sin_addr.s_addr = inet_addr("8.8.8.8");

    std::unordered_map<uint16_t, DnsSession> dns_nat_table;
    struct epoll_event events[2];

    LOGI("DNS Shield Async Epoll Loop Started.");

    while (g_running.load()) {
        int n_fds = epoll_wait(epoll_fd, events, 2, 1000);
        if (n_fds < 0) {
            if (g_running.load()) LOGE("epoll_wait error");
            break;
        }

        for (int i = 0; i < n_fds; i++) {
            if (events[i].data.fd == g_tun_fd) {
                ssize_t n = read(g_tun_fd, buffer, sizeof(buffer));
                if (n <= 0) continue;

                // Minimum size: IP header + UDP header + DNS header
                if (n < (ssize_t)(sizeof(struct iphdr) + sizeof(struct udphdr) + sizeof(struct DnsHeader))) {
                    continue;
                }

                struct iphdr* ip = (struct iphdr*)buffer;
                // Validate IP header length field
                if (ip->ihl < 5) continue;
                size_t ip_hdr_len = ip->ihl * 4;
                if ((ssize_t)(ip_hdr_len + sizeof(struct udphdr)) > n) continue;

                if (ip->protocol == IPPROTO_UDP) {
                    struct udphdr* udp = (struct udphdr*)(buffer + ip_hdr_len);
                    uint8_t* dns_payload = buffer + ip_hdr_len + sizeof(struct udphdr);
                    int dns_len = ntohs(udp->len) - sizeof(struct udphdr);

                    // Validate DNS length against actual packet data
                    if (dns_len <= 0 || dns_len > (n - ip_hdr_len - (int)sizeof(struct udphdr))) continue;

                    struct DnsHeader* dns = (struct DnsHeader*)dns_payload;
                    bool is_query = ((ntohs(dns->flags) & 0x8000) == 0); // QR=0 → query
                    bool is_standard = (((ntohs(dns->flags) >> 11) & 0xF) == 0); // opcode=0

                    if (ntohs(udp->dest) == 53 && is_query && is_standard) {
                        char domain[256];
                        int d_idx = 0;
                        uint8_t* p = dns_payload + sizeof(struct DnsHeader);
                        uint8_t* end = buffer + n;
                        if (p < end) {
                            while (*p > 0 && d_idx < 254 && p < end) {
                                if ((*p & 0xC0) == 0xC0) {
                                    // Pointer compression: skip the 2-byte pointer and stop.
                                    // We do not follow pointers — this avoids infinite loops
                                    // and keeps domain extraction bounded and deterministic.
                                    p += 2;
                                    break;
                                }
                                int len = *p++;
                                if ((ssize_t)(p + len) > end) break; // label runs past packet
                                for (int j = 0; j < len && d_idx < 254 && p < end; j++) domain[d_idx++] = *p++;
                                domain[d_idx++] = '.';
                            }
                            if (d_idx > 0) domain[d_idx - 1] = '\0';
                            else domain[0] = '\0';

                            if (domain[0] != '\0' && is_malicious_sync(domain, env)) {
                                LOGI("DNS Shield BLOCKED: %s", domain);
                                dns->flags = htons(0x8183);
                                
                                uint32_t tmp_ip = ip->saddr; ip->saddr = ip->daddr; ip->daddr = tmp_ip;
                                uint16_t tmp_port = udp->source; udp->source = udp->dest; udp->dest = tmp_port;
                                
                                udp->len = htons(sizeof(struct udphdr) + dns_len);
                                ip->tot_len = htons((uint16_t)(ip_hdr_len + sizeof(struct udphdr) + dns_len));
                                ip->check = 0;
                                ip->check = checksum((uint16_t*)ip, ip_hdr_len / 2);
                                udp->check = 0;
                                
                                write(g_tun_fd, buffer, ntohs(ip->tot_len));
                                continue; 
                            }
                        }
                        auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::steady_clock::now().time_since_epoch()).count();
                        dns_nat_table[dns->id] = {static_cast<uint32_t>(ip->saddr), udp->source, static_cast<uint64_t>(now_ms)};

                        // Forward asynchronously
                        sendto(g_upstream_udp_fd, dns_payload, dns_len, 0, (struct sockaddr*)&upstream_addr, sizeof(upstream_addr));
                    }
                } else if (ip->protocol == IPPROTO_TCP) {
                    // Validate IP header and minimum TCP segment size
                    if (ip->ihl < 5) continue;
                    size_t ip_hdr_len = ip->ihl * 4;
                    if ((ssize_t)(ip_hdr_len + sizeof(struct tcphdr)) > n) continue;
                    
                    struct tcphdr* tcp = (struct tcphdr*)(buffer + ip_hdr_len);
                    uint16_t dest_port = ntohs(tcp->dest);
                    
                    if ((dest_port == 853 || dest_port == 53) && tcp->syn && !tcp->ack) {
                        uint32_t tmp_ip = ip->saddr; ip->saddr = ip->daddr; ip->daddr = tmp_ip;
                        uint16_t tmp_port = tcp->source; tcp->source = tcp->dest; tcp->dest = tmp_port;
                        
                        uint32_t ack_num = ntohl(tcp->seq) + 1;
                        tcp->seq = 0;
                        tcp->ack_seq = htonl(ack_num);
                        tcp->doff = 5; 
                        
                        tcp->urg = 0; tcp->ack = 1; tcp->psh = 0; tcp->rst = 1; tcp->syn = 0; tcp->fin = 0;
                        tcp->window = 0;
                        tcp->check = 0;
                        tcp->urg_ptr = 0;
                        
                        int tcp_len = sizeof(struct tcphdr);
                        ip->tot_len = htons((uint16_t)(ip_hdr_len + tcp_len));
                        ip->check = 0;
                        ip->check = checksum((uint16_t*)ip, ip_hdr_len / 2);
                        tcp->check = tcp_checksum(ip, tcp, tcp_len);
                        
                        write(g_tun_fd, buffer, ntohs(ip->tot_len));
                    }
                }
            } else if (events[i].data.fd == g_upstream_udp_fd) {
                // Read async response from upstream DNS
                struct sockaddr_in from_addr;
                socklen_t from_len = sizeof(from_addr);
                ssize_t res_len = recvfrom(g_upstream_udp_fd, buffer + 28, sizeof(buffer) - 28, 0, (struct sockaddr*)&from_addr, &from_len);
                
                if (res_len > 0) {
                    // Need at least a DNS header to read the transaction ID
                    if (res_len < (ssize_t)sizeof(struct DnsHeader)) continue;
                    struct DnsHeader* dns = (struct DnsHeader*)(buffer + 28);
                    auto it = dns_nat_table.find(dns->id);
                    if (it != dns_nat_table.end()) {
                        struct iphdr* ip = (struct iphdr*)buffer;
                        struct udphdr* udp = (struct udphdr*)(buffer + 20);
                        
                        // Reconstruct IP/UDP Headers
                        ip->ihl = 5;
                        ip->version = 4;
                        ip->tos = 0;
                        ip->tot_len = htons(20 + 8 + res_len);
                        ip->id = htons(0);
                        ip->frag_off = htons(0);
                        ip->ttl = 64;
                        ip->protocol = IPPROTO_UDP;
                        ip->saddr = inet_addr("10.0.0.1");
                        ip->daddr = it->second.saddr;
                        ip->check = 0;
                        ip->check = checksum((uint16_t*)ip, 10);
                        
                        udp->source = htons(53);
                        udp->dest = it->second.sport;
                        udp->len = htons(8 + res_len);
                        udp->check = 0; 
                        
                        write(g_tun_fd, buffer, 28 + res_len);
                        dns_nat_table.erase(it);
                    }
                }
            }
        }
        
        // P2 FIX: TTL-based LRU eviction instead of sledgehammer clear().
        // Remove entries older than 10 seconds. DNS responses never take >10s.
        if (dns_nat_table.size() > 500) {
            auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
            for (auto it = dns_nat_table.begin(); it != dns_nat_table.end(); ) {
                if (now_ms - it->second.created_at_ms > 10000) {
                    it = dns_nat_table.erase(it);
                } else {
                    ++it;
                }
            }
        }
    }

    close(epoll_fd);
    g_jvm->DetachCurrentThread();
    LOGI("DNS Shield Loop Exited.");
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_protctus_edge_phishing_PhishingVpnService_startNativeEngine(JNIEnv* env, jobject thiz, jint fd) {
    if (g_running.load()) return;
    
    g_tun_fd = fd;
    env->GetJavaVM(&g_jvm);
    g_vpn_service_ref = env->NewGlobalRef(thiz);

    g_upstream_udp_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (!protect_socket(env, g_upstream_udp_fd)) {
        LOGE("Failed to protect upstream socket!");
    }

    struct timeval tv;
    tv.tv_sec = 1;
    tv.tv_usec = 500000; // 1.5s timeout
    setsockopt(g_upstream_udp_fd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof tv);

    g_running.store(true);
    pthread_t t1;
    if (pthread_create(&t1, nullptr, vpn_dns_loop, nullptr) != 0) {
        LOGE("Failed to create DNS loop thread");
        g_running.store(false);
    } else {
        pthread_detach(t1);
        LOGI("Native Engine initialized with Split-Tunnel DNS Shield.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_protctus_edge_phishing_PhishingVpnService_stopNativeEngine(JNIEnv* env, jobject thiz) {
    LOGI("Stopping Native Engine...");
    g_running.store(false);
    
    // Close sockets to break blocking calls
    if (g_upstream_udp_fd != -1) {
        close(g_upstream_udp_fd);
        g_upstream_udp_fd = -1;
    }
    
    // Small delay to allow loop to exit
    usleep(100000);

    if (g_vpn_service_ref) {
        env->DeleteGlobalRef(g_vpn_service_ref);
        g_vpn_service_ref = nullptr;
    }
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_protctus_edge_phishing_PhishingVpnService_getNativeMetrics(JNIEnv* env, jobject thiz) {
    jlongArray result = env->NewLongArray(6);
    jlong fill[6] = {0, 0, 0, 0, 0, 0};
    env->SetLongArrayRegion(result, 0, 6, fill);
    return result;
}
