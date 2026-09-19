package com.fst.tothesky.blockentity;

import com.fst.tothesky.registry.ModBlockEntities;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 扭蛋机方块实体：上方容器为奖品来源，下方容器为输出口。
 *
 * <p>交互逻辑（移植自 kjs sell&roll.js）：
 * <ul>
 *   <li>店主持抽奖券右键 → 绑定券的 NBT key 到机器的 key</li>
 *   <li>任何人右键 → 从上方容器随机取 1 件奖品：
 *     <ul>
 *       <li>有下方容器 → 直接插入下方容器（机械接入模式）</li>
 *       <li>无下方容器 → 要求手持匹配的抽奖券（消耗 1，给玩家物品）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>防吞/防刷保证：
 * <ul>
 *   <li>有下方容器时：先 simulate 提取确认有货 → 实际提取 → 插入下方容器；插入剩余丢地上（不吞）</li>
 *   <li>无下方容器时：先验证券 → simulate 提取 → 实际提取 → 消耗券 → 给玩家物品</li>
 *   <li>随机选奖品时遍历所有非空槽位，用有效索引而非原始 slot（避免跳过空槽位导致越界）</li>
 * </ul>
 *
 * 1.20.1 适配：抽奖券的绑定 key 直接存物品 NBT 根 tag（1.21 走 CUSTOM_DATA 组件）。
 *
 * <p>key 是 {@code double} 而非 long，与 kjs 保持一致（{@code Math.random()}）：旧存档里
 * 已绑定的抽奖券把 key 以 double 写在物品 NBT 上，券散落在箱子/背包/物流存储里无法枚举重写，
 * 只有机器侧也用 double 才能继续匹配上。
 */
public class RollerBlockEntity extends BlockEntity {

    private static final String TAG_OWNER_UUID = "owner_uuid";
    private static final String TAG_OWNER_NAME = "owner_name";
    private static final String TAG_KEY = "key";

    @Nullable private UUID ownerUuid;
    private String ownerName = "";
    private double key = ThreadLocalRandom.current().nextDouble();

