package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModBlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 扳手拆除（PR#59）：潜行 + 扳手右键可移除的方块（动力雕刻台），
 * 播放 Create 的 WRENCH_REMOVE 音效并返还方块物品。移植自 wrench_remove_block.js。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class WrenchRemoveEvents {
    private WrenchRemoveEvents() {
    }

    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (state.getBlock() != ModBlocks.MECHANICAL_CHISEL_TABLE.get()) {
            return;
        }
        ItemStack itemInHand = player.getItemInHand(event.getHand());
        boolean isWrench = itemInHand.is(net.minecraft.tags.ItemTags.create(
                        new net.minecraft.resources.ResourceLocation("forge", "tools/wrench")))
                || net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(itemInHand.getItem())
                        .equals(new net.minecraft.resources.ResourceLocation("create", "wrench"));
        if (!isWrench || !player.isSecondaryUseActive()) {
            return;
        }
        if (!level.isClientSide) {
            // Create 6: AllSoundEvents.WRENCH_REMOVE —— 经 catnip/Create 内部注册表播放
            playWrenchRemove(level, event.getPos());
            if (!player.isCreative()) {
                ItemStack blockItem = new ItemStack(state.getBlock().asItem());
                if (!blockItem.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(blockItem);
                }
            }
            level.destroyBlock(event.getPos(), false);
        }
        player.swing(event.getHand(), true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void playWrenchRemove(Level level, net.minecraft.core.BlockPos pos) {
        var sound = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS
                .getValue(new net.minecraft.resources.ResourceLocation("create", "wrench_remove"));
        if (sound != null) {
            level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0f, level.random.nextFloat() * 0.5f + 0.5f);
        }
    }
}