// =============================================================================
// Command.kt
// 役割: UI からエンジンへ渡す「操作(Command)」、エンジンが返す「結果(CommandResult)」、
//       結果に添える「出来事(GameEvent)」の型定義。
//
// UI の使い方:
//   val result = GameEngine.execute(state, Command.Fight, random, clock)
//   when (result) {
//     is CommandResult.Success -> { state = result.state; result.events を表示 }
//     is CommandResult.Failure -> { result.error.message をそのまま表示 }
//   }
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.battle.BattleOutcome
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId

/** プレイヤーの操作。 */
sealed interface Command {
    /** 現在のエリア・選択中の DLv で 1 回戦う。 */
    data object Fight : Command

    /** エリアを移動する(解放済みのエリアのみ)。 */
    data class SelectArea(val area: AreaId) : Command

    /** 現在のエリアの DLv を選ぶ(1 〜 クリア済み最高 DLv + 1)。 */
    data class SetDlv(val dlv: Int) : Command

    /** 転職する。 */
    data class ChangeJob(val job: JobId) : Command

    /** ショップで購入する。 */
    data class Buy(val item: ItemId) : Command

    /** 所持品を装備する。 */
    data class Equip(val item: ItemId) : Command

    /** 戦闘後のクールタイム(秒)を設定する(1〜30)。 */
    data class SetCooldown(val sec: Int) : Command
}

/** コマンドの結果。成功なら新しい状態とイベント、失敗ならエラー(状態は変わらない)。 */
sealed interface CommandResult {
    data class Success(val state: GameState, val events: List<GameEvent>) : CommandResult
    data class Failure(val error: GameError) : CommandResult
}

/** コマンドの結果として起きた出来事。リストの順序が表示順。 */
sealed interface GameEvent {
    /** 戦闘が終わった。[logLines] は表示用の日本語ログ(1 イベント 1 行)。 */
    data class BattleFinished(val outcome: BattleOutcome, val logLines: List<String>) : GameEvent

    /** 経験値を獲得した(キャラ EXP と職業 EXP)。 */
    data class ExpGained(val charExp: Long, val jobExp: Long) : GameEvent

    /** ゴールドを獲得した。 */
    data class GoldGained(val amount: Long) : GameEvent

    /** キャラのレベルが上がった([from] → [to])。 */
    data class CharLevelUp(val from: Long, val to: Long) : GameEvent

    /** 職業のレベルが上がった。 */
    data class JobLevelUp(val job: JobId, val from: Int, val to: Int) : GameEvent

    /** 職業をマスターした(ステータス永続ボーナスを得る)。 */
    data class JobMastered(val job: JobId) : GameEvent

    /** 上位職が解放された。 */
    data class JobUnlocked(val job: JobId) : GameEvent

    /** エリアが解放された。 */
    data class AreaUnlocked(val area: AreaId) : GameEvent

    /** アイテムを入手した。[isNew] が false なら強化値が上がった([enhance] は入手後の +N)。 */
    data class ItemGained(val item: ItemId, val enhance: Int, val isNew: Boolean) : GameEvent

    /** そのエリアで最高 DLv の記録を更新した。 */
    data class DlvRecord(val area: AreaId, val dlv: Int) : GameEvent
}
