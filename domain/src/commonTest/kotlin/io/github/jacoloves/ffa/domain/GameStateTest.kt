// =============================================================================
// GameStateTest.kt
// 役割: 新規ゲームの初期状態と、名前の検証を確認する。
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import io.github.jacoloves.ffa.domain.data.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameStateTest {

    private fun newState(name: String = "勇者") = (GameState.new(name) as GameResult.Ok<GameState>).value

    @Test
    fun 初期状態() {
        val s = newState()
        assertEquals(2, s.schemaVersion)
        assertEquals("勇者", s.playerName)
        assertEquals(1L, s.charLevel)
        assertEquals(0L, s.charExp)
        assertEquals(JobId.WARRIOR, s.currentJob)
        assertEquals(JobId.entries.toSet(), s.jobs.keys)
        assertTrue(s.jobs.values.all { it == JobProgress() })
        assertEquals(0L, s.gold)
        assertEquals(AreaId.MEADOW, s.currentArea)
        assertEquals(Balance.DEFAULT_COOLDOWN_SEC, s.cooldownSec)
        assertEquals(0L, s.nextBattleAt)
        assertEquals(Records(), s.records)
        // 全エリアで選択 DLv は 1、クリア済み DLv は 0
        assertEquals(AreaId.entries.associateWith { 1 }, s.selectedDlv)
        assertEquals(AreaId.entries.associateWith { 0 }, s.maxClearedDlv)
    }

    /** 初期装備は木の剣 +0。 */
    @Test
    fun 初期装備は木の剣() {
        val s = newState()
        assertEquals(mapOf(ItemId.WOODEN_SWORD to 0), s.owned)
        assertEquals(mapOf(Slot.WEAPON to ItemId.WOODEN_SWORD), s.equipped)
    }

    /** 名前の前後の空白は取り除かれる。 */
    @Test
    fun 名前は前後の空白が除かれる() {
        assertEquals("太郎", newState("  太郎 ").playerName)
    }

    /** 空・空白だけの名前は InvalidName。 */
    @Test
    fun 空白の名前は拒否() {
        for (name in listOf("", "   ", "　")) {
            val r = GameState.new(name)
            assertIs<GameResult.Err>(r, "name='$name'")
            assertEquals(GameError.InvalidName, r.error)
        }
        assertTrue(GameError.InvalidName.message.contains("名前"))
    }

    /** すべての GameError は、空でない日本語メッセージを持つ。 */
    @Test
    fun すべてのエラーにメッセージがある() {
        val errors = listOf<GameError>(
            GameError.CooldownActive(1_500),
            GameError.AreaLocked(AreaId.FOREST, io.github.jacoloves.ffa.domain.data.AreaUnlock(AreaId.MEADOW, 10)),
            GameError.DlvOutOfRange(5, 3),
            GameError.JobLocked(JobId.PALADIN, setOf(JobId.KNIGHT)),
            GameError.AlreadyCurrentJob,
            GameError.NotEnoughGold(10, 5),
            GameError.ItemNotOwned(ItemId.IRON_SWORD),
            GameError.ItemNotForSale(ItemId.FANG_DAGGER),
            GameError.InvalidCooldown(0),
            GameError.InvalidName,
        )
        for (e in errors) assertTrue(e.message.length > 10, "$e")
        // クールタイムは残り秒を切り上げて表示する
        assertTrue(GameError.CooldownActive(1_500).message.contains("2秒"))
        assertTrue(GameError.CooldownActive(85_000).message.contains("1分25秒"))
        assertTrue(GameError.InvalidCooldown(181).message.contains("1〜180"))
        assertTrue(GameError.DlvOutOfRange(5, 3).message.contains("5") && GameError.DlvOutOfRange(5, 3).message.contains("3"))
    }
}
