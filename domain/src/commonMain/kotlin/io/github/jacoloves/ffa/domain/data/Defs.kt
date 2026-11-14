// =============================================================================
// Defs.kt
// 役割: ゲームデータの「定義」を表す不変の data class 群(敵・ドロップ・エリア・解放条件)。
//       実際の値は GameData に置き、ここは形(スキーマ)だけを持つ。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

import io.github.jacoloves.ffa.domain.Stats

/**
 * 敵 1 種類の定義。DLv による強化前の「基礎値」を持つ。
 *
 * @property stats DLv 1 のときのステータス
 * @property exp DLv 1 のときに得られる経験値(キャラ EXP)
 * @property gold DLv 1 のときに得られるゴールド
 * @property weight エリア内での出現しやすさ(重み付き抽選の重み。1 以上)
 * @property drops ドロップ表(Phase 5 で設定する。今は空)
 */
data class EnemyDef(
    val id: EnemyId,
    val name: String,
    val stats: Stats,
    val exp: Long,
    val gold: Long,
    val weight: Int,
    val drops: List<DropEntry> = emptyList(),
)

/**
 * ドロップ表の 1 行。
 *
 * @property item 落とすアイテム
 * @property chance 基本のドロップ確率(0.0〜1.0)。運による補正は Phase 5 で掛ける
 */
data class DropEntry(val item: ItemId, val chance: Double)

/**
 * エリアの定義。
 *
 * @property tier 難易度の段階(1〜4)。Phase 4 で職業 EXP の獲得量に使う
 * @property enemies このエリアに出現する敵(順序は表示・抽選の順)
 * @property unlock 解放条件。null は最初から解放されている
 */
data class AreaDef(
    val id: AreaId,
    val name: String,
    val tier: Int,
    val enemies: List<EnemyId>,
    val unlock: AreaUnlock?,
)

/** エリアの解放条件「[area] で DLv [dlv] を(クリアして)到達すること」。 */
data class AreaUnlock(val area: AreaId, val dlv: Int)
