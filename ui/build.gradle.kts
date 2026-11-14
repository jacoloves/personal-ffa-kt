// =============================================================================
// ui/build.gradle.kts
// 役割: ブラウザで動く画面層(Compose Multiplatform for Web / Compose HTML)。
//       :domain に依存し、最終的に app.js + index.html を生成する。
// =============================================================================
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Kotlin 2.0 以降、@Composable を変換するコンパイラは Kotlin 側のプラグインが担う。
    // これが無いと @Composable 関数のコンパイルに失敗する。
    alias(libs.plugins.kotlin.compose)
    // Compose Multiplatform 本体(ランタイム・HTML ライブラリの配布と Gradle 連携)。
    alias(libs.plugins.compose)
}

kotlin {
    // js(IR) は非推奨(Kotlin 2.4 では IR が唯一のコンパイラ)。引数なしの js { } を使う。
    js {
        browser {
            commonWebpackConfig {
                // webpack の出力ファイル名を固定し、index.html の <script src="app.js"> と一致させる。
                outputFileName = "app.js"
            }
        }
        // 実行可能な JS(エントリポイント付き)を生成する。ライブラリでなくアプリなので必須。
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":domain"))   // ゲームロジック
                implementation(libs.compose.runtime)  // Composable の状態管理ランタイム
                implementation(libs.compose.html.core) // DOM を Composable で記述する API
            }
        }
    }
}
