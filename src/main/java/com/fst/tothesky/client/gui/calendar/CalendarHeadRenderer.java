package com.fst.tothesky.client.gui.calendar;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家头渲染：SkinManager.getInsecureSkinLocation 同步取皮肤 ResourceLocation
 * （未知名首次给默认皮肤并异步加载真皮肤，缓存后下一帧刷新为真皮肤）。
 * 脸部 8×8（源 8,8）+ 帽子层（源 40,8），8px 源放大到 size。
 */
public final class CalendarHeadRenderer {

    /** 玩家名 → 皮肤贴图（缓存；同一会话皮肤不变） */
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    private CalendarHeadRenderer() {
    }

    /** 在 (x, y) 渲染玩家头（size 见方），默认皮肤兜底，始终有输出 */
    public static void render(String playerName, GuiGraphics graphics, int x, int y, int size) {
        ResourceLocation skin = SKINS.computeIfAbsent(playerName, name -> {
            // 仅名字的 profile：按离线 UUID 规则推导，getInsecureSkinLocation 解析皮肤；取不到给默认皮肤
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
            GameProfile profile = new GameProfile(uuid, name);
            return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile);
        });
        // 皮肤 64×64；脸部源 (8,8) 8×8，放大到 size（11 参 blit：渲染尺寸独立于源尺寸）
        graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        // 帽子层源 (40,8)
        graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }
}