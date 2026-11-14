// =============================================================================
// Platform.kt
// 役割: ブラウザ固有の実装(時計・localStorage)を 1 か所に集める。
//       domain は「Clock」「SaveRepository」という抽象にだけ依存し、実体はここで差し込む。
//
// 設計判断:
//   - localStorage の操作は失敗しうる(プライベートモード・容量超過・ブロック設定)ため、
//     例外はここでは握りつぶさず呼び出し側(GameController)に投げ、画面のバナーで知らせる。
//   - テストで差し替えられるよう、GameController は GameStorage インターフェースにだけ依存する。
// =============================================================================
package io.github.jacoloves.ffa.ui

import io.github.jacoloves.ffa.domain.Clock
import io.github.jacoloves.ffa.domain.GameState
import io.github.jacoloves.ffa.domain.save.LoadResult
import io.github.jacoloves.ffa.domain.save.SaveCodec
import io.github.jacoloves.ffa.domain.save.SaveRepository
import kotlinx.browser.localStorage

/** ブラウザの現在時刻(Date.now())を返す Clock。 */
object SystemClock : Clock {
    override fun nowMillis(): Long = kotlin.js.Date.now().toLong()
}

/**
 * セーブ先の抽象。domain の [SaveRepository] に、UI が必要とする「壊れたデータの退避」「削除」を足したもの。
 * 失敗時は例外を投げてよい(呼び出し側が捕捉してバナーに出す)。
 */
interface GameStorage : SaveRepository {
    /** 壊れたセーブの生データを、上書きされても残るよう別キーに退避する。 */
    fun backupCorrupted(raw: String)

    /** セーブデータを削除する(退避済みのデータは消さない)。 */
    fun clear()
}

/**
 * localStorage を使った [GameStorage]。
 *
 * @param key セーブデータを置くキー
 * @param clock 退避キーに付けるエポック秒の取得に使う
 */
class LocalStorageGameStorage(
    private val key: String = SAVE_KEY,
    private val clock: Clock = SystemClock,
) : GameStorage {

    /** 保存済みデータを読む。無ければ NotFound、壊れていれば Corrupted(理由と生データ付き)。 */
    override fun load(): LoadResult {
        val raw = localStorage.getItem(key) ?: return LoadResult.NotFound
        return SaveCodec.decode(raw)
    }

    /** 状態を JSON にして保存する。 */
    override fun save(state: GameState) {
        localStorage.setItem(key, SaveCodec.encode(state))
    }

    /** 生データを `<key>.corrupted-<エポックミリ秒>` に退避する。 */
    override fun backupCorrupted(raw: String) {
        localStorage.setItem("$key.corrupted-${clock.nowMillis()}", raw)
    }

    /** セーブデータ本体を削除する。 */
    override fun clear() {
        localStorage.removeItem(key)
    }

    companion object {
        /** セーブデータのキー。 */
        const val SAVE_KEY = "ffa-kt/save"
    }
}
