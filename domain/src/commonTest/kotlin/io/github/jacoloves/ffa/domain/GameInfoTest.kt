// =============================================================================
// GameInfoTest.kt
// 役割: GameInfo の振る舞いを kotlin.test で検証する(TDD: 仕様をテストで先に表現)。
//       commonTest に置くことで、実行ターゲット(今は Node.js)に依存しない。
// =============================================================================
package io.github.jacoloves.ffa.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GameInfoTest {

    /** タイトルが空でなく、期待値どおりであること。 */
    @Test
    fun タイトルが定義されている() {
        assertEquals("Personal FFA", GameInfo.TITLE)
    }

    /** 名前を渡すと、その名前を含む挨拶が返ること。 */
    @Test
    fun 名前付きの挨拶を返す() {
        assertEquals("ようこそ、太郎さん！", GameInfo.greeting("太郎"))
    }

    /** 空白のみの名前はゲスト扱いになること(境界値)。 */
    @Test
    fun 空白の名前はゲスト扱い() {
        assertEquals("ようこそ、ゲストさん！", GameInfo.greeting("   "))
    }
}
