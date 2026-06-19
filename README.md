# VillagerScope

**日本語** | [English](README.en.md)

村人を**見るだけ**で、その村人が提供している取引（コスト → 結果）を、その村人の**頭上に実際のアイテムアイコンで**一覧表示する Paper プラグインです。
いちいち右クリックして取引画面を開かなくても、「この村人は何を売り買いしてくれるか」が一目で分かります。

```text
   ╭───────────────────────────╮
   │          司書 Lv3          │   ← 見ている村人の頭上に浮かぶ
   │ [紙]×24         →  [緑]     │      （[..] は実アイテムの3Dモデル）
   │ [緑]×9 [本]     →  [本] [入れ食い III]
   │ [本]×4          →  [緑]×5   │
   │ [ガ]×4          →  [緑]     │
   ╰─────────────┬─────────────╯
                 ▼
               村人
```

<!-- スクリーンショットは撮影後に docs/screenshot.png として追加してください -->

- **タイトル**（`司書 Lv3`）… 見ている村人の職業とレベル（行商人は「行商人」）
- **各行**（`アイコン → アイコン`）… 左が支払うアイテム、右が手に入るアイテム。アイコンは実アイテムの3Dモデルで、`×N` は個数
- **エンチャントの本**… アイコンだけでは中身が分からないため、`[入れ食い III]` のように付与される魔法とレベルを文字併記
- **在庫切れ**… 補充待ちの取引はアイコンを減光＋文字に打ち消し線で表示
- **リソースパック不要**… 表示エンティティで描くため、プレイヤーは何もインストール不要（バニラのままで OK）

---

## 背景・目的

村人の取引を確認するには、ふつう村人を右クリックして取引画面を開く必要があります。
取引相手を探すたびに一人ずつ画面を開いて確認するのは、交易所づくりや厳選作業では地味に面倒です。

VillagerScope はプレイヤーの視線を判定し、村人を見ている間だけ、その村人の頭上に取引一覧をアイコンで表示します。
クライアント MOD でもリソースパックでもなく**サーバー側プラグイン＋表示エンティティ**で描くため、**プレイヤーは何もインストール不要**で、バニラクライアントのまま機能します。

---

## 動作要件

| 項目 | バージョン |
| --- | --- |
| サーバー | Paper **26.1.2**（build 69 で確認） |
| Java | **25**（25.0.x で確認） |
| ビルド | JDK 25 + Maven（`brew install openjdk@25 maven`） |
| 依存プラグイン | **なし** |
| クライアント | **バニラのままで可**（MOD 不要） |

> この jar 1個だけで動作します。追加ライブラリ・別プラグインは不要です（`paper-api` は `provided` スコープ＝サーバーが実行時に提供）。

---

## 使い方

