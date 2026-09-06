package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModBlocks;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检查点系统（移植自 kubejs checker_server.js 与 check_server.js，行为以 RiaFST 4 版为准）。
 *
 * <ul>
 *   <li>放置/破坏检查站（CheckerBlock 回调）→ 维护 SavedData 索引并同步 config/CheckerData/posList.txt；
 *       破坏时同时删除 config/CheckerData/&lt;x,y,z&gt;.txt 记录文件。</li>
 *   <li>每 tick 检测玩家：冷却 5000ms（玩家 persistentData.lastInChecker）、手持 create:wrench 跳过；
 *       对每个检查站做 XZ 平面 3x3 洪水填充（supplementaries:checker_block 视为连接的地板块），
 *       玩家脚部落点位于任一子块上方 y∈[y, y+4) 判定通过（与旧脚本边界一致：y+4 含、xz 为 [x-0.5, x+1.5]）。
 *       原脚本的 checkerToBlocks 是内存缓存（重启即丢），这里改为每次判定实时洪水填充。</li>
 *   <li>手持 create:wrench 右键检查站：潜行=清空记录文件；非潜行=逐条 tell 记录。</li>
 *   <li>ServerStartedEvent：读取 config/server_settings.json 存入 SavedData（缺失/损坏则写默认文件）。</li>
 * </ul>
 *
 * 检查站默认部署在主世界；洪水填充取主世界方块（跨维度时按坐标几何判定，与旧脚本行为一致）。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class CheckerEvents {
    // ---- 判定参数（与旧脚本逐字一致） ----
    private static final long CHECK_INTERVAL_MS = 5 * 1000L;
    private static final int Y_HEIGHT = 4; // 检测的高度范围
    private static final int SEARCH_RADIUS = 1; // 寻找棋盘格方块检测半径（3*3）
    private static final String SUB_BLOCK_ID = "supplementaries:checker_block";

    private static final String WRENCH_ITEM_ID = "create:wrench";
    private static final String TAG_LAST_IN_CHECKER = "lastInChecker";

    private static final Path CHECKER_DATA_DIR = FMLPaths.CONFIGDIR.get().resolve("CheckerData");
    private static final Path CHECKER_INDEX_FILE = CHECKER_DATA_DIR.resolve("posList.txt");

    // 逐字消息
    private static final String MSG_PASSED = "你经过了一个检查站";
    private static final String MSG_CANNOT_READ = "！无法获取检查站信息，请尝试打破并重放";
    private static final String MSG_CLEARED = "此检查站信息已清空";
    private static final String MSG_NO_RECORD = "此检查站还未有人路过";
    private static final String SEPARATOR = "———————————————————————————————————";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    private static final Gson GSON = new Gson();

    private CheckerEvents() {
    }

    // ---------------- 服务器启动：索引 + 设置 ----------------

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        initCheckerIndex(server);
        initServerSettings(server);
    }

    /** 开服时加载检查站索引：SavedData 为空则从 posList.txt 导入（兼容旧 kubejs 存档与手工编辑），随后把文件同步回索引 */
    private static void initCheckerIndex(MinecraftServer server) {
        CheckerIndexData index = CheckerIndexData.get(server);
        if (index.isEmpty() && Files.isRegularFile(CHECKER_INDEX_FILE)) {
            try {
                for (String line : Files.readAllLines(CHECKER_INDEX_FILE, StandardCharsets.UTF_8)) {
                    BlockPos pos = CheckerIndexData.parsePos(line.trim());
                    if (pos != null) {
                        index.add(pos);
                    }
                }
            } catch (IOException e) {
                ToTheSky.LOGGER.error("无法读取检查站索引文件 {}", CHECKER_INDEX_FILE, e);
            }
        }
        writeIndexFileQuietly(index);
    }

    /** 读 config/server_settings.json；缺失或解析失败时写默认文件（与旧脚本一致） */
    private static void initServerSettings(MinecraftServer server) {
        JsonObject settings = null;
        Path settingsFile = FMLPaths.CONFIGDIR.get().resolve("server_settings.json");
        try {
            settings = JsonParser.parseString(Files.readString(settingsFile, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ToTheSky.LOGGER.error("oops,在读取服务器设置时遇到了以下问题：" + e + "，服务器将以默认设置启动");
            try {
                Files.writeString(settingsFile, GSON.toJson(defaultSettings()), StandardCharsets.UTF_8);
            } catch (IOException e2) {
                ToTheSky.LOGGER.error("无法写入默认服务器设置文件 {}", settingsFile, e2);
            }
        }
        if (settings == null) {
            settings = defaultSettings();
        }
        ServerSettingsData data = ServerSettingsData.get(server);
        data.apply(
                boolSetting(settings, "industry_server", false),
                boolSetting(settings, "game_server", false),
                stringSetting(settings, "game", ""),
                stringSetting(settings, "id", "default"));
    }

    /** 默认设置对象：键序与旧脚本 JSON.stringify(settings) 完全一致 */
    private static JsonObject defaultSettings() {
        JsonObject json = new JsonObject();
        json.addProperty("industry_server", false);
        json.addProperty("game_server", false);
        json.addProperty("game", "");
        json.addProperty("id", "default");
        return json;
    }

    private static boolean boolSetting(JsonObject json, String key, boolean def) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return def;
        }
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    private static String stringSetting(JsonObject json, String key, String def) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return def;
        }
        try {
            return json.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    // ---------------- 玩家登录：冷却归零（与旧脚本 PlayerEvents.loggedIn 一致） ----------------

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().putLong(TAG_LAST_IN_CHECKER, System.currentTimeMillis());
        }
    }

    // ---------------- 索引维护（CheckerBlock 回调） ----------------

    /** 放置检查站：索引加坐标 + 同步 posList.txt + 确保记录文件存在 */
    public static void onCheckerPlaced(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        CheckerIndexData index = CheckerIndexData.get(server);
        if (index.add(pos)) {
            writeIndexFileQuietly(index);
        }
        Path file = stationFile(pos);
        try {
            Files.createDirectories(CHECKER_DATA_DIR);
            if (!Files.exists(file)) {
                Files.writeString(file, "", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            ToTheSky.LOGGER.error("无法创建检查站记录文件 {}", file, e);
        }
    }

    /** 破坏检查站：索引删坐标 + 同步 posList.txt + 删除记录文件 */
    public static void onCheckerBroken(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        CheckerIndexData index = CheckerIndexData.get(server);
        if (index.remove(pos)) {
            writeIndexFileQuietly(index);
        }
        try {
            Files.deleteIfExists(stationFile(pos));
        } catch (IOException e) {
            ToTheSky.LOGGER.error("无法删除检查站记录文件 {}", stationFile(pos), e);
        }
    }

    private static void writeIndexFileQuietly(CheckerIndexData index) {
        try {
            writeIndexFile(index);
        } catch (IOException e) {
            ToTheSky.LOGGER.error("无法同步检查站索引文件 {}", CHECKER_INDEX_FILE, e);
        }
    }

    /** 每个坐标一行 "x,y,z"，与旧脚本 removeEmptyLines 清理后的 posList.txt 格式一致 */
    private static void writeIndexFile(CheckerIndexData index) throws IOException {
        Files.createDirectories(CHECKER_DATA_DIR);
        StringBuilder sb = new StringBuilder();
        for (BlockPos pos : index.positions()) {
            sb.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ())
                    .append(System.lineSeparator());
        }
        Files.writeString(CHECKER_INDEX_FILE, sb.toString(), StandardCharsets.UTF_8);
    }

    // ---------------- 每 tick 检测玩家经过 ----------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        CheckerIndexData index = CheckerIndexData.get(server);
        if (index.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();

        // 先筛出本 tick 冷却已过且未手持扳手的玩家（与旧脚本一致的“每 tick 判一次”语义：
        // 同一 tick 内多个重叠检查站可各记一次，冷却在下一 tick 才生效）
        List<ServerPlayer> ready = new ArrayList<>();
        for (ServerPlayer player : players) {
            long last = player.getPersistentData().getLong(TAG_LAST_IN_CHECKER);
            if (nowMs - last < CHECK_INTERVAL_MS) {
                continue;
            }
            if (isWrench(player.getMainHandItem())) {
                continue;
            }
            ready.add(player);
        }
        if (ready.isEmpty()) {
            return;
        }

        // 检查站在外层：每个检查站每 tick 只做一次洪水填充
        ServerLevel overworld = server.overworld();
        for (BlockPos checkerPos : index.positions()) {
            List<BlockPos> connected = findConnectedBlocks(overworld, checkerPos);
            for (ServerPlayer player : ready) {
                if (isInsideChecker(connected, player.position())) {
                    recordPass(player, checkerPos, nowMs);
                }
            }
        }
    }

    /**
     * 洪水填充：以 startPos 为中心在 XZ 平面 3x3 扩张，收集所有相互连接的
     * supplementaries:checker_block（起始检查站本身必然在集合中，与旧脚本一致）。
     */
    private static List<BlockPos> findConnectedBlocks(ServerLevel level, BlockPos start) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> result = new ArrayList<>();
        visited.add(start.asLong());
        queue.add(start);
        result.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos checkPos = pos.offset(dx, 0, dz);
                    if (visited.add(checkPos.asLong()) && isCheckerBlock(level, checkPos)) {
                        queue.add(checkPos);
                        result.add(checkPos);
                    }
                }
            }
        }
        return result;
    }

    private static boolean isCheckerBlock(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return key != null && SUB_BLOCK_ID.equals(key.toString());
    }

    /** 玩家脚部落点是否位于连接集合中任一子块上方（比较边界与旧脚本逐字一致） */
    private static boolean isInsideChecker(List<BlockPos> connected, Vec3 playerPos) {
        double px = playerPos.x();
        double py = playerPos.y();
        double pz = playerPos.z();
        for (BlockPos pos : connected) {
            boolean inX = px >= pos.getX() - 0.5 && px <= pos.getX() + 1.5;
            boolean inZ = pz >= pos.getZ() - 0.5 && pz <= pos.getZ() + 1.5;
            boolean inY = py >= pos.getY() && py <= pos.getY() + Y_HEIGHT;
            if (inX && inY && inZ) {
                return true;
            }
        }
        return false;
    }

    /** 通过检查站：追加记录行 + actionbar + 粒子 + 音效（与旧脚本逐字一致） */
    private static void recordPass(ServerPlayer player, BlockPos checkerPos, long nowMs) {
        String timestamp = "[" + LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT) + "] ";
        String line = timestamp + posKey(checkerPos) + " " + player.getName().getString();
        Path file = stationFile(checkerPos);
        try {
            appendLine(file, line);
        } catch (IOException e) {
            ToTheSky.LOGGER.error("无法写入检查站记录文件 {}", file, e);
            return;
        }

        player.displayClientMessage(Component.literal(MSG_PASSED).withStyle(ChatFormatting.GREEN), true);
        Vec3 pos = player.position();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.5, pos.z,
                100, 1.0, 1.0, 1.0, 0.5);
        // playsound ... player @a[distance=..20] ~ ~ ~ 1 1：声源在玩家位置，听众限同维度 20 格内
        long seed = level.random.nextLong();
        ClientboundSoundPacket sound = new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HARP,
                SoundSource.PLAYERS, pos.x, pos.y, pos.z, 1.0F, 1.0F, seed);
        for (ServerPlayer listener : level.players()) {
            if (listener.distanceToSqr(pos.x, pos.y, pos.z) <= 20.0 * 20.0) {
                listener.connection.send(sound);
            }
        }
        player.getPersistentData().putLong(TAG_LAST_IN_CHECKER, nowMs);
    }

    // ---------------- 手持扳手右键检查站 ----------------

    @SubscribeEvent
    public static void onRightClickChecker(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.CHECKER.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isWrench(player.getMainHandItem())) {
            return;
        }

        Path file = stationFile(pos);
        if (player.isCrouching()) { // 潜行：清空该站记录文件
            if (!Files.exists(file)) {
                player.sendSystemMessage(Component.literal(MSG_CANNOT_READ));
                return;
            }
            try {
                Files.writeString(file, "", StandardCharsets.UTF_8);
            } catch (IOException e) {
                ToTheSky.LOGGER.error("无法清空检查站记录文件 {}", file, e);
                return;
            }
            player.sendSystemMessage(Component.literal(MSG_CLEARED));
        } else { // 非潜行：读出全部记录逐条 tell
            if (!Files.exists(file)) {
                player.sendSystemMessage(Component.literal(MSG_CANNOT_READ));
                return;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                ToTheSky.LOGGER.error("无法读取检查站记录文件 {}", file, e);
                return;
            }
            if (lines.isEmpty()) {
                player.sendSystemMessage(Component.literal(MSG_NO_RECORD));
                return;
            }
            player.sendSystemMessage(Component.literal(SEPARATOR));
            for (String element : lines) {
                player.sendSystemMessage(Component.literal(element));
            }
            player.sendSystemMessage(Component.literal(SEPARATOR));
        }
    }

    // ---------------- 工具 ----------------

    /** 手持物品是否为 create:wrench（用注册名比较，避免对 Create 的编译期依赖） */
    private static boolean isWrench(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && WRENCH_ITEM_ID.equals(key.toString());
    }

    private static Path stationFile(BlockPos pos) {
        return CHECKER_DATA_DIR.resolve(posKey(pos) + ".txt");
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void appendLine(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
