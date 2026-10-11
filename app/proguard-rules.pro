# Keep all plugin classes, interfaces, and shared host contracts across the dynamic ClassLoader boundary
-keep class helium314.keyboard.translation.plugin.** { *; }
-keep interface helium314.keyboard.translation.plugin.** { *; }
-keep class helium314.keyboard.latin.translation.** { *; }
-keep interface helium314.keyboard.latin.translation.** { *; }

-keep class helium314.keyboard.translation.plugin.TranslationProviderImpl {
    public <init>();
    public <methods>;
}

-keep class helium314.keyboard.translation.plugin.MlKitTranslatorBridge {
    public <init>(android.content.Context);
    public <methods>;
}

-keep interface helium314.keyboard.latin.translation.ITranslationProvider {
    <methods>;
}

-keep interface helium314.keyboard.latin.translation.TranslationModelDownloadListener {
    <methods>;
}

# Keep reflection attributes and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepnames class com.google.mlkit.** extends androidx.work.ListenableWorker

-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-dontwarn com.google.**
-dontwarn androidx.**
