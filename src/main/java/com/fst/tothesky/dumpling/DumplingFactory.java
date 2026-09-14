package com.fst.tothesky.dumpling;

import com.fst.tothesky.registry.ModNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 生成带馅料/厨师名与命名、lore 的熟饺子物品栈 */
public final class DumplingFactory {
    private DumplingFactory() {
    }

    /** 从盘子里拿出的熟饺子：馅料 + 厨师名 + 确定性命名与评语 */
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
            ModNbt.setLore(stack, List.of(
                    Component.literal(profile.trait()).withStyle(ChatFormatting.GOLD),
                    Component.literal("厨师: " + author).withStyle(ChatFormatting.GRAY)));
        }
        return stack;
    }
}
