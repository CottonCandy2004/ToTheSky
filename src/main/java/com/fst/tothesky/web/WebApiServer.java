package com.fst.tothesky.web;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.http.CalendarApiHandler;
import com.fst.tothesky.config.ToTheSkyConfig;
import com.fst.tothesky.contact.http.LetterApiHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 本地 Web API 宿主：JDK 内置 {@code com.sun.net.httpserver}。端口与绑定地址读模组配置
 * {@code config/tothesky-common.toml} 的 {@code calendar.port} / {@code calendar.bindAddress}
 * （默认 39000 / 127.0.0.1）；端口占用只告警，游戏照常运行（HTTP 功能不可用）。
 *
 * <p>三块内容各挂一个 context（{@code com.sun.net.httpserver} 按最长前缀匹配；不带尾斜杠注册，
 * 这样 {@code /api/letters} 与 {@code /api/letters/{id}} 会落到同一个 handler）：
 * <ul>
 *   <li>{@code /api/calendar} → {@link CalendarApiHandler}（日历活动增删查改）</li>
 *   <li>{@code /api/letters} → {@link LetterApiHandler}（节日信/生日信的读写）</li>
 *   <li>{@code /} 与 {@code /index.html} → 管理页（classpath 资源 {@code /web/calendar_admin.html}，随 jar 打包）</li>
 * </ul>
 *
 * <p>生命周期由本类的订阅方法驱动：ServerStarted 启动、ServerStopped 关闭。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class WebApiServer {

    /** 挂 context 用：不加尾斜杠，`/api/letters` 与 `/api/letters/{id}` 都能落到同一个 handler */
    private static final String CALENDAR_PREFIX = "/api/calendar";
    private static final String LETTERS_PREFIX = "/api/letters";

    /** 当前实例；未启动或已停止为 null */
    private static volatile WebApiServer current;

    private com.sun.net.httpserver.HttpServer http;

    private WebApiServer() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WebApiServer server = current;
        current = null;
        if (server != null) {
            server.stop();
        }
    }

    /** 启动 HTTP；失败（端口占用等）仅告警 */
    private static void start(MinecraftServer server) {
        WebApiServer host = new WebApiServer();
        int port = ToTheSkyConfig.CALENDAR_PORT.get();
        String bind = ToTheSkyConfig.CALENDAR_BIND_ADDRESS.get();
        CalendarApiHandler calendar = new CalendarApiHandler(server);
        LetterApiHandler letters = new LetterApiHandler(server);
        try {
            com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(bind, port), 0);
            http.createContext(CALENDAR_PREFIX, calendar::handle);
            http.createContext(LETTERS_PREFIX, letters::handle);
            // 管理页：根路径（/、/index.html）直接返回内嵌 HTML，无需部署任何外部文件
            http.createContext("/", host::serveAdminPage);
            // 独立小线程池：请求量小，不与游戏线程池混用
            http.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ToTheSky-WebAPI");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            host.http = http;
            current = host;
            ToTheSky.LOGGER.info("[Web API] 已启动：http://{}:{}/ （管理页）、{}/* 与 {}/*（REST）",
                    bind, port, CALENDAR_PREFIX, LETTERS_PREFIX);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[Web API] 启动失败（{}），HTTP 功能不可用；如需启用请确认 {}:{} 未被占用，"
                            + "或在 {} 修改 calendar.port",
                    e.getMessage(), bind, port,
                    FMLPaths.CONFIGDIR.get().resolve(ToTheSky.MODID + "-common.toml"));
        }
    }

    /** 停止（ServerStopped 时调用）：立即关闭监听与处理中连接 */
    private void stop() {
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
        try (var in = WebApiServer.class.getResourceAsStream("/web/calendar_admin.html")) {
            if (in == null) {
                throw new IOException("classpath 资源缺失: /web/calendar_admin.html");
            }
            return in.readAllBytes();
        }
    }
}
