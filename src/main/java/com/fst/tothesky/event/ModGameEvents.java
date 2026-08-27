package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.DrinkGlassBlock;
import com.fst.tothesky.cocktail.CocktailHelper;
import com.fst.tothesky.registry.ModAttachments;
import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.EnumSet;

@EventBusSubscriber(modid = ToTheSky.MODID)
public final class ModGameEvents {
    private ModGameEvents() {
    }

    /** 潜行 + 手持鸡尾酒右键坚实方块顶面 → 放置对应的鸡尾酒方块 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getFace() != Direction.UP) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isShiftKeyDown()) {
            return;
        }
        ResourceLocation cocktailId = CocktailHelper.cocktailId(stack);
        if (cocktailId == null) {
            return;
        }
        DrinkGlassBlock drinkBlock = ModBlocks.drinkBlockFor(cocktailId);
        if (drinkBlock == null) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
            return;
        }
        BlockPos placePos = pos.above();
        if (!level.getBlockState(placePos).isAir()) {
            return;
        }
        event.setCanceled(true);
        if (level.isClientSide) {
            return;
        }
        level.setBlock(placePos, drinkBlock.defaultBlockState()
                .setValue(DrinkGlassBlock.FACING, player.getDirection()), 3);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    /** 死亡回溯：持有 rewind 效果时受到致命伤，取消伤害并回溯到记录点 */
    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasEffect(ModEffects.REWIND)) {
            return;
        }
        if (player.getHealth() - event.getAmount() > 1.0f) {
            return;
        }
        GlobalPos rewindPos = player.getData(ModAttachments.REWIND_POS);
        ServerLevel targetLevel = player.server.getLevel(rewindPos.dimension());
        if (targetLevel == null) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0f);
        BlockPos pos = rewindPos.pos();
        player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                EnumSet.noneOf(RelativeMovement.class), player.getYRot(), player.getXRot());
        // 图腾动画与音效（等价于旧脚本强塞图腾触发的免死表现）
        targetLevel.broadcastEntityEvent(player, (byte) 35);
    }

    /** 谵妄：无法发言 */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (event.getPlayer().hasEffect(ModEffects.MADNESS)) {
            event.setCanceled(true);
        }
    }

    /** 登录时刷新死亡回溯记录点 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        player.setData(ModAttachments.REWIND_POS,
                GlobalPos.of(player.level().dimension(), BlockPos.containing(player.position())));
    }
}
