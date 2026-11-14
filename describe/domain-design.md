# domain 詳細設計（Phase 2〜6）

> plan.md の決定事項を、実装できる粒度まで落とした設計書。数値はすべて `Balance` に集約し、Phase 9 で調整する。
> パッケージのルート: `io.github.jacoloves.ffa.domain`

## 全体像

```
                ┌───────────────────── GameEngine ─────────────────────┐
 Command ──────▶│ execute(state, command, random, clock): CommandResult │──▶ Success(newState, events)
 (Fight, ...)   └───────┬───────────────┬───────────────┬──────────────┘    Failure(GameError)
                        │               │               │
                 BattleEngine     Progression       Inventory
                 (戦闘計算)        (EXP/Lv/職業)      (装備/ドロップ/ショップ)
                        │               │               │
                        └──── StatCalculator（最終ステータス）────┘
                                        │
                        GameData（職業・エリア・敵・アイテムの定義）
                        Balance（係数）  SatMath / JpNumber（数値基盤）
```

- `GameEngine` は**純粋関数**。状態は不変の `data class` で、新しい状態を返す。
- 乱数は `kotlin.random.Random` を引数で受け取る（テストでは `Random(seed)` か、決まった値を返すサブクラス）。
- 時刻は `fun interface Clock { fun nowMillis(): Long }` を引数で受け取る。

## Phase 2: 数値基盤（`num` パッケージ）＋ Stats ＋ Balance

### SatMath（飽和演算）
| 関数 | 仕様 |
|---|---|
| `add(a, b)` | オーバーフローしたら `Long.MAX_VALUE`、アンダーフローしたら `Long.MIN_VALUE` |
| `mul(a, b)` | 同上 |
| `mulRatio(a, ratio: Double)` | `a × ratio` を切り捨てて Long にする。範囲外は MAX/MIN に丸める。NaN は 0 |
| `pow(base: Double, exp: Int): Double` | 倍率の計算用。Infinity になってもよく、`mulRatio` 側で飽和させる |

### JpNumber（和式表記）
- `formatJp(n: Long): String`
- 単位: 万=10^4、億=10^8、兆=10^12、京=10^16
- 1万未満はそのまま表示する（`9999` → `"9999"`、カンマなし）
- 1万以上は **上位2単位まで**表示し、下位の単位が0なら省略する
  - `12345` → `"1万2345"`、`123456789` → `"1億2345万"`、`100000000` → `"1億"`、`100000123` → `"1億"`、`1_2345_0000_0000` → `"1兆2345億"`
  - `Long.MAX_VALUE` → `"922京3372兆"`
- 負の数は先頭に `-` を付ける（`Long.MIN_VALUE` も扱えること）

### Stats
```kotlin
data class Stats(val hp: Long, val atk: Long, val def: Long, val spd: Long, val luk: Long)
```
- `plus`（飽和）、`times(ratio: Double)`（全項目に `mulRatio`）、`times(other: StatRatios)`（項目ごとの倍率）
- `StatRatios(hp: Double, atk: Double, ...)` で、マスターボーナスなどの割合を表す。`plus` で合算できる
- `Stats.ZERO`

