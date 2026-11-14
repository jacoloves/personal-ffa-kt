// =============================================================================
// BalanceSimulationTest.kt
// 役割: 自動プレイヤー(BalanceSimulator)を複数 seed で回し、ゲームの「目標カーブ」が保たれているか検証する。
//       Balance / GameData の数値を変えたとき、体験(何戦でエリアが開くか・勝てるか・装備を買えるか)が
//       壊れていないことを、机上ではなく実測で守るための安全網。
//
// 判定の方針:
//   - seed は固定(1〜8)なので結果は毎回同じ。ただし数値を調整すれば結果は動くため、
//     目標は「幅」(許容範囲)で持ち、中央値・平均で判定する。
//   - 実行時間はおよそ数秒(8 seed × 約 3,900 戦 + 勝率の測定。火山突入後 1,500 戦まで回す)。
//   - 目標値と実測値は describe/domain-design.md の「バランス調整結果」にも書いてある。
// =============================================================================
package io.github.jacoloves.ffa.domain.balance

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BalanceSimulationTest {

    companion object {
        /** シミュレーション結果(全テストで共有。最初に使われたときに 1 度だけ計算する)。 */
        val reports: List<SimReport> by lazy {
            (1..8).map { BalanceSimulator(it).run(maxFights = 6_000, stopAfterEnteringVolcano = 1_500, probeTrials = 300) }
        }
    }

    private fun median(values: List<Int>, what: String): Int =
        assertNotNull(values.medianOrNull(), "$what を達成した seed がありません(シミュレーションが目標に届いていない)")

    /** 目標 1: 作りたてのキャラ(Lv1 の戦士・木の剣)が、草原 DLv1 の最初の 1 戦に 90% 以上の確率で勝つ。 */
    @Test
    fun 最初の戦闘に九割以上勝てる() {
        val trials = 2_000
        val wins = (1..trials).count { BalanceSimulator.firstFightWon(it) }
        val rate = wins.toDouble() / trials
        assertTrue(rate >= 0.90, "最初の戦闘の勝率が ${fmt1(rate * 100)}% です(目標 90% 以上)。草原の敵(特に野犬)の基礎値を下げてください。")
    }

    /** 目標 2: 森の解放(草原 DLv10 クリア)は 100〜250 戦。 */
    @Test
    fun 森は百から二百五十戦で解放される() {
        val n = median(reports.mapNotNull { it.unlockFight[AreaId.FOREST] }, "森の解放")
        assertTrue(n in 100..250, "森の解放が $n 戦目です(目標 100〜250)。草原の EXP・DLv 倍率を見直してください。")
    }

    /** 目標 3: 洞窟は累計 500〜1200 戦、火山は累計 1500〜4000 戦で解放される。 */
    @Test
    fun 洞窟と火山の解放タイミング() {
        val cave = median(reports.mapNotNull { it.unlockFight[AreaId.CAVE] }, "洞窟の解放")
        val volcano = median(reports.mapNotNull { it.unlockFight[AreaId.VOLCANO] }, "火山の解放")
        assertTrue(cave in 500..1200, "洞窟の解放が $cave 戦目です(目標 500〜1200)。")
        assertTrue(volcano in 1500..4000, "火山の解放が $volcano 戦目です(目標 1500〜4000)。")
    }

    /** 目標 4: 新しいエリアに推奨 Lv で入った直後の DLv1 の勝率が 70% 以上。解放時のキャラLv は推奨 Lv の近く。 */
    @Test
    fun 新エリアに推奨レベルで入って勝てる() {
        for (area in AreaId.entries) {
            val def = GameData.area(area)
            val winRate = assertNotNull(reports.map { it.entryWinRate[area] }.filterNotNull().meanOrNull(), "${def.name} に入った seed がありません")
            assertTrue(winRate >= 0.70, "${def.name} の突入直後の DLv1 勝率が ${fmt1(winRate * 100)}% です(目標 70% 以上)。敵の倍率を下げるか推奨Lvを上げてください。")
            if (area == AreaId.MEADOW) continue
            val level = assertNotNull(reports.mapNotNull { it.unlockLevel[area] }.medianOrNull(), "${def.name} の解放時Lv")
            // 解放時のキャラLv が推奨Lv の 0.7〜1.5 倍に収まる(推奨Lv がシミュレーションの実態と乖離していない)
            assertTrue(level * 10 in def.recommendedLevel * 7..def.recommendedLevel * 15,
                "${def.name} の解放時のキャラLv=$level が、推奨Lv=${def.recommendedLevel} から離れすぎています。推奨Lvを実態に合わせてください。")
        }
    }

    /** 目標 4b: 洞窟に入った直後の DLv1 勝率は 75〜92%(簡単すぎず、勝てないほど難しくもない)。 */
    @Test
    fun 洞窟の突入直後の勝率が適度() {
        val rate = assertNotNull(reports.mapNotNull { it.entryWinRate[AreaId.CAVE] }.meanOrNull(), "洞窟に入った seed がありません")
        assertTrue(rate in 0.75..0.92, "洞窟の突入直後の DLv1 勝率が ${fmt1(rate * 100)}% です(目標 75〜92%)。洞窟の敵倍率(AREA_STAT_SCALE[2])を調整してください。")
    }

    /**
     * 目標 8(エンドレス区間): 火山へ入ったあとの 1,500 戦、成長が止まらない。
     *   - 100 戦あたりのキャラLv増加が、どの窓でも(seed の中央値で)5 以上
     *   - どの窓も、最初の窓の半分未満には落ちない(プラトーがない)
     *   - 300 戦ごとに、火山の最高 DLv が(seed の中央値で)1 以上上がり続ける
     */
    @Test
    fun 火山突入後の成長が止まらない() {
        val all = reports.map { it.endlessWindows() }
        val windows = all.minOf { it.size }
        assertTrue(windows >= 14, "火山突入後の計測が $windows 窓(100 戦ずつ)しかありません(目標 14 窓以上)。シミュレーションの長さを確認してください。")
        val levelGains = (0 until windows).map { k -> all.map { it[k].levelGain }.medianOrNull()!! }
        for ((k, g) in levelGains.withIndex()) {
            assertTrue(g >= 5, "火山突入後 ${k * 100 + 1}〜${(k + 1) * 100} 戦の Lv増加が $g です(目標 100 戦あたり 5 以上)。火山の報酬倍率や DLv の伸びを見直してください。")
            assertTrue(g * 2 >= levelGains.first(), "火山突入後 ${k * 100 + 1}〜${(k + 1) * 100} 戦の Lv増加 $g が、最初の窓(${levelGains.first()})の半分未満です(成長が失速しています)。")
        }
        for (block in 0 until windows / 3) {
            val gains = all.map { w -> (0 until 3).sumOf { w[block * 3 + it].dlvGain } }
            val m = gains.medianOrNull()!!
            assertTrue(m >= 1, "火山突入後 ${block * 300 + 1}〜${(block + 1) * 300} 戦で、火山の最高 DLv が上がっていません(中央値 $m)。DLV_REWARD_GROWTH と DLV_ENEMY_GROWTH の差を見直してください。")
        }
    }

    /** 目標 5: Tier1 の 4 職は、火山の解放までにすべてマスターできる(上位職も含めて報告する)。 */
    @Test
    fun 下位職は火山解放までにすべてマスターできる() {
        val volcano = median(reports.mapNotNull { it.unlockFight[AreaId.VOLCANO] }, "火山の解放")
        for (job in listOf(JobId.WARRIOR, JobId.KNIGHT, JobId.THIEF, JobId.PRIEST)) {
            val n = median(reports.mapNotNull { it.masteredFight[job] }, "${GameData.job(job).name} のマスター")
            assertTrue(n <= volcano, "${GameData.job(job).name} のマスターが $n 戦目で、火山の解放($volcano 戦目)に間に合っていません。")
        }
    }

    /** 目標 6: ドロップ限定品は、各エリアの滞在中に平均 1 個以上手に入る。 */
    @Test
    fun ドロップ限定品が各エリアで平均一個以上手に入る() {
        for (area in AreaId.entries) {
            val avg = reports.map { (it.dropOnlyByArea[area] ?: 0).toDouble() }.meanOrNull()!!
            assertTrue(avg >= 1.0, "${GameData.area(area).name} のドロップ限定品が平均 ${fmt1(avg)} 個です(目標 1 個以上)。ドロップ率を上げてください。")
        }
    }

    /** 目標 7: 各エリアのショップ装備は、そのエリアでの稼ぎだけで(累計)150 戦以内に買える額に収まっている。 */
    @Test
    fun ショップ装備は妥当な戦数で買える() {
        for (item in ItemId.entries) {
            val def = GameData.item(item)
            if (def.price == null || item == ItemId.WOODEN_SWORD) continue // 木の剣は最初から持っている
            val n = median(reports.mapNotNull { it.fightsToEarnPrice[item] }, "${def.name} の価格(${def.price}G)を稼ぎ切ること")
            assertTrue(n <= 150, "${def.name}(${def.price}G)を稼ぎ切るのに $n 戦かかります(目標 150 戦以内)。価格倍率を下げるか、ゴールド報酬を上げてください。")
        }
    }

    /** 同じ seed なら結果が完全に同じ(決定的)こと。シミュレーションが再現可能であることの確認。 */
    @Test
    fun 同じseedなら同じ結果() {
        val a = BalanceSimulator(3).run(maxFights = 800, probeTrials = 20)
        val b = BalanceSimulator(3).run(maxFights = 800, probeTrials = 20)
        assertEquals(a.finalLevel, b.finalLevel)
        assertEquals(a.finalGold, b.finalGold)
        assertEquals(a.unlockFight, b.unlockFight)
    }

    /** 設計書に貼る結果表を標準出力に出す(判定はしない。`./gradlew :domain:jsNodeTest -i` で見られる)。 */
    @Test
    fun 結果表を出力する() {
        println("バランス調整結果(seed 1〜8、中央値/平均)\n" + BalanceReport.areaTable(reports) + "\n" + BalanceReport.jobAndShopTable(reports) + "\n" + BalanceReport.endlessTable(reports))
    }
}
