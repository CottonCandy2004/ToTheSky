package com.fst.tothesky.item;

import com.fst.tothesky.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 采血套装plus（移植自 kubejs/server_scripts/feature/item_events.js 的 kubejs:hemostix_plus 分支）。
 * 右键：生命值 &lt;=5 只提示不能再抽血（不加冷却）；否则给 1 个血瓶、自伤 5、
 * actionbar 提示、冷却 30t、主手耐久 -1。文案与旧脚本逐字一致。
 */
public class HemostixPlusItem extends Item {
    public HemostixPlusItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
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
        if (!level.isClientSide) {
            drawBlood(player);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void drawBlood(Player player) {
        if (player.getHealth() <= 5.0F) {
            player.displayClientMessage(Component.literal("§c你不能再抽血了"), true);
            return;
        }
        // giveInHand：主手为空放主手，否则进背包，背包满则掉落
        giveOrDrop(player, new ItemStack(ModItems.BLOOD_BOTTLE.get()));
        // attack(5)：通用伤害源自伤 5
        player.hurt(player.damageSources().generic(), 5.0F);
        player.displayClientMessage(Component.literal("§c你感到血正在流出..."), true);
        player.getCooldowns().addCooldown(this, 30);
        player.getMainHandItem().hurtAndBreak(1, player,
                broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
