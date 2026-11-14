// =============================================================================
// GameData.kt
// 役割: ゲームの静的データ(エリア・敵)を 1 か所に集めたオブジェクト。
//
// 設計判断:
//   - データは Kotlin コード内の定数にする(plan.md の決定)。型で守られ、存在しない ID はコンパイルエラー。
//   - 参照は area(id) / enemy(id) を使う。定義が抜けていれば、原因と対処つきの例外で即座に分かる。
//     (抜けは GameDataTest が検出するので、実運用でこの例外が出ることは無いはず)
//   - 敵ステータスは「後のエリアほど約 10 倍」。草原を基準に、森 → 洞窟 → 火山で桁が上がる。
//   - 数値は初期案。Phase 9 で調整する。係数は Balance に、敵の基礎値はここにある。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.StatRatios
import io.github.jacoloves.ffa.domain.Stats

/** エリア・敵の定義データ。 */
object GameData {

    /** 敵定義を簡潔に書くためのヘルパー(引数の並びは hp/atk/def/spd/luk → exp/gold → 重み)。 */
    // TODO(Phase 5): アイテム(ItemId)が実装されたら、ここの drops にドロップ表を設定する。
    private fun enemy(
        id: EnemyId, name: String,
        hp: Long, atk: Long, def: Long, spd: Long, luk: Long,
        exp: Long, gold: Long, weight: Int,
    ) = EnemyDef(id, name, Stats(hp, atk, def, spd, luk), exp, gold, weight, drops = emptyList())

    /** すべての敵の定義(id → 定義)。 */
    val enemies: Map<EnemyId, EnemyDef> = listOf(
        // ---- 草原(tier 1): HP 20〜60 / 攻撃 5〜12 ----
        enemy(EnemyId.SLIME, "スライム", 20, 5, 1, 2, 1, 5, 3, 30),
        enemy(EnemyId.GIANT_RAT, "大ネズミ", 25, 6, 1, 6, 1, 6, 4, 25),
        enemy(EnemyId.HORNED_RABBIT, "角ウサギ", 30, 8, 2, 8, 2, 8, 5, 20),
        enemy(EnemyId.GOBLIN, "ゴブリン", 45, 10, 3, 5, 2, 12, 8, 15),
        enemy(EnemyId.WILD_DOG, "野犬", 60, 12, 3, 9, 3, 16, 10, 10),
        // ---- 森(tier 2): 草原の約 10 倍 ----
        enemy(EnemyId.WOOD_WOLF, "ウッドウルフ", 300, 90, 25, 90, 25, 80, 50, 25),
        enemy(EnemyId.POISON_MUSHROOM, "毒キノコ", 250, 60, 20, 20, 10, 60, 40, 30),
        enemy(EnemyId.FOREST_GOBLIN_CHIEF, "森のゴブリン長", 450, 110, 35, 50, 20, 120, 80, 15),
        enemy(EnemyId.TREANT, "トレント", 600, 100, 60, 20, 10, 150, 100, 10),
        enemy(EnemyId.FAIRY_PRANKSTER, "妖精の悪戯者", 200, 80, 15, 100, 60, 100, 70, 20),
        // ---- 洞窟(tier 3): 森の約 10 倍 ----
        enemy(EnemyId.GIANT_BAT, "大コウモリ", 2_500, 800, 200, 1_000, 200, 800, 500, 25),
        enemy(EnemyId.GOLEM, "ゴーレム", 8_000, 1_000, 800, 100, 50, 1_500, 1_000, 10),
        enemy(EnemyId.CAVE_LIZARD, "地底トカゲ", 4_000, 900, 400, 400, 100, 1_000, 700, 25),
        enemy(EnemyId.SKELETON, "スケルトン", 3_500, 1_100, 300, 500, 150, 1_100, 800, 25),
        enemy(EnemyId.CAVE_OGRE, "洞窟オーガ", 7_000, 1_300, 500, 250, 100, 1_600, 1_200, 15),
        // ---- 火山(tier 4): 洞窟の約 10 倍 ----
        enemy(EnemyId.FIRE_LIZARD, "火トカゲ", 40_000, 10_000, 4_000, 4_000, 1_000, 10_000, 7_000, 25),
        enemy(EnemyId.LAVA_SLIME, "溶岩スライム", 60_000, 9_000, 6_000, 1_500, 500, 9_000, 6_000, 25),
        enemy(EnemyId.FLAME_BEAST, "炎の魔獣", 70_000, 14_000, 5_000, 6_000, 1_500, 14_000, 10_000, 20),
        enemy(EnemyId.FIRE_ELEMENTAL, "ファイアエレメント", 50_000, 16_000, 3_000, 5_000, 2_500, 15_000, 11_000, 20),
        enemy(EnemyId.YOUNG_DRAGON, "若竜", 120_000, 18_000, 9_000, 3_500, 2_000, 25_000, 20_000, 10),
    ).associateBy { it.id }

