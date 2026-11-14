// =============================================================================
// StatCalculator.kt
// 役割: 職業・キャラLv・マスターボーナス・装備から、戦闘で使う最終ステータスを計算する。
//
// 式: ((job.base + job.growth × (lv−1)) × 1.03^(lv−1)) × (1 + マスターボーナス合計) + 装備
//   - 先に倍率(レベル・マスター)をかけ、装備は最後に足す。装備はマスターボーナスの対象外。
//   - 装備の合計は呼び出し側(Phase 5 の Inventory)が Stats として渡す。
//   - すべて飽和演算。Lv が極端に高くても折り返さない。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.StatRatios
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobDef
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import io.github.jacoloves.ffa.domain.num.SatMath

/** 最終ステータスの計算。 */
object StatCalculator {

    /**
     * 最終ステータスを返す。
     *
     * @param job 現在の職業
     * @param charLevel キャラLv(1 以上)
     * @param masterBonus マスター済み職業のボーナス合計([masterBonusTotal] の結果)
     * @param equipment 装備ステータスの合計
     */
    fun calculate(
        job: JobDef,
        charLevel: Long,
        masterBonus: StatRatios = StatRatios.ZERO,
        equipment: Stats = Stats.ZERO,
    ): Stats {
        require(charLevel >= 1) {
            "キャラのレベルに $charLevel が指定されました。レベルは 1 以上です。セーブデータが壊れていないか確認してください。"
        }
        val steps = charLevel - 1
        // 成長値 × (lv-1) を基礎値に足す
        val raw = job.base + job.growth * steps
        // 1.03^(lv-1)。指数は Int なので上限で頭打ちにする(それ以上は Infinity になり飽和する)
        val exponent = if (steps > Int.MAX_VALUE) Int.MAX_VALUE else steps.toInt()
        val levelFactor = SatMath.pow(Balance.LEVEL_FACTOR_GROWTH, exponent)
        return (raw * levelFactor) * (StatRatios.ONE + masterBonus) + equipment
    }

    /** マスター済みの全職業の masterBonus を合算する(未マスターは含めない)。 */
    fun masterBonusTotal(jobs: Map<JobId, JobProgress>): StatRatios =
        jobs.entries
            .filter { it.value.mastered }
            .fold(StatRatios.ZERO) { acc, (id, _) -> acc + GameData.job(id).masterBonus }
}
