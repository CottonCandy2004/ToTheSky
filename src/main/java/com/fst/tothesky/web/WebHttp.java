package com.fst.tothesky.web;

import com.fst.tothesky.ToTheSky;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 本地 Web API 的公共管道：JSON 编解码、CORS 头、请求体读取、服务器线程提交。
 * 各域自己的路由与字段校验留在各自的 {@code http} 包里
 * （{@code calendar.http.CalendarApiHandler} / {@code contact.http.LetterApiHandler}）。
 *
 * <p><b>为什么都要 {@link #submitOnServer}</b>：日历的 SavedData、信件的解析缓存与排期都是
 * 服务器线程独占的，HTTP 线程直接读会读到半截状态（写入更是直接坏档）。
 * 所以本模组的 Web API 约定：<b>碰领域数据就得回主线程</b>——代价是每次请求最多等 5 秒，
 * 对「本机管理页点一下」这点负载无所谓。
 *
 * <p>CORS 全开（含 OPTIONS 预检 204）：绑定地址默认 {@code 127.0.0.1}，页面就是本机的管理页，
 * 没有跨站需求；要对外暴露请同时改 {@code calendar.bindAddress} 并自备反向代理与鉴权。
 */
public final class WebHttp {

    /** Gson 线程安全，全员共用一个实例；不转义 HTML 字符，中文与 {@code <>&=} 原样出去（消费方是 JSON 解析器/管理页） */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** {@code server.execute()} 的等待上限；超时回 503（服务器卡住时不能让网页一直挂着） */
    private static final long SERVER_EXECUTE_TIMEOUT_SECONDS = 5;

    private WebHttp() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static String errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return GSON.toJson(obj);
    }

    /** 预检请求：告诉浏览器本 API 允许的方法，无正文 */
    public static void respondOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        respond(exchange, 204, "");
    }

    /** 统一出口：JSON + CORS 头；204 只发状态行 */
    public static void respond(HttpExchange exchange, int status, String json) throws IOException {
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 读请求体 JSON；空体 / 语法错已回 400 并返回 {@code null} */
    @Nullable
    public static JsonObject readJsonBody(HttpExchange exchange) throws IOException {
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

    /**
     * 提交到服务器线程并等结果。
     *
     * @return 执行结果；超时或执行抛异常返回 {@code null}（调用方一律回 503）
     */
    @Nullable
    public static <T> T submitOnServer(MinecraftServer server, Supplier<T> action) {
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
            ToTheSky.LOGGER.error("[Web API] 服务器线程执行失败", e);
            return null;
        }
    }
}
