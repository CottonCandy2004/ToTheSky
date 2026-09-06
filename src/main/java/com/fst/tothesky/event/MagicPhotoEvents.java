package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔法照片：潜行右键 exposure:photograph 且 NBT 含 XXXXAllowteleport="true" 时，
 * 传送至照片记录的坐标（移植自 magic_photo.js）。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class MagicPhotoEvents {
    private MagicPhotoEvents() {
    }

    @SubscribeEvent
    public static void onPhotoUse(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!stack.is(net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new net.minecraft.resources.ResourceLocation("exposure", "photograph")))) {
            return;
        }
        if (!player.isCrouching()) {
            return;
        }
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !"true".equals(nbt.getString("XXXXXAllowteleport"))) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        player.displayClientMessage(Component.literal("传送准备中，请稍候..."), false);
        // 粒子爆发
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 0.5, player.getZ(), 75, 0.3, 0.3, 0.3, 0.05);
        }
        // 50 tick 后传送
        com.fst.tothesky.util.DelayedTasks.schedule(player.getServer(), 50, () -> {
            if (nbt.contains("Pos")) {
                var posList = nbt.getList("Pos", net.minecraft.nbt.Tag.TAG_DOUBLE);
                if (posList.size() >= 3) {
                    player.teleportTo(posList.getDouble(0), posList.getDouble(1), posList.getDouble(2));
                    player.displayClientMessage(Component.literal("传送完成！"), false);
                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                                player.getX(), player.getY(), player.getZ(), 100, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }
        });
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
