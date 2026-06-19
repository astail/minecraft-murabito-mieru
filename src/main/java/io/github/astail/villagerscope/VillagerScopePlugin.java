package io.github.astail.villagerscope;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VillagerScopePlugin extends JavaPlugin implements Listener {

    /** 視線チェックの実行間隔（tick）。4 tick ≒ 0.2 秒ごと。 */
    private static final long CHECK_PERIOD_TICKS = 4L;

    /** 取引表示を自分でオフにしたプレイヤー（サーバー稼働中のみ保持）。 */
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    /** プレイヤーごとの頭上アイコンホログラム表示を管理する。 */
    private final TradeHologram hologram = new TradeHologram(this);

    private BukkitTask lookTask;

    @Override
    public void onEnable() {
        if (!register("villagerscope", new VillagerScopeCommand(this))) {
            return;
        }
        // クラッシュ等で前回の表示エンティティが取り残されていれば掃除する。
        hologram.sweepOrphans();
        // 退出／参加プレイヤーの表示を片付ける・隠すためのリスナー。
        getServer().getPluginManager().registerEvents(this, this);
        // 全オンラインプレイヤーの視線を定期判定するタスク。サーバー停止時は自動キャンセルされるが、
        // onDisable でも明示的に止める（リロード時の二重起動防止）。
        lookTask = new VillagerLookTask(this).runTaskTimer(this, 0L, CHECK_PERIOD_TICKS);
        getLogger().info("VillagerScope を有効化しました。村人を見ると取引が頭上にアイコン表示されます。");
    }

    @Override
    public void onDisable() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        // 表示中のホログラム（表示エンティティ）を全員分撤去する。
        hologram.clearAll();
    }

    /** 頭上アイコンホログラムの表示マネージャ。 */
    public TradeHologram hologram() {
        return hologram;
    }

    /** 指定プレイヤーが取引表示を受け取るか（既定は ON）。 */
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
        // 退出時は表示エンティティを撤去（ON/OFF 設定は維持）。
        hologram.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 表示中の他人のホログラムが新規参加者に見えないよう隠す（個人表示の維持）。
        hologram.hideAllFrom(event.getPlayer());
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
