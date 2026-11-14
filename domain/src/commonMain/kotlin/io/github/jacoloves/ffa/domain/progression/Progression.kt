// =============================================================================
// Progression.kt
// 役割: キャラ EXP / 職業 EXP の必要量とレベルアップ処理を行う純粋関数群。
//
// 設計判断:
//   - 状態(GameState)に依存せず「値を受けて結果を返す」関数にした。
//     Phase 6 の GameEngine がこれらを組み合わせるだけで済み、単体でテストしやすい。
//   - 結果にはレベルが何上がったか・新たにマスターしたかを含め、イベント生成に使えるようにする。
//   - Long は飽和演算で扱う。レベルが Long.MAX_VALUE に達したらそれ以上は上げない(無限ループ防止)。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.data.JobProgress
import io.github.jacoloves.ffa.domain.num.SatMath
import kotlin.math.ceil
import kotlin.math.sqrt

/** キャラ EXP 獲得の結果。[levelsGained] は今回上がったレベル数。 */
data class CharExpResult(val level: Long, val exp: Long, val levelsGained: Long)

/** 職業 EXP 獲得の結果。[newlyMastered] は今回の獲得で初めてマスターしたか。 */
data class JobExpResult(val progress: JobProgress, val levelsGained: Int, val newlyMastered: Boolean)

/** 成長(EXP とレベル)の計算。 */
object Progression {

    /**
     * キャラが次のレベルへ上がるのに必要な EXP = `ceil(10 × lv^1.5)`(飽和)。
     *
     * lv^1.5 は `lv × sqrt(lv)` で求める(pow より整数の平方で正確になりやすい)。
     */
    fun charExpToNext(level: Long): Long {
        require(level >= 1) {
            "キャラのレベルに $level が指定されました。レベルは 1 以上です。呼び出し元でレベルが 1 以上であることを確認してください。"
        }
        val lv = level.toDouble()
        val need = ceil(Balance.CHAR_EXP_BASE * lv * sqrt(lv)) // EXPONENT = 1.5 の式
        return SatMath.mulRatio(1L, need) // 範囲外は Long の上限で止める
    }

    /**
     * キャラ EXP を獲得し、レベルアップを(複数回でも)すべて処理する。
     *
     * 注意: 獲得量が極端に巨大だとループ回数が増える(Long 上限級で数千万回)。
     * 実際のゲームの獲得量では問題にならないため、特別な近似は入れていない。
     *
     * @param gained 獲得 EXP(0 以上)
     */
    fun gainCharExp(level: Long, exp: Long, gained: Long): CharExpResult {
        require(gained >= 0) {
            "獲得 EXP に $gained が指定されました。獲得 EXP は 0 以上です。報酬の計算結果が負になっていないか確認してください。"
        }
        var lv = level
        var cur = SatMath.add(exp, gained)
        var up = 0L
        while (lv < Long.MAX_VALUE) {
            val need = charExpToNext(lv)
            if (cur < need) break
            cur -= need
            lv++
            up++
        }
        return CharExpResult(lv, cur, up)
    }

    /** 職業が次のレベルへ上がるのに必要な EXP = `2 + jobLv / 5`(整数除算)。 */
    fun jobExpToNext(jobLevel: Int): Long {
        require(jobLevel >= 1) {
            "職業のレベルに $jobLevel が指定されました。レベルは 1 以上です。"
        }
        return (Balance.JOB_EXP_BASE + jobLevel / 5).toLong()
    }

    /**
     * 職業 EXP を獲得し、レベルアップとマスター判定を行う。
     *
     * マスターは職業Lv が [Balance.JOB_MASTER_LEVEL] 以上になった時点で 1 回だけ成立する。
     */
    fun gainJobExp(progress: JobProgress, gained: Long): JobExpResult {
        require(gained >= 0) {
            "獲得職業 EXP に $gained が指定されました。0 以上である必要があります。エリアの tier が正しく渡されているか確認してください。"
        }
        var lv = progress.level
        var cur = SatMath.add(progress.exp, gained)
        var up = 0
        while (lv < Int.MAX_VALUE) {
            val need = jobExpToNext(lv)
            if (cur < need) break
            cur -= need
            lv++
            up++
        }
        val reachedMaster = lv >= Balance.JOB_MASTER_LEVEL
        val newly = reachedMaster && !progress.mastered
        return JobExpResult(
            progress = JobProgress(level = lv, exp = cur, mastered = progress.mastered || reachedMaster),
            levelsGained = up,
            newlyMastered = newly,
        )
    }
}
