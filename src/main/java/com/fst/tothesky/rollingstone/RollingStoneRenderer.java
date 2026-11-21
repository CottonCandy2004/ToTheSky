package com.fst.tothesky.rollingstone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;

/** Keeps each stone tile at the same texture scale as the surrounding terrain. */
public final class RollingStoneRenderer extends EntityRenderer<RollingStoneEntity> {
    private final BlockRenderDispatcher blocks;

    public RollingStoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0.9F;
    }

    @Override
    public void render(RollingStoneEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        Direction direction = entity.getRollDirection();
        float angle = entity.getRollAngle(partialTick);
        pose.pushPose();
        pose.translate(0.0D, 1.0D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(direction.getStepZ() * angle));
        pose.mulPose(Axis.ZP.rotationDegrees(-direction.getStepX() * angle));
        for (int x = -1; x < 1; x++) {
            for (int y = -1; y < 1; y++) {
                for (int z = -1; z < 1; z++) {
                    pose.pushPose();
                    pose.translate(x, y, z);
                    blocks.renderSingleBlock(Blocks.STONE.defaultBlockState(), pose, buffers,
                            packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    pose.popPose();
                }
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RollingStoneEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
