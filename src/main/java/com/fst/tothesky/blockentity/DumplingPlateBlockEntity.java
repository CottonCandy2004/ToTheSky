package com.fst.tothesky.blockentity;

import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 一盘熟饺子的方块实体：保存 8 份馅料与厨师名 */
public class DumplingPlateBlockEntity extends BlockEntity {
    private static final String TAG_CONTENTS = "contents";

    @Nullable
    private DumplingPlateContents contents;

    public DumplingPlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUMPLING_PLATE.get(), pos, state);
    }

    @Nullable
    public DumplingPlateContents contents() {
        return contents;
    }

    public void setContents(@Nullable DumplingPlateContents contents) {
        this.contents = contents;
        setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (contents != null) {
            tag.put(TAG_CONTENTS, contents.save());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_CONTENTS)) {
            contents = DumplingPlateContents.load(tag.getCompound(TAG_CONTENTS));
        } else {
            contents = null;
        }
    }
}