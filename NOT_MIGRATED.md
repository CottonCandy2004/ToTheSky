# KubeJS 未迁移内容声明（1.20.1 分支）

本文档列出 RiaFST 4 实例（`D:\curseforge\minecraft\Instances\RiaFST 4\kubejs`）中**有意不迁移**到 ToTheSky 的内容及原因。迁移以原生 Java 实现为准；以下内容仍依赖 KubeJS 脚本运行，或已被原生实现取代。

## 一、服务器运营逻辑（不适合硬编码进模组）

| 脚本 | 说明 |
|------|------|
| `server_only/ban_items.js` | 违禁品检测与日志（config/illegal_list.txt） |
| `server_only/bot_manager.js` | 机器人管理 |
| `server_only/random_talk_list.js` | 随机语录 |
| `server_only/scheduled_events.js` | 定时事件 |
| `server_only/ria_protect/ria_protect.js` | RiaFST 领地保护 |
| `server_only/easy_functions.js` | 工具函数 |
| `server_only/wine_settings.js` | 酒类设置（与酿酒逻辑耦合） |
| `server_only/fps_settings.js` | FPS 设置 |
| `feature/check_server.js` | 服务器类型读取（industry_server/game_server）——检查点系统已迁移，但服务器类型配置属于运营配置 |
| `feature/auto_login.js` | 登录自动执行 `signin click` |
| `feature/landmark001.js` | 1 号地标传送门（固定坐标彩蛋） |
| `feature/compressed_chick.js` | 鸡被压扁彩蛋 |
| `feature/skin_extension.js` | 皮肤扩展 |

## 二、聚会玩法（party_scripts，~20 文件）

| 目录 | 说明 |
|------|------|
| `party_scripts/pig/` | 猪王 Boss 战（技能/职业/结构生成） |
| `party_scripts/fps/` | FPS 模式 |
| `party_scripts/cqb.js` | CQB 模式 |
| `party_scripts/kingchef2.js` | 厨王争霸 2 |
| `startup_scripts/fps.js` | FPS 活动物品（event_item_1~5）——**已迁移注册**，但玩法逻辑在 party_scripts 中 |

## 三、座椅系统

| 脚本 | 说明 |
|------|------|
| `startup_scripts/seat_entity_registry.js` | `seat` / `player_seat` 实体 |
| `feature/sit_on_player_server.js` + `client_scripts/sit_on_player_client.js` | 骑乘其他玩家 |

**原因**：涉及实体注册 + 网络同步 + 客户端渲染，工作量远超收益；且与 bugfix/seat_fix.js 耦合。如需迁移请单独提需求。

## 四、客户端脚本（不影响服务端逻辑）

| 脚本 | 说明 |
|------|------|
| `client_scripts/tooltips.js` | JADE/物品 tooltip 覆盖——原生物品 tooltip 已通过 TooltipItem 迁移 |
| `client_scripts/jeiModify.js` | JEI 物品隐藏 |
| `client_scripts/ponder.js` | Create Ponder 动画教程 |
| `client_scripts/brewing_barrel_render.js` | kk 酿桶渲染（已被注释禁用） |
| `client_scripts/sit_on_player_client.js` | 骑乘客户端 |
| `startup_scripts/client/keyBinding.js` | 按键绑定 |

## 五、其他模组的 bugfix（已在新版本修复或不再需要）

| 脚本 | 说明 |
|------|------|
| `bugfix/1182update/*` | 1.18.2→1.20.1 旧存档兼容层（contact/immersiveWeathering/sign/supplementaries） |
| `bugfix/chinjufumod_*` | 镇守府模组烤箱/植物修复 |
| `bugfix/coaster_fix.js` | 过山车修复 |
| `bugfix/drawerFix.js` | 抽屉修复 |
| `bugfix/seat_fix.js` | 座椅修复 |

## 六、数据包/资源包（仍走 KubeJS 数据驱动）

| 路径 | 说明 |
|------|------|
| `data/more_bullets/potato_cannon_projectile_types/*` | 18 种土豆炮弹药数据——属于 Create 数据包格式，可直接打包进模组 `data/more_bullets/` 但非紧迫 |
| `assets/carpetstairsmod/`、`assets/supplementaries/`、`assets/immersive_weathering/`、`assets/contact/`、`assets/displaydelight/` | 其他模组的中文汉化/模型修复资源包——不属于 ToTheSky 内容 |
| `assets/exposure/` | 魔法照片纹理（exposure 模组资源） |

