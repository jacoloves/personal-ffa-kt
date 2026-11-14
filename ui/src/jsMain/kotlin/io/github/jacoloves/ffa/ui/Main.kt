// =============================================================================
// Main.kt
// 役割: ブラウザ側のエントリポイント。index.html の #root 要素に Compose HTML を描画する。
// =============================================================================
package io.github.jacoloves.ffa.ui

import org.jetbrains.compose.web.renderComposable

/** アプリのバージョン表示(設定タブ)。 */
const val APP_VERSION = "0.7.0"

/**
 * ページ読み込み後にブラウザから呼ばれる。
 * rootElementId は index.html の <div id="root"> と対応する。
 */
fun main() {
    // 保存先・時計・乱数はここで組み立てて controller に渡す(依存の組み立ては入口に集める)。
    val controller = GameController(LocalStorageGameStorage())
    renderComposable(rootElementId = "root") {
        App(controller)
    }
}
