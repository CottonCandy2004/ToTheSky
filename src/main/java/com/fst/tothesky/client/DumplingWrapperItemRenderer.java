package com.fst.tothesky.client;

import com.fst.tothesky.dumpling.ItemStackSnapshot;
import com.fst.tothesky.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 饺子皮渲染器：包制中（持有 DUMPLING_WRAPPING 组件）馅料浮在皮上并随进度起伏，
 * 第一人称下皮转至水平——动画参数照搬 Create 的 SandPaperItemRenderer。
 */
public class DumplingWrapperItemRenderer extends CustomRenderedItemModelRenderer {
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;
        float partialTicks = AnimationTickHolder.getPartialTicks();

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        ms.pushPose();

        ItemStackSnapshot wrapping = stack.get(ModDataComponents.DUMPLING_WRAPPING);
        if (wrapping != null && !wrapping.stack().isEmpty() && player != null) {
            ms.pushPose();

            if (transformType == ItemDisplayContext.GUI) {
                ms.translate(0.0F, 0.2f, 1.0F);
                ms.scale(0.75f, 0.75f, 0.75f);
            } else {
                int modifier = leftHand ? -1 : 1;
                ms.mulPose(Axis.YP.rotationDegrees(modifier * 40));
            }

            // 反向起伏（同砂纸）
            float time = player.getUseItemRemainingTicks() - partialTicks + 1.0F;
            if (time / (float) stack.getUseDuration(player) < 0.8F) {
                float bobbing = -Mth.abs(Mth.cos(time / 4.0F * (float) Math.PI) * 0.1F);
                if (transformType == ItemDisplayContext.GUI) {
                    ms.translate(bobbing, bobbing, 0.0F);
                } else {
                    ms.translate(0.0f, bobbing, 0.0F);
                }
            }

            itemRenderer.renderStatic(wrapping.stack(), ItemDisplayContext.GUI, light, overlay, ms, buffer,
                    player.level(), 0);
            ms.popPose();
        }

        if (firstPerson && player != null && player.getUseItemRemainingTicks() > 0) {
            int modifier = leftHand ? -1 : 1;
            ms.translate(modifier * .5f, 0, -.25f);
            ms.mulPose(Axis.ZP.rotationDegrees(modifier * 40));
            ms.mulPose(Axis.XP.rotationDegrees(modifier * 10));
            ms.mulPose(Axis.YP.rotationDegrees(modifier * 90));
        }

        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay, model.getOriginalModel());

        ms.popPose();
    }
}
