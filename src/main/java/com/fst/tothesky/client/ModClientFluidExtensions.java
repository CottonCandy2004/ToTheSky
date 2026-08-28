package com.fst.tothesky.client;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModFluidTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 流体客户端渲染：把 still/flow 贴图绑定到 FluidType。
 *
 * 1.21.1 流体在世界/桶里的渲染由 IClientFluidTypeExtensions 提供：
 * - 桶物品用原版 minecraft:item/bucket 外壳，内容贴图取自 FluidType 的扩展（自动）。
 * - 流体方块用 getStillTexture/getFlowingTexture。
 * 贴图位于 assets/tothesky/textures/block/<name>_still|flow.png（从 kubejs 复制）。
 */
@EventBusSubscriber(modid = ToTheSky.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModClientFluidExtensions {

    private ModClientFluidExtensions() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new FluidTextures("tothesky", "netherite_liquar"),
                ModFluidTypes.NETHERITE_LIQUAR.get());
        event.registerFluidType(new FluidTextures("tothesky", "unstable_netherite_liquar"),
                ModFluidTypes.UNSTABLE_NETHERITE_LIQUAR.get());
    }

    private record FluidTextures(String namespace, String name) implements IClientFluidTypeExtensions {
        @Override
        public ResourceLocation getStillTexture() {
            return ResourceLocation.fromNamespaceAndPath(namespace, "block/" + name + "_still");
        }

        @Override
        public ResourceLocation getFlowingTexture() {
            return ResourceLocation.fromNamespaceAndPath(namespace, "block/" + name + "_flow");
        }
    }
}
