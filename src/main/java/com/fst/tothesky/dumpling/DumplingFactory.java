package com.fst.tothesky.dumpling;

import com.fst.tothesky.registry.ModDataComponents;
import com.fst.tothesky.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** 生成带馅料/作者组件与命名、lore 的饺子物品栈 */
public final class DumplingFactory {
    private DumplingFactory() {
    }

    /** 包好的生饺子：馅料 + 作者 + 两行灰色 lore */
    public static ItemStack rawDumpling(ItemStack filling, String author) {
        ItemStack stack = new ItemStack(ModItems.RAW_DUMPLING.get());
        stack.set(ModDataComponents.DUMPLING_FILLING, new ItemStackSnapshot(filling.copyWithCount(1)));
        stack.set(ModDataComponents.DUMPLING_AUTHOR, author);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("馅料: " + filling.getHoverName().getString()).withStyle(ChatFormatting.GRAY),
                Component.literal("厨师: " + author).withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    /** 煮熟的饺子：确定性命名 + 评语 + 厨师名 */
    public static ItemStack cookedDumpling(ItemStack filling, String author) {
        ItemStack stack = new ItemStack(ModItems.COOKED_DUMPLING.get());
        if (!filling.isEmpty()) {
            stack.set(ModDataComponents.DUMPLING_FILLING, new ItemStackSnapshot(filling.copyWithCount(1)));
        }
        stack.set(ModDataComponents.DUMPLING_AUTHOR, author);

        DumplingNamer.Profile profile = DumplingNamer.profileFor(filling);
        if (profile != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(profile.prefix() + " 饺子")
                    .setStyle(Style.EMPTY.withColor(profile.color()).withItalic(false)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(profile.trait()).withStyle(ChatFormatting.GOLD));
            lore.add(Component.literal("厨师: " + author).withStyle(ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /** 煮熟的一盘饺子：内容物 + 命名 + 评语 */
    public static ItemStack cookedPlate(DumplingPlateContents contents, DumplingNamer.Profile profile) {
        ItemStack stack = new ItemStack(ModItems.COOKED_DUMPLING_PLATE_ITEM.get());
        stack.set(ModDataComponents.DUMPLING_PLATE, contents);
        if (profile != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(profile.prefix() + " 一盘熟饺子")
                    .setStyle(Style.EMPTY.withColor(profile.color()).withItalic(false)));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal(profile.trait()).withStyle(ChatFormatting.GOLD))));
        }
        return stack;
    }

    /** 从生饺子堆中聚合出一盘生饺子的内容物（合成配方用） */
    public static DumplingPlateContents aggregate(List<ItemStack> rawDumplings) {
        List<ItemStack> fillings = new ArrayList<>(rawDumplings.size());
        List<String> authors = new ArrayList<>(rawDumplings.size());
        for (ItemStack dumpling : rawDumplings) {
            ItemStack filling = ItemStackSnapshot.unwrap(dumpling.get(ModDataComponents.DUMPLING_FILLING));
            fillings.add(filling.isEmpty() ? ItemStack.EMPTY : filling.copyWithCount(1));
            String author = dumpling.get(ModDataComponents.DUMPLING_AUTHOR);
            authors.add(author != null ? author : "Unknown");
        }
        return new DumplingPlateContents(fillings, authors);
    }
}
