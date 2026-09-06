package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.blockentity.RollerBlockEntity;
import com.fst.tothesky.blockentity.SellerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ToTheSky.MODID);

    public static final RegistryObject<BlockEntityType<DumplingPlateBlockEntity>> DUMPLING_PLATE =
            BLOCK_ENTITIES.register("dumpling_plate", () -> BlockEntityType.Builder
                    .of(DumplingPlateBlockEntity::new, ModBlocks.COOKED_DUMPLING_PLATE.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<SellerBlockEntity>> SELLER =
            BLOCK_ENTITIES.register("seller", () -> BlockEntityType.Builder
                    .of(SellerBlockEntity::new, ModBlocks.SELLER.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<RollerBlockEntity>> ROLLER =
            BLOCK_ENTITIES.register("roller", () -> BlockEntityType.Builder
                    .of(RollerBlockEntity::new, ModBlocks.ROLLER.get())
                    .build(null));

    private ModBlockEntities() {
    }
}