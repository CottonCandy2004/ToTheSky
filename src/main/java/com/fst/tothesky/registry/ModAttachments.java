package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ToTheSky.MODID);

    /**
     * 死亡回溯的记录点。玩家登录与饮用饮品659时刷新。
     * 实际逻辑里该值总会先于效果被写入（饮品659 先记录再给药水的），
     * 默认的出生点仅作兜底，不会被正常使用到。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlobalPos>> REWIND_POS =
            ATTACHMENTS.register("rewind_pos", () -> AttachmentType
                    .builder(() -> GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO))
                    .serialize(GlobalPos.CODEC)
                    .build());

    private ModAttachments() {
    }
}
