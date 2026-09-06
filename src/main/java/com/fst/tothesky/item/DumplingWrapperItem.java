package com.fst.tothesky.item;

import com.fst.tothesky.client.DumplingWrapperItemRenderer;
import com.fst.tothesky.dumpling.DumplingFactory;
import com.fst.tothesky.registry.ModNbt;
import com.fst.tothesky.registry.ModItems;
import com.simibubi.create.foundation.item.CustomUseEffectsItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * 饺子皮：任意手持皮、另一手持馅料，长按右键包制（参考机械动力砂纸打磨）。
 * 16t 完成，零粒子零声音；中止则退还馅料。
 *
 * 1.20.1 适配：包制状态存 ModNbt（1.21 是 DUMPLING_WRAPPING 数据组件）；
 * Create 0.5.1j 的 CustomUseEffectsItem.shouldTriggerUseEffects 返回 Boolean（非 TriState）；
 * 自定义渲染走 Item#initializeClient（1.20.5+ 才有 RegisterClientExtensionsEvent）。
 */
public class DumplingWrapperItem extends TooltipItem implements CustomUseEffectsItem {
    private static final int WRAP_TICKS = 16;

    public DumplingWrapperItem(Properties properties) {
        super(properties, "dumpling_wrapper", 1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 残留包制状态（如中途中断后 NBT 未清）：直接恢复包制
        if (!ModNbt.getWrapping(stack).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.pass(stack);
        }

        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack filling = player.getItemInHand(otherHand);
        if (filling.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        if (filling.is(ModItems.RAW_DUMPLING.get()) || filling.is(ModItems.COOKED_DUMPLING.get())
                || filling.is(ModItems.RAW_DUMPLING_PLATE.get()) || filling.is(ModItems.COOKED_DUMPLING_PLATE_ITEM.get())) {
            player.displayClientMessage(Component.literal("饺子皮已经足够厚了！"), true);
            return InteractionResultHolder.fail(stack);
        }

        ItemStack toWrap = player.getAbilities().instabuild ? filling.copyWithCount(1) : filling.split(1);
        ModNbt.setWrapping(stack, toWrap);
        player.startUsingItem(hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return stack;
        }
        ItemStack filling = ModNbt.getWrapping(stack);
        ModNbt.removeWrapping(stack);
        if (filling.isEmpty()) {
            return stack;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ItemStack dumpling = DumplingFactory.rawDumpling(filling, player.getGameProfile().getName());
        player.getInventory().placeItemBackInInventory(dumpling);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack filling = ModNbt.getWrapping(stack);
        if (!filling.isEmpty()) {
            player.getInventory().placeItemBackInInventory(filling);
        }
        ModNbt.removeWrapping(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return WRAP_TICKS;
    }

    /** 抑制原版吃食粒子与音效，仅保留 EAT 手部动作（Create 的 CustomItemUseEffectsMixin 消费此值） */
    @Override
    public Boolean shouldTriggerUseEffects(ItemStack stack, LivingEntity entity) {
        return false;
    }

    @Override
    public boolean triggerUseEffects(ItemStack stack, LivingEntity entity, int count, net.minecraft.util.RandomSource random) {
        return true;
    }

    /** Create 自定义渲染：包制中馅料浮在皮上（1.20.1 用 Item#initializeClient） */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new DumplingWrapperItemRenderer()));
    }
}