    /** すべてのエリアの定義(id → 定義)。解放条件は「1 つ前のエリアで DLv 10」。 */
    val areas: Map<AreaId, AreaDef> = listOf(
        AreaDef(
            AreaId.MEADOW, "はじまりの草原", tier = 1,
            enemies = listOf(EnemyId.SLIME, EnemyId.GIANT_RAT, EnemyId.HORNED_RABBIT, EnemyId.GOBLIN, EnemyId.WILD_DOG),
            unlock = null,
        ),
        AreaDef(
            AreaId.FOREST, "迷いの森", tier = 2,
            enemies = listOf(
                EnemyId.WOOD_WOLF, EnemyId.POISON_MUSHROOM, EnemyId.FOREST_GOBLIN_CHIEF,
                EnemyId.TREANT, EnemyId.FAIRY_PRANKSTER,
            ),
            unlock = AreaUnlock(AreaId.MEADOW, Balance.AREA_UNLOCK_DLV),
        ),
        AreaDef(
            AreaId.CAVE, "地底洞窟", tier = 3,
            enemies = listOf(
                EnemyId.GIANT_BAT, EnemyId.GOLEM, EnemyId.CAVE_LIZARD, EnemyId.SKELETON, EnemyId.CAVE_OGRE,
            ),
            unlock = AreaUnlock(AreaId.FOREST, Balance.AREA_UNLOCK_DLV),
        ),
        AreaDef(
            AreaId.VOLCANO, "灼熱の火山", tier = 4,
            enemies = listOf(
                EnemyId.FIRE_LIZARD, EnemyId.LAVA_SLIME, EnemyId.FLAME_BEAST,
                EnemyId.FIRE_ELEMENTAL, EnemyId.YOUNG_DRAGON,
            ),
            unlock = AreaUnlock(AreaId.CAVE, Balance.AREA_UNLOCK_DLV),
        ),
    ).associateBy { it.id }

    /** エリア定義を返す。定義が無ければ(= データ入力ミス)原因と対処つきで失敗する。 */
    fun area(id: AreaId): AreaDef = checkNotNull(areas[id]) {
        "エリア $id の定義が GameData.areas にありません。AreaId に値を追加したのに GameData への登録を忘れている可能性があります。" +
            "GameData.areas に AreaDef を追加してください。"
    }

    /** 敵定義を返す。定義が無ければ(= データ入力ミス)原因と対処つきで失敗する。 */
    fun enemy(id: EnemyId): EnemyDef = checkNotNull(enemies[id]) {
        "敵 $id の定義が GameData.enemies にありません。EnemyId に値を追加したのに GameData への登録を忘れている可能性があります。" +
            "GameData.enemies に enemy(...) を追加してください。"
    }

    /** すべての職業の定義(id → 定義)。数値は設計書 Phase 4 の表のとおり。 */
    val jobs: Map<JobId, JobDef> = listOf(
        JobDef(
            JobId.WARRIOR, "戦士", 1,
            base = Stats(30, 8, 4, 5, 3), growth = Stats(6, 3, 1, 1, 1),
            masterBonus = StatRatios(atk = 0.05), requires = emptySet(),
        ),
        JobDef(
            JobId.KNIGHT, "騎士", 1,
            base = Stats(40, 5, 7, 3, 3), growth = Stats(9, 1, 3, 1, 1),
            masterBonus = StatRatios(def = 0.05), requires = emptySet(),
        ),
        JobDef(
            JobId.THIEF, "盗賊", 1,
            base = Stats(25, 6, 3, 8, 7), growth = Stats(5, 2, 1, 3, 2),
            masterBonus = StatRatios(spd = 0.05), requires = emptySet(),
        ),
        JobDef(
            JobId.PRIEST, "僧侶", 1,
            base = Stats(35, 5, 5, 4, 5), growth = Stats(8, 2, 2, 1, 2),
            masterBonus = StatRatios(hp = 0.05), requires = emptySet(),
        ),
        JobDef(
            JobId.SWORD_MASTER, "剣聖", 2,
            base = Stats(35, 10, 4, 8, 5), growth = Stats(8, 4, 1, 3, 2),
            masterBonus = StatRatios(atk = 0.10, spd = 0.05), requires = setOf(JobId.WARRIOR, JobId.THIEF),
        ),
        JobDef(
            JobId.PALADIN, "聖騎士", 2,
            base = Stats(50, 7, 8, 4, 4), growth = Stats(11, 2, 3, 1, 1),
            masterBonus = StatRatios(hp = 0.10, def = 0.05), requires = setOf(JobId.KNIGHT, JobId.PRIEST),
        ),
    ).associateBy { it.id }

    /** 職業定義を返す。無ければ(= データ入力ミス)原因と対処つきで失敗する。 */
    fun job(id: JobId): JobDef = checkNotNull(jobs[id]) {
        "職業 $id の定義が GameData.jobs にありません。JobId に値を追加したのに GameData への登録を忘れている可能性があります。" +
            "GameData.jobs に JobDef を追加してください。"
    }
}
