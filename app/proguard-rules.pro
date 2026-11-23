# Compose, Hilt and Navigation ship their own consumer rules.
# Strip verbose/debug logging in release builds so no financial data is ever logged.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
