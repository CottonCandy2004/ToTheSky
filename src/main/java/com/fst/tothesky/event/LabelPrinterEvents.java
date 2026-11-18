package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;

/** 潜行时直接处理右键事件，避免原版跳过方块 use、转而使用手持物品。 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class LabelPrinterEvents {
    private LabelPrinterEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || player.isSpectator() || stack.isEmpty()
                || !event.getLevel().getBlockState(event.getPos()).is(ModBlocks.LABLE_PRINTER.get())) {
            return;
        }

        // 重复贴标也消费交互，防止随后放置或使用手持物品。
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
        if (event.getLevel().isClientSide || stack.hasTag() && stack.getTag().getBoolean(ModNbt.LABEL_PRINTED)) {
            return;
        }
        int cost = stack.getCount();
        int change = payPrintingFee(player, stack, cost);
        if (change < 0) {
            player.displayClientMessage(Component.literal("需要" + cost / 10 + "." + cost % 10 + "Δ现金"), true);
            return;
        }

        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        ItemStack nameTag = player.getOffhandItem();
        if (nameTag.is(Items.NAME_TAG) && nameTag.hasCustomHoverName()) {
            addLine(lore, nameTag.getHoverName().getString());
        }
        addLine(lore, "来自" + player.getGameProfile().getName());
        addLine(lore, "<" + LocalDate.now() + ">");
        display.put("Lore", lore);
        stack.getOrCreateTag().putBoolean(ModNbt.LABEL_PRINTED, true);
        // 先标记目标再找零，避免新三角片合并进待打印的钱栈、改变其数量。
        if (change > 0) {
            ItemStack changeStack = new ItemStack(ModItems.DELTA_COIN_CHIP.get(), change);
            if (!player.getInventory().add(changeStack)) {
                player.drop(changeStack, false);
            }
        }
    }

    /** 每件收费0.1Δ，整组预检；目标栈不参与付款。返回找零片数，余额不足返回-1。 */
    private static int payPrintingFee(Player player, ItemStack target, int cost) {
        var inventory = player.getInventory();
        int balance = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack payment = inventory.getItem(slot);
            if (payment == target) {
                continue;
            }
            if (payment.is(ModItems.DELTA_COIN_CHIP.get())) {
                balance += payment.getCount();
            } else if (payment.is(ModItems.DELTA_COIN.get())) {
                balance += payment.getCount() * 10;
            }
        }
        if (balance < cost) {
            return -1;
        }

        int remaining = cost;
        for (Item currency : new Item[]{ModItems.DELTA_COIN_CHIP.get(), ModItems.DELTA_COIN.get()}) {
            int value = currency == ModItems.DELTA_COIN_CHIP.get() ? 1 : 10;
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack payment = inventory.getItem(slot);
                if (payment == target || !payment.is(currency)) {
                    continue;
                }
                int take = Math.min(payment.getCount(), (remaining + value - 1) / value);
                payment.shrink(take);
                remaining -= take * value;
                if (payment.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        inventory.setChanged();
        return -remaining;
    }

    private static void addLine(ListTag lore, String text) {
        Component line = Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
    }
}
