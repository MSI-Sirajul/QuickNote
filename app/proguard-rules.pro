# Keep Room Database entities and DAOs from obfuscation breaks
-keep class com.example.data.** { *; }

# Keep native JNI methods for signature verification
-keepclasseswithmembernames class com.example.security.SecurityManager {
    native <methods>;
}

# Keep Biometric components
-keep class androidx.biometric.** { *; }

# Keep Glance app widget and layout dependencies
-keep class com.example.ui.widget.** { *; }
-keep class androidx.glance.** { *; }

# General optimization guidelines
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable

