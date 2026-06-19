package io.github.astail.villagerscope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.github.astail.villagerscope.TradeFormatter.TradeRow;

/**
 * 見ているプレイヤーの「村人の頭上」に、取引一覧を実アイテムのアイコン（{@link ItemDisplay}）と
 * 補足テキスト（{@link TextDisplay}）で浮かべて表示する。リソースパック不要。
 *
 * <p>すべての公開メソッドはメインスレッド（視線タスク・コマンド・イベント）からのみ呼ばれる前提。
 *
 * <h2>レイアウトの肝（1アンカー＋ビルボード）</h2>
 * すべての表示エンティティを村人頭上の<strong>同一座標（アンカー）</strong>にスポーンし、
 * パネル内の位置（列X・行Y）と縮尺は各エンティティの {@link Transformation} の translation / scale で与える。
 * 全要素を {@link Display.Billboard#CENTER} にすると、同一ピボットを共有したまま常にプレイヤーへ正対するため、
 * どの角度から見ても平面カードとして崩れない。村人が動いたらアンカーごと teleport して追従する。
 *
 * <h2>プレイヤーごとの表示</h2>
 * 表示エンティティは既定で全員に見えるため、スポーン時に<strong>所有者以外へ {@link Player#hideEntity}</strong> し、
 * 後から参加したプレイヤーには {@link #hideAllFrom(Player)} で隠す。これにより {@code /villagerscope} の個人 ON/OFF を保つ。
 */
final class TradeHologram {

    /** 取り残し（クラッシュ時の孤児エンティティ）掃除用の識別タグ。 */
    private static final String TAG = "villagerscope";

    /** 視線が外れてから実際に消すまでの猶予（タスク実行回数）。約 0.6 秒。短い見失いでちらつかせない。 */
    private static final int GRACE_RUNS = 3;

    /** 位置補間の tick 数。視線タスク間隔（4 tick）に合わせると村人の移動が滑らかに追従する。 */
    private static final int FOLLOW_TICKS = 4;

    // --- レイアウト定数（ブロック単位。初回の見え方に応じて微調整可能） ---
    /** アイコンの縮尺。 */
    private static final float ICON_SCALE = 0.45f;
    /** テキストの縮尺。 */
    private static final float TEXT_SCALE = 0.4f;
    /** 1行あたりの高さ。 */
    private static final float ROW_HEIGHT = 0.42f;
    /** 行の左端 X（負＝左）。行はここから右へ伸びる。 */
    private static final float ROW_LEFT_X = -1.1f;
    /** アイコン1個分の横送り。 */
    private static final float ICON_STEP = 0.5f;
    /** 「×N」分の横送り。 */
    private static final float COUNT_STEP = 0.42f;
    /** 「 + 」分の横送り。 */
    private static final float PLUS_STEP = 0.34f;
    /** 「 → 」分の横送り。 */
    private static final float ARROW_STEP = 0.42f;
    /** 付与魔法テキスト分の横送り。 */
    private static final float ENCHANT_STEP = 0.9f;
    /**
     * 列方向（横）の符号。+1 でパネル内 X が「プレイヤーから見て右」想定。
     * 万一 in-game で左右が反転して見えたら、ここを -1f にするだけで直る。
     */
    private static final float COLUMN_SIGN = 1f;

    /** 頭の上にどれだけ浮かせるか。 */
    private static final double HEAD_OFFSET = 0.5;
    /** 表示する取引の最大行数。超過分は「ほか N 件」にまとめる。 */
    private static final int MAX_ROWS = 10;

    private final VillagerScopePlugin plugin;

    /** プレイヤーごとの表示状態。キーが無ければ非表示。 */
    private final Map<UUID, Viewer> viewers = new HashMap<>();

    TradeHologram(VillagerScopePlugin plugin) {
        this.plugin = plugin;
    }

    /** 1人分の表示状態（生成済みエンティティ・内容シグネチャ・アンカー・猶予カウンタ）。 */
    private static final class Viewer {
        final List<Display> entities = new ArrayList<>();
        String signature;
        Location anchor;
        int awayRuns;
    }

