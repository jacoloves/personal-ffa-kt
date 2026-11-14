// =============================================================================
// StatusTab.kt
// 役割: ステータスタブ。最終ステータスと内訳、キャラ EXP・職業 EXP のゲージ。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.GameEngine
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.formatJp
import io.github.jacoloves.ffa.domain.progression.Progression
import io.github.jacoloves.ffa.domain.progression.StatCalculator
import kotlin.math.roundToInt
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Tr

/** ステータスタブ。 */
@Composable
fun StatusTab(controller: GameController) {
    val state = controller.state ?: return
    val job = GameData.job(state.currentJob)
    val progress = state.jobs[state.currentJob]
    val final = GameEngine.playerStats(state)
    val steps = state.charLevel - 1
    val base = job.base + job.growth * steps
    val master = StatCalculator.masterBonusTotal(state.jobs)
    val equipment = state.inventory.equipmentStats()
    val masterRatios = listOf(master.hp, master.atk, master.def, master.spd, master.luk)

    Div({ classes(AppStyles.card) }) {
        H2 { Text("ステータス(${job.name})") }
        Table({ classes(AppStyles.table) }) {
            Tr {
                Th({ classes(AppStyles.cellLabel) }) { Text("項目") }
                Th({ classes(AppStyles.cell) }) { Text("最終") }
                Th({ classes(AppStyles.cell) }) { Text("職業+成長") }
                Th({ classes(AppStyles.cell) }) { Text("マスター") }
                Th({ classes(AppStyles.cell) }) { Text("装備") }
            }
            val baseRows = base.rows()
            val equipRows = equipment.rows()
            final.rows().forEachIndexed { i, (label, value) ->
                Tr {
                    Td({ classes(AppStyles.cellLabel) }) { Text(label) }
                    Td({ classes(AppStyles.cell, AppStyles.bold) }) { Text(formatJp(value)) }
                    Td({ classes(AppStyles.cell) }) { Text(formatJp(baseRows[i].second)) }
                    Td({ classes(AppStyles.cell) }) { Text("+${(masterRatios[i] * 100).roundToInt()}%") }
                    Td({ classes(AppStyles.cell) }) { Text("+${formatJp(equipRows[i].second)}") }
                }
            }
        }
        P({ classes(AppStyles.muted) }) {
            Text(
                "最終 = (職業の基礎値 + 成長値×(Lv-1)) × ${Balance.LEVEL_FACTOR_GROWTH}^(Lv-1) × (1 + マスターボーナス) + 装備",
            )
        }
    }

    Div({ classes(AppStyles.card) }) {
        val need = Progression.charExpToNext(state.charLevel)
        P { Text("キャラ Lv ${formatJp(state.charLevel)}  EXP ${formatJp(state.charExp)} / ${formatJp(need)}") }
        Gauge(state.charExp, need)
        if (progress != null) {
            val jobNeed = Progression.jobExpToNext(progress.level)
            P { Text("${job.name} Lv ${progress.level}${if (progress.mastered) " ★マスター" else ""}  EXP ${formatJp(progress.exp)} / ${formatJp(jobNeed)}") }
            Gauge(progress.exp, jobNeed, jobColor = true)
            if (!progress.mastered) {
                P({ classes(AppStyles.muted) }) { Text("職業Lv ${Balance.JOB_MASTER_LEVEL} でマスターし、ステータスボーナスを得ます。") }
            }
        }
    }
}
