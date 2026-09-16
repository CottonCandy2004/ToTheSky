# 星露谷式日历系统设计（1.20.1 Forge 分支）

日期：2026-09-11

## 需求摘要

模仿星露谷的日历系统：

1. 一个可放置的日历方块（含方块物品），模型暂时留空（引用尚未存在的贴图，游戏内显示紫黑棋盘格）。
2. 右键方块弹出日历 GUI：
   - 底图 `textures/gui/calendar.png`（256×256）+ 日期格 `textures/gui/calendar_day.png`（24×24，四周 1px 透明边，可见内容 22×22）。
   - 日期格区：起点 (42, 92)，7 列 × 5 行，格子间距 25px（22px 内容 + 3px 间隔）。月份需要 6 周时在 y=217 追加第 6 行（仍在底图内）。
   - 每个日期格可显示一个或多个节日/玩家生日；多个活动时图标每 40 tick 轮播一个。
   - 悬停格子显示当日全部活动 tooltip（名称、类型、描述）。
   - 上/下月箭头翻页（1..12 循环），年份数字取服务器现实年份。
   - 「今天」（服务器现实日期）的格子高亮。
   - 日期格渲染图标：物品 / 方块 / 玩家的头。
3. 后端 REST API（外部网页增删查改）：JDK 内置 HttpServer，默认 `127.0.0.1:39000`，`config/tothesky-common.toml` 的 `calendar.port`/`calendar.bindAddress`（Forge COMMON 配置）可改端口与绑定地址。
4. 数据持久化：Overworld SavedData（随存档保存，与现有 `CheckerIndexData` 模式一致）。
5. 编辑全走外部 API；游戏内 GUI 只读。

## 已确认的用户决策

| 问题 | 决策 |
|---|---|
| 日期来源 | 现实公历（服务器系统时钟），月视图 |
| API 形态 | 内置 HTTP REST（localhost:39000，端口可配） |
| GUI 交互 | 只读 + 翻月；悬停 tooltip；今天高亮 |
| 多活动显示 | 单图标轮播（MC 物品 16px 已几乎填满 22px 格） |
| 占位图 | 用户已提供（日历.png 256×256、日期框.png 24×24） |
| 方块形态 | 完整立方体（16×16×16） |

## 架构

```
CalendarBlock（方块，use() → 服务端发包开屏）
        │
        ▼
CalendarData（SavedData，服务器权威，事件 CRUD）
        ▲                              ▲
        │ server.execute()             │ 读取/写入
CalendarHttpServer ── REST CRUD ──┘
        │ 变更后广播
        ▼
ModNetwork（SimpleChannel）──S2C CalendarDataPacket──▶ CalendarScreen（客户端 GUI）
```

### 数据模型 `CalendarEvent`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID | 创建时服务端生成 |
| name | String | 活动名（必填） |
| type | FESTIVAL / BIRTHDAY | 节日或生日 |
| month | 1..12 | 现实月 |
| day | 1..31 | 现实日（按月校验，2 月允许 29） |
| iconType | NONE / ITEM / BLOCK / PLAYER | 图标形态 |
| iconId | String | 物品/方块 registry id，或玩家名 |
| description | String | 可选，tooltip 显示 |

所有事件按「月-日」键控，逐年循环（无年份字段）。

### REST API（`/api/calendar/`）

- `GET /events` — 全部；`?month=N` 过滤
- `GET /events/{id}` — 单个（404）
- `POST /events` — 创建（校验字段，返回含 id 的完整对象）
- `PUT /events/{id}` — 部分更新
- `DELETE /events/{id}` — 删除
- `GET /today` — `{"date":"2026-09-11","events":[...]}`

约定：JSON；CORS 全开（`Access-Control-Allow-Origin: *` + OPTIONS 预检）；所有读写经 `server.execute()` 到服务器线程执行（5s 超时 → 503）；任何变更后向所有在线客户端广播新数据，打开中的 GUI 即时刷新。

### 网络与 GUI

- `CalendarDataPacket(open, year, month, today-day, events)` 单一 S2C 包：`open=true` 打开屏幕，否则仅刷新已打开屏幕的数据。
- 客户端类全部隔离在 `client` 包，经 DistExecutor 安全引用，专用服不加载。
- 玩家头渲染：SkinManager 注册 GameProfile 皮肤 → 从皮肤贴图 blit 脸部 8×8 + 帽子 40,8 两层；未加载完成时跳过图标。
- 物品/方块图标：`GuiGraphics.renderItem`（方块取其物品形态）。

### 持久化

SavedData `tothesky_calendar` 挂 Overworld（`server.overworld().getDataStorage()`），NBT List 存储，与 `CheckerIndexData` 相同模式。事件量级小（几十条），全量读写。

### 生命周期

- `ServerStartedEvent`：初始化 SavedData、读 config、启动 HTTP（端口占用仅告警不崩服）。
- `ServerStoppingEvent`：置停机标志拒绝新请求；`ServerStoppedEvent`：关 HTTP。

## 资源

- `assets/tothesky/textures/gui/calendar.png`、`calendar_day.png` ← 用户 Downloads 占位图拷贝。
- `blockstates/calendar.json` + `models/block/calendar.json` + `models/item/calendar.json`：JSON 齐全但引用不存在的贴图 `tothesky:block/calendar`（模型留空约定，后续画好贴图即完成）。
- `data/tothesky/loot_tables/blocks/calendar.json`：破坏掉落物品。
- 语言：`block.tothesky.calendar` = 日历 / Calendar。

## 错误处理

- HTTP JSON 解析失败 / 字段非法 → 400 + 错误消息。
- 未知物品/方块 id → 400（创建/更新时校验 `ForgeRegistries.ITEMS/BLOCKS`）。
- 端口被占用 → 日志告警，HTTP 禁用，游戏正常运行。
- 图标 id 在客户端解析失败（mod 卸载等）→ 跳过图标只显示文字。
- 服务器线程超时 → 503。

## 测试与验证

- 仓库无单测基建，CI 只跑 `gradlew build`（遵循仓库规则）；编译通过为底线。
- 烟雾测试：`runServer` 起服（EULA 已接受后）+ curl 全套 REST CRUD + 重启存档验证持久化。
- GUI 视觉与交互（翻月、tooltip、轮播、头渲染）无法无头验证 → 更新 `docs/serverTest_todo.md` 清单，用户进游戏手测。

## 范围外（YAGNI）

- 年份字段/一次性事件、游戏内编辑、权限控制（默认仅 localhost 绑定已隔离）、农历、方块模型与贴图。