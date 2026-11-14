// =============================================================================
// GameEngine.kt
// 役割: すべてのコマンドを処理する入口。純粋関数(同じ入力なら同じ出力)で、副作用を持たない。
//
// 設計判断:
//   - 状態・乱数・時計をすべて引数で受け取る。保存や画面更新は呼び出し側(ui)の責任。
//   - 失敗は例外ではなく CommandResult.Failure(GameError) で返す。失敗時は状態を一切変えない。
//   - 戦闘以外のコマンドはクールタイムの影響を受けない(買い物や転職は待たなくてよい)。
//
// Fight の処理順(設計書どおり):
//   1. クールタイム確認
//   2. 敵を抽選・生成し、プレイヤー(StatCalculator の結果)と戦闘
//   3. 勝利: EXP・ゴールド加算 → レベルアップ → ドロップ → 最高 DLv 更新 → エリア/職業の解放 → クールタイム
//   4. 敗北: 報酬なし、クールタイム × 2
//   5. どちらも records を更新
// 乱数の消費順: 敵の抽選 → 戦闘 → (勝利時のみ)ドロップ判定
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.battle.BattleEngine
import io.github.jacoloves.ffa.domain.battle.BattleLogFormatter
import io.github.jacoloves.ffa.domain.battle.Combatant
import io.github.jacoloves.ffa.domain.battle.EnemyFactory
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.inventory.Drops
import io.github.jacoloves.ffa.domain.inventory.Shop
import io.github.jacoloves.ffa.domain.num.SatMath
import io.github.jacoloves.ffa.domain.progression.JobRules
import io.github.jacoloves.ffa.domain.progression.Progression
import io.github.jacoloves.ffa.domain.progression.StatCalculator
import io.github.jacoloves.ffa.domain.world.AreaRules
import kotlin.random.Random

/** ゲームエンジン。 */
object GameEngine {

    /** コマンドを実行して、新しい状態とイベント(または失敗理由)を返す。 */
    fun execute(state: GameState, command: Command, random: Random, clock: Clock): CommandResult =
        when (command) {
            Command.Fight -> fight(state, random, clock)
            is Command.SelectArea -> selectArea(state, command.area)
            is Command.SetDlv -> setDlv(state, command.dlv)
            is Command.ChangeJob -> changeJob(state, command)
            is Command.Buy -> buy(state, command)
            is Command.Equip -> equip(state, command)
            is Command.SetCooldown -> setCooldown(state, command.sec)
        }

    /** プレイヤーの最終ステータス(職業・Lv・マスターボーナス・装備を反映)。画面表示にも使える。 */
    fun playerStats(state: GameState): Stats = StatCalculator.calculate(
        job = GameData.job(state.currentJob),
        charLevel = state.charLevel,
        masterBonus = StatCalculator.masterBonusTotal(state.jobs),
        equipment = state.inventory.equipmentStats(),
    )

    /** [area] で選べる DLv の上限 = クリア済み最高 DLv + 1(未クリアなら 1)。 */
    fun maxSelectableDlv(state: GameState, area: AreaId): Int = (state.maxClearedDlv[area] ?: 0) + 1

    // ---------- Fight ----------

