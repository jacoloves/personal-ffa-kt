// =============================================================================
// SaveCodecTest.kt
// 役割: セーブデータの JSON 変換(ラウンドトリップ・壊れた入力・バージョン)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.save

import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.GameState
import io.github.jacoloves.ffa.domain.Records
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveCodecTest {

    private fun newState() = (GameState.new("勇者") as GameResult.Ok<GameState>).value

    /** いろいろな値を入れた状態(enum キーの Map・大きな Long を含む)。 */
    private fun busyState() = newState().copy(
        charLevel = 1234, charExp = 99, gold = Long.MAX_VALUE,
        currentJob = JobId.KNIGHT,
        jobs = newState().jobs + (JobId.WARRIOR to JobProgress(level = 31, exp = 3, mastered = true)),
        owned = mapOf(ItemId.WOODEN_SWORD to 3, ItemId.IRON_SWORD to 0),
        currentArea = AreaId.FOREST,
        selectedDlv = mapOf(AreaId.MEADOW to 4, AreaId.FOREST to 2),
        maxClearedDlv = mapOf(AreaId.MEADOW to 12),
        cooldownSec = 9, nextBattleAt = 1_700_000_000_000,
        records = Records(battles = 10, wins = 7, totalGold = 5_000, maxCharLevel = 1234),
    )

    @Test
    fun 新規状態のラウンドトリップ() {
        val s = newState()
        assertEquals(LoadResult.Loaded(s), SaveCodec.decode(SaveCodec.encode(s)))
    }

    @Test
    fun 複雑な状態のラウンドトリップ() {
        val s = busyState()
        assertEquals(LoadResult.Loaded(s), SaveCodec.decode(SaveCodec.encode(s)))
    }

    /** 既定値の項目も、schemaVersion も JSON に含まれる(encodeDefaults)。 */
    @Test
    fun エンコードに既定値とバージョンが含まれる() {
        val json = SaveCodec.encode(newState())
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"cooldownSec\""))
        assertTrue(json.contains("\"WOODEN_SWORD\"")) // enum は名前で保存される
    }

    /** 未知のキーは無視して読める(将来のバージョンで項目が増えても壊れない)。 */
    @Test
    fun 未知のキーは無視() {
        val extra = SaveCodec.encode(newState()).trimEnd().removeSuffix("}") + ",\"futureField\":123}"
        assertEquals(LoadResult.Loaded(newState()), SaveCodec.decode(extra))
    }

    // ---------- 壊れた入力 ----------

    private fun assertCorrupted(raw: String, vararg reasonParts: String): LoadResult.Corrupted {
        val r = SaveCodec.decode(raw)
        assertIs<LoadResult.Corrupted>(r, "raw=$raw")
        assertEquals(raw, r.raw) // 元データは調査・救出のためそのまま保持する
        for (part in reasonParts) assertTrue(r.reason.contains(part), "reason に「$part」が無い: ${r.reason}")
        return r
    }

    @Test
    fun JSONではない文字列() {
        assertCorrupted("これはJSONではない", "JSON")
        assertCorrupted("", "JSON")
        assertCorrupted("{\"schemaVersion\": 1", "JSON") // 途中で切れている
    }

    @Test
    fun オブジェクトではないJSON() {
        assertCorrupted("[1,2,3]", "オブジェクト")
        assertCorrupted("123", "オブジェクト")
    }

    @Test
    fun schemaVersionがない() {
        assertCorrupted("{\"playerName\":\"a\"}", "schemaVersion")
        assertCorrupted("{\"schemaVersion\":\"one\"}", "schemaVersion")
    }

    /** 新しすぎるバージョンは読めない(古いアプリで新しいセーブを壊さないため)。 */
    @Test
    fun 未来のバージョン() {
        assertCorrupted("{\"schemaVersion\":99}", "99", "新しい")
        assertCorrupted("{\"schemaVersion\":3}", "3", "新しい") // 現行(2)より 1 つ新しいだけでも読まない
        assertCorrupted("{\"schemaVersion\":0}", "0")
    }

    /** バージョンは正しいが項目が足りない / 型が違う / 未知の enum 値。 */
    @Test
    fun 項目の欠落や不正な値() {
        assertCorrupted("{\"schemaVersion\":2}", "読み取れません")
        val good = SaveCodec.encode(newState())
        assertCorrupted(good.replace("\"MEADOW\"", "\"ATLANTIS\""), "読み取れません")
        assertCorrupted(good.replace("\"charLevel\":1", "\"charLevel\":\"x\""), "読み取れません")
    }

    /** 意味的に不正な値(Lv0 など)は、形式が正しくても Corrupted。 */
    @Test
    fun 意味的に不正な値() {
        val raw = SaveCodec.encode(newState().copy(charLevel = 0))
        assertCorrupted(raw, "キャラレベル")
        assertCorrupted(SaveCodec.encode(newState().copy(cooldownSec = 181)), "クールタイム")
        assertCorrupted(SaveCodec.encode(newState().copy(playerName = " ")), "名前")
    }

    // ---------- migrate ----------

    /** v2 のセーブは migrate で変わらない(現行形式)。 */
    @Test
    fun migrateはv2で恒等() {
        val obj: JsonObject = buildJsonObject { put("schemaVersion", 2); put("cooldownSec", 5) }
        assertEquals(obj, SaveCodec.migrate(2, obj))
        assertEquals(2, SaveCodec.CURRENT_SCHEMA_VERSION)
        assertEquals(SaveCodec.CURRENT_SCHEMA_VERSION, newState().schemaVersion)
    }

    /** 現行セーブを v1 形式(schemaVersion=1, cooldownSec 指定)に書き換えた JSON を作る。 */
    private fun v1Json(cooldownSec: Int): String =
        SaveCodec.encode(newState().copy(cooldownSec = cooldownSec))
            .replace("\"schemaVersion\":2", "\"schemaVersion\":1")

    /** v1→v2: 旧既定の 5 秒は新既定の 90 秒へ。バージョンも 2 になる。 */
    @Test
    fun v1の旧既定クールタイムは90秒に移行される() {
        val loaded = SaveCodec.decode(v1Json(5)) as LoadResult.Loaded
        assertEquals(90, loaded.state.cooldownSec)
        assertEquals(2, loaded.state.schemaVersion)
    }

    /** v1→v2: 利用者が決めた値(12 秒)はそのまま残す。 */
    @Test
    fun v1のカスタムクールタイムは維持される() {
        val loaded = SaveCodec.decode(v1Json(12)) as LoadResult.Loaded
        assertEquals(12, loaded.state.cooldownSec)
        assertEquals(2, loaded.state.schemaVersion)
    }

    /** v1→v2: 範囲外の値は 1〜180 に収める(migrate 単体)。 */
    @Test
    fun migrateは範囲外を収める() {
        val low = buildJsonObject { put("schemaVersion", 1); put("cooldownSec", 0) }
        val high = buildJsonObject { put("schemaVersion", 1); put("cooldownSec", 999) }
        assertEquals(JsonPrimitive(1), SaveCodec.migrate(1, low)["cooldownSec"])
        assertEquals(JsonPrimitive(180), SaveCodec.migrate(1, high)["cooldownSec"])
    }

    // ---------- SaveRepository ----------

    /** SaveRepository の契約: 未保存は NotFound、保存すれば Loaded で返る。 */
    private class InMemoryRepository : SaveRepository {
        private var raw: String? = null
        override fun load(): LoadResult = raw?.let(SaveCodec::decode) ?: LoadResult.NotFound
        override fun save(state: GameState) { raw = SaveCodec.encode(state) }
    }

    @Test
    fun リポジトリの契約() {
        val repo = InMemoryRepository()
        assertEquals(LoadResult.NotFound, repo.load())
        repo.save(busyState())
        assertEquals(LoadResult.Loaded(busyState()), repo.load())
    }
}