### Balance（初期値）
```kotlin
object Balance {
    // 戦闘
    const val MAX_ROUNDS = 50
    const val DAMAGE_VARIANCE_MIN = 0.9; const val DAMAGE_VARIANCE_MAX = 1.1
    const val DEF_FACTOR = 0.5
    const val CRIT_MULTIPLIER = 2.0
    const val CRIT_BASE = 0.05; const val CRIT_LUK_BONUS = 0.20; const val CRIT_MAX = 0.25
    const val EVADE_FACTOR = 0.05; const val EVADE_MAX = 0.30
    const val DOUBLE_ATTACK_FACTOR = 0.25; const val DOUBLE_ATTACK_MAX = 0.5
    // 成長
    const val CHAR_EXP_BASE = 10.0; const val CHAR_EXP_EXPONENT = 1.5
    const val LEVEL_FACTOR_GROWTH = 1.03     // ステータス倍率 = 1.03^(lv-1)(純粋な指数)
    const val JOB_MASTER_LEVEL = 30
    const val JOB_EXP_BASE = 2               // 職業の必要EXP = 2 + jobLv / 5
    // ダンジョンLv
    const val DLV_ENEMY_GROWTH = 1.25        // 敵ステータス倍率 = 1.25^(dlv-1)（Phase 9 で 1.15 から変更）
    const val DLV_REWARD_GROWTH = 1.15       // EXP・ゴールド倍率 = 1.15^(dlv-1)（Phase 9 で 1.12 から変更）
    const val AREA_UNLOCK_DLV = 10
    // エリア倍率（index = tier - 1。Phase 9 で追加。GameData の基礎値にかかる）
    val AREA_STAT_SCALE   = listOf(1.0, 4.0, 18.0, 65.0)   // 敵ステータス
    val AREA_REWARD_SCALE = listOf(1.0, 1.5, 3.5, 20.0)    // 敵の EXP・ゴールド
    val AREA_GEAR_SCALE   = listOf(1.0, 4.5, 14.0, 80.0)   // 装備の性能
    val AREA_PRICE_SCALE  = listOf(1.0, 5.0, 22.0, 200.0)  // ショップの価格
    // 装備
    const val ENHANCE_RATIO_PER_LEVEL = 0.10 // +1ごとに基礎値の +10%
    const val DROP_LUK_BONUS_MAX = 0.5       // ドロップ率 × (1 + 0.5 × pLuk/(pLuk+eLuk))
    // クールタイム
    const val DEFAULT_COOLDOWN_SEC = 90; const val MIN_COOLDOWN_SEC = 1; const val MAX_COOLDOWN_SEC = 180
    // 敗北時の倍化(LOSE_COOLDOWN_MULTIPLIER)は廃止。勝敗とも同じクールタイム
}
```

## Phase 3: 戦闘・エリア・敵

### ID は enum で定義する（型安全、シリアライズは enum 名）
`enum class AreaId { MEADOW, FOREST, CAVE, VOLCANO }` / `enum class EnemyId { ... }`

### 定義データ（`data` パッケージ、`GameData` オブジェクト）
```kotlin
data class EnemyDef(val id: EnemyId, val name: String, val stats: Stats, val exp: Long, val gold: Long,
                    val weight: Int, val drops: List<DropEntry>)
data class DropEntry(val item: ItemId, val chance: Double)
data class AreaDef(val id: AreaId, val name: String, val tier: Int, val enemies: List<EnemyId>,
                   val unlock: AreaUnlock?) // null = 最初から解放
data class AreaUnlock(val area: AreaId, val dlv: Int)
```
- エリアごとに5体（オリジナルの王道ファンタジー名）。敵の基礎値は**どのエリアも草原と同じ桁**（HP 15〜120、atk 4〜18）で書き、
  エリアの `tier` に応じた倍率表（`Balance.AREA_STAT_SCALE` / `AREA_REWARD_SCALE`）を `GameData` のヘルパーがかける。
  （Phase 9 前は「後のエリアほど基礎値を約10倍」を手で書いていたが、シミュレーションで整合を取るため倍率表に分離した。）
  - 草原（tier 1）: スライム、大ネズミ、角ウサギ、ゴブリン、野犬 — HP 16〜40、atk 4〜8（最初の 1 戦で Lv1 の戦士が 90% 以上勝てる強さ）
  - 森（tier 2）: ウッドウルフ、毒キノコ、森のゴブリン長、トレント、妖精の悪戯者
  - 洞窟（tier 3）: 大コウモリ、ゴーレム、地底トカゲ、スケルトン、洞窟オーガ
  - 火山（tier 4）: 火トカゲ、溶岩スライム、炎の魔獣、ファイアエレメント、若竜
