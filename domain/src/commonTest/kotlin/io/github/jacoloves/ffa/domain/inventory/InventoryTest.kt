// =============================================================================
// InventoryTest.kt
// 役割: 装備の強化値計算・入手(新規/強化)・装備・初期装備の仕様を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InventoryTest {

    private val sword = GameData.item(ItemId.WOODEN_SWORD) // atk 3
    private val iron = GameData.item(ItemId.IRON_SWORD)    // atk 30

    // ---------- 強化値 ----------

    /** +0 は基礎値そのもの。+N は基礎値の (1 + 0.1N) 倍(切り捨て)。 */
    @Test
    fun 強化値でステータスが増える() {
        assertEquals(iron.stats, iron.statsAt(0))
        assertEquals(30L, iron.statsAt(0).atk)
        assertEquals(33L, iron.statsAt(1).atk)
        assertEquals(39L, iron.statsAt(3).atk)
        assertEquals(60L, iron.statsAt(10).atk)
        // 切り捨て: 3 × 1.1 = 3.3 → 3、3 × 1.3 = 3.9 → 3
        assertEquals(3L, sword.statsAt(1).atk)
        assertEquals(3L, sword.statsAt(3).atk)
        assertEquals(4L, sword.statsAt(4).atk) // 3 × 1.4 = 4.2
    }

    /** 巨大な値でも飽和し、負にならない。 */
    @Test
    fun 強化でも飽和する() {
        val big = iron.copy(stats = Stats(Long.MAX_VALUE, 0, 0, 0, 0))
        assertEquals(Long.MAX_VALUE, big.statsAt(5).hp)
    }

    // ---------- 入手 ----------

    /** 初めて入手したら +0 で追加され、isNew = true。 */
    @Test
    fun 新規入手は強化値0() {
        val r = Inventory().gain(ItemId.IRON_SWORD)
        assertEquals(mapOf(ItemId.IRON_SWORD to 0), r.inventory.owned)
        assertEquals(ItemGain(ItemId.IRON_SWORD, enhance = 0, isNew = true), r.gain)
    }

    /** すでに持っていれば強化値が +1 される。 */
    @Test
    fun 重複入手は強化値が上がる() {
        val inv = Inventory().gain(ItemId.IRON_SWORD).inventory
        val r = inv.gain(ItemId.IRON_SWORD)
        assertEquals(1, r.inventory.owned[ItemId.IRON_SWORD])
        assertEquals(ItemGain(ItemId.IRON_SWORD, enhance = 1, isNew = false), r.gain)
        assertEquals(2, r.inventory.gain(ItemId.IRON_SWORD).gain.enhance)
    }

    /** 入手しても装備は自動では変わらない。 */
    @Test
    fun 入手しても装備は変わらない() {
        val r = Inventory.initial().gain(ItemId.IRON_SWORD)
        assertEquals(ItemId.WOODEN_SWORD, r.inventory.equipped[Slot.WEAPON])
    }

    // ---------- 装備 ----------

    /** 持っていないものは装備できない(ItemNotOwned)。 */
    @Test
    fun 未所持は装備できない() {
        val r = Inventory.initial().equip(ItemId.IRON_SWORD)
        assertIs<GameResult.Err>(r)
        assertEquals(GameError.ItemNotOwned(ItemId.IRON_SWORD), r.error)
        assertTrue(r.error.message.contains("鉄の剣"))
    }

    /** 持っていれば、その枠の装備が入れ替わる。 */
    @Test
    fun 所持していれば装備できる() {
        val inv = Inventory.initial().gain(ItemId.IRON_SWORD).inventory
        val r = inv.equip(ItemId.IRON_SWORD)
        assertIs<GameResult.Ok<Inventory>>(r)
        assertEquals(ItemId.IRON_SWORD, r.value.equipped[Slot.WEAPON])
        // 同じものをもう一度装備しても成功する(冪等)
        assertIs<GameResult.Ok<Inventory>>(r.value.equip(ItemId.IRON_SWORD))
    }

    /** 枠は別々に保持される。 */
    @Test
    fun 枠ごとに装備できる() {
        val inv = Inventory.initial().gain(ItemId.LEATHER_ARMOR).inventory
        val r = inv.equip(ItemId.LEATHER_ARMOR) as GameResult.Ok<Inventory>
        assertEquals(ItemId.WOODEN_SWORD, r.value.equipped[Slot.WEAPON])
        assertEquals(ItemId.LEATHER_ARMOR, r.value.equipped[Slot.ARMOR])
    }

    // ---------- 初期状態・装備合計 ----------

    /** 新規開始時は木の剣 +0 を装備している。 */
    @Test
    fun 初期装備は木の剣() {
        val inv = Inventory.initial()
        assertEquals(mapOf(ItemId.WOODEN_SWORD to 0), inv.owned)
        assertEquals(mapOf(Slot.WEAPON to ItemId.WOODEN_SWORD), inv.equipped)
    }

    /** 装備合計 = 装備中のアイテムの(強化込み)ステータスの合計。未装備の所持品は含めない。 */
    @Test
    fun 装備ステータスの合計() {
        assertEquals(Stats.ZERO, Inventory().equipmentStats())
        var inv = Inventory.initial()
        assertEquals(sword.stats, inv.equipmentStats())
        // 木の剣を +4 にし、革の鎧(未強化)を装備、鉄の剣は持つだけで装備しない
        repeat(4) { inv = inv.gain(ItemId.WOODEN_SWORD).inventory }
        inv = inv.gain(ItemId.LEATHER_ARMOR).inventory.gain(ItemId.IRON_SWORD).inventory
        inv = (inv.equip(ItemId.LEATHER_ARMOR) as GameResult.Ok<Inventory>).value
        val armor = GameData.item(ItemId.LEATHER_ARMOR)
        assertEquals(sword.statsAt(4) + armor.stats, inv.equipmentStats())
    }
}
