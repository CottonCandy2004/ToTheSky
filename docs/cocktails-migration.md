# 鸡尾酒迁移（fstwines → ToTheSky）

> 状态：**已实现**（2026-08-28）。
> 目标：把原 KubeJS 通过胡萝卜厨房（kitchenkarrot）添加的 7 种鸡尾酒连同效果迁移进本模组，并用**稳定的消耗完成事件**替换原先的脆弱检测。

---

## 1. 待迁移的鸡尾酒清单（7 种）

源：`kubejs/assets/fstwines/cocktail/list.json` + `kubejs/data/fstwines/recipes/*.json`

| cocktail id | 中文名 | 配方 author | 自定义喝完效果 |
|---|---|---|---|
| `fstwines:david` | 大卫·马丁内斯 | xtaotie233,ACHTER_BERG | 无（仅 buff） |
| `fstwines:call_of_tahiti` | 大溪地的原野呼唤 | Volloval | **聊天广播**「我一定会上工的，嗝～」 |
| `fstwines:free_nightingale` | 自由夜莺海 | ACHTER_BERG | **随机送一件物品**（4 选 1） |
| `fstwines:dangerous_party` | 危险派对 | EndGlacier | **击鼓传花**（hot potato 游戏） |
| `fstwines:silent_midnight` | 静谧午夜 | EndGlacier | 无（仅 buff） |
| `fstwines:shoal_in_dream` | 梦之汀 | EndGlacier | **放/给一张蓝色床** |
| `fstwines:lago_de_texcoco` | 特斯科科湖 | Vic_Bloomfield | 无（仅 buff） |

各配方的原料、酿造时间、饥饿/饱食、buff 列表完整记录在
`kubejs/data/fstwines/recipes/<name>.json`（1.20.1 schema，见 §3）。

---

## 2. 关键版本差异（**必须先理解**）

参考 kubejs 来自 **MC 1.20.1 + kitchenkarrot 1.20.1-0.6.4b**；
ToTheSky 目标是 **MC 1.21.1 + kitchenkarrot 1.21-0.6.3b**。
两版 kitchenkarrot 的鸡尾酒模型**不兼容**：

| 维度 | 1.20.1（旧） | 1.21.1（新） |
|---|---|---|
| 鸡尾酒存取 | ItemStack **NBT** 字符串 `cocktail` | **数据组件** `ModDataComponents.COCKTAIL`（类型 `CocktailProperty`） |
| `CocktailItem.getCocktail(stack)` | 返回 `ResourceLocation` | 返回 `CocktailProperty`（无则 UNKNOWN） |
| 效果来源 | 配方 `content.effect` 列表 | 注册表项 `CocktailProperty.effectStack()` |
| 配方 JSON 字段 | `author` + `content.effect` | `cocktail_property`（引用注册表 id）+ `content.{recipe,craftingtime,hunger,saturation}`（**无 effect**） |
| 注册表 | 无独立注册表（配方 id 即鸡尾酒 id） | `ModCocktails.COCKTAILS_REGISTRY`（见 §4） |

**结论：旧配方 JSON（`author`+`effect`）在 1.21.1 解析不了，必须按新 schema 重写。**

---

## 3. 旧配方 schema（1.20.1，kubejs 源）

`data/fstwines/recipes/david.json` 例：
```json
{
  "type": "kitchenkarrot:cocktail",
  "author": "xtaotie233,ACHTER_BERG",
  "content": {
    "recipe": [ {"item":"kitchenkarrot:vodka_base"}, ... ],
    "craftingtime": 80,
    "hunger": 1,
    "saturation": 2,
    "effect": [ {"id":"kitchenkarrot:tipsy","lvl":0,"duration":3600}, ... ]
  }
}
```

## 4. 新 schema（1.21.1）— 鸡尾酒属性是**静态注册表**，**不是数据包驱动**

> ⚠️ 纠正一个易错假设：`CocktailProperty` 注册表是 `RegistryBuilder.sync(true).create()`
> 经 `NewRegistryEvent` 创建的**静态同步注册表**，jar 内**没有** `DataPackRegistryEvent`。
> **只有配方是数据包 JSON；`CocktailProperty` 必须用代码注册。**

### kitchenkarrot 关键类（1.21.1-0.6.3b）
- `io.github.tt432.kitchenkarrot.cocktail.CocktailProperty` = record `(ResourceLocation id, String author, List<EffectStack> effectStack)`
- `io.github.tt432.kitchenkarrot.recipes.object.EffectStack` = `(String id, int lvl, int duration)`，`get()` → `MobEffectInstance`
- `io.github.tt432.kitchenkarrot.registries.ModCocktails`
  - `COCKTAILS_REGISTRY`（`Registry<CocktailProperty>`，静态字段）
  - `COCKTAIL_PROPERTIES`（`DeferredRegister<CocktailProperty>`，命名空间 kitchenkarrot）
  - `register(String name, String author, List<EffectStack>)` → 注册到 COCKTAIL_PROPERTIES
  - 内部 `CocktailProperty` 的 `id` 由 `CocktailModelRegistry.cocktailRL(name)` 生成（native 是 `kitchenkarrot:cocktails/<name>`）
