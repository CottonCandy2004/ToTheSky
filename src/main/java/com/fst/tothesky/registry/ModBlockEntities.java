package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ToTheSky.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DumplingPlateBlockEntity>> DUMPLING_PLATE =
            BLOCK_ENTITIES.register("dumpling_plate", () -> BlockEntityType.Builder
                    .of(DumplingPlateBlockEntity::new, ModBlocks.COOKED_DUMPLING_PLATE.get())
                    .build(null));

    private ModBlockEntities() {
    }
}
