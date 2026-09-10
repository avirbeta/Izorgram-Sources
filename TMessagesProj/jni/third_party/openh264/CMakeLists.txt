cmake_minimum_required(VERSION 3.19 FATAL_ERROR)

project(
	openh264
	VERSION 2.6.0
	DESCRIPTION "OpenH264 is a codec library which supports H.264 encoding and decoding. It is suitable for use in real time applications such as WebRTC."
	HOMEPAGE_URL "https://github.com/cisco/openh264"
	LANGUAGES C CXX
)

option(OPENH264_BUILD_CONSOLE "Build the h264dec and h264enc console applications" ON)
option(BUILD_SHARED_LIBS "Build shared libraries" ON)

set(CMAKE_POLICY_DEFAULT_CMP0048 NEW)
set(CMAKE_POLICY_DEFAULT_CMP0069 NEW)
set(CMAKE_POLICY_DEFAULT_CMP0077 NEW)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}")

set(OPENH264_SOVERSION 8)

find_package(Threads REQUIRED)

find_library(MATH_LIBRARY m)

set(
	OPENH264_INCLUDE_DIRS
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/api/wels"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/common/inc"
)

set(
	PROCESSING_INCLUDE_DIRS
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/interface"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/common"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/adaptivequantization"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/downsample"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/scrolldetection"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/vaacalc"
)

set(CONSOLE_COMMON_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/codec/console/common/inc")

set(
	DECODER_INCLUDE_DIRS
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/inc"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/plus/inc"
)

set(
	ENCODER_INCLUDE_DIRS
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/inc"
	"${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/plus/inc"
)

set(CASM_INCLUDE_DIRS "")

# System and CPU family detection
string(TOLOWER "${CMAKE_SYSTEM_PROCESSOR}" SYSTEM_PROCESSOR)

if (SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64)$")
	set(CPU_FAMILY "x86_64")
elseif (SYSTEM_PROCESSOR MATCHES "^(i[3-6]86|x86)$")
	set(CPU_FAMILY "x86")
elseif (SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)$")
	set(CPU_FAMILY "aarch64")
elseif (SYSTEM_PROCESSOR MATCHES "^arm")
	set(CPU_FAMILY "arm")
elseif (SYSTEM_PROCESSOR MATCHES "^loongarch64")
	set(CPU_FAMILY "loongarch64")
elseif (SYSTEM_PROCESSOR MATCHES "^loongarch32")
	set(CPU_FAMILY "loongarch32")
elseif (SYSTEM_PROCESSOR MATCHES "^riscv64")
	set(CPU_FAMILY "riscv64")
elseif (SYSTEM_PROCESSOR MATCHES "^(ppc64|powerpc64)$")
	set(CPU_FAMILY "ppc64")
elseif (SYSTEM_PROCESSOR MATCHES "^(ppc|powerpc)$")
	set(CPU_FAMILY "ppc")
else()
	message(FATAL_ERROR "FIXME: unhandled CPU family ${SYSTEM_PROCESSOR} for ${CMAKE_SYSTEM_NAME}")
endif()

if (WIN32)
	set(OPENH264_SYSTEM "windows")
elseif (UNIX)
	set(OPENH264_SYSTEM "unix")
else()
	message(FATAL_ERROR "FIXME: Unhandled system ${CMAKE_SYSTEM_NAME}")
endif()

if (CMAKE_C_COMPILER_ID MATCHES "GNU|Clang" OR CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
	add_compile_options(
		"-Wall"
		"$<$<COMPILE_LANGUAGE:CXX>:-Wno-non-virtual-dtor>"
		"$<$<COMPILE_LANGUAGE:CXX>:-Wunused-but-set-variable>"
		"$<$<COMPILE_LANGUAGE:CXX>:-Wno-strict-aliasing>"
	)
endif()

# Architecture-specific assembly setup
set(NASM_SOURCE_FILES "")

if (CPU_FAMILY STREQUAL "x86" OR CPU_FAMILY STREQUAL "x86_64")
	if (WIN32)
		if (CPU_FAMILY STREQUAL "x86")
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "win32")
			set(ASM_NASM_DEFINES "-DPREFIX -DX86_32")
			set(ASM_C_DEFINES X86_ASM X86_32_ASM)
		else()
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "win64")
			set(ASM_NASM_DEFINES "-DWIN64")
			set(ASM_C_DEFINES X86_ASM)
		endif()
	elseif (APPLE)
		if (CPU_FAMILY STREQUAL "x86")
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "macho32")
			set(ASM_NASM_DEFINES "-DX86_32 -DX86_32_PICASM -DHAVE_AVX2")
			set(ASM_C_DEFINES X86_ASM X86_32_ASM)
		else()
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "macho64")
			set(ASM_NASM_DEFINES "-DUNIX64 -DHAVE_AVX2")
			set(ASM_C_DEFINES X86_ASM)
		endif()
	else()
		if (CPU_FAMILY STREQUAL "x86")
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "elf32")
			set(ASM_NASM_DEFINES "-DX86_32 -DX86_32_PICASM -DHAVE_AVX2")
			set(ASM_C_DEFINES X86_ASM X86_32_ASM)
		else()
			set(CMAKE_ASM_NASM_OBJECT_FORMAT "elf64")
			set(ASM_NASM_DEFINES "-DUNIX64 -DHAVE_AVX2")
			set(ASM_C_DEFINES X86_ASM)
		endif()
	endif()

	enable_language(ASM_NASM)

	# nasm wants the include directory to end with a path separator.
	set(
		CMAKE_ASM_NASM_FLAGS
		"${CMAKE_ASM_NASM_FLAGS} -I${CMAKE_CURRENT_SOURCE_DIR}/codec/common/x86/ ${ASM_NASM_DEFINES}"
	)

	add_compile_definitions(HAVE_AVX2)
	foreach(def IN LISTS ASM_C_DEFINES)
		add_compile_definitions("${def}")
	endforeach()
