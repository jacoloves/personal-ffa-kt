// =============================================================================
// RecordTab.kt
// 役割: 記録タブ。戦闘数・勝率・累計ゴールド・最高Lv・マスター数・エリアごとの最高 DLv。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.formatJp
import io.github.jacoloves.ffa.domain.world.AreaRules
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Tr

/** 記録タブ。 */
@Composable
fun RecordTab(controller: GameController) {
    val state = controller.state ?: return
    val r = state.records
    // 勝率は小数 1 桁。Double の丸めに頼らず、千分率の整数で計算する。
    val winRate = if (r.battles == 0L) "-" else {
        val permille = r.wins * 1000 / r.battles
        "${permille / 10}.${permille % 10}%"
    }
    val mastered = state.jobs.values.count { it.mastered }
    val unlocked = AreaRules.unlockedAreas(state.maxClearedDlv)

    Div({ classes(AppStyles.card) }) {
        H2 { Text("記録") }
        Table({ classes(AppStyles.table) }) {
            RecordRow("総戦闘数", formatJp(r.battles))
            RecordRow("勝利数", formatJp(r.wins))
            RecordRow("勝率", winRate)
            RecordRow("累計獲得ゴールド", "${formatJp(r.totalGold)} G")
            RecordRow("最高キャラLv", formatJp(r.maxCharLevel))
            RecordRow("マスターした職業", "$mastered / ${state.jobs.size}")
        }
    }
    Div({ classes(AppStyles.card) }) {
        H2 { Text("エリアごとの最高 DLv") }
        Table({ classes(AppStyles.table) }) {
            for (area in AreaId.entries) {
                RecordRow(
                    GameData.area(area).name,
                    if (area in unlocked) "DLv ${state.maxClearedDlv[area] ?: 0}" else "未解放",
                )
            }
        }
    }
}

/** 記録表の 1 行(項目名と値)。 */
@Composable
private fun RecordRow(label: String, value: String) {
    Tr {
        Td({ classes(AppStyles.cellLabel) }) { Text(label) }
        Td({ classes(AppStyles.cell, AppStyles.bold) }) { Text(value) }
    }
}
