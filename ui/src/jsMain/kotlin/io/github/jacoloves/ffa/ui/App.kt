// =============================================================================
// App.kt
// 役割: ルートの Composable。画面の切り替え(破損確認 / 名前入力 / ゲーム本体)と、
//       共通部品(ヘッダー・タブ・お知らせ・ゲージ)を持つ。
//       ロジックは GameController と domain に任せ、ここは描画に専念する。
// =============================================================================
package io.github.jacoloves.ffa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.jacoloves.ffa.domain.GameInfo
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.formatJp
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.dom.Text

/** ルート。スタイルを 1 回だけ注入し、時刻の tick を回して、状態に応じた画面を出す。 */
@Composable
fun App(controller: GameController) {
    Style(AppStyles)

    // クールタイム表示用の時刻更新。0.25 秒刻みにして、「あと N 秒」の切り替わりを遅らせない。
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            controller.tick()
        }
    }

    Div({ classes(AppStyles.app) }) {
        val corrupted = controller.corrupted
        val state = controller.state
        when {
            corrupted != null -> CorruptedScreen(controller, corrupted.reason)
            state == null -> StartScreen(controller)
            else -> GameScreen(controller)
        }
    }
}

/** セーブが壊れていたときの確認画面。 */
@Composable
private fun CorruptedScreen(controller: GameController, reason: String) {
    Div({ classes(AppStyles.center) }) {
        H1({ classes(AppStyles.title) }) { Text(GameInfo.TITLE) }
        Div({ classes(AppStyles.card) }) {
            P { Text("セーブデータが壊れています(理由)") }
            P({ classes(AppStyles.muted) }) { Text(reason) }
            P { Text("壊れたデータは別の場所に退避してから、新しく始めます。よろしいですか?") }
            Button({
                classes(AppStyles.primary)
                onClick { controller.discardCorrupted() }
            }) { Text("退避して新しく始める") }
        }
        SaveWarning(controller)
    }
}

/** 名前入力画面。 */
@Composable
private fun StartScreen(controller: GameController) {
    var name by remember { mutableStateOf("") }
    Div({ classes(AppStyles.center) }) {
        H1({ classes(AppStyles.title) }) { Text(GameInfo.TITLE) }
        P({ classes(AppStyles.muted) }) { Text("ボタンを押すだけで戦い、育てていく放置系RPGです。まずは冒険者の名前を決めてください。") }
        Div({ classes(AppStyles.card) }) {
            Input(InputType.Text) {
                value(name)
                placeholder("名前(最大 ${GameController.MAX_NAME_LENGTH} 文字)")
                attr("maxlength", GameController.MAX_NAME_LENGTH.toString())
                attr("aria-label", "プレイヤー名")
                onInput { name = it.value }
                onKeyDown { if (it.key == "Enter") controller.startNew(name) }
            }
            controller.nameError?.let { err ->
                P({ classes(AppStyles.lose) }) { Text(err) }
            }
            Div({ style { property("margin-top", "12px") } }) {
                Button({
                    classes(AppStyles.primary, AppStyles.bigButton)
                    onClick { controller.startNew(name) }
                }) { Text("冒険を始める") }
            }
        }
        SaveWarning(controller)
    }
}

/** ゲーム本体(ヘッダー・タブ・お知らせ・タブの中身)。 */
@Composable
private fun GameScreen(controller: GameController) {
    val state = controller.state ?: return
    val job = GameData.job(state.currentJob)
    val jobProgress = state.jobs[state.currentJob]

    Div({ classes(AppStyles.header) }) {
        Span({ classes(AppStyles.headerName) }) { Text(state.playerName) }
        Span { Text("Lv ${formatJp(state.charLevel)}") }
        Span {
            Text("${job.name}(Lv ${jobProgress?.level ?: 1}${if (jobProgress?.mastered == true) " ★" else ""})")
        }
        Span({ classes(AppStyles.gold) }) { Text("${formatJp(state.gold)} G") }
    }

    Div({ classes(AppStyles.tabs); attr("role", "tablist") }) {
        for (t in Tab.entries) {
            Button({
                classes(AppStyles.tab)
                if (controller.tab == t) classes(AppStyles.tabActive)
                attr("role", "tab")
                onClick { controller.tab = t }
            }) { Text(t.label) }
        }
    }

    SaveWarning(controller)
    BannerView(controller)

    when (controller.tab) {
        Tab.BATTLE -> BattleTab(controller)
        Tab.STATUS -> StatusTab(controller)
        Tab.EQUIP -> EquipTab(controller)
        Tab.JOB -> JobTab(controller)
        Tab.SHOP -> ShopTab(controller)
        Tab.RECORD -> RecordTab(controller)
        Tab.SETTINGS -> SettingsTab(controller)
    }
}

/** 保存できないときの警告バナー。 */
@Composable
private fun SaveWarning(controller: GameController) {
    controller.saveWarning?.let { text ->
        Div({ classes(AppStyles.banner, AppStyles.bannerWarn); attr("role", "alert") }) {
            Span({ classes(AppStyles.bannerText) }) { Text(text) }
        }
    }
}

/** エラー・操作結果のお知らせ。閉じるボタン付き。 */
@Composable
private fun BannerView(controller: GameController) {
    controller.banner?.let { b ->
        val kindClass = if (b.kind == BannerKind.ERROR) AppStyles.bannerError else AppStyles.bannerInfo
        Div({ classes(AppStyles.banner, kindClass); attr("role", if (b.kind == BannerKind.ERROR) "alert" else "status") }) {
            Span({ classes(AppStyles.bannerText) }) { Text(b.text) }
            Button({
                attr("aria-label", "お知らせを閉じる")
                onClick { controller.dismissBanner() }
            }) { Text("×") }
        }
    }
}

/** ゲージ(div 2 枚重ね)。[current] / [max] を % にして幅を決める。 */
@Composable
fun Gauge(current: Long, max: Long, jobColor: Boolean = false) {
    val pct = if (max <= 0) 0.0 else (current.toDouble() / max.toDouble() * 100.0).coerceIn(0.0, 100.0)
    Div({ classes(AppStyles.gauge) }) {
        Div({
            classes(if (jobColor) AppStyles.gaugeFillJob else AppStyles.gaugeFill)
            style { width(pct.percent) }
        })
    }
}

/** 押せるボタンの共通形。[enabled] が false なら無効化する。 */
@Composable
fun ActionButton(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    Button({
        if (primary) classes(AppStyles.primary)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { Text(label) }
}
