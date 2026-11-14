// =============================================================================
// GameEngineTest.kt
// 役割: GameEngine.execute の全コマンドを、決まった乱数(ScriptedRandom)と偽の時計で検証する。
//
// 戦闘を確実に再現するためのレシピ:
//   - ScriptedRandom(ints = [0]) → エリア先頭の敵(草原ならスライム)が選ばれる。
//   - charLevel を 50 にすると、プレイヤーは草原の敵を 1 撃で倒せる。
//     → 乱数の消費は「攻撃 3 回分(回避・会心・倍率)」→ ドロップ判定(表の行ごとに 1 回)の順。
//   - 木の剣 +0 の Lv1 戦士は、森のウッドウルフに 1 撃で倒される(敗北の再現用)。
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.battle.EnemyFactory
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.EnemyId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import io.github.jacoloves.ffa.domain.data.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameEngineTest {

    private fun newState() = (GameState.new("勇者") as GameResult.Ok<GameState>).value
    private val strong get() = newState().copy(charLevel = 50)
    private var now = 0L
    private val clock = Clock { now }

    /** 草原先頭(スライム)を選ぶ乱数。続く判定値(戦闘 3 回分 + ドロップ)を doubles で指定する。 */
    private fun rng(vararg doubles: Double) = ScriptedRandom(doubles = doubles.toList(), ints = listOf(0))

    private fun run(state: GameState, cmd: Command, random: kotlin.random.Random = rng()): CommandResult =
        GameEngine.execute(state, cmd, random, clock)

    private fun ok(r: CommandResult): CommandResult.Success = assertIs<CommandResult.Success>(r)
    private fun err(r: CommandResult): GameError = assertIs<CommandResult.Failure>(r).error

    private inline fun <reified E : GameEvent> List<GameEvent>.one(): E = filterIsInstance<E>().single()

    // ---------- Fight: 勝利 ----------

    @Test
    fun 勝利すると報酬が入り記録が更新される() {
        now = 1_000
        val r = ok(run(strong, Command.Fight))
        val slime = GameData.enemy(EnemyId.SLIME)
        // BattleFinished: 勝利で、ログが日本語に変換されている
        val finished = r.events.one<GameEvent.BattleFinished>()
        assertTrue(finished.outcome.playerWon)
        assertEquals("スライムがあらわれた！", finished.logLines.first())
        assertEquals(finished.outcome.events.size, finished.logLines.size)
        // 報酬: DLv1 なので定義どおり。職業 EXP はエリアの tier(1)
        assertEquals(GameEvent.ExpGained(charExp = slime.exp, jobExp = 1), r.events.one<GameEvent.ExpGained>())
        assertEquals(GameEvent.GoldGained(slime.gold), r.events.one<GameEvent.GoldGained>())
        assertEquals(slime.gold, r.state.gold)
        assertEquals(slime.exp, r.state.charExp)
        assertEquals(1, r.state.jobs.getValue(JobId.WARRIOR).exp.toInt())
        // 記録
        assertEquals(Records(battles = 1, wins = 1, totalGold = slime.gold, maxCharLevel = 50), r.state.records)
        // 勝利のクールタイム = 現在時刻 + cooldownSec
        assertEquals(1_000L + 90_000L, r.state.nextBattleAt)
    }

    /** 元の状態は変更されない(不変)。 */
    @Test
    fun 元の状態は変わらない() {
        val before = strong
        run(before, Command.Fight)
        assertEquals(strong, before)
    }

    // ---------- Fight: クールタイム ----------

    @Test
    fun クールタイム中は戦えない() {
        val s = strong.copy(nextBattleAt = 10_000)
        now = 4_000
        val e = err(run(s, Command.Fight))
        assertEquals(GameError.CooldownActive(remainingMs = 6_000), e)
    }

    /** ちょうど解除時刻なら戦える(境界値)。1 ミリ秒前は戦えない。 */
    @Test
    fun クールタイムの境界() {
        val s = strong.copy(nextBattleAt = 10_000)
        now = 9_999
        assertEquals(GameError.CooldownActive(1), err(run(s, Command.Fight)))
        now = 10_000
        ok(run(s, Command.Fight))
    }

    @Test
    fun クールタイム設定が勝利後の待ち時間に反映される() {
        now = 0
        val s = ok(run(strong, Command.SetCooldown(10))).state
        val r = ok(run(s, Command.Fight))
        assertEquals(10_000L, r.state.nextBattleAt)
    }

    // ---------- Fight: 敗北 ----------

    private fun losingState() = newState().copy(
        currentArea = AreaId.FOREST,
        maxClearedDlv = mapOf(AreaId.MEADOW to 10),
    )

    @Test
    fun 敗北すると報酬なしでクールタイムは勝利と同じ() {
        now = 2_000
        val s = losingState()
        val r = ok(run(s, Command.Fight))
        assertFalse(r.events.one<GameEvent.BattleFinished>().outcome.playerWon)
        // 報酬系イベントは出ない
        assertEquals(1, r.events.size)
        assertEquals(s.gold, r.state.gold)
        assertEquals(s.charExp, r.state.charExp)
        assertEquals(s.jobs, r.state.jobs)
        assertEquals(s.maxClearedDlv, r.state.maxClearedDlv)
        assertEquals(Records(battles = 1, wins = 0, totalGold = 0, maxCharLevel = 1), r.state.records)
        // 敗北のクールタイム = 現在時刻 + cooldownSec(倍化しない)
        assertEquals(2_000L + 90_000L, r.state.nextBattleAt)
    }

    // ---------- Fight: レベルアップ・職業 ----------

    @Test
    fun キャラのレベルアップイベント() {
        // charLevel 50 の必要 EXP の直前まで溜めて、スライムの EXP(DLv1)でちょうど上がる
        val need = io.github.jacoloves.ffa.domain.progression.Progression.charExpToNext(50)
        val r = ok(run(strong.copy(charExp = need - GameData.enemy(EnemyId.SLIME).exp), Command.Fight))
        assertEquals(GameEvent.CharLevelUp(from = 50, to = 51), r.events.one<GameEvent.CharLevelUp>())
        assertEquals(51L, r.state.charLevel)
        assertEquals(0L, r.state.charExp)
        assertEquals(51L, r.state.records.maxCharLevel)
    }

    @Test
    fun 職業レベルアップイベント() {
        val s = strong.copy(jobs = JobRulesFresh.jobs + (JobId.WARRIOR to JobProgress(level = 1, exp = 1)))
        val r = ok(run(s, Command.Fight)) // 職業 EXP +1 で 2 に達し Lv2
        assertEquals(GameEvent.JobLevelUp(JobId.WARRIOR, 1, 2), r.events.one<GameEvent.JobLevelUp>())
        assertEquals(JobProgress(level = 2, exp = 0), r.state.jobs.getValue(JobId.WARRIOR))
    }

    /** マスター + 上位職の解放。盗賊をマスター済みで戦士が Lv30 に達すると、剣聖が解放される。 */
    @Test
    fun マスターと上位職解放() {
        val jobs = JobRulesFresh.jobs +
            (JobId.WARRIOR to JobProgress(level = 29, exp = 6)) + // 必要 7 のところ 6 → +1 で Lv30
            (JobId.THIEF to JobProgress(level = 30, mastered = true))
        val r = ok(run(strong.copy(jobs = jobs), Command.Fight))
        assertEquals(GameEvent.JobMastered(JobId.WARRIOR), r.events.one<GameEvent.JobMastered>())
        assertEquals(GameEvent.JobUnlocked(JobId.SWORD_MASTER), r.events.one<GameEvent.JobUnlocked>())
        assertTrue(r.state.jobs.getValue(JobId.WARRIOR).mastered)
        // 片方だけではまだ解放されない(盗賊が未マスターなら JobUnlocked は出ない)
        val r2 = ok(run(strong.copy(jobs = jobs + (JobId.THIEF to JobProgress())), Command.Fight))
        assertTrue(r2.events.filterIsInstance<GameEvent.JobUnlocked>().isEmpty())
    }

    // ---------- Fight: ドロップ・DLv・エリア解放 ----------

    @Test
    fun ドロップで装備が手に入る() {
        // 戦闘で 3 回(回避・会心・倍率)消費 → 次のドロップ判定 0.0 で LEATHER_ARMOR(スライムのドロップ)が落ちる
        val r = ok(run(strong, Command.Fight, rng(0.5, 0.5, 0.5, 0.0)))
        assertEquals(GameEvent.ItemGained(ItemId.LEATHER_ARMOR, enhance = 0, isNew = true), r.events.one<GameEvent.ItemGained>())
        assertEquals(0, r.state.owned[ItemId.LEATHER_ARMOR])
        // 2 回目は同じ装備なので +1(クールタイムが明けるまで時計を進める)
        now = 100_000
        val r2 = ok(run(r.state, Command.Fight, rng(0.5, 0.5, 0.5, 0.0)))
        assertEquals(GameEvent.ItemGained(ItemId.LEATHER_ARMOR, 1, isNew = false), r2.events.one<GameEvent.ItemGained>())
    }

    @Test
    fun DLvの記録更新とエリア解放() {
        val s = strong.copy(
            selectedDlv = mapOf(AreaId.MEADOW to 10),
            maxClearedDlv = mapOf(AreaId.MEADOW to 9),
        )
        val r = ok(run(s, Command.Fight))
        assertTrue(r.events.one<GameEvent.BattleFinished>().outcome.playerWon)
        assertEquals(GameEvent.DlvRecord(AreaId.MEADOW, 10), r.events.one<GameEvent.DlvRecord>())
        assertEquals(GameEvent.AreaUnlocked(AreaId.FOREST), r.events.one<GameEvent.AreaUnlocked>())
        assertEquals(10, r.state.maxClearedDlv[AreaId.MEADOW])
        // DLv10 の報酬倍率が適用されている(DLv1 の定義値より多い)
        val reward = EnemyFactory.reward(GameData.enemy(EnemyId.SLIME), 10)
        assertEquals(reward.gold, r.state.gold)
        assertTrue(reward.gold > GameData.enemy(EnemyId.SLIME).gold)
    }

    /** すでに記録済みの DLv を再クリアしても DlvRecord は出ない。DLv9 では解放されない。 */
    @Test
    fun 記録済みのDLvではイベントなし() {
        val s = strong.copy(selectedDlv = mapOf(AreaId.MEADOW to 9), maxClearedDlv = mapOf(AreaId.MEADOW to 9))
        val r = ok(run(s, Command.Fight))
        assertTrue(r.events.filterIsInstance<GameEvent.DlvRecord>().isEmpty())
        assertTrue(r.events.filterIsInstance<GameEvent.AreaUnlocked>().isEmpty())
    }

    /** 壊れた状態(選択 DLv が範囲外)では戦わずに DlvOutOfRange を返す。 */
    @Test
    fun 選択DLvが範囲外なら戦えない() {
        val s = strong.copy(selectedDlv = mapOf(AreaId.MEADOW to 5))
        assertEquals(GameError.DlvOutOfRange(5, 1), err(run(s, Command.Fight)))
    }

    // ---------- SelectArea / SetDlv ----------

    @Test
    fun エリア移動() {
        val s = newState().copy(maxClearedDlv = mapOf(AreaId.MEADOW to 10))
        assertEquals(AreaId.FOREST, ok(run(s, Command.SelectArea(AreaId.FOREST))).state.currentArea)
        val e = err(run(newState(), Command.SelectArea(AreaId.FOREST)))
        assertIs<GameError.AreaLocked>(e)
        assertEquals(AreaId.FOREST, e.area)
    }

    @Test
    fun DLvの範囲() {
        val s = newState().copy(maxClearedDlv = mapOf(AreaId.MEADOW to 5)) // 選べるのは 1..6
        assertEquals(6, ok(run(s, Command.SetDlv(6))).state.selectedDlv[AreaId.MEADOW])
        assertEquals(1, ok(run(s, Command.SetDlv(1))).state.selectedDlv[AreaId.MEADOW])
        assertEquals(GameError.DlvOutOfRange(7, 6), err(run(s, Command.SetDlv(7))))
        assertEquals(GameError.DlvOutOfRange(0, 6), err(run(s, Command.SetDlv(0))))
        // 何も倒していなければ 1..1
        assertEquals(GameError.DlvOutOfRange(2, 1), err(run(newState(), Command.SetDlv(2))))
    }

    // ---------- ChangeJob / Buy / Equip / SetCooldown ----------

    @Test
    fun 転職() {
        assertEquals(JobId.KNIGHT, ok(run(newState(), Command.ChangeJob(JobId.KNIGHT))).state.currentJob)
        assertEquals(GameError.AlreadyCurrentJob, err(run(newState(), Command.ChangeJob(JobId.WARRIOR))))
        val e = err(run(newState(), Command.ChangeJob(JobId.SWORD_MASTER)))
        assertIs<GameError.JobLocked>(e)
        assertEquals(setOf(JobId.WARRIOR, JobId.THIEF), e.missing)
    }

    /** 転職しても、キャラLvとゴールド、装備は保持される。 */
    @Test
    fun 転職してもキャラ情報は残る() {
        val s = strong.copy(gold = 123)
        val after = ok(run(s, Command.ChangeJob(JobId.THIEF))).state
        assertEquals(50L, after.charLevel)
        assertEquals(123L, after.gold)
        assertEquals(s.equipped, after.equipped)
    }

    @Test
    fun 購入() {
        val price = GameData.item(ItemId.LEATHER_ARMOR).price!!
        val r = ok(run(newState().copy(gold = price + 7), Command.Buy(ItemId.LEATHER_ARMOR)))
        assertEquals(7L, r.state.gold)
        assertEquals(0, r.state.owned[ItemId.LEATHER_ARMOR])
        assertEquals(GameEvent.ItemGained(ItemId.LEATHER_ARMOR, 0, true), r.events.one<GameEvent.ItemGained>())
        // 購入しても自動では装備されない
        assertEquals(null, r.state.equipped[Slot.ARMOR])
    }

    @Test
    fun 購入のエラー() {
        val price = GameData.item(ItemId.LEATHER_ARMOR).price!!
        assertEquals(GameError.NotEnoughGold(price, price - 1), err(run(newState().copy(gold = price - 1), Command.Buy(ItemId.LEATHER_ARMOR))))
        assertIs<GameError.AreaLocked>(err(run(newState().copy(gold = 1_000_000), Command.Buy(ItemId.IRON_SWORD))))
        assertEquals(GameError.ItemNotForSale(ItemId.FANG_DAGGER), err(run(newState().copy(gold = 1_000_000), Command.Buy(ItemId.FANG_DAGGER))))
    }

    @Test
    fun 装備() {
        val s = newState().copy(owned = mapOf(ItemId.WOODEN_SWORD to 0, ItemId.LEATHER_ARMOR to 2))
        val r = ok(run(s, Command.Equip(ItemId.LEATHER_ARMOR)))
        assertEquals(ItemId.LEATHER_ARMOR, r.state.equipped[Slot.ARMOR])
        assertEquals(GameError.ItemNotOwned(ItemId.IRON_SWORD), err(run(s, Command.Equip(ItemId.IRON_SWORD))))
    }

    /** 装備するとプレイヤーの最終ステータス(playerStats)に反映される。 */
    @Test
    fun 装備が最終ステータスに反映される() {
        val base = GameEngine.playerStats(newState())
        // 初期: 戦士 Lv1 の基礎値 + 木の剣(atk 3)
        assertEquals(io.github.jacoloves.ffa.domain.Stats(30, 8 + 3, 4, 5, 3), base)
        val s = newState().copy(owned = mapOf(ItemId.WOODEN_SWORD to 0, ItemId.LEATHER_ARMOR to 0))
        val armored = GameEngine.playerStats((ok(run(s, Command.Equip(ItemId.LEATHER_ARMOR)))).state)
        assertEquals(base.hp + 15, armored.hp)
        assertEquals(base.def + 2, armored.def)
    }

    @Test
    fun クールタイム設定の範囲() {
        assertEquals(1, ok(run(newState(), Command.SetCooldown(1))).state.cooldownSec)
        assertEquals(180, ok(run(newState(), Command.SetCooldown(180))).state.cooldownSec)
        assertEquals(GameError.InvalidCooldown(0), err(run(newState(), Command.SetCooldown(0))))
        assertEquals(GameError.InvalidCooldown(181), err(run(newState(), Command.SetCooldown(181))))
    }

    /** 失敗したコマンドは、クールタイム中でも設定系は影響を受けない(戦闘以外はクールタイム無関係)。 */
    @Test
    fun 戦闘以外はクールタイム中でも実行できる() {
        val s = newState().copy(nextBattleAt = 1_000_000)
        now = 0
        ok(run(s, Command.SetCooldown(3)))
        ok(run(s, Command.ChangeJob(JobId.KNIGHT)))
    }

    /** 与えられた maxClearedDlv にエリアが無い(未記録)場合も 0 として扱える。 */
    @Test
    fun 未記録のエリアは0扱い() {
        val s = newState().copy(maxClearedDlv = emptyMap(), selectedDlv = emptyMap())
        assertEquals(1, GameEngine.maxSelectableDlv(s, AreaId.MEADOW))
        ok(run(strong.copy(maxClearedDlv = emptyMap(), selectedDlv = emptyMap()), Command.Fight))
    }

    /** 実際の乱数でも、多数回戦ってもエラーにならず状態が整合する(スモークテスト)。 */
    @Test
    fun 乱数で連続して戦っても壊れない() {
        var s = strong
        val random = kotlin.random.Random(7)
        repeat(200) { i ->
            now = i * 100_000L
            val r = ok(GameEngine.execute(s, Command.Fight, random, clock))
            s = r.state
        }
        assertEquals(200L, s.records.battles)
        assertTrue(s.records.wins in 1..200)
        assertTrue(s.charLevel >= 50)
    }

    // 職業の初期値を使うための小さなヘルパー(JobRules.initialJobs と同じ内容)。
    private object JobRulesFresh {
        val jobs = io.github.jacoloves.ffa.domain.progression.JobRules.initialJobs()
    }
}
