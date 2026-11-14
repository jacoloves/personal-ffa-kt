// =============================================================================
// BalanceSimulator.kt
// 役割: ゲームバランスを「実際に遊ばせて」測るための、決定的な自動プレイヤー(bot)とその計測。
//
// なぜ作るか:
//   敵・装備・価格・EXP 曲線は互いに強く影響し合う(指数と多項式が混ざる)ため、机上で整合させるのは難しい。
//   本物の GameEngine.execute を Random(seed) と偽の時計で回し、「何戦目でエリアが解放されるか」
//   「そのときのレベルは」「勝率は」「装備はいつ買えるか」を数値で確認する。
//   Balance / GameData を変えるたびにこの結果が変わるので、BalanceSimulationTest が目標カーブを守る。
//
// bot の方針(貪欲法。人間の「普通の遊び方」を単純化したもの):
//   1. 戦闘: 今の DLv で戦い、直近 10 戦(最低 6 戦)の勝率が 70% 以上になったら 1 DLv 上げる(選べる最高 DLv まで)。
//      勝率が 70% を下回る / 3 連敗したら 1 DLv 下げる。つまり「勝率 70% を保てる最高の DLv」を探し続ける。
//   2. エリア: 新しく解放されたエリアの推奨 Lv に達したら移動する(それまでは今のエリアに留まる)。
//   3. 買い物: 枠ごとに「解放済みエリアのショップで、買える最も高い品」を買って装備する。
//      その枠の最良品を持っているときだけ、余ったゴールドで強化(同じ品の再購入)する。
//   4. 転職: 現在の職業をマスターしたら、次の未マスター職へ(上位職が解放されていれば優先)。
//   時計は偽物で、戦闘のたびにクールタイム明けまで一気に進める(実時間はかからない)。
// =============================================================================
package io.github.jacoloves.ffa.domain.balance

import io.github.jacoloves.ffa.domain.Clock
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.CommandResult
import io.github.jacoloves.ffa.domain.GameEngine
import io.github.jacoloves.ffa.domain.GameEvent
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.GameState
import io.github.jacoloves.ffa.domain.battle.BattleEngine
import io.github.jacoloves.ffa.domain.battle.Combatant
import io.github.jacoloves.ffa.domain.battle.EnemyFactory
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemDef
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.Slot
import io.github.jacoloves.ffa.domain.progression.JobRules
import io.github.jacoloves.ffa.domain.world.AreaRules
import kotlin.random.Random

/** 偽の時計。戦闘のたびにクールタイム明けへ書き換えて、待たずに連続で戦えるようにする。 */
class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now
}

/** 1 回のシミュレーション(1 seed 分)で集めた計測値。戦闘番号はすべて「通算の何戦目か」(1 始まり)。 */
class SimReport(val seed: Int) {
    /** エリアが解放された戦闘番号。 */
    val unlockFight = mutableMapOf<AreaId, Int>()
    /** エリア解放時のキャラLv。 */
    val unlockLevel = mutableMapOf<AreaId, Long>()
    /** エリアへ移動した戦闘番号(草原は 1)。 */
    val entryFight = mutableMapOf<AreaId, Int>()
    /** エリアへ移動した時点のキャラLv。 */
    val entryLevel = mutableMapOf<AreaId, Long>()
    /** エリアへ移動した直後に、その場で DLv1 の敵と多数回戦わせて測った勝率(0.0〜1.0)。 */
    val entryWinRate = mutableMapOf<AreaId, Double>()
    /** エリアごとの戦闘数・勝利数(bot が実際に戦った分)。 */
    val fights = mutableMapOf<AreaId, Int>()
    val wins = mutableMapOf<AreaId, Int>()
    /** エリアごとに到達した最高 DLv。 */
    val maxDlv = mutableMapOf<AreaId, Int>()
    /** 職業をマスターした戦闘番号。 */
    val masteredFight = mutableMapOf<JobId, Int>()
    /** ショップ品を初めて買った戦闘番号(ドロップで先に手に入った品は含まない)。 */
    val firstBoughtFight = mutableMapOf<ItemId, Int>()
    /**
     * そのショップ品の売られているエリアで、価格以上のゴールドを「稼ぎ終えた」までの、そのエリアでの戦闘数。
     * 手持ちではなく累計の稼ぎで測る(ほかの買い物や強化に使った分に左右されないようにするため)。
     */
    val fightsToEarnPrice = mutableMapOf<ItemId, Int>()
    /** エリアごとの獲得ゴールド累計(滞在中に稼いだ分)。 */
    val earnedGold = mutableMapOf<AreaId, Long>()
    /** ドロップで入手した回数(アイテム別・ドロップ限定品のみ数える)。 */
    val dropOnlyObtained = mutableMapOf<ItemId, Int>()
    /** ドロップ限定品を、そのエリアに滞在中に入手した回数。 */
    val dropOnlyByArea = mutableMapOf<AreaId, Int>()
    /** エリアに滞在した戦闘数(エリアを離れる/シミュレーション終了まで)。 */
    val stayFights = mutableMapOf<AreaId, Int>()
    var totalFights = 0
    var finalLevel = 1L
    var finalGold = 0L

