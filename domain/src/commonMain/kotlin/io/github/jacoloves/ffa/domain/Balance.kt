// =============================================================================
// Balance.kt
// 役割: ゲームバランスに関わる係数をすべて 1 か所に集約する。
//
// なぜ集約するか:
//   数値調整(Phase 9)のとき、ロジックを触らずこのファイルだけ変えれば済むようにするため。
//   const val なのでコンパイル時定数となり、実行時のコストもない。
//   値は初期案。設計書(describe/domain-design.md)と BalanceTest で固定している。
// =============================================================================
package io.github.jacoloves.ffa.domain

/** ゲームバランスの係数集。 */
object Balance {
    // ---------- 戦闘 ----------
    /** 戦闘の最大ラウンド数。超えるとタイムアウトでプレイヤーの敗北。 */
    const val MAX_ROUNDS = 50
    /** ダメージ乱数の下限・上限(攻撃力にかかる倍率)。 */
    const val DAMAGE_VARIANCE_MIN = 0.9
    const val DAMAGE_VARIANCE_MAX = 1.1
    /** 防御力がダメージを減らす割合(ダメージ = atk × 乱数 − def × この値)。 */
    const val DEF_FACTOR = 0.5
    /** 会心時のダメージ倍率。 */
    const val CRIT_MULTIPLIER = 2.0
    /** 会心率 = 基本値 + 運ボーナス × 運の比率、ただし上限あり。 */
    const val CRIT_BASE = 0.05
    const val CRIT_LUK_BONUS = 0.15
    const val CRIT_MAX = 0.25
    /** 回避率 = 係数 × 防御側spd / 攻撃側spd、ただし上限あり。 */
    const val EVADE_FACTOR = 0.05
    const val EVADE_MAX = 0.30
    /** 追撃率 = (自spd / 相手spd − 1) × 係数、0 以上で上限あり。 */
    const val DOUBLE_ATTACK_FACTOR = 0.25
    const val DOUBLE_ATTACK_MAX = 0.5

    // ---------- 成長 ----------
    /** キャラの必要 EXP = ceil(BASE × lv^EXPONENT)。 */
    const val CHAR_EXP_BASE = 10.0
    const val CHAR_EXP_EXPONENT = 1.5
    /** ステータス倍率 = lv × 1.01^(lv-1)。 */
    const val LEVEL_FACTOR_GROWTH = 1.01
    /** 職業をマスターするのに必要な職業Lv。 */
    const val JOB_MASTER_LEVEL = 30
    /** 職業の必要 EXP = 2 + jobLv / 5。 */
    const val JOB_EXP_BASE = 2

    // ---------- ダンジョンLv ----------
    /** 敵ステータス倍率 = 1.15^(dlv-1)。 */
    const val DLV_ENEMY_GROWTH = 1.15
    /** EXP・ゴールド倍率 = 1.12^(dlv-1)(敵の強さより緩やか)。 */
    const val DLV_REWARD_GROWTH = 1.12
    /** 次のエリアの解放に必要な、前のエリアの DLv。 */
    const val AREA_UNLOCK_DLV = 10

    // ---------- 装備 ----------
    /** 強化値 +1 ごとに基礎値へ加算される割合。 */
    const val ENHANCE_RATIO_PER_LEVEL = 0.10
    /** ドロップ率 × (1 + この値 × pLuk/(pLuk+eLuk))。 */
    const val DROP_LUK_BONUS_MAX = 0.5

    // ---------- クールタイム(秒) ----------
    const val DEFAULT_COOLDOWN_SEC = 5
    const val MIN_COOLDOWN_SEC = 1
    const val MAX_COOLDOWN_SEC = 30
    /** 敗北時はクールタイムをこの倍数にする。 */
    const val LOSE_COOLDOWN_MULTIPLIER = 2
}