- `CocktailItem.getCocktail(stack)` / `setCocktail(stack, prop)`（静态，读写数据组件）
- `CocktailItem.get(level, prop)` → 用 `recipeManager.byKey(prop.id())` 找配方

### native 三层 id 关系（务必对齐）
| 层 | 例 | 说明 |
|---|---|---|
| 注册表 key | `kitchenkarrot:bane_of_arthropods` | DeferredRegister 的 name |
| `CocktailProperty.id` | `kitchenkarrot:cocktails/bane_of_arthropods` | `cocktailRL` 加 `cocktails/` 前缀 |
| 配方 id（文件路径） | `kitchenkarrot:cocktails/bane_of_arthropods` | 与 `CocktailProperty.id` 一致，`byKey` 匹配 |
| 配方 JSON `cocktail_property` | `kitchenkarrot:bane_of_arthropods` | 引用**注册表 key** |

### fstwines 旧 id 约定（**无 `cocktails/` 前缀**）
lang 用 `fstwines.david` → `CocktailProperty.id` 应为 `fstwines:david`（`getDescriptionId` = `id.toString().replace(':','.')`）。
- 名字 lang key：`fstwines.david`
- tooltip lang key：`fstwines.david.tooltip`
- 作者 tooltip 固定 key：`item.cocktail.author`（参数 = author 字符串）

> 因此迁移时 fstwines 的 `CocktailProperty.id` 直接用 `ResourceLocation.fromNamespaceAndPath("fstwines", name)`，**不要**走 `cocktailRL`（那会强加 `cocktails/` 前缀）。

### 注册方式（新代码）
在 ToTheSky 侧建一个 `DeferredRegister.create(ModCocktails.COCKTAILS_REGISTRY, "fstwines")`，
`register(name, () -> new CocktailProperty(ResourceLocation.fromNamespaceAndPath("fstwines", name), author, effectList))`。
7 种各注册一项，`effectList` 由旧配方 `content.effect` 转成 `EffectStack`。

---

## 5. 稳定消耗检测（核心诉求）

### 原 kubejs 的脆弱做法（`server_scripts/feature/food_events.js`）
`ItemEvents.rightClicked("kitchencarrot:cocktail")` → 记录全背包同种鸡尾酒数量 → 32 tick 后再数一次，数量减 1 且恰过 32 tick 才判定“喝完了”。问题：依赖固定 32 tick 饮用时长、数背包差值，极易误判/漏判。

### 稳定方案：**NeoForge 自带事件，无需反射/mixin**
`net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish`

- 触发点：`LivingEntity.completeUsingItem()`（`LivingEntity.java` ~3280），服务端、喝完那一刻。
  ```java
  ItemStack copy = this.useItem.copy();               // 消耗前副本（含鸡尾酒组件）
  EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(),
                             this.useItem.finishUsingItem(level, this));
  ```
- 事件体：`getItem()` = 消耗前副本（可读鸡尾酒 id）；`getEntity()` = 玩家。
- 用法：
  ```java
  @SubscribeEvent
  static void onDrink(LivingEntityUseItemEvent.Finish e) {
      ItemStack s = e.getItem();
      if (!(s.getItem() instanceof CocktailItem)) return;   // 或判 COCKTAIL 组件
      CocktailProperty prop = CocktailItem.getCocktail(s);
      ResourceLocation id = prop.id();                       // fstwines:david 等
      if (e.getEntity() instanceof ServerPlayer sp) { /* switch(id) 跑自定义效果 */ }
  }
  ```
- 仅在服务端跑（`completeUsingItem` 在服务端路径），天然规避双端/计时问题。

---

## 6. 自定义效果迁移清单（kubejs `switch` → Java）

源：`food_events.js` 的 `switch (wineItemType)`（注意旧 id 带 `fstwines:` 前缀）。

1. **`fstwines:call_of_tahiti`** → 全服 tellraw 聊天：
   `• <玩家>: 我一定会上工的，嗝～`（绿色/灰色/白色混合样式，可用 `Component` 拼装或简化）。
2. **`fstwines:free_nightingale`** → 随机送 4 选 1：
   `4x nethersdelight:propelpearl` / `4x culturaldelights:pickle` /
   `4x create_confectionery:caramelized_marshmellow_on_a_stick` / `4x crabbersdelight:pearl`。
