package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 下界合金产线的流体注册：still / flowing / 桶。
 *
 * 1.21.1 NeoForge 用 BaseFlowingFluid（原 ForgeFlowingFluid 改名）。
 * 仍用 still/flowing 双 Fluid 注册（约定：flowing id = <still>_flowing）。
 * noBlock（对应 kjs 的 .noBlock()）：Properties 不设 block，流体不在世界自然放置，
 * 仅供 Create mixing/filling 配方引用 + 桶搬运。
 *
 * still/flowing/bucket 互相引用。为避开 Java 静态字段初始化器的确定赋值/前向引用限制，
 * 字段不设 final、构造放进 static 块（lambda 内引用字段在运行时解析）。
 */
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ToTheSky.MODID);
    /** 桶单独走 item 注册（与 ModItems.ITEMS 分开，便于管理） */
    public static final DeferredRegister<Item> BUCKETS =
            DeferredRegister.create(Registries.ITEM, ToTheSky.MODID);

    public static DeferredHolder<Fluid, BaseFlowingFluid> NETHERITE_LIQUAR;
    public static DeferredHolder<Fluid, BaseFlowingFluid> NETHERITE_LIQUAR_FLOWING;
    public static DeferredHolder<Item, Item> NETHERITE_LIQUAR_BUCKET;

    public static DeferredHolder<Fluid, BaseFlowingFluid> UNSTABLE_NETHERITE_LIQUAR;
    public static DeferredHolder<Fluid, BaseFlowingFluid> UNSTABLE_NETHERITE_LIQUAR_FLOWING;
    public static DeferredHolder<Item, Item> UNSTABLE_NETHERITE_LIQUAR_BUCKET;

    static {
        // 下界溶液：桶先注册（被 still/flowing 的 Properties 引用）
        NETHERITE_LIQUAR_BUCKET = BUCKETS.register("netherite_liquar_bucket", () -> new BucketItem(
                NETHERITE_LIQUAR.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        NETHERITE_LIQUAR = FLUIDS.register("netherite_liquar", () -> new BaseFlowingFluid.Source(
                new BaseFlowingFluid.Properties(ModFluidTypes.NETHERITE_LIQUAR,
                        NETHERITE_LIQUAR, NETHERITE_LIQUAR_FLOWING).bucket(NETHERITE_LIQUAR_BUCKET)));
        NETHERITE_LIQUAR_FLOWING = FLUIDS.register("netherite_liquar_flowing", () -> new BaseFlowingFluid.Flowing(
                new BaseFlowingFluid.Properties(ModFluidTypes.NETHERITE_LIQUAR,
                        NETHERITE_LIQUAR, NETHERITE_LIQUAR_FLOWING).bucket(NETHERITE_LIQUAR_BUCKET)));

        // 不稳定下界溶液
        UNSTABLE_NETHERITE_LIQUAR_BUCKET = BUCKETS.register("unstable_netherite_liquar_bucket", () -> new BucketItem(
                UNSTABLE_NETHERITE_LIQUAR.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        UNSTABLE_NETHERITE_LIQUAR = FLUIDS.register("unstable_netherite_liquar", () -> new BaseFlowingFluid.Source(
                new BaseFlowingFluid.Properties(ModFluidTypes.UNSTABLE_NETHERITE_LIQUAR,
                        UNSTABLE_NETHERITE_LIQUAR, UNSTABLE_NETHERITE_LIQUAR_FLOWING).bucket(UNSTABLE_NETHERITE_LIQUAR_BUCKET)));
        UNSTABLE_NETHERITE_LIQUAR_FLOWING = FLUIDS.register("unstable_netherite_liquar_flowing", () -> new BaseFlowingFluid.Flowing(
                new BaseFlowingFluid.Properties(ModFluidTypes.UNSTABLE_NETHERITE_LIQUAR,
                        UNSTABLE_NETHERITE_LIQUAR, UNSTABLE_NETHERITE_LIQUAR_FLOWING).bucket(UNSTABLE_NETHERITE_LIQUAR_BUCKET)));
    }

    private ModFluids() {
    }
}