# VillagerScope

[日本語](README.md) | **English**

A Paper plugin that shows a villager's trades (cost → result) on the sidebar just by **looking at it** —
so you can tell what a villager buys and sells without opening the trading screen.

```text
            司書 Lv3
 紙 ×24             → エメラルド
 エメラルド ×9 + 本  → エンチャントの本 [入れ食い III]
 本 ×4             → エメラルド ×5
 ガラス ×4          → エメラルド
```

<!-- Add docs/screenshot.png once captured -->

- **Title** (`司書 Lv3`) … the profession and level of the villager you're looking at (wandering traders show "行商人")
- **Each row** (`cost → result`) … left is what you pay, right is what you get. `×N` is the amount
- **Enchanted books** … the stored enchantment and level are shown, e.g. `[Lure III]`
- **Out of stock** … trades awaiting restock are shown in gray with a strikethrough

> Item names are localized to the client's language (this example shows a Japanese client).

---

## Background & Purpose

Normally you have to right-click a villager and open its trading screen to see what it offers.
Opening them one by one while building a trading hall or hunting for a specific deal is tedious.

VillagerScope ray-traces the player's line of sight and shows the trade list on the sidebar only while they're looking at a villager.
Because it's a server-side plugin (not a client mod), **players need to install nothing** — it works on a vanilla client.

---

## Requirements

| Item | Version |
| --- | --- |
| Server | Paper **26.1.2** (verified on build 69) |
| Java | **25** (verified on 25.0.x) |
| Build | JDK 25 + Maven (`brew install openjdk@25 maven`) |
| Dependencies | **None** |
| Client | **Vanilla is fine** (no mod required) |

> This single jar is all you need. No extra libraries or plugins (`paper-api` is `provided` — supplied by the server at runtime).

---

## Usage

1. Drop the jar into the server's `plugins/` and restart (→ [Deploying to a Server](#deploying-to-a-server)).
2. In-game, just **aim your crosshair at a villager** (or a wandering trader) — the trade list appears on the sidebar.
3. Turn it off with `/villagerscope off`, back on with `/villagerscope on`.

### Reading the display

| Field | Meaning |
| --- | --- |
| `司書 Lv3` | Sidebar title: profession + trade level (1–5) |
| `紙 ×24 → エメラルド` | One trade. Left is what you **pay**, right is what you **get** |
| `エメラルド ×9 + 本 → …` | Trades that cost two items are joined with ` + ` |
| `… [Lure III]` | Stored enchantment and level for enchanted books |
| Gray + strikethrough | **Out of stock** (locked until the villager restocks) |
| `取引なし` | Villagers with no trades (unemployed, nitwit, baby) |

---

## Commands

| Command | Description | Who | Permission |
| --- | --- | --- | --- |
| `/villagerscope` | Toggle the display on/off | Players only | `villagerscope.use` |
| `/villagerscope on` | Turn the display on | Players only | `villagerscope.use` |
| `/villagerscope off` | Turn the display off (hides immediately if showing) | Players only | `villagerscope.use` |
| `/villagerscope status` | Show the current on/off state | Players only | `villagerscope.use` |

- The on/off state is **per player** (it does not affect others).
- Default is **on** for everyone. State is kept only while the server is running and **resets to on after a restart**.
- Tab completion on `/villagerscope` suggests `on` / `off` / `status`.

---

## Permissions

| Permission node | Default | Description |
| --- | --- | --- |
| `villagerscope.use` | `true` (everyone) | Allows receiving the display and using `/villagerscope` |

By default every player sees the display (no LuckPerms setup needed).
Only if you want to **disable** it for a specific player/group, set the node to `false` for them.

```bash
# e.g. disable the display for a group
lp group default permission set villagerscope.use false
# e.g. disable the display for a user
lp user <name> permission set villagerscope.use false
```

---

## How It Works (technical notes)

