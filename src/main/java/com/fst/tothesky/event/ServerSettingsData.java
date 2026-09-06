package com.fst.tothesky.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 服务器设置（config/server_settings.json 的镜像，ServerStartedEvent 时读取），
 * 存于服务器 SavedData 供其他系统查询。
 * 移植自 kubejs check_server.js（industry_server / game_server / gameName / serverId）。
 */
public final class ServerSettingsData extends SavedData {
    public static final String DATA_NAME = "tothesky_server_settings";
    private static final String TAG_INDUSTRY_SERVER = "industry_server";
    private static final String TAG_GAME_SERVER = "game_server";
    private static final String TAG_GAME_NAME = "game_name";
    private static final String TAG_SERVER_ID = "server_id";

    private boolean industryServer;
    private boolean gameServer;
    /** config 中的 game 字段；仅在 gameServer 为 true 时有意义（对应旧脚本的 gameName） */
    private String gameName = "";
    private String serverId = "default";

    private ServerSettingsData() {
    }

    /** 读取/创建（服务器级，挂在主世界上） */
    public static ServerSettingsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServerSettingsData::read, ServerSettingsData::new, DATA_NAME);
    }

    private static ServerSettingsData read(CompoundTag tag) {
        ServerSettingsData data = new ServerSettingsData();
        data.industryServer = tag.getBoolean(TAG_INDUSTRY_SERVER);
        data.gameServer = tag.getBoolean(TAG_GAME_SERVER);
        data.gameName = tag.getString(TAG_GAME_NAME);
        data.serverId = tag.getString(TAG_SERVER_ID);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(TAG_INDUSTRY_SERVER, industryServer);
        tag.putBoolean(TAG_GAME_SERVER, gameServer);
        tag.putString(TAG_GAME_NAME, gameName);
        tag.putString(TAG_SERVER_ID, serverId);
        return tag;
    }

    /** 用 config/server_settings.json 解析出的值整体刷新 */
    public void apply(boolean industryServer, boolean gameServer, String gameName, String serverId) {
        this.industryServer = industryServer;
        this.gameServer = gameServer;
        this.gameName = gameName == null ? "" : gameName;
        this.serverId = serverId == null ? "default" : serverId;
        setDirty();
    }

    /** 对应旧脚本 persistentData.industry_server */
    public boolean isIndustryServer() {
        return industryServer;
    }

    /** 对应旧脚本 persistentData.game_server */
    public boolean isGameServer() {
        return gameServer;
    }

    /** 对应旧脚本 persistentData.gameName（仅 gameServer 时有意义） */
    public String getGameName() {
        return gameName;
    }

    /** 对应旧脚本 persistentData.serverId */
    public String getServerId() {
        return serverId;
    }
}
