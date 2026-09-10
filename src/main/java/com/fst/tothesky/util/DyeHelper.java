package com.fst.tothesky.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 染料 / 雕刻材料的公共判定，供包装颜料配方与动力雕刻台共用，避免两处 drift。
 *
 * - 染料 = 任意原版染料（DyeItem 实例判定，零 tag 依赖）或群青颜料粉
 *   （ultramarine 自己维护的 dye_powder tag，含 gold_dye_powder）。
 * - 雕刻材料 = 抛光木板（#ultramarine:polished_planks）或原木（#minecraft:logs，含去皮变体与菌柄）。
 */
public final class DyeHelper {
    private static final TagKey<Item> ULTRAMARINE_DYE_POWDER =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("ultramarine", "dye_powder"));
    private static final TagKey<Item> ULTRAMARINE_POLISHED_PLANKS =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("ultramarine", "polished_planks"));

    private DyeHelper() {
    }

    /** 任意原版染料或群青颜料粉 */
    public static boolean isDye(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.DyeItem
                || stack.is(ULTRAMARINE_DYE_POWDER);
    }

    /** 抛光木板或原木（雕刻台材料槽接受的输入） */
    public static boolean isPlankOrLog(ItemStack stack) {
        return stack.is(ULTRAMARINE_POLISHED_PLANKS) || stack.is(ItemTags.LOGS);
    }
}
