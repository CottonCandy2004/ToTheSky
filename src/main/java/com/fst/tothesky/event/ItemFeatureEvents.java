package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块交互事件（移植自 kubejs/server_scripts/feature/block_events.js）：
 * a. 机械手润滑剂：主手润滑剂右键 create:deployer → 写入公共 Owner、播放 slime_added、耐久 -1、冷却 10t；
 * b. 箱子防潜影盒：潜行 + 双手潜影壳右键 forge:chests → 取消事件；
 * c. 行商召唤：主手 ultramarine:copper_cash_coin 右键钟 → 复制行商（重置交易次数）放到钟上方，
 *    消耗 1 枚硬币、提示「召唤了行商」，48000t 后 discard。ultramarine 为软依赖，缺失时整段跳过。
 * 所有实际逻辑仅服务端执行（客户端只做事件取消）。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class ItemFeatureEvents {
    private static final TagKey<Block> FORGE_CHESTS =
            TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "chests"));
    private static final ResourceLocation CREATE_DEPLOYER = new ResourceLocation("create", "deployer");
    private static final ResourceLocation CREATE_SLIME_ADDED = new ResourceLocation("create", "slime_added");
    private static final ResourceLocation ULTRAMARINE_COIN = new ResourceLocation("ultramarine", "copper_cash_coin");
    private static final ResourceLocation ULTRAMARINE_MERCHANT = new ResourceLocation("ultramarine", "travelling_merchant");

    /** 脚本中的公共 Owner（Create 机械手所有者，NBT [I;...] 四段） */
    private static final int[] SHARED_OWNER = {2112482347, -724487850, -1824816047, -774830698};

    private ItemFeatureEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // block_events.js 三个分支（含通用 rightClicked 内的 hand 检查）都要求主手
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack main = event.getItemStack();
        BlockState state = level.getBlockState(pos);

        // b. 箱子防潜影盒（block_events.js 末尾的通用 BlockEvents.rightClicked）：
        // 潜行 + 主手潜影壳 + 副手潜影壳 右键 forge:chests 标签方块 → 取消（服务端权威，客户端同步取消）
        if (player.isShiftKeyDown() && main.is(Items.SHULKER_SHELL)
                && player.getOffhandItem().is(Items.SHULKER_SHELL) && state.is(FORGE_CHESTS)) {
            event.setCanceled(true);
            return;
        }
        if (level.isClientSide) {
            return;
        }

        // a. 机械手润滑剂（block_events.js 的 BlockEvents.rightClicked("create:deployer") 分支）
        if (main.is(ModItems.DEPLOYER_LUBRICANT.get())) {
            lubricateDeployer(level, pos, state, player, main);
            return;
        }

        // c. 行商召唤（block_events.js 的 BlockEvents.rightClicked("minecraft:bell") 分支）
        if (ModList.get().isLoaded("ultramarine")) {
            Item coin = ForgeRegistries.ITEMS.getValue(ULTRAMARINE_COIN);
            if (coin != null && main.is(coin) && state.is(Blocks.BELL)
                    && !player.getCooldowns().isOnCooldown(coin)) {
                summonMerchant(level, pos, player, main);
            }
        }
    }

    private static void lubricateDeployer(Level level, BlockPos pos, BlockState state,
                                          Player player, ItemStack lubricant) {
        Block deployer = ForgeRegistries.BLOCKS.getValue(CREATE_DEPLOYER);
        if (deployer == null || !state.is(deployer)) {
            return;
        }
        // mergeEntityData("{Owner:[I;...]}"):读全量 NBT → putIntArray 覆盖 → load 回 → 标脏待存
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            CompoundTag tag = blockEntity.saveWithFullMetadata();
            tag.putIntArray("Owner", SHARED_OWNER);
            blockEntity.load(tag);
            blockEntity.setChanged();
        }
        // playsound create:slime_added voice @a[distance=..20] ~ ~ ~ 1 1 0.1
        // vanilla 解析为 volume=1 / pitch=1 / minVolume=0.1（playSound 无 minVolume 参数，音量 1 时无可听差异）
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(CREATE_SLIME_ADDED);
        if (sound != null) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                    SoundSource.VOICE, 1.0F, 1.0F);
        }
        lubricant.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        player.getCooldowns().addCooldown(lubricant.getItem(), 10);
    }

    private static void summonMerchant(Level level, BlockPos pos, Player player, ItemStack coinStack) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ULTRAMARINE_MERCHANT);
        if (type == null) {
            return;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Merchant merchant)) {
            return;
        }
        // 遍历旧 offers：每个 createTag → new MerchantOffer(tag) → resetUses 后塞回（每次召唤重置交易次数）
        List<MerchantOffer> rebuilt = new ArrayList<>();
        try {
            for (MerchantOffer offer : merchant.getOffers()) {
                MerchantOffer copy = new MerchantOffer(offer.createTag());
                copy.resetUses();
                rebuilt.add(copy);
            }
        } catch (Exception e) {
            ToTheSky.LOGGER.warn("行商交易列表重建失败，已中止召唤: {}", e.toString());
            return;
        }
        merchant.getOffers().clear();
        merchant.getOffers().addAll(rebuilt);

        // setPos(钟上方) + addFreshEntity；原脚本不取消钟的敲响
        entity.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        level.addFreshEntity(entity);
        player.displayClientMessage(Component.literal("召唤了行商"), true);
        coinStack.shrink(1);
        DelayedTasks.schedule(level.getServer(), 48000, entity::discard);
    }
}
