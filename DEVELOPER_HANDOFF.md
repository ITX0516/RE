# BadukNext 开发交接文档

> 本文档为接手的开发者提供完整项目背景、架构、已知问题与下一步方向。
> 最后更新：2026-08-02

## 一、项目概述

**BadukNext** 是一款安卓围棋 AI 应用，基于开源项目 `philippmerz/badukai` fork 而来，深度改造了 UI 并加入完整分析/复盘功能。

- **包名**：`com.badukai.next`
- **技术栈**：Android (minSdk 26 / targetSdk 34) + Kotlin + Jetpack Compose + Material3
- **引擎**：KataGo（原生 .so，通过 GTP 协议通信）
- **模型**：6b 网络，首次启动从 katagotraining.org 下载
- **GitHub**：`https://github.com/ITX0516/RE`（master 分支）
- **CI**：GitHub Actions 自动构建 APK

## 二、文件结构与职责

```
app/src/main/java/com/badukai/next/
├── MainActivity.kt              — 入口，ViewModel 生命周期，回调接线
├── BadukNextApplication.kt      — Application 初始化（日志/崩溃捕获）
├── engine/
│   ├── KataGoEngine.kt          — ⭐ GTP 通信核心（最复杂，见下文）
│   └── ModelManager.kt          — 6b 模型下载管理
├── game/
│   ├── GameViewModel.kt         — ⭐ 状态机（对弈+分析+设置+SGF）
│   ├── GoBoard.kt               — 围棋规则（气/提子/劫争/悔棋）
│   └── SettingsStore.kt         — SharedPreferences 持久化
├── ui/
│   ├── GameScreen.kt            — 主界面（顶栏/胜率条/棋盘/底部控制/弹窗）
│   ├── BoardView.kt             — 棋盘 Canvas 渲染（棋子/领地/选点/动画）
│   ├── Theme.kt                 — 4 套主题配色
│   └── CelebrationOverlay.kt    — 终局庆祝动画（彩带/秋叶/握手）
├── analysis/
│   ├── AnalysisData.kt          — AnalyzeResult/CandidateMove/AnalysisTab 数据结构
│   └── GameRecorder.kt          — 棋谱记录（支持悔棋）
├── audio/
│   └── StoneSoundPlayer.kt      — 落子音效（5 种可选）
├── sgf/
│   └── SgfUtil.kt               — SGF 读写（导出/导入）
└── logging/
    ├── AppLogger.kt             — 日志（写文件）
    └── CrashHandler.kt          — 崩溃捕获
```

## 三、关键架构决策（接手必读）

### 1. AI 分析只用 `lz-analyze`（重要！）
**这版 KataGo 引擎的 `kata-analyze` 命令不工作**（各种参数格式都试过，不输出 JSON）。
实测确认 `lz-analyze` 完全可用，输出 Leela Zero `info` 格式：
```
info move E5 visits 4812 winrate 4492 ... info move F5 ...
```
- **winrate 单位：10000 = 100%**（4492 = 44.92%）—— 之前误除 1000 导致 411% 的 bug
- `KataGoEngine.analyzePosition()` 用流式读取（`inStreamMode` 标志），逐行入队，用 `protocol_version` 停止分析

### 2. 胜率视角统一
- `state.winrate` 存 **白方** 胜率（胜率条按 `1 - winrate` 显示黑方）
- `winrateHistory` 存 **黑方** 胜率（用于走势图/悔棋品质判断）
- 转换逻辑在 `GameViewModel.requestAnalysis()`

### 3. 每回合只分析一次
分析只在 **AI 落子后**（`handleAiMove`）触发，玩家落子后不分析（避免视角翻转导致胜率乱跳 20↔80%）。引擎先同步再分析。

### 4. 代际计数器防串（新棋局重置）
`gameGeneration` 递增，所有异步协程（analysis/genmove/handleGameEnd）在应用结果前检查代际，旧游戏的协程结果不会污染新棋局。**这是"新建棋局不重置"的修复**。

### 5. 形势判断 = 洪水填充
`computeHeuristicOwnership()` 用洪水填充法：空点区域只被黑子包围=黑地，只被白子=白地，两边碰=中立。比 Voronoi 准确。

### 6. AI 落子时限 = 加拿大制
```
time_settings 0 N 1     # 每手 N 秒
```
**注意**：`kata-set-option maxTime` 和 `time_settings N 0 0`（绝对制）在这版引擎**无效**。加拿大制实测生效。

### 7. AI 认输开关
引擎不支持 `kata-set-option allowResignation`，在 app 内拦截：`handleAiMove` 里如果 AI 返回 resign 且开关关闭，当作 pass 处理。

