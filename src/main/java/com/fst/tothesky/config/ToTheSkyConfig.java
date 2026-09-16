package com.fst.tothesky.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * ToTheSky 通用配置（Forge {@code ModConfig.Type.COMMON}，文件 {@code config/tothesky-common.toml}）。
 * 在模组构造期注册，供 {@code CalendarHttpServer} 在 ServerStarted 读取；
 * 因此改动需重启（或重载配置后重启服务器）才生效。
 */
public final class ToTheSkyConfig {

    /** 日历 HTTP API 与自带管理页的监听端口 */
    public static final ForgeConfigSpec.IntValue CALENDAR_PORT;
    /** 日历 HTTP API 的绑定地址（默认仅本机） */
    public static final ForgeConfigSpec.ConfigValue<String> CALENDAR_BIND_ADDRESS;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("日历 HTTP API（REST + 自带管理页）").push("calendar");
        CALENDAR_PORT = builder
                .comment("监听端口。与其它进程（含同机的另一个 Minecraft 实例）冲突时仅记录 WARN，"
                        + "游戏正常运行、HTTP 功能不可用。")
                .defineInRange("port", 39000, 1, 65535);
        CALENDAR_BIND_ADDRESS = builder
                .comment("绑定地址。127.0.0.1 仅本机可访问；改为 0.0.0.0 可供局域网访问。")
                .define("bindAddress", "127.0.0.1", o -> o instanceof String s && !s.isBlank());
        builder.pop();
        SPEC = builder.build();
    }

    private ToTheSkyConfig() {
    }
}
