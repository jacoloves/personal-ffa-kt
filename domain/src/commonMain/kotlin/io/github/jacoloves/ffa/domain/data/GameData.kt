// =============================================================================
// GameData.kt
// 役割: ゲームの静的データ(エリア・敵)を 1 か所に集めたオブジェクト。
//
// 設計判断:
//   - データは Kotlin コード内の定数にする(plan.md の決定)。型で守られ、存在しない ID はコンパイルエラー。
//   - 参照は area(id) / enemy(id) を使う。定義が抜けていれば、原因と対処つきの例外で即座に分かる。
//     (抜けは GameDataTest が検出するので、実運用でこの例外が出ることは無いはず)
//   - 敵・装備の基礎値は「草原と同じ桁」で書き、エリアの tier に応じた倍率表(Balance.AREA_*_SCALE)をヘルパーでかける。
//     エリア間の強さ・報酬・価格の整合は倍率表だけで調整できる(Phase 9。BalanceSimulationTest の自動プレイで実測して決定)。
//   - 推奨Lv は、シミュレーションで実際にそのエリアが解放された時点のキャラLv に合わせてある。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.StatRatios
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.num.SatMath

/** エリア・敵の定義データ。 */
object GameData {

    /** tier(1〜4)に対応する倍率を倍率表から引く。tier が範囲外なら原因つきで失敗する。 */
    private fun scaleOf(table: List<Double>, tier: Int, name: String): Double =
        table.getOrNull(tier - 1) ?: error(
            "tier $tier に対応する $name がありません(表の要素数=${table.size})。" +
                "Balance.$name に tier の数だけ倍率を用意してください。",
        )

    /**
     * 敵定義を簡潔に書くためのヘルパー(引数の並びは hp/atk/def/spd/luk → exp/gold → 重み → ドロップ表)。
     *
     * 数値は「草原と同じ桁の基礎値」で書き、エリアの [tier] に応じた倍率
     * ([Balance.AREA_STAT_SCALE] / [Balance.AREA_REWARD_SCALE])をここでかける。
     * こうしておくと、エリア間の強さの差の調整が Balance の倍率表だけで済み、敵ごとの「らしさ」(HP が高い・素早い等)は
     * 基礎値の比で表せる。
     *
     * ドロップ表の方針: 各敵が「そのエリアの装備(ショップ品 = 強化用 / ドロップ限定品)」を 1〜2 種類落とす。
     * ドロップ限定品は 1 エリアあたり平均 1 個以上入手できるよう、確率は 5〜12% 程度にしている。
     */
    private fun enemy(
        tier: Int, id: EnemyId, name: String,
        hp: Long, atk: Long, def: Long, spd: Long, luk: Long,
        exp: Long, gold: Long, weight: Int,
        drops: List<DropEntry>,
    ): EnemyDef {
        val stat = scaleOf(Balance.AREA_STAT_SCALE, tier, "AREA_STAT_SCALE")
        val reward = scaleOf(Balance.AREA_REWARD_SCALE, tier, "AREA_REWARD_SCALE")
        return EnemyDef(
            id, name,
            // どの値も最低 1(運と防御は 0 でもよいが、極端に小さい基礎値が 0 に潰れないよう下限を 1 にする)
            Stats(hp, atk, def, spd, luk) * stat,
            SatMath.mulRatio(exp, reward).coerceAtLeast(1),
            SatMath.mulRatio(gold, reward).coerceAtLeast(1),
            weight, drops,
        )
    }

    /** すべての敵の定義(id → 定義)。基礎値は草原と同じ桁(HP 15〜120 / 攻撃 4〜18)で書き、tier の倍率がかかる。 */
    val enemies: Map<EnemyId, EnemyDef> = buildEnemies()

