cmake_minimum_required(VERSION 3.4.1)

find_library( log-lib
              log )

#添加引用的头文件
include_directories(
                     src/main/cpp
                     src/main/cpp/include)

#添加源代码
add_library( decoder_demo
             #rtmp-handle
             SHARED
             #src/main/cpp/rtmp_handle.cpp
             src/main/cpp/decoder.cpp
             src/main/cpp/decoder_demo.cpp)

#添加库 动态库为SHARED  静态库就是STATIC
#add_library( rtmp
#             SHARED
#             IMPORTED )
#set_target_properties( rtmp
#                       PROPERTIES IMPORTED_LOCATION
#                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/librtmp.so)

add_library( avcodec-57
             SHARED
             IMPORTED)
set_target_properties( avcodec-57
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libavcodec-57.so)

add_library( avfilter-6
             SHARED
             IMPORTED)
set_target_properties( avfilter-6
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libavfilter-6.so)

add_library( avformat-57
             SHARED
             IMPORTED)
set_target_properties( avformat-57
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libavformat-57.so)

add_library( avutil-55
             SHARED
             IMPORTED)
set_target_properties( avutil-55
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libavutil-55.so)

add_library( swresample-2
             SHARED
             IMPORTED)
set_target_properties( swresample-2
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libswresample-2.so)

add_library( swscale-4
             SHARED
             IMPORTED)
set_target_properties( swscale-4
                       PROPERTIES IMPORTED_LOCATION
                       ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libswscale-4.so)

target_link_libraries(
                       decoder_demo
                       #rtmp-handle

                       avcodec-57
                       avfilter-6
                       avformat-57
                       avutil-55
                       swresample-2
                       swscale-4
                       #rtmp
                       ${log-lib} )