1. サーバーの `plugins/` に jar を置いて再起動（→ [サーバーへの配置](#サーバーへの配置)）。
2. ゲーム内で**村人（または行商人）に視線を合わせる**だけ。その村人の頭上に取引一覧が表示されます。
3. 表示が不要なときは `/villagerscope off`、再び有効化は `/villagerscope on`。

### 表示の見かた

| 表示 | 意味 |
| --- | --- |
| `司書 Lv3` | ホログラム上部のタイトル。職業 + 取引レベル（1〜5） |
| `[アイコン]×24 → [アイコン]` | 1取引。左が**支払う**アイテム、右が**手に入る**アイテム（アイコンは実アイテムの3Dモデル） |
| `[アイコン] + [アイコン] → …` | 支払いが2種類の取引は ` + ` で連結 |
| `… [入れ食い III]` | エンチャントの本の付与魔法とレベル（アイコンの右に文字併記） |
| アイコン減光＋打ち消し線 | **在庫切れ**（その村人が補充するまで取引不可） |
| `取引なし` | 無職・ニトウィット・子供など、取引を持たない村人 |
| `ほか N 件` | 取引が多い場合、表示上限（10行）を超えた分の件数 |

> アイテム名はクライアントの言語で表示されます（日本語クライアントなら日本語）。

---

## コマンド仕様

| コマンド | 説明 | 実行者 | 権限 |
| --- | --- | --- | --- |
| `/villagerscope` | 表示の ON/OFF をトグル | プレイヤーのみ | `villagerscope.use` |
| `/villagerscope on` | 表示を ON | プレイヤーのみ | `villagerscope.use` |
| `/villagerscope off` | 表示を OFF（表示中なら即座に消える） | プレイヤーのみ | `villagerscope.use` |
| `/villagerscope status` | 現在の ON/OFF を確認 | プレイヤーのみ | `villagerscope.use` |

- 表示の ON/OFF は**プレイヤーごと**に切り替わります（他人には影響しません）。
- 既定は全員 **ON**。ON/OFF の状態はサーバー稼働中のみ保持され、**サーバー再起動で ON に戻ります**。
- `/villagerscope` のタブ補完で `on` / `off` / `status` が候補に出ます。

---

## 権限

| 権限ノード | 既定 | 説明 |
| --- | --- | --- |
| `villagerscope.use` | `true`（全員） | 取引表示の受信と `/villagerscope` の使用を許可 |

既定では全プレイヤーに表示されます（LuckPerms 等の追加設定は不要）。
特定プレイヤー／グループで**無効**にしたい場合のみ、対象に該当ノードを `false` に設定します。

```bash
# 例: あるグループで取引表示を無効化
lp group default permission set villagerscope.use false
# 例: あるユーザーで取引表示を無効化
lp user <name> permission set villagerscope.use false
```

---

## 仕組み（技術メモ）

- **視線判定**: 一定間隔（4 tick ≒ 0.2 秒）で全オンラインプレイヤーをループし、目線から `getTargetEntity(8)` でエンティティへレイを飛ばします。手前にブロックがあれば遮られ（壁越しには出ません）、当たった先が村人（`AbstractVillager`＝村人・行商人）なら表示します。
- **取引の取得**: `AbstractVillager#getRecipes()` で `MerchantRecipe` の一覧を取得し、`getIngredients()`（支払い・1〜2個）／`getResult()`（結果）を表示モデルに整形します。`getUses() >= getMaxUses()` の取引は在庫切れとしてアイコン減光＋文字に打ち消し線にします。
- **表示**: プレイヤーごとに、見ている村人の頭上へ表示エンティティ（`ItemDisplay`＝アイコン、`TextDisplay`＝`×N`・`→`・付与魔法）をスポーンします。全要素を同一アンカー座標に置き、`Display.Billboard.CENTER` ＋ `Transformation`（translation/scale）でグリッド配置するため、どの角度から見ても平面カードとして崩れず常にプレイヤーへ正対します。アイコンは `ItemStack` をそのまま描くため**リソースパック不要**で、エンチャント名は `Enchantment#displayName(level)` を使うためクライアントの言語でローカライズされます。
- **個人表示**: 表示エンティティはスポーン時に所有者以外へ `Player#hideEntity(...)` し、後から参加したプレイヤーにも隠します。これにより `/villagerscope` の ON/OFF と「見ている本人にだけ表示」をプレイヤー単位で保ちます。
- **差分更新・追従**: 表示中の村人の取引内容から安価なシグネチャを作り、**変化があったときだけ**ホログラムを作り直します。村人が動いたらアンカーごと teleport（`setTeleportDuration` で補間）して滑らかに追従します。同じ村人を見続けている間は再生成せず、ちらつきと負荷を抑えます。
- **猶予表示**: 視線が外れても約 0.6 秒は表示を残し、照準のわずかなブレで表示が点滅しないようにしています。視線を外し続けるか `/villagerscope off` で消えます。
- **後始末**: 視線喪失・退出・OFF・`onDisable` で表示エンティティを撤去します。さらに各エンティティへ識別タグを付け、`onEnable` 時に全ワールドを走査して取り残し（クラッシュ時の孤児）を掃除します。
- **負荷について**: コストは「頻度 × オンライン人数 × 1 回のレイトレース」が主体です（頻度は秒間 5 回、レイは最大 8 ブロック）。表示エンティティの再生成は内容が変わった瞬間だけなので、村人を眺めている間の追加コストはごくわずかです。レイトレースとエンティティ操作はワールド状態を扱うためメインスレッド（`runTaskTimer`）で実行します。
- **村人の改変は一切行いません**（読み取り専用）。

> 視線判定は Bukkit のグローバルスケジューラ（`runTaskTimer`）で動くため、対象は **Paper**（非 Folia）です。

---

## ビルド

JDK 25 と Maven が必要です（未導入なら `brew install openjdk@25 maven`）。
付属の `deploy.sh` でビルドできます（**Docker 不要**）。

```bash
./deploy.sh
```

生成物: `target/VillagerScope-1.0.0.jar`

`deploy.sh` は内部で JDK 25 を指定して `mvn clean package` を実行します。
別の場所の JDK を使う場合は `JAVA_HOME=/path/to/jdk25 ./deploy.sh` で上書きできます。直接ビルドするなら:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

---

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手は次の 2 通り（A・B）です。Docker（itzg/minecraft-server）を使う場合は、後述の「Docker Compose で自動ダウンロード」も利用できます。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/minecraft-murabito-mieru/releases) から最新の `VillagerScope-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/minecraft-murabito-mieru --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/VillagerScope-1.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/VillagerScope-1.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/VillagerScope-1.0.0.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

### Docker Compose（itzg/minecraft-server）で自動ダウンロード

[`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) イメージを使う場合は、jar を手元に用意しなくても **`PLUGINS` 環境変数にリリースの URL を並べるだけ**で、起動時に自動ダウンロードして `plugins/` に配置できます。

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.0.0/VillagerScope-1.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。他プラグインと併用する例:

