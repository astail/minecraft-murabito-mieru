package io.github.astail.villagerscope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 村人／行商人の取引を、頭上アイコンホログラム（{@link TradeHologram}）で描くための構造化データへ整形する純粋ロジック。
 *
 * <p>Bukkit の表示エンティティ操作は {@link TradeHologram} 側が担当し、ここは「取引データ → 表示モデル」だけに集中する。
 * アイテムはアイコン（{@link ItemStack} そのもの）で見せるが、エンチャントの本のように見た目だけでは中身が分からない物は
 * {@link #enchantText(ItemStack, boolean)} で付与魔法を文字併記する。
 */
final class TradeFormatter {

    /** 村人の職業 → 日本語名。NoteScope の楽器マップと同じ方針（既知は固定訳・未知はフォールバック）。 */
    private static final Map<Villager.Profession, String> PROFESSION_JA = Map.ofEntries(
            Map.entry(Villager.Profession.ARMORER, "防具鍛冶"),
            Map.entry(Villager.Profession.BUTCHER, "肉屋"),
            Map.entry(Villager.Profession.CARTOGRAPHER, "製図家"),
            Map.entry(Villager.Profession.CLERIC, "聖職者"),
            Map.entry(Villager.Profession.FARMER, "農民"),
            Map.entry(Villager.Profession.FISHERMAN, "漁師"),
            Map.entry(Villager.Profession.FLETCHER, "矢師"),
            Map.entry(Villager.Profession.LEATHERWORKER, "革職人"),
            Map.entry(Villager.Profession.LIBRARIAN, "司書"),
            Map.entry(Villager.Profession.MASON, "石工"),
            Map.entry(Villager.Profession.SHEPHERD, "羊飼い"),
            Map.entry(Villager.Profession.TOOLSMITH, "道具鍛冶"),
            Map.entry(Villager.Profession.WEAPONSMITH, "武器鍛冶"),
            Map.entry(Villager.Profession.NITWIT, "ニトウィット"),
            Map.entry(Villager.Profession.NONE, "無職"));

    private TradeFormatter() {
    }

    /**
     * 1取引分の表示モデル。アイコン＝{@link ItemStack} そのもの（個数も Stack が保持）。
     *
     * @param ingredients 支払いアイテム（空気を除いた 1〜2 個）
     * @param result      手に入るアイテム
     * @param muted       在庫切れ（補充待ち）なら true
     */
    record TradeRow(List<ItemStack> ingredients, ItemStack result, boolean muted) {
    }

    /** ホログラム上部のタイトル（村人の種類とレベル）。例:「司書 Lv3」「行商人」。 */
    static Component title(AbstractVillager villager) {
        if (villager instanceof Villager v) {
            Villager.Profession profession = v.getProfession();
            String name = PROFESSION_JA.getOrDefault(profession, "村人");
            // 無職・ニトウィットは取引を持たないためレベルは表示しない。
            boolean jobless = profession == Villager.Profession.NONE
                    || profession == Villager.Profession.NITWIT;
            String label = jobless ? name : name + " Lv" + v.getVillagerLevel();
            return Component.text(label, NamedTextColor.GOLD);
        }
        if (villager instanceof WanderingTrader) {
            return Component.text("行商人", NamedTextColor.GOLD);
        }
        return Component.text("取引", NamedTextColor.GOLD);
    }

    /** 取引一覧（1取引＝1行）。取引が無ければ空リスト（呼び出し側で「取引なし」を出す）。 */
    static List<TradeRow> rows(AbstractVillager villager) {
        List<MerchantRecipe> recipes = villager.getRecipes();
        List<TradeRow> out = new ArrayList<>(recipes.size());
        for (MerchantRecipe recipe : recipes) {
            List<ItemStack> ingredients = new ArrayList<>(2);
            List<ItemStack> raw = recipe.getIngredients();
            if (!raw.isEmpty()) {
                addIfPresent(ingredients, raw.get(0));
            }
            if (raw.size() >= 2) {
                addIfPresent(ingredients, raw.get(1));
            }
            boolean muted = recipe.getUses() >= recipe.getMaxUses(); // 在庫切れ
            out.add(new TradeRow(ingredients, recipe.getResult(), muted));
        }
        return out;
    }

    private static void addIfPresent(List<ItemStack> dest, ItemStack stack) {
        if (stack != null && !stack.getType().isAir()) {
            dest.add(stack);
        }
    }

    /**
     * エンチャントの本なら付与魔法を「[効果 レベル, …]」の {@link Component} で返す。それ以外は null。
     * アイコンだけでは中身（どの魔法か）が分からないため、ホログラム上でアイコンの右へ併記する。
     */
    static Component enchantText(ItemStack stack, boolean muted) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta book) || !book.hasStoredEnchants()) {
            return null;
        }
        NamedTextColor bracket = muted ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;
        Component inner = Component.empty();
        boolean first = true;
        for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
            if (!first) {
                inner = inner.append(Component.text(", ", bracket));
            }
            Component enchant = entry.getKey().displayName(entry.getValue());
            inner = inner.append(muted ? enchant.color(NamedTextColor.DARK_GRAY) : enchant);
            first = false;
        }
        return Component.empty()
                .append(Component.text("[", bracket))
                .append(inner)
                .append(Component.text("]", bracket));
    }

    /** 変化検出（シグネチャ）用の安価な識別子。職業とレベルだけ拾えればよい。 */
    static String identity(AbstractVillager villager) {
        if (villager instanceof Villager v) {
            return PROFESSION_JA.getOrDefault(v.getProfession(), "村人") + v.getVillagerLevel();
        }
        return "wandering";
    }

    /**
     * 表示内容が変わったか判定するための安価なシグネチャ。
     * Component やアイコンを作らず、村人の取引データ（職業・レベル・各取引のアイテムと在庫状態）だけから生成する。
     * 変化があったときだけホログラムを作り直すことで、ちらつきと負荷を抑える。
     */
    static String signature(AbstractVillager villager) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(villager.getUniqueId()).append('|').append(identity(villager));
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
