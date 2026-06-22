cmake_minimum_required(VERSION 3.22.1)
project("vpn-core")

set(CMAKE_CXX_STANDARD 17)

# Native Phishing Engine
add_library(vpn-core SHARED edge_phishing_pipeline.cpp)

# Chakravyuh Native Security
add_library(chakravyuh SHARED chakravyuh.cpp)

# Native Scanner (Security/Deep Scan Refactor)
add_library(native_scanner SHARED native_scanner.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(vpn-core 
    ${log-lib} 
    ${android-lib}
)

target_link_libraries(chakravyuh 
    ${log-lib} 
    ${android-lib}
)

target_link_libraries(native_scanner 
    ${log-lib} 
    ${android-lib}
)

# Production flags: LTO, dead code elimination, optimized math
target_compile_options(vpn-core PRIVATE -O3 -flto -fno-exceptions -fno-rtti)
target_link_options(vpn-core PRIVATE -flto -Wl,--gc-sections)

target_compile_options(chakravyuh PRIVATE -O3 -flto -fno-exceptions -fno-rtti)
target_link_options(chakravyuh PRIVATE -flto -Wl,--gc-sections)

target_compile_options(native_scanner PRIVATE -O3 -flto -fno-exceptions -fno-rtti)
target_link_options(native_scanner PRIVATE -flto -Wl,--gc-sections)
