// =============================================================================
// Inventory.kt
// 役割: 所持品(owned)と装備(equipped)を表す不変の値と、その操作(入手・装備・装備合計)。
//
// 設計判断:
//   - owned は「アイテム → 強化値」の Map。持っている = キーが存在する。強化値は +0 から始まる。
//   - 同じ装備をもう一度入手(ドロップ・購入)すると強化値が +1 される。重複して持つことはない。
//   - 失敗しうる操作(装備)は GameResult で返し、成功時は新しい Inventory を返す(元は変更しない)。
//   - GameState(Phase 6)は owned / equipped をそのまま持ち、この型に詰め替えて呼ぶ。
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.Slot

/** アイテム入手の結果情報(「新規か」と入手後の強化値)。UI のログ・イベントに使う。 */
data class ItemGain(val item: ItemId, val enhance: Int, val isNew: Boolean)

/** [Inventory.gain] の結果。新しい所持品と、何が起きたか。 */
data class GainResult(val inventory: Inventory, val gain: ItemGain)

/**
 * 所持品と装備。
 *
 * @property owned 所持アイテムと強化値(+N)
 * @property equipped 枠ごとの装備中アイテム(装備していない枠はキー無し)
 */
data class Inventory(
    val owned: Map<ItemId, Int> = emptyMap(),
    val equipped: Map<Slot, ItemId> = emptyMap(),
) {
    /**
     * アイテムを 1 つ入手する。持っていなければ +0 で追加(isNew = true)、
     * 持っていれば強化値を +1(isNew = false)。装備は自動では変わらない。
     */
    fun gain(item: ItemId): GainResult {
        val current = owned[item]
        val isNew = current == null
        val enhance = if (current == null) 0 else current + 1
        return GainResult(copy(owned = owned + (item to enhance)), ItemGain(item, enhance, isNew))
    }

    /**
     * アイテムを装備する(その枠の装備が入れ替わる)。持っていなければ [GameError.ItemNotOwned]。
     * すでに装備中のものを指定しても成功する(冪等)。
     */
    fun equip(item: ItemId): GameResult<Inventory> {
        if (item !in owned) return GameResult.Err(GameError.ItemNotOwned(item))
        val slot = GameData.item(item).slot
        return GameResult.Ok(copy(equipped = equipped + (slot to item)))
    }

    /** 装備中のアイテムの(強化値込みの)ステータス合計。未装備の所持品は含めない。 */
    fun equipmentStats(): Stats =
        equipped.values.fold(Stats.ZERO) { acc, id ->
            // 装備中なのに owned に無い状態は通常ありえないが、壊れたセーブでも落ちないよう +0 として扱う。
            acc + GameData.item(id).statsAt(owned[id] ?: 0)
        }

    companion object {
        /** 新規開始時の所持品: 木の剣 +0 を持ち、装備している。 */
        fun initial(): Inventory = Inventory(
            owned = mapOf(ItemId.WOODEN_SWORD to 0),
            equipped = mapOf(Slot.WEAPON to ItemId.WOODEN_SWORD),
        )
    }
}