```yaml
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/DiscordSRV/DiscordSRV/releases/download/v1.30.5/DiscordSRV-Build-1.30.5.jar
        https://github.com/astail/minecraft-onpu/releases/download/v1.0.0/NoteScope-1.0.0.jar
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.0.0/VillagerScope-1.0.0.jar
```

- バージョンを更新したら、URL の `v1.0.0` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v1.1.0/VillagerScope-1.1.0.jar`）。
- VillagerScope は依存プラグインなしで動くため、URL 1 行だけで導入できます。

起動ログに以下が出れば成功です。

```text
[VillagerScope] VillagerScope を有効化しました。村人を見ると取引が頭上にアイコン表示されます。
```

---

## プロジェクト構成

```text
.
├── pom.xml
├── deploy.sh
├── README.md
└── src/main/
    ├── java/io/github/astail/villagerscope/
    │   ├── VillagerScopePlugin.java   # 本体（コマンド登録・視線タスク起動・参加/退出時の片付け・起動時スイープ）
    │   ├── VillagerLookTask.java      # 視線判定 → ホログラムの表示/非表示
    │   ├── TradeHologram.java         # プレイヤー別の頭上アイコン表示エンティティ管理（差分更新・追従・猶予・後始末）
    │   ├── TradeFormatter.java        # 取引データ → 表示モデル（行・付与魔法・シグネチャ）の整形
    │   └── VillagerScopeCommand.java  # /villagerscope（ON/OFF トグル）
    └── resources/plugin.yml
```

> パッケージ名（`io.github.astail.villagerscope`）/ `VillagerScope` / コマンド名は任意でリネーム可能です（pom.xml・各 `package`・`plugin.yml` を揃えて変更）。

---

## 注意点

- **表示が出ない場合**: 村人にきちんと視線が合っているか（最大 8 ブロック・手前にブロックがないか）、`/villagerscope status` が ON か、`villagerscope.use` 権限があるかを確認してください。
- **スコアボードと競合しません**: 表示エンティティで描くため、スコアボードのサイドバーを使う他プラグインと併用しても干渉しません。
- **取引が多い村人**: 表示は最大 10 行までで、超過分は「ほか N 件」とまとめます。
- **価格について**: 表示は取引の**基準コスト**です。需要（売れ筋による値上がり）や「村の英雄」効果・ゾンビ治療による値引きは反映しません。
- **行商人にも対応**: 村人だけでなく行商人（Wandering Trader）の取引も同じ要領で表示します。
- `paper-api` の build 番号はサーバー更新に追従可能です（例: `26.1.2.build.70-stable`）。
