// =============================================================================
// GameController.kt
// 役割: 画面の状態ホルダー。domain の GameEngine を呼ぶ「唯一の場所」。
//       画面(Composable)は、ここの状態を読んで描画し、操作を dispatch で渡すだけにする。
//
// 設計判断:
//   - 状態は mutableStateOf で持つ。Compose が読み取りを追跡し、変化した部分だけ再描画する。
//   - 保存は dispatch が成功したときだけ行う(失敗時は状態が変わらないため)。
//   - エラー文言は domain の GameError.message をそのまま表示する(UI で作り直さない)。
//   - 時計・乱数・保存先はコンストラクタで受け取る(テストでは偽物を差し込める)。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jacoloves.ffa.domain.Clock
import io.github.jacoloves.ffa.domain.Command
import io.github.jacoloves.ffa.domain.CommandResult
import io.github.jacoloves.ffa.domain.GameEngine
import io.github.jacoloves.ffa.domain.GameEvent
import io.github.jacoloves.ffa.domain.GameResult
import io.github.jacoloves.ffa.domain.GameState
import io.github.jacoloves.ffa.domain.save.LoadResult
import kotlin.random.Random

/** 画面上部に出すお知らせの種類。 */
enum class BannerKind { ERROR, INFO }

/** お知らせ(エラー・操作結果)。 */
data class Banner(val text: String, val kind: BannerKind)

/** タブの種類(表示順)。 */
enum class Tab(val label: String) {
    BATTLE("戦闘"), STATUS("ステータス"), EQUIP("装備"), JOB("転職"), SHOP("ショップ"), RECORD("記録"), SETTINGS("設定"),
}

/**
 * 直近の戦闘の表示用データ。
 *
 * @property won 勝利か
 * @property lines 戦闘ログ(1 行 1 イベント)
 * @property lineKinds [lines] と同じ長さの色分け情報(長さが合わないときは空)
 * @property rewards 報酬・通知(経験値、ゴールド、レベルアップなど。BattleFinished 以外のイベント)
 */
data class BattleReport(
    val won: Boolean,
    val lines: List<String>,
    val lineKinds: List<LogKind>,
    val rewards: List<GameEvent>,
)

