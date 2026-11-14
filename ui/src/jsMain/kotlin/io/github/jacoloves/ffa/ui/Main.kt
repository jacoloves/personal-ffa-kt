// =============================================================================
// Main.kt
// 役割: ブラウザ側のエントリポイント。index.html の #root 要素に Compose HTML を描画する。
//       Phase 1 では :domain との配線確認として、タイトルと挨拶を表示するだけ。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.GameInfo
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

/**
 * アプリのエントリポイント。ページ読み込み後にブラウザから呼ばれる。
 * rootElementId は index.html の <div id="root"> と対応する。
 */
fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}

/**
 * ルートの Composable。ダークな背景にタイトルと挨拶を表示する。
 * 最小構成のため、スタイルはインラインで指定している。
 */
@Composable
fun App() {
    Div({
        style {
            backgroundColor(Color("#1e1e2e")) // ダーク背景
            color(Color("#cdd6f4"))           // 明るい文字色
            padding(24.px)
        }
    }) {
        H1 { Text(GameInfo.TITLE) }
        Div { Text(GameInfo.greeting("プレイヤー")) }
    }
}
