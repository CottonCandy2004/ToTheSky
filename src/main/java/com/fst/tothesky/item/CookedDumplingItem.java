package com.fst.tothesky.item;

import com.fst.tothesky.registry.ModDataComponents;
import com.fst.tothesky.dumpling.ItemStackSnapshot;
import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 熟饺子：吃下后根据馅料产生效果——
 * 可食用的馅料会叠加上其食物属性；TNT 会原地引爆（不破坏方块）；
 * 不可食用的馅料会原样还给玩家。
 */
public class CookedDumplingItem extends TooltipItem {
    public CookedDumplingItem(Properties properties) {
        super(properties, "cooked_dumpling", 0);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return result;
        }
        ItemStack filling = ItemStackSnapshot.unwrap(stack.get(ModDataComponents.DUMPLING_FILLING));
        if (filling.isEmpty()) {
            return result;
        }
        FoodProperties food = filling.get(DataComponents.FOOD);
        if (food != null) {
            // 应用馅料自身的食物属性（数值、效果）
            player.eat(serverLevel, filling.copy(), food);
        } else if (filling.is(Items.TNT)) {
            spawnFakeTnt(serverLevel, player);
        } else {
            ItemStack returned = filling.copyWithCount(1);
            if (!player.addItem(returned)) {
                player.drop(returned, false);
            }
        }
        return result;
    }

    /** 在玩家脚下生成一个不会破坏方块的"TNT 馅" */
    private static void spawnFakeTnt(ServerLevel level, ServerPlayer player) {
        PrimedTnt tnt = new PrimedTnt(level, player.getX(), player.getY() + 0.5, player.getZ(), player);
        player.displayClientMessage(Component.literal("你听到了滋滋滋的声响………").withStyle(ChatFormatting.DARK_RED), true);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.addFreshEntity(tnt);
        // 引信 80 tick，剩 2 tick 时提前引爆并移除实体（等效旧脚本的逐 tick 轮询）
        DelayedTasks.schedule(level.getServer(), 78, () -> {
            if (tnt.isRemoved()) {
                return;
            }
            level.explode(tnt, tnt.getX(), tnt.getY(0.0625), tnt.getZ(), 4.0f, Level.ExplosionInteraction.NONE);
            tnt.discard();
        });
    }
}
