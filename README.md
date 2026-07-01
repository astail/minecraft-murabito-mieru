# VillagerScope

**日本語** | [English](README.en.md)

村人を**見るだけ**で、その村人が提供している取引（コスト → 結果）をサイドバーに一覧表示する Paper プラグインです。
いちいち右クリックして取引画面を開かなくても、「この村人は何を売り買いしてくれるか」が一目で分かります。

```text
            司書 Lv3
 紙 ×24             → エメラルド
 エメラルド ×9 + 本  → エンチャントの本 [入れ食い III]
 本 ×4             → エメラルド ×5
 ガラス ×4          → エメラルド
```

<!-- スクリーンショットは撮影後に docs/screenshot.png として追加してください -->

- **タイトル**（`司書 Lv3`）… 見ている村人の職業とレベル（行商人は「行商人」）
- **各行**（`コスト → 結果`）… 左が支払うアイテム、右が手に入るアイテム。`×N` は個数
- **エンチャントの本**… `[入れ食い III]` のように付与される魔法とレベルも表示
- **在庫切れ**… 補充待ちの取引は灰色＋打ち消し線で表示

---

## 背景・目的

村人の取引を確認するには、ふつう村人を右クリックして取引画面を開く必要があります。
取引相手を探すたびに一人ずつ画面を開いて確認するのは、交易所づくりや厳選作業では地味に面倒です。

VillagerScope はプレイヤーの視線を判定し、村人を見ている間だけサイドバーに取引一覧を表示します。
クライアント MOD ではなくサーバー側プラグインなので、**プレイヤーは何もインストール不要**で、バニラクライアントのまま機能します。

---

## 動作要件

| 項目 | バージョン |
| --- | --- |
| サーバー | Paper **26.2**（experimental チャンネル） |
| Java | **25**（25.0.x で確認） |
| ビルド | JDK 25 + Maven（`brew install openjdk@25 maven`） |
| 依存プラグイン | **なし** |
| クライアント | **バニラのままで可**（MOD 不要） |

> この jar 1個だけで動作します。追加ライブラリ・別プラグインは不要です（`paper-api` は `provided` スコープ＝サーバーが実行時に提供）。

---

## 使い方

