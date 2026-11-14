// =============================================================================
// GameDataTest.kt
// 役割: GameData(エリア・敵の定義)の整合性を検証する。
//       「enum に定義が抜けている」「敵がどのエリアにも属さない」といった
//       データ入力ミスを、実行時ではなくテストで確実に検出するための安全網。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

import io.github.jacoloves.ffa.domain.Balance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameDataTest {

    /** すべての AreaId に定義があり、定義の id がキーと一致すること。 */
    @Test
    fun すべてのAreaIdに定義がある() {
        for (id in AreaId.entries) {
            assertEquals(id, GameData.area(id).id)
        }
        assertEquals(AreaId.entries.size, GameData.areas.size)
    }

    /** すべての EnemyId に定義があり、定義の id がキーと一致すること。 */
    @Test
    fun すべてのEnemyIdに定義がある() {
        for (id in EnemyId.entries) {
            assertEquals(id, GameData.enemy(id).id)
        }
        assertEquals(EnemyId.entries.size, GameData.enemies.size)
    }

    /** 4 エリア × 敵 5 体であること(仕様)。 */
    @Test
    fun 四エリア各五体() {
        assertEquals(4, AreaId.entries.size)
        assertEquals(20, EnemyId.entries.size)
        for (area in GameData.areas.values) {
            assertEquals(5, area.enemies.size, "${area.name} の敵は 5 体のはず")
        }
    }

    /** エリアの敵リストが空でなく、重複もないこと。 */
    @Test
    fun エリアの敵リストは空でなく重複しない() {
        for (area in GameData.areas.values) {
            assertTrue(area.enemies.isNotEmpty(), "${area.name} の敵リストが空")
            assertEquals(area.enemies.size, area.enemies.toSet().size, "${area.name} に重複がある")
        }
    }

    /** どの敵も、ちょうど 1 つのエリアに所属していること。 */
    @Test
    fun すべての敵がちょうど1つのエリアに属する() {
        val all = GameData.areas.values.flatMap { it.enemies }
        assertEquals(EnemyId.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size, "同じ敵が複数エリアに登録されている")
    }

    /** 敵の定義値(名前・重み・ステータス・報酬・ドロップ率)が妥当であること。 */
    @Test
    fun 敵の定義値が妥当() {
        for (e in GameData.enemies.values) {
            assertTrue(e.name.isNotBlank(), "${e.id} の名前が空")
            assertTrue(e.weight > 0, "${e.name} の出現重みが 0 以下")
            val s = e.stats
            assertTrue(s.hp > 0 && s.atk > 0 && s.def >= 0 && s.spd > 0 && s.luk >= 0, "${e.name} のステータスが不正: $s")
            assertTrue(e.exp > 0 && e.gold > 0, "${e.name} の報酬が 0 以下")
            for (d in e.drops) assertTrue(d.chance in 0.0..1.0, "${e.name} のドロップ率が範囲外")
        }
        // 名前の重複がないこと(ログで区別できなくなるため)
        val names = GameData.enemies.values.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    /** エリアの tier が 1〜4 で、解放条件が 1 つ前のエリアを DLv 10 で要求していること。 */
    @Test
    fun エリアの解放条件() {
        assertEquals(listOf(1, 2, 3, 4), GameData.areas.values.map { it.tier }.sorted())
        assertNull(GameData.area(AreaId.MEADOW).unlock, "最初のエリアは最初から解放")
        val order = listOf(AreaId.MEADOW, AreaId.FOREST, AreaId.CAVE, AreaId.VOLCANO)
        for (i in 1 until order.size) {
            val unlock = assertNotNull(GameData.area(order[i]).unlock)
            assertEquals(order[i - 1], unlock.area)
            assertEquals(Balance.AREA_UNLOCK_DLV, unlock.dlv)
        }
    }

    /** 後のエリアほど敵が強い(平均 HP が 3 倍以上ずつ増える。倍率表 Balance.AREA_STAT_SCALE が反映されている)。 */
    @Test
    fun エリアが進むほど敵は強くなる() {
        val order = listOf(AreaId.MEADOW, AreaId.FOREST, AreaId.CAVE, AreaId.VOLCANO)
        val avgHp = order.map { id ->
            val enemies = GameData.area(id).enemies.map { GameData.enemy(it) }
            enemies.sumOf { it.stats.hp } / enemies.size
        }
        for (i in 1 until avgHp.size) {
            assertTrue(avgHp[i] >= avgHp[i - 1] * 3, "平均HPが十分に増えていない: $avgHp")
        }
    }

    /** 草原の敵は、最初の 1 戦で Lv1 の戦士(HP30・攻撃11)が勝てる範囲(HP 15〜60、攻撃 4〜12)に収まっていること。 */
    @Test
    fun 草原の敵は設計書の範囲() {
        for (id in GameData.area(AreaId.MEADOW).enemies) {
            val s = GameData.enemy(id).stats
            assertTrue(s.hp in 15..60, "$id hp=${s.hp}")
            assertTrue(s.atk in 4..12, "$id atk=${s.atk}")
        }
    }

    /** 推奨キャラLv がシミュレーション(BalanceSimulationTest)で測った実態どおり(草原1・森10・洞窟25・火山56)であること。 */
    @Test
    fun 推奨レベルが仕様どおり() {
        assertEquals(1L, GameData.area(AreaId.MEADOW).recommendedLevel)
        assertEquals(10L, GameData.area(AreaId.FOREST).recommendedLevel)
        assertEquals(25L, GameData.area(AreaId.CAVE).recommendedLevel)
        assertEquals(56L, GameData.area(AreaId.VOLCANO).recommendedLevel)
    }

    /** 推奨Lv は後のエリアほど大きい(単調増加)こと。UI の警告の前提。 */
    @Test
    fun 推奨レベルはエリアが進むほど大きい() {
        val levels = AreaId.entries.map { GameData.area(it).recommendedLevel }
        assertEquals(levels.sorted(), levels)
        assertEquals(levels.size, levels.toSet().size)
    }

    /** 敵のステータス・報酬、装備の性能・価格に、エリア(tier)の倍率表がかかっていること。 */
    @Test
    fun エリア倍率が敵と装備に反映されている() {
        // 森(tier 2): ウッドウルフの基礎値は HP 30・攻撃 9・EXP 8・ゴールド 5
        val wolf = GameData.enemy(EnemyId.WOOD_WOLF)
        assertEquals((30 * Balance.AREA_STAT_SCALE[1]).toLong(), wolf.stats.hp)
        assertEquals((9 * Balance.AREA_STAT_SCALE[1]).toLong(), wolf.stats.atk)
        assertEquals((8 * Balance.AREA_REWARD_SCALE[1]).toLong(), wolf.exp)
        assertEquals((5 * Balance.AREA_REWARD_SCALE[1]).toLong(), wolf.gold)
        // 鉄の剣(基礎: 攻撃 3・価格 50)
        val iron = GameData.item(ItemId.IRON_SWORD)
        assertEquals((3 * Balance.AREA_GEAR_SCALE[1]).toLong(), iron.stats.atk)
        assertEquals((50 * Balance.AREA_PRICE_SCALE[1]).toLong(), iron.price)
        // 草原は倍率 1.0 なので基礎値のまま
        assertEquals(3L, GameData.item(ItemId.WOODEN_SWORD).stats.atk)
        assertEquals(50L, GameData.item(ItemId.WOODEN_SWORD).price)
    }

    /** ドロップ率は 5〜12% の範囲(ドロップ限定品を平均 1 個以上手に入れられる水準)であること。 */
    @Test
    fun ドロップ率の範囲() {
        for (e in GameData.enemies.values) {
            for (d in e.drops) assertTrue(d.chance in 0.05..0.12, "${e.name} の ${d.item} のドロップ率 ${d.chance} が 5〜12% の範囲外")
        }
    }
}
