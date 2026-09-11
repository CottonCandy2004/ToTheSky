package com.fst.tothesky.client.gui.calendar;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家头渲染。皮肤获取方式与 Armourers-Workshop 模特一致（AbstractCustomProfileTextureLoader）：
 * <ol>
 *   <li>名字→profile：{@code SkullBlockEntity.updateGameprofile}（头颅方块同款异步补全，正/离线服通吃）</li>
 *   <li>profile→皮肤：{@code SkinManager.registerSkins}；回调未回出 SKIN 时由渲染帧驱动、
 *       每 500ms 重试一次直至 30s 超时（AW 的轮询策略，只是驱动源从 sleep 换成 render tick）</li>
 *   <li>未知玩家：properties 为空直接按 UUID 给默认皮肤，不傻等 Mojang API</li>
 * </ol>
 * 脸部 8×8（源 8,8）+ 帽子层（源 40,8），放大到 size。
 */
public final class CalendarHeadRenderer {

    /** 玩家名 → 已就绪皮肤贴图（缓存；同一会话皮肤不变） */
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    /** 玩家名 → 重试截止时间；存在且 >now 表示解析在途/等待重试，render 到点重新发起 */
    private static final Map<String, Long> RETRY_DEADLINE = new HashMap<>();
    /** 两次重试之间的最小间隔（AW：500ms） */
    private static final long RETRY_INTERVAL_MS = 500;
    /** 单个名字的重试总时长（AW：30s） */
    private static final long RETRY_TIMEOUT_MS = 30_000;

    private CalendarHeadRenderer() {
    }

    /** 在 (x, y) 渲染玩家头（size 见方），未就绪时渲染默认皮肤，不空格 */
    public static void render(String playerName, GuiGraphics graphics, int x, int y, int size) {
        ResourceLocation skin = SKINS.get(playerName);
        if (skin == null) {
            skin = resolve(playerName);
        }
        if (skin == null) {
            // profile 补全中：默认皮肤占位，就绪后 SKINS 有值自然刷新
            skin = DefaultPlayerSkin.getDefaultSkin(offlineUuid(playerName));
        }
        // 脸部源 (8,8) 8×8 + 帽子层源 (40,8)，放大到 size（11 参 blit：渲染尺寸独立于源尺寸）
        graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    /** 渲染帧驱动：需要解析时发起解析链，返回已就绪皮肤或 null（本帧默认皮肤占位） */
    private static ResourceLocation resolve(String playerName) {
        Long deadline = RETRY_DEADLINE.get(playerName);
        long now = System.currentTimeMillis();
        if (deadline != null) {
            if (now < deadline) {
                return null; // 在途或未到重试间隔
            }
            if (now > deadline + RETRY_TIMEOUT_MS) {
                RETRY_DEADLINE.remove(playerName); // 超时：允许本轮重发；仍失败则永远默认皮肤
            } else {
                RETRY_DEADLINE.put(playerName, now + RETRY_TIMEOUT_MS); // 重试窗口重置
                return null;
            }
        }
        RETRY_DEADLINE.put(playerName, now + RETRY_TIMEOUT_MS);
        UUID offlineId = offlineUuid(playerName);
        GameProfile profile = new GameProfile(offlineId, playerName);
        // AW 第一步：头颅方块同款 profile 异步补全（成功/失败都会回调）
        SkullBlockEntity.updateGameprofile(profile, filled ->
                registerSkin(playerName, filled, System.currentTimeMillis() + RETRY_TIMEOUT_MS));
        return null;
    }

    /** AW 第二步：注册皮肤；未知玩家（properties 空）直接默认皮肤，不等 Mojang API */
    private static void registerSkin(String playerName, GameProfile profile, long endTime) {
        if (profile.getProperties().isEmpty()) {
            SKINS.put(playerName, DefaultPlayerSkin.getDefaultSkin(profile.getId()));
            RETRY_DEADLINE.remove(playerName);
            return;
        }
        SkinManager skinManager = Minecraft.getInstance().getSkinManager();
        skinManager.registerSkins(profile, (type, location, filledProfile) -> {
            // 回调最多三次（skin/cape/elytra），只取 skin
            if (type != MinecraftProfileTexture.Type.SKIN) {
                return;
            }
            SKINS.put(playerName, location);
            RETRY_DEADLINE.remove(playerName);
        }, false);
        // 未在回调中就绪 → RETRY_DEADLINE 仍在；下次 render 到重试间隔后由 resolve() 重发整条链
        // （endTime 超时后 resolve 不再重发）
        if (SKINS.containsKey(playerName)) {
            RETRY_DEADLINE.remove(playerName);
        } else {
            RETRY_DEADLINE.put(playerName, endTime);
        }
    }

    /** 离线 UUID 规则（与原版 OfflinePlayer 一致），仅用于默认皮肤挑选 */
    private static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
    }
}