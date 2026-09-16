package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 节日信库：读写 {@code config/tothesky/letters}。
 *
 * <p>一个 {@code *.json} = 一封信（格式见 {@link FestivalLetter}）；首次启动时若目录不存在，
 * 就建目录并放三份示例（明信片 / 包裹 / 红包，都带 {@code enabled: false}，不会误发），
 * 另外总会备好默认启用中的 {@code birthday.json}（见 {@link #ensureDefaultFiles()}）。
 *
 * <p><b>何时重读</b>：服务器启动后的首轮检查，以及 {@code /tothesky reloadletters}——「往目录里
 * 丢新 json / 改配置」本身不触发重读，改完要跑一次命令才生效（见 {@link #reload()}）。
 *
 * <p>目录只扫一层：子目录与非 {@code .json} 文件（编辑器临时文件、{@code .json.bak} 等）不会被读取。
 *
 * <p><b>按内容缓存</b>：同一文件内容没变就不重新解析——既省掉重复解析，
 * 更关键的是避免「坏文件反复重载都刷一遍 WARN」。文件删掉即从缓存移除。
 *
 * <p>只在服务端线程调用（见 {@link LetterScheduler}）。
 */
public final class LetterLibrary {
    /** 节日信目录：{@code config/tothesky/letters} */
    private static final String LETTERS_DIR = "letters";
    private static final String LETTER_SUFFIX = ".json";
    /** 生日信文件名（默认内容见 {@link #BIRTHDAY_LETTER}） */
    private static final String BIRTHDAY_FILE = "birthday.json";

    /** 文件名 → 缓存（文件内容 + 解析结果，解析失败记 null 以免重复报错） */
    private static final Map<String, Cached> CACHE = new HashMap<>();

    /** 「birthday.json 写不进去」只告警一次，避免每分钟刷屏 */
    private static boolean birthdayWriteWarned;

    private LetterLibrary() {
    }

    private record Cached(String content, FestivalLetter letter) {
    }

    /** 节日信目录 */
    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(ToTheSky.MODID).resolve(LETTERS_DIR);
    }

    /**
     * 备好目录与默认文件（幂等）。
     *
     * <p>调用点有三处：模组构造期（首次加载即备好，玩家还没进世界就能改配置，见 {@code ToTheSky}）、
     * 服务器启动后的首轮检查、{@code /tothesky reloadletters}（后两处见 {@link LetterScheduler}）。
     *
     * <p>三份**示例**（{@code example_*.json}）只在目录不存在时写一次——它们是说明书，
     * 删掉不该复活。{@code birthday.json} 不同：它是**启用中**的功能配置，缺失就补回默认的一份，
     * 所以「删掉它」不等于停用（下次加载又会长回来），停用请把文件里的 {@code enabled} 改成 {@code false}。
     */
    public static void ensureDefaultFiles() {
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
                writeExample(dir, "example_postcard.json", POSTCARD_EXAMPLE);
                writeExample(dir, "example_parcel.json", PARCEL_EXAMPLE);
                writeExample(dir, "example_red_packet.json", RED_PACKET_EXAMPLE);
                ToTheSky.LOGGER.info("[节日信] 已生成示例目录 {}（三份示例均为 enabled=false，改完再改成 true）", dir);
            } catch (IOException e) {
                ToTheSky.LOGGER.warn("[节日信] 生成示例目录 {} 失败：{}", dir, e.getMessage());
                return;
            }
        }
        ensureBirthdayLetter(dir);
    }

    /** 生日信配置缺失就补回默认内容（见 {@link #ensureDefaultFiles()} 的说明） */
    private static void ensureBirthdayLetter(Path dir) {
        Path file = dir.resolve(BIRTHDAY_FILE);
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.writeString(file, BIRTHDAY_LETTER, StandardCharsets.UTF_8);
            ToTheSky.LOGGER.info("[节日信] 已生成默认生日信 {}（日历里 type=birthday 的活动当天投递；停用请改 enabled=false）",
                    file);
        } catch (IOException e) {
            if (!birthdayWriteWarned) {
                birthdayWriteWarned = true;
                ToTheSky.LOGGER.warn("[节日信] 生成 {} 失败（生日信不会投递）：{}", file, e.getMessage());
            }
        }
    }

    /**
     * 重读目录，返回当前所有**有效且启用**的节日信（顺序按文件名，稳定）。
     *
     * <p>只在两处调用：服务器启动后的首轮检查、以及 {@code /tothesky reloadletters}
     * （见 {@link LetterScheduler}）——投递检查本身不碰磁盘，所以「改配置」总是显式的一次动作。
     */
    public static List<FestivalLetter> reload() {
        Path dir = dir();
        List<FestivalLetter> letters = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return letters;
        }
        Set<String> seen = new HashSet<>();
        try (Stream<Path> paths = Files.list(dir)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(LETTER_SUFFIX))
                    .sorted()
                    .toList();
            for (Path file : files) {
                FestivalLetter letter = loadOne(file);
                seen.add(file.getFileName().toString());
                if (letter != null) {
                    letters.add(letter);
                }
            }
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 扫描 {} 失败：{}", dir, e.getMessage());
            return letters;
        }
        // 文件已删除的缓存条目一并清掉，重新放回同名文件时会重新解析
        CACHE.keySet().retainAll(seen);
        return letters;
    }

    /** 读一个文件（命中缓存则跳过解析）；解析失败返回 null（已打日志） */
    private static FestivalLetter loadOne(Path file) {
        String name = file.getFileName().toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("[节日信] 读取 {} 失败：{}", name, e.getMessage());
            return null;
        }
        Cached cached = CACHE.get(name);
        if (cached != null && cached.content().equals(content)) {
            return cached.letter();
        }
        FestivalLetter letter = parse(name, content);
        CACHE.put(name, new Cached(content, letter));
        return letter;
    }

    /** 解析文件内容；任何异常（含 JSON 语法错）都只告警并返回 null */
    private static FestivalLetter parse(String name, String content) {
        String id = stripExtension(name);
        try {
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return FestivalLetter.parse(id, json);
        } catch (Exception e) {
            ToTheSky.LOGGER.warn("[节日信] {} 已跳过：JSON 解析失败（{}）", id,
                    e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(LETTER_SUFFIX)
                ? fileName.substring(0, fileName.length() - LETTER_SUFFIX.length())
                : fileName;
    }

    private static void writeExample(Path dir, String fileName, String json) throws IOException {
        Files.writeString(dir.resolve(fileName), json, StandardCharsets.UTF_8);
    }

    // ==================== 默认文件（改完把 enabled 改成 true 即生效） ====================

    /**
     * 默认生日信：收件人与日期都取自日历（{@code type=birthday} 的活动，活动名 = 玩家昵称），
     * 所以这份文件里没有 {@code player} / {@code date}。
     * <p>默认发一张明信片——明信片可以带正文，是「生日祝福」最自然的载体；
     * 想改送包裹/红包，把 {@code type} 与对应字段换掉即可（见 {@link FestivalLetter} 的格式说明）。
     */
    private static final String BIRTHDAY_LETTER = """
            {
              "_comment": "生日信：收件人与日期来自日历里 type=birthday 的活动（活动名 = 玩家昵称），当天投递；每个当天过生日的玩家各收一份。改这里即可更换生日礼物。停用请把 enabled 改成 false。",
              "enabled": true,
              "trigger": "birthday",
              "type": "postcard",
              "style": "contact:spring_day",
              "text": "祝${player}生日快乐！今天是${date}。"
            }
            """;

    private static final String POSTCARD_EXAMPLE = """
            {
              "_comment": "示例：明信片（样式 + 正文）。enabled 改成 true 才会投递。player 是收件人昵称；date 写 MM-DD 表示每年该日投递，写 YYYY-MM-DD 表示只投一次。${player} 会替换成收件人，${date} 会替换成投递当天的日期（yyyy-MM-dd）。",
              "enabled": false,
              "type": "postcard",
              "player": "Steve",
              "date": "10-24",
              "style": "contact:new_year_2023",
              "text": "祝${player}生日快乐！今天是${date}。"
            }
            """;

    private static final String PARCEL_EXAMPLE = """
            {
              "_comment": "示例：包裹（只有内容物，Contact 的包裹没有正文），最多 4 件。count 可省略（默认 1）。",
              "enabled": false,
              "type": "parcel",
              "player": "Steve",
              "date": "2027-01-01",
              "items": [
                { "item": "minecraft:cake", "count": 3 },
                { "item": "minecraft:apple", "count": 5 }
              ]
            }
            """;

    private static final String RED_PACKET_EXAMPLE = """
            {
              "_comment": "示例：红包（内容物 + 祝福语），最多 1 件。${item} 只在红包里有效，会替换成内容物清单（物品显示名×数量）。",
              "enabled": false,
              "type": "red_packet",
              "player": "Steve",
              "date": "01-01",
              "items": [
                { "item": "minecraft:diamond", "count": 8 }
              ],
              "text": "祝${player}新年快乐！红包里是${item}。"
            }
            """;
}
