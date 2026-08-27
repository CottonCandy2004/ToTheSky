package com.fst.tothesky.dumpling;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 饺子烹饪会话管理器（仅厨锅）：替代旧脚本对厨锅内部烹饪流程的反射劫持。
 *
 * <p>防刷物品原则：输入物在会话开始时就真实进入容器；
 * 完成时先校验输入仍在原处，先核销输入再产出；
 * 容器被破坏时内容物由其自身掉落逻辑处理，会话直接作废。</p>
 */
@EventBusSubscriber(modid = ToTheSky.MODID)
public final class DumplingCookingManager {
    public static final SoundEvent MIXING_SOUND =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("create", "mixing"));
    public static final SoundEvent FINISH_SOUND =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("create", "schematicannon_finish"));

    private enum TickResult {RUNNING, PAUSED, FINISHED, CANCELLED}

    private record Session(ItemStack input, ItemStack result, int totalTicks, int elapsed) {
        Session ticked() {
            return new Session(input, result, totalTicks, elapsed + 1);
        }
    }

    private static final Map<GlobalPos, Session> SESSIONS = new HashMap<>();

    private DumplingCookingManager() {
    }

    public static boolean hasSession(GlobalPos key) {
        return SESSIONS.containsKey(key);
    }

    /** 厨锅会话：输入物（1 个）放入 0 号槽展示，完成后放入输出槽 */
    public static void startPotSession(ServerLevel level, BlockPos pos, CookingPotBlockEntity pot,
                                       ItemStack input, ItemStack result, int ticks) {
        pot.getInventory().setStackInSlot(0, input.copy());
        pot.setChanged();
        SESSIONS.put(GlobalPos.of(level.dimension(), pos),
                new Session(input.copy(), result, Math.max(ticks, 1), 0));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (Map.Entry<GlobalPos, Session> entry : SESSIONS.entrySet()) {
            GlobalPos key = entry.getKey();
            entry.setValue(tick(event, key, entry.getValue()));
        }
        SESSIONS.values().removeIf(session -> session == null);
    }

    /** @return 新会话状态；返回 null 表示会话结束 */
    private static Session tick(ServerTickEvent.Post event, GlobalPos key, Session session) {
        if (!(event.getServer().getLevel(key.dimension()) instanceof ServerLevel level)
                || !level.isLoaded(key.pos())) {
            return session; // 维度/区块未加载：原地等待
        }
        TickResult result = tickCookingPot(level, key.pos(), session);
        return switch (result) {
            case RUNNING -> session.ticked();
            case PAUSED -> session;
            case FINISHED, CANCELLED -> null;
        };
    }

    private static TickResult tickCookingPot(ServerLevel level, BlockPos pos, Session session) {
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return TickResult.CANCELLED; // 锅没了：内容物由锅自身的破坏掉落处理
        }
        ItemStackHandler inventory = pot.getInventory();
        if (!ItemStack.matches(inventory.getStackInSlot(0), session.input())) {
            return TickResult.CANCELLED; // 输入被玩家取走：作废，不产出
        }
        if (!pot.isHeated()) {
            return TickResult.PAUSED; // 失去热源：暂停计时
        }
        // 同步进度到 FD 厨锅 GUI（data[0]=cookTime, data[1]=cookTimeTotal）
        int elapsed = Math.min(session.elapsed() + 1, session.totalTicks());
        CookingPotProgressBridge.setProgress(pot, elapsed, session.totalTicks());
        if (session.elapsed() + 1 < session.totalTicks()) {
            return TickResult.RUNNING;
        }
        // 完成：先模拟产出，确认输出槽放得下再核销输入
        if (!inventory.insertItem(CookingPotBlockEntity.OUTPUT_SLOT, session.result().copy(), true).isEmpty()) {
            return TickResult.RUNNING; // 输出槽已满，等下一 tick 再试
        }
        inventory.extractItem(0, 1, false);
        inventory.insertItem(CookingPotBlockEntity.OUTPUT_SLOT, session.result().copy(), false);
        pot.setChanged();
        playFinishEffects(level, pos);
        return TickResult.FINISHED;
    }

    private static void playFinishEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, FINISH_SOUND, SoundSource.BLOCKS, 1.0f, 1.2f);
        com.fst.tothesky.util.DelayedTasks.schedule(level.getServer(), 6,
                () -> level.playSound(null, pos, FINISH_SOUND, SoundSource.BLOCKS, 1.0f, 0.9f));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }
}
