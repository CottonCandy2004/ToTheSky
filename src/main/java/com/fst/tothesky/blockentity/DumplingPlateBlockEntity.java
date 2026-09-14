package com.fst.tothesky.blockentity;

import com.fst.tothesky.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一盘熟饺子的方块实体：内容物 NBT 原样透传，不做任何解释。
 *
 * <p>键形状与旧 KubeJS 的方块实体（{@code kubejs:cooked_dumpling_plate}，即 {@code BlockEntityJS}）
 * 完全一致——每份馅料与厨师名存在 {@code data.filling} / {@code data.author} 下。
 * 因此：
 * <ul>
 *   <li>旧存档里的方块实体数据经 {@code MissingMappingEvents} 把 BE 类型 remap 到
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
    private static final String TAG_DATA = "data";
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
