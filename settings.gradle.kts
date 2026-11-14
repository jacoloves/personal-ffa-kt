// =============================================================================
// settings.gradle.kts
// 役割: Gradle ビルド全体の「入口」。プロジェクト名・サブモジュール・
//       プラグイン/依存ライブラリの取得先リポジトリをここで一元管理する。
// =============================================================================

// ルートプロジェクト名。成果物名やIDE上の表示名に使われる。
rootProject.name = "personal-ffa-kt"

// --- プラグインの解決設定 ---------------------------------------------------
// Gradle プラグイン(Kotlin Multiplatform / Compose など)をどこから取るかを指定する。
// ここに書かないと plugins { } ブロックでのプラグイン解決に失敗する。
pluginManagement {
    repositories {
        gradlePluginPortal() // Gradle 公式プラグインポータル
        mavenCentral()       // JetBrains 系プラグインの一部はここにも公開される
        google()             // Compose Multiplatform が参照する Google 製成果物用
    }
}

// --- 依存ライブラリの解決設定 -----------------------------------------------
// 各モジュールの dependencies { } で使うライブラリの取得先を一箇所に集約する。
// 各 build.gradle.kts に repositories { } を書かなくてよくなり、保守性が上がる。
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// --- モジュール構成 -----------------------------------------------------------
// :domain = ゲームのルール・状態(UI 非依存の純粋ロジック。Node 上でテスト可能)
// :ui     = ブラウザ描画層(Compose HTML)。:domain に依存する
include(":domain", ":ui")
