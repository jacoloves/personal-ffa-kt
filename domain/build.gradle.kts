// =============================================================================
// domain/build.gradle.kts
// 役割: ゲームのルール・状態を置く純 Kotlin モジュール(UI 非依存)。
//       Kotlin/JS ターゲットでビルドし、テストは Node.js 上で実行する。
// =============================================================================
plugins {
    // Kotlin Multiplatform。将来 JVM 等へ広げる余地を残しつつ、今は JS のみ。
    alias(libs.plugins.kotlin.multiplatform)
    // GameState 等の @Serializable を処理する(セーブデータの JSON 化に使う)。
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // js(IR) は非推奨(Kotlin 2.4 では IR が唯一のコンパイラ)。引数なしの js { } を使う。
    js {
        // nodejs() を指定する理由:
        //   browser() のテストは Karma + ヘッドレスブラウザ(Chrome 等)が必要で、
        //   環境依存・低速になりがち。domain は DOM を使わない純ロジックなので、
        //   Node.js 上でテストすれば十分かつ簡単(Kotlin が Node を自動ダウンロードする)。
        nodejs {
            testTask {
                // mocha の既定タイムアウトは 2 秒。バランスシミュレーション(数千戦 × 複数 seed)は
                // 数秒かかるため、テストごとの上限を 120 秒に延ばす(無限ループの検出にはなお十分短い)。
                useMocha { timeout = "120s" }
            }
        }
    }

    sourceSets {
        // commonMain の依存は kotlinx-serialization-json のみ(セーブの JSON 化)。
        // GameState が公開 API で @Serializable を使うため、利用側(ui)にも見えるよう api にする。
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }
        // commonTest: 全ターゲット共通のテスト。kotlin-test が JS 用アダプタを自動解決する。
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
