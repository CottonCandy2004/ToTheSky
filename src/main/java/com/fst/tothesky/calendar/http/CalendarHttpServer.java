package com.fst.tothesky.calendar.http;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.config.ToTheSkyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 日历 HTTP 服务器宿主：com.sun.net.httpserver。端口与绑定地址读模组配置
 * {@code config/tothesky-common.toml} 的 {@code calendar.port} / {@code calendar.bindAddress}
 * （默认 39000 / 127.0.0.1）。
 * 端口占用仅日志告警，游戏正常运行（HTTP 禁用）。
 * 生命周期由 {@code CalendarEvents} 驱动：ServerStarted 启动，ServerStopped 关闭。
 */
public final class CalendarHttpServer {

    private static final String API_PREFIX = "/api/calendar/";

    private com.sun.net.httpserver.HttpServer http;
    private CalendarApiHandler handler;

    private CalendarHttpServer() {
    }

    /** 启动 HTTP；失败（端口占用等）仅告警 */
    public static CalendarHttpServer start(MinecraftServer server) {
        CalendarHttpServer host = new CalendarHttpServer();
        int port = ToTheSkyConfig.CALENDAR_PORT.get();
        String bind = ToTheSkyConfig.CALENDAR_BIND_ADDRESS.get();
        host.handler = new CalendarApiHandler(server);
        try {
            com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(bind, port), 0);
            http.createContext(API_PREFIX, exchange -> host.handler.handle(exchange));
            // 管理页：根路径（/、/index.html）直接返回内嵌 HTML，无需部署任何外部文件
            http.createContext("/", exchange -> host.serveAdminPage(exchange));
            // 独立小线程池：请求量小，不与游戏线程池混用
            http.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ToTheSky-CalendarAPI");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            host.http = http;
            ToTheSky.LOGGER.info("[日历API] 已启动：http://{}:{}/ （管理页） 与 {}*（REST API）",
                    bind, port, API_PREFIX);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[日历API] 启动失败（{}），HTTP 功能不可用；如需启用请确认 {}:{} 未被占用，"
                            + "或在 {} 修改 calendar.port",
                    e.getMessage(), bind, port,
                    FMLPaths.CONFIGDIR.get().resolve(ToTheSky.MODID + "-common.toml"));
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

    // ---- 管理页 ----

    /** 管理页：/ 或 /index.html 返回内嵌 HTML；其余 404 */
    private void serveAdminPage(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod())
                    || !(path.equals("/") || path.equals("/index.html"))) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] page = ADMIN_PAGE_CACHE != null ? ADMIN_PAGE_CACHE
                    : (ADMIN_PAGE_CACHE = readAdminPage());
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, page.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(page);
            }
        } finally {
            exchange.close();
        }
    }

    /** 页面缓存（一次性读入 classpath 资源；内容随 jar 固定） */
    private static volatile byte[] ADMIN_PAGE_CACHE;

    private static byte[] readAdminPage() throws IOException {
        try (var in = CalendarHttpServer.class.getResourceAsStream("/web/calendar_admin.html")) {
            if (in == null) {
                throw new IOException("classpath 资源缺失: /web/calendar_admin.html");
            }
            return in.readAllBytes();
        }
    }
}
