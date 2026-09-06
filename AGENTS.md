# 仓库指南（Repository Guidelines）

## 项目概述
ToTheSky（`tothesky`）是作者 RiaAED 为 RiaFST 服务器开发的核心模组，基于 Minecraft 1.21.1 的 NeoForge（NeoForge 21.1.248，Java 21）——「一点点混沌，很多的乐趣」。正在将旧版 KubeJS 脚本迁移为原生 NeoForge 实现，已迁移鸡尾酒、饺子、食物配方、下界合金产线/魔女因子链、售货机/扭蛋机等。许可证：保留所有权利（`TEMPLATE_LICENSE.txt` 中的 MIT 许可证仅覆盖 NeoForged MDK 模板本身，不适用于模组本体）。

**当前阶段最重要的工作：把现有 KubeJS 逻辑与注册迁移到本 mod。** 参考源位于 `D:\Minecraft\Client\.minecraft\versions\RIAFst 3\kubejs`（注意路径含空格），迁移和开发时应优先从该路径寻找参考。详见下文「当前开发重点」。

## 1.20.1 Forge 分支（分支名：`1.20.1`）
本分支是主分支（NeoForge 1.21.1）的 **Forge 1.20.1 移植版**，面向 RiaFST 4 客户端实例（`D:\curseforge\minecraft\Instances\RiaFST 4`，Forge 47.4.10，Create 6.0.8；旧参考 RIAFst 3 的 KubeJS 脚本与之几乎一致，仍可作行为参考）。与 main 的关键差异：
- **构建**：ForgeGradle 6 + Gradle 8.4 wrapper（FG 不支持 Gradle 9）、官方映射、Java 17。`mods.toml` 为静态文件（`src/main/resources/META-INF/mods.toml`），`${}` 占位符由 `processResources` 展开，无 `src/main/templates`。
- **依赖**：编译期依赖放 `libs/`（git 忽略）：从 RiaFST 4 mods 目录复制 `create-1.20.1-6.0.8.jar`、`FarmersDelight-1.20.1-1.2.4.jar`、`kitchenkarrot-1.20.1-0.6.4b.jar`，并从 Create 6 的 `META-INF/jarjar/` 解包 `ponder-forge-1.20.1-1.0.91.jar`（catnip 类 `TriState`/`AnimationTickHolder` 所在）。`gradle.properties` 内置 `systemProp.*.proxyPort=7890` 本地代理。
- **API 形态**：无数据组件/AttachmentType → 一律物品 NBT（`registry/ModNbt` 工具类与玩家 `persistentData`）；kk 1.20.1 鸡尾酒是 NBT 驱动 + 数据驱动（`CocktailItem.getCocktail/setCocktail`，效果在配方 JSON `content.effect`，模型/创造栏由 kk 自动扫 `assets/<ns>/cocktail/list.json`）——故无 ModCocktails 注册类；方块交互是单一 `use()`；`MobEffect.applyEffectTick` 返回 void；Create 6 的 `CustomUseEffectsItem.shouldTriggerUseEffects` 返回 catnip `TriState`，`AnimationTickHolder` 在 `net.createmod.catnip.animation`。
- **资源**：`data/<ns>/recipes|loot_tables`（复数）、shaped result 用 `"item"`、tag 前缀 `forge:`、Create 配方字段 `heatRequirement`/`transitionalItem`（驼峰）、流体 ingredient 为扁平 `{fluid, amount}`（Create 6 与 0.5.1 相同，已实测 6.0.8 内置配方验证）。

## 当前开发重点：KubeJS 迁移
- 参考源：`D:\Minecraft\Client\.minecraft\versions\RIAFst 3\kubejs`（路径含空格，脚本/命令中请加引号或转义）。此目录是旧版客户端 KubeJS 脚本集，其中大量内容（注册、配方、功能、资源）将被迁移进 ToTheSky，以原生 NeoForge 方式实现。
- 目录结构速览（迁移时逐项对照）：
  - `startup_scripts/` — 注册类逻辑：`registry.js`（15 KB 主注册表）、`drink_block_registry.js`、`seat_entity_registry.js` 等物品/方块/实体注册。
  - `server_scripts/` — 服务端逻辑：`recipes/`、`feature/`、`bugfix/`、`server_only/`、`general_functions.js`。
  - `client_scripts/` — 客户端逻辑：`tooltips.js`、`jeiModify.js`、`ponder.js`、`brewing_barrel_render.js` 等。
  - `assets/`、`data/` — 各模组（fstwines、kaleidoscope_cookery 等）的自定义资源与数据包。
- 约定：编写 ToTheSky 新代码时，凡 KubeJS 已有实现的，优先阅读该目录下的对应脚本作为需求与行为参考，再以 Java（注册表、事件、mixin、datagen）原生实现，而不是照搬脚本语法。

## 架构与数据流
- 单模块 NeoForge 模组。入口点：`@Mod` 类 `ToTheSky`，位于 `src/main/java/com/fst/tothesky/ToTheSky.java`。
- 元数据流：`gradle.properties` 的值 → `src/main/templates/META-INF/neoforge.mods.toml` 中的 `${...}` 占位符 → 由 `generateModMetadata` 任务展开到 `build/generated/sources/modMetadata`。始终编辑模板，绝不动生成产物。
- 数据生成流：`data` run 写入 `src/generated/resources/`，该目录已挂入 `sourceSets.main.resources`（排除 `src/generated/**/.cache`），生成内容随模组资源一起打包。
- `MODID = "tothesky"` 是唯一事实来源：它同时驱动 `@Mod` 注解、mixin 配置文件名（`tothesky.mixins.json`）、mods.toml 的 `modId` 和游戏测试命名空间。
- 事件流：构造函数接收 `IEventBus`（mod 总线）与 `ModContainer`；目前为空——没有事件订阅，也没有 `DeferredRegister`。

