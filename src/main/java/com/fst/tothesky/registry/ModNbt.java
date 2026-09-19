package com.fst.tothesky.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 1.20.1 的 NBT 数据键与读写工具（替代 1.21 的数据组件注册）。
 *
 * <p>饺子相关的键沿用旧 KubeJS 脚本的**根标签**写法（{@code filling} / {@code author}），
 * 而非过渡版 1.20.1 移植用的 {@code dumpling_*} 前缀：RiaFST 4 的
 * {@code dumpling_making.js} 仍在读写 {@code item.nbt.filling} 等键，键名必须一致，
 * 否则脚本与模组会各自看到一份不同的数据。旧前缀只作为读取时的兼容分支保留。
 */
public final class ModNbt {
    /** 饺子的馅料（单个物品栈，挂在物品根标签下） */
    public static final String DUMPLING_FILLING = "filling";
    /** 饺子的厨师名（挂在物品根标签下） */
    public static final String DUMPLING_AUTHOR = "author";
    /** 一盘饺子内容物的外层键（过渡版 1.20.1 移植所用，仅读取兼容） */
    public static final String DUMPLING_PLATE_LEGACY = "dumpling_plate";
    /** 过渡版 1.20.1 移植的馅料/厨师键（仅读取兼容） */
    public static final String DUMPLING_FILLING_LEGACY = "dumpling_filling";
    public static final String DUMPLING_AUTHOR_LEGACY = "dumpling_author";

    /** 玩家死亡回溯记录点（存于 player.getPersistentData()） */
    public static final String REWIND_POS = "rewind_pos";

    private ModNbt() {
    }

    // ---------------- kjs 时代方块实体的 persistentData ----------------

    /**
     * kjs 时代（{@code BlockEntityJS}）的 {@code block.entity.persistentData} 落在 Forge 的
     * {@code ForgeData} 下而非 KJS 自己的 {@code data} 里——旧存档中售货机/扭蛋机的
     * owner、价格、抽奖券 key 都写在那里（KJS 的 {@code data} 是空的）。
     * 与饺子馅料同样是「读旧键、写新结构」的一次性迁移：迁移后旧键即被清除，状态只有一个来源。
     */
    public static final String KJS_FORGE_DATA = "ForgeData";
    /** kjs 记录的机器拥有者（值是玩家名——kjs 不记录 UUID） */
    public static final String KJS_OWNER = "owner";

    /** 只读探测方块实体存档里的 kjs 旧数据；无旧键返回 {@code null}（不创建空标签） */
    @Nullable
    public static CompoundTag kjsData(CompoundTag blockEntityTag) {
        CompoundTag legacy = blockEntityTag.getCompound(KJS_FORGE_DATA);
        return legacy.isEmpty() ? null : legacy;
    }

    /**
     * 清掉已迁移的 kjs 旧键。传入的存档标签与 Forge 保存时写回的活引用（{@code getPersistentData()}）
     * 都清一遍——两者通常是同一个对象，但不必假设。
     */
    public static void clearKjsKeys(BlockEntity be, CompoundTag blockEntityTag, String... keys) {
        CompoundTag legacy = blockEntityTag.getCompound(KJS_FORGE_DATA);
        CompoundTag live = be.getPersistentData();
        for (String key : keys) {
            legacy.remove(key);
            if (live != legacy) {
                live.remove(key);
            }
        }
    }

    // ---------------- 饺子馅料 / 厨师名 ----------------

    /** 读取馅料物品栈；无馅料返回 EMPTY */
    public static ItemStack getFilling(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag filling = compoundOf(tag, DUMPLING_FILLING, DUMPLING_FILLING_LEGACY);
        return filling == null ? ItemStack.EMPTY : ItemStack.of(filling);
    }

    /** 写入馅料（数量恒为 1） */
    public static void setFilling(ItemStack stack, ItemStack filling) {
        if (filling.isEmpty()) {
            stack.removeTagKey(DUMPLING_FILLING);
            return;
        }
        stack.getOrCreateTag().put(DUMPLING_FILLING, filling.copyWithCount(1).save(new CompoundTag()));
    }

    /** 读取厨师名；无记录返回 null */
    @Nullable
    public static String getAuthor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        if (tag.contains(DUMPLING_AUTHOR, Tag.TAG_STRING)) {
            return tag.getString(DUMPLING_AUTHOR);
        }
        return tag.contains(DUMPLING_AUTHOR_LEGACY, Tag.TAG_STRING) ? tag.getString(DUMPLING_AUTHOR_LEGACY) : null;
    }

    public static void setAuthor(ItemStack stack, String author) {
        stack.getOrCreateTag().putString(DUMPLING_AUTHOR, author);
    }

    /** 取出承载一盘饺子内容物的复合标签（新键直接挂在根上，旧键在 {@code dumpling_plate} 里）；无内容物返回 null */
    @Nullable
    public static CompoundTag plateContents(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (tag.contains(DUMPLING_PLATE_LEGACY, Tag.TAG_COMPOUND)) {
            return tag.getCompound(DUMPLING_PLATE_LEGACY);
        }
        return tag.contains(DUMPLING_FILLING) || tag.contains(DUMPLING_AUTHOR) ? tag : null;
    }

    @Nullable
    private static CompoundTag compoundOf(CompoundTag tag, String key, String legacyKey) {
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            return tag.getCompound(key);
        }
        return tag.contains(legacyKey, Tag.TAG_COMPOUND) ? tag.getCompound(legacyKey) : null;
    }

    // ---------------- 显示名与 Lore（1.20.1 的 display NBT） ----------------

    public static void setCustomName(ItemStack stack, Component name) {
        stack.getOrCreateTagElement("display").putString("Name", Component.Serializer.toJson(name));
    }

    public static void setLore(ItemStack stack, List<Component> lore) {
        ListTag list = new ListTag();
        for (Component line : lore) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(line)));
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
                net.minecraft.resources.ResourceLocation.parse(tag.getString("dim")));
        return GlobalPos.of(key, NbtUtils.readBlockPos(tag.getCompound("pos")));
    }
}