    /** あるエリアでの実戦の勝率(戦っていなければ null)。 */
    fun winRate(area: AreaId): Double? {
        val f = fights[area] ?: return null
        return if (f == 0) null else (wins[area] ?: 0).toDouble() / f
    }
}

/** 自動プレイヤー。1 インスタンス = 1 回のシミュレーション。 */
class BalanceSimulator(private val seed: Int) {
    private val random = Random(seed)
    private val probeRandom = Random(seed + 7_777) // 勝率の測定用。ゲーム本体の乱数列を乱さないよう別系統にする
    private val clock = FakeClock()
    private val report = SimReport(seed)

    private var state: GameState = (GameState.new("勇者") as GameResult.Ok).value

    /** (エリア, DLv) ごとの直近の勝敗(最大 [WINDOW] 件)。 */
    private val windows = mutableMapOf<Pair<AreaId, Int>, ArrayDeque<Boolean>>()
    /** エリアごとの、今挑んでいる DLv(初期値は 1)。 */
    private val currentDlv = mutableMapOf<AreaId, Int>()
    private var lastLoseStreak = 0

    /**
     * [maxFights] 戦までシミュレーションする。[stopAfterEnteringVolcano] 戦を火山で過ごしたら早めに終える。
     * @param probeTrials エリア移動時に勝率を測る戦闘回数
     */
    fun run(maxFights: Int, stopAfterEnteringVolcano: Int = 600, probeTrials: Int = 300): SimReport {
        report.entryFight[AreaId.MEADOW] = 1
        report.entryLevel[AreaId.MEADOW] = 1
        report.entryWinRate[AreaId.MEADOW] = probeWinRate(AreaId.MEADOW, probeTrials)
        for (n in 1..maxFights) {
            manage(n, probeTrials)
            fightOnce(n)
            val volcanoEntry = report.entryFight[AreaId.VOLCANO]
            if (volcanoEntry != null && n - volcanoEntry >= stopAfterEnteringVolcano) break
        }
        report.finalLevel = state.charLevel
        report.finalGold = state.gold
        return report
    }

    // ---------- 戦闘 ----------

