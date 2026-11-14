// =============================================================================
// Balance.kt
// 役割: ゲームバランスに関わる係数をすべて 1 か所に集約する。
//
// なぜ集約するか:
//   数値調整(Phase 9)のとき、ロジックを触らずこのファイルだけ変えれば済むようにするため。
//   const val なのでコンパイル時定数となり、実行時のコストもない。
//   値は Phase 9 のシミュレーション(BalanceSimulationTest)で調整済み。設計書(describe/domain-design.md)と BalanceTest で固定している。
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
    const val CRIT_LUK_BONUS = 0.20
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
    /** レベルによるステータス倍率 = 1.03^(lv-1)(純粋な指数。lv を直接かける項は無い)。 */
    const val LEVEL_FACTOR_GROWTH = 1.03
    /** 職業をマスターするのに必要な職業Lv。 */
    const val JOB_MASTER_LEVEL = 30
    /** 職業の必要 EXP = 2 + jobLv / 5。 */
    const val JOB_EXP_BASE = 2

    // ---------- ダンジョンLv ----------
    /**
     * 敵ステータス倍率 = 1.25^(dlv-1)。DLv10 で約 7.5 倍。
     * 1.15 では、育っていないキャラでも運よく DLv10 を 1 回勝てて(解放条件は「DLv10 を 1 回クリア」)
     * 次のエリアが早く開きすぎたため、勝率 70% を保てる DLv が育成度に応じて緩やかに伸びるよう 1.25 に上げた。
     */
    const val DLV_ENEMY_GROWTH = 1.25
    /** EXP・ゴールド倍率 = 1.15^(dlv-1)(敵の強さより緩やか)。 */
    const val DLV_REWARD_GROWTH = 1.15
    /** 次のエリアの解放に必要な、前のエリアの DLv。 */
    const val AREA_UNLOCK_DLV = 10

    // ---------- エリア倍率(index = tier - 1) ----------
    // GameData の敵・装備は「草原と同じ桁の基礎値」で書き、ここの倍率をエリアの tier に応じてかける。
    // 数値調整はこの表だけで済む(シミュレーター BalanceSimulationTest で確認する)。
    /** 敵ステータス(HP・攻撃・防御・素早さ・運)の倍率。 */
    val AREA_STAT_SCALE: List<Double> = listOf(1.0, 4.0, 21.0, 75.0)
    /** 敵の EXP・ゴールドの倍率。 */
    val AREA_REWARD_SCALE: List<Double> = listOf(1.0, 1.5, 3.5, 27.0)
    /** 装備の性能の倍率(敵の強さに装備が見合うようにする)。 */
    val AREA_GEAR_SCALE: List<Double> = listOf(1.0, 4.5, 14.0, 80.0)
    /** ショップの価格の倍率。各エリアの装備が「そのエリアで 40〜120 戦」稼げば買える水準(EXP・ゴールド倍率の数倍)。 */
    val AREA_PRICE_SCALE: List<Double> = listOf(1.0, 5.0, 22.0, 200.0)

    // ---------- 装備 ----------
    /** 強化値 +1 ごとに基礎値へ加算される割合。 */
    const val ENHANCE_RATIO_PER_LEVEL = 0.10
    /** ドロップ率 × (1 + この値 × pLuk/(pLuk+eLuk))。 */
    const val DROP_LUK_BONUS_MAX = 0.5

    // ---------- クールタイム(秒) ----------
    // 勝っても負けても同じクールタイム(敗北の倍化は廃止。負けは「報酬なし」だけで十分な罰になるため)。
    /** 初期値。1 戦ごとに 90 秒待つ、放置寄りのテンポ。 */
    const val DEFAULT_COOLDOWN_SEC = 90
    const val MIN_COOLDOWN_SEC = 1
    const val MAX_COOLDOWN_SEC = 180
}
