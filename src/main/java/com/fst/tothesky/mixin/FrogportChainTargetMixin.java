package com.fst.tothesky.mixin;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 蛙港（Package Frogport）与锁链传动轮（Chain Conveyor）网络的解耦——服务器性能策略。
 * <p>
 * 动机：锁链网络上的每个包裹都要逐 tick 推进链位、做路由查表与端口匹配，规模上去后是纯 CPU
 * 负担，而本服务器不需要「锁链传包裹」这条产线。目标状态是让锁链网络上永远不存在包裹，
 * 于是蛙港与锁链网络之间唯一的两个接触点都要掐断：
 * <ul>
 *   <li>{@code export(..., simulate)}：蛙港向锁链写包裹的唯一出口。simulate 与落盘共用同一
 *       实现，故置 false 后：{@code tryPullingFromOwnAndAdjacentInventories} 的预检直接失败
 *       （不抽包裹、不播动画、不发声），也不会有「地址不符的包裹转挂回链」这一步。包裹留在
 *       原容器/蛙港内部，不掉落、不消失。若存档在投递动画中途被保存（{@code AnimatedPackage}
 *       走 NBT 恢复），动画会跑完并在落盘点走 {@code drop()} 掉到地上——物品仍在，不会凭空消失。</li>
 *   <li>{@code register(...)}：蛙港把自己登记为锁链端口（{@code loopPorts}/{@code travelPorts}
 *       与 {@code routingTable}）的唯一入口。注入点在 {@code getFilterString()} 调用处，跳过后
 *       恰好略去路由表 {@code receivePortInfo} 与端口表写入，而该方法前半段「锁链转向后把
 *       target 重新归位到另一端」的逻辑仍然执行，蛙港朝向不会错位。锁链传动轮由此不再认识任何
 *       蛙港：{@code exportToPort} 没有端口可遍历（蛙港不可能从链上截包裹），也不再有每 80 tick
 *       一次的路由表广播。</li>
 * </ul>
 * 两个注入都只拦服务器（{@code isClientSide}）：Ponder 场景（{@code PonderLevel} 是
 * client-side 的包装世界）与客户端渲染里蛙港照常工作，教程动画不受影响。车站蛙港走
 * {@code TrainStationFrogportTarget}，不在本 mixin 覆盖范围内，行为不变。
 */
@Mixin(PackagePortTarget.ChainConveyorFrogportTarget.class)
public abstract class FrogportChainTargetMixin {

    /**
     * 蛙港不得把包裹放上锁链：预检（simulate）与真实落盘一律拒绝。
     */
    @Inject(method = "export", at = @At("HEAD"), cancellable = true)
    private void tothesky$rejectExportToChain(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level l && !l.isClientSide()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 蛙港不得把自己登记为锁链端口：略去路由表与端口表的写入。
     */
    @Inject(
        method = "register",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortBlockEntity;getFilterString()Ljava/lang/String;"
        ),
        cancellable = true
    )
    private void tothesky$skipChainPortRegistration(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos,
                                                    CallbackInfo ci) {
        if (level instanceof Level l && !l.isClientSide()) {
            ci.cancel();
        }
    }
}