/** ゲーム全体の状態と操作。 */
class GameController(
    private val storage: GameStorage,
    private val clock: Clock = SystemClock,
    private val random: Random = Random.Default,
) {
    /** 現在のゲーム状態。null は「未開始」(名前入力画面)。 */
    var state: GameState? by mutableStateOf(null)
        private set

    /** 読み込んだセーブが壊れていたときの情報(理由と生データ)。null でなければ確認画面を出す。 */
    var corrupted: LoadResult.Corrupted? by mutableStateOf(null)
        private set

    /** 直近の戦闘結果。 */
    var lastBattle: BattleReport? by mutableStateOf(null)
        private set

    /** お知らせ(エラー・購入結果など)。 */
    var banner: Banner? by mutableStateOf(null)
        private set

    /** 保存できないときの警告。null なら正常。 */
    var saveWarning: String? by mutableStateOf(null)
        private set

    /** 名前入力画面のエラー表示。 */
    var nameError: String? by mutableStateOf(null)
        private set

    /** 現在のタブ。 */
    var tab: Tab by mutableStateOf(Tab.BATTLE)

    /** 現在時刻(エポックミリ秒)。tick で更新し、クールタイムの残り表示に使う。 */
    var nowMillis: Long by mutableStateOf(clock.nowMillis())
        private set

    init {
        load()
    }

    /** 起動時の読み込み。結果に応じて state / corrupted を設定する。 */
    private fun load() {
        val result = try {
            storage.load()
        } catch (e: Throwable) {
            saveWarning = unavailableMessage(e)
            LoadResult.NotFound
        }
        when (result) {
            is LoadResult.Loaded -> state = result.state
            LoadResult.NotFound -> Unit
            is LoadResult.Corrupted -> corrupted = result
        }
    }

    /** 時刻を更新する(1 秒以下の間隔で呼ぶ)。 */
    fun tick() {
        nowMillis = clock.nowMillis()
    }

    /** 次の戦闘までの残りミリ秒(0 以下なら戦える)。 */
    fun cooldownRemainingMs(): Long = (state?.nextBattleAt ?: 0L) - nowMillis

    /** 新しいゲームを始める。名前が不正ならエラーを表示して何もしない。 */
    fun startNew(rawName: String) {
        if (rawName.trim().length > MAX_NAME_LENGTH) {
            nameError = "名前が長すぎます(${rawName.trim().length} 文字)。$MAX_NAME_LENGTH 文字以内の名前にしてください。"
            return
        }
        when (val r = GameState.new(rawName)) {
            is GameResult.Err -> nameError = r.error.message
            is GameResult.Ok -> {
                nameError = null
                banner = null
                lastBattle = null
                tab = Tab.BATTLE
                state = r.value
                persist(r.value)
            }
        }
    }

    /** 壊れたセーブを別キーに退避して、新しく始める(名前入力画面へ進む)。 */
    fun discardCorrupted() {
        val c = corrupted ?: return
        try {
            storage.backupCorrupted(c.raw)
        } catch (e: Throwable) {
            saveWarning = unavailableMessage(e)
        }
        corrupted = null
    }

    /** セーブデータを削除して最初の画面へ戻る。 */
    fun deleteSave() {
        try {
            storage.clear()
        } catch (e: Throwable) {
            saveWarning = unavailableMessage(e)
        }
        state = null
        lastBattle = null
        banner = null
        tab = Tab.BATTLE
    }

    /** 画面側で見つけた入力エラーを、エラーのお知らせとして表示する。 */
    fun showError(message: String) {
        banner = Banner(message, BannerKind.ERROR)
    }

    /** お知らせを閉じる。 */
    fun dismissBanner() {
        banner = null
    }

    /**
     * コマンドを実行する。成功したら状態を更新して保存し、true を返す。
     * 失敗したら状態は変えず、エラー文言をお知らせに出して false を返す。
     */
    fun dispatch(command: Command): Boolean {
        val current = state ?: return false
        // 時刻を最新にしてから実行する(tick との間隔のずれで、クールタイム判定が古くならないように)
        tick()
        return when (val r = GameEngine.execute(current, command, random, clock)) {
            is CommandResult.Failure -> {
                banner = Banner(r.error.message, BannerKind.ERROR)
                false
            }
            is CommandResult.Success -> {
                state = r.state
                banner = noticeFor(command, r.events)
                if (command == Command.Fight) lastBattle = toReport(current, r.events)
                persist(r.state)
                true
            }
        }
    }

    /** 戦闘以外の成功を短く知らせる文言(買い物・転職・装備)。それ以外は null。 */
    private fun noticeFor(command: Command, events: List<GameEvent>): Banner? {
        val text = when (command) {
            is Command.Buy -> events.filterIsInstance<GameEvent.ItemGained>().firstOrNull()?.let { describeItemGained(it) }
            is Command.ChangeJob -> "「${io.github.jacoloves.ffa.domain.data.GameData.job(command.job).name}」に転職しました。"
            is Command.Equip -> "「${io.github.jacoloves.ffa.domain.data.GameData.item(command.item).name}」を装備しました。"
            else -> null
        }
        return text?.let { Banner(it, BannerKind.INFO) }
    }

    /** 戦闘イベントを画面用の [BattleReport] にまとめる。 */
    private fun toReport(before: GameState, events: List<GameEvent>): BattleReport? {
        val finished = events.filterIsInstance<GameEvent.BattleFinished>().firstOrNull() ?: return null
        val kinds = if (finished.outcome.events.size == finished.logLines.size) {
            finished.outcome.events.map { logKindOf(it, before.playerName) }
        } else {
            emptyList()
        }
        return BattleReport(
            won = finished.outcome.playerWon,
            lines = finished.logLines,
            lineKinds = kinds,
            rewards = events.filter { it !is GameEvent.BattleFinished },
        )
    }

    /** 保存する。失敗しても遊びは続けられるよう、例外は警告に変える。 */
    private fun persist(s: GameState) {
        try {
            storage.save(s)
            saveWarning = null
        } catch (e: Throwable) {
            saveWarning = unavailableMessage(e)
        }
    }

    /** 保存できないときの警告文(何が・なぜ・どうなるか)。 */
    private fun unavailableMessage(e: Throwable): String =
        "保存できません(${e.message ?: e.toString()})。このままでも遊べますが、閉じると進行が消えます。" +
            "ブラウザのプライベートモードやサイトデータのブロック設定を確認してください。"

    companion object {
        /** 名前の最大文字数。 */
        const val MAX_NAME_LENGTH = 12
    }
}
