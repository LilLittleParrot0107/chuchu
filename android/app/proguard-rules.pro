# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces
-renamesourcefileattribute SourceFile

# === JNI: keep native bridge classes ===
-keepclassmembers class com.jossephus.chuchu.service.terminal.GhosttyBridge {
    *** native*;
}
-keepclassmembers class com.jossephus.chuchu.service.ssh.NativeSshBridge {
    *** native*;
}

# === Room: keep entities, DAOs, and database ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# === Compose ===
# KHONG keep toan bo androidx.compose. Luat do tung o day va no khoa 75% so
# method trong dex khoi R8: do duoc 40087/52773 method la Compose, giu nguyen
# ten day du (vd LazyStaggeredGridItemProviderKt — app khong dung staggered
# grid o dau ca). Compose ship san consumer proguard rules, khong can keep tay.
# Bo dong do cat ~1.5MB va keo so method ra xa tran 65536 cua mot dex.
-dontwarn androidx.compose.**

# === Kotlin coroutines ===
-dontwarn kotlinx.coroutines.**

# === Android components ===
# AGP tu sinh keep rule cho moi class khai bao trong manifest, nen hai dong
# `extends Activity/Service` la thua. Giu lai AppCompatActivity vi no la
# lop CHA trung gian, khong nam trong manifest.
-keep class * extends androidx.appcompat.app.AppCompatActivity

# === Serialization ===
-keepattributes *Annotation*, Signature, ExceptionHandler

# === Enum serialization ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}