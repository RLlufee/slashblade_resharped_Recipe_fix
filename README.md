# SlashBlade Resharped Recipe Fix (重锋拔刀剑 1.21.1 JEI 合成与名刀展示修复补丁)

专门为 Minecraft 1.21.1 NeoForge 环境下的 **拔刀剑：重锋 (SlashBlade: Resharped)** 开发的 JEI 配方与名刀展示修复补丁模组。

---

## 🔍 问题分析与 1.21.1 NeoForge 现状

在《拔刀剑：重锋》1.21.1 中，状态存储机制由旧版 Forge Capability 升级为 Minecraft 原生 Data Components。尽管官方做出了部分兼容调整（如将名刀注册入创造栏、新增 `SlashBladeSubtypeInterpreter`），但仍存在以下缺陷：

1. **工作台配方扩展缺失（`SlashBladeCraftingCategoryExtension` 缺失）**：
   - 官方原版 `JEICompat` 仅注册了锻造台配方扩展（`SlashBladeSmithingRecipe`），未向工作台（Crafting）分类注册任何扩展。
   - 工作台九宫格配方中输入槽所需的**杀敌数 (KillCount)**、**耀魂值 (ProudSoul)**、**锻造数 (Refine)**、**所需附魔**以及**剑质要求（封/印/妖/断）**完全无法在 JEI 中直观展示提示。
2. **多品类刀与附属刀子类型解释器未全量覆盖**：
   - 官方 `registerItemSubtypes` 仅注册了 `SlashBladeItems.SLASHBLADE.get()` 单一物品。
   - 模组内置的木刀、竹光、银纸竹光、白鞘（均为 `ItemSlashBladeDetune`）及附属模组自定义继承 `ItemSlashBlade` 的物品未注册子类型解析器，存在物品合并与冲突隐患。
3. **动态刀运行时注入保障**：
   - 依赖创造模式物品栏事件索引，在热重载或无对应创造分组时可能丢失索引，补丁通过 `onRuntimeAvailable` 提供了动态补充注入保障与离线安全产物保底。

---

## 🛠️ 补丁核心实现原理

1. **单例模式工作台配方扩展 (`SlashBladeCraftingCategoryExtension`)**：
   - 适配 JEI 19.x 单例泛型接口 `ICraftingCategoryExtension<SlashBladeShapedRecipe>`。
   - 通过反射安全缓存解析 `SlashBladeIngredient` 内的私有 `RequestDefinition`。
   - 结合 `craftingGridHelper.createAndSetInputs` 实现 3x3 空间精确排布。
   - 挂载 `IRecipeSlotBuilder.addRichTooltipCallback` 高亮展示：
     - 🔴 **需求杀敌数**
     - 🟣 **需求荣耀之魂**
     - 🔵 **需求锻造数**
     - 🟡 **需求附魔** (基于 `Enchantment.getFullname`)
     - 🟠 **剑质要求** (BEWITCHED / BROKEN / SEALED)
2. **全量拔刀剑子类型覆盖**：
   - 遍历 `BuiltInRegistries.ITEM`，为所有继承自 `ItemSlashBlade` 的物品（除已由官方注册的基底刀外）注册子类型解析器，使用 `BladeStateAccess.of(stack).map(ISlashBladeState::getTranslationKey)` 进行精准唯一标识。
3. **离线产物兜底保底**：
   - 解决客户端启动期或后台烘焙期 `recipe.getResultItem` 无法抓取到动态注册表的问题，生成合规 `translationKey` 的 `ItemStack`，确保 Subtype 100% 匹配，按 `R` 键可稳定打开配方。

---

## 📝 修改日志 (Changelog)

### v1.0.0 (2026-09-09)
- **1.21.1 NeoForge 初始移植版发布**：
  - [移植] 适配 Minecraft 1.21.1 NeoForge 与 JEI 19.x API。
  - [重构] 移除 1.20.1 旧版 Forge Capability 与 NBT 依赖，全面适配 `BladeStateAccess` 与 Data Components 机制。
  - [新增] `SlashBladeJeiPlugin.java`：实现 JEI 19.x 规范，全量注册 `ItemSlashBlade` 子类型解析器并保障运行时注入。
  - [新增] `SlashBladeCraftingCategoryExtension.java`：为 `SlashBladeShapedRecipe` 实现工作台扩展，提供 3x3 对齐排布及详细彩色要求 Tooltip。
  - [新增] 国际化支持：提供 `zh_cn.json` 与 `en_us.json`。
