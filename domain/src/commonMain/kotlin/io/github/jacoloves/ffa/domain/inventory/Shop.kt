// =============================================================================
// Shop.kt
// 役割: ショップでの購入処理。検証 → ゴールド減算 → アイテム入手(新規 +0 / 重複 +1)。
//
// 検証の順序(先に当てはまったエラーを返す):
//   1. 非売品(price が null)  → ItemNotForSale
//   2. エリア未解放            → AreaLocked(ゴールドが足りていても買えない)
//   3. ゴールド不足            → NotEnoughGold
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.world.AreaRules

/** 購入成功時の結果。[gold] は購入後の所持金。 */
data class PurchaseResult(val gold: Long, val inventory: Inventory, val gain: ItemGain)

/** ショップ。状態を持たない。 */
object Shop {

    /**
     * [item] を購入する。
     *
     * @param gold 現在の所持金
     * @param maxClearedDlv エリアごとのクリア済み最高 DLv(エリア解放の判定に使う)
     */
    fun buy(
        item: ItemId,
        gold: Long,
        inventory: Inventory,
        maxClearedDlv: Map<AreaId, Int>,
    ): GameResult<PurchaseResult> {
        val def = GameData.item(item)
        val price = def.price
        val area = def.shopArea
        if (price == null || area == null) return GameResult.Err(GameError.ItemNotForSale(item))
        if (!AreaRules.isUnlocked(area, maxClearedDlv)) {
            // 解放済みでないなら unlock は必ず非 null(null なら常に解放済みのため)
            val requirement = checkNotNull(GameData.area(area).unlock)
            return GameResult.Err(GameError.AreaLocked(area, requirement))
        }
        if (gold < price) return GameResult.Err(GameError.NotEnoughGold(need = price, have = gold))
        val result = inventory.gain(item)
        return GameResult.Ok(PurchaseResult(gold - price, result.inventory, result.gain))
    }
}
