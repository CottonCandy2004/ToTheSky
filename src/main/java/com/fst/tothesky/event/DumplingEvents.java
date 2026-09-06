package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.dumpling.DumplingCookingManager;
import com.fst.tothesky.dumpling.DumplingFactory;
import com.fst.tothesky.dumpling.DumplingNamer;
import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.registry.ModNbt;
import com.fst.tothesky.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

/**
 * 饺子玩法的交互入口：厨锅煮制。
 * 移植自 dumpling_making.js，全部改为强类型调用，不再反射。
 * 包制为手持长按（见 {@link com.fst.tothesky.item.DumplingWrapperItem}），不再走砧板。
 * 森罗物语汤锅的饺子烹饪已移除（不做兼容）。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class DumplingEvents {
    private DumplingEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof CookingPotBlockEntity pot) {
            onCookingPot(event, pot);
        }
    }

    // ---------------- 厨锅：煮单个饺子 / 煮一盘饺子 ----------------

    private static void onCookingPot(PlayerInteractEvent.RightClickBlock event, CookingPotBlockEntity pot) {
        Player player = event.getEntity();
        ItemStack hand = event.getItemStack();
        boolean single = hand.is(ModItems.RAW_DUMPLING.get());
        boolean plate = hand.is(ModItems.RAW_DUMPLING_PLATE.get());
        if (!single && !plate) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        ItemStack result;
        int ticks;
        String message;
        if (single) {
            ItemStack filling = ModNbt.getFilling(hand);
            if (filling.isEmpty()) {
                status(player, "这个生饺子好像没有馅呢……");
                event.setCanceled(true);
                return;
            }
            DumplingNamer.Profile profile = DumplingNamer.profileFor(filling);
            if (profile == null) {
                status(player, "这个生饺子好像没有馅呢……");
                event.setCanceled(true);
                return;
            }
            String author = hand.getOrCreateTag().getString(ModNbt.DUMPLING_AUTHOR);
            result = DumplingFactory.cookedDumpling(filling, author.isEmpty() ? "Unknown" : author);
            ticks = profile.processTicks();
            message = "饺子已入锅，预计需要 %s 秒来煮熟！";
        } else {
            var tag = hand.getTag();
            DumplingPlateContents contents = tag != null && tag.contains(ModNbt.DUMPLING_PLATE)
                    ? DumplingPlateContents.load(tag.getCompound(ModNbt.DUMPLING_PLATE)) : null;
            if (contents == null || !contents.hasAnyFilling()) {
                status(player, "这盘生饺子好像没有馅呢……");
                event.setCanceled(true);
                return;
            }
            DumplingNamer.Profile profile = DumplingNamer.profileForPlate(contents.fillings());
            if (profile == null) {
                status(player, "这盘生饺子好像没有馅呢……");
                event.setCanceled(true);
                return;
            }
            result = DumplingFactory.cookedPlate(contents, profile);
            ticks = profile.processTicks();
            message = "一盘饺子已入锅，预计需要 %s 秒来煮熟！";
        }

        // 锅必须空闲：六个原料槽全空（旧脚本是直接清空弹出，改为拒绝更不易误吞物品）
        for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) {
            if (!pot.getInventory().getStackInSlot(slot).isEmpty()) {
                status(player, "锅里有别的东西，先清空再煮饺子……");
                return; // 不取消事件，允许正常打开锅界面
            }
        }
        event.setCanceled(true);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack input = hand.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            hand.shrink(1);
        }
        DumplingCookingManager.startPotSession(serverLevel, pos, pot, input, result, ticks);
        status(player, message.formatted(String.format("%.2f", ticks / 20.0)));
        level.playSound(null, pos, DumplingCookingManager.MIXING_SOUND, SoundSource.BLOCKS,
                0.5f, level.random.nextFloat() * 0.1f + 0.9f);
    }

    private static void status(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}