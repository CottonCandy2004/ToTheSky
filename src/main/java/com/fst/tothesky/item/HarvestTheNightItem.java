package com.fst.tothesky.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 收割黑夜（移植自 kubejs/server_scripts/feature/item_events.js 的 kubejs:harvest_the_night 分支）。
 * 右键：end_rod 粒子 400 个 + trident.return 音效，肃清 150 格内 Size:0 的普通幻翼
 * （传送到玩家上方 4 格后 kill）；存在非 0 Size 幻翼时仅提示无法下手；冷却 300t。
 * 聊天文案与旧脚本逐字一致（含 § 颜色代码）。
 */
public class HarvestTheNightItem extends Item {
    /** 原脚本 .glow(true)：恒有附魔光泽 */
    private static final int RADIUS = 150;

    public HarvestTheNightItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 脚本只在主手持有时生效；冷却中直接跳过
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            cullPhantoms(serverLevel, player);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void cullPhantoms(ServerLevel level, Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        // execute at @a run particle end_rod <pos> 1 1 1 0.25 400 normal
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 400, 1.0, 1.0, 1.0, 0.25);
        // playsound minecraft:item.trident.return player @a <pos> 1 1（player 频道 → SoundSource.PLAYER）
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.0F);

        // @e[type=phantom,distance=..150]：精确类型 + 玩家为中心 150 格欧氏距离
        AABB box = new AABB(x - RADIUS, y - RADIUS, z - RADIUS, x + RADIUS, y + RADIUS, z + RADIUS);
        double rangeSqr = (double) RADIUS * RADIUS;
        List<Phantom> phantoms = level.getEntitiesOfClass(Phantom.class, box,
                phantom -> phantom.getType() == EntityType.PHANTOM && phantom.distanceToSqr(player) <= rangeSqr);
        List<Phantom> normal = phantoms.stream().filter(phantom -> phantom.getPhantomSize() == 0).toList();
        List<Phantom> special = phantoms.stream().filter(phantom -> phantom.getPhantomSize() != 0).toList();

        // 脚本分别跑 5 条命令：先按条件 tellraw，再 tp + kill；三种提示可同时出现前两种
        if (!normal.isEmpty()) {
            player.sendSystemMessage(Component.literal("§8<§7收割黑夜§8>: §f肃清了周围的幻翼."));
        }
        if (!special.isEmpty()) {
            // 原脚本此处写作 tellraw @p]（多余括号），仅保留文本
            player.sendSystemMessage(Component.literal("§8<§7收割黑夜§8>: §f那不是普通的幻翼...我做不到..."));
        }
        if (phantoms.isEmpty()) {
            player.sendSystemMessage(Component.literal("§8<§7收割黑夜§8>: §f你的周围不存在幻翼."));
        }
        // tp @e[type=phantom,distance=..150,nbt={Size:0}] ~ ~4 ~（相对玩家位置）+ kill
        for (Phantom phantom : normal) {
            phantom.teleportTo(x, y + 4.0, z);
            phantom.kill();
        }
        player.getCooldowns().addCooldown(this, 300);
    }
}
