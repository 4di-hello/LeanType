# Keep all plugin classes, interfaces, and shared host contracts across the dynamic ClassLoader boundary
-keep class com.leanbitlab.leantype.cjk.plugin.** { *; }
-keep interface com.leanbitlab.leantype.cjk.plugin.** { *; }
-keep class helium314.keyboard.latin.cjk.** { *; }
-keep interface helium314.keyboard.latin.cjk.** { *; }

# Keep the dynamic entrypoint class and its public parameterless constructor
-keep class com.leanbitlab.leantype.cjk.plugin.CjkEngineImpl {
    public <init>();
    public <methods>;
}

# Keep the JNI bridge and all native methods
-keep class com.leanbitlab.leantype.cjk.plugin.PinyinDecoderJni {
    native <methods>;
    public static <fields>;
    public static <methods>;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep reflection attributes, annotations, and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Prevent warnings for optional dependencies
-dontwarn androidx.**
