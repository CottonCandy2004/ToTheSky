package com.fst.tothesky.calendar.http;

import com.fst.tothesky.ToTheSky;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * 日历 HTTP 服务器宿主：com.sun.net.httpserver，配置读
 * {@code config/tothesky_calendar.json}（缺失写默认
 * {@code {"port":39000,"bindAddress":"127.0.0.1"}}）。
 * 端口占用仅日志告警，游戏正常运行（HTTP 禁用）。
 * 生命周期由 {@code CalendarEvents} 驱动：ServerStarted 启动，ServerStopped 关闭。
 */
public final class CalendarHttpServer {

    private static final String CONFIG_FILE = "tothesky_calendar.json";
    private static final int DEFAULT_PORT = 39000;
    private static final String DEFAULT_BIND = "127.0.0.1";
    private static final String API_PREFIX = "/api/calendar/";

    private com.sun.net.httpserver.HttpServer http;
    private CalendarApiHandler handler;

    private CalendarHttpServer() {
    }

    /** 启动 HTTP；失败（端口占用等）仅告警 */
    public static CalendarHttpServer start(MinecraftServer server) {
        CalendarHttpServer host = new CalendarHttpServer();
        Config config = readConfig();
        host.handler = new CalendarApiHandler(server);
        try {
            com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(config.bindAddress, config.port), 0);
            http.createContext(API_PREFIX, exchange -> host.handler.handle(exchange));
            // 独立小线程池：请求量小，不与游戏线程池混用
            http.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ToTheSky-CalendarAPI");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            host.http = http;
            ToTheSky.LOGGER.info("[日历API] 已启动：http://{}:{}{}", config.bindAddress, config.port, API_PREFIX);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[日历API] 启动失败（{}），HTTP 功能不可用；如需启用请检查端口占用或修改 {}",
                    e.getMessage(), FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE));
        }
        return host;
    }

    /** 停止（ServerStopped 时调用） */
    public void stop() {
        if (handler != null) {
            handler.shuttingDown = true;
        }
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    // ---- config ----

    record Config(int port, String bindAddress) {
    }

    /** 读 config；缺失或损坏写默认文件（与 server_settings.json 同模式） */
    private static Config readConfig() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        int port = DEFAULT_PORT;
        String bind = DEFAULT_BIND;
        try {
            if (Files.exists(file)) {
                JsonObject obj = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("port") && obj.get("port").isJsonPrimitive()) {
                    int p = obj.get("port").getAsInt();
                    if (p > 0 && p < 65536) {
                        port = p;
                    }
                }
                if (obj.has("bindAddress") && obj.get("bindAddress").isJsonPrimitive()) {
                    String b = obj.get("bindAddress").getAsString();
                    if (!b.isBlank()) {
                        bind = b;
                    }
                }
            } else {
                writeDefaultConfig(file);
            }
        } catch (Exception e) {
            ToTheSky.LOGGER.warn("[日历API] 配置解析失败（{}），使用默认值 {}", e.getMessage(),
                    writeDefaultConfig(file) ? "并重写默认文件" : "");
        }
        return new Config(port, bind);
    }

    private static boolean writeDefaultConfig(Path file) {
        JsonObject obj = new JsonObject();
        obj.addProperty("port", DEFAULT_PORT);
        obj.addProperty("bindAddress", DEFAULT_BIND);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, obj.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[日历API] 写默认配置失败: {}", e.getMessage());
            return false;
        }
    }
}