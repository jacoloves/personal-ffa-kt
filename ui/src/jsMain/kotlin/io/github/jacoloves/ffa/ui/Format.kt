// =============================================================================
// Format.kt
// 役割: 表示用の文字列変換(ステータス、イベントの説明、ログの色分け)をまとめる。
//       画面の Composable から文字列組み立てを追い出し、読みやすく保つ。
// =============================================================================
package io.github.jacoloves.ffa.ui

import io.github.jacoloves.ffa.domain.GameEvent
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.battle.BattleEvent
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.Slot
import io.github.jacoloves.ffa.domain.num.formatJp

/** 戦闘ログ 1 行の色分け種別。 */
enum class LogKind { NORMAL, CRIT, WIN, LOSE }

/** 戦闘イベントから、ログ行の色を決める。 */
fun logKindOf(event: BattleEvent, playerName: String): LogKind = when (event) {
    is BattleEvent.Attack -> if (event.critical) LogKind.CRIT else LogKind.NORMAL
    is BattleEvent.Defeated -> if (event.name == playerName) LogKind.LOSE else LogKind.WIN
    BattleEvent.Timeout -> LogKind.LOSE
    else -> LogKind.NORMAL
}

/** 報酬カードの 1 行の見た目の種別。 */
enum class RewardKind { NORMAL, GOLD, GOOD, SPECIAL }

/** 報酬カードの 1 行。 */
data class RewardLine(val text: String, val kind: RewardKind)

/** ステータスを「HP+15 攻撃+3」のように、0 でない項目だけ並べる。全部 0 なら「なし」。 */
fun statsText(s: Stats): String {
    val parts = listOf("HP" to s.hp, "攻撃" to s.atk, "防御" to s.def, "素早さ" to s.spd, "運" to s.luk)
        .filter { it.second != 0L }
        .map { "${it.first}+${formatJp(it.second)}" }
    return if (parts.isEmpty()) "なし" else parts.joinToString(" ")
}

/** ステータスを (ラベル, 値) の 5 組にする。表示順は HP / 攻撃 / 防御 / 素早さ / 運。 */
fun Stats.rows(): List<Pair<String, Long>> =
    listOf("HP" to hp, "攻撃" to atk, "防御" to def, "素早さ" to spd, "運" to luk)

/** 装備枠の表示名。 */
fun slotLabel(slot: Slot): String = when (slot) {
    Slot.WEAPON -> "武器"
    Slot.ARMOR -> "防具"
    Slot.ACCESSORY -> "装身具"
}

/** アイテム入手イベントの説明文。 */
fun describeItemGained(e: GameEvent.ItemGained): String {
    val name = GameData.item(e.item).name
    return if (e.isNew) "「$name」を入手しました。" else "「$name」が +${e.enhance} に強化されました。"
}

/** エリア解放条件の説明(例: 「はじまりの草原で DLv 10 に到達」)。無条件なら null。 */
fun unlockText(area: AreaId): String? =
    GameData.area(area).unlock?.let { "${GameData.area(it.area).name}で DLv ${it.dlv} に到達" }

/** 戦闘後の報酬系イベントを、画面に出す 1 行へ変換する。表示しないイベントは null。 */
fun describeReward(e: GameEvent): RewardLine? = when (e) {
    is GameEvent.BattleFinished -> null
    is GameEvent.ExpGained -> RewardLine("EXP +${formatJp(e.charExp)} / 職業EXP +${formatJp(e.jobExp)}", RewardKind.NORMAL)
    is GameEvent.GoldGained -> RewardLine("${formatJp(e.amount)} G を獲得", RewardKind.GOLD)
    is GameEvent.CharLevelUp -> RewardLine("レベルアップ! Lv ${formatJp(e.from)} → ${formatJp(e.to)}", RewardKind.GOOD)
    is GameEvent.JobLevelUp ->
        RewardLine("職業レベルアップ! ${GameData.job(e.job).name} Lv ${e.from} → ${e.to}", RewardKind.GOOD)
    is GameEvent.JobMastered -> RewardLine("★ ${GameData.job(e.job).name} をマスターしました!", RewardKind.SPECIAL)
    is GameEvent.JobUnlocked -> RewardLine("上位職「${GameData.job(e.job).name}」が解放されました", RewardKind.SPECIAL)
    is GameEvent.AreaUnlocked -> RewardLine("新エリア「${GameData.area(e.area).name}」が解放されました", RewardKind.SPECIAL)
    is GameEvent.ItemGained -> RewardLine(describeItemGained(e), RewardKind.GOOD)
    is GameEvent.DlvRecord -> RewardLine("${GameData.area(e.area).name}の最高 DLv を更新: DLv ${e.dlv}", RewardKind.GOOD)
}
