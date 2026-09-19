package com.fst.tothesky.crystalclear;

import com.fst.tothesky.ToTheSky;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 移植过来的三个方块实体类型（传动杆 / 齿轮 / 大齿轮），注册名与上游一致。
 * <p>
 * 方块实体类型注册表在 Forge 侧是 {@code disableSaving()} 的，旧存档不会为它产生缺失映射事件，
 * 而区块里的方块实体又是按名字解析的，因此 {@code crystal_clear:glass_encased_shaft} 这类旧名字
 * 必须靠 {@link com.fst.tothesky.event.RegistryAliasEvents} 注册的别名落到这里的注册项上。
 * 注册名保持上游命名，别名才有一一对应的目标。
 * <p>
 * Create 的方块实体在构造时就要拿到自己的 {@code BlockEntityType}，而字段初始化式里直接引用自身
 * 会被 javac 判为 self-reference；因此三个类型各由一个私有工厂方法创建（注册表只持有 supplier，
 * 真正执行时字段早已赋值）。
 */
public final class CrystalClearBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ToTheSky.MODID);

    /** 6 种玻璃包裹传动杆共用 */
    public static final RegistryObject<BlockEntityType<KineticBlockEntity>> GLASS_ENCASED_SHAFT =
            BLOCK_ENTITIES.register("glass_encased_shaft", CrystalClearBlockEntities::shaftType);

    /** 6 种玻璃包裹齿轮共用（小齿轮） */
    public static final RegistryObject<BlockEntityType<SimpleKineticBlockEntity>> GLASS_ENCASED_COG =
            BLOCK_ENTITIES.register("glass_encased_cog", CrystalClearBlockEntities::cogwheelType);

    /** 6 种玻璃包裹大齿轮共用 */
    public static final RegistryObject<BlockEntityType<SimpleKineticBlockEntity>> GLASS_ENCASED_LARGE_COG =
            BLOCK_ENTITIES.register("glass_encased_large_cog", CrystalClearBlockEntities::largeCogwheelType);

    private static BlockEntityType<KineticBlockEntity> shaftType() {
        return BlockEntityType.Builder
                .of((pos, state) -> new KineticBlockEntity(GLASS_ENCASED_SHAFT.get(), pos, state),
                        CrystalClearBlocks.encasedShaftBlocks())
                .build(null);
    }

    private static BlockEntityType<SimpleKineticBlockEntity> cogwheelType() {
        return BlockEntityType.Builder
                .of((pos, state) -> new SimpleKineticBlockEntity(GLASS_ENCASED_COG.get(), pos, state),
                        CrystalClearBlocks.encasedCogwheelBlocks())
                .build(null);
    }

    private static BlockEntityType<SimpleKineticBlockEntity> largeCogwheelType() {
        return BlockEntityType.Builder
                .of((pos, state) -> new SimpleKineticBlockEntity(GLASS_ENCASED_LARGE_COG.get(), pos, state),
                        CrystalClearBlocks.encasedLargeCogwheelBlocks())
                .build(null);
    }

    private CrystalClearBlockEntities() {
    }
}
