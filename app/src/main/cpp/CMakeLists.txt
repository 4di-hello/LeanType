cmake_minimum_required(VERSION 3.22.1)

project(jni_pinyin)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(
    jni_pinyin
    SHARED
    PinyinJni.cpp
    share/dictbuilder.cpp
    share/dictlist.cpp
    share/dicttrie.cpp
    share/lpicache.cpp
    share/matrixsearch.cpp
    share/mystdlib.cpp
    share/ngram.cpp
    share/pinyinime.cpp
    share/searchutility.cpp
    share/spellingtable.cpp
    share/spellingtrie.cpp
    share/splparser.cpp
    share/sync.cpp
    share/userdict.cpp
    share/utf16char.cpp
    share/utf16reader.cpp
)

find_library(log-lib log)

target_link_libraries(
    jni_pinyin
    ${log-lib}
)
