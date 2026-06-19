package io.github.astail.villagerscope;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * オンラインプレイヤーの視線を定期的に判定し、見ている先が村人（または行商人）なら
 * その取引一覧を頭上アイコンホログラムで表示するタスク。
 */
final class VillagerLookTask extends BukkitRunnable {

    /** 視線判定の最大距離（ブロック）。少し離れた村人にも合わせやすい距離。 */
    private static final int REACH = 8;

    private final VillagerScopePlugin plugin;

    VillagerLookTask(VillagerScopePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        TradeHologram hologram = plugin.hologram();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("villagerscope.use") || !plugin.isEnabledFor(player.getUniqueId())) {
                hologram.hide(player); // 権限なし／OFF はその場で消す
                continue;
            }
            AbstractVillager villager = villagerInSight(player);
            if (villager == null) {
                hologram.markAway(player); // 視線が外れたら猶予を置いて消す（ちらつき防止）
                continue;
            }
            hologram.show(player, villager);
        }
    }

    /** プレイヤーが見ている先の村人／行商人を返す。なければ null。 */
    private AbstractVillager villagerInSight(Player player) {
        // 目線からエンティティへレイを飛ばす。手前にブロックがあれば遮られる（壁越しには出ない）。
        Entity target = player.getTargetEntity(REACH);
        return (target instanceof AbstractVillager villager) ? villager : null;
    }
}
