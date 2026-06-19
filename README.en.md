# VillagerScope

[日本語](README.md) | **English**

A Paper plugin that shows a villager's trades (cost → result) **above its head as real item icons** just by **looking at it** —
so you can tell what a villager buys and sells without opening the trading screen.

```text
   ╭───────────────────────────╮
   │          司書 Lv3          │   ← floats above the villager you look at
   │ [paper]×24      →  [emrld] │      ([..] are real 3D item models)
   │ [emrld]×9 [book] → [book] [Lure III]
   │ [book]×4        →  [emrld]×5
   │ [glass]×4       →  [emrld] │
   ╰─────────────┬─────────────╯
                 ▼
              villager
```

<!-- Add docs/screenshot.png once captured -->

- **Title** (`司書 Lv3`) … the profession and level of the villager you're looking at (wandering traders show "行商人")
- **Each row** (`icon → icon`) … left is what you pay, right is what you get. Icons are real 3D item models; `×N` is the amount
- **Enchanted books** … since the icon alone can't tell you the contents, the stored enchantment and level are shown as text, e.g. `[Lure III]`
- **Out of stock** … trades awaiting restock are dimmed with a strikethrough on the text
- **No resource pack** … rendered with display entities, so players install nothing (vanilla client is fine)

> Item names are localized to the client's language (this example shows a Japanese client).

---

## Background & Purpose

Normally you have to right-click a villager and open its trading screen to see what it offers.
Opening them one by one while building a trading hall or hunting for a specific deal is tedious.

VillagerScope ray-traces the player's line of sight and shows the trade list above the villager only while they're looking at it.
Because it's a server-side plugin with display entities (not a client mod, not a resource pack), **players need to install nothing** — it works on a vanilla client.

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
2. In-game, just **aim your crosshair at a villager** (or a wandering trader) — the trade list appears above its head.
3. Turn it off with `/villagerscope off`, back on with `/villagerscope on`.

### Reading the display

| Field | Meaning |
| --- | --- |
| `司書 Lv3` | Hologram title: profession + trade level (1–5) |
| `[icon]×24 → [icon]` | One trade. Left is what you **pay**, right is what you **get** (icons are real 3D item models) |
| `[icon] + [icon] → …` | Trades that cost two items are joined with ` + ` |
| `… [Lure III]` | Stored enchantment and level for enchanted books (shown as text next to the icon) |
| Dimmed icon + strikethrough | **Out of stock** (locked until the villager restocks) |
| `取引なし` | Villagers with no trades (unemployed, nitwit, baby) |
| `ほか N 件` | When a villager has many trades, the count beyond the 10-row display limit |

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
- **Reading trades**: `AbstractVillager#getRecipes()` returns the `MerchantRecipe` list; each row is built from `getIngredients()` (cost, 1–2 items) and `getResult()` (result). A trade with `getUses() >= getMaxUses()` is out of stock, with the icon dimmed and a strikethrough on the text.
- **Display**: for each player, display entities are spawned above the villager they look at (`ItemDisplay` for icons, `TextDisplay` for `×N`, `→`, and enchantments). All elements share one anchor location and are laid out on a grid via `Display.Billboard.CENTER` + `Transformation` (translation/scale), so the panel always faces the player and never skews from any angle. Icons render the `ItemStack` directly (**no resource pack**), and enchantment names use `Enchantment#displayName(level)`, so they are localized to the client's language.
- **Per-player visibility**: spawned entities are hidden from everyone except the owner via `Player#hideEntity(...)` (and from players who join later), so the per-player on/off and "only the viewer sees it" behavior is preserved.
- **Diff updates & following**: a cheap signature is computed from the looked-at villager's trade data, and the hologram is rebuilt **only when it changes**. When the villager moves, the anchor is teleported (interpolated via `setTeleportDuration`) so it follows smoothly. While you keep looking at the same villager, nothing is respawned — avoiding flicker and overhead.
- **Grace period**: the display lingers ~0.6 s after you look away, so small aim jitter doesn't make it flicker. It clears when you keep looking away or run `/villagerscope off`.
- **Cleanup**: display entities are removed on look-away, quit, off, and `onDisable`. Each entity is tagged, and on `onEnable` all worlds are swept to remove any leftovers (orphans from a crash).
- **Performance**: the cost is mainly `interval × online players × one ray trace` (5×/second, up to 8 blocks). Hologram respawns happen only the moment content changes, so staring at a villager adds very little. The ray trace and entity operations touch world state, so they run on the main thread (`runTaskTimer`).
- **It never modifies villagers** (read-only).

> The line-of-sight task runs on Bukkit's global scheduler (`runTaskTimer`), so the target is **Paper** (not Folia).

---

## Build

JDK 25 and Maven are required (`brew install openjdk@25 maven` if missing).
Build with the bundled `deploy.sh` (**no Docker needed**):

```bash
./deploy.sh
```

Output: `target/VillagerScope-1.0.0.jar`

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

Follow [Build](#build) to produce `target/VillagerScope-1.0.0.jar`.

### Placement

```bash
# bind mount (copy to the host-side plugins dir)
cp target/VillagerScope-1.0.0.jar /path/to/data/plugins/
docker restart <container>

# named volume etc. (copy directly into the container)
docker cp target/VillagerScope-1.0.0.jar <container>:/data/plugins/
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
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.0.0/VillagerScope-1.0.0.jar
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
        https://github.com/astail/minecraft-murabito-mieru/releases/download/v1.0.0/VillagerScope-1.0.0.jar
```

- When you cut a new release, update the `v1.0.0` and filename in the URL (e.g. `.../download/v1.1.0/VillagerScope-1.1.0.jar`).
- VillagerScope has no dependencies, so a single URL line is enough.

If you see this in the startup log, it worked:

```text
[VillagerScope] VillagerScope を有効化しました。村人を見ると取引が頭上にアイコン表示されます。
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
    │   ├── VillagerScopePlugin.java   # entry point (commands, task startup, join/quit cleanup, startup sweep)
    │   ├── VillagerLookTask.java      # line-of-sight check → show/hide the hologram
    │   ├── TradeHologram.java         # per-player overhead icon display-entity management (diff updates, following, grace, cleanup)
    │   ├── TradeFormatter.java        # trade data → display model (rows, enchantments, signature)
    │   └── VillagerScopeCommand.java  # /villagerscope (on/off toggle)
    └── resources/plugin.yml
```

> The package name (`io.github.astail.villagerscope`), `VillagerScope`, and the command name can all be renamed (change pom.xml, each `package`, and `plugin.yml` together).

---

## Notes

- **Nothing showing?** Check that your crosshair is actually on the villager (within 8 blocks, nothing in front), that `/villagerscope status` is on, and that you have the `villagerscope.use` permission.
- **No scoreboard conflict**: because it uses display entities, it does not interfere with other plugins that use the scoreboard sidebar.
- **Villagers with many trades**: the display shows up to 10 rows; anything beyond that is summarized as "ほか N 件".
- **About prices**: the display shows each trade's **base cost**. It does not reflect demand-based price increases or discounts from the Hero of the Village effect / curing a zombie villager.
- **Wandering traders too**: trades of wandering traders are shown the same way as villagers.
- The `paper-api` build number can track server updates (e.g. `26.1.2.build.70-stable`).
