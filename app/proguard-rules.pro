# ANDCODEDIT ProGuard / R8 rules

# Keep line numbers for crash reports, hide original source file name
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose
-keep class androidx.compose.** { *; }

# JGit relies on reflection and service loaders
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# dexlib2 / smali
-keep class org.jf.** { *; }
-keep class com.android.tools.smali.** { *; }
-dontwarn org.jf.**
-dontwarn com.android.tools.smali.**

# Sora Editor
-keep class io.github.rosemoe.** { *; }
-dontwarn io.github.rosemoe.**

# In-process language engines (reflection / dynamic class loading)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class org.python.** { *; }
-dontwarn org.python.**
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**
-keep class bsh.** { *; }
-dontwarn bsh.**
# Our Rhino-exposed console bridge is reached via reflection.
-keep class com.andcodedit.lang.InProcessRunner$JsConsole { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
