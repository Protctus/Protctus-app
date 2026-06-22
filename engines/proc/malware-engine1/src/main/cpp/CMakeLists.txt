cmake_minimum_required(VERSION 3.22.1)

project("vpn-core")

# Strict Compilation Flags for Memory Safety & Performance
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Werror -O3 -fno-strict-aliasing")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Werror -O3")

# Define the source files (Extracted from NetGuard, stripped and modified)
set(VPN_SOURCES
    jni.c
    tun.c
    pcap.c
    dns.c
    util.c
)

# Add the shared library
add_library(
    vpn-core
    SHARED
    ${VPN_SOURCES}
)

# Link against Android NDK libraries
find_library(log-lib log)

target_link_libraries(
    vpn-core
    ${log-lib}
    android
)
