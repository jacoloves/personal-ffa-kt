// =============================================================================
// ShopTab.kt
// 役割: ショップタブ。解放済みエリアの商品を表示し、購入する。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.formatJp
import io.github.jacoloves.ffa.domain.world.AreaRules
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** ショップタブ。 */
@Composable
fun ShopTab(controller: GameController) {
    val state = controller.state ?: return
    val unlocked = AreaRules.unlockedAreas(state.maxClearedDlv)
    P({ classes(AppStyles.goldText) }) { Text("所持ゴールド: ${formatJp(state.gold)} G") }
    P({ classes(AppStyles.muted) }) { Text("同じ装備をもう一度手に入れると、強化値が +1 されます。") }

    for (area in AreaId.entries) {
        Div({ classes(AppStyles.card) }) {
            H2 { Text(GameData.area(area).name) }
            if (area !in unlocked) {
                P({ classes(AppStyles.muted) }) { Text("未解放です(${unlockText(area)})。解放すると商品が買えます。") }
                return@Div
            }
            for (item in GameData.shopItems(area)) {
                val price = checkNotNull(item.price) { "ショップ品 ${item.id} に価格がありません。GameData.items を確認してください。" }
                val enhance = state.owned[item.id]
                val shortage = price - state.gold
                Div({ classes(AppStyles.spaceBetween) }) {
                    Div({ classes(AppStyles.grow) }) {
                        Span({ classes(AppStyles.bold) }) { Text("${item.name}(${slotLabel(item.slot)})") }
                        P({ classes(AppStyles.muted) }) {
                            Text(statsText(item.stats) + if (enhance != null) " / 所持中(+$enhance)購入で +${enhance + 1}" else "")
                        }
                        P({ classes(AppStyles.goldText) }) {
                            Text("${formatJp(price)} G" + if (shortage > 0) "(あと ${formatJp(shortage)} G 足りません)" else "")
                        }
                    }
                    ActionButton("購入", enabled = shortage <= 0) { controller.dispatch(Command.Buy(item.id)) }
                }
            }
        }
    }
}
