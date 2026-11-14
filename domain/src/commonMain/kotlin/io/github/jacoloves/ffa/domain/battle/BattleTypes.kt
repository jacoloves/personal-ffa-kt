// =============================================================================
// BattleTypes.kt
// 役割: 戦闘で使うデータ型(戦闘者・イベント・結果)をまとめる。
//
// 設計判断:
//   - 戦闘は「イベントのリスト」として結果を返し、文章化は BattleLogFormatter に分離する。
//     こうすると、ロジックのテストが文字列に依存せず、表示形式を変えてもロジックは影響を受けない。
//   - すべて不変の data class / data object。等価比較できるので、テストで結果をそのまま比較できる。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.Stats

/** 戦闘に参加する 1 人分。HP は [stats] の hp が戦闘開始時の値(戦闘ごとに全快)。 */
data class Combatant(val name: String, val stats: Stats)

/** 戦闘中に起きた出来事。リストの順序が時系列。 */
sealed interface BattleEvent {
    /** 敵に遭遇した。戦闘ログの最初のイベント。 */
    data class Encounter(val enemyName: String) : BattleEvent

    /** 攻撃がヒットした。[critical] が true なら会心(ダメージ 2 倍)。 */
    data class Attack(
        val attacker: String,
        val target: String,
        val damage: Long,
        val critical: Boolean,
    ) : BattleEvent

    /** [target] が [attacker] の攻撃を回避した。 */
    data class Evaded(val attacker: String, val target: String) : BattleEvent

    /** [name] が HP 0 以下になり戦闘不能になった。 */
    data class Defeated(val name: String) : BattleEvent

    /** 最大ラウンド数に達しても決着しなかった(プレイヤーの敗北扱い)。 */
    data object Timeout : BattleEvent
}

/**
 * 戦闘の結果。
 *
 * @property playerWon プレイヤーが勝ったか(タイムアウトは false)
 * @property events 時系列のイベント一覧
 * @property rounds 決着までに行われたラウンド数(タイムアウトは MAX_ROUNDS)
 */
data class BattleOutcome(val playerWon: Boolean, val events: List<BattleEvent>, val rounds: Int)
