// =============================================================================
// BalanceTest.kt
// 役割: Balance の初期値が設計書どおりであること、および値同士の整合性
//       (上限が下限より小さくない等)を検証する。調整時に不整合を早期に検出するための安全網。
// =============================================================================
package io.github.jacoloves.ffa.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceTest {

    /** 設計書(domain-design.md)に書かれた初期値と一致すること。 */
    @Test
    fun 初期値が設計書どおり() {
        assertEquals(50, Balance.MAX_ROUNDS)
        assertEquals(0.9, Balance.DAMAGE_VARIANCE_MIN)
        assertEquals(1.1, Balance.DAMAGE_VARIANCE_MAX)
        assertEquals(0.5, Balance.DEF_FACTOR)
        assertEquals(2.0, Balance.CRIT_MULTIPLIER)
        assertEquals(0.05, Balance.CRIT_BASE)
        assertEquals(0.20, Balance.CRIT_LUK_BONUS)
        assertEquals(0.25, Balance.CRIT_MAX)
        assertEquals(0.05, Balance.EVADE_FACTOR)
        assertEquals(0.30, Balance.EVADE_MAX)
        assertEquals(0.25, Balance.DOUBLE_ATTACK_FACTOR)
        assertEquals(0.5, Balance.DOUBLE_ATTACK_MAX)
        assertEquals(10.0, Balance.CHAR_EXP_BASE)
        assertEquals(1.5, Balance.CHAR_EXP_EXPONENT)
        assertEquals(1.03, Balance.LEVEL_FACTOR_GROWTH)
        assertEquals(30, Balance.JOB_MASTER_LEVEL)
        assertEquals(2, Balance.JOB_EXP_BASE)
        assertEquals(1.15, Balance.DLV_ENEMY_GROWTH)
        assertEquals(1.12, Balance.DLV_REWARD_GROWTH)
        assertEquals(10, Balance.AREA_UNLOCK_DLV)
        assertEquals(0.10, Balance.ENHANCE_RATIO_PER_LEVEL)
        assertEquals(0.5, Balance.DROP_LUK_BONUS_MAX)
        assertEquals(5, Balance.DEFAULT_COOLDOWN_SEC)
        assertEquals(1, Balance.MIN_COOLDOWN_SEC)
        assertEquals(30, Balance.MAX_COOLDOWN_SEC)
        assertEquals(2, Balance.LOSE_COOLDOWN_MULTIPLIER)
    }

    /** 値同士の整合性(調整ミスの検出)。 */
    @Test
    fun 値同士が矛盾していない() {
        assertTrue(Balance.DAMAGE_VARIANCE_MIN < Balance.DAMAGE_VARIANCE_MAX)
        assertTrue(Balance.CRIT_BASE <= Balance.CRIT_MAX)
        // 会心率は luk 比率 < 1 のため 0.05 + 0.20 = 0.25 に漸近する。
        // CRIT_MAX(0.25)と一致するので、上限に(限りなく)届き、かつ超えないことを確認する。
        assertTrue(Balance.CRIT_BASE + Balance.CRIT_LUK_BONUS == Balance.CRIT_MAX)
        assertTrue(Balance.MIN_COOLDOWN_SEC <= Balance.DEFAULT_COOLDOWN_SEC)
        assertTrue(Balance.DEFAULT_COOLDOWN_SEC <= Balance.MAX_COOLDOWN_SEC)
        assertTrue(Balance.DLV_REWARD_GROWTH <= Balance.DLV_ENEMY_GROWTH)
        assertTrue(Balance.MAX_ROUNDS > 0)
    }
}
