cmake_minimum_required(VERSION 3.22.1)
project("yara-core")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -O3 -flto -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O3 -flto -fvisibility=hidden")

set(YARA_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/yara-src/libyara)

add_definitions(
    -D_GNU_SOURCE
    -DUSE_NO_BISON
    -DYR_NO_CRYPTO
    -DMODULE_DEX
    -DMODULE_ELF
    -DMODULE_MACHO
    -D_FILE_OFFSET_BITS=64
    -DBUCKETS_128
    -DCHECKSUM_1B
    -DUSE_NO_PROC
)

set(YARA_SOURCES
    ${YARA_SRC_DIR}/ahocorasick.c
    ${YARA_SRC_DIR}/arena.c
    ${YARA_SRC_DIR}/atoms.c
    ${YARA_SRC_DIR}/bitmask.c
    ${YARA_SRC_DIR}/compiler.c
    ${YARA_SRC_DIR}/endian.c
    ${YARA_SRC_DIR}/exec.c
    ${YARA_SRC_DIR}/exefiles.c
    ${YARA_SRC_DIR}/filemap.c
    ${YARA_SRC_DIR}/grammar.c
    ${YARA_SRC_DIR}/hash.c
    ${YARA_SRC_DIR}/hex_grammar.c
    ${YARA_SRC_DIR}/hex_lexer.c
    ${YARA_SRC_DIR}/lexer.c
    ${YARA_SRC_DIR}/libyara.c
    ${YARA_SRC_DIR}/mem.c
    ${YARA_SRC_DIR}/modules.c
    ${YARA_SRC_DIR}/notebook.c
    ${YARA_SRC_DIR}/object.c
    ${YARA_SRC_DIR}/parser.c
    ${YARA_SRC_DIR}/re.c
    ${YARA_SRC_DIR}/re_grammar.c
    ${YARA_SRC_DIR}/re_lexer.c
    ${YARA_SRC_DIR}/rules.c
    ${YARA_SRC_DIR}/scan.c
    ${YARA_SRC_DIR}/scanner.c
    ${YARA_SRC_DIR}/base64.c
    ${YARA_SRC_DIR}/sizedstr.c
    ${YARA_SRC_DIR}/stack.c
    ${YARA_SRC_DIR}/stopwatch.c
    ${YARA_SRC_DIR}/strutils.c
    ${YARA_SRC_DIR}/stream.c
    ${YARA_SRC_DIR}/threading.c
    
    ${YARA_SRC_DIR}/modules/dex/dex.c
    ${YARA_SRC_DIR}/modules/elf/elf.c
    ${YARA_SRC_DIR}/modules/macho/macho.c
    ${YARA_SRC_DIR}/modules/pe/pe.c
    ${YARA_SRC_DIR}/modules/pe/pe_utils.c
    ${YARA_SRC_DIR}/modules/tests/tests.c
    ${YARA_SRC_DIR}/modules/math/math.c
    ${YARA_SRC_DIR}/modules/time/time.c
    ${YARA_SRC_DIR}/modules/console/console.c
    ${YARA_SRC_DIR}/modules/string/string.c
    
    ${YARA_SRC_DIR}/proc.c
    ${YARA_SRC_DIR}/proc/none.c
    ${YARA_SRC_DIR}/simple_str.c
    
    ${YARA_SRC_DIR}/tlshc/tlsh.c
    ${YARA_SRC_DIR}/tlshc/tlsh_impl.c
    ${YARA_SRC_DIR}/tlshc/tlsh_util.c
)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/yara-src/libyara/include
    ${CMAKE_CURRENT_SOURCE_DIR}/yara-src/libyara/
)

add_library(libyara STATIC ${YARA_SOURCES})

add_library(yara-core SHARED yara_engine.cpp)

find_library(log-lib log)
target_link_libraries(yara-core libyara ${log-lib})
