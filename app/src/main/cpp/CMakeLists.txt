cmake_minimum_required(VERSION 3.22)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)

# JNI bridge library
add_library(llama_jni SHARED llama_jni.cpp)

# Link Android log library
find_library(log-lib log)
target_link_libraries(llama_jni ${log-lib})

# When llama.cpp submodule is present, uncomment:
# add_subdirectory(llama.cpp)
# target_link_libraries(llama_jni llama common)
# target_include_directories(llama_jni PRIVATE llama.cpp/include)
