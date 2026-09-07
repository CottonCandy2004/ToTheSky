package com.fst.tothesky.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 遗忘之露（PR#56）：对自己驯服的宠物使用，解除主人绑定。
 * 移植自 kubejs/server_scripts/feature/dew_of_oblivion_use.js。
 */
@Mod.EventBusSubscriber(modid = com.fst.tothesky.ToTheSky.MODID)
public class DewOfOblivionItem extends Item {
    public DewOfOblivionItem(Properties properties) {
        super(properties);
    }

    /** 对驯服生物右键交互（PlayerInteractEvent.EntityInteract） */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof TamableAnimal target)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack item = event.getItemStack();
        if (!item.is(com.fst.tothesky.registry.ModItems.DEW_OF_OBLIVION.get())) {
            return;
        }
        if (event.getLevel().isClientSide) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!target.isTame()) {
            return;
        }
        if (!target.isOwnedBy(player)) {
            player.displayClientMessage(Component.literal("它的主人好像不是你呢……")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        target.setOwnerUUID(null);
        target.setTame(false);
        target.setOrderedToSit(false);
        target.setInSittingPose(false);

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 16 点环绕附魔粒子
            double cx = target.getX();
            double cy = target.getY() + target.getBbHeight() * 0.5;
            double cz = target.getZ();
            for (int i = 0; i < 16; i++) {
                double angle = (i / 16.0) * 2 * Math.PI;
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        cx + Math.cos(angle), cy + (serverLevel.random.nextDouble() - 0.5) * 0.5,
                        cz + Math.sin(angle), 1, 0, 0, 0, 0.1);
            }
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                    1.0f, 0.8f + serverLevel.random.nextFloat() * 0.4f);
        }
        player.displayClientMessage(Component.literal("它忘记了一切……"), true);
        if (!player.isCreative()) {
            item.shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("一滴清澈的液体，似乎能洗去记忆的尘埃。")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}