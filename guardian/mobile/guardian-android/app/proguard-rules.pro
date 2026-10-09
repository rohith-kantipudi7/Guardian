# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.guardian.app.** { *** Companion; }
-keepclasseswithmembers class com.guardian.app.** { kotlinx.serialization.KSerializer serializer(...); }
