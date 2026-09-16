package com.fst.tothesky.calendar.http;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarData;
import com.fst.tothesky.calendar.CalendarEvent;
import com.fst.tothesky.network.ModNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 日历 REST API：JDK 内置 HttpServer，外部（网页等）增删查改活动。
 *
 * <p>路由（前缀 {@code /api/calendar/}）：
 * <ul>
 *   <li>{@code GET  /events} — 全部（可 {@code ?month=N} 过滤）</li>
 *   <li>{@code GET  /events/{id}} — 单个（404）</li>
 *   <li>{@code POST /events} — 创建（校验字段，返回完整对象）</li>
 *   <li>{@code PUT  /events/{id}} — 部分更新（null 字段保留）</li>
 *   <li>{@code DELETE /events/{id}} — 删除</li>
 *   <li>{@code GET  /today} — 今天日期 + 当日活动</li>
 * </ul>
 *
 * <p>约定：JSON、CORS 全开（含 OPTIONS 预检 204）；读写经 {@code server.execute()}
 * 到服务器线程（5s 超时 → 503）；变更后广播刷新打开中的 GUI。
 * {@code iconType} 为 item/block 时校验 registry id 存在。
 */
public final class CalendarApiHandler {

    private static final Gson GSON = new Gson();
    private static final long SERVER_EXECUTE_TIMEOUT_SECONDS = 5;

    private final MinecraftServer server;

    /** 服务器停机中：拒绝新请求（生命周期事件置位） */
    public volatile boolean shuttingDown = false;

    public CalendarApiHandler(MinecraftServer server) {
        this.server = server;
    }

    // ---- 路由 ----