    private fun buildEnemies(): Map<EnemyId, EnemyDef> = listOf(
        // ---- 草原(tier 1): 最初の 1 戦で Lv1 の戦士(HP30・攻撃11)が勝てる強さ ----
        enemy(1, EnemyId.SLIME, "スライム", 16, 4, 1, 2, 1, 3, 3, 30, listOf(DropEntry(ItemId.LEATHER_ARMOR, 0.08))),
        enemy(1, EnemyId.GIANT_RAT, "大ネズミ", 20, 5, 1, 5, 1, 4, 4, 25, listOf(DropEntry(ItemId.LUCKY_CHARM, 0.08))),
        enemy(1, EnemyId.HORNED_RABBIT, "角ウサギ", 24, 6, 1, 6, 1, 5, 5, 20, listOf(DropEntry(ItemId.WOODEN_SWORD, 0.08), DropEntry(ItemId.FANG_DAGGER, 0.08))),
        enemy(1, EnemyId.GOBLIN, "ゴブリン", 32, 7, 2, 4, 2, 7, 8, 15, listOf(DropEntry(ItemId.WOODEN_SWORD, 0.10), DropEntry(ItemId.LEATHER_ARMOR, 0.08))),
        enemy(1, EnemyId.WILD_DOG, "野犬", 40, 8, 2, 7, 2, 10, 10, 10, listOf(DropEntry(ItemId.FANG_DAGGER, 0.12), DropEntry(ItemId.LUCKY_CHARM, 0.08))),
        // ---- 森(tier 2) ----
        enemy(2, EnemyId.WOOD_WOLF, "ウッドウルフ", 30, 9, 3, 9, 3, 8, 5, 25, listOf(DropEntry(ItemId.IRON_SWORD, 0.08), DropEntry(ItemId.ELDER_WOOD_STAFF, 0.08))),
        enemy(2, EnemyId.POISON_MUSHROOM, "毒キノコ", 25, 6, 2, 2, 1, 6, 4, 30, listOf(DropEntry(ItemId.WIND_RING, 0.08))),
        enemy(2, EnemyId.FOREST_GOBLIN_CHIEF, "森のゴブリン長", 45, 11, 4, 5, 2, 12, 8, 15, listOf(DropEntry(ItemId.CHAIN_MAIL, 0.10), DropEntry(ItemId.ELDER_WOOD_STAFF, 0.10))),
        enemy(2, EnemyId.TREANT, "トレント", 60, 10, 6, 2, 1, 15, 10, 10, listOf(DropEntry(ItemId.ELDER_WOOD_STAFF, 0.12), DropEntry(ItemId.CHAIN_MAIL, 0.08))),
        enemy(2, EnemyId.FAIRY_PRANKSTER, "妖精の悪戯者", 20, 8, 2, 10, 6, 10, 7, 20, listOf(DropEntry(ItemId.WIND_RING, 0.10), DropEntry(ItemId.IRON_SWORD, 0.06))),
        // ---- 洞窟(tier 3) ----
        enemy(3, EnemyId.GIANT_BAT, "大コウモリ", 25, 8, 2, 10, 2, 8, 5, 25, listOf(DropEntry(ItemId.GEM_PENDANT, 0.08), DropEntry(ItemId.OGRE_CLUB, 0.06))),
        enemy(3, EnemyId.GOLEM, "ゴーレム", 80, 10, 8, 1, 1, 15, 10, 10, listOf(DropEntry(ItemId.STEEL_SWORD, 0.08), DropEntry(ItemId.OGRE_CLUB, 0.10))),
        enemy(3, EnemyId.CAVE_LIZARD, "地底トカゲ", 40, 9, 4, 4, 1, 10, 7, 25, listOf(DropEntry(ItemId.MITHRIL_ARMOR, 0.08))),
        enemy(3, EnemyId.SKELETON, "スケルトン", 35, 11, 3, 5, 2, 11, 8, 25, listOf(DropEntry(ItemId.STEEL_SWORD, 0.08), DropEntry(ItemId.GEM_PENDANT, 0.08))),
        enemy(3, EnemyId.CAVE_OGRE, "洞窟オーガ", 70, 13, 5, 3, 1, 16, 12, 15, listOf(DropEntry(ItemId.OGRE_CLUB, 0.12), DropEntry(ItemId.MITHRIL_ARMOR, 0.08))),
        // ---- 火山(tier 4) ----
        enemy(4, EnemyId.FIRE_LIZARD, "火トカゲ", 40, 10, 4, 4, 1, 10, 7, 25, listOf(DropEntry(ItemId.FLAME_SWORD, 0.08), DropEntry(ItemId.DRAGON_FANG, 0.06))),
        enemy(4, EnemyId.LAVA_SLIME, "溶岩スライム", 60, 9, 6, 2, 1, 9, 6, 25, listOf(DropEntry(ItemId.PHOENIX_FEATHER, 0.08), DropEntry(ItemId.DRAGON_SCALE_ARMOR, 0.08))),
        enemy(4, EnemyId.FLAME_BEAST, "炎の魔獣", 70, 14, 5, 6, 2, 14, 10, 20, listOf(DropEntry(ItemId.DRAGON_FANG, 0.10), DropEntry(ItemId.FLAME_SWORD, 0.08))),
        enemy(4, EnemyId.FIRE_ELEMENTAL, "ファイアエレメント", 50, 16, 3, 5, 3, 15, 11, 20, listOf(DropEntry(ItemId.PHOENIX_FEATHER, 0.10))),
        enemy(4, EnemyId.YOUNG_DRAGON, "若竜", 120, 18, 9, 4, 2, 25, 20, 10, listOf(DropEntry(ItemId.DRAGON_FANG, 0.12), DropEntry(ItemId.DRAGON_SCALE_ARMOR, 0.10))),
    ).associateBy { it.id }

