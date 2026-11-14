// =============================================================================
// AppStyles.kt
// 役割: アプリ全体のスタイル(ダークテーマ)を StyleSheet に集約する。
//
// 設計判断:
//   - 色は CSS カスタムプロパティ(--bg など)で定義し、各クラスは var(--xxx) で参照する。
//     配色の変更が :root の 1 か所で済む。
//   - Compose HTML の型付き DSL は冗長になるため、宣言は raw("a: b; c: d") のように
//     CSS 文字列で書けるヘルパーを使う(読みやすさ優先)。
//   - レイアウトは flex-wrap と max-width だけで、幅 360px でも横スクロールしないようにする。
// =============================================================================
package io.github.jacoloves.ffa.ui

import org.jetbrains.compose.web.css.StyleScope
import org.jetbrains.compose.web.css.StyleSheet

/** "a: b; c: d" 形式の CSS 宣言を、StyleScope のプロパティとして設定する。 */
private fun StyleScope.raw(css: String) {
    css.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { decl ->
            val i = decl.indexOf(':')
            require(i > 0) { "CSS 宣言「$decl」にコロンがありません。AppStyles の raw(...) の書式を確認してください。" }
            property(decl.substring(0, i).trim(), decl.substring(i + 1).trim())
        }
}

/** アプリのスタイルシート。使うときは `Style(AppStyles)` を 1 回描画する。 */
object AppStyles : StyleSheet() {

    init {
        // ---- 全体 ----
        ":root" style {
            raw(
                "--bg: #12131a; --surface: #1c1e29; --surface-2: #262938; --border: #33374a;" +
                    "--text: #e6e8f0; --text-dim: #9aa0b4; --accent: #7aa2f7; --win: #9ece6a;" +
                    "--lose: #f7768e; --gold: #e0af68; --crit: #ff9e64",
            )
        }
        "*" style { raw("box-sizing: border-box") }
        "body" style {
            raw(
                "margin: 0; background: var(--bg); color: var(--text); line-height: 1.6;" +
                    "font-family: system-ui, -apple-system, 'Hiragino Sans', 'Noto Sans JP', sans-serif;" +
                    "font-variant-numeric: tabular-nums; -webkit-text-size-adjust: 100%",
            )
        }
        "h2" style { raw("font-size: 1.1rem; margin: 0 0 8px") }
        "h3" style { raw("font-size: 1rem; margin: 0 0 6px") }
        "p" style { raw("margin: 4px 0") }
        // ---- ボタン(高さ 44px 以上でタップしやすく) ----
        "button" style {
            raw(
                "font: inherit; color: var(--text); background: var(--surface-2); border: 1px solid var(--border);" +
                    "border-radius: 8px; min-height: 44px; padding: 0 16px; cursor: pointer",
            )
        }
        "button:hover:not(:disabled)" style { raw("border-color: var(--accent)") }
        "button:disabled" style { raw("opacity: 0.45; cursor: not-allowed") }
        "input" style {
            raw(
                "font: inherit; color: var(--text); background: var(--bg); border: 1px solid var(--border);" +
                    "border-radius: 8px; min-height: 44px; padding: 0 12px; width: 100%",
            )
        }
    }

    /** ページ全体の枠(中央寄せ・最大 720px・左右 16px)。 */
    val app by style { raw("max-width: 720px; margin: 0 auto; padding: 0 16px 48px") }

    /** ヘッダー(名前・Lv・職業・ゴールド)。 */
    val header by style {
        raw(
            "display: flex; flex-wrap: wrap; gap: 4px 16px; align-items: baseline; padding: 12px 0;" +
                "border-bottom: 1px solid var(--border)",
        )
    }
    val headerName by style { raw("font-weight: bold; font-size: 1.1rem") }
    val gold by style { raw("color: var(--gold); margin-left: auto") }

    /** タブ列(横スクロールできるチップ)。 */
    val tabs by style {
        raw("display: flex; gap: 8px; overflow-x: auto; padding: 12px 0; white-space: nowrap; scrollbar-width: thin")
    }
    val tab by style { raw("flex: 0 0 auto; border-radius: 999px; padding: 0 16px") }
    val tabActive by style { raw("background: var(--accent); color: var(--bg); border-color: var(--accent); font-weight: bold") }

    /** カード(内容のまとまり)。 */
    val card by style {
        raw("background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 12px 16px; margin-bottom: 12px")
    }
    /** 横並び(折り返しあり)。 */
    val row by style { raw("display: flex; flex-wrap: wrap; gap: 8px; align-items: center") }
    val spaceBetween by style { raw("display: flex; flex-wrap: wrap; gap: 4px 12px; justify-content: space-between; align-items: center") }
    val grow by style { raw("flex: 1 1 160px; min-width: 0") }
    val muted by style { raw("color: var(--text-dim); font-size: 0.9rem") }
    val bold by style { raw("font-weight: bold") }

    /** 主要ボタン。 */
    val primary by style { raw("background: var(--accent); color: var(--bg); border-color: var(--accent); font-weight: bold") }
    val danger by style { raw("border-color: var(--lose); color: var(--lose)") }
    val bigButton by style { raw("width: 100%; min-height: 56px; font-size: 1.2rem; margin: 8px 0") }
    /** エリア選択のチップ(選択中)。 */
    val selected by style { raw("border-color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent)") }

    /** お知らせバナー。 */
    val banner by style {
        raw("display: flex; gap: 8px; align-items: flex-start; justify-content: space-between; border-radius: 8px; padding: 8px 12px; margin: 8px 0")
    }
    val bannerError by style { raw("background: rgba(247,118,142,0.12); border: 1px solid var(--lose)") }
    val bannerInfo by style { raw("background: rgba(122,162,247,0.12); border: 1px solid var(--accent)") }
    val bannerWarn by style { raw("background: rgba(224,175,104,0.12); border: 1px solid var(--gold)") }
    val bannerText by style { raw("flex: 1; min-width: 0; overflow-wrap: anywhere") }

    /** 戦闘ログ(通常フォント・1 行ずつ)。 */
    val log by style {
        raw("background: var(--bg); border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; max-height: 320px; overflow-y: auto")
    }
    val logLine by style { raw("white-space: pre-wrap; overflow-wrap: anywhere") }
    val logCrit by style { raw("color: var(--crit)") }
    val logWin by style { raw("color: var(--win); font-weight: bold") }
    val logLose by style { raw("color: var(--lose); font-weight: bold") }
    val win by style { raw("color: var(--win)") }
    val lose by style { raw("color: var(--lose)") }
    val goldText by style { raw("color: var(--gold)") }
    val special by style { raw("color: var(--crit); font-weight: bold") }

    /** ゲージ(div 2 枚重ね)。 */
    val gauge by style { raw("height: 10px; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; overflow: hidden") }
    val gaugeFill by style { raw("height: 100%; background: var(--accent)") }
    val gaugeFillJob by style { raw("height: 100%; background: var(--win)") }

    /** 職業・装備・ショップのカードを並べるグリッド。 */
    val grid by style { raw("display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px") }

    /** 表。 */
    val table by style { raw("width: 100%; border-collapse: collapse") }
    val cell by style { raw("text-align: right; padding: 4px 6px; border-bottom: 1px solid var(--border)") }
    val cellLabel by style { raw("text-align: left; padding: 4px 6px; border-bottom: 1px solid var(--border); color: var(--text-dim)") }

    /** 名前入力などの中央寄せ画面。 */
    val center by style { raw("max-width: 420px; margin: 0 auto; padding: 48px 16px") }
    val title by style { raw("font-size: 1.8rem; margin: 0 0 8px") }
}
