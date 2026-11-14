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

    // ---------- キャラ EXP 曲線(累計関数 C で定義する) ----------
    //
    // 「レベル lv に到達するまでの累計 EXP」C(lv)(Lv1 から)を先に決め、
    //   次のレベルまでの必要 EXP = C(lv+1) − C(lv)
    // と定義する。こうすると「いくつ獲得したらレベルはいくつか」は C の逆引き(二分探索)で
    // 巨大な獲得でも誤差なく(1 レベルずつ上げた結果と完全に一致して)求まる。
    //
    // C(lv) = ceil(4 × ((lv−0.5)^2.5 − 0.5^2.5))
    //   これは「1 レベルの必要 EXP ≒ 10 × lv^1.5」(Balance.CHAR_EXP_BASE, CHAR_EXP_EXPONENT)の積分を、
    //   中点則(lv−0.5)でそろえたもの。従来の曲線(ceil(10 × lv^1.5))とほぼ同じ形で、バランスはほとんど動かない。
    //   k = BASE / (EXPONENT + 1) = 4。 y^2.5 は y×y×sqrt(y) で計算する
    //   (y は半整数なので y×y は正確で、丸めは sqrt と掛け算の IEEE 演算だけ=どの環境でも同じ値になる)。

    /** 累計曲線の係数 k = 10 / 2.5 = 4。 */
    private val CUM_K = Balance.CHAR_EXP_BASE / (Balance.CHAR_EXP_EXPONENT + 1.0)

    /** 定数項 k × 0.5^2.5。C(1) = 0 にそろえるために引く。 */
    private val CUM_OFFSET = CUM_K * (0.5 * 0.5 * sqrt(0.5))

    /** Long の上限(2^63)を Double で表した値。これ以上は Long に収まらないので飽和させる。 */
    private const val TWO_POW_63 = 9.223372036854775807E18

    /** 累計曲線の生の値(Double、丸め前)。lv >= 1。 */
    private fun cumulativeRaw(level: Long): Double {
        val y = level.toDouble() - 0.5
        return CUM_K * (y * y * sqrt(y)) - CUM_OFFSET
    }

    /**
     * Lv1 からレベル [level] に到達するまでの累計 EXP(Long で飽和)。C(1) = 0。
     * レベルが大きすぎて Long に収まらない場合は [Long.MAX_VALUE]。
     */
    fun charExpCumulative(level: Long): Long {
        require(level >= 1) {
            "キャラのレベルに $level が指定されました。レベルは 1 以上です。呼び出し元でレベルが 1 以上であることを確認してください。"
        }
        if (level == 1L) return 0L
        val c = ceil(cumulativeRaw(level))
        return if (c >= TWO_POW_63) Long.MAX_VALUE else c.toLong()
    }

    /**
     * 累計 EXP が Long に収まる(飽和しない)最大のレベル。これ以上のレベルは「次のレベルへの必要 EXP」を
     * Long.MAX_VALUE(事実上到達不能)として扱う。約 1,500 万レベルで、通常のプレイでは到達しない。
     * 二分探索で求める(累計は単調増加)。
     */
    private val maxCumulativeLevel: Long by lazy {
        var lo = 1L // 収まる
        var hi = 1L shl 40 // 収まらない(十分大きい)
        while (lo + 1 < hi) {
            val mid = lo + (hi - lo) / 2
            if (ceil(cumulativeRaw(mid)) < TWO_POW_63) lo = mid else hi = mid
        }
        lo
    }

    /**
     * キャラが次のレベルへ上がるのに必要な EXP = C(lv+1) − C(lv)(約 `10 × (lv−0.5)^1.5`)。
     * 累計が Long に収まらない巨大レベルでは [Long.MAX_VALUE](飽和)。
     */
    fun charExpToNext(level: Long): Long {
        require(level >= 1) {
            "キャラのレベルに $level が指定されました。レベルは 1 以上です。呼び出し元でレベルが 1 以上であることを確認してください。"
        }
        if (level >= maxCumulativeLevel) return Long.MAX_VALUE
        return charExpCumulative(level + 1) - charExpCumulative(level)
    }

    /**
     * キャラ EXP を獲得し、レベルアップを(何レベルでも)正確に処理する。
     *
     * 手順: 現在の累計 `C(level) + exp + gained`(Long で飽和)を求め、`C(lv) <= 累計` を満たす最大の lv を
     * 二分探索し、余りを exp とする。1 レベルずつ上げた結果と常に完全に一致する(近似はない)。
     * 不変条件 0 <= exp < 次の必要 EXP を満たす。累計が Long の上限を超える獲得は飽和する(上限以上は無視)。
     *
     * @param gained 獲得 EXP(0 以上)
     */
    fun gainCharExp(level: Long, exp: Long, gained: Long): CharExpResult {
        require(gained >= 0) {
            "獲得 EXP に $gained が指定されました。獲得 EXP は 0 以上です。報酬の計算結果が負になっていないか確認してください。"
        }
        val cap = maxCumulativeLevel
        if (level >= cap) {
            // 巨大レベル: 次のレベルへの必要 EXP は Long.MAX_VALUE。EXP が溜まりきったときだけ 1 つ上がる。
            val cur = SatMath.add(exp, gained)
            return if (cur == Long.MAX_VALUE && level < Long.MAX_VALUE) CharExpResult(level + 1, 0, 1) else CharExpResult(level, cur, 0)
        }
        val total = SatMath.add(SatMath.add(charExpCumulative(level), exp), gained)
        // 不変条件: C(lo) <= total、hi は「C(hi) > total」または cap の次(範囲外)
        var lo = level
        var hi = cap + 1
        while (lo + 1 < hi) {
            val mid = lo + (hi - lo) / 2
            if (charExpCumulative(mid) <= total) lo = mid else hi = mid
        }
        return CharExpResult(level = lo, exp = total - charExpCumulative(lo), levelsGained = lo - level)
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
