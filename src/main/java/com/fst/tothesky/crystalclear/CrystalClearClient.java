package com.fst.tothesky.crystalclear;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.GlassCasingBlock;
import com.fst.tothesky.block.GlassEncasedCogwheelBlock;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/**
 * 移植方块的客户端接线：渲染层、连接纹理、机壳连接性、Flywheel 可视化与方块实体渲染器。
 * <p>
 * 上游用 Registrate 把这些都在注册期挂好（{@code addLayer}/{@code connectedTextures}/
 * {@code casingConnectivity}/{@code renderer}/{@code visual}）；本 mod 不用 Registrate，
 * 于是等价地手动调用 Create 暴露的同一批入口：{@code CustomBlockModels}、{@code CasingConnectivity}、
 * {@code VisualizerRegistry}。时机都是“客户端资源重载/世界渲染之前”，与原 mod 一致。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalClearClient {

    /**
     * 玻璃方块一律 cutout，否则内部面会挡住连接纹理。
     * <p>
     * 1.20.1 里区块与物品的渲染层最终都走 {@code ItemBlockRenderTypes} 的这张表
     * （{@code IForgeBakedModel#getRenderTypes} 的默认实现就是查它），模型 JSON 里的
     * {@code render_type} 只影响按渲染层过滤面片的模型；两边都写上，表是权威来源。
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        for (RegistryObject<? extends Block> block : CrystalClearBlocks.allBlocks()) {
            ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
        }
        registerEncasedTextures();
        registerGlassCasingTextures();
        registerVisualizers();
    }

    /** 传动杆/齿轮：Create 原版渲染器（Flywheel 关闭时兜底） */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CrystalClearBlockEntities.GLASS_ENCASED_SHAFT.get(),
                context -> new ShaftRenderer<>(context));
        event.registerBlockEntityRenderer(CrystalClearBlockEntities.GLASS_ENCASED_COG.get(),
                EncasedCogRenderer::small);
        event.registerBlockEntityRenderer(CrystalClearBlockEntities.GLASS_ENCASED_LARGE_COG.get(),
                EncasedCogRenderer::large);
    }

    /** 玻璃机壳：全向连接纹理（机壳本身不参与机壳连接性，与上游一致） */
    private static void registerGlassCasingTextures() {
        for (Map.Entry<String, RegistryObject<com.fst.tothesky.block.GlassCasingBlock>> entry : CrystalClearBlocks.GLASS_CASINGS.entrySet()) {
            registerCT(entry.getValue().get(), CrystalClearSpriteShifts.glassCasing(entry.getKey(), false));
        }
        for (Map.Entry<String, RegistryObject<com.fst.tothesky.block.GlassCasingBlock>> entry : CrystalClearBlocks.CLEAR_GLASS_CASINGS.entrySet()) {
            registerCT(entry.getValue().get(), CrystalClearSpriteShifts.glassCasing(entry.getKey(), true));
        }
    }

    /** 包裹传动杆/齿轮：机壳贴图连接 + 扳手切换机壳外观时的连接性 */
    private static void registerEncasedTextures() {
        CrystalClearBlocks.GLASS_ENCASED_SHAFTS.forEach((casing, block) -> {
            CTSpriteShiftEntry shift = CrystalClearSpriteShifts.glassCasing(casing, false);
            registerCT(block.get(), new GlassEncasedCTBehaviour(shift));
            CreateClient.CASING_CONNECTIVITY.make(block.get(), shift, (state, face) -> true);
        });
        CrystalClearBlocks.CLEAR_GLASS_ENCASED_SHAFTS.forEach((casing, block) -> {
            CTSpriteShiftEntry shift = CrystalClearSpriteShifts.glassCasing(casing, true);
            registerCT(block.get(), new GlassEncasedCTBehaviour(shift));
            CreateClient.CASING_CONNECTIVITY.make(block.get(), shift, (state, face) -> true);
        });
        registerCogwheelTextures(CrystalClearBlocks.GLASS_ENCASED_COGWHEELS, false, false);
        registerCogwheelTextures(CrystalClearBlocks.CLEAR_GLASS_ENCASED_COGWHEELS, true, false);
        registerCogwheelTextures(CrystalClearBlocks.GLASS_ENCASED_LARGE_COGWHEELS, false, true);
        registerCogwheelTextures(CrystalClearBlocks.CLEAR_GLASS_ENCASED_LARGE_COGWHEELS, true, true);
    }

    private static void registerCogwheelTextures(Map<String, RegistryObject<GlassEncasedCogwheelBlock>> blocks,
                                                 boolean clear, boolean large) {
        blocks.forEach((casing, block) -> {
            CTSpriteShiftEntry shift = CrystalClearSpriteShifts.glassCasing(casing, clear);
            // 大齿轮只有竖直方向连接；小齿轮另有水平侧面（与上游一致）
            GlassEncasedCogCTBehaviour behaviour = large
                    ? new GlassEncasedCogCTBehaviour(shift)
                    : new GlassEncasedCogCTBehaviour(shift, Couple.create(
                            CrystalClearSpriteShifts.cogwheelSide(casing),
                            CrystalClearSpriteShifts.horizontal("encased_cogwheels/" + casing + "_encased_cogwheel_side")));
            registerCT(block.get(), behaviour);
            CreateClient.CASING_CONNECTIVITY.make(block.get(), shift, (state, face) -> isShaftFacingOutside(state, face));
        });
    }

    /**
     * 与上游一致的机壳连接判定：只有与旋转轴平行的那两个面、且该面方向上没有传动杆时才相连。
     * （传动杆位置由 {@code TOP_SHAFT}/{@code BOTTOM_SHAFT} 表示。）
     */
    private static boolean isShaftFacingOutside(BlockState state, Direction face) {
        if (!(state.getBlock() instanceof GlassEncasedCogwheelBlock)) {
            return false;
        }
        if (face.getAxis() != state.getValue(RotatedPillarKineticBlock.AXIS)) {
            return false;
        }
        // 面向正方向的那一侧看 TOP_SHAFT（传动杆露出来就不相连），反向看 BOTTOM_SHAFT
        return !state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? EncasedCogwheelBlock.TOP_SHAFT
                : EncasedCogwheelBlock.BOTTOM_SHAFT);
    }

    private static void registerCT(Block block, CTSpriteShiftEntry shift) {
        registerCT(block, new SimpleCTBehaviour(shift));
    }

    private static void registerCT(Block block, com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour behaviour) {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(block.builtInRegistryHolder().key().location(), model -> new CTModel(model, behaviour));
    }

    /** Flywheel 可视化：传动杆单轴旋转、齿轮大小两种；与 Create 原版包裹方块用的是同一批 visual */
    private static void registerVisualizers() {
        setVisualizer(CrystalClearBlockEntities.GLASS_ENCASED_SHAFT.get(),
                (context, be, partialTick) -> SingleAxisRotatingVisual.shaft(context, be, partialTick));
        setVisualizer(CrystalClearBlockEntities.GLASS_ENCASED_COG.get(), EncasedCogVisual::small);
        setVisualizer(CrystalClearBlockEntities.GLASS_ENCASED_LARGE_COG.get(), EncasedCogVisual::large);
    }

    private static <T extends BlockEntity> void setVisualizer(BlockEntityType<T> type,
                                                              SimpleBlockEntityVisualizer.Factory<T> factory) {
        VisualizerRegistry.setVisualizer(type, SimpleBlockEntityVisualizer.builder(type)
                .factory(factory)
                // 有 visual 时跳过原版渲染器，避免同一根轴画两遍（Create 对包裹方块也是这么设的）
                .skipVanillaRender(be -> true)
                .apply());
    }

    private CrystalClearClient() {
    }
}
