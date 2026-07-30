# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Segment field names must stay stable — they're read by the pre-KMP native app's
# Gson-written rows (see data/model/Segment.kt). kotlinx-serialization ships its own
# consumer rules, but keep this explicit given the upgrade-in-place invariant.
-keepclassmembers,allowobfuscation class com.nomnomsom.armstrongandgetty.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.nomnomsom.armstrongandgetty.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nomnomsom.armstrongandgetty.data.model.**$$serializer { *; }
-keep class com.nomnomsom.armstrongandgetty.data.model.** { <fields>; }