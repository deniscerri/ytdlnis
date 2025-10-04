# Set NDK path
NDK=/path/to/android-ndk

# Target: arm64-v8a, min API 24
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang -O2 -static -o android-js-arm64 fake-node.c

# armeabi-v7a
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi14-clang -O2 -static -o android-js-arm fake-node.c

# x86
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android24-clang -O2 -static -o android-js-x86 fake-node.c

# x86_64
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android24-clang -O2 -static -o android-js-x86_64 fake-node.c
