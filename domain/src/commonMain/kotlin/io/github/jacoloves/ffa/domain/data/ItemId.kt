// =============================================================================
// ItemId.kt
// 役割: アイテム(装備)の識別子。各エリアに「ショップ商品 3 枠分 + ドロップ限定品 1 つ」を用意する。
//       セーブデータには enum の名前で保存する(名前の変更はセーブ互換性を壊すので注意)。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

/** アイテムの識別子。 */
enum class ItemId {
    // 草原
    WOODEN_SWORD, LEATHER_ARMOR, LUCKY_CHARM, FANG_DAGGER,
    // 森
    IRON_SWORD, CHAIN_MAIL, WIND_RING, ELDER_WOOD_STAFF,
    // 洞窟
    STEEL_SWORD, MITHRIL_ARMOR, GEM_PENDANT, OGRE_CLUB,
    // 火山
    FLAME_SWORD, DRAGON_SCALE_ARMOR, PHOENIX_FEATHER, DRAGON_FANG,
}
