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

    /**
     * 敵定義を簡潔に書くためのヘルパー(引数の並びは hp/atk/def/spd/luk → exp/gold → 重み → ドロップ表)。
     *
     * ドロップ表の方針: 各敵が「そのエリアの装備(ショップ品 = 強化用 / ドロップ限定品)」を 1〜2 種類落とす。
     * 確率は 2〜10% 程度。ショップ品が落ちるのは、重複入手による強化(+1)を狙えるようにするため。
     */
    private fun enemy(
        id: EnemyId, name: String,
        hp: Long, atk: Long, def: Long, spd: Long, luk: Long,
        exp: Long, gold: Long, weight: Int,
        drops: List<DropEntry>,
    ) = EnemyDef(id, name, Stats(hp, atk, def, spd, luk), exp, gold, weight, drops)

    /** すべての敵の定義(id → 定義)。 */
    val enemies: Map<EnemyId, EnemyDef> = listOf(
        // ---- 草原(tier 1): HP 20〜60 / 攻撃 5〜12 ----
        enemy(EnemyId.SLIME, "スライム", 20, 5, 1, 2, 1, 5, 3, 30, listOf(DropEntry(ItemId.LEATHER_ARMOR, 0.05))),
        enemy(EnemyId.GIANT_RAT, "大ネズミ", 25, 6, 1, 6, 1, 6, 4, 25, listOf(DropEntry(ItemId.LUCKY_CHARM, 0.05))),
        enemy(EnemyId.HORNED_RABBIT, "角ウサギ", 30, 8, 2, 8, 2, 8, 5, 20, listOf(DropEntry(ItemId.WOODEN_SWORD, 0.05), DropEntry(ItemId.FANG_DAGGER, 0.03))),
        enemy(EnemyId.GOBLIN, "ゴブリン", 45, 10, 3, 5, 2, 12, 8, 15, listOf(DropEntry(ItemId.WOODEN_SWORD, 0.08), DropEntry(ItemId.LEATHER_ARMOR, 0.06))),
        enemy(EnemyId.WILD_DOG, "野犬", 60, 12, 3, 9, 3, 16, 10, 10, listOf(DropEntry(ItemId.FANG_DAGGER, 0.08), DropEntry(ItemId.LUCKY_CHARM, 0.05))),
        // ---- 森(tier 2): 草原の約 10 倍 ----
        enemy(EnemyId.WOOD_WOLF, "ウッドウルフ", 300, 90, 25, 90, 25, 80, 50, 25, listOf(DropEntry(ItemId.IRON_SWORD, 0.05), DropEntry(ItemId.ELDER_WOOD_STAFF, 0.03))),
        enemy(EnemyId.POISON_MUSHROOM, "毒キノコ", 250, 60, 20, 20, 10, 60, 40, 30, listOf(DropEntry(ItemId.WIND_RING, 0.05))),
        enemy(EnemyId.FOREST_GOBLIN_CHIEF, "森のゴブリン長", 450, 110, 35, 50, 20, 120, 80, 15, listOf(DropEntry(ItemId.CHAIN_MAIL, 0.06), DropEntry(ItemId.ELDER_WOOD_STAFF, 0.05))),
        enemy(EnemyId.TREANT, "トレント", 600, 100, 60, 20, 10, 150, 100, 10, listOf(DropEntry(ItemId.ELDER_WOOD_STAFF, 0.08), DropEntry(ItemId.CHAIN_MAIL, 0.05))),
        enemy(EnemyId.FAIRY_PRANKSTER, "妖精の悪戯者", 200, 80, 15, 100, 60, 100, 70, 20, listOf(DropEntry(ItemId.WIND_RING, 0.06), DropEntry(ItemId.IRON_SWORD, 0.03))),
        // ---- 洞窟(tier 3): 森の約 10 倍 ----
        enemy(EnemyId.GIANT_BAT, "大コウモリ", 2_500, 800, 200, 1_000, 200, 800, 500, 25, listOf(DropEntry(ItemId.GEM_PENDANT, 0.05), DropEntry(ItemId.OGRE_CLUB, 0.02))),
        enemy(EnemyId.GOLEM, "ゴーレム", 8_000, 1_000, 800, 100, 50, 1_500, 1_000, 10, listOf(DropEntry(ItemId.STEEL_SWORD, 0.05), DropEntry(ItemId.OGRE_CLUB, 0.03))),
        enemy(EnemyId.CAVE_LIZARD, "地底トカゲ", 4_000, 900, 400, 400, 100, 1_000, 700, 25, listOf(DropEntry(ItemId.MITHRIL_ARMOR, 0.05))),
        enemy(EnemyId.SKELETON, "スケルトン", 3_500, 1_100, 300, 500, 150, 1_100, 800, 25, listOf(DropEntry(ItemId.STEEL_SWORD, 0.05), DropEntry(ItemId.GEM_PENDANT, 0.04))),
        enemy(EnemyId.CAVE_OGRE, "洞窟オーガ", 7_000, 1_300, 500, 250, 100, 1_600, 1_200, 15, listOf(DropEntry(ItemId.OGRE_CLUB, 0.08), DropEntry(ItemId.MITHRIL_ARMOR, 0.05))),
        // ---- 火山(tier 4): 洞窟の約 10 倍 ----
        enemy(EnemyId.FIRE_LIZARD, "火トカゲ", 40_000, 10_000, 4_000, 4_000, 1_000, 10_000, 7_000, 25, listOf(DropEntry(ItemId.FLAME_SWORD, 0.05), DropEntry(ItemId.DRAGON_FANG, 0.02))),
        enemy(EnemyId.LAVA_SLIME, "溶岩スライム", 60_000, 9_000, 6_000, 1_500, 500, 9_000, 6_000, 25, listOf(DropEntry(ItemId.PHOENIX_FEATHER, 0.05), DropEntry(ItemId.DRAGON_SCALE_ARMOR, 0.04))),
        enemy(EnemyId.FLAME_BEAST, "炎の魔獣", 70_000, 14_000, 5_000, 6_000, 1_500, 14_000, 10_000, 20, listOf(DropEntry(ItemId.DRAGON_FANG, 0.05), DropEntry(ItemId.FLAME_SWORD, 0.04))),
        enemy(EnemyId.FIRE_ELEMENTAL, "ファイアエレメント", 50_000, 16_000, 3_000, 5_000, 2_500, 15_000, 11_000, 20, listOf(DropEntry(ItemId.PHOENIX_FEATHER, 0.06))),
        enemy(EnemyId.YOUNG_DRAGON, "若竜", 120_000, 18_000, 9_000, 3_500, 2_000, 25_000, 20_000, 10, listOf(DropEntry(ItemId.DRAGON_FANG, 0.1), DropEntry(ItemId.DRAGON_SCALE_ARMOR, 0.08))),
    ).associateBy { it.id }

    /** アイテム定義の短縮ヘルパー(引数は hp/atk/def/spd/luk の順)。 */
    private fun item(
        id: ItemId, name: String, slot: Slot,
        hp: Long = 0, atk: Long = 0, def: Long = 0, spd: Long = 0, luk: Long = 0,
        price: Long? = null, shopArea: AreaId? = null,
    ) = ItemDef(id, name, slot, Stats(hp, atk, def, spd, luk), price, shopArea)

    /**
     * すべてのアイテムの定義(id → 定義)。
     * エリアごとに「武器・防具・装身具のショップ品」と「ドロップ限定の武器 1 つ」。
     * 性能・価格はエリアが進むほど約 10 倍(敵の強さに合わせた初期案。Phase 9 で調整する)。
     */
    val items: Map<ItemId, ItemDef> = listOf(
        // ---- 草原 ----
        item(ItemId.WOODEN_SWORD, "木の剣", Slot.WEAPON, atk = 3, price = 50, shopArea = AreaId.MEADOW),
        item(ItemId.LEATHER_ARMOR, "革の鎧", Slot.ARMOR, hp = 15, def = 2, price = 80, shopArea = AreaId.MEADOW),
        item(ItemId.LUCKY_CHARM, "幸運のお守り", Slot.ACCESSORY, spd = 1, luk = 3, price = 60, shopArea = AreaId.MEADOW),
        item(ItemId.FANG_DAGGER, "牙の短剣", Slot.WEAPON, atk = 5, spd = 2),
        // ---- 森 ----
        item(ItemId.IRON_SWORD, "鉄の剣", Slot.WEAPON, atk = 30, price = 800, shopArea = AreaId.FOREST),
        item(ItemId.CHAIN_MAIL, "鎖かたびら", Slot.ARMOR, hp = 150, def = 20, price = 1_200, shopArea = AreaId.FOREST),
        item(ItemId.WIND_RING, "風の指輪", Slot.ACCESSORY, spd = 20, luk = 10, price = 1_000, shopArea = AreaId.FOREST),
        item(ItemId.ELDER_WOOD_STAFF, "古樹の杖", Slot.WEAPON, hp = 50, atk = 50),
        // ---- 洞窟 ----
        item(ItemId.STEEL_SWORD, "鋼の剣", Slot.WEAPON, atk = 300, price = 15_000, shopArea = AreaId.CAVE),
        item(ItemId.MITHRIL_ARMOR, "ミスリルの鎧", Slot.ARMOR, hp = 1_500, def = 200, price = 25_000, shopArea = AreaId.CAVE),
        item(ItemId.GEM_PENDANT, "宝石のペンダント", Slot.ACCESSORY, atk = 50, luk = 100, price = 20_000, shopArea = AreaId.CAVE),
        item(ItemId.OGRE_CLUB, "鬼の大棍棒", Slot.WEAPON, hp = 300, atk = 550),
        // ---- 火山 ----
        item(ItemId.FLAME_SWORD, "炎の剣", Slot.WEAPON, atk = 3_000, price = 300_000, shopArea = AreaId.VOLCANO),
        item(ItemId.DRAGON_SCALE_ARMOR, "竜鱗の鎧", Slot.ARMOR, hp = 15_000, def = 2_000, price = 500_000, shopArea = AreaId.VOLCANO),
        item(ItemId.PHOENIX_FEATHER, "不死鳥の羽飾り", Slot.ACCESSORY, spd = 1_000, luk = 1_000, price = 400_000, shopArea = AreaId.VOLCANO),
        item(ItemId.DRAGON_FANG, "竜牙の剣", Slot.WEAPON, atk = 6_000),
    ).associateBy { it.id }

    /** アイテム定義を返す。無ければ(= データ入力ミス)原因と対処つきで失敗する。 */
    fun item(id: ItemId): ItemDef = checkNotNull(items[id]) {
        "アイテム $id の定義が GameData.items にありません。ItemId に値を追加したのに GameData への登録を忘れている可能性があります。" +
            "GameData.items に item(...) を追加してください。"
    }

    /** [area] のショップで売っている商品(価格が付いているもの)。 */
    fun shopItems(area: AreaId): List<ItemDef> = items.values.filter { it.shopArea == area && it.price != null }

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
