# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.opencode.android.**$$serializer { *; }
-keepclassmembers class dev.opencode.android.** {
    *** Companion;
}
-keepclasseswithmembers class dev.opencode.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}