// =============================================================================
// BattleEngine.kt
// 役割: 1 対 1 の自動戦闘を計算する純粋なロジック。UI にも状態にも依存しない。
//
// 戦闘の流れ(1 ラウンド):
//   1. spd が高い方が先に行動する(同じならプレイヤーが先)。
//   2. 各自が 1 回行動する。行動 = 攻撃 1 回 + (追撃判定に成功すれば、もう 1 回攻撃)。
//   3. どちらかの HP が 0 以下になったら、その場で戦闘終了(倒された側は反撃できない)。
//   4. MAX_ROUNDS を超えても決着しなければ Timeout(プレイヤーの敗北)。
//
// 乱数の消費順序(テストで固定するための契約):
//   攻撃 1 回ごと  : 回避判定 → (回避しなかった場合のみ)会心判定 → ダメージ乱数
//   行動 1 回ごと  : 1 回目の攻撃のあとに追撃判定(相手が生存していて、追撃率 > 0 のときだけ)
//   判定はすべて random.nextDouble() 1 回。`値 < 確率` なら成功。
//
// なぜ乱数を引数で受けるか:
//   テストでは Random(seed) で再現、あるいは固定値を返す Random で分岐を強制するため。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.num.SatMath
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** 戦闘エンジン。状態を持たないので object にしてある。 */
object BattleEngine {

    /**
     * プレイヤーと敵を、決着(または最大ラウンド)まで戦わせる。
     *
     * @param player プレイヤー側(stats.hp が戦闘開始時の HP)
     * @param enemy 敵側
     * @param random 乱数源(テストでは固定シード / 固定値)
     * @return 勝敗・イベント列・ラウンド数
     * @throws IllegalArgumentException どちらかの HP が 0 以下のとき(戦闘が成立しないため)
     */
    fun fight(player: Combatant, enemy: Combatant, random: Random): BattleOutcome {
        require(player.stats.hp > 0) {
            "プレイヤー「${player.name}」の HP が ${player.stats.hp} です。戦闘を始めるには HP が 1 以上必要です。" +
                "ステータス計算(装備・職業ボーナス)の結果が 0 以下になっていないか確認してください。"
        }
        require(enemy.stats.hp > 0) {
            "敵「${enemy.name}」の HP が ${enemy.stats.hp} です。戦闘を始めるには HP が 1 以上必要です。" +
                "GameData の敵定義や EnemyFactory の倍率計算を確認してください。"
        }

        // HP は戦闘の中だけで使うローカル状態(戦闘ごとに全快するので永続化しない)。
        var playerHp = player.stats.hp
        var enemyHp = enemy.stats.hp
        val events = mutableListOf<BattleEvent>(BattleEvent.Encounter(enemy.name))

        // 先攻: spd が高い方。同じならプレイヤー。
        val playerFirst = player.stats.spd >= enemy.stats.spd

        /**
         * attacker が defender に 1 回の行動(攻撃 + 追撃判定)を行う。
         * defender の新しい HP を返す(0 以下なら撃破)。
         */
        fun act(attacker: Combatant, defender: Combatant, defenderHp: Long): Long {
            var hp = defenderHp
            // 1 回目の攻撃
            hp = attackOnce(attacker, defender, hp, random, events)
            if (hp <= 0) return hp // 倒したら追撃判定もしない
            // 追撃判定
            val rate = doubleAttackRate(attacker.stats.spd, defender.stats.spd)
            if (random.nextDouble() < rate) {
                hp = attackOnce(attacker, defender, hp, random, events)
            }
            return hp
        }

        for (round in 1..Balance.MAX_ROUNDS) {
            // 先攻 → 後攻の順に行動する。どちらも同じ手順なので、順序だけを切り替える。
            val order = if (playerFirst) listOf(true, false) else listOf(false, true)
            for (isPlayerTurn in order) {
                if (isPlayerTurn) {
                    enemyHp = act(player, enemy, enemyHp)
                    if (enemyHp <= 0) {
                        events += BattleEvent.Defeated(enemy.name)
                        return BattleOutcome(playerWon = true, events = events, rounds = round)
                    }
                } else {
                    playerHp = act(enemy, player, playerHp)
                    if (playerHp <= 0) {
                        events += BattleEvent.Defeated(player.name)
                        return BattleOutcome(playerWon = false, events = events, rounds = round)
                    }
                }
            }
        }

        // 最大ラウンドを超えても決着しなかった → プレイヤーの敗北扱い。
        events += BattleEvent.Timeout
        return BattleOutcome(playerWon = false, events = events, rounds = Balance.MAX_ROUNDS)
    }

    /**
     * 攻撃 1 回分の処理。イベントを [events] に追加し、防御側の新しい HP を返す。
     * 手順: 回避判定 → 会心判定 → ダメージ計算(すべて飽和演算)。
     */
    private fun attackOnce(
        attacker: Combatant,
        defender: Combatant,
        defenderHp: Long,
        random: Random,
        events: MutableList<BattleEvent>,
    ): Long {
        // 1. 回避判定
        if (random.nextDouble() < evadeRate(attacker.stats.spd, defender.stats.spd)) {
            events += BattleEvent.Evaded(attacker.name, defender.name)
            return defenderHp
        }
        // 2. 会心判定
        val critical = random.nextDouble() < critRate(attacker.stats.luk, defender.stats.luk)
        // 3. ダメージ = max(1, atk × 乱数[0.9,1.1) − def × 0.5)。会心なら ×2。
        val variance = Balance.DAMAGE_VARIANCE_MIN +
            random.nextDouble() * (Balance.DAMAGE_VARIANCE_MAX - Balance.DAMAGE_VARIANCE_MIN)
        val raw = SatMath.add(
            SatMath.mulRatio(attacker.stats.atk, variance),
            -SatMath.mulRatio(defender.stats.def, Balance.DEF_FACTOR),
        )
        var damage = max(1L, raw) // 防御が高くても最低 1 ダメージ(戦闘が必ず進む保証)
        if (critical) damage = SatMath.mulRatio(damage, Balance.CRIT_MULTIPLIER)
        events += BattleEvent.Attack(attacker.name, defender.name, damage, critical)
        return SatMath.add(defenderHp, -damage)
    }

    /** 回避率 = min(EVADE_MAX, EVADE_FACTOR × 防御側spd / max(1, 攻撃側spd))。 */
    fun evadeRate(attackerSpd: Long, defenderSpd: Long): Double =
        min(Balance.EVADE_MAX, Balance.EVADE_FACTOR * defenderSpd.toDouble() / max(1L, attackerSpd).toDouble())

    /** 会心率 = min(CRIT_MAX, CRIT_BASE + CRIT_LUK_BONUS × 自分のluk / (自分のluk + 相手のluk + 1))。 */
    fun critRate(ownLuk: Long, opponentLuk: Long): Double =
        min(
            Balance.CRIT_MAX,
            Balance.CRIT_BASE + Balance.CRIT_LUK_BONUS * ownLuk.toDouble() / (ownLuk.toDouble() + opponentLuk.toDouble() + 1.0),
        )

    /** 追撃率 = clamp((自分spd / max(1, 相手spd) − 1) × DOUBLE_ATTACK_FACTOR, 0, DOUBLE_ATTACK_MAX)。 */
    fun doubleAttackRate(ownSpd: Long, opponentSpd: Long): Double {
        val raw = (ownSpd.toDouble() / max(1L, opponentSpd).toDouble() - 1.0) * Balance.DOUBLE_ATTACK_FACTOR
        return raw.coerceIn(0.0, Balance.DOUBLE_ATTACK_MAX)
    }
}
