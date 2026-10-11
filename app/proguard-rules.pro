# Keep all plugin classes, interfaces, and shared host contracts across the dynamic ClassLoader boundary
-keep class helium314.keyboard.handwriting.plugin.** { *; }
-keep interface helium314.keyboard.handwriting.plugin.** { *; }
-keep class helium314.keyboard.latin.handwriting.** { *; }
-keep interface helium314.keyboard.latin.handwriting.** { *; }

# Keep the entry point class, its constructor, and all public methods,
# as it is loaded dynamically by class name reflection.
-keep class helium314.keyboard.handwriting.plugin.HandwritingRecognizerImpl {
    public <init>();
    public <methods>;
}

-keep class helium314.keyboard.handwriting.plugin.NativeDigitalInkRecognitionException {
    *;
}

# Keep the interface methods to match the host app
-keep interface helium314.keyboard.latin.handwriting.HandwritingRecognizer {
    <methods>;
}
-keep interface helium314.keyboard.latin.handwriting.ModelDownloadListener {
    <methods>;
}

# Keep reflection attributes and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepnames class com.google.mlkit.** extends androidx.work.ListenableWorker

# Keep ML Kit components, JNI classes and Firebase/GMS dependencies
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-dontwarn com.google.**
-dontwarn androidx.**
