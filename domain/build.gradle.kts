// =============================================================================
// domain/build.gradle.kts
// 役割: ゲームのルール・状態を置く純 Kotlin モジュール(UI 非依存)。
//       Kotlin/JS ターゲットでビルドし、テストは Node.js 上で実行する。
// =============================================================================
plugins {
    // Kotlin Multiplatform。将来 JVM 等へ広げる余地を残しつつ、今は JS のみ。
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // js(IR) は非推奨(Kotlin 2.4 では IR が唯一のコンパイラ)。引数なしの js { } を使う。
    js {
        // nodejs() を指定する理由:
        //   browser() のテストは Karma + ヘッドレスブラウザ(Chrome 等)が必要で、
        //   環境依存・低速になりがち。domain は DOM を使わない純ロジックなので、
        //   Node.js 上でテストすれば十分かつ簡単(Kotlin が Node を自動ダウンロードする)。
        nodejs()
    }

    sourceSets {
        // commonMain は依存ライブラリなし(Phase 1 では何も追加しない方針)。
        commonMain {
            // 依存なし
        }
        // commonTest: 全ターゲット共通のテスト。kotlin-test が JS 用アダプタを自動解決する。
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
