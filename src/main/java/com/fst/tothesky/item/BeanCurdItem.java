package com.fst.tothesky.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 豆腐料理（甜豆花/咸豆腐脑/麻婆豆腐/浆果麻婆豆腐）。
 * 吃完返还一个碗；浆果麻婆豆腐有 20% 概率招来五雷轰顶（移植自 food_events.js）。
 */
public class BeanCurdItem extends Item {
    /** 是否为浆果麻婆豆腐（带雷击彩蛋） */
    private final boolean berry;

    public BeanCurdItem(Properties properties, boolean berry) {
        super(properties);
        this.berry = berry;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            if (berry && level.random.nextFloat() <= 0.2f) {
                for (int i = 0; i < 5; i++) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(player.getX(), player.getY(), player.getZ());
                        level.addFreshEntity(bolt);
                    }
                }
            }
            ItemStack bowl = new ItemStack(Items.BOWL);
            if (!player.addItem(bowl)) {
                player.drop(bowl, false);
            }
        }
        return result;
    }
}
