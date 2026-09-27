plugins {
    alias(libs.plugins.android.application) apply false
    // 显式声明 Kotlin 插件版本，使 AGP 内置 Kotlin 与 Compose/Serialization 编译器插件保持一致。
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
