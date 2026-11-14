// =============================================================================
// ShopTest.kt
// 役割: ショップでの購入(ゴールド・エリア解放・非売品)の仕様を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShopTest {

    private val ironPrice = GameData.item(ItemId.IRON_SWORD).price!!
    private val forestOpen = mapOf(AreaId.MEADOW to 10) // 草原で DLv10 → 森が解放済み

    /** 買えるとゴールドが減り、アイテムが +0 で手に入る。 */
    @Test
    fun 購入に成功する() {
        val r = Shop.buy(ItemId.IRON_SWORD, gold = ironPrice + 5, Inventory.initial(), forestOpen)
        assertIs<GameResult.Ok<PurchaseResult>>(r)
        assertEquals(5L, r.value.gold)
        assertEquals(0, r.value.inventory.owned[ItemId.IRON_SWORD])
        assertEquals(ItemGain(ItemId.IRON_SWORD, 0, true), r.value.gain)
    }

    /** ちょうどの所持金で買える(境界値)。 */
    @Test
    fun ちょうどの所持金で買える() {
        val r = Shop.buy(ItemId.IRON_SWORD, ironPrice, Inventory.initial(), forestOpen)
        assertEquals(0L, (r as GameResult.Ok<PurchaseResult>).value.gold)
    }

    /** 1 ゴールド足りないと NotEnoughGold(必要額と所持額つき)。 */
    @Test
    fun ゴールド不足() {
        val r = Shop.buy(ItemId.IRON_SWORD, ironPrice - 1, Inventory.initial(), forestOpen)
        assertIs<GameResult.Err>(r)
        assertEquals(GameError.NotEnoughGold(need = ironPrice, have = ironPrice - 1), r.error)
        assertTrue(r.error.message.contains("ゴールド"))
    }

    /** 未解放エリアの商品は AreaLocked(ゴールドが足りていても)。 */
    @Test
    fun 未解放エリアの商品は買えない() {
        val r = Shop.buy(ItemId.IRON_SWORD, Long.MAX_VALUE, Inventory.initial(), emptyMap())
        assertIs<GameResult.Err>(r)
        val e = r.error
        assertIs<GameError.AreaLocked>(e)
        assertEquals(AreaId.FOREST, e.area)
        assertTrue(e.message.contains("迷いの森") && e.message.contains("はじまりの草原"))
        // DLv 9 ではまだ足りない
        assertIs<GameResult.Err>(Shop.buy(ItemId.IRON_SWORD, Long.MAX_VALUE, Inventory.initial(), mapOf(AreaId.MEADOW to 9)))
    }

    /** 草原の商品は最初から買える。 */
    @Test
    fun 草原の商品は最初から買える() {
        assertIs<GameResult.Ok<PurchaseResult>>(Shop.buy(ItemId.LEATHER_ARMOR, 1_000, Inventory.initial(), emptyMap()))
    }

    /** ドロップ限定品は売っていない。 */
    @Test
    fun 非売品は買えない() {
        val r = Shop.buy(ItemId.FANG_DAGGER, Long.MAX_VALUE, Inventory.initial(), forestOpen)
        assertIs<GameResult.Err>(r)
        assertEquals(GameError.ItemNotForSale(ItemId.FANG_DAGGER), r.error)
    }

    /** 同じ商品を買い足すと強化値が +1 される。 */
    @Test
    fun 買い足すと強化される() {
        val first = (Shop.buy(ItemId.LEATHER_ARMOR, 1_000, Inventory.initial(), emptyMap()) as GameResult.Ok<PurchaseResult>).value
        val second = (Shop.buy(ItemId.LEATHER_ARMOR, first.gold, first.inventory, emptyMap()) as GameResult.Ok<PurchaseResult>).value
        assertEquals(1, second.inventory.owned[ItemId.LEATHER_ARMOR])
        assertEquals(false, second.gain.isNew)
    }
}
