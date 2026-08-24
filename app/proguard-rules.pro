# Add project specific ProGuard rules here.
# Keep OkHttp/okio internals that are accessed via reflection.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

# Keep data models we (de)serialize by field name via org.json reflection-free parsing.
-keep class com.klarl.accessibility.model.** { *; }
-keep class com.klarl.accessibility.ai.** { *; }
