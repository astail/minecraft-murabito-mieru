package io.github.astail.villagerscope;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VillagerScopePlugin extends JavaPlugin implements Listener {

    /** 視線チェックの実行間隔（tick）。4 tick ≒ 0.2 秒ごと。 */
    private static final long CHECK_PERIOD_TICKS = 4L;

    /** サイドバー表示を自分でオフにしたプレイヤー（サーバー稼働中のみ保持）。 */
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    /** プレイヤーごとのサイドバー表示を管理する。 */
    private final TradeSidebar sidebar = new TradeSidebar(this);

    private BukkitTask lookTask;

    @Override
    public void onEnable() {
        if (!register("villagerscope", new VillagerScopeCommand(this))) {
            return;
        }
        // 退出プレイヤーのサイドバー状態を片付けるためのリスナー。
        getServer().getPluginManager().registerEvents(this, this);
        // 全オンラインプレイヤーの視線を定期判定するタスク。サーバー停止時は自動キャンセルされるが、
        // onDisable でも明示的に止める（リロード時の二重起動防止）。
        lookTask = new VillagerLookTask(this).runTaskTimer(this, 0L, CHECK_PERIOD_TICKS);
        getLogger().info("VillagerScope を有効化しました。村人を見ると取引がサイドバーに表示されます。");
    }

    @Override
    public void onDisable() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        // 表示中のサイドバーを全員分片付け、元のスコアボードへ戻す。
        sidebar.clearAll();
    }

    /** サイドバー表示マネージャ。 */
    public TradeSidebar sidebar() {
        return sidebar;
    }

    /** 指定プレイヤーがサイドバー表示を受け取るか（既定は ON）。 */
    public boolean isEnabledFor(UUID playerId) {
        return !disabled.contains(playerId);
    }

    /** 表示の ON/OFF を設定する。 */
    public void setEnabledFor(UUID playerId, boolean enabled) {
        if (enabled) {
            disabled.remove(playerId);
        } else {
            disabled.add(playerId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 退出時はサイドバー用の使い回しオブジェクトを破棄（ON/OFF 設定は維持）。
        sidebar.clear(event.getPlayer().getUniqueId());
    }

    private boolean register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("コマンド '" + name + "' が plugin.yml に未定義です。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
        return true;
    }
}
