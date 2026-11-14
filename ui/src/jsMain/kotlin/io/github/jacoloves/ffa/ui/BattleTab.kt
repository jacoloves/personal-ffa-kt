// =============================================================================
// BattleTab.kt
// 役割: 戦闘タブ。エリア選択、DLv 選択、[戦う] ボタン(クールタイム表示)、戦闘ログ、報酬カード。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.GameEngine
import io.github.jacoloves.ffa.domain.GameState
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.formatJp
import io.github.jacoloves.ffa.domain.world.AreaRules
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** 戦闘タブ全体。 */
@Composable
fun BattleTab(controller: GameController) {
    val state = controller.state ?: return
    AreaSelector(controller, state)
    DlvAndFight(controller, state)
    controller.lastBattle?.let { BattleResultView(it) }
}

/** エリアの選択。未解放のエリアは条件を併記して無効化する。 */
@Composable
private fun AreaSelector(controller: GameController, state: GameState) {
    val unlocked = AreaRules.unlockedAreas(state.maxClearedDlv)
    Div({ classes(AppStyles.card) }) {
        H2 { Text("エリア") }
        Div({ classes(AppStyles.row) }) {
            for (id in AreaId.entries) {
                val def = GameData.area(id)
                val isUnlocked = id in unlocked
                Button({
                    if (id == state.currentArea) classes(AppStyles.selected)
                    if (!isUnlocked) disabled()
                    onClick { controller.dispatch(Command.SelectArea(id)) }
                }) { Text(def.name) }
            }
        }
        // 未解放エリアの条件を、ボタンの下にまとめて併記する
        for (id in AreaId.entries.filter { it !in unlocked }) {
            P({ classes(AppStyles.muted) }) { Text("未解放: ${GameData.area(id).name}(${unlockText(id)})") }
        }
        // 推奨Lv。下回っていれば警告を出す
        val area = GameData.area(state.currentArea)
        P({ classes(AppStyles.muted) }) { Text("推奨Lv ${formatJp(area.recommendedLevel)}") }
        if (state.charLevel < area.recommendedLevel) {
            Div({ classes(AppStyles.banner, AppStyles.bannerWarn); attr("role", "alert") }) {
                Span({ classes(AppStyles.bannerText) }) {
                    Text(
                        "推奨Lv ${formatJp(area.recommendedLevel)} 未満です(今は Lv ${formatJp(state.charLevel)})。" +
                            "負けるとクールタイムが2倍になります。",
                    )
                }
            }
        }
    }
}

/** DLv 選択と [戦う] ボタン。 */
@Composable
private fun DlvAndFight(controller: GameController, state: GameState) {
    val maxDlv = GameEngine.maxSelectableDlv(state, state.currentArea)
    val dlv = state.selectedDlv[state.currentArea] ?: 1
    val remainingMs = controller.cooldownRemainingMs()
    val coolingDown = remainingMs > 0
    val remainingSec = (remainingMs + 999) / 1000 // 切り上げて「あと N 秒」

    Div({ classes(AppStyles.card) }) {
        Div({ classes(AppStyles.spaceBetween) }) {
            Span({ classes(AppStyles.bold) }) { Text("ダンジョンLv(DLv)") }
            Div({ classes(AppStyles.row) }) {
                ActionButton("-", enabled = dlv > 1) { controller.dispatch(Command.SetDlv(dlv - 1)) }
                Span({ classes(AppStyles.bold) }) { Text("DLv $dlv") }
                ActionButton("+", enabled = dlv < maxDlv) { controller.dispatch(Command.SetDlv(dlv + 1)) }
            }
        }
        P({ classes(AppStyles.muted) }) {
            Text(
                if (maxDlv == 1) "今選べるのは DLv 1 だけです。勝つと DLv 2 が選べるようになります。"
                else "選べるのは 1〜$maxDlv です。最高 DLv をクリアすると上が選べるようになります。",
            )
        }
        Button({
            classes(AppStyles.primary, AppStyles.bigButton)
            if (coolingDown) disabled()
            onClick { controller.dispatch(Command.Fight) }
        }) { Text(if (coolingDown) "あと $remainingSec 秒" else "戦う") }
    }
}

/** 戦闘ログと報酬カード。 */
@Composable
private fun BattleResultView(report: BattleReport) {
    Div({ classes(AppStyles.card) }) {
        H2 {
            Span({ classes(if (report.won) AppStyles.win else AppStyles.lose) }) {
                Text(if (report.won) "勝利!" else "敗北…(報酬なし・クールタイム2倍)")
            }
        }
        Div({ classes(AppStyles.log); attr("aria-live", "polite") }) {
            report.lines.forEachIndexed { i, line ->
                val kindClass = when (report.lineKinds.getOrNull(i)) {
                    LogKind.CRIT -> AppStyles.logCrit
                    LogKind.WIN -> AppStyles.logWin
                    LogKind.LOSE -> AppStyles.logLose
                    else -> null
                }
                Div({
                    classes(AppStyles.logLine)
                    if (kindClass != null) classes(kindClass)
                }) { Text(line) }
            }
        }
    }
    val rewards = report.rewards.mapNotNull { describeReward(it) }
    if (rewards.isNotEmpty()) {
        Div({ classes(AppStyles.card) }) {
            H2 { Text("獲得") }
            for (line in rewards) {
                val cls = when (line.kind) {
                    RewardKind.GOLD -> AppStyles.goldText
                    RewardKind.GOOD -> AppStyles.win
                    RewardKind.SPECIAL -> AppStyles.special
                    RewardKind.NORMAL -> null
                }
                P({ if (cls != null) classes(cls) }) { Text(line.text) }
            }
        }
    }
}
