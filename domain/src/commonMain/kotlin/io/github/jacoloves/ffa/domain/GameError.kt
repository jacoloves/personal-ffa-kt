// =============================================================================
// GameError.kt
// 役割: ゲーム操作が失敗した理由を表す型。UI はこの message をそのまま表示できる。
//
// 設計判断:
//   - 例外ではなく値(sealed interface)で返す。プレイヤーの操作ミスは「想定内の失敗」であり、
//     呼び出し側が when で網羅的に扱えるようにするため。
//   - message は日本語で「何が起きたか・なぜか・どうすればよいか」を含める(利用者向けの案内)。
//   - 各 Phase で必要になった種類をここへ追加していく。
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.AreaUnlock
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.num.formatJp

/** 操作の失敗理由。 */
sealed interface GameError {
    /** プレイヤー向けの日本語メッセージ(何が・なぜ・どうすれば)。 */
    val message: String

    /** 解放条件を満たしていない職業へ転職しようとした。[missing] は未マスターの必要職業。 */
    data class JobLocked(val job: JobId, val missing: Set<JobId>) : GameError {
        override val message: String
            get() {
                val need = missing.joinToString("・") { GameData.job(it).name }
                return "「${GameData.job(job).name}」にはまだ転職できません。解放条件の職業($need)がマスターされていないためです。" +
                    "$need をマスター(職業Lv ${Balance.JOB_MASTER_LEVEL})してからもう一度お試しください。"
            }
    }

    /** 今と同じ職業へ転職しようとした。 */
    data object AlreadyCurrentJob : GameError {
        override val message: String =
            "すでにその職業に就いています。転職先には、今の職業と別の職業を選んでください。"
    }

    /** ゴールドが足りない。 */
    data class NotEnoughGold(val need: Long, val have: Long) : GameError {
        override val message: String
            get() = "ゴールドが足りません(必要 ${formatJp(need)}、所持 ${formatJp(have)})。" +
                "あと ${formatJp(need - have)} ゴールド貯めてから購入してください。敵を倒すとゴールドが手に入ります。"
    }

    /** 未解放のエリアに関わる操作(移動・買い物)をした。[requirement] が解放条件。 */
    data class AreaLocked(val area: AreaId, val requirement: AreaUnlock) : GameError {
        override val message: String
            get() = "「${GameData.area(area).name}」はまだ解放されていません。" +
                "解放には「${GameData.area(requirement.area).name}」で DLv ${requirement.dlv} に到達する必要があります。" +
                "先に「${GameData.area(requirement.area).name}」で戦って DLv を上げてください。"
    }

    /** 持っていないアイテムを装備しようとした。 */
    data class ItemNotOwned(val item: ItemId) : GameError {
        override val message: String
            get() = "「${GameData.item(item).name}」は持っていないため装備できません。" +
                "ショップで購入するか、敵を倒してドロップで入手してください。"
    }

    /** ドロップ限定品など、ショップで売っていないアイテムを買おうとした。 */
    data class ItemNotForSale(val item: ItemId) : GameError {
        override val message: String
            get() = "「${GameData.item(item).name}」はショップでは売っていません(ドロップ限定品です)。" +
                "敵を倒してドロップで入手してください。"
    }
}

/**
 * 「成功なら値、失敗なら [GameError]」を表す結果型。
 * 例外を使わないのは、プレイヤーの操作ミスが想定内の失敗であり、when で網羅的に扱いたいため。
 */
sealed interface GameResult<out T> {
    /** 成功。 */
    data class Ok<T>(val value: T) : GameResult<T>

    /** 失敗。理由は [error]。 */
    data class Err(val error: GameError) : GameResult<Nothing>
}
