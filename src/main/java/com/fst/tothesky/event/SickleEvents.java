package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 镰刀范围收获：右键时以目标方块为中心收割 5x2x5 范围内的成熟作物与灌木。
 * 移植自 sickle_use.js，作用于 kaleidoscope_cookery 命名空间的镰刀。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class SickleEvents {
    private SickleEvents() {
    }

    @SubscribeEvent
    public static void onSickleUse(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack item = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (id == null || !id.getNamespace().equals("kaleidoscope_cookery") || !id.getPath().endsWith("_sickle")) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        // 射线追踪目标方块
        double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get())
                + (player.isCreative() ? 0.5 : 0);
        BlockHitResult hit = (BlockHitResult) player.pick(reach, 1.0f, false);
        BlockPos origin = hit.getBlockPos();
        int breakCount = 0;
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (harvestBlock(level, player, origin.offset(x, y, z))) {
                        breakCount++;
                    }
                }
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.swing(event.getHand(), true);
        player.sweepAttack();
        int finalCount = breakCount;
        item.hurtAndBreak(finalCount, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        player.getCooldowns().addCooldown(item.getItem(), 10);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean harvestBlock(Level level, Player player, BlockPos pos) {
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        // 收割黑名单
        if (state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                new ResourceLocation("kaleidoscope_cookery", "sickle_harvest_blacklist")))) {
            return false;
        }
        Block block = state.getBlock();

        if (block instanceof CropBlock crop) {
            // 水稻特判：kaleidoscope RiceCropBlock 的 LOCATION 属性
            BlockPos targetPos = pos;
            BlockState targetState = state;
            if (block.getClass().getName().equals("com.github.ysbbbbbb.kaleidoscopecookery.block.crop.RiceCropBlock")) {
                try {
                    var locationProp = (net.minecraft.world.level.block.state.properties.IntegerProperty)
                            block.getClass().getField("LOCATION").get(null);
                    int location = state.getValue(locationProp);
                    targetPos = pos.below(location);
                    targetState = level.getBlockState(targetPos);
                } catch (Exception ignored) {
                }
            }
            if (crop.isMaxAge(targetState)) {
                block.playerDestroy(level, player, targetPos, targetState, null, ItemStack.EMPTY);
                BlockState newState = crop.getStateForAge(0);
                if (newState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    newState = newState.setValue(BlockStateProperties.WATERLOGGED,
                            targetState.getValue(BlockStateProperties.WATERLOGGED));
                }
                level.setBlock(targetPos, newState, Block.UPDATE_ALL);
                level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, targetPos, Block.getId(targetState));
                return true;
            }
            return false;
        }

        if (block instanceof BushBlock) {
            if (player.isCreative()) {
                level.destroyBlock(pos, false, player);
            } else {
                level.destroyBlock(pos, true, player);
            }
            level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            return true;
        }
        return false;
    }
}
