# R8 规则。release 包开启混淆 + 资源压缩，目标 APK < 15MB。

# kotlinx.serialization：保留 @Serializable 类的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.myapp.**$$serializer { *; }
-keepclassmembers class com.myapp.** {
    *** Companion;
}

# 类型安全导航依赖路由类的元数据
-keep class com.myapp.core.ui.navigation.Route** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Compose 在 R8 下已有默认规则，无需额外配置
