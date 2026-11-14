// =============================================================================
// BattleLogFormatterTest.kt
// 役割: BattleEvent が日本語のログ行に変換されること(数値は和式表記)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import kotlin.test.Test
import kotlin.test.assertEquals

class BattleLogFormatterTest {

    @Test
    fun 遭遇() {
        assertEquals("スライムがあらわれた！", BattleLogFormatter.format(BattleEvent.Encounter("スライム")))
    }

    /** ダメージは formatJp で表記される(設計書の例: 1万2345)。 */
    @Test
    fun 攻撃のダメージは和式表記() {
        assertEquals(
            "勇者の攻撃！ スライムに 1万2345 のダメージ！",
            BattleLogFormatter.format(BattleEvent.Attack("勇者", "スライム", 12_345, critical = false)),
        )
        assertEquals(
            "勇者の攻撃！ スライムに 7 のダメージ！",
            BattleLogFormatter.format(BattleEvent.Attack("勇者", "スライム", 7, critical = false)),
        )
    }

    /** 会心のときは先頭に「会心の一撃！」が付く。 */
    @Test
    fun 会心の一撃() {
        assertEquals(
            "会心の一撃！ 勇者の攻撃！ スライムに 1億 のダメージ！",
            BattleLogFormatter.format(BattleEvent.Attack("勇者", "スライム", 100_000_000, critical = true)),
        )
    }

    @Test
    fun 回避() {
        assertEquals(
            "スライムは 勇者の攻撃をかわした！",
            BattleLogFormatter.format(BattleEvent.Evaded("勇者", "スライム")),
        )
    }

    @Test
    fun 撃破() {
        assertEquals("スライムは倒れた！", BattleLogFormatter.format(BattleEvent.Defeated("スライム")))
    }

    @Test
    fun タイムアウト() {
        assertEquals("決着がつかなかった… 戦いは打ち切られた。", BattleLogFormatter.format(BattleEvent.Timeout))
    }

    /** formatAll はイベントの順序を保って全行を変換する。 */
    @Test
    fun すべてのイベントを順に変換() {
        val lines = BattleLogFormatter.formatAll(
            listOf(BattleEvent.Encounter("スライム"), BattleEvent.Defeated("スライム")),
        )
        assertEquals(listOf("スライムがあらわれた！", "スライムは倒れた！"), lines)
    }
}
