package io.github.astail.villagerscope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.scoreboard.numbers.NumberFormat;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * プレイヤーごとに専用スコアボードを用意し、見ている村人の取引をサイドバーへ表示する。
 *
 * <p>すべての公開メソッドはメインスレッド（視線タスク・コマンド・退出イベント）からのみ呼ばれる前提。
 * 同じ内容を毎 tick 作り直さないよう、表示中の内容シグネチャを保持して差分があるときだけ再構築する。
 */
final class TradeSidebar {

    /** スコアボード objective 名（16 文字以内）。 */
    private static final String OBJECTIVE_NAME = "vscope";

    /** 行を識別するためのユニークかつ「表示されても空に見える」エントリ（§+色コード）。最大 15 行。 */
    private static final String[] LINE_ENTRIES = buildEntries();

    /** 視線が外れてから実際に消すまでの猶予（タスク実行回数）。約 0.6 秒。短い見失いでちらつかせない。 */
    private static final int GRACE_RUNS = 3;

    private final VillagerScopePlugin plugin;

    /** プレイヤー専用スコアボード（使い回す）。 */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** 現在表示中の内容シグネチャ。キーが無ければ非表示。 */
    private final Map<UUID, String> shown = new HashMap<>();
    /** 視線が外れている連続タスク回数（猶予判定用）。 */
    private final Map<UUID, Integer> awayRuns = new HashMap<>();

    TradeSidebar(VillagerScopePlugin plugin) {
        this.plugin = plugin;
    }

    /** 村人の取引を表示（内容が前回と同じなら何もしない）。 */
    void show(Player player, AbstractVillager villager) {
        UUID id = player.getUniqueId();
        awayRuns.remove(id);

        String signature = signature(villager);
        if (signature.equals(shown.get(id))) {
            return; // 変化なし：再構築しない（ちらつき防止＆負荷軽減）
        }

        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            return; // 起動直後でワールド未ロード等。次 tick で再試行。
        }
        Scoreboard board = boards.computeIfAbsent(id, k -> manager.getNewScoreboard());
        render(board, villager);

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        shown.put(id, signature);
    }

    /** 視線が外れている間に毎回呼ぶ。猶予を過ぎたら消す。 */
    void markAway(Player player) {
        UUID id = player.getUniqueId();
        if (!shown.containsKey(id)) {
            return; // そもそも表示していない
        }
        if (awayRuns.merge(id, 1, Integer::sum) >= GRACE_RUNS) {
            hide(player);
        }
    }

    /** 即座に消す（コマンド OFF・権限なし・退出時など）。非表示中は何もしない。 */
    void hide(Player player) {
        UUID id = player.getUniqueId();
        awayRuns.remove(id);
        if (shown.remove(id) == null) {
            return;
        }
        Scoreboard board = boards.get(id);
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager != null && player.getScoreboard() == board) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    /** 退出プレイヤーの使い回しオブジェクトを破棄。 */
    void clear(UUID id) {
        boards.remove(id);
        shown.remove(id);
        awayRuns.remove(id);
    }

    /** 無効化時：表示中の全員をメインスコアボードへ戻して状態を破棄。 */
    void clearAll() {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        for (Map.Entry<UUID, Scoreboard> entry : boards.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && manager != null && player.getScoreboard() == entry.getValue()) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        boards.clear();
        shown.clear();
        awayRuns.clear();
    }

    /** スコアボードを村人の取引内容で作り直す。 */
    private void render(Scoreboard board, AbstractVillager villager) {
        Objective existing = board.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            existing.unregister();
        }
        Objective objective = board.registerNewObjective(
                OBJECTIVE_NAME, Criteria.DUMMY, TradeFormatter.title(villager));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank()); // 右側の赤い数字を隠す

        List<Component> lines = TradeFormatter.lines(villager);
        int count = Math.min(lines.size(), LINE_ENTRIES.length);
        for (int i = 0; i < count; i++) {
            Score score = objective.getScore(LINE_ENTRIES[i]);
            score.customName(lines.get(i)); // エントリ文字列ではなく Component を表示
            score.setScore(count - i);      // スコア降順に並ぶので上から順に
        }
    }

    private static String[] buildEntries() {
        String hex = "0123456789abcde"; // 15 個（§0〜§e）
        String[] entries = new String[hex.length()];
        for (int i = 0; i < hex.length(); i++) {
            entries[i] = "§" + hex.charAt(i);
        }
        return entries;
    }

    /**
     * 表示内容が変わったか判定するための安価なシグネチャ。
     * Component を作らず、村人の取引データ（職業・レベル・各取引のアイテムと在庫状態）だけから生成する。
     */
    private static String signature(AbstractVillager villager) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(villager.getUniqueId()).append('|').append(TradeFormatter.identity(villager));
        for (MerchantRecipe recipe : villager.getRecipes()) {
            sb.append(';');
            for (ItemStack ingredient : recipe.getIngredients()) {
                sb.append(ingredient.getType()).append('x').append(ingredient.getAmount()).append(',');
            }
            sb.append('>').append(recipe.getResult().getType())
                    .append('x').append(recipe.getResult().getAmount());
            if (recipe.getUses() >= recipe.getMaxUses()) {
                sb.append('!'); // 在庫切れ表示の切り替えも検出する
            }
        }
        return sb.toString();
    }
}