    private fun fightOnce(n: Int) {
        val area = state.currentArea
        val dlv = chooseDlv(area)
        state = apply(Command.SetDlv(dlv))
        clock.now = maxOf(clock.now, state.nextBattleAt) // クールタイム明けまで一気に進める
        val before = state
        val result = GameEngine.execute(state, Command.Fight, random, clock)
        check(result is CommandResult.Success) { "戦闘に失敗しました: $result(状態: $before)" }
        state = result.state
        report.totalFights = n
        report.stayFights[area] = (report.stayFights[area] ?: 0) + 1
        report.fights[area] = (report.fights[area] ?: 0) + 1
        val won = state.records.wins > before.records.wins
        if (won) report.wins[area] = (report.wins[area] ?: 0) + 1
        for (e in result.events) recordEvent(n, area, e)
        // 計測: このエリアの売り物について、累計の稼ぎが価格に届いた時点の戦闘数
        for (item in GameData.shopItems(area)) {
            if (item.id !in report.fightsToEarnPrice && (report.earnedGold[area] ?: 0L) >= item.price!!) {
                report.fightsToEarnPrice[item.id] = report.fights.getValue(area)
            }
        }

        // 勝敗の記録と、挑む DLv の更新
        val w = windows.getOrPut(area to dlv) { ArrayDeque() }
        w.addLast(won)
        if (w.size > WINDOW) w.removeFirst()
        lastLoseStreak = if (won) 0 else lastLoseStreak + 1
        val rate = w.count { it }.toDouble() / w.size
        val max = GameEngine.maxSelectableDlv(state, area)
        if ((w.size >= MIN_SAMPLES_DOWN && rate < WIN_RATE_THRESHOLD || lastLoseStreak >= LOSE_STREAK) && dlv > 1) {
            // 苦戦している → 1 つ下げる(下げた先でも新しく勝率を測り直す)
            currentDlv[area] = dlv - 1
            windows[area to dlv]?.clear()
            windows[area to dlv - 1]?.clear()
            lastLoseStreak = 0
        } else if (w.size >= MIN_SAMPLES_UP && rate >= WIN_RATE_THRESHOLD && dlv < max) {
            // 安定して勝てている → 1 つ上げる
            currentDlv[area] = dlv + 1
        }
    }

    /** 今回挑む DLv(選べる範囲に収める)。 */
    private fun chooseDlv(area: AreaId): Int =
        (currentDlv[area] ?: 1).coerceIn(1, GameEngine.maxSelectableDlv(state, area))

    private fun recordEvent(n: Int, area: AreaId, e: GameEvent) {
        when (e) {
            is GameEvent.AreaUnlocked -> {
                report.unlockFight[e.area] = n
                report.unlockLevel[e.area] = state.charLevel
            }
            is GameEvent.GoldGained -> report.earnedGold[area] = (report.earnedGold[area] ?: 0L) + e.amount
            is GameEvent.JobMastered -> report.masteredFight[e.job] = n
            is GameEvent.DlvRecord -> report.maxDlv[e.area] = maxOf(report.maxDlv[e.area] ?: 0, e.dlv)
            is GameEvent.ItemGained -> {
                if (GameData.item(e.item).price == null) {
                    report.dropOnlyObtained[e.item] = (report.dropOnlyObtained[e.item] ?: 0) + 1
                    report.dropOnlyByArea[area] = (report.dropOnlyByArea[area] ?: 0) + 1
                }
            }
            else -> Unit
        }
    }

    // ---------- 戦闘以外の行動(待たずにできる) ----------

    private fun manage(n: Int, probeTrials: Int) {
        manageJob()
        manageShop(n)
        manageArea(n, probeTrials)
    }

    /** 現在の職業をマスターしたら、次の未マスター職へ転職する(上位職を優先)。 */
    private fun manageJob() {
        val current = state.jobs.getValue(state.currentJob)
        if (!current.mastered) return
        val unlocked = JobRules.unlockedJobs(state.jobs)
        val next = unlocked
            .filter { state.jobs.getValue(it).mastered.not() }
            .sortedWith(compareByDescending<JobId> { GameData.job(it).tier }.thenBy { it.ordinal })
            .firstOrNull() ?: return
        state = apply(Command.ChangeJob(next))
    }