- `GameData.area(id)`、`GameData.enemy(id)` などで参照する（定義が抜けていればテストで検出する）。

### 敵の生成
`EnemyFactory.create(def, dlv): Combatant`
- ステータス = `def.stats × 1.25^(dlv-1)`。報酬 = `def.exp/gold × 1.15^(dlv-1)`（`def` にはすでにエリア倍率がかかっている）
- エリア内での出現は `weight` による重み付き抽選（`pickEnemy(area, random)`）

### BattleEngine
```kotlin
data class Combatant(val name: String, val stats: Stats)
sealed interface BattleEvent {
  data class Encounter(val enemyName: String) ; data class Attack(val attacker: String, val target: String, val damage: Long, val critical: Boolean)
  data class Evaded(val attacker: String, val target: String) ; data class Defeated(val name: String) ; data object Timeout
}
data class BattleOutcome(val playerWon: Boolean, val events: List<BattleEvent>, val rounds: Int)
fun fight(player: Combatant, enemy: Combatant, random: Random): BattleOutcome
```
- 1ラウンドの流れ: `spd` が高い方が先に行動する（同じならプレイヤーが先）→ 双方が1回ずつ攻撃する。さらに追撃の判定に成功すると、もう1回攻撃する。
- 攻撃1回の処理:
  1. 回避率 = `min(EVADE_MAX, EVADE_FACTOR × 防御側spd / max(1, 攻撃側spd))`
  2. 会心率 = `min(CRIT_MAX, CRIT_BASE + CRIT_LUK_BONUS × 自分のluk / (自分のluk + 相手のluk + 1))`
  3. ダメージ = `max(1, atk × 乱数[0.9,1.1) − def × 0.5)`。会心ならさらに ×2（すべて飽和演算）
- 追撃率 = `clamp((自分spd / max(1, 相手spd) − 1) × 0.25, 0, 0.5)`
- 相手のHPが0以下になったら即座に終了する。`MAX_ROUNDS` を超えたら `Timeout` でプレイヤーの敗北
- HPは戦闘の中だけで使うローカル変数にする（戦闘ごとに全快）
- `BattleLogFormatter.format(event): String` で日本語に変換する（数値は `formatJp`）
  - 例: `「スライムがあらわれた！」`、`「勇者の攻撃！ スライムに 1万2345 のダメージ！」`、`「会心の一撃！」`

## Phase 4: 成長・職業

```kotlin
enum class JobId { WARRIOR, KNIGHT, THIEF, PRIEST, SWORD_MASTER, PALADIN }
data class JobDef(val id: JobId, val name: String, val tier: Int, val base: Stats, val growth: Stats,
                  val masterBonus: StatRatios, val requires: Set<JobId>)
data class JobProgress(val level: Int = 1, val exp: Long = 0, val mastered: Boolean = false)
```
| 職業 | base (hp/atk/def/spd/luk) | growth/Lv | マスターボーナス |
|---|---|---|---|
| 戦士 | 30/8/4/5/3 | 6/3/1/1/1 | atk +5% |
| 騎士 | 40/5/7/3/3 | 9/1/3/1/1 | def +5% |
| 盗賊 | 25/6/3/8/7 | 5/2/1/3/2 | spd +5% |
| 僧侶 | 35/5/5/4/5 | 8/2/2/1/2 | hp +5% |
| 剣聖（戦士＋盗賊） | 35/10/4/8/5 | 8/4/1/3/2 | atk +10%, spd +5% |
| 聖騎士（騎士＋僧侶） | 50/7/8/4/4 | 11/2/3/1/1 | hp +10%, def +5% |

