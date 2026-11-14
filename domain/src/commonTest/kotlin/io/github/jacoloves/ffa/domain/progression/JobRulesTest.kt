// =============================================================================
// JobRulesTest.kt
// 役割: 職業の解放条件・転職の検証・職業テーブルの整合性を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.GameError
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobRulesTest {

    private val fresh = JobRules.initialJobs()

    private fun mastered(vararg ids: JobId) =
        fresh + ids.associateWith { JobProgress(level = 30, mastered = true) }

    /** すべての JobId に定義があり、Tier1 が 4 職、Tier2 が 2 職。 */
    @Test
    fun 職業テーブルの整合性() {
        for (id in JobId.entries) assertEquals(id, GameData.job(id).id)
        assertEquals(6, GameData.jobs.size)
        assertEquals(4, GameData.jobs.values.count { it.tier == 1 })
        assertEquals(2, GameData.jobs.values.count { it.tier == 2 })
        // Tier1 は条件なし、Tier2 は Tier1 のみを要求する
        for (j in GameData.jobs.values) {
            if (j.tier == 1) assertTrue(j.requires.isEmpty())
            else assertTrue(j.requires.isNotEmpty() && j.requires.all { GameData.job(it).tier == 1 })
        }
    }

    /** 設計書の職業値(剣聖と聖騎士を抜粋)。 */
    @Test
    fun 職業の値が設計書どおり() {
        val sm = GameData.job(JobId.SWORD_MASTER)
        assertEquals("剣聖", sm.name)
        assertEquals(setOf(JobId.WARRIOR, JobId.THIEF), sm.requires)
        assertEquals(0.10, sm.masterBonus.atk, 1e-9)
        assertEquals(0.05, sm.masterBonus.spd, 1e-9)
        val p = GameData.job(JobId.PALADIN)
        assertEquals(setOf(JobId.KNIGHT, JobId.PRIEST), p.requires)
        assertEquals(50L, p.base.hp)
    }

    /** 初期状態: 全職業が Lv1・未マスターで、Tier1 だけが解放済み。 */
    @Test
    fun 初期状態() {
        assertEquals(JobId.entries.toSet(), fresh.keys)
        assertTrue(fresh.values.all { it == JobProgress() })
        assertEquals(
            setOf(JobId.WARRIOR, JobId.KNIGHT, JobId.THIEF, JobId.PRIEST),
            JobRules.unlockedJobs(fresh),
        )
    }

    /** Tier2 は required 職がすべてマスター済みで解放される。片方だけでは解放されない。 */
    @Test
    fun Tier2の解放() {
        assertTrue(JobId.SWORD_MASTER !in JobRules.unlockedJobs(mastered(JobId.WARRIOR)))
        assertTrue(JobId.SWORD_MASTER in JobRules.unlockedJobs(mastered(JobId.WARRIOR, JobId.THIEF)))
        assertTrue(JobId.PALADIN !in JobRules.unlockedJobs(mastered(JobId.WARRIOR, JobId.THIEF)))
        assertTrue(JobId.PALADIN in JobRules.unlockedJobs(mastered(JobId.KNIGHT, JobId.PRIEST)))
    }

    /** 新しく解放された職業だけが返る。 */
    @Test
    fun 新規解放の検出() {
        val before = mastered(JobId.WARRIOR)
        val after = mastered(JobId.WARRIOR, JobId.THIEF)
        assertEquals(setOf(JobId.SWORD_MASTER), JobRules.newlyUnlocked(before, after))
        assertEquals(emptySet(), JobRules.newlyUnlocked(after, after))
    }

    @Test
    fun 転職は問題なければnull() {
        assertNull(JobRules.validateChange(JobId.WARRIOR, JobId.KNIGHT, fresh))
        assertNull(JobRules.validateChange(JobId.WARRIOR, JobId.SWORD_MASTER, mastered(JobId.WARRIOR, JobId.THIEF)))
    }

    @Test
    fun 同じ職業への転職は拒否() {
        val e = JobRules.validateChange(JobId.WARRIOR, JobId.WARRIOR, fresh)
        assertEquals(GameError.AlreadyCurrentJob, e)
        assertTrue(e!!.message.isNotBlank())
    }

    /** 未解放の職業へは、足りない職業の一覧つきで拒否される。 */
    @Test
    fun 未解放の職業への転職は拒否() {
        val e = JobRules.validateChange(JobId.WARRIOR, JobId.SWORD_MASTER, mastered(JobId.WARRIOR))
        assertIs<GameError.JobLocked>(e)
        assertEquals(JobId.SWORD_MASTER, e.job)
        assertEquals(setOf(JobId.THIEF), e.missing)
        // メッセージは「何が・なぜ・どうすれば」を含む
        assertTrue(e.message.contains("剣聖") && e.message.contains("盗賊") && e.message.contains("マスター"))
    }
}
