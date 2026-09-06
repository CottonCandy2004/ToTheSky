package com.fst.tothesky.dumpling;

import com.fst.tothesky.registry.ModNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 生成带馅料/作者 NBT 与命名、lore 的饺子物品栈 */
public final class DumplingFactory {
    private DumplingFactory() {
    }

    /** 包好的生饺子：馅料 + 作者 + 两行灰色 lore */
    public static ItemStack rawDumpling(ItemStack filling, String author) {
        return rawDumpling0(new ItemStack(com.fst.tothesky.registry.ModItems.RAW_DUMPLING.get()), filling, author);
    }

    private static ItemStack rawDumpling0(ItemStack stack, ItemStack filling, String author) {
        ModNbt.setFilling(stack, filling);
        ModNbt.setAuthor(stack, author);
        ModNbt.setLore(stack, List.of(
                Component.literal("馅料: " + filling.getHoverName().getString()).withStyle(ChatFormatting.GRAY),
                Component.literal("厨师: " + author).withStyle(ChatFormatting.GRAY)));
        return stack;
    }

    /** 煮熟的饺子：确定性命名 + 评语 + 厨师名 */
    public static ItemStack cookedDumpling(ItemStack filling, String author) {
        ItemStack stack = new ItemStack(com.fst.tothesky.registry.ModItems.COOKED_DUMPLING.get());
        if (!filling.isEmpty()) {
            ModNbt.setFilling(stack, filling);
        }
        ModNbt.setAuthor(stack, author);

        DumplingNamer.Profile profile = DumplingNamer.profileFor(filling);
        if (profile != null) {
            ModNbt.setCustomName(stack, Component.literal(profile.prefix() + " 饺子")
                    .setStyle(Style.EMPTY.withColor(profile.color()).withItalic(false)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(profile.trait()).withStyle(ChatFormatting.GOLD));
            lore.add(Component.literal("厨师: " + author).withStyle(ChatFormatting.GRAY));
            ModNbt.setLore(stack, lore);
        }
        return stack;
    }

    /** 煮熟的一盘饺子：内容物 + 命名 + 评语 */
    public static ItemStack cookedPlate(DumplingPlateContents contents, DumplingNamer.Profile profile) {
        ItemStack stack = new ItemStack(com.fst.tothesky.registry.ModItems.COOKED_DUMPLING_PLATE_ITEM.get());
        stack.getOrCreateTag().put(ModNbt.DUMPLING_PLATE, contents.save());
        if (profile != null) {
            ModNbt.setCustomName(stack, Component.literal(profile.prefix() + " 一盘熟饺子")
                    .setStyle(Style.EMPTY.withColor(profile.color()).withItalic(false)));
            ModNbt.setLore(stack, List.of(
                    Component.literal(profile.trait()).withStyle(ChatFormatting.GOLD)));
        }
        return stack;
    }

    /** 从生饺子堆中聚合出一盘生饺子的内容物（合成配方用） */
    public static DumplingPlateContents aggregate(List<ItemStack> rawDumplings) {
        List<ItemStack> fillings = new ArrayList<>(rawDumplings.size());
        List<String> authors = new ArrayList<>(rawDumplings.size());
        for (ItemStack dumpling : rawDumplings) {
            ItemStack filling = ModNbt.getFilling(dumpling);
            fillings.add(filling.isEmpty() ? ItemStack.EMPTY : filling.copyWithCount(1));
            String author = ModNbt.getAuthor(dumpling);
            authors.add(author != null ? author : "Unknown");
        }
        return new DumplingPlateContents(fillings, authors);
    }
}