- **キャラLv**: 必要EXP = `ceil(CHAR_EXP_BASE × lv^1.5)`。1回で複数Lv上がることもある。上限はなく、Long で飽和させる
  - `gainCharExp` は通常 1 レベルずつ正確に計算する。1 回の獲得でループが 10,000 回を超える巨大な獲得
    （Long 上限級なら約 1,000 万レベル分）では、累計必要EXPの積分近似 `4×((lv+n−0.5)^2.5 − (lv−0.5)^2.5)+0.5n` を二分探索して
    まとめてレベルを飛ばし、残りだけ正確に計算する。不変条件（`0 <= exp < 次の必要EXP`、Long で飽和）は常に守る
- **職業Lv**: 必要EXP = `JOB_EXP_BASE + jobLv / 5`。勝利ごとに職業EXPを `エリアの tier` だけ獲得する（DLv の影響なし）
  - `JOB_MASTER_LEVEL` に到達すると `mastered = true` になる（1回だけ）。その後も職業Lvは上がる
- **最終ステータス**（`StatCalculator`）:
  `((job.base + job.growth × (lv−1)) × 1.03^(lv−1)) × (1 + マスターボーナスの合計) + 装備の合計`
  - マスターボーナスの合計 = マスター済みの全職業の `masterBonus` を足したもの
- **転職**: 解放条件は、`requires` の職業がすべてマスター済みであること。Tier1 は最初から解放されている
  - 解放されていなければ `GameError.JobLocked(job, missing)`、同じ職業なら `GameError.AlreadyCurrentJob`
- 実績: `maxCharLevel`、マスターした職業の数

## Phase 5: 装備・ドロップ・ショップ

```kotlin
enum class Slot { WEAPON, ARMOR, ACCESSORY }
enum class ItemId { ... }
data class ItemDef(val id: ItemId, val name: String, val slot: Slot, val stats: Stats,
                   val price: Long?, val shopArea: AreaId?)  // price=null は非売品（ドロップ限定）
```
- 各エリアに、ショップで買える装備（3枠×1つ）と、ドロップ限定の装備（1つ）を用意する
- 装備の基礎性能・価格も敵と同様に**草原と同じ桁**で書き、`Balance.AREA_GEAR_SCALE`（性能）・`AREA_PRICE_SCALE`（価格）をエリアの tier でかける
  （草原は 木の剣 atk3/50G、革の鎧 hp15・def2/80G、幸運のお守り spd1・luk3/60G、ドロップ限定の牙の短剣 atk5・spd2）
- ドロップ率は 5〜12%（Phase 9 で 2〜10% から引き上げ。ドロップ限定品を各エリアで平均 1 個以上入手できる水準）
- 装備のステータス = `stats × (1 + 0.1 × 強化値)`
- **持ち物**: `owned: Map<ItemId, Int>`（値は強化値。初めて入手したときは0）、`equipped: Map<Slot, ItemId>`
- **入手時の処理**: まだ持っていなければ +0 で追加する。持っていれば強化値を+1する（`ItemGained(item, enhance, isNew)` イベント）
- **ドロップ**: 敵の `drops` を1つずつ判定する。確率 = `chance × (1 + 0.5 × pLuk/(pLuk+eLuk))`。1回の戦闘で複数落ちることもある
- **ショップ**: 解放済みエリアの `shopArea` の商品を買える。ゴールドが足りなければ `GameError.NotEnoughGold(need, have)`。まだ解放されていないエリアの商品なら `GameError.AreaLocked`
- **装備する**: 持っていなければ `GameError.ItemNotOwned`
- 新規開始時は、草原ショップの武器（木の剣）を +0 で装備している

## Phase 6: GameState・GameEngine・セーブ

