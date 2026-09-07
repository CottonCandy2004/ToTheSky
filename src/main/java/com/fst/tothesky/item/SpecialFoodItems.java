package com.fst.tothesky.item;

import com.fst.tothesky.registry.ModEffects;
import com.fst.tothesky.registry.ModNbt;
import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * 食用后有特殊行为的特色食品。行为移植自
 * kubejs/server_scripts/feature/food_events.js，去掉了命令拼装，全部改为原生调用。
 *
 * 1.20.1 适配：烟花数据直接写 NBT（1.21 是 Fireworks 数据组件）；
 * 死亡回溯点存玩家 persistentData（1.21 是 AttachmentType）。
 */
public final class SpecialFoodItems {
    private SpecialFoodItems() {
    }

    private static void broadcast(ServerPlayer player, String message) {
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void giveOrDrop(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    // ---------------- 供 PlaceableFoodBlockItem 复用的原行为（PR#58 方块化不改变食用效果） ----------------

    /** 鱿鱼狂欢节：吃完返碗（原 BowlFoodItem 行为） */
    public static void squidFestivalEaten(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            giveOrDrop(player, new ItemStack(Items.BOWL));
        }
    }

    /** 幻翼虾仁：发光 60 秒 + 头顶四发礼花 */
    public static void phantomShrimpEaten(LivingEntity entity) {
        if (entity instanceof ServerPlayer player && entity.level() instanceof ServerLevel serverLevel) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
            int[] colors = {16711680, 16383744, 50943, 65311};
            for (int i = 0; i < colors.length; i++) {
                int color = colors[i];
                DelayedTasks.schedule(serverLevel.getServer(), 20 * (i + 1),
                        () -> spawnFirework(serverLevel, player, color));
            }
        }
    }

