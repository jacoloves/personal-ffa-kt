// =============================================================================
// AreaRulesTest.kt
// 役割: エリアの解放判定(AreaUnlock と maxClearedDlv の関係)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.world

import io.github.jacoloves.ffa.domain.data.AreaId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AreaRulesTest {

    @Test
    fun 草原は常に解放済み() {
        assertTrue(AreaRules.isUnlocked(AreaId.MEADOW, emptyMap()))
    }

    /** 前のエリアで DLv 10 以上をクリアすると解放される(9 では未解放: 境界値)。 */
    @Test
    fun 前エリアのDLv10で解放() {
        assertFalse(AreaRules.isUnlocked(AreaId.FOREST, emptyMap()))
        assertFalse(AreaRules.isUnlocked(AreaId.FOREST, mapOf(AreaId.MEADOW to 9)))
        assertTrue(AreaRules.isUnlocked(AreaId.FOREST, mapOf(AreaId.MEADOW to 10)))
        assertTrue(AreaRules.isUnlocked(AreaId.FOREST, mapOf(AreaId.MEADOW to 25)))
        // 洞窟は森の進行が必要(草原だけでは足りない)
        assertFalse(AreaRules.isUnlocked(AreaId.CAVE, mapOf(AreaId.MEADOW to 99)))
        assertTrue(AreaRules.isUnlocked(AreaId.CAVE, mapOf(AreaId.FOREST to 10)))
    }

    @Test
    fun 新規解放の検出() {
        val before = mapOf(AreaId.MEADOW to 9)
        val after = mapOf(AreaId.MEADOW to 10)
        assertEquals(setOf(AreaId.FOREST), AreaRules.newlyUnlocked(before, after))
        assertEquals(emptySet(), AreaRules.newlyUnlocked(after, after))
    }
}