    public RollerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROLLER.get(), pos, state);
    }

    // ---- owner 管理 ----

    public void setOwner(ServerPlayer player) {
        this.ownerUuid = player.getUUID();
        this.ownerName = player.getName().getString();
        setChanged();
    }

    public boolean isOwner(Player player) {
        if (ownerUuid != null) {
            return ownerUuid.equals(player.getUUID());
        }
        // kjs 时代只写了玩家名（ForgeData.owner，无 UUID）：名字命中即认作拥有者，
        // 并在服务器侧把 UUID 补写进新结构，之后一律走 UUID 比较
        if (ownerName.isEmpty() || !ownerName.equals(player.getName().getString())) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && level != null && !level.isClientSide) {
            ownerUuid = serverPlayer.getUUID();
            setChanged();
        }
        return true;
    }

    @Nullable public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public double key() { return key; }

    // ---- 交互 ----

    public InteractionResult onRightClick(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();

        // 店主持抽奖券：绑定
        if (isOwner(player) && mainHand.getItem() == ModItems.ROLLER_TICKET.get()) {
            if (mainHand.hasTag() && mainHand.getTag().contains(TAG_KEY)) {
                player.sendSystemMessage(Component.literal("无法覆盖已绑定的抽奖券"));
                return InteractionResult.PASS;
            }
            // 写入 NBT key（与 kjs 同为 double，旧券才匹配得上）
            mainHand.getOrCreateTag().putDouble(TAG_KEY, key);

            // 附魔光效（kjs: enchanted = true）——1.20.1 NBT：Enchantments 列表 + 隐藏附魔 flag 显示光效
            CompoundTag enchantTag = new CompoundTag();
            enchantTag.putString("id", "minecraft:unbreaking");
            enchantTag.putShort("lvl", (short) 1);
            mainHand.getOrCreateTag().getList("Enchantments", net.minecraft.nbt.Tag.TAG_COMPOUND).add(enchantTag);
            player.sendSystemMessage(Component.literal("抽奖券已绑定！"));
            return InteractionResult.CONSUME;
        }

        // 抽奖逻辑
        IItemHandler up = ContainerAccess.getItemHandler(level, worldPosition, Direction.UP);
        if (up == null) {
            player.sendSystemMessage(Component.literal("扭蛋机未正确配置！"));
            return InteractionResult.PASS;
        }

        // 收集所有非空槽位的索引（用索引而非物品副本，避免物品身份混淆）
        List<Integer> nonEmptySlots = new java.util.ArrayList<>();
        for (int i = 0; i < up.getSlots(); i++) {
            if (!up.getStackInSlot(i).isEmpty()) nonEmptySlots.add(i);
        }
        if (nonEmptySlots.isEmpty()) {
            player.sendSystemMessage(Component.literal("扭蛋机已空！"));
            return InteractionResult.PASS;
        }

        // 随机选一个非空槽位
        int selectedIndex = nonEmptySlots.get(ThreadLocalRandom.current().nextInt(nonEmptySlots.size()));

        IItemHandler down = ContainerAccess.getItemHandler(level, worldPosition, Direction.DOWN);

        if (down == null) {
            // 无下方容器：要求手持匹配的抽奖券
            if (mainHand.getItem() != ModItems.ROLLER_TICKET.get()) {
                player.sendSystemMessage(Component.literal("你需要手持抽奖券！"));
                return InteractionResult.PASS;
            }
            // 检查券的 key：旧券写的是 double（kjs Math.random()），新券也是 double
            CompoundTag ticketTag = mainHand.getTag();
            if (ticketTag == null || !ticketTag.contains(TAG_KEY)
                    || ticketTag.getDouble(TAG_KEY) != key) {
                player.sendSystemMessage(Component.literal("抽奖券与扭蛋机不匹配"));
                return InteractionResult.PASS;
            }

            // simulate 提取确认
            ItemStack simulated = up.extractItem(selectedIndex, 1, true);
            if (simulated.isEmpty()) {
                player.sendSystemMessage(Component.literal("扭蛋机出货失败！"));
                return InteractionResult.PASS;
            }

            // 实际提取
            ItemStack prize = up.extractItem(selectedIndex, 1, false);
            if (prize.isEmpty()) {
                player.sendSystemMessage(Component.literal("扭蛋机出货失败！"));
                return InteractionResult.PASS;
            }

            // 消耗券
            mainHand.shrink(1);
            if (mainHand.isEmpty()) player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);

            // 给玩家物品（不吞）
            giveItemToPlayer(player, prize);
        } else {
            // 有下方容器：直接输出（机械接入模式，无需券）
            ItemStack simulated = up.extractItem(selectedIndex, 1, true);
            if (simulated.isEmpty()) {
                return InteractionResult.PASS;
            }
            ItemStack prize = up.extractItem(selectedIndex, 1, false);
            if (prize.isEmpty()) {
                return InteractionResult.PASS;
            }
            // 插入下方容器，剩余丢地上（不吞）
            ItemStack remaining = ContainerAccess.insert(down, prize);
            if (!remaining.isEmpty()) {
                ItemEntity entity = new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() - 0.5, worldPosition.getZ() + 0.5,
                        remaining);
                entity.setPickUpDelay(20);
                level.addFreshEntity(entity);
            }
        }
        return InteractionResult.CONSUME;
    }

    private void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
            entity.setPickUpDelay(20);
            level.addFreshEntity(entity);
        }
    }

    // ---- NBT 持久化 ----

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putDouble(TAG_KEY, key);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            ownerUuid = tag.getUUID(TAG_OWNER_UUID);
        }
        ownerName = tag.getString(TAG_OWNER_NAME);
        if (tag.get(TAG_KEY) instanceof NumericTag stored) {
            key = stored.getAsDouble();
        }
        migrateFromKjs(tag);
    }

    /**
     * 迁移 kjs 时代留在 {@code ForgeData} 里的扭蛋机数据（owner / key）。
     * 旧键存在时以它为准：已迁移过的存档里本类的新键还是空的默认值，真实状态只在旧键里。
     */
    private void migrateFromKjs(CompoundTag tag) {
        CompoundTag legacy = ModNbt.kjsData(tag);
        if (legacy == null) {
            return;
        }
        if (legacy.contains(ModNbt.KJS_OWNER, Tag.TAG_STRING)) {
            ownerName = legacy.getString(ModNbt.KJS_OWNER);
            ownerUuid = null;
        }
        if (legacy.get(TAG_KEY) instanceof NumericTag stored) {
            key = stored.getAsDouble();
        }
        ModNbt.clearKjsKeys(this, tag, ModNbt.KJS_OWNER, TAG_KEY);
    }
}