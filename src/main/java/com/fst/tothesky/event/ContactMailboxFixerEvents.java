package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 往来（Contact）回归后的区块数据修正（配合 {@code ContactChunkMigrationMixin}）。
 * <p>
 * KJS 时代的褪色邮箱是单格方块；调色板迁移把它改写成 mod 白色邮箱的下半格
 * （half=lower）。mod 的白色邮箱是双格 + BlockEntity 结构，本处理器在区块
 * 加载进世界时补齐：
 * <ul>
 *   <li>孤儿下格（上方不是同方块 upper）：上方是空气则补上半格</li>
 *   <li>半格无 BlockEntity：LevelChunk.setBlockState 对 hasBlockEntity 的
 *       状态自动创建 BE（调色板直接解析出的方块不经该路径，没有 BE）</li>
 * </ul>
 * setBlockState 会置 unsaved 标记，迁移随区块自动保存落盘，一次生效。
 * 仅服务端执行：客户端从网络同步修正后的数据。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class ContactMailboxFixerEvents {

    private ContactMailboxFixerEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        // 不要读 event.getLevel()：它会 NPE。ChunkSerializer.read 从两处发这个事件，
        // 非 FULL 状态（绝大多数区块加载/生成）走 ProtoChunk 那一支，事件由
        // ChunkEvent(chunk) → super(chunk.getWorldForge()) 构造，而 ChunkAccess.getWorldForge()
        // 的默认实现返回 null（只有 LevelChunk 覆写它返回 level）。
        // instanceof LevelChunk 既筛掉了 ProtoChunk 那一支，又保证拿到的就是可写的 LevelChunk，
        // 所以这里不需要（也没法）判客户端——read 的形参本来就是 ServerLevel。
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.isEmpty()) return;

        List<BlockPos> fixes = null;
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;

        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!isContactMailbox(state)) continue;
                    if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) continue;

                    BlockPos abovePos = pos.above();
                    BlockState above = chunk.getBlockState(abovePos);
                    BlockState expectedUpper = state
                            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                            .trySetValue(BlockStateProperties.OPEN, Boolean.FALSE);

                    boolean lowerNeedsPair = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                            && above != expectedUpper;
                    boolean upperNeedsPair = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                            && chunk.getBlockState(pos.below()).getBlock() != state.getBlock();
                    if (!lowerNeedsPair && !upperNeedsPair) continue;

                    if (fixes == null) fixes = new ArrayList<>();
                    fixes.add(pos.immutable());
                }
            }
        }

        if (fixes == null) return;

        int repaired = 0;
        for (BlockPos pos : fixes) {
            BlockState state = chunk.getBlockState(pos);
            BlockState upper = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                    .trySetValue(BlockStateProperties.OPEN, Boolean.FALSE);
            BlockPos abovePos = pos.above();

            if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                if (!chunk.getBlockState(abovePos).isAir()) continue; // 上格被占，放弃
                chunk.setBlockState(abovePos, upper, false); // 创建 upper + BE
            } else {
                BlockPos belowPos = pos.below();
                BlockState below = chunk.getBlockState(belowPos);
                if (!below.isAir()) continue;
                BlockState lower = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                chunk.setBlockState(belowPos, lower, false);
            }
            // 下格自身重放：挂 BE（若缺）并保持既有 BE 不变
            chunk.setBlockState(pos, state, false);
            repaired++;
            ToTheSky.LOGGER.info("[Contact迁移] 补齐邮箱结构 @ {}", pos);
        }
        if (repaired > 0) {
            ToTheSky.LOGGER.info("[Contact迁移] chunk {} 共补齐 {} 处邮箱", chunk.getPos(), repaired);
        }
    }

    private static boolean isContactMailbox(BlockState state) {
        return state.getBlock().getClass().getName().startsWith("com.flechazo.contact");
    }
}