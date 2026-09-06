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
| `recipes/add_tags.js` | 给其他模组物品加 tag（茄子/玉米/黄瓜/辣椒等）——`forge:cheese` 已随奶酪迁移 |
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
| `dumpling_making.js` | `dumpling/` 包全套 |
| `sell&roll.js` | `event/VendingEvents` |
| `item_events.js` 采血/竹蜻蜓/收割黑夜/礼花 | `item/HemostixPlusItem` / `CopterItem` / `HarvestTheNightItem` / `SparklerItem` |
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
| `startup_scripts/instruments.js` | 乐器注册 + ModSounds |
