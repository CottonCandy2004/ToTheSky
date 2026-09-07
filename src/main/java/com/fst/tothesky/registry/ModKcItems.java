package com.fst.tothesky.registry;

import com.github.ysbbbbbb.kaleidoscopecookery.item.SickleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * kaleidoscope_cookery 命名空间的两把镰刀（PR#39）。
 * kjs 用 event.createCustom 直接实例化 kk 的 SickleItem；mod 侧在 kk 命名空间注册，
 * 使镰刀的 id 与 tag 与 kjs 时代完全一致（存档/配方无需映射）。
 */
public final class ModKcItems {
    public static final DeferredRegister<Item> KC_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "kaleidoscope_cookery");

    /** 钻石镰刀：挖掘等级 4、耐久 3000、速度 9.0、攻击 3.0、附魔 10 */
    public static final RegistryObject<Item> DIAMOND_SICKLE = KC_ITEMS.register("diamond_sickle",
            () -> new SickleItem(diamondTier(), 0, -2.4f, new Item.Properties()));

    /** 下界合金镰刀：耐久 4000、攻击 5.0、附魔 15、防火 */
    public static final RegistryObject<Item> NETHERITE_SICKLE = KC_ITEMS.register("netherite_sickle",
            () -> new SickleItem(netheriteTier(), 0, -2.4f, new Item.Properties().fireResistant()));

    private static Tier diamondTier() {
        return tier("diamond_sickle", 4, 3000, 9.0f, 3.0f, 10);
    }

    private static Tier netheriteTier() {
        return tier("netherite_sickle", 4, 4000, 9.0f, 5.0f, 15);
    }

    private static Tier tier(String name, int level, int uses, float speed, float attack, int enchantment) {
        // kjs 直接 new ForgeTier(...)（未注册 TierSortingRegistry），这里保持一致——
        // 注册到排序表可以让耐久条/挖掘等级与其他 Tier 正确比较，属纯增强。
        ForgeTier tier = new ForgeTier(level, uses, speed, attack, enchantment,
                BlockTags.NEEDS_STONE_TOOL, () -> Ingredient.of(Items.DIAMOND));
        TierSortingRegistry.registerTier(tier, new ResourceLocation("tothesky", name),
                java.util.List.of(net.minecraft.world.item.Tiers.DIAMOND), java.util.List.of());
        return tier;
    }

    private ModKcItems() {
    }
}