    /**
     * アイテム定義の短縮ヘルパー(引数は hp/atk/def/spd/luk の順)。
     * 性能は [Balance.AREA_GEAR_SCALE]、価格は [Balance.AREA_PRICE_SCALE] を、エリアの [tier] に応じてかける。
     */
    private fun item(
        tier: Int, id: ItemId, name: String, slot: Slot,
        hp: Long = 0, atk: Long = 0, def: Long = 0, spd: Long = 0, luk: Long = 0,
        price: Long? = null, shopArea: AreaId? = null,
    ): ItemDef {
        val gear = scaleOf(Balance.AREA_GEAR_SCALE, tier, "AREA_GEAR_SCALE")
        val priceScale = scaleOf(Balance.AREA_PRICE_SCALE, tier, "AREA_PRICE_SCALE")
        return ItemDef(
            id, name, slot, Stats(hp, atk, def, spd, luk) * gear,
            price?.let { SatMath.mulRatio(it, priceScale) }, shopArea,
        )
    }

    /**
     * すべてのアイテムの定義(id → 定義)。エリアごとに「武器・防具・装身具のショップ品」と「ドロップ限定の武器 1 つ」。
     * 基礎値(草原と同じ桁)に、エリアの倍率(性能・価格)がかかる。
     */
    val items: Map<ItemId, ItemDef> = buildItems()

