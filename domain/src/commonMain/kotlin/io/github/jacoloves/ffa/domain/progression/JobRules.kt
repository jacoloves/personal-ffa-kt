// =============================================================================
// JobRules.kt
// 役割: 職業の解放条件と転職の可否を判定する。
//
// ルール:
//   - Tier1 は最初から解放済み(requires が空)。
//   - Tier2 は requires の職業がすべてマスター済みで解放される。
//   - 転職は無料でいつでも可能。ただし同じ職業へは不可、未解放の職業へも不可。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress

/** 職業の解放・転職ルール。 */
object JobRules {

    /** 新規開始時の職業進行状況(全職業 Lv1・未マスター)。 */
    fun initialJobs(): Map<JobId, JobProgress> = JobId.entries.associateWith { JobProgress() }

    /** [job] の解放に必要でまだマスターしていない職業の集合(空なら解放済み)。 */
    fun missingRequirements(job: JobId, jobs: Map<JobId, JobProgress>): Set<JobId> =
        GameData.job(job).requires.filterTo(mutableSetOf()) { jobs[it]?.mastered != true }

    /** 解放済みの職業の集合。 */
    fun unlockedJobs(jobs: Map<JobId, JobProgress>): Set<JobId> =
        JobId.entries.filterTo(mutableSetOf()) { missingRequirements(it, jobs).isEmpty() }

    /** [before] から [after] の間で新しく解放された職業(JobUnlocked イベント用)。 */
    fun newlyUnlocked(before: Map<JobId, JobProgress>, after: Map<JobId, JobProgress>): Set<JobId> =
        unlockedJobs(after) - unlockedJobs(before)

    /**
     * 転職の検証。問題なければ null、問題があればその理由を返す。
     * 同じ職業のチェックを先に行う(「すでにその職業」の方が分かりやすいため)。
     */
    fun validateChange(current: JobId, target: JobId, jobs: Map<JobId, JobProgress>): GameError? {
        if (current == target) return GameError.AlreadyCurrentJob
        val missing = missingRequirements(target, jobs)
        if (missing.isNotEmpty()) return GameError.JobLocked(target, missing)
        return null
    }
}