    /**
     * 買い物。優先順位は「①枠ごとに、買える最高価格の未所持品 → ②全枠で解放済みの最良品を持っていれば、余ったゴールドで強化」。
     * 強化(同じ品の買い足し)は未所持の良い品への貯金を妨げないよう、①がすべて済んでからだけ行う。
     * 装備は常に、枠ごとの最も強い所持品へ付け替える。
     */
    private fun manageShop(n: Int) {
        val unlockedAreas = AreaRules.unlockedAreas(state.maxClearedDlv)
        val stocks = Slot.entries.associateWith { slot ->
            GameData.items.values
                .filter { it.slot == slot && it.price != null && it.shopArea in unlockedAreas }
                .sortedByDescending { it.price } // 高い品ほど強い設計
        }
        // ① 未所持の品を買う(その枠で今買える最も高い品)
        for ((_, stock) in stocks) {
            val candidate = stock.firstOrNull { it.price!! <= state.gold } ?: continue
            if (candidate.id !in state.owned) {
                state = apply(Command.Buy(candidate.id))
                report.firstBoughtFight.getOrPut(candidate.id) { n }
            }
        }
        // ② すべての枠で最良品を持っているときだけ、強化する(1 回につき 1 個)
        val allBestOwned = stocks.values.all { it.isEmpty() || it.first().id in state.owned }
        if (allBestOwned) {
            val target = stocks.values
                .mapNotNull { it.firstOrNull() }
                .filter { state.owned.getValue(it.id) < MAX_ENHANCE && it.price!! <= state.gold }
                .minByOrNull { it.price!! }
            if (target != null) state = apply(Command.Buy(target.id))
        }
        // 装備: 枠ごとに(強化込みの)性能が最大の所持品へ
        for (slot in Slot.entries) {
            val bestOwned = state.owned.keys
                .filter { GameData.item(it).slot == slot }
                .maxByOrNull { score(GameData.item(it), state.owned.getValue(it)) } ?: continue
            if (state.equipped[slot] != bestOwned) {
                state = apply(Command.Equip(bestOwned))
            }
        }
    }

    /** 装備の強さの簡易スコア(HP は他より数値が大きいので 1/5 に割る)。 */
    private fun score(item: ItemDef, enhance: Int): Long {
        val s = item.statsAt(enhance)
        return s.atk + s.def + s.spd + s.luk + s.hp / 5
    }

    /** 解放済みで推奨 Lv に達した最も先のエリアへ移動する。 */
    private fun manageArea(n: Int, probeTrials: Int) {
        val target = AreaId.entries
            .filter { AreaRules.isUnlocked(it, state.maxClearedDlv) && state.charLevel >= GameData.area(it).recommendedLevel }
            .maxByOrNull { GameData.area(it).tier } ?: return
        if (target == state.currentArea) return
        if (GameData.area(target).tier < GameData.area(state.currentArea).tier) return // 後戻りはしない
        state = apply(Command.SelectArea(target))
        report.entryFight[target] = n
        report.entryLevel[target] = state.charLevel
        report.entryWinRate[target] = probeWinRate(target, probeTrials)
    }

    /** 今の状態のまま [area] の DLv1 の敵と [trials] 回戦わせて勝率を測る(状態は変えない)。 */
    private fun probeWinRate(area: AreaId, trials: Int): Double {
        val player = Combatant("勇者", GameEngine.playerStats(state))
        val def = GameData.area(area)
        var win = 0
        repeat(trials) {
            val enemy = EnemyFactory.create(EnemyFactory.pickEnemy(def, probeRandom), 1)
            if (BattleEngine.fight(player, enemy, probeRandom).playerWon) win++
        }
        return win.toDouble() / trials
    }

    private fun apply(command: Command): GameState {
        val r = GameEngine.execute(state, command, random, clock)
        check(r is CommandResult.Success) { "コマンド $command が失敗しました: $r" }
        return r.state
    }

    companion object {
        const val WINDOW = 10
        const val MIN_SAMPLES_UP = 6
        const val MIN_SAMPLES_DOWN = 5
        const val WIN_RATE_THRESHOLD = 0.7
        const val LOSE_STREAK = 3
        const val MAX_ENHANCE = 10

        /** 最初の 1 戦だけを、与えられた seed で行って勝敗を返す(「最初の戦闘に勝てるか」の測定用)。 */
        fun firstFightWon(seed: Int): Boolean {
            val state = (GameState.new("勇者") as GameResult.Ok).value
            val r = GameEngine.execute(state, Command.Fight, Random(seed), FakeClock(0)) as CommandResult.Success
            return r.state.records.wins == 1L
        }
    }
}
