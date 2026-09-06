package com.fst.tothesky.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 竹蜻蜓（移植自 kubejs/server_scripts/feature/item_events.js 的 kubejs:copter 分支）。
 * 右键：levitation 3 秒 amplifier 4（粒子显示）、cloud 粒子 200 个、冷却 300t、主手耐久 -1。
 */
public class CopterItem extends Item {
    public CopterItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 脚本只在主手持有时生效；冷却中直接跳过
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            // effect give ... minecraft:levitation 3 4 false：60t / amplifier 4 / 不隐藏粒子
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 4, false, true, true));
            // particle cloud <pos> 1 1 1 0.25 200 normal
            serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
                    200, 1.0, 1.0, 1.0, 0.25);
            player.getCooldowns().addCooldown(this, 300);
            player.getMainHandItem().hurtAndBreak(1, player,
                    broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
}
