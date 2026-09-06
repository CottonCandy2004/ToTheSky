package com.fst.tothesky.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 1.20.1 的 NBT 数据键与读写工具（替代 1.21 的数据组件注册）。
 *
 * 1.21.1 中馅料/作者/盘子内容物走 DataComponentType；1.20.1 无数据组件，
 * 一律存在物品 NBT 根 tag 下（键名沿用组件名，便于对照）。
 */
public final class ModNbt {
    /** 饺子的馅料（单个物品栈 NBT，数量恒为 1） */
    public static final String DUMPLING_FILLING = "dumpling_filling";
    /** 包制中的馅料快照（use 时从另一手拆入，完成/中止时移除） */
    public static final String DUMPLING_WRAPPING = "dumpling_wrapping";
    /** 饺子的厨师名 */
    public static final String DUMPLING_AUTHOR = "dumpling_author";
    /** 一盘饺子的内容物 */
    public static final String DUMPLING_PLATE = "dumpling_plate";

    /** 玩家死亡回溯记录点（存于 player.getPersistentData()） */
    public static final String REWIND_POS = "rewind_pos";

    private ModNbt() {
    }

    // ---------------- 馅料（单个物品栈） ----------------

    /** 读取馅料物品栈；无馅料返回 EMPTY（数量恒为 1） */
    public static ItemStack getFilling(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DUMPLING_FILLING, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(tag.getCompound(DUMPLING_FILLING));
    }

    /** 写入馅料（内部 copy + 数量归一） */
    public static void setFilling(ItemStack stack, ItemStack filling) {
        if (filling.isEmpty()) {
            stack.removeTagKey(DUMPLING_FILLING);
            return;
        }
        ItemStack copy = filling.copy();
        copy.setCount(1);
        stack.getOrCreateTag().put(DUMPLING_FILLING, copy.save(new CompoundTag()));
    }

    /** 包制中的馅料：与 {@link #getFilling} 同结构，键不同 */
    public static ItemStack getWrapping(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DUMPLING_WRAPPING, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(tag.getCompound(DUMPLING_WRAPPING));
    }

    public static void setWrapping(ItemStack stack, ItemStack filling) {
        ItemStack copy = filling.copy();
        copy.setCount(1);
        stack.getOrCreateTag().put(DUMPLING_WRAPPING, copy.save(new CompoundTag()));
    }

    public static void removeWrapping(ItemStack stack) {
        stack.removeTagKey(DUMPLING_WRAPPING);
        removeEmptyTag(stack);
    }

    // ---------------- 厨师名 ----------------

    @Nullable
    public static String getAuthor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(DUMPLING_AUTHOR) ? tag.getString(DUMPLING_AUTHOR) : null;
    }

    public static void setAuthor(ItemStack stack, String author) {
        stack.getOrCreateTag().putString(DUMPLING_AUTHOR, author);
    }

    // ---------------- 显示名与 Lore（1.20.1 的 display NBT） ----------------

    public static void setCustomName(ItemStack stack, Component name) {
        stack.getOrCreateTagElement("display").putString("Name", Component.Serializer.toJson(name));
    }

    public static void setLore(ItemStack stack, java.util.List<Component> lore) {
        ListTag list = new ListTag();
        for (Component line : lore) {
            list.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        stack.getOrCreateTagElement("display").put("Lore", list);
    }

    // ---------------- 死亡回溯点（玩家 persistentData） ----------------

    public static void setRewindPos(net.minecraft.world.entity.player.Player player, GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension().location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(pos.pos()));
        player.getPersistentData().put(REWIND_POS, tag);
    }

    public static GlobalPos getRewindPos(net.minecraft.world.entity.player.Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(REWIND_POS);
        if (!tag.contains("dim")) {
            return GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO);
        }
        // 1.20.1 按 ResourceKey<Level> 反查维度
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(tag.getString("dim")));
        return GlobalPos.of(key, NbtUtils.readBlockPos(tag.getCompound("pos")));
    }

    // ---------------- 内部 ----------------

    /** 移除键后如果整个 tag 变空则清掉，避免无意义 NBT 空标签影响堆叠匹配 */
    private static void removeEmptyTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}