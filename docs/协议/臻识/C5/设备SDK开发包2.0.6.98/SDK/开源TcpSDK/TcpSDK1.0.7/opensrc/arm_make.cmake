# CMake toolchain file for building ARM software on OI environment

include(CMakeForceCompiler)
# this one is important
SET(CMAKE_SYSTEM_NAME Linux)
set( CMAKE_SYSTEM_PROCESSOR arm ) 
#this one not so much
SET(CMAKE_SYSTEM_VERSION 1)

# specify the cross compiler
#SET(CMAKE_C_COMPILER   arm-linux-gnueabihf-gcc)
#SET(CMAKE_CXX_COMPILER arm-linux-gnueabihf-g++)
#SET(CMAKE_STRIP arm-linux-gnueabihf-strip)
#CMAKE_FORCE_C_COMPILER(arm-arago-linux-gnueabi-gcc GNU)
#CMAKE_FORCE_CXX_COMPILER(arm-arago-linux-gnueabi-g++ GNU)

SET(CMAKE_C_COMPILER   gcc)
SET(CMAKE_CXX_COMPILER g++)
SET(CMAKE_STRIP strip)

# where is the target environment 
#SET(CMAKE_FIND_ROOT_PATH  "/mnt/hgfs/Share/cmake project/libvznet")

# search for programs in the build host directories
SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
# for libraries and headers in the target directories
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)