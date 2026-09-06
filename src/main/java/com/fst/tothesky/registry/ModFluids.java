package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 下界合金产线的流体注册：still / flowing / 桶。
 *
 * 1.20.1 Forge 用 ForgeFlowingFluid（1.21 NeoForge 改名 BaseFlowingFluid）。
 * 仍用 still/flowing 双 Fluid 注册（约定：flowing id = <still>_flowing）。
 * noBlock（对应 kjs 的 .noBlock()）：Properties 不设 block，流体不在世界自然放置，
 * 仅供 Create mixing/filling 配方引用 + 桶搬运。
 *
 * still/flowing/bucket 互相引用。为避开 Java 静态字段初始化器的确定赋值/前向引用限制，
 * 字段不设 final、构造放进 static 块（lambda 内引用字段在运行时解析）。
 */
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, ToTheSky.MODID);
    /** 桶单独走 item 注册（与 ModItems.ITEMS 分开，便于管理） */
    public static final DeferredRegister<Item> BUCKETS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ToTheSky.MODID);

    public static RegistryObject<ForgeFlowingFluid> NETHERITE_LIQUAR;
    public static RegistryObject<ForgeFlowingFluid> NETHERITE_LIQUAR_FLOWING;
    public static RegistryObject<Item> NETHERITE_LIQUAR_BUCKET;

    public static RegistryObject<ForgeFlowingFluid> UNSTABLE_NETHERITE_LIQUAR;
    public static RegistryObject<ForgeFlowingFluid> UNSTABLE_NETHERITE_LIQUAR_FLOWING;
    public static RegistryObject<Item> UNSTABLE_NETHERITE_LIQUAR_BUCKET;

    static {
        // 下界溶液：桶先注册（被 still/flowing 的 Properties 引用）
        NETHERITE_LIQUAR_BUCKET = BUCKETS.register("netherite_liquar_bucket", () -> new BucketItem(
                NETHERITE_LIQUAR, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        NETHERITE_LIQUAR = FLUIDS.register("netherite_liquar", () -> new ForgeFlowingFluid.Source(
                new ForgeFlowingFluid.Properties(ModFluidTypes.NETHERITE_LIQUAR,
                        NETHERITE_LIQUAR, NETHERITE_LIQUAR_FLOWING).bucket(NETHERITE_LIQUAR_BUCKET)));
        NETHERITE_LIQUAR_FLOWING = FLUIDS.register("netherite_liquar_flowing", () -> new ForgeFlowingFluid.Flowing(
                new ForgeFlowingFluid.Properties(ModFluidTypes.NETHERITE_LIQUAR,
                        NETHERITE_LIQUAR, NETHERITE_LIQUAR_FLOWING).bucket(NETHERITE_LIQUAR_BUCKET)));

        // 不稳定下界溶液
        UNSTABLE_NETHERITE_LIQUAR_BUCKET = BUCKETS.register("unstable_netherite_liquar_bucket", () -> new BucketItem(
                UNSTABLE_NETHERITE_LIQUAR, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        UNSTABLE_NETHERITE_LIQUAR = FLUIDS.register("unstable_netherite_liquar", () -> new ForgeFlowingFluid.Source(
                new ForgeFlowingFluid.Properties(ModFluidTypes.UNSTABLE_NETHERITE_LIQUAR,
                        UNSTABLE_NETHERITE_LIQUAR, UNSTABLE_NETHERITE_LIQUAR_FLOWING).bucket(UNSTABLE_NETHERITE_LIQUAR_BUCKET)));
        UNSTABLE_NETHERITE_LIQUAR_FLOWING = FLUIDS.register("unstable_netherite_liquar_flowing", () -> new ForgeFlowingFluid.Flowing(
                new ForgeFlowingFluid.Properties(ModFluidTypes.UNSTABLE_NETHERITE_LIQUAR,
                        UNSTABLE_NETHERITE_LIQUAR, UNSTABLE_NETHERITE_LIQUAR_FLOWING).bucket(UNSTABLE_NETHERITE_LIQUAR_BUCKET)));
    }

    private ModFluids() {
    }
}