// =============================================================================
// build.gradle.kts (ルート)
// 役割: プラグインのバージョンを「ルートで一度だけ」解決しておく場所。
//       `apply false` は「クラスパスに載せるだけで、ルート自身には適用しない」の意味。
//       こうすると各サブモジュールが バージョン指定なし で同じプラグインを使えるため、
//       モジュール間でバージョンが食い違う事故(クラスローダ競合)を防げる。
// 実際のバージョン番号は gradle/libs.versions.toml (Version Catalog) に集約している。
// =============================================================================
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false // Kotlin Multiplatform 本体
    alias(libs.plugins.kotlin.compose) apply false       // Kotlin 2.x 用 Compose コンパイラプラグイン
    alias(libs.plugins.kotlin.serialization) apply false // @Serializable 用コンパイラプラグイン
    alias(libs.plugins.compose) apply false              // Compose Multiplatform (JetBrains)
}
