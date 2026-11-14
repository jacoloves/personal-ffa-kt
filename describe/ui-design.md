# ui 詳細設計（Phase 7）

> plan.md の「4. UI/UX」を実装できる粒度まで落とした設計書。パッケージ: `io.github.jacoloves.ffa.ui`

## 全体像

```
 main() ── renderComposable("root") ── App()
                                        │
                     GameController（状態ホルダー。domain の GameEngine を呼ぶ唯一の場所）
                       ├ state: GameState?        (mutableStateOf。null = 未開始 → 名前入力画面)
                       ├ lastBattle: 最新の戦闘ログと報酬のイベント
                       ├ message: 直近の GameError の日本語メッセージ（トースト／バナーで表示）
                       ├ nowMillis: 1秒ごとに更新する（クールタイムの残り表示に使う）
                       └ dispatch(command) → GameEngine.execute → 成功したら state を更新して save
                       │
          LocalStorageSaveRepository（SaveRepository の実装。key = "ffa-kt/save"）
          SystemClock（Clock の実装。Date.now()）
          Random.Default
```

- **画面の部品は描画に専念する**。ロジックは domain に、状態遷移は GameController に置く。
- 1秒ごとの tick には `LaunchedEffect(Unit) { while (true) { delay(1000); controller.tick() } }` を使う。tick は `nowMillis` を更新するだけ。
- 起動時に `load()` を呼ぶ。
  - `Loaded` → そのまま開始する
  - `NotFound` → 名前入力画面を出す
  - `Corrupted` → 「セーブデータが壊れています（理由）。退避して新しく始めますか？」と表示する。生データは `ffa-kt/save.corrupted-<epoch>` に退避する

## 画面構成

```
┌ Header ─────────────────────────────────┐
│ 勇者アレン  Lv 1億2345万  剣聖(Lv 42 ★)   💰 3億    │
└─────────────────────────────────────────┘
[戦闘] [ステータス] [装備] [転職] [ショップ] [記録] [設定]   ← 横スクロールできるチップ
┌ タブの内容 ─────────────────────────────┐
```

| タブ | 内容 |
|---|---|
| 戦闘 | エリアの選択（解放済みのエリアだけ。未解放のエリアは条件を併記して無効化）、DLv の `[-]` 数値 `[+]`（範囲は 1..max+1）、**[戦う] ボタン**（クールタイム中は無効化し「あと 1分25秒」のように分秒で表示。押した結果も表示する）、戦闘ログ（勝敗の行は色付き）、報酬カード（EXP / G / ドロップ / Lvアップ / マスター / 解放の通知） |
| ステータス | HP・攻撃・防御・素早さ・運の最終値（formatJp で表示）、内訳（職業の基礎値、マスターボーナスの%、装備）、キャラEXPのゲージ、職業EXPのゲージ |
| 装備 | 3つの枠ごとに、装備中のアイテムと所持しているアイテムの一覧（名前 +N、ステータス）を表示し、[装備] ボタンで付け替える |
| 転職 | 6つの職業をカードで表示（Tier、職業Lv、★マスター、解放条件）。[転職する] ボタンは、現在の職業と未解放の職業では無効化する |
| ショップ | 解放済みエリアの商品（名前、ステータス、価格。所持済みなら「購入で+1」と表示）。ゴールドが足りなければボタンを無効化し、理由を併記する |
| 記録 | 総戦闘数、勝利数、勝率、累計ゴールド、最高キャラLv、マスターした職業の数、エリアごとの最高DLv |
| 設定 | クールタイムの秒数（1〜180 の数値入力＋「設定」ボタン、または ±。初期値 90 秒。勝敗で待ち時間は変わらない）、セーブデータの削除（confirm ダイアログで2回確認する）、バージョン表示 |

### 名前入力画面
- タイトル、名前の入力欄（最大12文字。空白だけの名前はエラーを表示）、[冒険を始める] ボタン

## スタイル（ダークテーマ）

- Compose HTML の `StyleSheet` オブジェクト `AppStyles` に集約する
- CSS のカスタムプロパティで色を定義する

```
--bg: #12131a; --surface: #1c1e29; --surface-2: #262938; --border: #33374a;
--text: #e6e8f0; --text-dim: #9aa0b4; --accent: #7aa2f7; --win: #9ece6a; --lose: #f7768e; --gold: #e0af68; --crit: #ff9e64
```

- フォントはシステムフォント（`system-ui, -apple-system, "Hiragino Sans", "Noto Sans JP", sans-serif`）。数値には `font-variant-numeric: tabular-nums` を指定する
- **レイアウト**
  - `max-width: 720px` で中央寄せ、左右の余白は 16px
  - 幅 360px でも横スクロールしない
  - タブは `overflow-x: auto` で横スクロールできるチップにする
  - ボタンの高さは 44px 以上（タップしやすくする）
- 戦闘ログは等幅ではなく通常のフォントにする
  - 1行ずつ、`white-space: pre-wrap` を指定する
  - 会心の行は `--crit`、勝利は `--win`、敗北は `--lose` の色にする
- ゲージは `div` 2枚重ねにして、幅を % で指定する
- `index.html` の body 背景を `--bg` と同じ色にし、読み込み中に白く光らないようにする

## 実装上の注意
- domain の `GameError.message` をそのまま表示する。UI 側で文言を作り直さない
- 状態を保存するのは、`dispatch` が成功したときだけにする
- localStorage の操作は例外をキャッチする。プライベートモードなどで使えない場合は「保存できません（理由）。このままでも遊べますが、閉じると進行が消えます」というバナーを出す
- `GameInfo` のプレースホルダーは、不要になれば削除する
- UI の自動テストは任意。GameController の dispatch と保存の流れは、Fake の Repository を使って jsTest で確認できると良い（Karma が必要になるなら省略してよい）
- 動作確認として、`./gradlew :ui:jsBrowserDistribution` の成果物を簡易サーバー（例: `python3 -m http.server`）で配信し、Claude in Chrome などで画面を確認できれば確認する。できない場合は、確認していないことを報告する