- **Line-of-sight check**: every 4 ticks (~0.2 s) it loops over online players and casts `getTargetEntity(8)` from the eye. Blocks in front occlude it (it won't show through walls), and if the hit entity is an `AbstractVillager` (villager or wandering trader), it shows the trades.
- **Reading trades**: `AbstractVillager#getRecipes()` returns the `MerchantRecipe` list; each row is built from `getIngredients()` (cost, 1–2 items) and `getResult()` (result). A trade with `getUses() >= getMaxUses()` is out of stock and rendered gray with a strikethrough.
- **Display**: each player gets a dedicated scoreboard shown in the `DisplaySlot.SIDEBAR` slot. Each line is drawn straight from an Adventure Component via `Score#customName(Component)`, and `Objective#numberFormat(NumberFormat.blank())` hides the red score numbers on the right. Item names use the translatable `ItemStack#effectiveName()` and enchantments use `Enchantment#displayName(level)`, so everything is localized to the client's language.
- **Diff updates**: a cheap signature is computed from the looked-at villager's trade data, and the scoreboard is rebuilt **only when it changes**. While you keep looking at the same villager, nothing is re-rendered — avoiding flicker and overhead.
- **Grace period**: the display lingers ~0.6 s after you look away, so small aim jitter doesn't make the sidebar flicker. It clears when you keep looking away or run `/villagerscope off`.
- **Performance**: the cost is mainly `interval × online players × one ray trace` (5×/second, up to 8 blocks). Scoreboard rebuilds happen only the moment content changes, so staring at a villager adds very little. The ray trace and entity reads touch world state, so they run on the main thread (`runTaskTimer`).
- **It never modifies villagers** (read-only).

> The line-of-sight task runs on Bukkit's global scheduler (`runTaskTimer`), so the target is **Paper** (not Folia).

---

## Build

JDK 25 and Maven are required (`brew install openjdk@25 maven` if missing).
Build with the bundled `deploy.sh` (**no Docker needed**):

```bash
./deploy.sh
```

Output: `target/VillagerScope-1.2.0.jar`

`deploy.sh` runs `mvn clean package` with JDK 25 internally.
Override with another JDK via `JAVA_HOME=/path/to/jdk25 ./deploy.sh`, or build directly:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

---

## Deploying to a Server

Put the jar in the server's `plugins/` and restart. There are two ways to get the jar (A/B); if you run Docker (itzg/minecraft-server), the "Docker Compose auto-download" below is also handy.

### A. Use a release build (no build needed, recommended)

Download the latest `VillagerScope-<version>.jar` from [Releases](https://github.com/astail/minecraft-murabito-mieru/releases). No JDK or Maven required.

```bash
gh release download --repo astail/minecraft-murabito-mieru --pattern '*.jar'
```

### B. Build it yourself

Follow [Build](#build) to produce `target/VillagerScope-1.2.0.jar`.

### Placement

```bash
# bind mount (copy to the host-side plugins dir)
cp target/VillagerScope-1.2.0.jar /path/to/data/plugins/
docker restart <container>

# named volume etc. (copy directly into the container)
docker cp target/VillagerScope-1.2.0.jar <container>:/data/plugins/
docker restart <container>
```

### Docker Compose (itzg/minecraft-server) auto-download

If you use the [`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) image, you don't have to place the jar yourself — just **list the release URL in the `PLUGINS` environment variable** and it's downloaded into `plugins/` on startup.

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
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.2.0/VillagerScope-1.2.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` accepts multiple newline-separated URLs. Example alongside other plugins:

```yaml
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/DiscordSRV/DiscordSRV/releases/download/v1.30.5/DiscordSRV-Build-1.30.5.jar
        https://github.com/astail/minecraft-onpu/releases/download/v1.0.0/NoteScope-1.0.0.jar
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.2.0/VillagerScope-1.2.0.jar
```

- When you cut a new release, update the `v1.2.0` and filename in the URL (e.g. `.../download/v1.3.0/VillagerScope-1.3.0.jar`).
- VillagerScope has no dependencies, so a single URL line is enough.

If you see this in the startup log, it worked:

```text
[VillagerScope] VillagerScope を有効化しました。村人を見ると取引がサイドバーに表示されます。
```

---

## Project Layout

```text
.
├── pom.xml
├── deploy.sh
├── README.md
└── src/main/
    ├── java/io/github/astail/villagerscope/
    │   ├── VillagerScopePlugin.java   # entry point (commands, task startup, quit cleanup)
    │   ├── VillagerLookTask.java      # line-of-sight check → show/hide the sidebar
    │   ├── TradeSidebar.java          # per-player scoreboard management (diff updates, grace)
    │   ├── TradeFormatter.java        # trade data → display Components
    │   └── VillagerScopeCommand.java  # /villagerscope (on/off toggle)
    └── resources/plugin.yml
```

> The package name (`io.github.astail.villagerscope`), `VillagerScope`, and the command name can all be renamed (change pom.xml, each `package`, and `plugin.yml` together).

---

## Notes

- **Nothing showing?** Check that your crosshair is actually on the villager (within 8 blocks, nothing in front), that `/villagerscope status` is on, and that you have the `villagerscope.use` permission.
- **Sidebar conflicts**: a player can only show one sidebar at a time. If another plugin uses the scoreboard sidebar, this plugin takes over while you look at a villager and returns you to the **main scoreboard** afterward (it does not restore another plugin's sidebar automatically).
- **About prices**: the display shows each trade's **base cost**. It does not reflect demand-based price increases or discounts from the Hero of the Village effect / curing a zombie villager.
- **Wandering traders too**: trades of wandering traders are shown the same way as villagers.
- The `paper-api` build number can track server updates (e.g. `26.1.2.build.70-stable`).
