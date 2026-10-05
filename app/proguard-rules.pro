# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.voquab.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.voquab.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.voquab.** {
    kotlinx.serialization.KSerializer serializer(...);
}
