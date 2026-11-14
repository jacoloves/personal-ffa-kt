// =============================================================================
// GameState.kt
// 役割: セーブ対象となる「ゲームの全状態」を表す不変の data class。
//
// 設計判断:
//   - 不変(val のみ)にし、変更は copy() で新しい状態を作る。GameEngine は純粋関数として動く。
//   - @Serializable でそのまま JSON にする。enum は名前の文字列で保存されるため、
//     enum の名前の変更はセーブ互換性を壊す(変更するなら schemaVersion を上げて migrate で対応する)。
//   - 時刻は nextBattleAt(エポックミリ秒)で持つ。クールタイム判定は Clock から渡された時刻と比べるだけ。
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import io.github.jacoloves.ffa.domain.data.Slot
import io.github.jacoloves.ffa.domain.inventory.Inventory
import io.github.jacoloves.ffa.domain.progression.JobRules
import kotlinx.serialization.Serializable

/**
 * ゲームの全状態。
 *
 * @property schemaVersion セーブ形式のバージョン(将来の移行用)
 * @property playerName プレイヤー名(戦闘ログに使う)
 * @property charLevel キャラLv(上限なし。職業を変えても保持される)
 * @property charExp 現在のキャラ EXP(次のレベルまでの途中経過)
 * @property currentJob 現在の職業
 * @property jobs 職業ごとの進行状況
 * @property gold 所持ゴールド
 * @property owned 所持アイテムと強化値(+N)
 * @property equipped 枠ごとの装備
 * @property currentArea 現在のエリア
 * @property selectedDlv エリアごとの選択中 DLv
 * @property maxClearedDlv エリアごとにクリアした最高 DLv(未クリアは 0 または記録なし)
 * @property cooldownSec 戦闘後のクールタイム(秒)の設定
 * @property nextBattleAt 次に戦えるようになる時刻(エポックミリ秒)
 * @property records 実績・統計
 */
@Serializable
data class GameState(
    val schemaVersion: Int = 2,
    val playerName: String,
    val charLevel: Long = 1,
    val charExp: Long = 0,
    val currentJob: JobId = JobId.WARRIOR,
    val jobs: Map<JobId, JobProgress>,
    val gold: Long = 0,
    val owned: Map<ItemId, Int>,
    val equipped: Map<Slot, ItemId>,
    val currentArea: AreaId = AreaId.MEADOW,
    val selectedDlv: Map<AreaId, Int>,
    val maxClearedDlv: Map<AreaId, Int>,
    val cooldownSec: Int = Balance.DEFAULT_COOLDOWN_SEC,
    val nextBattleAt: Long = 0,
    val records: Records = Records(),
) {
    /** owned / equipped を [Inventory] として扱うための橋渡し。 */
    val inventory: Inventory get() = Inventory(owned, equipped)

    companion object {
        /**
         * 新規ゲームの初期状態を作る。名前の前後の空白は取り除く。
         * 名前が空・空白のみなら [GameError.InvalidName]。
         */
        fun new(playerName: String): GameResult<GameState> {
            val name = playerName.trim()
            if (name.isEmpty()) return GameResult.Err(GameError.InvalidName)
            val start = Inventory.initial()
            return GameResult.Ok(
                GameState(
                    playerName = name,
                    jobs = JobRules.initialJobs(),
                    owned = start.owned,
                    equipped = start.equipped,
                    selectedDlv = AreaId.entries.associateWith { 1 },
                    maxClearedDlv = AreaId.entries.associateWith { 0 },
                ),
            )
        }
    }
}

/**
 * 実績・統計。
 *
 * @property battles 戦闘回数
 * @property wins 勝利回数
 * @property totalGold 獲得したゴールドの累計
 * @property maxCharLevel 到達した最高キャラLv
 */
@Serializable
data class Records(
    val battles: Long = 0,
    val wins: Long = 0,
    val totalGold: Long = 0,
    val maxCharLevel: Long = 1,
)
