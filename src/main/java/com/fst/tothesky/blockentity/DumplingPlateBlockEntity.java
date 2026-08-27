package com.fst.tothesky.blockentity;

import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (contents != null) {
            DumplingPlateContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents)
                    .ifSuccess(serialized -> tag.put(TAG_CONTENTS, serialized));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CONTENTS)) {
            contents = DumplingPlateContents.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(TAG_CONTENTS))
                    .result().orElse(null);
        } else {
            contents = null;
        }
    }
}
