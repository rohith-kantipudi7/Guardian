cmake_minimum_required(VERSION 3.10)

project(whisper.cpp)

set(CMAKE_CXX_STANDARD 17)

# app/src/main/cpp -> up 4 dirs to guardian-android, then into the vendored whisper.cpp clone.
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../whisper.cpp)

file(READ "${WHISPER_LIB_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()
message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${WHISPER_LIB_DIR}/src/aneforge/whisper-aneforge.cpp
    ${CMAKE_SOURCE_DIR}/jni.c
)

find_library(LOG_LIB log)

include(FetchContent)

add_library(whisper SHARED ${SOURCE_FILES})
target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

target_link_libraries(whisper ${LOG_LIB} android ggml)

include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)
include_directories(${WHISPER_LIB_DIR}/ggml/src/ggml-cpu)
