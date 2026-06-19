package io.github.astail.villagerscope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 村人／行商人の取引を、サイドバー表示用の Adventure {@link Component} に整形する純粋ロジック。
 *
 * <p>Bukkit のスコアボード操作は {@link TradeSidebar} 側が担当し、ここは「取引データ → 見た目」だけに集中する。
 * アイテム名はクライアントの言語で表示されるよう翻訳可能な {@link ItemStack#effectiveName()} を使う。
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

    /** サイドバー上部のタイトル（村人の種類とレベル）。例:「司書 Lv3」「行商人」。 */
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

    /** 取引一覧（1取引＝1行）。取引が無ければ「取引なし」の1行。 */
    static List<Component> lines(AbstractVillager villager) {
        List<MerchantRecipe> recipes = villager.getRecipes();
        if (recipes.isEmpty()) {
            return List.of(Component.text("取引なし", NamedTextColor.GRAY));
        }
        List<Component> out = new ArrayList<>(recipes.size());
        for (MerchantRecipe recipe : recipes) {
            out.add(tradeLine(recipe));
        }
        return out;
    }

    /** 1取引を「コスト → 結果」の1行に整形。在庫切れ（補充待ち）は灰色＋打ち消し線。 */
    private static Component tradeLine(MerchantRecipe recipe) {
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return Component.text("???", NamedTextColor.DARK_GRAY);
        }
        boolean muted = recipe.getUses() >= recipe.getMaxUses(); // 在庫切れ
        NamedTextColor accent = muted ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;

        Component line = Component.empty().append(itemLabel(ingredients.get(0), NamedTextColor.WHITE, muted));
        if (ingredients.size() >= 2) {
            ItemStack second = ingredients.get(1);
            if (second != null && !second.getType().isAir()) {
                line = line.append(Component.text(" + ", accent))
                        .append(itemLabel(second, NamedTextColor.WHITE, muted));
            }
        }
        line = line.append(Component.text(" → ", accent))
                .append(itemLabel(recipe.getResult(), NamedTextColor.GREEN, muted));
        return muted ? line.decorate(TextDecoration.STRIKETHROUGH) : line;
    }

    /** アイテム1個分のラベル（名前＋個数。エンチャントの本なら付与魔法も）。 */
    private static Component itemLabel(ItemStack stack, NamedTextColor baseColor, boolean muted) {
        Component name = muted
                ? stack.effectiveName().color(NamedTextColor.DARK_GRAY)
                : stack.effectiveName().colorIfAbsent(baseColor);

        Component label = Component.empty().append(name);
        if (stack.getAmount() > 1) {
            label = label.append(Component.text(" ×" + stack.getAmount(),
                    muted ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY));
        }
        Component enchants = enchantSuffix(stack, muted);
        return enchants == null ? label : label.append(enchants);
    }

    /** エンチャントの本なら「 [効果 レベル]」を返す。それ以外は null。 */
    private static Component enchantSuffix(ItemStack stack, boolean muted) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta book) || !book.hasStoredEnchants()) {
            return null;
        }
        // 括弧 [ ] は白、付与名・レベル数字は黄にして区別する。在庫切れは全体が暗くなるので暗灰。
        NamedTextColor bracket = muted ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE;
        NamedTextColor body = muted ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW;
        Component inner = Component.empty();
        boolean first = true;
        for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
            if (!first) {
                inner = inner.append(Component.text(", ", bracket));
            }
            Component enchant = entry.getKey().displayName(entry.getValue());
            inner = inner.append(enchant.color(body));
            first = false;
        }
        return Component.empty()
                .append(Component.text(" [", bracket))
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
}