    /** 村人の取引を表示（内容が前回と同じならアンカー追従だけ行う）。 */
    void show(Player player, AbstractVillager villager) {
        UUID id = player.getUniqueId();
        Viewer v = viewers.computeIfAbsent(id, k -> new Viewer());
        v.awayRuns = 0;

        Location anchor = anchorOf(villager);
        if (anchor == null) {
            return; // ワールド未取得など。次 tick で再試行。
        }

        String signature = TradeFormatter.signature(villager);
        if (signature.equals(v.signature)) {
            reposition(v, anchor); // 変化なし：作り直さず追従のみ（ちらつき防止＆負荷軽減）
            return;
        }

        despawn(v);
        spawn(player, v, villager, anchor);
        v.signature = signature;
    }

    /** 視線が外れている間に毎回呼ぶ。猶予を過ぎたら消す。 */
    void markAway(Player player) {
        Viewer v = viewers.get(player.getUniqueId());
        if (v == null || v.signature == null) {
            return; // そもそも表示していない
        }
        if (++v.awayRuns >= GRACE_RUNS) {
            hide(player);
        }
    }

    /** 即座に消す（コマンド OFF・権限なし・視線喪失の猶予切れなど）。 */
    void hide(Player player) {
        Viewer v = viewers.remove(player.getUniqueId());
        if (v != null) {
            despawn(v);
        }
    }

    /** 退出プレイヤーの表示を破棄。 */
    void clear(UUID id) {
        Viewer v = viewers.remove(id);
        if (v != null) {
            despawn(v);
        }
    }

    /** 無効化時：全員分の表示エンティティを撤去。 */
    void clearAll() {
        for (Viewer v : viewers.values()) {
            despawn(v);
        }
        viewers.clear();
    }

