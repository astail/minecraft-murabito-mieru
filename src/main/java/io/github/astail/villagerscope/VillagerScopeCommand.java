package io.github.astail.villagerscope;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /villagerscope … 自分の取引表示（頭上アイコン）を ON/OFF（引数なしはトグル）するコマンド。 */
public final class VillagerScopeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "status");

    private final VillagerScopePlugin plugin;

    public VillagerScopeCommand(@NotNull VillagerScopePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // villagerscope.use の有無は plugin.yml の commands.villagerscope.permission により Bukkit が事前チェック済み。
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        boolean current = plugin.isEnabledFor(player.getUniqueId());

        if (args.length == 0) {
            setAndReport(player, !current); // 引数なしはトグル
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§e使い方: /villagerscope [on|off|status]");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> setAndReport(player, true);
            case "off" -> setAndReport(player, false);
            case "status" -> player.sendMessage(current
                    ? "§a取引表示は ON です。村人を見ると表示されます。"
                    : "§7取引表示は OFF です。/villagerscope on で有効化できます。");
            default -> player.sendMessage("§e使い方: /villagerscope [on|off|status]");
        }
        return true;
    }

    private void setAndReport(Player player, boolean enabled) {
        plugin.setEnabledFor(player.getUniqueId(), enabled);
        if (!enabled) {
            plugin.hologram().hide(player); // 表示中なら即座に消す
        }
        player.sendMessage(enabled
                ? "§a取引表示を ON にしました。村人を見ると頭上にアイコン表示されます。"
                : "§7取引表示を OFF にしました。");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        // 第1引数のみ候補を返す。既定のプレイヤー名補完を抑止するため、該当なしでも空リストを返す。
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(prefix))
                .toList();
    }
}
