package com.fst.tothesky.item;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One registered item serves both as a music disc and as a boulder placement item. */
public class RollingStoneRecordItem extends RecordItem {
    public RollingStoneRecordItem() {
        // Forge's supplier constructor accepts ticks, unlike the deprecated SoundEvent constructor.
        super(1, ModSounds.MUSIC_GIMME_SHELTER, new Properties().stacksTo(1), 5414);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(Blocks.JUKEBOX)) {
            // In particular, sneaking at an occupied jukebox must not place a boulder beside it.
            return state.getValue(JukeboxBlock.HAS_RECORD) ? InteractionResult.FAIL : super.useOn(context);
        }
        return placeStone(new BlockPlaceContext(context));
    }

    private InteractionResult placeStone(BlockPlaceContext context) {
        Level level = context.getLevel();
        Block block = ModBlocks.ROLLING_STONE.get();
        if (!block.isEnabled(level.enabledFeatures()) || !context.canPlace()) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = block.getStateForPlacement(context);
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        if (state == null || !state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, collision)) {
            return InteractionResult.FAIL;
        }
        // Match BlockItem placement without constructing an unregistered Item/intrusive holder.
        // ItemStack.useOn supplies Forge's placement events and cancellation rollback around this call.
        if (!level.setBlock(pos, state, 11)) return InteractionResult.FAIL;

        ItemStack stack = context.getItemInHand();
        BlockState placed = level.getBlockState(pos);
        if (placed.is(block)) {
            block.setPlacedBy(level, pos, placed, player, stack);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
            }
        }
        SoundType sound = placed.getSoundType(level, pos, player);
        level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, placed));
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public String getDescriptionId() {
        return ModBlocks.ROLLING_STONE.get().getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String key = "tooltip." + ToTheSky.MODID + ".rolling_stone";
        for (int i = 0; i < 3; ++i) {
            tooltip.add(Component.translatable(key + "." + i).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(key + ".record").withStyle(ChatFormatting.GRAY));
    }
}