## 七、配方调整（非本模组物品的通用修复）

| 脚本 | 说明 |
|------|------|
| `recipes/remove_recipes.js` | 移除原版/其他模组配方（83 行） |
| `recipes/replace_recipes.js` | 替换其他模组配方 |
| `recipes/remove_tags.js` | 移除 tag |
| `recipes/add_tags.js` | 给其他模组物品加 tag（茄子/玉米/黄瓜/辣椒等）——`forge:cheese` 与 `ultramarine:chisel_dye` 已迁移 |
| `recipes/ultramarine.js` | ultramarine 模组木工/凿石配方（与本模组无关） |
| `recipes/add_recipes.js` 中不含 `kubejs:` 的部分 | 镇守府/Create/夸克互转配方、长椅修复、P2P 调谐等 |

## 八、已被原生实现取代（脚本不再需要）

| 原脚本 | 原生替代 |
|--------|----------|
| `food_events.js` 披萨右键 | `block/PizzaBlock` 的 SLICES 属性 |
| `food_events.js` 拉面右键 | `block/RamenBlock` |
| `food_events.js` 特殊食物效果 | `item/SpecialFoodItems` |
| `food_events.js` 豆腐食用 | `item/BeanCurdItem` |
| `place_drink_block.js` | `block/DrinkGlassBlock` + `cocktail/CocktailHelper` |
| `wine_cabinet_interact.js` | `fstwines` 数据驱动（酒柜方块在 fstwines 模组） |
| `brewing_barrel_interact.js` | 已被注释禁用 |
| `dumpling_making.js` | **未取代**（见下，1.20.1 分支只保留注册，玩法逻辑仍在该脚本） |
| `sell&roll.js` | `event/VendingEvents` |
| `item_events.js` 采血/竹蜻蜓/收割黑夜/礼花 | `item/HemostixPlusItem` / `CopterItem` / `HarvestTheNightItem` / `SparklerItem` |
| `dew_of_oblivion_use.js`（PR#56） | `item/DewOfOblivionItem` |
| `BasicFoodBlock/Item`（PR#58 食物方块化） | `block/PlaceableFoodBlock` + `item/PlaceableFoodBlockItem` |
| `MechanicalChiselTableBlock` + `PackedColorsItem`（PR#59） | `block/MechanicalChiselTableBlock` + `item/PackedColorsItem` |
| `wrench_remove_block.js`（PR#59） | `event/WrenchRemoveEvents` |
| `block_events.js` 春节部分 | `block/MultipleFireworksBlock` / `MultipleFirecrackersBlock` |
| `block_events.js` 润滑剂/汉堡/行商 | `event/ItemFeatureEvents` / `block/BurgerBlock` |
| `instruments_server.js` | `item/InstrumentItem` + `event/InstrumentsEvents` |
| `checker_server.js` | `block/CheckerBlock` + `event/CheckerEvents` |
| `sickle_use.js` | `event/SickleEvents` |
| `magic_photo.js` | `event/MagicPhotoEvents` |
| `startup_scripts/registry.js` | `registry/ModItems` + `ModBlocks` + `ModFluids` + `ModEffects` |
| `startup_scripts/springFestival.js` | 同上（firecracker/sparkler/烟花方块） |
| `startup_scripts/wine_startup.js` | 同上（酿酒方块，逻辑未迁移） |
| `startup_scripts/magic_photo.js` | 四色魔石注册 |
| `startup_scripts/celestia_startup.js` | 索拉里斯勋章注册 |
| `startup_scripts/checker.js` | 检查站方块注册 |
| PR#21 按键绑定 `startup_scripts/client/keyBinding.js` | 客户端按键绑定（服务端无逻辑，客户端功能留 kjs） |
| `startup_scripts/instruments.js` | 乐器注册 + ModSounds |

## 九、饺子（1.20.1 分支：注册 + 取食/食用已迁，包制与煮制仍在脚本）

1.21.1 分支的饺子玩法实现（`dumpling/` 包、厨锅会话、盘子方块等）曾在 1.20.1 移植（`ec53c5c`），随后由 `79efe9b` 整体移除。本分支重建了注册，并把**取食**与**食用**搬进了 Java：