    /** 饮品659：记录死亡回溯点并给予 rewind 效果（可叠加时长） */
    public static void drink659Eaten(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ModNbt.setRewindPos(player,
                    GlobalPos.of(entity.level().dimension(), BlockPos.containing(player.position())));
            MobEffectInstance existing = player.getEffect(ModEffects.REWIND.get());
            int duration = 6000 + (existing != null ? existing.getDuration() : 0);
            player.addEffect(new MobEffectInstance(ModEffects.REWIND.get(), duration, 0));
        }
    }

    /** 晴天鳕鱼：食用后雨过天晴 */
    public static void sunshineCodEaten(LivingEntity entity) {
        if (entity instanceof ServerPlayer player && entity.level() instanceof ServerLevel serverLevel
                && serverLevel.dimensionType().hasSkyLight()) {
            serverLevel.setWeatherParameters(0, 12000 + serverLevel.random.nextInt(168000), false, false);
            broadcast(player, player.getGameProfile().getName() + "食用了晴天鳕鱼，善哉，天公作美！");
        }
    }

    /** 头顶礼花（ 幻翼虾仁 与 PlaceableFoodBlockItem 共用） */
    private static void spawnFirework(ServerLevel level, net.minecraft.world.entity.player.Player player, int color) {
        if (player.isRemoved()) {
            return;
        }
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        // 1.20.1 烟花 NBT：Fireworks{Flight,Explosions:[{Type,Colors,FadeColors,Trail,Flicker}]}
        CompoundTag explosion = new CompoundTag();
        explosion.putInt("Type", 0); // SMALL_BALL
        ListTag colors = new ListTag();
        colors.add(IntTag.valueOf(color));
        explosion.put("Colors", colors);
        explosion.put("FadeColors", colors.copy());
        explosion.putBoolean("Trail", true);
        explosion.putBoolean("Flicker", false);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag fireworks = new CompoundTag();
        fireworks.put("Explosions", explosions);
        fireworks.putInt("Flight", 1);
        CompoundTag tag = new CompoundTag();
        tag.put("Fireworks", fireworks);
        rocket.setTag(tag);
        FireworkRocketEntity firework = new FireworkRocketEntity(level, player.getX(), player.getY() + 2, player.getZ(), rocket);
        level.addFreshEntity(firework);
    }

    /** 焦糖鳕鱼羹：吃掉自己 1 颗心，残血时直接致命。碗装食物，吃完返碗（BowlFoodItem） */
    public static class CaramelCodSoup extends net.minecraft.world.item.BowlFoodItem {
        public CaramelCodSoup(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            for (int i = 0; i < 5; i++) {
                tooltip.add(Component.translatable("tooltip.tothesky.caramel_cod_soup." + i)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                if (player.getHealth() <= 2.0f) {
                    broadcast(player, player.getGameProfile().getName() + "摄入焦糖鳕鱼羹过多而死");
                    player.hurt(player.damageSources().generic(), 20.0f);
                } else {
                    player.displayClientMessage(Component.literal("§c是错觉吗？似乎胃里有什么蹦跳了一下"), true);
                }
                player.hurt(player.damageSources().generic(), 2.0f);
            }
            return result;
        }
    }

    /** 鱿鱼狂欢节：碗装食物，吃完返碗（BowlFoodItem） */
    public static class SquidFestival extends net.minecraft.world.item.BowlFoodItem {
        public SquidFestival(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            for (int i = 0; i < 8; i++) {
                tooltip.add(Component.translatable("tooltip.tothesky.squid_festival." + i)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    /** 幻翼虾仁：发光 60 秒，并每隔 1 秒在头顶放一发礼花，共四发 */
    public static class PhantomShrimp extends TooltipItem {
        private static final int[] COLORS = {16711680, 16383744, 50943, 65311};

        public PhantomShrimp(Properties properties) {
            super(properties, "phantom_shrimp", 0);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
                for (int i = 0; i < COLORS.length; i++) {
                    int color = COLORS[i];
                    DelayedTasks.schedule(serverLevel.getServer(), 20 * (i + 1),
                            () -> spawnFirework(serverLevel, player, color));
                }
            }
            return result;
        }

    }

    /** 劲爆鳕鱼堡：入口即爆。在玩家头上方一格生成强度 4.0 的真实爆炸 */
    public static class BombCodBurger extends TooltipItem {
        public BombCodBurger(Properties properties) {
            super(properties, "bomb_cod_burger", 6);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player && level instanceof ServerLevel serverLevel) {
                // 真正的爆炸：在玩家头上方一格生成强度 4.0 的爆炸。
                // 声音、粒子、实体伤害/击退、以及原版爆炸死亡信息全部由 Explosion 生成；
                // NONE 不破坏方块（与饺子 TNT 馅一致）。
                // 注意 source 传 null：getEntities(source, ...) 会排除 source 本身，
                // 若传 player，吃下汉堡的玩家反而不会受到爆炸伤害。
                serverLevel.explode(null, player.getX(), player.getY() + 1, player.getZ(),
                        4.0f, Level.ExplosionInteraction.NONE);
            }
            return result;
        }
    }

    /** 三角粥：buff 大礼包 + 冷却 60 秒 */
    public static class DeltaPorridge extends TooltipItem {
        public DeltaPorridge(Properties properties) {
            super(properties, "delta_porridge", 0);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                player.getCooldowns().addCooldown(this, 1200);
                giveOrDrop(player, new ItemStack(Items.BOWL));
                player.addEffect(new MobEffectInstance(ModEffects.FAIR_PLAY.get(), 1200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 0));
            }
            return result;
        }
    }

    /** 饮品659：记录当前位置并赋予死亡回溯 */
    public static class Drink659 extends TooltipItem {
        public Drink659(Properties properties) {
            super(properties, "drink659", 5);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                ModNbt.setRewindPos(player,
                        GlobalPos.of(level.dimension(), BlockPos.containing(player.position())));
                MobEffectInstance existing = player.getEffect(ModEffects.REWIND.get());
                int duration = 6000 + (existing != null ? existing.getDuration() : 0);
                player.addEffect(new MobEffectInstance(ModEffects.REWIND.get(), duration, 0));
            }
            return result;
        }
    }

    /** 秘封洋葱绿叶肥虫汤：赋予谵妄（时长可叠加） */
    public static class BugSoup extends TooltipItem {
        public BugSoup(Properties properties) {
            super(properties, "bug_soup", 0);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                MobEffectInstance existing = player.getEffect(ModEffects.MADNESS.get());
                int duration = 2400 + (existing != null ? existing.getDuration() : 0);
                player.addEffect(new MobEffectInstance(ModEffects.MADNESS.get(), duration, 0));
            }
            return result;
        }
    }

    /** 晴天鳕鱼：食用后雨过天晴 */
    public static class SunshineCod extends TooltipItem {
        public SunshineCod(Properties properties) {
            super(properties, "sunshine_cod", 0);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack result = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player && level instanceof ServerLevel serverLevel
                    && serverLevel.dimensionType().hasSkyLight()) {
                serverLevel.setWeatherParameters(0, 12000 + serverLevel.random.nextInt(168000), false, false);
                broadcast(player, player.getGameProfile().getName() + "食用了晴天鳕鱼，善哉，天公作美！");
            }
            return result;
        }
    }
}