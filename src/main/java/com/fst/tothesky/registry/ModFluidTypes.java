package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 流体类型注册（下界合金产线的中间流体）。
 *
 * 1.20.1 Forge 的 FluidType 注册到 ForgeRegistries.Keys.FLUID_TYPES。
 * 这里给自定义 FluidType 覆写 initializeClient 提供贴图（1.20.5+ 才有
 * RegisterClientExtensionsEvent.registerFluidType，1.20.1 用此钩子注册
 * IClientFluidTypeExtensions）。
 *
 * 流体仅供 Create mixing/filling 配方引用 + 桶搬运，不在世界自然流动（noBlock）。
 */
public final class ModFluidTypes {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ToTheSky.MODID);

    /** 下界溶液：魔女因子/下界合金链中间流体 */
    public static final RegistryObject<FluidType> NETHERITE_LIQUAR =
            FLUID_TYPES.register("netherite_liquar", () -> new TexturedFluidType("tothesky", "netherite_liquar"));
    /** 不稳定下界溶液：下界溶液的上游 */
    public static final RegistryObject<FluidType> UNSTABLE_NETHERITE_LIQUAR =
            FLUID_TYPES.register("unstable_netherite_liquar",
                    () -> new TexturedFluidType("tothesky", "unstable_netherite_liquar"));
    /** 豆浆：豆腐链上游（大豆混合产出） */
    public static final RegistryObject<FluidType> BEAN_SAUSE =
            FLUID_TYPES.register("bean_sause", () -> new TexturedFluidType("tothesky", "bean_sause"));
    /** 大豆油：油炸鳕鱼用油（大豆压实产出） */
    public static final RegistryObject<FluidType> BEAN_OIL =
            FLUID_TYPES.register("bean_oil", () -> new TexturedFluidType("tothesky", "bean_oil"));
    /** 酱油：豆浆+下界疣混合 */
    public static final RegistryObject<FluidType> SOY_SAUSE =
            FLUID_TYPES.register("soy_sause", () -> new TexturedFluidType("tothesky", "soy_sause"));
    /** 恶魂之泪：灵魂珠/哭泣黑曜石链中间流体 */
    public static final RegistryObject<FluidType> GHAST_TEAR =
            FLUID_TYPES.register("ghast_tear", () -> new TexturedFluidType("tothesky", "ghast_tear"));

    private ModFluidTypes() {
    }

    /** 带客户端贴图绑定的 FluidType（still/flow 贴图位于 textures/block/<name>_still|flow.png） */
    public static class TexturedFluidType extends FluidType {
        private final String namespace;
        private final String name;

        public TexturedFluidType(String namespace, String name) {
            super(Properties.create());
            this.namespace = namespace;
            this.name = name;
        }

        @Override
        public void initializeClient(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return new ResourceLocation(namespace, "block/" + name + "_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return new ResourceLocation(namespace, "block/" + name + "_flow");
                }
            });
        }
    }
}