    public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            // createContext 挂在 /api/calendar/，但 context path 裁切不可依赖（尾部斜杠行为不一致）——手动剥前缀
            String prefix = "/api/calendar";
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            }
            if (path.isEmpty()) {
                path = "/";
            }
            if ("OPTIONS".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE, OPTIONS");
                respond(exchange, 204, "");
                return;
            }
            if ("/today".equals(path) && "GET".equals(method)) {
                handleToday(exchange);
                return;
            }
            if (path.equals("/events") || path.startsWith("/events/")) {
                String rest = path.substring("/events".length());
                String idPart = rest.startsWith("/") ? rest.substring(1) : "";
                switch (method) {
                    case "GET" -> {
                        if (idPart.isEmpty()) {
                            handleList(exchange);
                        } else {
                            handleGet(exchange, idPart);
                        }
                    }
                    case "POST" -> {
                        if (idPart.isEmpty()) {
                            handleCreate(exchange);
                        } else {
                            respond(exchange, 405, errorJson("POST to /events only"));
                        }
                    }
                    case "PUT" -> {
                        if (!idPart.isEmpty()) {
                            handleUpdate(exchange, idPart);
                        } else {
                            respond(exchange, 405, errorJson("PUT to /events/{id} only"));
                        }
                    }
                    case "DELETE" -> {
                        if (!idPart.isEmpty()) {
                            handleDelete(exchange, idPart);
                        } else {
                            respond(exchange, 405, errorJson("DELETE to /events/{id} only"));
                        }
                    }
                    default -> respond(exchange, 405, errorJson("unsupported method " + method));
                }
                return;
            }
            respond(exchange, 404, errorJson("not found: " + path));
        } catch (Exception e) {
            ToTheSky.LOGGER.error("[日历API] 处理请求失败", e);
            try {
                respond(exchange, 500, errorJson("internal error"));
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    // ---- 各路由实现 ----

    private void handleToday(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        LocalDate today = LocalDate.now();
        CalendarData data = CalendarData.get(server);
        JsonObject root = new JsonObject();
        root.addProperty("date", today.toString());
        root.add("events", CalendarApiJson.eventsArray(data.byDay(today.getMonthValue(), today.getDayOfMonth())));
        respond(exchange, 200, GSON.toJson(root));
    }

    private void handleList(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        CalendarData data = CalendarData.get(server);
        List<CalendarEvent> events = data.all();
        Integer monthFilter = null;
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if ("month".equals(kv[0]) && kv.length == 2) {
                    try {
                        monthFilter = Integer.parseInt(kv[1]);
                        if (monthFilter < 1 || monthFilter > 12) {
                            respond(exchange, 400, errorJson("month must be 1-12"));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        respond(exchange, 400, errorJson("month must be a number"));
                        return;
                    }
                }
            }
        }
        final Integer filter = monthFilter;
        List<CalendarEvent> result = filter == null ? events
                : events.stream().filter(e -> e.month == filter).toList();
        respond(exchange, 200, GSON.toJson(CalendarApiJson.eventsArray(result)));
    }

    private void handleGet(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        CalendarEvent event = CalendarData.get(server).find(id);
        if (event == null) {
            respond(exchange, 404, errorJson("event not found"));
            return;
        }
        respond(exchange, 200, GSON.toJson(CalendarApiJson.eventObject(event)));
    }

    private void handleCreate(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        JsonObject body = readJsonBody(exchange);
        if (body == null) {
            return; // readJsonBody 已响应错误
        }
        CalendarApiJson.Validated v = CalendarApiJson.validateCreate(body);
        if (v.error != null) {
            respond(exchange, 400, errorJson(v.error));
            return;
        }
        CalendarEvent created = submitOnServer(() -> {
            CalendarData data = CalendarData.get(server);
            CalendarEvent event = CalendarEvent.create(UUID.randomUUID(), v.name, v.type,
                    v.month, v.day, v.iconType, v.iconId, v.description, v.letter);
            data.add(event);
            broadcast();
            return event;
        });
        if (created == null) {
            respond(exchange, 503, errorJson("server busy"));
            return;
        }
        respond(exchange, 201, GSON.toJson(CalendarApiJson.eventObject(created)));
    }

    private void handleUpdate(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        JsonObject body = readJsonBody(exchange);
        if (body == null) {
            return;
        }
        CalendarApiJson.Patch patch = CalendarApiJson.validatePatch(body);
        if (patch.error != null) {
            respond(exchange, 400, errorJson(patch.error));
            return;
        }
        CalendarEvent updated = submitOnServer(() -> {
            CalendarData data = CalendarData.get(server);
            CalendarEvent existing = data.find(id);
            if (existing == null) {
                return null;
            }
            CalendarEvent merged = existing.with(patch.name, patch.type, patch.month, patch.day,
                    patch.iconType, patch.iconId, patch.description, patch.letter);
            data.update(id, merged);
            broadcast();
            return merged;
        });
        if (updated == null) {
            respond(exchange, 404, errorJson("event not found"));
            return;
        }
        respond(exchange, 200, GSON.toJson(CalendarApiJson.eventObject(updated)));
    }

    private void handleDelete(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        Boolean removed = submitOnServer(() -> {
            boolean ok = CalendarData.get(server).remove(id);
            if (ok) {
                broadcast();
            }
            return ok;
        });
        if (removed == null) {
            respond(exchange, 503, errorJson("server busy"));
            return;
        }
        respond(exchange, removed ? 200 : 404,
                GSON.toJson(CalendarApiJson.statusObject(removed ? "deleted" : "event not found")));
    }

    // ---- 工具 ----

    /** server.execute() 提交到服务器线程，5s 超时返回 null（503） */
    private <T> T submitOnServer(java.util.function.Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(SERVER_EXECUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            ToTheSky.LOGGER.error("[日历API] 服务器线程执行失败", e);
            return null;
        }
    }

    /** 变更后广播（在服务器线程内调用） */
    private void broadcast() {
        ModNetwork.broadcastCalendar(server);
    }

    private UUID parseId(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        try {
            return UUID.fromString(idPart);
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, errorJson("invalid uuid: " + idPart));
            return null;
        }
    }

    /** 读请求体 JSON；解析失败已响应 400 并返回 null */
    private JsonObject readJsonBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            respond(exchange, 400, errorJson("empty body"));
            return null;
        }
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            respond(exchange, 400, errorJson("invalid JSON: " + e.getMessage()));
            return null;
        }
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return GSON.toJson(obj);
    }
}