    /** 新規参加プレイヤーには、他人のホログラムを隠す（自分のものは存在しない）。 */
    void hideAllFrom(Player joiner) {
        UUID joinerId = joiner.getUniqueId();
        for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
            if (entry.getKey().equals(joinerId)) {
                continue;
            }
            for (Display d : entry.getValue().entities) {
                if (d.isValid()) {
                    joiner.hideEntity(plugin, d);
                }
            }
        }
    }

    /**
     * 起動時の取り残し掃除。クラッシュや強制終了で {@link #clearAll()} が走らず残った
     * タグ付き表示エンティティを全ワールドから削除する。
     */
    void sweepOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Display && e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 村人頭上のアンカー座標。 */
    private Location anchorOf(AbstractVillager villager) {
        Location base = villager.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return null;
        }
        double top = villager.getBoundingBox().getHeight();
        return new Location(world, base.getX(), base.getY() + top + HEAD_OFFSET, base.getZ());
    }

    /** アンカーが動いていれば全エンティティを新しいアンカーへ移動（teleportDuration により補間追従）。 */
    private void reposition(Viewer v, Location anchor) {
        if (v.anchor != null
                && v.anchor.getWorld() == anchor.getWorld()
                && v.anchor.distanceSquared(anchor) < 0.0004) {
            return; // ほぼ動いていない
        }
        for (Display e : v.entities) {
            if (e.isValid()) {
                e.teleport(anchor);
            }
        }
        v.anchor = anchor.clone();
    }

    /** 取引内容からホログラムを構築する。 */
    private void spawn(Player owner, Viewer v, AbstractVillager villager, Location anchor) {
        World world = anchor.getWorld();
        List<TradeRow> rows = TradeFormatter.rows(villager);

        int bodyLines = rows.isEmpty() ? 1 : Math.min(rows.size(), MAX_ROWS);
        boolean overflow = rows.size() > MAX_ROWS;
        int totalLines = 1 + bodyLines + (overflow ? 1 : 0); // タイトル + 本文 + 「ほか N 件」
        float topY = (totalLines - 1) * ROW_HEIGHT;

        // タイトル（中央寄せ）
        v.entities.add(spawnText(world, anchor, TradeFormatter.title(villager), 0f, topY, true));

        if (rows.isEmpty()) {
            v.entities.add(spawnText(world, anchor,
                    Component.text("取引なし", NamedTextColor.GRAY), 0f, topY - ROW_HEIGHT, true));
        } else {
            for (int i = 0; i < bodyLines; i++) {
                buildRow(world, anchor, v, rows.get(i), topY - ROW_HEIGHT * (i + 1));
            }
            if (overflow) {
                v.entities.add(spawnText(world, anchor,
                        Component.text("ほか " + (rows.size() - MAX_ROWS) + " 件", NamedTextColor.GRAY),
                        0f, topY - ROW_HEIGHT * (bodyLines + 1), true));
            }
        }

        v.anchor = anchor.clone();

        // 所有者以外の現在オンラインプレイヤーから隠す（個人表示）。
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(owner)) {
                continue;
            }
            for (Display d : v.entities) {
                other.hideEntity(plugin, d);
            }
        }
    }

    /** 1取引を「アイコン ×N (+ アイコン ×N) → アイコン ×N [エンチャント]」の横並びで構築する。 */
    private void buildRow(World world, Location anchor, Viewer v, TradeRow row, float y) {
        boolean muted = row.muted();
        NamedTextColor accent = muted ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;
        float x = ROW_LEFT_X;

        List<ItemStack> ingredients = row.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack ing = ingredients.get(i);
            x = appendItem(world, anchor, v, ing, x, y, muted, accent);
            if (i < ingredients.size() - 1) {
                v.entities.add(spawnText(world, anchor, deco(Component.text(" + ", accent), muted), x, y, false));
                x += PLUS_STEP;
            }
        }

        v.entities.add(spawnText(world, anchor, deco(Component.text(" → ", accent), muted), x, y, false));
        x += ARROW_STEP;

        appendItem(world, anchor, v, row.result(), x, y, muted, accent);
    }

    /** アイコン＋（必要なら）「×N」＋（エンチャント本なら）付与魔法テキストを置き、次の X を返す。 */
    private float appendItem(World world, Location anchor, Viewer v, ItemStack stack,
                             float x, float y, boolean muted, NamedTextColor accent) {
        v.entities.add(spawnIcon(world, anchor, stack, x, y, muted));
        x += ICON_STEP;
        if (stack.getAmount() > 1) {
            v.entities.add(spawnText(world, anchor,
                    deco(Component.text("×" + stack.getAmount(), accent), muted), x, y, false));
            x += COUNT_STEP;
        }
        Component enchant = TradeFormatter.enchantText(stack, muted);
        if (enchant != null) {
            v.entities.add(spawnText(world, anchor, deco(enchant, muted), x, y, false));
            x += ENCHANT_STEP;
        }
        return x;
    }

    private ItemDisplay spawnIcon(World world, Location anchor, ItemStack stack,
                                  float x, float y, boolean muted) {
        return world.spawn(anchor, ItemDisplay.class, e -> {
            e.setItemStack(stack);
            e.setBillboard(Display.Billboard.CENTER);
            e.setTransformation(transform(x, y, ICON_SCALE));
            e.setTeleportDuration(FOLLOW_TICKS);
            e.addScoreboardTag(TAG);
            if (muted) {
                e.setBrightness(new Display.Brightness(3, 3)); // 在庫切れは減光
            }
        });
    }

    private TextDisplay spawnText(World world, Location anchor, Component text,
                                  float x, float y, boolean centered) {
        return world.spawn(anchor, TextDisplay.class, e -> {
            e.text(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(centered ? TextDisplay.TextAlignment.CENTER : TextDisplay.TextAlignment.LEFT);
            e.setTransformation(transform(x, y, TEXT_SCALE));
            e.setTeleportDuration(FOLLOW_TICKS);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 背景は透明（文字＋影で読ませる）
            e.addScoreboardTag(TAG);
        });
    }

    /** translation(x,y) と一様 scale だけの変換（回転なし）。x は {@link #COLUMN_SIGN} で左右を切り替え可能。 */
    private static Transformation transform(float x, float y, float scale) {
        return new Transformation(
                new Vector3f(x * COLUMN_SIGN, y, 0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /** 在庫切れなら打ち消し線を付ける。 */
    private static Component deco(Component c, boolean muted) {
        return muted ? c.decorate(TextDecoration.STRIKETHROUGH) : c;
    }

    private static void despawn(Viewer v) {
        for (Display e : v.entities) {
            if (e.isValid()) {
                e.remove();
            }
        }
        v.entities.clear();
        v.signature = null;
        v.anchor = null;
        v.awayRuns = 0;
    }
}
