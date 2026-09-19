package com.fst.tothesky.blockentity;

import com.fst.tothesky.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一盘熟饺子的方块实体：内容物 NBT 原样透传，不做任何解释。
 *
 * <p>键形状与旧 KubeJS 的方块实体（{@code kubejs:cooked_dumpling_plate}，即 {@code BlockEntityJS}）
 * 完全一致——每份馅料与厨师名存在 {@code data.filling} / {@code data.author} 下。
 * 因此：
 * <ul>
 *   <li>旧存档里的方块实体数据类型不变，{@code kubejs:cooked_dumpling_plate} 经
 *       {@link com.fst.tothesky.event.RegistryAliasEvents} 的注册表别名落到
 *       {@code tothesky:dumpling_plate} 后，直接 {@link #load} 进 {@link #data()}，不丢一份馅料；</li>
 *   <li>仍在使用中的 v4Update 脚本（{@code dumpling_making.js} 按 {@code block.entityData.data.*}
 *       读写馅料/厨师名）无需改动。</li>
 * </ul>
 *
 * <p>{@link #saveAdditional} 写入的是 {@link #data} 的同一个对象引用，这是刻意为之：
 * 脚本侧 {@code entityData.data.put(...)} 改写的是
 * {@code saveWithoutMetadata().getCompound("data")} 返回的对象，只有活引用才会落到存档。
 */
public class DumplingPlateBlockEntity extends BlockEntity {
    /** 一盘固定 8 份 */
    public static final int SLOTS = 8;
    /** 找不到厨师名时的占位（与旧脚本一致） */
    public static final String UNKNOWN_AUTHOR = "Unknown";

    private static final String TAG_DATA = "data";
    private static final String TAG_FILLING = "filling";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_NAME = "name";
    /** 过渡版 1.20.1 移植（79efe9b 之前）用的键，读取时一并兼容 */
    private static final String TAG_CONTENTS_LEGACY = "contents";

    private CompoundTag data = new CompoundTag();

    public DumplingPlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUMPLING_PLATE.get(), pos, state);
    }

    /** 内容物标签的活引用（脚本与会写的调用方直接在其上操作） */
    public CompoundTag data() {
        return data;
    }

    /** 第 {@code index} 份馅料；缺省或不合法返回 EMPTY */
    public ItemStack fillingAt(int index) {
        if (!(data.get(TAG_FILLING) instanceof ListTag list) || index < 0 || index >= list.size()) {
            return ItemStack.EMPTY;
        }
        Tag entry = list.get(index);
        return entry instanceof CompoundTag compound ? ItemStack.of(compound) : ItemStack.EMPTY;
    }

    /**
     * 第 {@code index} 份的厨师名。
     * 新格式是 {@code {name: "..."}} 复合标签（旧脚本），过渡版移植写的是纯字符串，
     * 两种都读。
     */
    public String authorAt(int index) {
        if (!(data.get(TAG_AUTHOR) instanceof ListTag list) || index < 0 || index >= list.size()) {
            return UNKNOWN_AUTHOR;
        }
        Tag entry = list.get(index);
        if (entry instanceof CompoundTag compound && compound.contains(TAG_NAME, Tag.TAG_STRING)) {
            return compound.getString(TAG_NAME);
        }
        return entry.getId() == Tag.TAG_STRING ? entry.getAsString() : UNKNOWN_AUTHOR;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_DATA, data);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_DATA, Tag.TAG_COMPOUND)) {
            data = tag.getCompound(TAG_DATA);
        } else if (tag.contains(TAG_CONTENTS_LEGACY, Tag.TAG_COMPOUND)) {
            data = tag.getCompound(TAG_CONTENTS_LEGACY);
        } else {
            data = new CompoundTag();
        }
    }
}