```kotlin
@Serializable data class GameState(
  val schemaVersion: Int = 2,
  val playerName: String,
  val charLevel: Long = 1, val charExp: Long = 0,
  val currentJob: JobId = JobId.WARRIOR,
  val jobs: Map<JobId, JobProgress>,
  val gold: Long = 0,
  val owned: Map<ItemId, Int>, val equipped: Map<Slot, ItemId>,
  val currentArea: AreaId = AreaId.MEADOW,
  val selectedDlv: Map<AreaId, Int>, val maxClearedDlv: Map<AreaId, Int>,
  val cooldownSec: Int = Balance.DEFAULT_COOLDOWN_SEC,
  val nextBattleAt: Long = 0,
  val records: Records = Records(),
)
@Serializable data class Records(val battles: Long = 0, val wins: Long = 0, val totalGold: Long = 0, val maxCharLevel: Long = 1)
```
- `GameState.new(playerName)` で初期状態を作る（名前が空白なら `GameError.InvalidName`）

### コマンドとエラー
```kotlin
sealed interface Command { data object Fight; data class SelectArea(AreaId); data class SetDlv(Int);
  data class ChangeJob(JobId); data class Buy(ItemId); data class Equip(ItemId); data class SetCooldown(Int) }
sealed interface CommandResult { data class Success(val state: GameState, val events: List<GameEvent>); data class Failure(val error: GameError) }
sealed interface GameError { val message: String }   // 日本語で「何が・なぜ・どうすれば」を書く
//   CooldownActive(remainingMs), AreaLocked(area, requirement), DlvOutOfRange(dlv, max),
//   JobLocked(job, missing), AlreadyCurrentJob, NotEnoughGold(need, have), ItemNotOwned, InvalidCooldown, InvalidName
sealed interface GameEvent // BattleFinished(outcome, logLines), CharLevelUp(from,to), JobLevelUp(job,from,to),
                           // JobMastered(job), JobUnlocked(job), AreaUnlocked(area), GoldGained, ExpGained, ItemGained, DlvRecord(area, dlv)
```

### Fight の処理順
1. `clock.now < nextBattleAt` なら `CooldownActive` を返す
2. 敵を抽選して生成し、プレイヤーの Combatant（StatCalculator の結果）と戦闘させる
3. **勝利**: キャラEXP・職業EXP・ゴールドを加算 → Lvアップ処理 → ドロップ判定 → `maxClearedDlv` を更新 → 新しく解放されたエリアがあれば `AreaUnlocked` / 職業があれば `JobUnlocked` → `nextBattleAt = now + cooldown`
4. **敗北**: 報酬はなし → `nextBattleAt = now + cooldown`（勝利と同じ。倍化はしない）
5. どちらの場合も `records` を更新する
- **DLv**: 選べる範囲は `1..(maxClearedDlv[area] + 1)`（何も倒していなければ `1..1`）
- **エリア解放**: `AreaUnlock(area, dlv)` について、`maxClearedDlv[area] >= dlv` を満たすこと

### セーブ（`kotlinx-serialization-json`）
- domain には `interface SaveRepository { fun load(): LoadResult; fun save(state: GameState) }` と、`SaveCodec`（`encode(state): String` / `decode(json): LoadResult`）を置く
- `LoadResult`: `Loaded(state)` / `NotFound` / `Corrupted(reason: String, raw: String)`
- `decode` はまず `schemaVersion` だけを読み、`migrate(fromVersion, json)` を通してからデコードする（v1→v2: 既定クールタイムが 5 秒から 90 秒に変わったため、`cooldownSec` が旧既定の 5 なら 90 に置き換え、それ以外のカスタム値は 1〜180 に収めて維持する。未知の/新しすぎるバージョンは Corrupted）
- `Json { ignoreUnknownKeys = true; encodeDefaults = true }`
- localStorage への実装は ui 側に置く（Phase 7）。domain では文字列の往復（ラウンドトリップ）と、壊れたJSONの扱いをテストする

## テスト方針
- フェーズごとに **テストを先に** 書いてから実装する（`commonTest`、`kotlin.test`）
- 戦闘のテストは `Random(seed)` で結果を再現できるようにする。境界値は、固定の値を返す `Random` のサブクラスで検証する
- `GameData` の整合性テスト: すべての enum 値に定義がある、ドロップ先の ItemId が存在する、エリアの敵リストが空でない、など

## Phase 9: バランス調整結果