## 关键目录
- `src/main/java/` — 模组代码，位于 `com.fst.tothesky` 包下；mixin 必须放在 `com.fst.tothesky.mixin` 包（mixins.json 的 `package` 字段强制）。
- `src/main/resources/` — 静态资源；包含 `tothesky.mixins.json`。
- `src/main/templates/` — 含 `${...}` 占位符的元数据模板；`neoforge.mods.toml` 在这里。
- `src/generated/resources/` — 数据生成输出；执行 `runData` 之前不存在。
- `run/` — 已被 git 忽略的运行时目录，由客户端/服务端 run 生成。

## 开发命令
Gradle 9.2.1 wrapper——POSIX 用 `./gradlew`，Windows 用 `gradlew.bat`。
- `gradlew runClient` — 启动客户端
- `gradlew runServer` — 启动服务端（`--nogui`）
- `gradlew runGameTestServer` — 运行游戏测试（命名空间 `tothesky`）
- `gradlew runData` — 运行数据生成，写入 `src/generated/resources/`
- `gradlew build` — 编译 + 打包 + 空的 test 任务；CI 执行的就是这个
- `gradlew publish` — 发布到本地 `repo/` maven 目录（已配置 maven-publish）
- `gradlew --refresh-dependencies` — 刷新 Gradle 缓存；`gradlew clean` — 重置构建输出

## 代码约定与常见模式
- 模组 ID 常量：`public static final String MODID = "tothesky";` — 一律引用 `ToTheSky.MODID`，不要硬编码字符串。
- 日志：`public static final Logger LOGGER = LogUtils.getLogger();`（来自 `com.mojang.logging.LogUtils` 的 Mojang slf4j）；使用 `ToTheSky.LOGGER`。
- Java 21，官方 Mojang 映射 + Parchment（1.21.1 / 2024.11.17）参数名。
- Mixin 约定（由 `tothesky.mixins.json` 规定）：类必须位于 `com.fst.tothesky.mixin` 包且声明在 `mixins` 数组中；`defaultRequire: 1` — 每个注入器必须至少匹配 1 个目标方法，否则游戏加载失败；`overwrites.requireAnnotations: true` — 禁止匿名覆盖。
- 注册表：目前没有；新增时应使用 mod 总线上的 `DeferredRegister`（NeoForge 惯例）。
- 访问转换器：当前禁用（mods.toml 模板中为注释块——启用时取消注释即可）。
- 所有 `JavaCompile` 任务强制 UTF-8 编码。

## 重要文件
- `src/main/java/com/fst/tothesky/ToTheSky.java` — 模组入口点
- `src/main/templates/META-INF/neoforge.mods.toml` — 模组元数据模板（ID、依赖、许可证、作者）
- `src/main/resources/tothesky.mixins.json` — mixin 配置
- `build.gradle` — `net.neoforged.moddev` 2.0.144；runs（client/server/gameTestServer/data）；数据生成与元数据生成接线
- `gradle.properties` — 所有版本与模组身份属性
- `.github/workflows/build.yml` — CI（JDK 21 temurin，`./gradlew build`）

## 运行时/工具偏好
- 需要 JDK 21（toolchain 21；foojay resolver 缺 JDK 时自动下载）。IntelliJ 项目已锁定 JDK 21。
- NeoForge 21.1.248，经 `net.neoforged.moddev` 2.0.144 引入。`repositories {}` 与 `dependencies {}` 已接入 Create、Farmers Delight、KitchenKarrot、AE2、CEI 等生活/工业模组依赖（详见 `gradle.properties`）。
- `runtimeClasspath.extendsFrom localRuntime` — 可选的仅运行时模组放入 `localRuntime` 配置。
- Gradle daemon、并行构建、构建缓存、配置缓存均已启用（`gradle.properties`）；1 GB 堆内存。
- CI：Ubuntu + Temurin 21；push 和 PR 时触发。
- 仓库已有 `.git` 目录，远端 `origin` 指向 `https://github.com/RIA-AED/ToTheSky`；可正常使用 git 命令。
- IDE：已启用源码与 Javadoc 下载；默认代码风格。

## 测试与 QA
- 无单元测试：没有 `src/test` 源码，也没有 JUnit 依赖——Gradle 的 `test` 任务是空操作。
- NeoForge 游戏测试：`client`、`server`、gameTestServer 三个 run 均启用了 `tothesky` 命名空间（`neoforge.enabledGameTestNamespaces`）。
- **游戏内测试由用户手动进行**——助手只确保 `gradlew build` 编译通过。测试清单维护在 `docs/serverTest_todo.md`。
- 用 `gradlew runGameTestServer` 可运行游戏测试，但非必需（用户进游戏手测为准）。
- CI 只执行 `./gradlew build`；没有独立的测试任务或覆盖率门槛。

## Git 提交规范
- 提交标题**必须**以 `feat:`/`fix:`/`refactor:`/`docs:`/`chore:`/`style:`/`test:`/`build:` 等 [Conventional Commits](https://www.conventionalcommits.org/) 前缀开头。
  - 示例：`feat: 移植售货机与扭蛋机`、`fix: 修正模组名编码`、`docs: 更新测试待办`。
- 标题用中文描述，简洁清晰。
- 远端：`origin` → `https://github.com/RIA-AED/ToTheSky`。
