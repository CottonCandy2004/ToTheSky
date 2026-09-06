package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.RollerBlock;
import com.fst.tothesky.block.SellerBlock;
import com.fst.tothesky.blockentity.ContainerAccess;
import com.fst.tothesky.blockentity.RollerBlockEntity;
import com.fst.tothesky.blockentity.SellerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 售货机/扭蛋机相关事件：放置时记录 owner + 容器保护。
 *
 * <p>容器保护策略（替代 kjs 的"向容器 BE 写 owner NBT + 全局监听"）：
 * <ul>
 *   <li>机器破坏保护：{@link #onBreak} 非拥有者不可破坏机器</li>
 *   <li>容器开箱保护：{@link #onRightClickBlock} 右键容器时，若正下方是售货机/扭蛋机且 owner 不匹配，则 cancel</li>
 *   <li>机器上方有容器非空时，owner 也不能直接破坏机器（防误清）</li>
 * </ul>
 * 不向第三方容器 BE 写任何 NBT，不污染其它模组数据。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class VendingEvents {
    /**
     * 放置售货机/扭蛋机时：设置 owner，继承朝向。
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof SellerBlock) && !(state.getBlock() instanceof RollerBlock)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof SellerBlockEntity seller) {
            seller.setOwner(player);
        } else if (be instanceof RollerBlockEntity roller) {
            roller.setOwner(player);
        }
    }

    /**
     * 破坏机器时：非拥有者不可破坏；拥有者破坏时若上方容器有物品则警告阻止（防误清）。
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof SellerBlock) && !(state.getBlock() instanceof RollerBlock)) {
            return;
        }
        Player player = event.getPlayer();
        Level level = (Level) event.getLevel();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be instanceof SellerBlockEntity seller) {
            if (!seller.isOwner(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("你无法破坏：此售货机属于" + seller.ownerName() + "！"));
                return;
            }
            // 上方容器有物品 → 阻止（防误清）
            if (hasItemsAbove(level, event.getPos())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("请先清空上方容器再破坏售货机！"));
            }
        } else if (be instanceof RollerBlockEntity roller) {
            if (!roller.isOwner(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("你无法破坏：此扭蛋机属于" + roller.ownerName() + "！"));
                return;
            }
            if (hasItemsAbove(level, event.getPos())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("请先清空上方容器再破坏扭蛋机！"));
            }
        }
    }

    /**
     * 右键容器方块时：若正下方是售货机/扭蛋机且 owner 不匹配，则阻止开箱。
     * 仅拦截「紧贴机器上方」的容器右键交互——与 kjs 保护范围一致。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockPos containerPos = event.getPos();
        BlockPos belowPos = containerPos.below();
        BlockState belowState = event.getLevel().getBlockState(belowPos);

        if (!(belowState.getBlock() instanceof SellerBlock) && !(belowState.getBlock() instanceof RollerBlock)) {
            return;
        }

        // 只有上方容器（紧贴机器顶部）才保护
        if (!containerPos.equals(belowPos.above())) return;

        BlockEntity be = event.getLevel().getBlockEntity(belowPos);
        Player player = event.getEntity();
        if (be instanceof SellerBlockEntity seller) {
            if (!seller.isOwner(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("你无法打开：此容器属于" + seller.ownerName() + "！"));
            }
        } else if (be instanceof RollerBlockEntity roller) {
            if (!roller.isOwner(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("你无法打开：此容器属于" + roller.ownerName() + "！"));
            }
        }
    }

    private static boolean hasItemsAbove(Level level, BlockPos machinePos) {
        var handler = ContainerAccess.getItemHandler(level, machinePos, Direction.UP);
        return ContainerAccess.hasItems(handler);
    }
}