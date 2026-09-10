package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.effect.HotPotatoEffect;
import com.fst.tothesky.registry.ModEffects;
import com.fst.tothesky.cocktail.CocktailHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Random;

/**
 * 鸡尾酒喝完时的自定义效果。
 *
 * 原 KubeJS 用「右键 + 32 tick 后数背包差值」的不稳定方案（food_events.js），
 * 此处改为监听 Forge 的 {@link LivingEntityUseItemEvent.Finish}——
 * 该事件在 {@code LivingEntity.completeUsingItem()} 里、服务端、喝完那一刻触发，
 * 且 getItem() 是消耗前的栈副本（仍含鸡尾酒 NBT），可直接读到鸡尾酒 id。
 *
 * 涉及鸡尾酒：
 * - fstwines:call_of_tahiti  → 全服聊天
 * - fstwines:free_nightingale → 随机送一件物品
 * - fstwines:dangerous_party  → 击鼓传花（效果自驱动，见 HotPotatoEffect）
 * - fstwines:shoal_in_dream   → 脚下放一张蓝色床（四方向找空位），否则给一张
 *
 * 1.20.1 适配：鸡尾酒 id 用 CocktailHelper 读 NBT，不引用 kk 类。
 * buff 本体在配方 JSON 的 content.effect 里（1.21 是 CocktailProperty 注册表项）。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class CocktailEffects {
    private static final Random RANDOM = new Random();

    private CocktailEffects() {
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        ResourceLocation id = CocktailHelper.cocktailId(stack);
        if (id == null) {
            return;
        }
        switch (id.toString()) {
            case "fstwines:call_of_tahiti" -> onCallOfTahiti(player);
            case "fstwines:free_nightingale" -> onFreeNightingale(player);
            case "fstwines:dangerous_party" -> onDangerousParty(player);
            case "fstwines:shoal_in_dream" -> onShoalInDream(player);
            default -> {
                // david / silent_midnight / lago_de_texcoco：无自定义效果，buff 由配方 effect 处理
            }
        }
    }

    // ---------------- fstwines:call_of_tahiti：全服聊天 ----------------

    private static void onCallOfTahiti(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        player.server.getPlayerList().broadcastSystemMessage(
                Component.empty()
                        .append(Component.literal("• ").withStyle(s -> s.withColor(0x55FF55)))
                        .append(Component.literal("<").withStyle(s -> s.withColor(0x555555)))
                        .append(Component.literal(name).withStyle(s -> s.withColor(0xAAAAAA)))
                        .append(Component.literal(">: ").withStyle(s -> s.withColor(0x555555)))
                        .append(Component.literal("我一定会上工的，嗝～").withStyle(s -> s.withColor(0xFFFFFF))),
                false);
    }

    // ---------------- fstwines:free_nightingale：随机送一件物品 ----------------

    private static final List<ItemStack> NIGHTINGALE_GIFTS = List.of(
            item("mynethersdelight", "bullet_pepper"),
            item("culturaldelights", "pickle"),
            item("create_confectionery", "caramelized_marshmellow_on_a_stick"),
            item("crabbersdelight", "pearl"));

    private static ItemStack item(String ns, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(ns, path));
        return new ItemStack(item == null ? Items.AIR : item, 4);
    }

    private static void onFreeNightingale(ServerPlayer player) {
        giveOrDrop(player, NIGHTINGALE_GIFTS.get(RANDOM.nextInt(NIGHTINGALE_GIFTS.size())));
    }

    // ---------------- fstwines:dangerous_party：击鼓传花 ----------------

    private static final int POTATO_DURATION = HotPotatoEffect.DURATION;

    private static void onDangerousParty(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        player.server.getPlayerList().broadcastSystemMessage(
                Component.literal(name + "饮下了危险派对！注意躲避他的攻击！"), false);
        // 给饮用者挂上 hot_potato：duration 编码本场剩余时长，效果自驱动到期击杀。
        // 持有的效果实例各自带独立时钟 → 多场游戏可同时进行，无需全服唯一标志。
        player.addEffect(new MobEffectInstance(ModEffects.HOT_POTATO.get(), POTATO_DURATION, 0));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, POTATO_DURATION, 0));
    }

    // ---------------- fstwines:shoal_in_dream：放/给一张蓝色床 ----------------

    private static void onShoalInDream(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos below = player.blockPosition().below();
        if (level.getBlockState(below).is(Blocks.AIR)) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos headPos = below.relative(facing);
                if (level.getBlockState(headPos).is(Blocks.AIR)) {
                    setBed(level, below, facing, BedPart.FOOT);
                    setBed(level, headPos, facing, BedPart.HEAD);
                    return;
                }
            }
        }
        giveOrDrop(player, new ItemStack(Items.BLUE_BED));
    }

    private static void setBed(net.minecraft.server.level.ServerLevel level, BlockPos pos, Direction facing, BedPart part) {
        BlockState state = Blocks.BLUE_BED.defaultBlockState()
                .setValue(BedBlock.PART, part)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        level.setBlock(pos, state, 3);
    }

    // ---------------- 工具 ----------------

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}