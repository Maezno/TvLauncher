# Release hardening. R8 full mode is the AGP default.
-repackageclasses ''
-allowaccessmodification

# No logging in release builds (no package names / app lists in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

-dontwarn org.jetbrains.annotations.**
