package com.fst.tothesky.item;

import com.fst.tothesky.registry.ModNbt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 饺子：吃下后按馅料生效。
 *
 * <p>两条分支（移植自 {@code dumpling_making.js} 的 {@code ItemEvents.foodEaten}）：
 * <ul>
 *   <li><b>可食用馅料</b>：委托馅料自己的 {@link Item#finishUsingItem}，让玩家真的吃下该物品——
 *       数值、含概率的药水效果、进食进度、打嗝音效、容器返还乃至紫颂果传送等自定义逻辑
 *       全部按原版执行（不是简单复制 foodProperties）；产生的容器（如碗）回收到背包。</li>
 *   <li><b>不可食用馅料</b>：原样给玩家一份（背包放不下则掉落）。</li>
 * </ul>
 *
 * <p>没有馅料（裸饺子）时什么都不做。
 */
public class CookedDumplingItem extends Item {
    public CookedDumplingItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // 先读馅料：super 会把饺子栈消耗掉
        ItemStack filling = ModNbt.getFilling(stack);
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (filling.isEmpty() || !(level instanceof ServerLevel) || !(entity instanceof ServerPlayer player)) {
            return result;
        }
        if (filling.getItem().isEdible()) {
            ItemStack leftover = filling.getItem().finishUsingItem(filling.copyWithCount(1), level, player);
            if (!leftover.isEmpty()) {
                player.getInventory().placeItemBackInInventory(leftover);
            }
        } else {
            player.getInventory().placeItemBackInInventory(filling.copyWithCount(1));
        }
        return result;
    }
}
