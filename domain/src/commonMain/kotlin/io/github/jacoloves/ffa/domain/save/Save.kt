// =============================================================================
// Save.kt
// 役割: セーブデータの保存・読み込みの抽象(SaveRepository)と、JSON との相互変換(SaveCodec)。
//
// 設計判断:
//   - domain は「文字列 ⇔ GameState」の変換だけを担当し、実際の保存先(localStorage)は ui 側が
//     SaveRepository を実装して差し込む。domain がブラウザに依存しないようにするため。
//   - decode は失敗を例外で投げず LoadResult.Corrupted で返す。壊れたセーブでアプリが落ちず、
//     元の文字列(raw)を残して利用者が救出・報告できるようにするため。
//   - 読み込みの流れ: JSON として解釈 → schemaVersion だけ先に読む → migrate → GameState に変換 → 妥当性確認。
//     先に schemaVersion だけを見るのは、将来スキーマが変わっても古いデータを変換してから読めるようにするため。
// =============================================================================
package io.github.jacoloves.ffa.domain.save

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.GameState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** 読み込みの結果。 */
sealed interface LoadResult {
    /** 正常に読み込めた。 */
    data class Loaded(val state: GameState) : LoadResult

    /** セーブデータが存在しない(初回起動)。 */
    data object NotFound : LoadResult

    /** データが壊れている / 読めない。[reason] は日本語の説明、[raw] は元の文字列。 */
    data class Corrupted(val reason: String, val raw: String) : LoadResult
}

/** セーブデータの保存先の抽象。ui 側が localStorage などで実装する。 */
interface SaveRepository {
    /** 保存済みデータを読み込む。 */
    fun load(): LoadResult

    /** 状態を保存する。 */
    fun save(state: GameState)
}

/** GameState と JSON 文字列の相互変換。 */
object SaveCodec {

    /** 現在のセーブ形式のバージョン。形式を変えるときに上げ、[migrate] に変換処理を足す。 */
    const val CURRENT_SCHEMA_VERSION = 1

    // ignoreUnknownKeys: 将来項目が増えたセーブも読める / encodeDefaults: 既定値の項目も保存して中身を明示する
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 状態を JSON 文字列にする。 */
    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** JSON 文字列を読み込む。壊れていれば [LoadResult.Corrupted](例外は投げない)。 */
    fun decode(raw: String): LoadResult {
        // 1. JSON として解釈できるか
        val element = try {
            json.parseToJsonElement(raw)
        } catch (e: Exception) {
            return corrupted("セーブデータが JSON として読めません(途中で切れているか、別の内容が書き込まれた可能性があります)。詳細: ${e.message}", raw)
        }
        val obj = element as? JsonObject
            ?: return corrupted("セーブデータの形式が正しくありません。JSON のオブジェクト({ ... })である必要があります。", raw)

        // 2. schemaVersion を先に読む
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return corrupted("セーブデータに schemaVersion(整数)がありません。バージョンが分からないため読み込めません。", raw)
        if (version < 1 || version > CURRENT_SCHEMA_VERSION) {
            return corrupted(
                "セーブデータのバージョン($version)に対応していません。このアプリが読めるのは 1〜$CURRENT_SCHEMA_VERSION です。" +
                    if (version > CURRENT_SCHEMA_VERSION) "より新しいバージョンのアプリで作られた可能性があります。アプリを更新してください。" else "データが壊れています。",
                raw,
            )
        }

        // 3. 現行形式へ変換してから GameState にデコード
        val state = try {
            json.decodeFromJsonElement(GameState.serializer(), migrate(version, obj))
        } catch (e: Exception) {
            return corrupted("セーブデータの内容を読み取れません(項目の不足・型の違い・未知の値がある可能性があります)。詳細: ${e.message}", raw)
        }

        // 4. 形式は正しくても意味が不正な値(ゲームを壊す値)を弾く
        validate(state)?.let { return corrupted(it, raw) }
        return LoadResult.Loaded(state)
    }

    /**
     * 古いバージョンのセーブを現行形式へ変換する。現在は v1 のみなので恒等変換(将来のための枠組み)。
     * 形式を変えるときは `when (fromVersion)` で段階的に(v1→v2→…)変換する。
     */
    fun migrate(fromVersion: Int, obj: JsonObject): JsonObject = obj

    /** 意味的な妥当性の確認。問題なければ null、あれば日本語の理由。 */
    private fun validate(s: GameState): String? = when {
        s.charLevel < 1 -> "キャラレベルが ${s.charLevel} です。1 以上である必要があります。"
        s.playerName.isBlank() -> "プレイヤーの名前が空です。"
        s.cooldownSec !in Balance.MIN_COOLDOWN_SEC..Balance.MAX_COOLDOWN_SEC ->
            "クールタイムが ${s.cooldownSec} 秒です。${Balance.MIN_COOLDOWN_SEC}〜${Balance.MAX_COOLDOWN_SEC} 秒である必要があります。"
        else -> null
    }

    private fun corrupted(reason: String, raw: String) = LoadResult.Corrupted(reason, raw)
}
