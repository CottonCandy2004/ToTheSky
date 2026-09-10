package com.fst.tothesky.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 包装颜料（PR#59）：NBT StoredColors 记录内含染料 id 列表；
 * 潜行右键拆包返还内容物。合成时由 PackedColorsRecipe 写入 NBT 与 lore。
 */
public class PackedColorsItem extends Item {
    public static final String TAG_COLORS = "StoredColors";

    public PackedColorsItem(Properties properties) {
        super(properties);
    }

    /** 读取内容物（保持存储顺序） */
    public static List<ItemStack> getColors(ItemStack stack) {
        List<ItemStack> colors = new ArrayList<>();
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(TAG_COLORS)) {
            ListTag list = nbt.getList(TAG_COLORS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(net.minecraft.resources.ResourceLocation.parse(list.getString(i)));
                if (item != null) {
                    colors.add(new ItemStack(item, stack.getCount()));
                }
            }
        }
        return colors;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide) {
            for (ItemStack color : getColors(stack.copyWithCount(1))) {
                player.getInventory().placeItemBackInInventory(color);
            }
            level.playSound(null, player.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            stack.shrink(1);
            player.swing(hand, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(TAG_COLORS)) {
            tooltip.add(Component.literal("包含的颜料: ").withStyle(net.minecraft.ChatFormatting.GOLD));
            ListTag list = nbt.getList(TAG_COLORS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(net.minecraft.resources.ResourceLocation.parse(list.getString(i)));
                if (item != null) {
                    tooltip.add(Component.literal("- " + item.getName(stack).getString())
                            .withStyle(net.minecraft.ChatFormatting.WHITE));
                }
            }
        }
        tooltip.add(Component.literal("按住 Shift 右键拆开包装颜料")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}