### 何をなぜ変えたか
| 問題 | 原因 | 対処 |
|---|---|---|
| 最初の戦闘で野犬に負ける | Lv1 戦士（HP30・atk11）に対し野犬は atk12・HP60 | 草原の敵の基礎値を下げた（野犬 HP40・atk8 など）。最初の 1 戦の勝率 **95.4%** |
| エリアの強さがキャラの成長曲線と合わない（敵は「約10倍」だが、キャラは 1.03^(lv-1) の指数） | 手で書いた 10 倍の数値に根拠がなかった | 基礎値を草原と同じ桁にそろえ、エリア倍率表（`AREA_*_SCALE`）に分離。ボット（下記）で倍率を実測して決めた |
| 育っていないキャラでも運よく DLv10 を 1 回勝てて、森が 20〜80 戦で開く | `DLV_ENEMY_GROWTH = 1.15` で DLv10 が 3.5 倍止まり | `1.25`（DLv10 で約 7.5 倍）に上げ、報酬倍率も `1.15` に。さらに草原の EXP を下げた |
| 推奨Lv（30/100/300）が実態と乖離 | キャラのステータスが指数なので、Lv300 は敵の桁を大きく超える | 推奨Lvを実測の解放時Lv に合わせ、**1 / 10 / 25 / 52** に変更 |
| ドロップ限定品が出ない（2〜3%） | 確率が低すぎる | 5〜12% に引き上げ。各エリアの滞在中に平均 1 個以上（草原でも約 2.4 個） |
| `gainCharExp` が巨大な獲得で遅い | 1 レベルずつのループ | 10,000 回を超えるときだけ二分探索で飛ばす（上記） |
| ショップ価格が収入と無関係 | 敵と同じ約 10 倍で決めていた | 価格倍率を EXP/ゴールド倍率に連動させ、各エリアの装備を「そのエリアで 40〜120 戦」稼げば買える水準にした |

### 倍率表と草原からの変化
| | 草原 | 森 | 洞窟 | 火山 |
|---|---|---|---|---|
| 敵ステータス倍率 | 1 | 4 | 18 | 65 |
| EXP・ゴールド倍率 | 1 | 1.5 | 3.5 | 20 |
| 装備性能倍率 | 1 | 4.5 | 14 | 80 |
| 価格倍率 | 1 | 5 | 22 | 200 |
| 推奨Lv | 1 | 10 | 25 | 52 |

草原の敵の基礎値（旧 → 新）: スライム HP20・atk5 → HP16・atk4、大ネズミ 25/6 → 20/5、角ウサギ 30/8 → 24/6、ゴブリン 45/10 → 32/7、野犬 60/12 → 40/8。
草原の EXP（スライム/ネズミ/ウサギ/ゴブリン/野犬）: 5/6/8/12/16 → 3/4/5/7/10（解放ペースを 100〜250 戦に収めるため）。

### 計測方法（シミュレーター）
`domain/src/commonTest/.../balance/BalanceSimulator.kt` が、本物の `GameEngine.execute` を `Random(seed)` と偽の時計（クールタイムを飛ばす）で回す貪欲な自動プレイヤー。

- 戦闘: 今の DLv で戦い、直近 10 戦（最低 6 戦）の勝率が 70% 以上なら 1 DLv 上げる。70% 未満（最低 5 戦）または 3 連敗なら 1 DLv 下げる
- エリア: 解放済みで推奨Lvに達した最も先のエリアへ移動する
- 買い物: 枠ごとに買える最高価格の未所持品を買って装備する。全枠で最良品を持っていれば、余りで強化する（+10 まで）
- 転職: マスターしたら、未マスターの職業へ（解放済みの上位職を優先）
- 計測: エリアごとの解放戦数・解放時Lv・突入時の DLv1 勝率（300 回の模擬戦）・実戦勝率・ドロップ・職業マスター・装備の稼ぎ切り戦数

