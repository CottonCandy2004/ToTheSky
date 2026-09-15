package com.fst.tothesky.mixin;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 锁链传动轮不再接受任何包裹——服务器性能策略（与 {@link FrogportChainTargetMixin} 配套）。
 * <p>
 * 玩家手持包裹右键锁链（{@code ChainPackageInteractionPacket} 的放置分支）会在消耗物品之前
 * 问一次 {@code canAcceptPackagesFor}，这里统一返回 false：包裹不会被吞掉，仍留在玩家手里。
 * 蛙港向锁链的 simulate 预检同样走这个方法，构成蛙港之外的第二道闸。
 * <p>
 * 注意本方法不等同于「链上传送」：传动轮之间搬运在途包裹走
 * {@code canAcceptMorePackagesFromOtherConveyor} 与 {@code addTravellingPackage}，不经过这里，
 * 故 Ponder 场景与既有的在途包裹搬运逻辑不受影响——在途包裹只有在「上游能产出包裹」时才可能出现，
 * 而两个入口（玩家手动放置、蛙港投递）都已被关掉。
 */
@Mixin(ChainConveyorBlockEntity.class)
public abstract class ChainConveyorPackageBanMixin {

    /**
     * 任何连接方向都不接受包裹（含循环链路与跨传动轮的连接）。
     */
    @Inject(method = "canAcceptPackagesFor", at = @At("HEAD"), cancellable = true)
    private void tothesky$rejectPackageInsertion(BlockPos connection, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