1. サーバーの `plugins/` に jar を置いて再起動（→ [サーバーへの配置](#サーバーへの配置)）。
2. ゲーム内で**村人（または行商人）に視線を合わせる**だけ。サイドバーに取引一覧が表示されます。
3. 表示が不要なときは `/villagerscope off`、再び有効化は `/villagerscope on`。

### 表示の見かた

| 表示 | 意味 |
| --- | --- |
| `司書 Lv3` | サイドバーのタイトル。職業 + 取引レベル（1〜5） |
| `紙 ×24 → エメラルド` | 1取引。左が**支払う**アイテム、右が**手に入る**アイテム |
| `エメラルド ×9 + 本 → …` | 支払いが2種類の取引は ` + ` で連結 |
| `… [入れ食い III]` | エンチャントの本の付与魔法とレベル |
| 灰色＋打ち消し線 | **在庫切れ**（その村人が補充するまで取引不可） |
| `取引なし` | 無職・ニトウィット・子供など、取引を持たない村人 |

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
- **取引の取得**: `AbstractVillager#getRecipes()` で `MerchantRecipe` の一覧を取得し、`getIngredients()`（支払い・1〜2個）／`getResult()`（結果）を行に整形します。`getUses() >= getMaxUses()` の取引は在庫切れとして灰色＋打ち消し線にします。
- **表示**: プレイヤーごとに専用スコアボードを用意し、サイドバー枠（`DisplaySlot.SIDEBAR`）へ表示します。各行は `Score#customName(Component)` で Adventure の Component をそのまま描画し、`Objective#numberFormat(NumberFormat.blank())` で右端の赤いスコア数字を隠します。アイテム名は翻訳可能な `ItemStack#effectiveName()`、エンチャント名は `Enchantment#displayName(level)` を使うため、クライアントの言語でローカライズされます。
- **差分更新**: 表示中の村人の取引内容から安価なシグネチャを作り、**変化があったときだけ**スコアボードを作り直します。同じ村人を見続けている間は再描画せず、ちらつきと負荷を抑えます。
- **猶予表示**: 視線が外れても約 0.6 秒は表示を残し、照準のわずかなブレでサイドバーが点滅しないようにしています。視線を外し続けるか `/villagerscope off` で消えます。
- **負荷について**: コストは「頻度 × オンライン人数 × 1 回のレイトレース」が主体です（頻度は秒間 5 回、レイは最大 8 ブロック）。スコアボードの再構築は内容が変わった瞬間だけなので、村人を眺めている間の追加コストはごくわずかです。レイトレースとエンティティ読み取りはワールド状態を扱うためメインスレッド（`runTaskTimer`）で実行します。
- **村人の改変は一切行いません**（読み取り専用）。

> 視線判定は Bukkit のグローバルスケジューラ（`runTaskTimer`）で動くため、対象は **Paper**（非 Folia）です。

---

## ビルド

JDK 25 と Maven が必要です（未導入なら `brew install openjdk@25 maven`）。
付属の `deploy.sh` でビルドできます（**Docker 不要**）。

```bash
./deploy.sh
```

生成物: `target/VillagerScope-1.2.0.jar`

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

[Releases](https://github.com/astail/mc-murabito-mieru/releases) から最新の `VillagerScope-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/mc-murabito-mieru --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/VillagerScope-1.2.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/VillagerScope-1.2.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/VillagerScope-1.2.0.jar <コンテナ名>:/data/plugins/
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
        https://github.com/astail/mc-murabito-mieru/releases/download/v1.2.0/VillagerScope-1.2.0.jar
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
        https://github.com/astail/mc-murabito-mieru/releases/download/v1.2.0/VillagerScope-1.2.0.jar
```

- バージョンを更新したら、URL の `v1.2.0` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v1.3.0/VillagerScope-1.3.0.jar`）。
- VillagerScope は依存プラグインなしで動くため、URL 1 行だけで導入できます。

起動ログに以下が出れば成功です。

```text
[VillagerScope] VillagerScope を有効化しました。村人を見ると取引がサイドバーに表示されます。
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
    │   ├── VillagerScopePlugin.java   # 本体（コマンド登録・視線タスク起動・退出時の片付け）
    │   ├── VillagerLookTask.java      # 視線判定 → サイドバーの表示/非表示
    │   ├── TradeSidebar.java          # プレイヤー別スコアボード管理（差分更新・猶予表示）
    │   ├── TradeFormatter.java        # 取引データ → 表示用 Component の整形
    │   └── VillagerScopeCommand.java  # /villagerscope（ON/OFF トグル）
    └── resources/plugin.yml
```

> パッケージ名（`io.github.astail.villagerscope`）/ `VillagerScope` / コマンド名は任意でリネーム可能です（pom.xml・各 `package`・`plugin.yml` を揃えて変更）。

---

## 注意点

- **表示が出ない場合**: 村人にきちんと視線が合っているか（最大 8 ブロック・手前にブロックがないか）、`/villagerscope status` が ON か、`villagerscope.use` 権限があるかを確認してください。
- **サイドバーの競合**: 1プレイヤーが表示できるサイドバーは1つだけです。スコアボードのサイドバーを使う他プラグインと併用すると、村人を見ている間は本プラグインの表示が優先され、見終わると**メインスコアボード**に戻ります（他プラグインのサイドバーには自動復帰しません）。
- **価格について**: 表示は取引の**基準コスト**です。需要（売れ筋による値上がり）や「村の英雄」効果・ゾンビ治療による値引きは反映しません。
- **行商人にも対応**: 村人だけでなく行商人（Wandering Trader）の取引も同じ要領で表示します。
- `paper-api` の build 番号はサーバー更新に追従可能です（例: `26.2.build.41-alpha`）。
