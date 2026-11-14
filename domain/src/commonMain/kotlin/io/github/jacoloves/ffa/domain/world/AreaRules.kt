// =============================================================================
// AreaRules.kt
// 役割: エリアの解放判定。「エリア X は、エリア Y で DLv N をクリアしたら解放」という条件を評価する。
//
// 入力の maxClearedDlv は「エリアごとの、クリア(勝利)した最高 DLv」。無ければ未クリア(0 扱い)。
// =============================================================================
package io.github.jacoloves.ffa.domain.world

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData

/** エリアの解放ルール。 */
object AreaRules {

    /** [area] が解放済みか。解放条件が無ければ常に true。 */
    fun isUnlocked(area: AreaId, maxClearedDlv: Map<AreaId, Int>): Boolean {
        val unlock = GameData.area(area).unlock ?: return true
        return (maxClearedDlv[unlock.area] ?: 0) >= unlock.dlv
    }

    /** 解放済みのエリアの集合。 */
    fun unlockedAreas(maxClearedDlv: Map<AreaId, Int>): Set<AreaId> =
        AreaId.entries.filterTo(mutableSetOf()) { isUnlocked(it, maxClearedDlv) }

    /** [before] から [after] の間で新しく解放されたエリア(AreaUnlocked イベント用)。 */
    fun newlyUnlocked(before: Map<AreaId, Int>, after: Map<AreaId, Int>): Set<AreaId> =
        unlockedAreas(after) - unlockedAreas(before)
}