`BalanceSimulationTest`（seed 1〜8、実行は約 2 秒）が下の目標を判定する。

### 目標と実測値（seed 1〜8、中央値/平均）
| 目標 | 許容範囲 | 実測 |
|---|---|---|
| 最初の草原 DLv1 の戦闘に勝つ確率 | 90% 以上 | 95.4%（2,000 seed） |
| 森の解放（草原 DLv10 クリア） | 100〜250 戦 | 165 戦（Lv10） |
| 洞窟の解放 | 累計 500〜1,200 戦 | 796 戦（Lv25） |
| 火山の解放 | 累計 1,500〜4,000 戦 | 2,089 戦（Lv52） |
| 新エリア突入時の DLv1 勝率 | 70% 以上 | 森 86.7% / 洞窟 98.4% / 火山 92.6% |
| 解放時のキャラLv ≒ 推奨Lv | 推奨Lv の 0.7〜1.5 倍 | 10 / 25 / 52（推奨Lvと一致） |
| 下位職 4 つのマスター | 火山の解放まで | 戦士 167 / 騎士 255 / 盗賊 345 / 僧侶 530 戦（上位職は剣聖 434 / 聖騎士 622 戦） |
| ドロップ限定品（滞在中の平均入手数） | 1 個以上 | 草原 2.4 / 森 19.6 / 洞窟 48.0 / 火山 14.5（火山は突入後 400 戦） |
| ショップ装備を稼ぎ切る戦数 | そのエリアで 150 戦以内 | 下の表 |

### エリア別の結果
| エリア | 解放戦数 | 解放時Lv | 推奨Lv | 突入時DLv1勝率 | 実戦勝率 | 到達DLv | 滞在戦数 | ドロップ限定品/滞在 |
|---|---|---|---|---|---|---|---|---|
| はじまりの草原 | - | - | 1 | 96.1% | 78.8% | 10 | 172 | 2.4 |
| 迷いの森 | 165 | 10 | 10 | 86.7% | 74.1% | 10 | 618 | 19.6 |
| 地底洞窟 | 796 | 25 | 25 | 98.4% | 72.4% | 10 | 1,292 | 48.0 |
| 灼熱の火山 | 2,089 | 52 | 52 | 92.6% | 74.1% | 7 | 401 | 14.5 |

（実戦勝率が 70% 台なのは、ボットが「勝率 70% を保てる最高の DLv」を探し続けるため。到達DLv は、各エリアの滞在中にボットが踏んだ最高 DLv で、
森・洞窟は次のエリアの解放条件（DLv10）に達した時点で次へ進むため 10 前後。）

### 装備を稼ぎ切るまでの戦数（そのエリアに入ってから、累計の獲得ゴールドが価格に届くまで）
| 商品 | 価格 | 戦数 |
|---|---|---|
| 革の鎧 / 幸運のお守り（草原） | 80G / 60G | 16 / 12 |
| 鉄の剣 / 鎖かたびら / 風の指輪（森） | 250G / 400G / 300G | 36 / 58 / 45 |
| 鋼の剣 / ミスリルの鎧 / 宝石のペンダント（洞窟） | 1,100G / 1,760G / 1,320G | 56 / 87 / 66 |
| 炎の剣 / 竜鱗の鎧 / 不死鳥の羽飾り（火山） | 10,000G / 16,000G / 12,000G | 74 / 116 / 90 |

### 残る課題・留意点
- 火山は最終エリアで、突入後はキャラLvが緩やかに伸びる（400 戦で Lv52 → 約 70）。エンドレス部分の成長ペースは今回は目標にしていない
- 洞窟の突入時勝率（98%）は森・火山より高い。洞窟の敵倍率（18）を上げれば揃うが、火山の解放（現在 2,089 戦）が遅くなる
- 結果は seed とボットの方針に依存する。方針（勝率しきい値・買い物順）を変えると数値は動くので、判定は許容範囲の幅で持っている