elseif (CPU_FAMILY STREQUAL "arm")
	enable_language(ASM)
	set(CASM_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm")
	add_compile_definitions(HAVE_NEON)
elseif (CPU_FAMILY STREQUAL "aarch64")
	enable_language(ASM)
	set(CASM_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm64")
	add_compile_definitions(HAVE_NEON_AARCH64)
elseif (CPU_FAMILY STREQUAL "loongarch32" OR CPU_FAMILY STREQUAL "loongarch64")
	set(CASM_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/loongarch")
	add_compile_options(-mlsx -mlasx)
	add_compile_definitions(HAVE_LSX HAVE_LASX)
elseif (CPU_FAMILY STREQUAL "riscv64" OR CPU_FAMILY STREQUAL "ppc" OR CPU_FAMILY STREQUAL "ppc64")
	# No architecture-specific optimization for now.
elseif (WIN32 AND CPU_FAMILY STREQUAL "arm")
	if (CMAKE_C_COMPILER_ID STREQUAL "MSVC")
		message(FATAL_ERROR "ARM assembly with MSVC requires armasm; use Clang instead.")
	endif()
	enable_language(ASM)
elseif (WIN32 AND CPU_FAMILY STREQUAL "aarch64")
	if (CMAKE_C_COMPILER_ID STREQUAL "MSVC")
		message(FATAL_ERROR "AArch64 assembly with MSVC requires armasm64; use Clang instead.")
	endif()
	enable_language(ASM)
else()
	message(FATAL_ERROR "FIXME: unhandled CPU family ${CPU_FAMILY} for ${CMAKE_SYSTEM_NAME}")
endif()

file(GLOB COMMON_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/src/*.cpp")
file(GLOB DECODER_CORE_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/src/*.cpp")
file(GLOB DECODER_PLUS_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/plus/src/*.cpp")
file(GLOB ENCODER_CORE_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/src/*.cpp")
file(GLOB ENCODER_PLUS_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/plus/src/*.cpp")
file(GLOB_RECURSE PROCESSING_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/*.cpp")

if (NOT WIN32)
	list(REMOVE_ITEM ENCODER_PLUS_SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/plus/src/DllEntry.cpp")
endif()

set(DECODER_SOURCE_FILES ${DECODER_CORE_SOURCE_FILES} ${DECODER_PLUS_SOURCE_FILES})
set(ENCODER_SOURCE_FILES ${ENCODER_CORE_SOURCE_FILES} ${ENCODER_PLUS_SOURCE_FILES})

# Architecture-specific assembly sources
if (CPU_FAMILY STREQUAL "x86" OR CPU_FAMILY STREQUAL "x86_64")
	file(GLOB COMMON_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/x86/*.asm")
	file(GLOB DECODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/x86/*.asm")
	file(GLOB ENCODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/x86/*.asm")
	file(GLOB PROCESSING_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/x86/*.asm")

	list(REMOVE_ITEM COMMON_ASM "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/x86/asm_inc.asm")

	list(APPEND COMMON_SOURCE_FILES ${COMMON_ASM})
	list(APPEND DECODER_SOURCE_FILES ${DECODER_ASM})
	list(APPEND ENCODER_SOURCE_FILES ${ENCODER_ASM})
	list(APPEND PROCESSING_SOURCE_FILES ${PROCESSING_ASM})

	set(NASM_SOURCE_FILES ${COMMON_ASM} ${DECODER_ASM} ${ENCODER_ASM} ${PROCESSING_ASM})
elseif (CPU_FAMILY STREQUAL "arm")
	file(GLOB COMMON_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm/*.S")
	file(GLOB DECODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/arm/*.S")
	file(GLOB ENCODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/arm/*.S")
	file(GLOB PROCESSING_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/arm/*.S")

	list(REMOVE_ITEM COMMON_ASM "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm/arm_arch_common_macro.S")

	list(APPEND COMMON_SOURCE_FILES ${COMMON_ASM})
	list(APPEND DECODER_SOURCE_FILES ${DECODER_ASM})
	list(APPEND ENCODER_SOURCE_FILES ${ENCODER_ASM})
	list(APPEND PROCESSING_SOURCE_FILES ${PROCESSING_ASM})
elseif (CPU_FAMILY STREQUAL "aarch64")
	file(GLOB COMMON_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm64/*.S")
	file(GLOB DECODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/arm64/*.S")
	file(GLOB ENCODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/arm64/*.S")
	file(GLOB PROCESSING_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/arm64/*.S")

	list(REMOVE_ITEM COMMON_ASM "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/arm64/arm_arch64_common_macro.S")

	list(APPEND COMMON_SOURCE_FILES ${COMMON_ASM})
	list(APPEND DECODER_SOURCE_FILES ${DECODER_ASM})
	list(APPEND ENCODER_SOURCE_FILES ${ENCODER_ASM})
	list(APPEND PROCESSING_SOURCE_FILES ${PROCESSING_ASM})
elseif (CPU_FAMILY STREQUAL "loongarch32" OR CPU_FAMILY STREQUAL "loongarch64")
	file(GLOB COMMON_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/common/loongarch/*.c")
	file(GLOB DECODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/decoder/core/loongarch/*.c")
	file(GLOB ENCODER_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/encoder/core/loongarch/*.c")
	file(GLOB PROCESSING_ASM CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/processing/src/loongarch/*.c")

	list(APPEND COMMON_SOURCE_FILES ${COMMON_ASM})
	list(APPEND DECODER_SOURCE_FILES ${DECODER_ASM})
	list(APPEND ENCODER_SOURCE_FILES ${ENCODER_ASM})
	list(APPEND PROCESSING_SOURCE_FILES ${PROCESSING_ASM})
elseif (CPU_FAMILY STREQUAL "riscv64" OR CPU_FAMILY STREQUAL "ppc" OR CPU_FAMILY STREQUAL "ppc64")
	# No assembly optimization for these architectures.
endif()

if (NASM_SOURCE_FILES)
	set_source_files_properties(${NASM_SOURCE_FILES} PROPERTIES LANGUAGE ASM_NASM)
endif()

# Libraries
add_library(common OBJECT ${COMMON_SOURCE_FILES})
target_include_directories(common PRIVATE ${OPENH264_INCLUDE_DIRS} ${CASM_INCLUDE_DIRS})

add_library(processing OBJECT ${PROCESSING_SOURCE_FILES})
target_include_directories(processing PRIVATE ${OPENH264_INCLUDE_DIRS} ${PROCESSING_INCLUDE_DIRS} ${CASM_INCLUDE_DIRS})

add_library(encoder OBJECT ${ENCODER_SOURCE_FILES})
target_include_directories(encoder PRIVATE ${OPENH264_INCLUDE_DIRS} ${PROCESSING_INCLUDE_DIRS} ${ENCODER_INCLUDE_DIRS} ${CASM_INCLUDE_DIRS})

add_library(decoder OBJECT ${DECODER_SOURCE_FILES})
target_include_directories(decoder PRIVATE ${OPENH264_INCLUDE_DIRS} ${DECODER_INCLUDE_DIRS} ${CASM_INCLUDE_DIRS})

add_library(openh264)
target_link_libraries(
	openh264
	PRIVATE
	common
	processing
	encoder
	decoder
	Threads::Threads
)
set_target_properties(
	openh264
	PROPERTIES
	VERSION "${PROJECT_VERSION}"
	SOVERSION "${OPENH264_SOVERSION}"
	LINKER_LANGUAGE CXX
)

if (MATH_LIBRARY)
	target_link_libraries(openh264 PRIVATE ${MATH_LIBRARY})
endif()

if (WIN32)
	target_sources(openh264 PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/openh264.def")
endif()

# CLI
if (OPENH264_BUILD_CONSOLE AND NOT ANDROID AND NOT IOS)
	file(GLOB CONSOLE_COMMON_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/console/common/src/*.cpp")

	add_library(console_common OBJECT ${CONSOLE_COMMON_SOURCE_FILES})
	target_include_directories(console_common PRIVATE ${OPENH264_INCLUDE_DIRS} ${CONSOLE_COMMON_INCLUDE_DIRS})
	
	file(GLOB H264DEC_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/console/dec/src/*.cpp")
	add_executable(h264dec ${H264DEC_SOURCE_FILES})
	target_include_directories(h264dec PRIVATE ${OPENH264_INCLUDE_DIRS} ${CONSOLE_COMMON_INCLUDE_DIRS} "${CMAKE_CURRENT_SOURCE_DIR}/codec/console/dec/inc")
	target_link_libraries(
		h264dec
		PRIVATE
		decoder
		common
		console_common
		Threads::Threads
	)
	
	if (MATH_LIBRARY)
		target_link_libraries(h264dec PRIVATE ${MATH_LIBRARY})
	endif()

	file(GLOB H264ENC_SOURCE_FILES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/codec/console/enc/src/*.cpp")
	add_executable(h264enc ${H264ENC_SOURCE_FILES})
	target_include_directories(h264enc PRIVATE ${OPENH264_INCLUDE_DIRS} ${CONSOLE_COMMON_INCLUDE_DIRS} ${PROCESSING_INCLUDE_DIRS} ${ENCODER_INCLUDE_DIRS})
	target_link_libraries(
		h264enc
		PRIVATE
		encoder
		common
		processing
		console_common
		Threads::Threads
	)
	
	if (MATH_LIBRARY)
		target_link_libraries(h264enc PRIVATE ${MATH_LIBRARY})
	endif()
endif()

install(
	TARGETS openh264
	RUNTIME DESTINATION bin
	LIBRARY DESTINATION lib
	ARCHIVE DESTINATION lib
)

install(
	DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/codec/api/wels/"
	DESTINATION include/wels
	FILES_MATCHING PATTERN "*.h"
)

# pkg-config file
set(prefix "${CMAKE_INSTALL_PREFIX}")
set(libdir "\${prefix}/lib")
set(VERSION "${PROJECT_VERSION}")
set(LIBS "")

if (WIN32)
	set(LIBS_PRIVATE "")
else()
	set(LIBS_PRIVATE "-lm -lpthread")
endif()

configure_file(
	"${CMAKE_CURRENT_SOURCE_DIR}/openh264.pc.in"
	"${CMAKE_CURRENT_BINARY_DIR}/openh264.pc"
	@ONLY
)

install(FILES "${CMAKE_CURRENT_BINARY_DIR}/openh264.pc" DESTINATION lib/pkgconfig)