| 注册 | 位置 | 说明 |
|------|------|------|
| `dumpling_wrapper` / `raw_dumpling` / `raw_dumpling_plate` / `cooked_dumpling` / `cooked_dumpling_plate`（物品） | `registry/ModItems` | 数值与旧 kjs 一致（饺子 4 饥饿/1.0 饱和/快速进食、两种盘子不可堆叠） |
| `cooked_dumpling_plate`（方块） | `registry/ModBlocks` + `block/CookedDumplingPlateBlock` | `bite 0-9` + `facing`，与旧 kjs `cardinal` 方块状态一致，无掉落 |
| `dumpling_plate`（方块实体） | `registry/ModBlockEntities` + `blockentity/DumplingPlateBlockEntity` | 内容物 NBT（`data.filling` / `data.author`）原样透传 |

已迁入 Java 的逻辑：

| 逻辑 | 实现 | 脚本侧 |
|------|------|--------|
| 从一盘熟饺子逐个取出 | `block/CookedDumplingPlateBlock.use`（`bite` 兼作已取份数与下标，第 8 口还碗并移除方块） | 原 `BlockEvents.rightClicked("tothesky:cooked_dumpling_plate")` **已删** |
| 吃饺子按馅料生效 | `item/CookedDumplingItem.finishUsingItem`（可食用馅料委托其 `finishUsingItem` 让玩家真的吃下，容器回收进背包；不可食用原样给到手） | 原 `ItemEvents.foodEaten("tothesky:cooked_dumpling")` **已删**（含随之失去引用的 `dumpling$spawnFakeTNT`） |
| 饺子命名/评语/颜色/时长 | `dumpling/DumplingNamer` + `dumpling/DumplingFactory` | 与脚本 `dumpling$processNBT` 同种子同抽取顺序，两边同名 |
| 放置一盘饺子时搬内容物进方块实体 | `item/CookedDumplingPlateItem#updateCustomBlockEntityTag` | 脚本的「邻位补数据」分支保留（对已放置的盘子仍有效） |

**为何必须删脚本侧那两个处理**：Forge 的 `PlayerInteractEvent.RightClickBlock` 先于 `BlockState.use()` 触发（`ServerPlayerGameMode`），两边都执行会导致**一次右键取出两只饺子**、第 8 口返还两个碗、食用时馅料被吃两遍/给两份。`ItemEvents.foodEaten` 同理与 `finishUsingItem` 叠加。

仍在脚本里的：包制（砧板/长按）、厨锅与森罗汤锅的煮制、饺子的枚举与 lore 生成入口。

**行为差异（按需求刻意为之）**：旧脚本对 `minecraft:tnt` 馅料会生成一个不破坏方块的假 TNT；新实现把 TNT 当普通不可食用物品**直接给到手中**。

- `MissingMappingEvents` 负责 `kubejs:*` → `tothesky:*` 的存档 remap；方块实体类型走 `event/KjsRegistryAliasEvents` 的**注册表别名**（该注册表在 Forge 侧 `disableSaving()`，从不写进存档快照，永远不会产生缺失映射事件，而区块里的方块实体是按名字解析的）。
- 资源（贴图 / 方块模型 / blockstate / 物品模型 / lang）已恢复；`cooked_dumpling_plate` 的方块模型带 `render_type: minecraft:cutout`。

## 十、kubejs v4Update 分支（RiaFST 4 KubeJS 脚本库的 mod 并行版）
RiaFST 4 的 kubejs 脚本库已建 `v4Update` 分支（从 main 分出）：
- **已删除**：全部迁移到 mod 的注册与逻辑（startup 注册、food/item/block events、售货机、镰刀、检查站、乐器、鸡尾酒放置、雕刻台、遗忘之露、扳手拆除、配方段等），assets/kubejs 仅保留 FPS 活动物品与 player_seat 几何。
- **保留并改指 tothesky: id**：dumpling_making（汤锅段未迁）、wine_server（酿酒逻辑未迁——WineCraftingTableBlock 仅注册方块形态）、loots、tooltips/jeiModify/ponder、party 脚本。
- **保留 kubejs: 注册**：fps.js 的 event_item_1~5（活动物品）、seat/player_seat（座椅实体）、guitar_sound 之外的 kjs 端音效无。
- 旧存档迁移：`event/MissingMappingEvents` 做 kubejs:* → tothesky:* 的注册表 remap（方块/物品/流体/效果/音效，含披萨阶段方块）；方块实体类型因 Forge 侧 `disableSaving()` 不走该事件，由 `event/KjsRegistryAliasEvents` 在 `RegisterEvent` 里加注册表别名（`kubejs:cooked_dumpling_plate` → `tothesky:dumpling_plate` 等）。
