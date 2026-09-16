package com.fst.tothesky.mixin;

import com.fst.tothesky.ToTheSky;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 往来（Contact）回归的世界数据迁移：在区块反序列化前改写调色板字符串。
 * <p>
 * 历史：Contact 曾短暂离开整合包，KubeJS 用 startup 脚本在 {@code contact:} 命名空间
 * 顶替注册了 faded_mailbox / center_mailbox / red_postbox / green_postbox。现在 Contact
 * 回归，mod 重新拥有这些 ID，但 KJS 方块的状态定义与 mod 不一致：
 * <ul>
 *   <li>faded_mailbox：单格、只有 facing 属性；mod white_mailbox 是双格（half/open）</li>
 *   <li>红/绿邮筒：KJS 用 vanilla {@code Half} 枚举（top/bottom），mod 用
 *       {@code DoubleBlockHalf}（upper/lower）——同名字符串解析失败会直接变空气</li>
 * </ul>
 * 区块调色板经 {@code BlockState.CODEC}（vanilla BuiltInRegistries）解析，
 * MissingMappingsEvent 的 Forge 别名够不到这里，所以必须在 NBT 字符串层改写。
 * <p>
 * KJS bugfix 脚本的放置逻辑：玩家手放的格是 half=top（下格），脚本补的格是
 * half=bottom（上格）。对应转换为 top→lower、bottom→upper。
 * 上格/BlockEntity 的补齐由 {@code ContactMailboxFixerEvents} 在区块加载后完成。
 */
@Mixin(ChunkSerializer.class)
public abstract class ContactChunkMigrationMixin {

    private static final String CONTACT = "contact:";

    @Inject(method = "read", at = @At("HEAD"))
    private static void tothesky$migrateContactPalette(ServerLevel level, PoiManager poiManager, ChunkPos pos,
                                                        CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        try {
            ListTag sections = tag.getList("sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompound(i);
                if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
                CompoundTag blockStates = section.getCompound("block_states");
                ListTag palette = blockStates.getList("palette", Tag.TAG_STRING);
                if (palette.isEmpty()) continue;

                boolean changed = false;
                for (int j = 0; j < palette.size(); j++) {
                    String state = palette.getString(j);
                    if (!state.startsWith(CONTACT)) continue;
                    String migrated = migrate(state);
                    if (!migrated.equals(state)) {
                        palette.set(j, StringTag.valueOf(migrated));
                        changed = true;
                    }
                }
                if (changed) {
                    ToTheSky.LOGGER.info("[Contact迁移] chunk {} section Y={} 调色板已改写", pos, section.getByte("Y"));
                }
            }
        } catch (Exception e) {
            ToTheSky.LOGGER.error("[Contact迁移] chunk {} 调色板改写失败，保持原样", pos, e);
        }
    }

    /**
     * 单条状态字符串改写。无法识别的 contact: 条目原样返回（交由 codec 兜底成空气，
     * 与没有本迁移时的行为一致）。
     */
    private static String migrate(String state) {
        String name = state;
        String props = null;
        int bracket = state.indexOf('[');
        if (bracket >= 0) {
            name = state.substring(0, bracket);
            props = state.substring(bracket + 1, state.length() - 1);
        }
        switch (name) {
            case "contact:faded_mailbox" -> {
                String facing = extract(props, "facing");
                return "contact:white_mailbox[half=lower,open=false" + facing + "]";
            }
            case "contact:red_postbox", "contact:green_postbox" -> {
                String facing = extract(props, "facing");
                String half = extract(props, "half");
                // KJS: top=下格（玩家手放）→ mod lower；bottom=上格（脚本补）→ mod upper
                String modHalf = half.length() > 0 && half.substring(5).equals("top") ? "lower" : "upper";
                return name + "[half=" + modHalf + facing + "]";
            }
            default -> {
                return state;
            }
        }
    }

    /** 从 "facing=north,half=top" 里抽取 facing 片段为 ",facing=north"；缺失返回空串 */
    private static String extract(String props, String key) {
        if (props == null) return "";
        for (String kv : props.split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            if (kv.substring(0, eq).equals(key)) return "," + kv;
        }
        return "";
    }
}