// =============================================================================
// BattleLogFormatter.kt
// 役割: BattleEvent を、画面に表示する日本語のログ行に変換する。
//
// 設計判断:
//   - 戦闘ロジック(BattleEngine)と表示文言を分離し、文言変更でロジックのテストが壊れないようにする。
//   - 数値は formatJp(和式表記)で表示する。
//   - 会心は「会心の一撃！」を同じ行の先頭に付ける(1 イベント = 1 行にして UI を単純にするため)。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.num.formatJp

/** 戦闘イベントの日本語整形。 */
object BattleLogFormatter {

    /** イベント 1 件を 1 行の日本語に変換する。 */
    fun format(event: BattleEvent): String = when (event) {
        is BattleEvent.Encounter -> "${event.enemyName}があらわれた！"
        is BattleEvent.Attack -> {
            val prefix = if (event.critical) "会心の一撃！ " else ""
            "${prefix}${event.attacker}の攻撃！ ${event.target}に ${formatJp(event.damage)} のダメージ！"
        }
        is BattleEvent.Evaded -> "${event.target}は ${event.attacker}の攻撃をかわした！"
        is BattleEvent.Defeated -> "${event.name}は倒れた！"
        BattleEvent.Timeout -> "決着がつかなかった… 戦いは打ち切られた。"
    }

    /** イベント列を順序を保ったまま全行変換する。 */
    fun formatAll(events: List<BattleEvent>): List<String> = events.map(::format)
}
