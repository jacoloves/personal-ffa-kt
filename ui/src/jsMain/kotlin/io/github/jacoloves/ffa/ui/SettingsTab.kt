// =============================================================================
// SettingsTab.kt
// 役割: 設定タブ。クールタイム秒数の変更、セーブデータの削除(2 回確認)、バージョン表示。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.Command
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** 設定タブ。 */
@Composable
fun SettingsTab(controller: GameController) {
    val state = controller.state ?: return
    val sec = state.cooldownSec

    Div({ classes(AppStyles.card) }) {
        H2 { Text("クールタイム") }
        Div({ classes(AppStyles.row) }) {
            ActionButton("-", enabled = sec > Balance.MIN_COOLDOWN_SEC) { controller.dispatch(Command.SetCooldown(sec - 1)) }
            Span({ classes(AppStyles.bold) }) { Text("$sec 秒") }
            ActionButton("+", enabled = sec < Balance.MAX_COOLDOWN_SEC) { controller.dispatch(Command.SetCooldown(sec + 1)) }
        }
        P({ classes(AppStyles.muted) }) {
            Text("${Balance.MIN_COOLDOWN_SEC}〜${Balance.MAX_COOLDOWN_SEC} 秒で設定できます。負けた戦闘のあとは 2 倍になります。")
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
