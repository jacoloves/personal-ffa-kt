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

import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobId

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
}
