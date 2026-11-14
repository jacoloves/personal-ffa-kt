// =============================================================================
// BattleEngineTest.kt
// 役割: BattleEngine.fight と確率式の仕様を検証する。
//
// テスト方針:
//   - 確率で分岐する経路(会心・回避・追撃)は ScriptedRandom で値を固定して確実に通す。
//   - ScriptedRandom の既定値 0.5 は「何も起きない・ダメージ倍率 1.0」になる(ScriptedRandom 参照)。
//   - 乱数の消費順序は 攻撃ごとに [回避 → 会心 → ダメージ乱数]、行動ごとに [追撃判定] とする。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.ScriptedRandom
import io.github.jacoloves.ffa.domain.Stats
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BattleEngineTest {

    private fun combatant(
        name: String, hp: Long, atk: Long, def: Long = 0, spd: Long = 10, luk: Long = 5,
    ) = Combatant(name, Stats(hp, atk, def, spd, luk))

    private val noEffectRandom get() = ScriptedRandom() // 常に 0.5: 回避・会心・追撃なし、倍率 1.0

    // ---------- 基本の決着 ----------

    /** 決まった乱数で、イベント列・ラウンド数・勝敗が完全に再現できること。 */
    @Test
    fun 決定的な戦闘でイベント列が確定する() {
        // 勇者: atk 10 → スライムへ 10 - 2×0.5 = 9 ダメージ。スライム: atk 4 → 勇者へ 4 ダメージ。
        val player = combatant("勇者", hp = 100, atk = 10, spd = 10)
        val enemy = combatant("スライム", hp = 25, atk = 4, def = 2, spd = 5)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)

        assertTrue(outcome.playerWon)
        assertEquals(3, outcome.rounds) // 25 → 16 → 7 → -2
        assertEquals(
            listOf(
                BattleEvent.Encounter("スライム"),
                BattleEvent.Attack("勇者", "スライム", 9, false),
                BattleEvent.Attack("スライム", "勇者", 4, false),
                BattleEvent.Attack("勇者", "スライム", 9, false),
                BattleEvent.Attack("スライム", "勇者", 4, false),
                BattleEvent.Attack("勇者", "スライム", 9, false),
                BattleEvent.Defeated("スライム"), // 3 ラウンド目はプレイヤーが倒すので敵は反撃しない
            ),
            outcome.events,
        )
    }

    /** プレイヤーが倒される場合は playerWon=false で、Defeated がプレイヤー名になる。 */
    @Test
    fun プレイヤーが倒されると敗北() {
        val player = combatant("勇者", hp = 10, atk = 1, spd = 10)
        val enemy = combatant("オーガ", hp = 1000, atk = 6, spd = 5)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)
        assertFalse(outcome.playerWon)
        assertEquals(BattleEvent.Defeated("勇者"), outcome.events.last())
        assertEquals(2, outcome.rounds) // 10 → 4 → -2
    }

    /** 同じ seed なら完全に同じ結果、決着はラウンド上限以内で必ず付くこと。 */
    @Test
    fun seed固定で再現できる() {
        val player = combatant("勇者", hp = 200, atk = 30, def = 5, spd = 12, luk = 8)
        val enemy = combatant("ゴブリン", hp = 150, atk = 25, def = 4, spd = 10, luk = 3)
        val a = BattleEngine.fight(player, enemy, Random(12345))
        val b = BattleEngine.fight(player, enemy, Random(12345))
        assertEquals(a, b)
        assertTrue(a.rounds in 1..Balance.MAX_ROUNDS)
        assertEquals(BattleEvent.Encounter("ゴブリン"), a.events.first())
    }

    /** 圧倒的に強いプレイヤーは、どんな seed でも勝つこと。 */
    @Test
    fun 圧倒的な強さなら常に勝つ() {
        val player = combatant("勇者", hp = 100_000, atk = 10_000, def = 1000, spd = 50)
        val enemy = combatant("スライム", hp = 20, atk = 5, def = 1, spd = 2)
        for (seed in 1..50) {
            assertTrue(BattleEngine.fight(player, enemy, Random(seed)).playerWon, "seed=$seed")
        }
    }

    // ---------- ダメージ ----------

    /** 防御が高くてもダメージは最低 1。 */
    @Test
    fun ダメージは最低1() {
        val player = combatant("勇者", hp = 100, atk = 1, spd = 10)
        val enemy = combatant("岩", hp = 3, atk = 1, def = 1000, spd = 1)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)
        val damages = outcome.events.filterIsInstance<BattleEvent.Attack>().filter { it.attacker == "勇者" }
        assertTrue(damages.all { it.damage == 1L })
        assertEquals(3, damages.size) // 3 回殴って倒す
        assertTrue(outcome.playerWon)
    }

    /** ダメージ乱数の下限(0.0)・上限(1.0 未満)で、式どおりの値になること。 */
    @Test
    fun ダメージ乱数の範囲() {
        val player = combatant("勇者", hp = 100, atk = 100, spd = 10)
        val enemy = combatant("的", hp = 100_000, atk = 1, spd = 1)
        // 攻撃 1 回目: 回避 0.5(なし)・会心 0.5(なし)・乱数 0.0 → 倍率 0.9 → 90
        val low = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.5, 0.0)))
        assertEquals(90L, (low.events[1] as BattleEvent.Attack).damage)
        // 乱数 0.999... → 倍率 ≒ 1.1 → 109(切り捨て)
        val high = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.5, 0.9999)))
        assertEquals(109L, (high.events[1] as BattleEvent.Attack).damage)
    }

    /** ダメージが Long の範囲を超えても飽和し、負数にならないこと。 */
    @Test
    fun 巨大なダメージは飽和する() {
        val player = combatant("勇者", hp = 100, atk = Long.MAX_VALUE, spd = 10)
        val enemy = combatant("的", hp = Long.MAX_VALUE, atk = 1, spd = 1)
        // 1 回目の攻撃で会心(会心判定 0.0)。倍率 1.0。
        val outcome = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.0, 0.5)))
        val attack = outcome.events[1] as BattleEvent.Attack
        assertTrue(attack.critical)
        assertEquals(Long.MAX_VALUE, attack.damage)
        assertTrue(outcome.playerWon)
    }

    // ---------- 会心・回避・追撃 ----------

    /** 会心判定に成功するとダメージが 2 倍になり、critical=true。 */
    @Test
    fun 会心はダメージ2倍() {
        val player = combatant("勇者", hp = 100, atk = 10, spd = 10)
        val enemy = combatant("的", hp = 20, atk = 1, spd = 1)
        // 回避 0.5(なし)→ 会心 0.0(成功)→ 乱数 0.5(倍率 1.0)
        val outcome = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.0, 0.5)))
        assertEquals(BattleEvent.Attack("勇者", "的", 20, true), outcome.events[1])
        assertIs<BattleEvent.Defeated>(outcome.events[2])
    }

    /** 会心判定に失敗(0.99)すると通常ダメージ。 */
    @Test
    fun 会心しない経路() {
        val player = combatant("勇者", hp = 100, atk = 10, spd = 10)
        val enemy = combatant("的", hp = 1000, atk = 1, spd = 1)
        val outcome = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.99, 0.5)))
        assertEquals(BattleEvent.Attack("勇者", "的", 10, false), outcome.events[1])
    }

    /** 回避判定に成功すると Evaded になり、ダメージは入らない(会心・乱数も消費しない)。 */
    @Test
    fun 回避する経路() {
        val player = combatant("勇者", hp = 100, atk = 10, spd = 10)
        // 勇者(spd 10)が先攻。回避率 = 0.05 × 9 / 10 = 0.045 > 0
        val enemy = combatant("コウモリ", hp = 1000, atk = 1, spd = 9)
        val random = ScriptedRandom(doubles = listOf(0.0)) // 回避判定 0.0 < 0.045 → 回避
        val outcome = BattleEngine.fight(player, enemy, random)
        assertEquals(BattleEvent.Evaded("勇者", "コウモリ"), outcome.events[1])
        // 回避時は会心・倍率の判定を行わず(追撃判定のみ)、そのまま敵の行動へ移る
        assertEquals(BattleEvent.Attack("コウモリ", "勇者", 1, false), outcome.events[2])
    }

    /** 回避判定が率以上(0.99)なら回避されず攻撃が当たる。 */
    @Test
    fun 回避しない経路() {
        val player = combatant("勇者", hp = 100, atk = 10, spd = 10)
        val enemy = combatant("コウモリ", hp = 1000, atk = 1, spd = 9)
        val outcome = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.99)))
        assertIs<BattleEvent.Attack>(outcome.events[1])
    }

    /** 追撃判定に成功すると、同じ行動で 2 回攻撃する。 */
    @Test
    fun 追撃で2回攻撃する() {
        // 勇者 spd 20 / 敵 spd 10 → 追撃率 (2-1)×0.25 = 0.25。
        val player = combatant("勇者", hp = 100, atk = 10, spd = 20)
        val enemy = combatant("的", hp = 1000, atk = 1, spd = 10)
        // 1 回目: 回避 0.5・会心 0.5・乱数 0.5 → 追撃判定 0.0(成功)→ 2 回目は既定値
        val outcome = BattleEngine.fight(player, enemy, ScriptedRandom(doubles = listOf(0.5, 0.5, 0.5, 0.0)))
        assertEquals("勇者", (outcome.events[1] as BattleEvent.Attack).attacker)
        assertEquals("勇者", (outcome.events[2] as BattleEvent.Attack).attacker) // 連続して勇者
        assertEquals("的", (outcome.events[3] as BattleEvent.Attack).attacker)
    }

    /** 追撃の 1 回目で倒したら、追撃判定もせず即終了する。 */
    @Test
    fun 倒したら追撃しない() {
        val player = combatant("勇者", hp = 100, atk = 100, spd = 20)
        val enemy = combatant("的", hp = 10, atk = 1, spd = 10)
        val random = ScriptedRandom()
        val outcome = BattleEngine.fight(player, enemy, random)
        assertEquals(3, outcome.events.size) // Encounter, Attack, Defeated
        // 乱数の消費は 回避・会心・ダメージ倍率 の 3 回だけ。倒した後の追撃判定は行わない。
        assertEquals(3, random.doubleCalls)
    }

    // ---------- 先攻 ----------

    /** spd が高い方が先に行動する。 */
    @Test
    fun 素早い方が先攻() {
        val slowPlayer = combatant("勇者", hp = 100, atk = 1, spd = 5)
        val fastEnemy = combatant("ネズミ", hp = 100, atk = 1, spd = 9)
        val first = BattleEngine.fight(slowPlayer, fastEnemy, noEffectRandom).events[1] as BattleEvent.Attack
        assertEquals("ネズミ", first.attacker)

        val fastPlayer = combatant("勇者", hp = 100, atk = 1, spd = 9)
        val slowEnemy = combatant("ネズミ", hp = 100, atk = 1, spd = 5)
        val first2 = BattleEngine.fight(fastPlayer, slowEnemy, noEffectRandom).events[1] as BattleEvent.Attack
        assertEquals("勇者", first2.attacker)
    }

    /** spd が同じならプレイヤーが先攻。 */
    @Test
    fun 同速ならプレイヤーが先攻() {
        val player = combatant("勇者", hp = 100, atk = 1, spd = 7)
        val enemy = combatant("ネズミ", hp = 100, atk = 1, spd = 7)
        val first = BattleEngine.fight(player, enemy, noEffectRandom).events[1] as BattleEvent.Attack
        assertEquals("勇者", first.attacker)
    }

    /** 先攻の敵に倒される場合、プレイヤーは一度も攻撃できない(先攻の意味の確認)。 */
    @Test
    fun 先攻の敵が一撃で倒す() {
        val player = combatant("勇者", hp = 5, atk = 100, spd = 1)
        val enemy = combatant("竜", hp = 1000, atk = 100, spd = 50)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)
        assertFalse(outcome.playerWon)
        assertTrue(outcome.events.filterIsInstance<BattleEvent.Attack>().none { it.attacker == "勇者" })
    }

    // ---------- タイムアウト ----------

    /** MAX_ROUNDS で決着しなければ Timeout になり、プレイヤーの敗北扱い。 */
    @Test
    fun ラウンド上限で敗北扱い() {
        val player = combatant("勇者", hp = 1000, atk = 1, def = 1000, spd = 10)
        val enemy = combatant("鉄壁", hp = 100_000, atk = 1, def = 1000, spd = 5)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)
        assertFalse(outcome.playerWon)
        assertEquals(Balance.MAX_ROUNDS, outcome.rounds)
        assertEquals(BattleEvent.Timeout, outcome.events.last())
        // Encounter 1 + 攻撃 (50 ラウンド × 2 人) + Timeout 1
        assertEquals(1 + Balance.MAX_ROUNDS * 2 + 1, outcome.events.size)
    }

    /** 最終ラウンドちょうどで倒した場合は Timeout にならず勝利(境界値)。 */
    @Test
    fun 最終ラウンドで倒せば勝利() {
        // 1 ダメージずつ、HP 50 → ちょうど 50 ラウンド目で倒す
        val player = combatant("勇者", hp = 1000, atk = 1, spd = 10)
        val enemy = combatant("的", hp = Balance.MAX_ROUNDS.toLong(), atk = 1, spd = 5)
        val outcome = BattleEngine.fight(player, enemy, noEffectRandom)
        assertTrue(outcome.playerWon)
        assertEquals(Balance.MAX_ROUNDS, outcome.rounds)
        assertFalse(outcome.events.contains(BattleEvent.Timeout))
    }

    // ---------- 入力の検証 ----------

    /** HP が 0 以下の戦闘者は、日本語の説明つきで拒否される。 */
    @Test
    fun HPが0以下は拒否() {
        val ok = combatant("敵", hp = 10, atk = 1)
        val e = assertFailsWith<IllegalArgumentException> {
            BattleEngine.fight(combatant("勇者", hp = 0, atk = 1), ok, noEffectRandom)
        }
        assertTrue(e.message!!.contains("HP"))
        assertFailsWith<IllegalArgumentException> {
            BattleEngine.fight(ok, combatant("敵", hp = -5, atk = 1), noEffectRandom)
        }
    }

    // ---------- 確率式 ----------

    @Test
    fun 回避率の式と上限() {
        // 0.05 × 防御側spd / 攻撃側spd
        assertEquals(0.05, BattleEngine.evadeRate(attackerSpd = 10, defenderSpd = 10), 1e-9)
        assertEquals(0.025, BattleEngine.evadeRate(attackerSpd = 20, defenderSpd = 10), 1e-9)
        // 上限 0.30
        assertEquals(Balance.EVADE_MAX, BattleEngine.evadeRate(attackerSpd = 1, defenderSpd = 1_000_000), 1e-9)
        // 攻撃側 spd が 0 でも 0 除算にならない
        assertEquals(0.05 * 3, BattleEngine.evadeRate(attackerSpd = 0, defenderSpd = 3), 1e-9)
    }

    @Test
    fun 会心率の式と上限() {
        // 運が両者 0 → 基本値のみ(0.05 + 0.15 × 0/1)
        assertEquals(Balance.CRIT_BASE, BattleEngine.critRate(ownLuk = 0, opponentLuk = 0), 1e-9)
        // 運 10 vs 10 → 0.05 + 0.15 × 10/21
        assertEquals(0.05 + 0.15 * 10.0 / 21.0, BattleEngine.critRate(10, 10), 1e-9)
        // 相手の運が 0 で自分が非常に高い → 0.20 に漸近(上限 0.25 には届かない)
        assertTrue(BattleEngine.critRate(1_000_000_000, 0) <= Balance.CRIT_MAX)
        assertTrue(BattleEngine.critRate(1_000_000_000, 0) > 0.19)
    }

    @Test
    fun 追撃率の式と上限() {
        // 同速 → 0、自分の方が遅い → 負にならず 0
        assertEquals(0.0, BattleEngine.doubleAttackRate(ownSpd = 10, opponentSpd = 10), 1e-9)
        assertEquals(0.0, BattleEngine.doubleAttackRate(ownSpd = 5, opponentSpd = 10), 1e-9)
        // 2 倍速 → 0.25
        assertEquals(0.25, BattleEngine.doubleAttackRate(ownSpd = 20, opponentSpd = 10), 1e-9)
        // 上限 0.5
        assertEquals(Balance.DOUBLE_ATTACK_MAX, BattleEngine.doubleAttackRate(1_000_000, 1), 1e-9)
        // 相手 spd 0 でも 0 除算にならない
        assertEquals(Balance.DOUBLE_ATTACK_MAX, BattleEngine.doubleAttackRate(100, 0), 1e-9)
    }
}
