// =============================================================================
// EquipTab.kt
// 役割: 装備タブ。枠(武器・防具・装身具)ごとに、装備中のアイテムと所持品を表示し、付け替える。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.Slot
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** 装備タブ。 */
@Composable
fun EquipTab(controller: GameController) {
    val state = controller.state ?: return
    for (slot in Slot.entries) {
        val equippedId = state.equipped[slot]
        val owned = state.owned.keys.filter { GameData.item(it).slot == slot }
        Div({ classes(AppStyles.card) }) {
            H2 { Text(slotLabel(slot)) }
            P({ classes(AppStyles.muted) }) {
                Text(
                    if (equippedId == null) "装備中: なし"
                    else "装備中: ${GameData.item(equippedId).name} +${state.owned[equippedId] ?: 0}",
                )
            }
            if (owned.isEmpty()) {
                P({ classes(AppStyles.muted) }) { Text("この枠の所持品はありません。ショップで買うか、敵のドロップで手に入れましょう。") }
            }
            for (id in owned) {
                val def = GameData.item(id)
                val enhance = state.owned[id] ?: 0
                val isEquipped = id == equippedId
                Div({ classes(AppStyles.spaceBetween) }) {
                    Div({ classes(AppStyles.grow) }) {
                        Span({ classes(AppStyles.bold) }) { Text("${def.name} +$enhance") }
                        P({ classes(AppStyles.muted) }) { Text(statsText(def.statsAt(enhance))) }
                    }
                    ActionButton(if (isEquipped) "装備中" else "装備する", enabled = !isEquipped) {
                        controller.dispatch(Command.Equip(id))
                    }
                }
            }
        }
    }
}
