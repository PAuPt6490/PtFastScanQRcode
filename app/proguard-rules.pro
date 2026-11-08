# zxing core: keep the decoder paths we call reflectively-free but obfuscation-safe.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep our own entry points that the framework instantiates by name.
-keep class com.ptfast.scanqr.MainActivity { *; }
-keep class com.ptfast.scanqr.SettingsActivity { *; }
-keep class com.ptfast.scanqr.ScanTileService { *; }
-keep class com.ptfast.scanqr.QuickLaunchService { *; }
-keep class com.ptfast.scanqr.CaptureProvider { *; }
-keep class com.ptfast.scanqr.ScanOverlay { *; }

# Strip logging in release.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
