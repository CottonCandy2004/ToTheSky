package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 流体类型注册（下界合金产线的中间流体）。
 *
 * 1.21.1 NeoForge 把 ForgeFlowingFluid 改名为 BaseFlowingFluid；
 * FluidType 注册到 NeoForgeRegistries.FLUID_TYPES（静态同步注册表）。
 * 这里仅给最简 FluidType（默认属性），因流体仅供 Create mixing/filling 配方引用，
 * 不在世界自然流动（noBlock）。
 */
public final class ModFluidTypes {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, ToTheSky.MODID);

    /** 下界溶液：魔女因子/下界合金链中间流体 */
    public static final DeferredHolder<FluidType, FluidType> NETHERITE_LIQUAR =
            FLUID_TYPES.register("netherite_liquar", () -> new FluidType(FluidType.Properties.create()));
    /** 不稳定下界溶液：下界溶液的上游 */
    public static final DeferredHolder<FluidType, FluidType> UNSTABLE_NETHERITE_LIQUAR =
            FLUID_TYPES.register("unstable_netherite_liquar", () -> new FluidType(FluidType.Properties.create()));

    private ModFluidTypes() {
    }
}