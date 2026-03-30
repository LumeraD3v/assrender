# Keep JNI methods
-keepclasseswithmembernames class io.github.assrender.NativeBridge {
    native <methods>;
}

# Keep public API
-keep class io.github.assrender.AssRenderer { *; }
-keep class io.github.assrender.AssRenderConfig { *; }
-keep class io.github.assrender.SubtitleTrack { *; }
