cmake_minimum_required(VERSION 3.22.1)

project("native-fs")

add_compile_options(
        -O3
        -fPIC
        -ffunction-sections
        -fdata-sections
        -D_FORTIFY_SOURCE=0
        -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=/src
)

add_link_options(
        -s
        -flto
        -Wl,--gc-sections
        -Wl,--build-id=none
        -Wl,--hash-style=both
)

# libnative-filesystem.so
add_library(native-filesystem SHARED
        native-filesystem.cpp
)

target_link_libraries(native-filesystem
        android
        log
)
