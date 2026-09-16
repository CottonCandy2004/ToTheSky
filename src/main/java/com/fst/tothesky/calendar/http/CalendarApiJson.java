package com.fst.tothesky.calendar.http;

import com.fst.tothesky.calendar.CalendarEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.YearMonth;
import java.util.List;

/**
 * REST API 的 JSON DTO 与校验。
 * 字段：id / name / type(festival|birthday) / month(1-12) / day(按月校验，2 月允许 29) /
 * iconType(none|item|block|player) / iconId / description / letter(仅 festival)。
 * item/block 时 iconId 必须能从 ForgeRegistries 解析。
 * letter 是绑定信件 id（{@code config/tothesky/letters} 里的文件名去 {@code .json}），
 * 空 = 未绑定；是否真有这封信由 {@code contact.LetterScheduler} 在投递时判定
 * （信件目录可以独立于日历变动，这里不做存在性校验）。
 */
final class CalendarApiJson {

    private CalendarApiJson() {
    }

    // ---- 序列化 ----

    static JsonObject eventObject(CalendarEvent event) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", event.id.toString());
        obj.addProperty("name", event.name);
        obj.addProperty("type", event.type);
        obj.addProperty("month", event.month);
        obj.addProperty("day", event.day);
        obj.addProperty("iconType", event.iconType);
        obj.addProperty("iconId", event.iconId);
        obj.addProperty("description", event.description);
        obj.addProperty("letter", event.letter);
        return obj;
    }

    static JsonArray eventsArray(List<CalendarEvent> events) {
        JsonArray array = new JsonArray();
        for (CalendarEvent event : events) {
            array.add(eventObject(event));
        }
        return array;
    }

    static JsonObject statusObject(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", message);
        return obj;
    }

    // ---- 校验 ----

    /** POST 用：全量必填字段 */
    static final class Validated {
        String error;
        String name;
        String type;
        int month;
        int day;
        String iconType;
        String iconId;
        String description;
        String letter;
    }

    /** PUT 用：null 字段保留原值 */
    static final class Patch {
        String error;
        String name;
        String type;
        Integer month;
        Integer day;
        String iconType;
        String iconId;
        String description;
        String letter;
    }

    static Validated validateCreate(JsonObject body) {
        Validated v = new Validated();
        v.name = optString(body, "name");
        if (v.name == null || v.name.isBlank()) {
            v.error = "name is required";
            return v;
        }
        v.type = optString(body, "type");
        if (!CalendarEvent.TYPE_FESTIVAL.equals(v.type) && !CalendarEvent.TYPE_BIRTHDAY.equals(v.type)) {
            v.error = "type must be festival or birthday";
            return v;
        }
        v.month = optInt(body, "month", -1);
        if (v.month < 1 || v.month > 12) {
            v.error = "month must be 1-12";
            return v;
        }
        v.day = optInt(body, "day", -1);
        if (v.day < 1 || v.day > YearMonth.of(2000, v.month).lengthOfMonth()) {
            v.error = "day invalid for month " + v.month;
            return v;
        }
        v.iconType = body.has("iconType") ? optString(body, "iconType") : CalendarEvent.ICON_NONE;
        v.error = validateIcon(v.iconType, optString(body, "iconId"));
        if (v.error != null) {
            return v;
        }
        v.iconId = optString(body, "iconId");
        if (v.iconId == null) {
            v.iconId = "";
        }
        v.description = optString(body, "description");
        if (v.description == null) {
            v.description = "";
        }
        v.letter = optString(body, "letter");
        if (v.letter == null) {
            v.letter = "";
        }
        v.error = validateLetter(v.letter);
        if (v.error != null) {
            return v;
        }
        if (!v.letter.isEmpty() && !CalendarEvent.TYPE_FESTIVAL.equals(v.type)) {
            v.error = "letter is only for festival events";
            return v;
        }
        return v;
    }

    static Patch validatePatch(JsonObject body) {
        Patch p = new Patch();
        if (body.has("name")) {
            p.name = optString(body, "name");
            if (p.name == null || p.name.isBlank()) {
                p.error = "name must not be blank";
                return p;
            }
        }
        if (body.has("type")) {
            p.type = optString(body, "type");
            if (!CalendarEvent.TYPE_FESTIVAL.equals(p.type) && !CalendarEvent.TYPE_BIRTHDAY.equals(p.type)) {
                p.error = "type must be festival or birthday";
                return p;
            }
        }
        if (body.has("month")) {
            p.month = optInt(body, "month", -1);
            if (p.month < 1 || p.month > 12) {
                p.error = "month must be 1-12";
                return p;
            }
        }
        if (body.has("day")) {
            int day = optInt(body, "day", -1);
            int month = p.month != null ? p.month : 1; // 合法性最终在合并后校验
            if (day < 1 || day > 31) {
                p.error = "day must be 1-31 (checked against month after merge)";
                return p;
            }
            p.day = day;
        }
        if (body.has("iconType") || body.has("iconId")) {
            String iconType = body.has("iconType") ? optString(body, "iconType") : CalendarEvent.ICON_NONE;
            String iconId = optString(body, "iconId");
            p.error = validateIcon(iconType, iconId);
            if (p.error != null) {
                return p;
            }
            if (body.has("iconType")) {
                p.iconType = iconType;
            }
            if (body.has("iconId")) {
                p.iconId = iconId == null ? "" : iconId;
            }
        }
        if (body.has("description")) {
            p.description = optString(body, "description");
        }
        if (body.has("letter")) {
            p.letter = optString(body, "letter");
            if (p.letter == null) {
                p.letter = "";
            }
            p.error = validateLetter(p.letter);
            if (p.error != null) {
                return p;
            }
            // 同一请求里既改类型又绑信件时能立刻判死；只改类型的冲突由 CalendarEvent 清掉绑定
            if (!p.letter.isEmpty() && CalendarEvent.TYPE_BIRTHDAY.equals(p.type)) {
                p.error = "letter is only for festival events";
                return p;
            }
        }
        return p;
    }

    /**
     * 绑定信件字段：空 = 未绑定；非空必须是信件 id（{@code config/tothesky/letters} 里的文件名去 {@code .json}）。
     * <p>只挡「不可能是文件名」的写法（Windows 不允许的字符、路径分隔符、状态键分隔符 {@code |}），
     * 中文名等合法文件名一律放行；信件是否存在留给投递时判定。
     */
    private static String validateLetter(String letter) {
        if (letter.isEmpty()) {
            return null;
        }
        if (letter.endsWith(".json")) {
            return "letter 填文件名去掉 .json（如 chunjie），实为 " + letter;
        }
        if (letter.length() > 64) {
            return "letter 过长（最多 64 字符）";
        }
        for (int i = 0; i < letter.length(); i++) {
            if ("\\/:*?\"<>|".indexOf(letter.charAt(i)) >= 0) {
                return "letter 含文件名不允许的字符：" + letter;
            }
        }
        return null;
    }

    /** iconType 合法性；item/block 时 iconId 必须能从 registry 解析 */
    private static String validateIcon(String iconType, String iconId) {
        if (iconType == null || iconType.isEmpty()) {
            return null; // 默认 none
        }
        switch (iconType) {
            case CalendarEvent.ICON_NONE -> {
                return null;
            }
            case CalendarEvent.ICON_ITEM -> {
                if (iconId == null || iconId.isBlank()) {
                    return "iconId required for item icon";
                }
                ResourceLocation id = ResourceLocation.tryParse(iconId);
                // getValue 对未知 id 返回 AIR（默认值），须用 containsKey 判断存在性
                if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                    return "unknown item: " + iconId;
                }
                return null;
            }
            case CalendarEvent.ICON_BLOCK -> {
                if (iconId == null || iconId.isBlank()) {
                    return "iconId required for block icon";
                }
                ResourceLocation id = ResourceLocation.tryParse(iconId);
                if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
                    return "unknown block: " + iconId;
                }
                return null;
            }
            case CalendarEvent.ICON_PLAYER -> {
                if (iconId == null || iconId.isBlank()) {
                    return "iconId (player name) required for player icon";
                }
                return null;
            }
            default -> {
                return "iconType must be none/item/block/player";
            }
        }
    }

    private static String optString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        var el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static int optInt(JsonObject obj, String key, int def) {
        try {
            return obj.has(key) ? obj.get(key).getAsInt() : def;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return def;
        }
    }
}