    private fun fight(state: GameState, random: Random, clock: Clock): CommandResult {
        // 1. クールタイム確認(解除時刻ちょうどなら戦える)
        val now = clock.nowMillis()
        if (now < state.nextBattleAt) return fail(GameError.CooldownActive(state.nextBattleAt - now))

        // 状態の整合確認(通常は起きないが、壊れたセーブでも不正な戦闘をしないための防御)
        val area = GameData.area(state.currentArea)
        if (!AreaRules.isUnlocked(area.id, state.maxClearedDlv)) {
            return fail(GameError.AreaLocked(area.id, checkNotNull(area.unlock)))
        }
        val dlv = state.selectedDlv[area.id] ?: 1
        val maxDlv = maxSelectableDlv(state, area.id)
        if (dlv !in 1..maxDlv) return fail(GameError.DlvOutOfRange(dlv, maxDlv))

        // 2. 敵の抽選・生成と戦闘
        val enemyDef = EnemyFactory.pickEnemy(area, random)
        val enemy = EnemyFactory.create(enemyDef, dlv)
        val stats = playerStats(state)
        val outcome = BattleEngine.fight(Combatant(state.playerName, stats), enemy, random)
        val events = mutableListOf<GameEvent>(
            GameEvent.BattleFinished(outcome, BattleLogFormatter.formatAll(outcome.events)),
        )
        val cooldownMs = state.cooldownSec * 1000L
        val battles = state.records.battles + 1

        // 4. 敗北(タイムアウトを含む): 報酬なし、クールタイム × 2
        if (!outcome.playerWon) {
            val next = state.copy(
                nextBattleAt = now + cooldownMs * Balance.LOSE_COOLDOWN_MULTIPLIER,
                records = state.records.copy(battles = battles),
            )
            return CommandResult.Success(next, events)
        }

        // 3. 勝利
        val reward = EnemyFactory.reward(enemyDef, dlv)
        val jobExpGain = area.tier.toLong() // 職業 EXP = エリアの tier(DLv の影響なし)
        events += GameEvent.ExpGained(reward.exp, jobExpGain)
        events += GameEvent.GoldGained(reward.gold)
        val gold = SatMath.add(state.gold, reward.gold)

        // キャラのレベルアップ
        val charResult = Progression.gainCharExp(state.charLevel, state.charExp, reward.exp)
        if (charResult.levelsGained > 0) events += GameEvent.CharLevelUp(state.charLevel, charResult.level)

        // 職業のレベルアップ・マスター・上位職解放
        val jobBefore = state.jobs[state.currentJob] ?: io.github.jacoloves.ffa.domain.data.JobProgress()
        val jobResult = Progression.gainJobExp(jobBefore, jobExpGain)
        val jobs = state.jobs + (state.currentJob to jobResult.progress)
        if (jobResult.levelsGained > 0) {
            events += GameEvent.JobLevelUp(state.currentJob, jobBefore.level, jobResult.progress.level)
        }
        if (jobResult.newlyMastered) events += GameEvent.JobMastered(state.currentJob)
        for (job in JobRules.newlyUnlocked(state.jobs, jobs)) events += GameEvent.JobUnlocked(job)

        // ドロップ(運は DLv 強化後の敵の値を使う)
        var inventory = state.inventory
        for (item in Drops.roll(enemyDef, stats.luk, enemy.stats.luk, random)) {
            val gained = inventory.gain(item)
            inventory = gained.inventory
            events += GameEvent.ItemGained(item, gained.gain.enhance, gained.gain.isNew)
        }

        // 最高 DLv の更新と、それによるエリア解放
        var maxCleared = state.maxClearedDlv
        if (dlv > (state.maxClearedDlv[area.id] ?: 0)) {
            maxCleared = maxCleared + (area.id to dlv)
            events += GameEvent.DlvRecord(area.id, dlv)
            for (a in AreaRules.newlyUnlocked(state.maxClearedDlv, maxCleared)) events += GameEvent.AreaUnlocked(a)
        }

        val next = state.copy(
            charLevel = charResult.level,
            charExp = charResult.exp,
            jobs = jobs,
            gold = gold,
            owned = inventory.owned,
            equipped = inventory.equipped,
            maxClearedDlv = maxCleared,
            nextBattleAt = now + cooldownMs,
            records = state.records.copy(
                battles = battles,
                wins = state.records.wins + 1,
                totalGold = SatMath.add(state.records.totalGold, reward.gold),
                maxCharLevel = maxOf(state.records.maxCharLevel, charResult.level),
            ),
        )
        return CommandResult.Success(next, events)
    }

    // ---------- その他のコマンド ----------

    private fun selectArea(state: GameState, area: AreaId): CommandResult {
        if (!AreaRules.isUnlocked(area, state.maxClearedDlv)) {
            return fail(GameError.AreaLocked(area, checkNotNull(GameData.area(area).unlock)))
        }
        return ok(state.copy(currentArea = area))
    }

    private fun setDlv(state: GameState, dlv: Int): CommandResult {
        val max = maxSelectableDlv(state, state.currentArea)
        if (dlv !in 1..max) return fail(GameError.DlvOutOfRange(dlv, max))
        return ok(state.copy(selectedDlv = state.selectedDlv + (state.currentArea to dlv)))
    }

    private fun changeJob(state: GameState, command: Command.ChangeJob): CommandResult {
        JobRules.validateChange(state.currentJob, command.job, state.jobs)?.let { return fail(it) }
        return ok(state.copy(currentJob = command.job))
    }

    private fun buy(state: GameState, command: Command.Buy): CommandResult =
        when (val r = Shop.buy(command.item, state.gold, state.inventory, state.maxClearedDlv)) {
            is GameResult.Err -> fail(r.error)
            is GameResult.Ok -> ok(
                state.copy(gold = r.value.gold, owned = r.value.inventory.owned, equipped = r.value.inventory.equipped),
                GameEvent.ItemGained(r.value.gain.item, r.value.gain.enhance, r.value.gain.isNew),
            )
        }

    private fun equip(state: GameState, command: Command.Equip): CommandResult =
        when (val r = state.inventory.equip(command.item)) {
            is GameResult.Err -> fail(r.error)
            is GameResult.Ok -> ok(state.copy(owned = r.value.owned, equipped = r.value.equipped))
        }

    private fun setCooldown(state: GameState, sec: Int): CommandResult {
        if (sec !in Balance.MIN_COOLDOWN_SEC..Balance.MAX_COOLDOWN_SEC) return fail(GameError.InvalidCooldown(sec))
        return ok(state.copy(cooldownSec = sec))
    }

    private fun ok(state: GameState, vararg events: GameEvent) = CommandResult.Success(state, events.toList())

    private fun fail(error: GameError) = CommandResult.Failure(error)
}