    private fun buildItems(): Map<ItemId, ItemDef> = listOf(
        // ---- 草原 ----
        item(1, ItemId.WOODEN_SWORD, "木の剣", Slot.WEAPON, atk = 3, price = 50, shopArea = AreaId.MEADOW),
        item(1, ItemId.LEATHER_ARMOR, "革の鎧", Slot.ARMOR, hp = 15, def = 2, price = 80, shopArea = AreaId.MEADOW),
        item(1, ItemId.LUCKY_CHARM, "幸運のお守り", Slot.ACCESSORY, spd = 1, luk = 3, price = 60, shopArea = AreaId.MEADOW),
        item(1, ItemId.FANG_DAGGER, "牙の短剣", Slot.WEAPON, atk = 5, spd = 2),
        // ---- 森 ----
        item(2, ItemId.IRON_SWORD, "鉄の剣", Slot.WEAPON, atk = 3, price = 50, shopArea = AreaId.FOREST),
        item(2, ItemId.CHAIN_MAIL, "鎖かたびら", Slot.ARMOR, hp = 15, def = 2, price = 80, shopArea = AreaId.FOREST),
        item(2, ItemId.WIND_RING, "風の指輪", Slot.ACCESSORY, spd = 2, luk = 1, price = 60, shopArea = AreaId.FOREST),
        item(2, ItemId.ELDER_WOOD_STAFF, "古樹の杖", Slot.WEAPON, hp = 5, atk = 5),
        // ---- 洞窟 ----
        item(3, ItemId.STEEL_SWORD, "鋼の剣", Slot.WEAPON, atk = 3, price = 50, shopArea = AreaId.CAVE),
        item(3, ItemId.MITHRIL_ARMOR, "ミスリルの鎧", Slot.ARMOR, hp = 15, def = 2, price = 80, shopArea = AreaId.CAVE),
        item(3, ItemId.GEM_PENDANT, "宝石のペンダント", Slot.ACCESSORY, atk = 1, luk = 1, price = 60, shopArea = AreaId.CAVE),
        item(3, ItemId.OGRE_CLUB, "鬼の大棍棒", Slot.WEAPON, hp = 3, atk = 6),
        // ---- 火山 ----
        item(4, ItemId.FLAME_SWORD, "炎の剣", Slot.WEAPON, atk = 3, price = 50, shopArea = AreaId.VOLCANO),
        item(4, ItemId.DRAGON_SCALE_ARMOR, "竜鱗の鎧", Slot.ARMOR, hp = 15, def = 2, price = 80, shopArea = AreaId.VOLCANO),
        item(4, ItemId.PHOENIX_FEATHER, "不死鳥の羽飾り", Slot.ACCESSORY, spd = 1, luk = 1, price = 60, shopArea = AreaId.VOLCANO),
        item(4, ItemId.DRAGON_FANG, "竜牙の剣", Slot.WEAPON, atk = 6),
    ).associateBy { it.id }

    /** アイテム定義を返す。無ければ(= データ入力ミス)原因と対処つきで失敗する。 */
    fun item(id: ItemId): ItemDef = checkNotNull(items[id]) {
        "アイテム $id の定義が GameData.items にありません。ItemId に値を追加したのに GameData への登録を忘れている可能性があります。" +
            "GameData.items に item(...) を追加してください。"
    }

    /** [area] のショップで売っている商品(価格が付いているもの)。 */
    fun shopItems(area: AreaId): List<ItemDef> = items.values.filter { it.shopArea == area && it.price != null }

    /** すべてのエリアの定義(id → 定義)。解放条件は「1 つ前のエリアで DLv 10」。 */
    val areas: Map<AreaId, AreaDef> = buildAreas()

    private fun buildAreas(): Map<AreaId, AreaDef> = listOf(
        AreaDef(
            AreaId.MEADOW, "はじまりの草原", recommendedLevel = 1, tier = 1,
            enemies = listOf(EnemyId.SLIME, EnemyId.GIANT_RAT, EnemyId.HORNED_RABBIT, EnemyId.GOBLIN, EnemyId.WILD_DOG),
            unlock = null,
        ),
        AreaDef(
            AreaId.FOREST, "迷いの森", recommendedLevel = 10, tier = 2,
            enemies = listOf(
                EnemyId.WOOD_WOLF, EnemyId.POISON_MUSHROOM, EnemyId.FOREST_GOBLIN_CHIEF,
                EnemyId.TREANT, EnemyId.FAIRY_PRANKSTER,
            ),
            unlock = AreaUnlock(AreaId.MEADOW, Balance.AREA_UNLOCK_DLV),
        ),
        AreaDef(
            AreaId.CAVE, "地底洞窟", recommendedLevel = 25, tier = 3,
            enemies = listOf(
                EnemyId.GIANT_BAT, EnemyId.GOLEM, EnemyId.CAVE_LIZARD, EnemyId.SKELETON, EnemyId.CAVE_OGRE,
            ),
            unlock = AreaUnlock(AreaId.FOREST, Balance.AREA_UNLOCK_DLV),
        ),
        AreaDef(
            AreaId.VOLCANO, "灼熱の火山", recommendedLevel = 56, tier = 4,
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
