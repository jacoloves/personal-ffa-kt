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
import kotlin.math.pow
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

    /** 1 レベルずつ正確に計算する回数の上限。これを超える巨大な獲得だけ、まとめて飛ばす近似に切り替える。 */
    private const val EXACT_LOOP_LIMIT = 10_000

    /**
     * キャラ EXP を獲得し、レベルアップを(複数回でも)すべて処理する。
     *
     * 通常の獲得(数レベル〜数千レベル)は 1 レベルずつ正確に計算する。
     * ただし 1 回の獲得が巨大(Long 上限級なら約 1,000 万レベル分)でループが [EXACT_LOOP_LIMIT] 回を超える場合は、
     * 累計必要 EXP の積分近似([approxCumulative])を二分探索して一気にレベルを飛ばし、残りだけ正確に計算する。
     * この経路でも不変条件(0 <= exp < 次の必要 EXP、レベルは飽和)は必ず守り、到達レベルの誤差は ±数レベル以内。
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
        var loops = 0
        while (lv < Long.MAX_VALUE) {
            val need = charExpToNext(lv)
            if (cur < need) break
            if (++loops > EXACT_LOOP_LIMIT && need < Long.MAX_VALUE) {
                // 巨大な獲得: 二分探索で「近似累計 <= cur」を満たす最大のレベル数 n をまとめて飛ばす。
                val n = maxLevelsAffordable(lv, cur)
                if (n > 0) {
                    cur = (cur - approxCumulative(lv, n).toLong()).coerceAtLeast(0)
                    lv += n
                    up += n
                    loops = 0
                    continue
                }
            }
            cur -= need
            lv++
            up++
        }
        return CharExpResult(lv, cur, up)
    }

    /**
     * レベル [from] から [n] レベル上がるのに必要な EXP の合計の近似。
     * 各レベルの必要 EXP は 10 × k^1.5(切り上げで平均 +0.5)なので、中点則の積分
     * `4 × ((from+n-0.5)^2.5 − (from-0.5)^2.5) + 0.5 × n` で近似する(相対誤差は 1e-9 級)。
     */
    private fun approxCumulative(from: Long, n: Long): Double {
        val a = from.toDouble() - 0.5
        val b = from.toDouble() + n.toDouble() - 0.5
        val k = Balance.CHAR_EXP_BASE / (Balance.CHAR_EXP_EXPONENT + 1.0) // 10 / 2.5 = 4
        return k * (b.pow(Balance.CHAR_EXP_EXPONENT + 1.0) - a.pow(Balance.CHAR_EXP_EXPONENT + 1.0)) + 0.5 * n
    }

    /** [budget] の EXP で、レベル [from] から何レベル上がれるかの近似上限(二分探索。近似累計は n に対して単調増加)。 */
    private fun maxLevelsAffordable(from: Long, budget: Long): Long {
        val room = Long.MAX_VALUE - from // レベルが Long を超えないための上限
        var lo = 0L
        var hi = 1L
        // 上限 hi を倍々で探す(近似累計が予算を超えるか、room に達するまで)
        while (hi < room && approxCumulative(from, hi) <= budget.toDouble()) hi = if (hi > room / 2) room else hi * 2
        // 不変条件: lo は払える、hi は払えない(または room)
        while (lo + 1 < hi) {
            val mid = lo + (hi - lo) / 2
            if (approxCumulative(from, mid) <= budget.toDouble()) lo = mid else hi = mid
        }
        return lo
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
