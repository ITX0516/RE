# RE 仓库 refactor 分支 — 架构重构与代码精简日志

> **分支约束（必须遵守）**：
> - 默认分支 `master` 代码一律不动。所有重构 / 精简 / 修复只能在 **`refactor`** 副分支提交。
> - "凡是本软件涉及到的东西都不能删" —— 任何运行时依赖（jniLibs 下的 so、assets 模型、Manifest 权限、核心业务枚举与字段、ViewModel 公共 API）在未证明 100% 无用之前禁止删除；只允许做**非破坏性精简**。
> - 对运行行为有影响的修改（引擎启动链、按钮 enabled 条件、棋盘输入拦截、R8/资源压缩）**必须先在 CI 里构建通过，再注明回滚路径**。

本文件目录：

1. [代码增删总览（相对 master）](#1-代码增删总览相对-master)
2. [按提交顺序的重构明细（最早 → 最新）](#2-按提交顺序的重构明细最早--最新)
3. [本次「安全精简」范围与结果](#3-本次安全精简范围与结果)
4. [回滚记录 / 回退点](#4-回滚记录--回退点)
5. [刻意保留、暂时不删的候选点](#5-刻意保留暂时不删的候选点)
6. [体积 vs 稳定性 取舍说明](#6-体积-vs-稳定性-取舍说明)

---

## 1. 代码增删总览（相对 master）

```
分支差集：master..refactor （共 8 个提交，包括本次精简提交）
文件数：21 个 Kotlin + 5 个构建/脚本 + 1 个 workflow + 本日志
累计：+1326 / -823 行（不包括本日志文件）
```

下表是 refactor 分支相对 master 的 **净新增文件列表**（被改动/重写的老文件不计入）：

| 文件 | 类型 | 作用 | 可以删吗？ |
|---|---|---|---|
| `app/src/main/java/com/badukai/next/engine/GtpClient.kt` | **新增** | 纯 GTP 协议通信层（进程启动、命令发送、响应解析、stderr 消费、Mutex 串行化），不依赖 Android Context | ❌ 引擎通信核心，不能删 |
| `app/src/main/java/com/badukai/next/engine/EngineManager.kt` | **新增** | 引擎生命周期管理层（release 资产 + 构造命令 + 构造环境变量 + 组合 GtpClient + 对外暴露 isReady） | ❌ 启动链核心，不能删 |
| `app/src/main/java/com/badukai/next/engine/EngineBootstrap.kt` | **新增** | 文件释放层（引擎可执行 + config + 模型从 assets 释放到 filesDir，版本检查 + chmod +x） | ❌ 资产释放核心，不能删 |
| `app/src/main/java/com/badukai/next/data/game/GameEntity.kt` | **新增** | Room 实体：`games` 表结构 | ⚠️ 可删，但会丢掉「棋谱保存」能力 |
| `app/src/main/java/com/badukai/next/data/game/GameDao.kt` | **新增** | Room DAO：games 的 CRUD | ⚠️ 同上，删 Entity 一并删 |
| `app/src/main/java/com/badukai/next/data/game/GameDatabase.kt` | **新增** | Room Database 单例 + KSP 编译 | ⚠️ 同上 |
| `app/src/main/java/com/badukai/next/data/game/GameRepository.kt` | **新增** | 棋谱仓库门面（DAO 的薄封装） | ⚠️ 同上 |
| `app/src/main/java/com/badukai/next/data/settings/SettingsRepository.kt` | **新增** | DataStore 偏好（主题/音效/坐标/模式/模型/棋盘大小/玩家颜色持久化） | ⚠️ 可删，但会丢掉重启后恢复配置能力 |

## 2. 按提交顺序的重构明细（最早 → 最新）

### 提交 `67d5f2f9` — 全面架构重构
**范围最大的一次提交，净 +909 / −581。分成三层：**

#### 引擎层拆分（最关键，避免 KataGoEngine.kt 500+ 行上帝类）
| 新增/修改 | 做了什么 |
|---|---|
| 新增 `EngineBootstrap.kt` (118 行) | 从旧 KataGoEngine 里抽出「文件释放」：二进制大小对比增量释放、libkatago.so chmod +x、assets → filesDir 统一拷贝 |
| 新增 `EngineManager.kt` (134 行) | 从旧 KataGoEngine 里抽出「进程生命周期」：组合 Bootstrap + GtpClient、构造 linker64 命令行、注入 LD_LIBRARY_PATH/ADSP_LIBRARY_PATH/HOME 环境变量、维护 isReady StateFlow |
| 新增 `GtpClient.kt` (353 行) | 纯 GTP 层（可单测）：ProcessBuilder、stdout/stderr 独立协程消费、Mutex 串行命令、超时、响应 `=`/`?` 解析、genmove/play/boardsize/clear_board/komi/undo/final_score 辅助方法 |
| 重写 `KataGoEngine.kt` | 从 ~496 行上帝类精简到 63 行外观类：**唯一目的是保留嵌套 `KataGoEngine.Model` enum**（因为 Kotlin typealias 不支持嵌套类名解析），其余方法 1:1 委托 EngineManager |

#### UI 层重构（主题）
| 文件 | 做了什么 |
|---|---|
| 修改 `Theme.kt` (−64 行净) | **删全局可变单例 `BadukNextColors`**；新增 `LocalThemeColors = staticCompositionLocalOf<ThemeColors>` + `@Composable BadukNextTheme(theme)`；所有颜色统一通过 `@Composable fun themeColors(): ThemeColors` 读取。避免「全局状态导致主题切换后重组不生效」bug |
| 修改 `BoardView.kt` (~48 行差异) | 颜色读取改为 `themeColors()`；棋盘绘制逻辑本体未动 |
| 修改 `GameScreen.kt` (~40 行差异) | 最外层包 `BadukNextTheme(theme = state.currentTheme)`；`MainActivity` 也包一次 Theme 给 Activity-level composable |

#### 数据层新增（Room + DataStore）
| 文件 | 做了什么 |
|---|---|
| 新增 `GameEntity / GameDao / GameDatabase / GameRepository` | Room 棋谱库（boardSize、playerColor、komi、result、moveCount、sgf、createdAt、modelName）；KSP 注解处理器生成实现 |
| 新增 `SettingsRepository` | `UserSettings` 数据类（主题/音效/坐标/落子模式/音效索引/模型/棋盘大小/玩家颜色），Preferences DataStore 读写 |
| 修改 `BadukNextApplication.kt` (+10) | Application.onCreate 里初始化数据库 + 棋谱仓库 + 配置仓库 |
| 修改 `build.gradle.kts`（顶层 + app） | 加 KSP plugin、`androidx.room:room-runtime/room-ktx/room-compiler`、`androidx.datastore:datastore-preferences:1.0.0` |

---

### 提交 `5d75b9ee` — 修 KSP 版本（不存在的版本号）
- 改 `build.gradle.kts` KSP `1.9.20-1.0.25` → `1.9.20-1.0.14`。
- 原因：`1.9.20-1.0.25` 在 maven 仓库不存在（KSP 版本号必须严格对齐 Kotlin 版本的 `-1.0.x` 后缀）。

---

### 提交 `0b16f750` — CI 触发器扩展
- 改 `.github/workflows/build-apk.yml` 的 push 分支列表从 `[master, main]` 扩到 `[master, main, refactor]`。
- 目的：refactor 分支推送能自动跑构建。

---

### 提交 `20242ffc` — 对齐 Kotlin 2.0 + KSP（解决 NoSuchMethodError）
- 改顶层 `build.gradle.kts`：Kotlin 从 1.9.20 对齐到 2.0.0，Compose 插件从 2.0.0 对齐（与 Kotlin 2.0 匹配），KSP 从 1.9.20-1.0.14 对齐到 `2.0.0-1.0.21`。
- 原因：KSP 1.9.20 与 Compose Gradle 插件 2.0.0 在同一 build classpath 里会触发 `KspTaskJvm.getChangedFiles` 的 `NoSuchMethodError`（旧 KSP 生成的方法签名与新 Gradle API 不兼容）。统一升到 2.0.0 修复。

---

### 提交 `7d0a3322` — 尝试大幅缩 APK（⚠️ 后续被回滚）
**改动**：
1. 改 `setup-from-badukai.sh`：引入 jniLibs 白名单，只拷 `libkatago.so + gtp_static.cfg + libc++_shared.so`；models >80MB 跳过。
2. 改 `EngineBootstrap`：新增 ASSET_PREFIXES 搜索、resolveAssetPath，兼容 `assets/engine/` 新目录。
3. `debug/release` 都开 `isMinifyEnabled=true` + `isShrinkResources=true`，新增 `proguard-rules.pro` Room/Compose/DataStore/coroutines keep 规则。

**结果**：APK 从 107.7MB → 15.9MB。

**风险暴露（用户立刻反馈）**：
- 引擎启动链的 `isEngineReady` 绑定到了棋盘 `enabled`，导致 AI 起不来时点棋盘没反应。
- 用户明确要求「凡本软件涉及到的东西都不能删」——白名单裁剪 jniLibs 违反此条。
- debug 包开 R8 有反射误删风险（keep 规则不全时 Compose/ViewModel 会崩）。

**处理方式**：本提交 **保留在 git 历史中不做 rebase 删除**，但通过后续两个提交**功能上整体回滚**（见下）。

---

### 提交 `ff1a0443` — 修棋盘无反应
**用户症状**：点棋盘没反应。

**根因（两层叠加）**：
1. `GameScreen.kt` 的棋盘组件 `enabled = state.isEngineReady && !state.isThinking`。AI 没起来 = 整个棋盘 pointerInput 直接 return。ANALYSIS 模式（自由摆）本来就不依赖引擎，也被锁死。
2. `EngineBootstrap.ready` 要求 `modelFile.exists()`。但提交 `7d0a3322` 把大模型 (>80MB) 跳过了，导致 modelFile 永远不存在 → ready=false → isEngineReady=false。

**修复**：
1. 棋盘 `enabled` 改为按模式拆：PLAY = `!isThinking`（引擎起不来也允许玩家本地落子）；ANALYSIS = `true`（自由摆永真）。
2. `ReleaseResult.ready` → 改名 `binaryReady`，只要求 binary+config 存在；weight 用户可运行时选择。
3. debug 包关掉 `isMinifyEnabled/isShrinkResources`（release 才开 R8，避免调试阶段反射类被误删）。

---

### 提交 `a3d5442b` — 回滚 jniLibs 裁剪 + 解锁按钮 + 模型缺失时不再半启动
**用户症状**：AI 不落子、下方按钮（Territory/Pass/Undo/Resign）全灰。

**修复 1 — 严格遵守「都不能删」原则**：
- `setup-from-badukai.sh` 回退到原始行为：`copy_dir $SRC_APP/jniLibs/arm64-v8a` **整目录全量复制**（libSNPE.so / libQnn* / libopencv_* / libSDL2* / libtensorflowlite / libimgToSgf / libcalculator / libpython3.7m.so / libffi / libhidapi / libmain.so / 多个 libkatago* 变体 / libc++_shared.so 等 **40+ 个 so 一个不丢**）。
- 体积代价：APK 从 ~16MB 回到 ~110MB，但**消除了「运行时引擎懒加载 dlopen 失败导致 AI 静默失败」的风险**。

**修复 2 — 底部按钮不再集体死锁**：
PlayFooter 的四个按钮，旧 enabled 条件 → 新条件：
| 按钮 | 旧 enabled（灰掉的原因）| 新 enabled |
|---|---|---|
| Territory | `state.isEngineReady`（引擎不起来=灰） | `true`（任何时候都亮，引擎不 ready 时 UI 退化为显示双方提子数）|
| Undo | `isPlayerTurn && !isThinking && move≥2` | `!isThinking && move≥2`（不再卡玩家回合，引擎也能悔）|
| Pass | `isPlayerTurn && !isThinking && isEngineReady` | `isPlayerTurn && !isThinking`（玩家永远有权虚手）|
| Resign | `isPlayerTurn && !isThinking && move>0` | 不变 |

**修复 3 — 模型缺失不再假装引擎能跑**：
- `EngineManager.start()`：如果 `released.modelFile` 不存在，**直接 return false**（不再不带 `-model` 半启动 KataGo）。
- 原因：`genmove` 一定会在引擎内部抛 "NN not loaded"，用户看到的就是"AI 不落子"。不如从 ViewModel 这一层就明确走 engineErrorMessage → 引导用户「去设置选权重」。
- `ASSET_PREFIXES` 搜索优先级调整为 `["", "engine/"]`：先按 badukai 原始路径（`assets/libkatago.so` + `assets/gtp_static.cfg`）找，找不到再回退到 `assets/engine/` 自定义目录。

---

### 提交 `HEAD`（本次） — 安全精简 + 新增本日志
见下一章。

---

## 3. 本次「安全精简」范围与结果

**精简原则（硬性红线）**：
- ❌ 不删任何 jniLibs / assets / 权限 / Manifest 配置
- ❌ 不删 GameState 中任何现有字段（analysisMoves、placementMode、confirmMoveQueued、pendingTap、doubleTapActive 都在 UI/VM 里有引用路径，删了编译就挂）
- ❌ 不删 KataGoEngine、EngineManager、EngineBootstrap、GtpClient 对外 API
- ✅ 只删**确定未使用**的 import、**合并**重复的 wildcard import
- ✅ 只对**完全确定无用的公开方法**加说明注释（不删方法本身，保留 API 面）

### 3.1 精简明细

#### `app/src/main/java/com/badukai/next/ui/GameScreen.kt`
**删除未使用的 import（4 条）**：
1. `import androidx.compose.animation.*` → 替换成**具体用到的唯一一项** `import androidx.compose.animation.AnimatedVisibility`。wildcard 改为指名，减小编译 classpath 搜索面。
2. `import androidx.compose.foundation.lazy.LazyRow` — 文件里 grep "LazyRow" 0 结果 → 真没使用 → 删除。
3. `import androidx.compose.foundation.lazy.itemsIndexed` — 文件里 grep "itemsIndexed" 0 结果 → 真没使用 → 删除。
4. `import androidx.compose.ui.geometry.Offset` — 文件里没有任何 `Offset(...)` 构造或 `Offset` 类型显式使用（MoveTree/Chart/Candidates 都没用到坐标）→ 删除。

**结果**：import 从 32 条 → 29 条，wildcard 从 2 个 → 1 个（`layout.*` 是标准 wildcard，保留）。

#### `app/src/main/java/com/badukai/next/game/GoBoard.kt`
`getLegalMoves(color: StoneColor): List<Point>` 整个方法在工程内零引用（只在 GoBoard.kt 自身定义处出现一次），属于**潜在有用但目前未用**的 API：
- 策略：**不删**。改 KDoc 说明"当前 BadukNext 未使用；保留的原因是下游（候选点筛选、胜率热力图、候选点 UI）会用到；等 Analysis 候选点渲染真的接入后再决定是否真的删除。"
- 增加 `@Suppress("unused")` 避免 IDE/CI lint 警告。

---

## 4. 回滚记录 / 回退点

| # | 提交 | 回滚方式 | 何时需要回退 |
|---|---|---|---|
| 1 | `7d0a3322` APK 大缩包 | **已由后续 `a3d5442b` 功能上整体回退**。git 历史仍保留以便未来需要「release 构建时精确白名单」时参考 | 用户反馈 AI 静默失败/不落子时；或用户明确表态"宁可要功能不要大小"时 |
| 2 | debug 开 R8/shrinkResources | 已在 `ff1a0443` 回退为 debug=false/release=true | 未来 release 构建 crash 且栈里出现 Compose/Room/DataStore 类缺失时，进一步把 release 也关 R8 排查 |
| 3 | `Theme.kt` 全局单例 → CompositionLocal | **未回退，判定正确**。如果未来需要支持跨 Activity/Process 共享主题，可在 Application 加一个 currentTheme Flow 配合 CompositionLocal 双写 | Theme 切换重组丢失（回退到全局单例） |
| 4 | 引擎拆分 GtpClient+EngineManager+EngineBootstrap | **未回退，判定正确**（保持 63 行外观类兼容旧调用点）。如果未来需要极简合并回一个类，可把 `EngineBootstrap.release` 和 `EngineManager.start` 合并回 `KataGoEngine`，但会回到上帝类 | 出现严重启动链 bug 且三层定位困难时 |

---

## 5. 刻意保留、暂时不删的候选点

下表列出"理论上可以删但现在不删"的代码，**留待真正的 Analysis 功能接入、棋谱保存功能接入后再评估**。评估原则：删前必须先证明"用户可感知的功能都保留"。

| 代码点 | 现状证据 | 为什么暂不删 | 可以删的前提 |
|---|---|---|---|
| `GoBoard.getLegalMoves()` | 工程内 grep 只出现在定义行 | Analysis 候选点渲染、胜率热力图、策略显示都要这个 | Analysis 做完后确认用不到 |
| `GameState.analysisMoveIndex / analysisMoves` | GameScreen AnalysisFooter、GameViewModel.navigateToMove/analysisPrev/analysisNext/handleAiMove/playMove/startNewGame 都有读写 | 显示手顺树 + 点击跳转的功能，是 Analysis 最小可用集 | 若产品层面决定删掉 Analysis 模式 |
| `GameState.pendingTap / doubleTapActive / confirmMoveQueued` | GameViewModel 的 DOUBLE_TAP/CONFIRM 分支 + PlayFooter 的「Confirm Button」栏 + BoardView `pendingDot` | 用户能在设置里切落子模式（Single Tap / Double Tap / Confirm Button）→ 三类 UI 都依赖 | 产品决定只保留 TAP 模式 |
| Room 棋谱库（GameEntity/DAO/Database/Repository）| 代码完备，但 GameViewModel 目前**还没**在对局结束时调用 `gameRepository.save(...)` | 这是"数据层"重构目标的一部分，还没接完 → 但代码本身可用、构建无错；删了未来接棋谱库时要重写 | 永远不做棋谱保存、棋谱列表导入导出 |
| SettingsRepository（DataStore 偏好）| BadukNextApplication 已初始化，但 GameViewModel 未接 `settingsRepository.getSettings().collect {...}` 初始化 | 是"配置持久化"目标的一部分，目前主题/音效设置还存在于内存 StateFlow 里；未接持久化读取不影响现有功能运行 | 永远不做重启恢复设置 |
| `data class RecordedMove` + `GameRecorder` | recorder 在 ViewModel 每步都 recordMove()，AnalysisMoves 从 recorder 导出来 | MoveTree 显示 1..N 序号时用得到；属于 Analysis 功能的最小依赖 | 若删除 Analysis 模式 |

---

## 6. 体积 vs 稳定性 取舍说明

| 方案 | APK 体积 | 风险级别 | 结论（按用户目前要求） |
|---|---|---|---|
| **jniLibs 全量拷入（现状）** | ~112 MB | 极低（与 badukai 官方 APK 运行环境完全一致） | ✅ 采用，符合「凡本软件涉及到的东西都不能删」 |
| 白名单拷入（提交 7d0a3322） | ~16 MB | 高（KataGo 可能懒加载 SNPE/QNN/OpenCV 导致 dlopen 失败，AI 静默不落子，日志里只有 stderr 一条 `UnsatisfiedLinkError` 极难排查） | ❌ 已功能回退，**仅留作未来 release 构建时逐项验证 so 依赖后才能再开** |
| debug 开 R8 shrink | 再少 5–10 MB | 中（Compose/ViewModel/Room/DataStore 反射链 keep 不全会直接崩，debug 调试困难） | ❌ debug 关；release 保留 |
| release 开 R8 shrink（默认） | 再少 2–5 MB | 低（生产构建常规操作 + 已写 keep 规则） | ✅ 保留，但 debug 不开 |
| 去掉 models/ 里的大权重 | 再少 40–200 MB | 低（用户在设置里自选权重就好） | ⚠️ 用户未表态，暂不做；按"权重用户自己选"的原始需求其实可删，等用户明确说"权重我自己选，APK 里不要带"时再动手 |

---

_最后更新：`a3d5442b` 之后的精简提交（本日志所在提交）。所有改动位于 `refactor` 分支；`master` 保持不变。_
