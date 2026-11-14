// =============================================================================
// BalanceReport.kt
// 役割: 複数 seed のシミュレーション結果([SimReport])を集計し、人間が読める表にする。
//       テスト(BalanceSimulationTest)の判定と、設計書「バランス調整結果」の数値の両方がここから出る。
// =============================================================================
package io.github.jacoloves.ffa.domain.balance

import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.ItemId
import io.github.jacoloves.ffa.domain.data.JobId

/** 値の列の中央値(空なら null)。外れ値に引きずられないよう、平均ではなく中央値で評価する。 */
fun <T : Comparable<T>> List<T>.medianOrNull(): T? = if (isEmpty()) null else sorted()[size / 2]

fun List<Double>.meanOrNull(): Double? = if (isEmpty()) null else sum() / size

/** 小数 1 桁の文字列(JS/JVM で同じ表記になるよう手で整形する)。 */
fun fmt1(d: Double): String {
    val r = kotlin.math.round(d * 10) / 10
    val s = r.toString()
    return if (s.contains('.')) s else "$s.0"
}

object BalanceReport {

    /** 各エリアの解放・突入・勝率の表。 */
    fun areaTable(reports: List<SimReport>): String = buildString {
        appendLine("| エリア | 解放戦数(中央値) | 解放時Lv | 推奨Lv | 突入時Lv | 突入時DLv1勝率 | 実戦勝率 | 到達DLv | 滞在戦数 | ドロップ限定品/滞在 |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        for (area in AreaId.entries) {
            val def = GameData.area(area)
            val unlockF = reports.mapNotNull { it.unlockFight[area] }.medianOrNull()
            val unlockL = reports.mapNotNull { it.unlockLevel[area] }.medianOrNull()
            val entryL = reports.mapNotNull { it.entryLevel[area] }.medianOrNull()
            val entryW = reports.mapNotNull { it.entryWinRate[area] }.meanOrNull()
            val realW = reports.mapNotNull { it.winRate(area) }.meanOrNull()
            val dlv = reports.mapNotNull { it.maxDlv[area] }.medianOrNull()
            val stay = reports.mapNotNull { it.stayFights[area] }.medianOrNull()
            val drop = reports.map { (it.dropOnlyByArea[area] ?: 0).toDouble() }.meanOrNull()
            appendLine(
                "| ${def.name} | ${unlockF ?: "-"} | ${unlockL ?: "-"} | ${def.recommendedLevel} | ${entryL ?: "-"} | " +
                    "${entryW?.let { fmt1(it * 100) + "%" } ?: "-"} | ${realW?.let { fmt1(it * 100) + "%" } ?: "-"} | " +
                    "${dlv ?: "-"} | ${stay ?: "-"} | ${drop?.let { fmt1(it) } ?: "-"} |",
            )
        }
    }

    /** 職業マスターと、ショップ品の初購入までの戦数。 */
    fun jobAndShopTable(reports: List<SimReport>): String = buildString {
        appendLine("| 項目 | 戦数(中央値) | seed 数 |")
        appendLine("|---|---|---|")
        for (job in JobId.entries) {
            val v = reports.mapNotNull { it.masteredFight[job] }
            appendLine("| ${GameData.job(job).name} マスター | ${v.medianOrNull() ?: "-"} | ${v.size}/${reports.size} |")
        }
        for (item in ItemId.entries) {
            val def = GameData.item(item)
            if (def.price == null || item == ItemId.WOODEN_SWORD) continue // 木の剣は最初から持っている
            // 稼ぎが価格に届くまでの、そのエリアでの戦闘数(滞在中に限る)
            val v = reports.mapNotNull { it.fightsToEarnPrice[item] }
            appendLine("| ${def.name}(${def.price}G)を稼ぎ切るまで | ${v.medianOrNull() ?: "-"}戦 | ${v.size}/${reports.size} |")
        }
    }

    /** エンドレス区間(火山突入後)の 100 戦ごとのキャラLv増加と火山DLv増加(seed の中央値)。 */
    fun endlessTable(reports: List<SimReport>): String = buildString {
        val all = reports.map { it.endlessWindows() }
        val n = all.minOfOrNull { it.size } ?: 0
        appendLine("| 窓(火山突入後) | Lv増加/100戦(中央値) | 火山DLv増加/100戦(中央値) | 窓終了時のLv(中央値) |")
        appendLine("|---|---|---|---|")
        for (k in 0 until n) {
            val gains = all.map { it[k].levelGain }
            val dlvs = all.map { it[k].dlvGain }
            appendLine("| ${k * 100 + 1}〜${(k + 1) * 100} 戦 | ${gains.medianOrNull()} | ${dlvs.medianOrNull()} | - |")
        }
    }
}
