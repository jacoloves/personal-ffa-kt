// =============================================================================
// SettingsTab.kt
// 役割: 設定タブ。クールタイム秒数の変更、セーブデータの削除(2 回確認)、バージョン表示。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.Command
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** 設定タブ。 */
@Composable
fun SettingsTab(controller: GameController) {
    val state = controller.state ?: return
    val sec = state.cooldownSec
    // 入力欄の下書き(文字列)。保存済みの値が変わったら(± や設定の成功で)入れ替える
    var draft by remember(sec) { mutableStateOf(sec.toString()) }

    Div({ classes(AppStyles.card) }) {
        H2 { Text("クールタイム") }
        Div({ classes(AppStyles.row) }) {
            ActionButton("-", enabled = sec > Balance.MIN_COOLDOWN_SEC) { controller.dispatch(Command.SetCooldown(sec - 1)) }
            Span({ classes(AppStyles.bold) }) { Text("$sec 秒") }
            ActionButton("+", enabled = sec < Balance.MAX_COOLDOWN_SEC) { controller.dispatch(Command.SetCooldown(sec + 1)) }
        }
        // 90 秒など大きな値を ±1 だけで変えるのは大変なので、秒数を直接入力して設定できるようにする
        Div({ classes(AppStyles.row) }) {
            Input(InputType.Text) {
                value(draft)
                attr("inputmode", "numeric")
                attr("aria-label", "クールタイム(秒)")
                attr("id", "cooldown-input")
                onInput { draft = it.value }
                onKeyDown { if (it.key == "Enter") applyCooldown(controller, draft) }
            }
            ActionButton("設定") { applyCooldown(controller, draft) }
        }
        P({ classes(AppStyles.muted) }) {
            Text("${Balance.MIN_COOLDOWN_SEC}〜${Balance.MAX_COOLDOWN_SEC} 秒で設定できます(初期値 ${Balance.DEFAULT_COOLDOWN_SEC} 秒)。勝っても負けても同じ待ち時間です。")
        }
    }

    Div({ classes(AppStyles.card) }) {
        H2 { Text("セーブデータ") }
        P({ classes(AppStyles.muted) }) { Text("進行はこのブラウザの localStorage に自動で保存されます。削除すると元に戻せません。") }
        Button({
            classes(AppStyles.danger)
            onClick {
                // 誤操作を防ぐため、確認を 2 回行う
                if (window.confirm("セーブデータを削除します。よろしいですか?") &&
                    window.confirm("本当に削除しますか? この操作は元に戻せません。")
                ) {
                    controller.deleteSave()
                }
            }
        }) { Text("セーブデータを削除") }
    }

    Div({ classes(AppStyles.card) }) {
        P({ classes(AppStyles.muted) }) { Text("バージョン $APP_VERSION") }
    }
}

/**
 * 入力欄の文字列をクールタイム(秒)として設定する。
 * 数字として読めない場合は、何が・なぜ・どうすればを含むエラーをお知らせに出す。
 * 範囲外(1〜180 以外)の判定と文言は GameEngine(InvalidCooldown)に任せる。
 */
private fun applyCooldown(controller: GameController, text: String) {
    val n = text.trim().toIntOrNull()
    if (n == null) {
        controller.showError(
            "「$text」はクールタイムとして読めません。秒数は整数で入力する必要があります。" +
                "${Balance.MIN_COOLDOWN_SEC}〜${Balance.MAX_COOLDOWN_SEC} の整数を入れて「設定」を押してください。",
        )
        return
    }
    controller.dispatch(Command.SetCooldown(n))
}