3. **`fstwines:dangerous_party`** → 击鼓传花（hot potato）：
   - 状态字段（kubejs 用 `server.persistentData`）：`isPotatoOn`、`potatoTime`、`potatoVictim`。
   - 逻辑：已在进行且受害者非本人→清效果提示；否则开局、广播、每 tick 递归计时，1200 tick（60s）内每 200 tick 报剩余秒数，超时 `kill` 受害者并复位。
   - **迁移建议**：用一个服务端 SavedData / 单例 + 玩家 tag 维护状态，别用 persistentData 字符串。
4. **`fstwines:shoal_in_dream`** → 脚下放 `minecraft:blue_bed`（foot+head 两段，四方向找空位），没空位则给玩家一张蓝色床。

其余 3 种（david / silent_midnight / lago_de_texcoco）**无自定义效果**，buff 由注册表 `effectStack` 自动处理，无需额外代码。

---

## 7. 资源迁移清单

| 源（kubejs） | 目标（ToTheSky） |
|---|---|
| `assets/fstwines/lang/zh_cn.json` | `src/main/resources/assets/tothesky/lang/zh_cn.json`（**注意命名空间改为 tothesky，或保留 fstwines key 视注册 id 而定**） |
| `assets/fstwines/models/cocktail/*.json` | 同名模型 |
| `assets/fstwines/textures/item/*.png` | 同名贴图 |
| `data/fstwines/recipes/*.json` | 重写为 1.21.1 schema 后放 `src/main/resources/data/<ns>/recipe/cocktail/*.json`（或 datagen） |

> lang 命名空间抉择：`CocktailProperty.id` 决定 lang key。若注册 id 保持 `fstwines:xxx`，lang key 就是 `fstwines.xxx`；若改成 `tothesky:`，lang 与配方 id 同步改。二选一，保持一致。

---

## 8. 实现结果（已完成）

### 8.1 鸡尾酒注册与配方（前半程）
代码：
- `src/main/java/com/fst/tothesky/registry/ModCocktails.java` — `DeferredRegister` 挂到 `ModCocktails.COCKTAILS_REGISTRY`（命名空间 `fstwines`），注册 7 个 `CocktailProperty`。**dangerous_party 的 `kubejs:hot_potato` 非真实 mob effect，从 effectStack 剔除**（否则 `EffectStack.get()` 解析不到会抛异常）；击鼓传花改为 mob effect 驱动。
- `src/main/java/com/fst/tothesky/event/CocktailEffects.java` — 订阅 `LivingEntityUseItemEvent.Finish` 实现 4 个自定义效果。
- `src/main/java/com/fst/tothesky/client/CocktailClientEvents.java` — `ModelEvent.RegisterAdditional` 补注册 fstwines 鸡尾酒模型（kitchenkarrot 的注册器只遍历它自己的 DeferredRegister，不含 fstwines）。
- `src/main/java/com/fst/tothesky/event/CocktailCreativeTab.java` — 把 7 种 fstwines 鸡尾酒加进 kitchenkarrot 的「胡萝卜厨房-鸡尾酒」创造栏（其 `ModTabs.addCreative` 只遍历自己的 `COCKTAIL_PROPERTIES`）。
- `ToTheSky.java` — `ModCocktails.COCKTAILS.register(modEventBus)`。

配方（1.21.1 schema，放 `src/main/resources/data/fstwines/recipe/`）：7 个 `kitchenkarrot:cocktail` 配方，`cocktail_property` 引用注册表 key。
资源：`assets/fstwines/{lang,models/cocktail,textures/item}`（从 kubejs 复制）。

### 8.2 击鼓传花：重构为 **mob effect 自驱动**（支持多场并发）

> 原实现（早期）：击鼓传花用服务端静态字段 + `DelayedTasks` 每 tick 递归推进，且「全服唯一」（`isPotatoOn`）。**有两个问题**：
> 1. `DelayedTasks.onServerTick` 用 `ArrayList` 迭代器遍历任务时，危险派对每 tick `schedule` 往同一列表 `add` → `ConcurrentModificationException` 崩溃。
> 2. 全服唯一标志不支持多场同时进行。
>
> 重构后：**新注册 mob effect `tothesky:hot_potato`（击鼓传花），效果本身驱动全部逻辑**。

