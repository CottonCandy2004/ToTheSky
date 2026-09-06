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

    // 豆腐链 / 恶魂之泪链流体（noBlock，仅供 Create 配方与桶搬运）
    public static RegistryObject<ForgeFlowingFluid> BEAN_SAUSE;
    public static RegistryObject<ForgeFlowingFluid> BEAN_SAUSE_FLOWING;
    public static RegistryObject<Item> BEAN_SAUSE_BUCKET;
    public static RegistryObject<ForgeFlowingFluid> BEAN_OIL;
    public static RegistryObject<ForgeFlowingFluid> BEAN_OIL_FLOWING;
    public static RegistryObject<Item> BEAN_OIL_BUCKET;
    public static RegistryObject<ForgeFlowingFluid> SOY_SAUSE;
    public static RegistryObject<ForgeFlowingFluid> SOY_SAUSE_FLOWING;
    public static RegistryObject<Item> SOY_SAUSE_BUCKET;
    public static RegistryObject<ForgeFlowingFluid> GHAST_TEAR;
    public static RegistryObject<ForgeFlowingFluid> GHAST_TEAR_FLOWING;
    public static RegistryObject<Item> GHAST_TEAR_BUCKET;

    /** registerTrio 的产出缓存（static 块内赋值结束后搬运到上方具名字段） */
    private static final RegistryObject<?>[][] TRIO_CACHE = new RegistryObject<?>[4][3];
    private static int trioIndex = 0;

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

        registerTrio("bean_sause", ModFluidTypes.BEAN_SAUSE);
        registerTrio("bean_oil", ModFluidTypes.BEAN_OIL);
        registerTrio("soy_sause", ModFluidTypes.SOY_SAUSE);
        registerTrio("ghast_tear", ModFluidTypes.GHAST_TEAR);
        BEAN_SAUSE = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[0][0];
        BEAN_SAUSE_FLOWING = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[0][1];
        BEAN_SAUSE_BUCKET = (RegistryObject<Item>) TRIO_CACHE[0][2];
        BEAN_OIL = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[1][0];
        BEAN_OIL_FLOWING = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[1][1];
        BEAN_OIL_BUCKET = (RegistryObject<Item>) TRIO_CACHE[1][2];
        SOY_SAUSE = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[2][0];
        SOY_SAUSE_FLOWING = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[2][1];
        SOY_SAUSE_BUCKET = (RegistryObject<Item>) TRIO_CACHE[2][2];
        GHAST_TEAR = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[3][0];
        GHAST_TEAR_FLOWING = (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[3][1];
        GHAST_TEAR_BUCKET = (RegistryObject<Item>) TRIO_CACHE[3][2];
    }

    /** 注册一组 still/flowing/bucket（桶先注册，与上方下界溶液同模式） */
    private static void registerTrio(String name, RegistryObject<net.minecraftforge.fluids.FluidType> type) {
        final int i = trioIndex; // lambda 执行时 trioIndex 已递增，必须用局部快照
        RegistryObject<Item> bucket = BUCKETS.register(name + "_bucket", () -> new BucketItem(
                stillRef(i), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        RegistryObject<ForgeFlowingFluid> still = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(
                new ForgeFlowingFluid.Properties(type, stillRef(i), flowingRef(i)).bucket(bucketRef(i))));
        RegistryObject<ForgeFlowingFluid> flowing = FLUIDS.register(name + "_flowing", () -> new ForgeFlowingFluid.Flowing(
                new ForgeFlowingFluid.Properties(type, stillRef(i), flowingRef(i)).bucket(bucketRef(i))));
        TRIO_CACHE[trioIndex][0] = still;
        TRIO_CACHE[trioIndex][1] = flowing;
        TRIO_CACHE[trioIndex][2] = bucket;
        trioIndex++;
    }

    private static RegistryObject<ForgeFlowingFluid> stillRef(int i) {
        return (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[i][0];
    }

    private static RegistryObject<ForgeFlowingFluid> flowingRef(int i) {
        return (RegistryObject<ForgeFlowingFluid>) TRIO_CACHE[i][1];
    }

    private static RegistryObject<Item> bucketRef(int i) {
        return (RegistryObject<Item>) TRIO_CACHE[i][2];
    }

    private ModFluids() {
    }
}