package com.fst.tothesky.item;

import com.fst.tothesky.block.PlaceableFoodBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 带行为回调的可放置食物物品（PR#58）。
 * 非潜行右键直接进食（32 tick，EAT）；潜行右键放置方块；吃完触发 onEaten 回调
 * （返碗/烟花/换晴天等原 SpecialFoodItems 行为）。
 */
public class PlaceableFoodBlockItem extends BlockItem {
    private final int useDuration;
    private final Consumer<LivingEntity> eaten;

    public PlaceableFoodBlockItem(PlaceableFoodBlock block, Item.Properties properties, Consumer<LivingEntity> eaten) {
        super(block, properties);
        this.useDuration = 32;
        this.eaten = eaten;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        FoodProperties food = getFoodProperties();
        if (!player.isShiftKeyDown() && food != null && player.canEat(food.canAlwaysEat())) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return super.useOn(context);
        }
        return InteractionResult.PASS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return useDuration;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && eaten != null) {
            eaten.accept(entity);
        }
        return result;
    }
}