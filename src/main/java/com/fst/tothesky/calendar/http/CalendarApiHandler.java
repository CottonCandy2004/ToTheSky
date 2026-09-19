package com.fst.tothesky.calendar.http;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarData;
import com.fst.tothesky.calendar.CalendarEvent;
import com.fst.tothesky.network.ModNetwork;
import com.fst.tothesky.web.WebHttp;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 日历 REST API：外部（网页等）增删查改活动；由 {@code web.WebApiServer} 挂在 {@code /api/calendar/}。
 *
 * <p>路由：
 * <ul>
 *   <li>{@code GET  /events} — 全部（可 {@code ?month=N} 过滤）</li>
 *   <li>{@code GET  /events/{id}} — 单个（404）</li>
 *   <li>{@code POST /events} — 创建（校验字段，返回完整对象）</li>
 *   <li>{@code PUT  /events/{id}} — 部分更新（null 字段保留）</li>
 *   <li>{@code DELETE /events/{id}} — 删除</li>
 *   <li>{@code GET  /today} — 今天日期 + 当日活动（农历活动按换算后的公历日命中）</li>
 * </ul>
 *
 * <p>约定：JSON 与 CORS 头由 {@link WebHttp} 统一出；<b>读写一律提交回服务器线程</b>
 * （{@link WebHttp#submitOnServer}，SavedData 归主线程独占），超时 → 503；
 * 变更后广播刷新打开中的 GUI。{@code iconType} 为 item/block 时校验 registry id 存在。
 */
public final class CalendarApiHandler {

    /** PUT 的结果：{@code error} 非空 = 400，{@code event} 为 null = 404 */
    private record Update(CalendarEvent event, String error) {
    }

    private final MinecraftServer server;

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
                WebHttp.respondOptions(exchange);
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
                            WebHttp.respond(exchange, 405, WebHttp.errorJson("POST to /events only"));
                        }
                    }
                    case "PUT" -> {
                        if (!idPart.isEmpty()) {
                            handleUpdate(exchange, idPart);
                        } else {
                            WebHttp.respond(exchange, 405, WebHttp.errorJson("PUT to /events/{id} only"));
                        }
                    }
                    case "DELETE" -> {
                        if (!idPart.isEmpty()) {
                            handleDelete(exchange, idPart);
                        } else {
                            WebHttp.respond(exchange, 405, WebHttp.errorJson("DELETE to /events/{id} only"));
                        }
                    }
                    default -> WebHttp.respond(exchange, 405, WebHttp.errorJson("unsupported method " + method));
                }
                return;
            }
            WebHttp.respond(exchange, 404, WebHttp.errorJson("not found: " + path));
        } catch (Exception e) {
            ToTheSky.LOGGER.error("[日历API] 处理请求失败", e);
            try {
                WebHttp.respond(exchange, 500, WebHttp.errorJson("internal error"));
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    // ---- 各路由实现 ----

    private void handleToday(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        LocalDate today = LocalDate.now();
        String json = WebHttp.submitOnServer(server, () -> {
            JsonObject root = new JsonObject();
            root.addProperty("date", today.toString());
            root.add("events", CalendarApiJson.eventsArray(CalendarData.get(server).on(today)));
            return WebHttp.toJson(root);
        });
        WebHttp.respond(exchange, json == null ? 503 : 200,
                json == null ? WebHttp.errorJson("server busy") : json);
    }

    private void handleList(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Integer monthFilter = null;
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if ("month".equals(kv[0]) && kv.length == 2) {
                    try {
                        monthFilter = Integer.parseInt(kv[1]);
                        if (monthFilter < 1 || monthFilter > 12) {
                            WebHttp.respond(exchange, 400, WebHttp.errorJson("month must be 1-12"));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        WebHttp.respond(exchange, 400, WebHttp.errorJson("month must be a number"));
                        return;
                    }
                }
            }
        }
        final Integer filter = monthFilter;
        String json = WebHttp.submitOnServer(server, () -> {
            List<CalendarEvent> events = CalendarData.get(server).all();
            List<CalendarEvent> result = filter == null ? events
                    : events.stream().filter(e -> e.month == filter).toList();
            return WebHttp.toJson(CalendarApiJson.eventsArray(result));
        });
        WebHttp.respond(exchange, json == null ? 503 : 200,
                json == null ? WebHttp.errorJson("server busy") : json);
    }

    private void handleGet(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        // 用 Optional 区分「超时」与「没有这条」（submitOnServer 超时也返回 null）
        Optional<CalendarEvent> found = WebHttp.submitOnServer(server,
                () -> Optional.ofNullable(CalendarData.get(server).find(id)));
        if (found == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        if (found.isEmpty()) {
            WebHttp.respond(exchange, 404, WebHttp.errorJson("event not found"));
            return;
        }
        WebHttp.respond(exchange, 200, WebHttp.toJson(CalendarApiJson.eventObject(found.get())));
    }

    private void handleCreate(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        JsonObject body = WebHttp.readJsonBody(exchange);
        if (body == null) {
            return; // readJsonBody 已响应错误
        }
        CalendarApiJson.Validated v = CalendarApiJson.validateCreate(body);
        if (v.error != null) {
            WebHttp.respond(exchange, 400, WebHttp.errorJson(v.error));
            return;
        }
        CalendarEvent created = WebHttp.submitOnServer(server, () -> {
            CalendarData data = CalendarData.get(server);
            CalendarEvent event = CalendarEvent.create(UUID.randomUUID(), v.name, v.type,
                    v.month, v.day, v.iconType, v.iconId, v.description, v.letter, v.lunar);
            data.add(event);
            broadcast();
            return event;
        });
        if (created == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        WebHttp.respond(exchange, 201, WebHttp.toJson(CalendarApiJson.eventObject(created)));
    }

    private void handleUpdate(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        JsonObject body = WebHttp.readJsonBody(exchange);
        if (body == null) {
            return;
        }
        CalendarApiJson.Patch patch = CalendarApiJson.validatePatch(body);
        if (patch.error != null) {
            WebHttp.respond(exchange, 400, WebHttp.errorJson(patch.error));
            return;
        }
        Update result = WebHttp.submitOnServer(server, () -> {
            CalendarData data = CalendarData.get(server);
            CalendarEvent existing = data.find(id);
            if (existing == null) {
                return new Update(null, null);
            }
            CalendarEvent merged = existing.with(patch.name, patch.type, patch.month, patch.day,
                    patch.iconType, patch.iconId, patch.description, patch.letter, patch.lunar);
            // 部分更新拼出来的月-日可能不搭（只改 lunar 或只改 day 最容易）：合并后再校验一次，
            // 免得存进一个排不了期、也算不出落在哪天的活动（农历月最多 30 天，公历 2 月最多 29）
            if (!merged.hasValidDate()) {
                return new Update(null, "invalid date for the merged event: "
                        + (merged.lunar ? "农历" : "") + merged.month + "-" + merged.day);
            }
            data.update(id, merged);
            broadcast();
            return new Update(merged, null);
        });
        if (result == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        if (result.error() != null) {
            WebHttp.respond(exchange, 400, WebHttp.errorJson(result.error()));
            return;
        }
        if (result.event() == null) {
            WebHttp.respond(exchange, 404, WebHttp.errorJson("event not found"));
            return;
        }
        WebHttp.respond(exchange, 200, WebHttp.toJson(CalendarApiJson.eventObject(result.event())));
    }

    private void handleDelete(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        UUID id = parseId(exchange, idPart);
        if (id == null) {
            return;
        }
        Boolean removed = WebHttp.submitOnServer(server, () -> {
            boolean ok = CalendarData.get(server).remove(id);
            if (ok) {
                broadcast();
            }
            return ok;
        });
        if (removed == null) {
            WebHttp.respond(exchange, 503, WebHttp.errorJson("server busy"));
            return;
        }
        WebHttp.respond(exchange, removed ? 200 : 404,
                WebHttp.toJson(CalendarApiJson.statusObject(removed ? "deleted" : "event not found")));
    }

    // ---- 工具 ----

    /** 变更后广播（在服务器线程内调用） */
    private void broadcast() {
        ModNetwork.broadcastCalendar(server);
    }

    private UUID parseId(com.sun.net.httpserver.HttpExchange exchange, String idPart) throws IOException {
        try {
            return UUID.fromString(idPart);
        } catch (IllegalArgumentException e) {
            WebHttp.respond(exchange, 400, WebHttp.errorJson("invalid uuid: " + idPart));
            return null;
        }
    }
}
