// =============================================================================
// JobTab.kt
// 役割: 転職タブ。全職業をカードで表示し、転職できる職業には [転職する] ボタンを出す。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.progression.JobRules
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** 転職タブ。 */
@Composable
fun JobTab(controller: GameController) {
    val state = controller.state ?: return
    val unlocked = JobRules.unlockedJobs(state.jobs)
    P({ classes(AppStyles.muted) }) {
        Text("転職は無料で、いつでもできます。キャラLv・装備・ゴールドはそのまま引き継がれます。職業Lv ${Balance.JOB_MASTER_LEVEL} でマスターすると永続ボーナスが得られます。")
    }
    Div({ classes(AppStyles.grid) }) {
        for (id in JobId.entries) {
            val def = GameData.job(id)
            val progress = state.jobs[id]
            val isCurrent = id == state.currentJob
            val isUnlocked = id in unlocked
            Div({
                classes(AppStyles.card)
                if (isCurrent) classes(AppStyles.selected)
            }) {
                H3 { Text("${def.name}${if (progress?.mastered == true) " ★" else ""}") }
                P({ classes(AppStyles.muted) }) {
                    Text("Tier ${def.tier} / 職業Lv ${progress?.level ?: 1}")
                }
                P({ classes(AppStyles.muted) }) { Text("基礎値: ${statsText(def.base)}") }
                P({ classes(AppStyles.muted) }) { Text("成長/Lv: ${statsText(def.growth)}") }
                if (def.requires.isNotEmpty()) {
                    val need = def.requires.joinToString("・") { GameData.job(it).name }
                    P({ classes(AppStyles.muted) }) {
                        Text("解放条件: $need をマスター${if (isUnlocked) "(達成)" else ""}")
                    }
                }
                val label = when {
                    isCurrent -> "現在の職業"
                    !isUnlocked -> "未解放"
                    else -> "転職する"
                }
                ActionButton(label, enabled = !isCurrent && isUnlocked) { controller.dispatch(Command.ChangeJob(id)) }
            }
        }
    }
}