## 四、功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| 对弈（人 vs AI） | ✅ | 7~19 棋盘、执黑白、让子、贴目 |
| 三档 AI | ➡️ | 改为单 6b 模型，首次启动下载 |
| 悔棋 | ✅ | 回退两步 + 重新分析 |
| Pass/Resign | ✅ | |
| 两虚终局 + final_score | ✅ | 修复了 pass 引擎同步顺序 |
| 胜率条 | ✅ | 动态、600ms 动画、12dp |
| 分析模式（复盘） | ✅ | 落子树/走势图/选点表 |
| 走势图 | ✅ | 胜率/目差/表现 三图，红线跟手数，轴标注 |
| 选点表 | ✅ | 候选着法列表，选点圈标注胜率% |
| 形势判断 | ⚠️ | **当前有 bug，见"已知问题"** |
| 👁️ Eye 复盘标注 | ✅ | 实战白圈 + 胜率跌 5-10% 标粉、≥10% 标红 |
| 落子动画 | ✅ | 浮出/落下/树叶/无 |
| 落子音效 | ✅ | 5 种可选 |
| 4 套主题 | ✅ | Warm Light/Dark/Modern/Ancient |
| 设置持久化 | ✅ | SharedPreferences |
| SGF 导出/导入 | ✅ | 顶部 Save/Load，历史棋局列表 |
| 终局庆祝动画 | ✅ | 胜利彩带/失败秋叶/和棋握手 |
| AI 时间/认输设置 | ✅ | 新建棋局页可配（默认 20s） |

## 五、已知问题（接手优先处理）

### 🔴 形势功能当前有 bug
- **症状**：点"形势"后领地不显示在棋盘上，结果永远 `W+7.5`
- **诊断已加**：现在结果显示 `(B:xx W:xx K:7.5)`，下一步需用户报告数字判断：
  - `B:0 W:0` → 棋盘数据没传进 `toggleTerritoryOverlay`（可能 s.board 为空）
  - 正常数字但棋盘没显示 → BoardView drawTerritory 问题
- 已排查：`computeHeuristicOwnership` 洪水填充逻辑本身正确（人工推演过），`drawTerritory` 方块已放大到 55%、透明度提高

### 🟡 APK 体积 ~96MB（硬限制）
- **瓶颈**：`jniLibs/arm64-v8a/*.so`（QNN/SNPE/SDL/OpenCV/TFLite，~90MB）
- **不能直接删**：之前删掉导致引擎闪退（libkatago.so 运行时依赖）
- strip 调试符号无效：这些 .so 已是 release 构建无符号可剥；CI runner 的 x86 strip 也处理不了 ARM64
- **想瘦身唯一安全路径**：在真机上一手一手测试哪些 .so 可删（需要用户配合）

### 🟡 SGF 解析用正则，不处理分支
- `SgfUtil.parseSgf` 只取主线 B/W 落子，忽略变化/注释
- 自己保存的棋局无分支，够用；若要完整支持需重写为递归节点解析

### 🟢 已修复的历史问题（避免倒退）
- 胜率乱跳（每回合只分析一次 + 引擎先同步）
- 新建棋局不重置（代际计数器）
- 终局 90% 胜率却输（pass 引擎同步顺序）
- 落子动画/声音
- Eye 按钮只在分析模式

## 六、本地开发环境

- **JDK 17**，Android SDK (compileSdk 34)
- `local.properties` 写 `sdk.dir=...`
- 本地测试 KataGo：`C:\Katago\katago.exe` + 6b 模型（可用它验证 GTP 命令）
- 构建：`./gradlew assembleDebug`
- 首次需 `gradle wrapper --gradle-version 8.5`

## 七、CI 构建

- `.github/workflows/build-apk.yml`
- 流程：clone → 拉 badukai 二进制（`setup-from-badukai.sh`）→ 删模型 → assembleDebug → 上传 APK
- **模型不打包**：6b 在 app 首次启动时下载（`INTERNET` 权限已加）

## 八、下一步建议

1. **先修形势功能**：让用户跑最新诊断版，报告 `B:xx W:xx` 数字
2. **APK 瘦身**：真机逐 .so 测试（需用户配合），找出可删清单
3. **SGF 完整支持**：重写解析器处理分支/变化
4. **R8 混淆**：省几 MB DEX（有轻微风险）
5. **分享导入 SGF**：处理 `ACTION_VIEW` intent 打开 .sgf
6. **远程 AI 分析**：云端 KataGo（对标 AhQ Go 的在线分析）

## 九、参考资源

- KataGo GTP 扩展文档：https://github.com/lightvector/KataGo/blob/master/docs/GTP_Extensions.md
- 围棋点目（Benson 算法/洪水填充）：参考 x01.weiqi 项目（csdn/china_x01/p/19903950）
- 6b 模型：https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz
- 商业参考：AhQ Go（12 套棋盘主题/远程分析）、BadukAI（胜率直方图/选点/形势）
