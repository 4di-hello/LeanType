# ProGuard / R8 rules for LeanType GIF Plugin

# Keep AIDL IPC interfaces, Stubs, and Callbacks
-keep class com.leanbitlab.leantype.gif.IGifEngine** { *; }
-keep interface com.leanbitlab.leantype.gif.IGifEngine** { *; }
-keep class com.leanbitlab.leantype.gif.IGifCallback** { *; }
-keep interface com.leanbitlab.leantype.gif.IGifCallback** { *; }
-keepclassmembers class * extends com.leanbitlab.leantype.gif.IGifEngine$Stub { *; }

# Keep Parcelable models and CREATOR fields
-keep class com.leanbitlab.leantype.gif.GifItem { *; }
-keep class com.leanbitlab.leantype.gif.ProviderInfo { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep IPC Service entry point, ContentProvider, and security caller checks
-keep class com.leanbitlab.leantype.gif.GifEngineService { *; }
-keep class com.leanbitlab.leantype.gif.Contract { *; }
-keep class com.leanbitlab.leantype.gif.SigningCerts { *; }
-keep class com.leanbitlab.leantype.gif.security.CallerCheck { *; }
-keep class com.leanbitlab.leantype.gif.storage.ThumbnailProvider { *; }

# Keep attributes required for IPC reflection, annotations, and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# OkHttp and Coil rules to prevent runtime reflection issues
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-keep class coil.** { *; }
