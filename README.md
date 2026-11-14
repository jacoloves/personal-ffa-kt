# personal-ffa-kt

ボタンを押すだけで自動戦闘が進む、ブラウザ向けの放置系 RPG(いわゆるポチゲー)です。
Kotlin/JS + Compose HTML で作られ、GitHub Pages で公開されています。1 人用で、通信はありません。

## 遊ぶ

https://jacoloves.github.io/personal-ffa-kt/

セーブはブラウザの localStorage に保存されます(設定タブから書き出し・初期化もできます)。

## ゲームの流れ

1. エリアとダンジョンLv(DLv)を選び、「戦う」を押すと 1 対 1 の戦闘が自動で決着します。
2. 勝つと経験値(キャラ・職業)・ゴールド・装備ドロップが手に入ります。
3. キャラLvが上がり、職業をマスターするとステータスが永続的に強化されます。
4. ショップで装備を買い、転職し、DLv 10 をクリアして次のエリアを解放していきます。
   草原 → 森 → 洞窟 → 火山。最終目標はエンドレスの記録更新です。
5. 戦闘のあとは待ち時間(クールタイム)があります。初期値は 90 秒で、設定タブで 1〜180 秒に変更できます。
   勝っても負けても同じ待ち時間で、負けた戦闘は報酬がもらえません。

## 開発

必要なもの: JDK 21(Gradle Wrapper が Gradle を取得します。Node.js は Kotlin が自動で用意します)。

```sh
./gradlew check                      # domain のテストを Node.js 上で実行
./gradlew :ui:jsBrowserDevelopmentRun # 開発サーバを起動してブラウザで確認
./gradlew :ui:jsBrowserDistribution  # 公開用ビルド(ui/build/dist/js/productionExecutable)
```

## 構成

| パス | 内容 |
|------|------|
| `domain/` | ゲームのルール・状態・戦闘・成長・セーブ(UI 非依存の純 Kotlin。TDD で開発) |
| `ui/` | Compose HTML による画面(戦闘・ステータス・装備・転職・ショップ・記録・設定) |
| `describe/` | 設計書(`domain-design.md`、`ui-design.md`) |
| `plan.md` | 仕様書・決定事項・フェーズ計画 |
| `.github/workflows/` | `ci.yml`(PR で `check`)、`deploy.yml`(main への push で Pages へ公開) |

## デプロイ

`main` にマージされると `deploy.yml` が `:ui:jsBrowserDistribution` の成果物を GitHub Pages へ公開します。
リポジトリの Pages 設定は「Source: GitHub Actions」にしてください。