- `src/main/java/com/fst/tothesky/effect/HotPotatoEffect.java`（新增）— 继承 `MobEffect`：
  - 时钟**编码在效果 duration 里**（`DURATION=1200` tick / 60s）。谁持有效果谁就是当前受害者，多场并发 = 各自的 `MobEffectInstance` 独立计时，互不干扰。
  - `applyEffectTick` 每 tick 检查：每 200 tick（10s）全服广播「xxx身上的击鼓传花还剩余xx秒」；剩余 `<=1` 时对持有者结算 **200 点伤害**（`player.hurt(damageSources().generic(), HotPotatoEffect.EXPIRE_DAMAGE)`）并广播「没能及时把好运传给下一个人」。
  - **不再用 `kill()`**（2026-09-17 调整）：`LivingEntity.kill()` 走 `generic_kill` 伤害源，该类型属 `bypasses_invulnerability` 标签，而 `checkTotemDeathProtection` 首行即因该标签返回 false → 不死图腾、死亡回溯等免死手段全部失效（硬杀）。改用 200 点 `minecraft:generic`：正常走 `hurt()` 伤害管线，图腾/死亡回溯可规避；`generic` 属 `bypasses_armor`（护甲不减免，只被抗性/保护附魔削减），故 200 点对正常配装玩家仍是致命伤。
  - **关键修复**：必须用 `BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this)` 查询 `activeEffects`（返回注册表 reference holder），不能用 `Holder.direct(this)`——后者 `kind()==Direct`，而 `activeEffects` 的 key 是 REFERENCE holder（`addEffect` 存入 `ModEffects.HOT_POTATO`），NeoForge 覆写的 `Holder.Reference.equals` 只与 REFERENCE holder 相等，`Holder.direct` 永远查不到实例 → 无广播、无击杀。
  - `shouldApplyEffectTickThisTick` 返回 true，确保每 tick 都进来检查到期。
- `src/main/java/com/fst/tothesky/registry/ModEffects.java` — 注册 `HOT_POTATO`（HARMFUL，`0xFF6D37`）。
- `src/main/java/com/fst/tothesky/event/ModGameEvents.java` — `onPlayerHurt` 增加**传递逻辑**：攻击者持有 `hot_potato` 时，把效果传给受害者（继承剩余时长），受害者附赠 `glowing`/`blindness(20)`/`movement_slowdown(20,254)`，攻击者移除效果，广播传递消息（对照 kjs `EntityEvents.hurt`）。
- `src/main/java/com/fst/tothesky/util/DelayedTasks.java` — **修复 CME**：把「取出到期任务」与「执行」分开，执行阶段 `schedule` 新增的未来任务只追加 `TASKS`，不干扰本轮迭代。保留给幻翼虾仁礼花/饺子烹饪等其它场景。
- `CocktailEffects.onDangerousParty` — 不再有「另一场进行中」的全服唯一拒绝逻辑，喝下即给饮用者挂 `hot_potato`（1200 tick）+ `glowing`，广播开场。

### 8.3 资源与语言（hot_potato 的贴图/名称）
- 贴图：复制 kubejs `assets/kubejs/textures/mob_effect/hot_potato.png`（16×16 RGBA）→ `assets/tothesky/textures/mob_effect/hot_potato.png`。
- lang：zh_cn.json / en_us.json 加 `effect.tothesky.hot_potato` = 「击鼓传花」。

### 8.4 外部依赖适配（1.20.1 → 1.21.1 迁移中必须）
1. `nethersdelight:propelpearl` → `mynethersdelight:bullet_pepper`（1.21.1 的 Nether's Delight 端口改名重构，原 `propelpearl` 已不存在）。影响 `dangerous_party` 配方 + `free_nightingale` 礼物。
2. `forge:vegetables/pepper` → `c:vegetables`（1.21.1 用 common tag，`forge:vegetables/pepper` 无任何提供者）。影响 `lago_de_texcoco` 配方。

### 8.5 验证
- `gradlew build` 通过（多次，含改动后）。
- `gradlew runGameTestServer`：mods/配方/注册表全部加载成功，无 fstwines/recipe 解析错误。
- 运行时探针确认：`COCKTAILS_REGISTRY size=28` 含全部 7 个 `fstwines:*`；`total_cocktail_recipes=27` 中 7 个全是 `CocktailRecipe`；`byKey(fstwines:david)=true`。
- 打包产物确认 `assets/tothesky/textures/mob_effect/hot_potato.png` 已进 jar。
- 未做游戏内实际喝一杯的 UI 冒烟（需启动完整客户端）。

### 待办
- 进游戏实际喝一杯验证 buff + 4 个自定义效果 + 击鼓传花全流程（开场广播 / 每 10s 剩余 / 传递 / 超时 200 伤害结算 / 持不死图腾免死）。
- 见 `docs/serverTest_todo.md`（危险派对多人模式测试）。

### 复用工具
- `util/DelayedTasks.schedule(server, ticks, runnable)`（已修复 CME）仍用于幻翼虾仁礼花/饺子烹饪等。
- `SpecialFoodItems.broadcast(player, msg)` → 全服聊天思路沿用（`getPlayerList().broadcastSystemMessage`）。
