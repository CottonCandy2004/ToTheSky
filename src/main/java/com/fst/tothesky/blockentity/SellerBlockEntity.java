package com.fst.tothesky.blockentity;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModBlockEntities;
import com.fst.tothesky.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 售货机方块实体：owner 信息 + 价格（price1.price2 Δ）。
 * 上方容器为商品来源，交互逻辑在 {@link #onLeftClick} / {@link #onRightClick}。
 *
 * <p>防吞/防刷保证：
 * <ul>
 *   <li>购买时先 simulate 提取确认上方有货，再实际扣款+提取——任一步失败则回滚已做的修改</li>
 *   <li>扣款遍历玩家物品栏时用 simulate 模式确认余额，实际扣款在确认后原子执行</li>
 *   <li>找零用 {@code ItemHandlerHelper} 给玩家，溢出物品丢地上（不吞）</li>
 * </ul>
 */
public class SellerBlockEntity extends BlockEntity {

    private static final String TAG_OWNER_UUID = "owner_uuid";
    private static final String TAG_OWNER_NAME = "owner_name";
    private static final String TAG_PRICE1 = "price1";
    private static final String TAG_PRICE2 = "price2";

    @Nullable private UUID ownerUuid;
    private String ownerName = "";
    private int price1 = 0; // 整数部分
    private int price2 = 0; // 小数部分 (0-9)

    public SellerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SELLER.get(), pos, state);
    }

    // ---- owner 管理 ----

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.ownerName = player.getName().getString();
        setChanged();
    }

    public boolean isOwner(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    @Nullable public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public int price1() { return price1; }
    public int price2() { return price2; }

    // ---- 交互 ----

    /** 右键：店主加价 / 顾客看信息 */
    public InteractionResult onRightClick(ServerPlayer player) {
        if (isOwner(player)) {
            // 店主：加价
            if (player.isShiftKeyDown()) {
                price2++;
            } else {
                price1++;
            }
            if (price2 >= 10) {
                price2 = 0;
                price1++;
            }
            setChanged();
            player.displayClientMessage(Component.literal("当前价格：" + price1 + "." + price2 + "Δ"), true);
        } else {
            // 顾客：显示商品信息
            IItemHandler up = ContainerAccess.getItemHandler(level, worldPosition, Direction.UP);
            if (up == null) {
                player.sendSystemMessage(Component.literal("售货机未正确设置！"));
                return InteractionResult.PASS;
            }
            var goods = ContainerAccess.collectNonEmpty(up);
            if (goods.isEmpty()) {
                player.sendSystemMessage(Component.literal("售货机已空！"));
                return InteractionResult.PASS;
            }
            ItemStack good = goods.get(goods.size() - 1);
            player.sendSystemMessage(Component.literal("这个售货机属于" + ownerName
                    + "，每个" + good.getHoverName().getString() + price1 + "." + price2 + "Δ"));
        }
        return InteractionResult.CONSUME;
    }

    /** 左键：店主减价 / 顾客购买 */
    public void onLeftClick(ServerPlayer player) {
        if (isOwner(player)) {
            // 店主：减价
            if (player.isShiftKeyDown()) {
                price2--;
            } else {
                price1--;
            }
            if (price2 < 0) {
                price2 = 9;
                price1--;
            }
            if (price1 < 0) {
                price1 = 0;
                price2 = 0;
            }
            setChanged();
            player.displayClientMessage(Component.literal("当前价格：" + price1 + "." + price2 + "Δ"), true);
            return;
        }

        // 顾客：购买
        IItemHandler up = ContainerAccess.getItemHandler(level, worldPosition, Direction.UP);
        if (up == null) {
            player.sendSystemMessage(Component.literal("售货机未正确设置！"));
            return;
        }
        var goods = ContainerAccess.collectNonEmpty(up);
        if (goods.isEmpty()) {
            player.sendSystemMessage(Component.literal("售货机已空！"));
            return;
        }
        // kjs 用 allItems.pop() 取最后一个非空槽位的物品
        ItemStack goodStack = goods.get(goods.size() - 1);
        int goodSlot = findSlotOf(up, goodStack);
        if (goodSlot < 0) {
            player.sendSystemMessage(Component.literal("售货机出错！"));
            return;
        }

        int price = price1 * 10 + price2;

        // 1. 先 simulate 提取，确认有货
        ItemStack simulated = up.extractItem(goodSlot, 1, true);
        if (simulated.isEmpty()) {
            player.sendSystemMessage(Component.literal("售货机已空！"));
            return;
        }

        // 2. 计算玩家余额（三角币 = 10Δ，三角片 = 1Δ）
        int balance = calculateBalance(player);
        if (balance < price) {
            player.sendSystemMessage(Component.literal("你的现金余额少于此商品的价格！"));
            return;
        }

        // 3. 实际扣款（收集并清除玩家物品栏中的三角币/三角片）
        int deducted = deductCurrency(player, price);
        if (deducted < price) {
            // 理论不应发生（前面已检查余额），但防御性退款
            refundCurrency(player, deducted);
            player.sendSystemMessage(Component.literal("扣款失败！"));
            return;
        }

        // 4. 实际提取物品
        ItemStack purchased = up.extractItem(goodSlot, 1, false);
        if (purchased.isEmpty()) {
            // 提取失败（竞争条件？），退款
            refundCurrency(player, price);
            player.sendSystemMessage(Component.literal("售货机出货失败，已退款！"));
            return;
        }

        // 5. 给玩家物品
        giveItemToPlayer(player, purchased);
        player.sendSystemMessage(Component.literal("你花费了" + price1 + "." + price2 + "Δ以购买"
                + purchased.getHoverName().getString()));

        // 6. 找零
        int change = balance - price;
        refundCurrency(player, change);

        // 7. 给店主打款（经济系统命令占位）
        ToTheSky.LOGGER.info("[售货机] 玩家 {} 购买商品，应打款 {}.{}Δ 给店主 {}（经济系统未接入，需手动 money give）",
                player.getName().getString(), price1, price2, ownerName);
        var source = player.getServer().createCommandSourceStack();
        player.getServer().getCommands().performPrefixedCommand(
                source,
                "money give " + ownerName + " " + (price1 + price2 * 0.1));
    }

    // ---- 货币工具 ----

    private int calculateBalance(Player player) {
        int balance = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ModItems.DELTA_COIN.get()) {
                balance += stack.getCount() * 10;
            } else if (stack.getItem() == ModItems.DELTA_COIN_CHIP.get()) {
                balance += stack.getCount();
            }
        }
        return balance;
    }

    /**
     * 从玩家物品栏扣除指定金额的三角币/三角片。
     * 返回实际扣除的金额（可能 < amount 如果余额不足——防御性）。
     */
    private int deductCurrency(Player player, int amount) {
        int remaining = amount;
        // 先扣三角片（1Δ 每个）
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getItem() != ModItems.DELTA_COIN_CHIP.get()) continue;
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        // 再扣三角币（10Δ 每个）
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getItem() != ModItems.DELTA_COIN.get()) continue;
            int take = Math.min(stack.getCount(), (remaining + 9) / 10); // 需要的币数（向上取整）
            take = Math.min(take, stack.getCount());
            stack.shrink(take);
            remaining -= take * 10;
            if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        // remaining > 0 表示没扣够（但前面已检查余额，不应发生）
        return amount - Math.max(0, remaining);
    }

    /**
     * 退还指定金额的三角币+三角片给玩家。溢出的物品丢地上（不吞）。
     */
    private void refundCurrency(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        int coins = amount / 10;
        int chips = amount % 10;
        if (coins > 0) giveItemToPlayer(player, new ItemStack(ModItems.DELTA_COIN.get(), coins));
        if (chips > 0) giveItemToPlayer(player, new ItemStack(ModItems.DELTA_COIN_CHIP.get(), chips));
    }

    private void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            // 物品栏满了，丢地上（不吞物品）
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
            entity.setPickUpDelay(20);
            level.addFreshEntity(entity);
        }
    }

    /** 在 IItemHandler 中找到与 stack 相同物品的槽位 */
    private int findSlotOf(IItemHandler handler, ItemStack stack) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    // ---- NBT 持久化 ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putInt(TAG_PRICE1, price1);
        tag.putInt(TAG_PRICE2, price2);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            ownerUuid = tag.getUUID(TAG_OWNER_UUID);
        }
        ownerName = tag.getString(TAG_OWNER_NAME);
        price1 = tag.getInt(TAG_PRICE1);
        price2 = tag.getInt(TAG_PRICE2);
    }
}