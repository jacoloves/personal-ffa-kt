// =============================================================================
// Ids.kt
// 役割: エリアと敵の識別子(enum)。
//
// なぜ enum か:
//   - 存在しない ID をコードに書くとコンパイルエラーになる(型安全)。
//   - `entries` で全値を列挙でき、「定義が抜けていないか」をテストで機械的に確認できる。
//   - セーブデータには enum の名前(文字列)で保存する(Phase 6)。名前の変更はセーブ互換性を壊すので注意。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

/** エリアの識別子。 */
enum class AreaId { MEADOW, FOREST, CAVE, VOLCANO }

/** 敵の識別子。エリアごとに 5 体(草原 → 森 → 洞窟 → 火山)。 */
enum class EnemyId {
    // 草原
    SLIME, GIANT_RAT, HORNED_RABBIT, GOBLIN, WILD_DOG,
    // 森
    WOOD_WOLF, POISON_MUSHROOM, FOREST_GOBLIN_CHIEF, TREANT, FAIRY_PRANKSTER,
    // 洞窟
    GIANT_BAT, GOLEM, CAVE_LIZARD, SKELETON, CAVE_OGRE,
    // 火山
    FIRE_LIZARD, LAVA_SLIME, FLAME_BEAST, FIRE_ELEMENTAL, YOUNG_DRAGON,
}

/** 職業の識別子。Tier1 の 4 職と、それをマスターして解放される Tier2 の 2 職。 */
enum class JobId { WARRIOR, KNIGHT, THIEF, PRIEST, SWORD_MASTER, PALADIN }

/** 装備枠。 */
enum class Slot { WEAPON, ARMOR, ACCESSORY }
