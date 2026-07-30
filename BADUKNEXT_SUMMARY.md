# BadukNext 开发总结 & 改进手册

## 项目结构

```
BadukNext/
├── app/src/main/java/com/badukai/next/
│   ├── MainActivity.kt          — 入口，ViewModel 生命周期
│   ├── BadukNextApplication.kt  — Application 初始化
│   ├── ui/
│   │   ├── GameScreen.kt        — 主界面布局（Compose，AHQ Go 风格）
│   │   ├── BoardView.kt         — 棋盘 Canvas 渲染
│   │   └── Theme.kt             — 4 套主题配色
│   ├── game/
│   │   ├── GameViewModel.kt     — 状态机（对弈+分析+设置）
│   │   ├── GoBoard.kt           — 围棋规则（气/提子/劫争）
│   │   └── SettingsStore.kt     — SharedPreferences 持久化
│   ├── engine/
│   │   ├── KataGoEngine.kt      — GTP 协议通信
│   │   └── ModelManager.kt      — 6b 权重下载管理
│   ├── analysis/
│   │   ├── GameRecorder.kt      — 棋谱记录
│   │   └── AnalysisData.kt      — 分析结果数据结构
│   └── audio/
│       └── StoneSoundPlayer.kt  — 落子音效
├── .github/workflows/build-apk.yml  — CI 构建
└── setup-from-badukai.sh        — 同步 badukai 的 .so 和模型
```

## 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| 9×9/13×13/19×19 棋盘 | ✅ | 支持 7~19 连续可调 |
| 执黑/执白 | ✅ | 新建棋局选择 |
| AI 对弈 | ✅ | KataGo 6b 权重，第一次启动下载 |
| 三档 AI | ➡️ | 原是 10b/18b/28b，现改为单 6b |
| 悔棋 | ✅ | 一次回退两步 |
| Pass / 虚着 | ✅ | |
| Resign / 认输 | ✅ | |
| 两虚终局 + final_score | ✅ | |
| 坐标标签 | ✅ | 设置开关 |
| 4 套主题 | ✅ | Warm Light / Dark / Modern / Ancient |
| 落子音效 | ✅ | 5 种可选，S1~S5 |
| 3 种落子方式 | ✅ | 单击 / 双击 / 确认按钮 |
| 设置持久化 | ✅ | SharedPreferences |
| 分析模式（自由摆子） | ✅ | |
| 落子树 | ✅ | 步进浏览棋谱 |
| **走势图（胜率/目差）** | ✅ | Canvas 折线图 + Performance 柱状图 |
| **选点表（候选着法）** | ❌ | kata-analyze 数据未传到 UI |
| 形势判断弹窗 | ⚠️ | 依赖 kata-analyze，可能不工作 |
| 胜率条 | ⚠️ | 依赖 kata-analyze |
| 让子 | ❌ | 有 bug，会覆盖已有棋子 |
| 远程 AI 分析 | ❌ | |
| 棋盘主题图片 | ❌ | |
| 6b 模型下载 | ✅ | 第一次启动从 katagotraining.org 下载 |

## 关键数据流

```
用户落子 → onBoardTap() → playMove()
  ├─ recorder.recordMove()    ← 记录到棋谱
  ├─ requestAnalysis()        ← 调 kata-analyze
  │   └─ engine.analyzePosition()
  │       └─ GTP: kata-analyze {maxVisits 300} {ownership true}
  │       └─ 解析 JSON → winrate, scoreLead, candidates, ownership
  ├─ 更新 GameState
  └─ requestAiMove()          ← 让 AI 回应
```

## APK 体积分析

| 部分 | 当前 | 优化目标 | 方法 |
|------|------|---------|------|
| 模型文件 | 0MB | 0MB | ✅ 启动后下载 |
| libkatago.so | ~5MB | ~400KB | strip + NDK release 编译 |
| jniLibs/*.so | ~70MB | ~5MB | 仅保留必需 .so，删 QNN 系列 |
| DEX | ~5MB | ~3MB | R8 压缩 |
| res/ | ~2MB | ~1MB | 压缩资源 |
| **总计** | **~96MB** | **~15MB** | |

## 已知 Bug & 待改进

### 高优先级
1. **kata-analyze 可能不工作** — 候选着法列表始终"无 AI 分析"，需检查 GTP 命令是否被支持
2. **让子重叠** — `getHandicapPoints` 对小棋盘星位计算有误，且未验证让子数与棋盘大小的兼容性
3. **设置按钮** — 现在是齿轮图标，需改为文字 "Settings" 放在右上角
4. **UI 缩放** — 元素偏小，需增大

### 中优先级
5. **jniLibs 瘦身** — 逐个测试哪些 .so 可以删
6. **走势图颜色** — 用主题色而非硬编码
7. **新建棋局弹窗** — AI 颜色选择逻辑需优化
8. **Changelog 自动更新** — CI 生成

### 低优先级
9. **远程 AI 分析** — 调用云端 KataGo
10. **棋盘主题图片** — 类似 AhQ Go 的 12 套主题
11. **SGF 导入导出**
12. **拍照识棋**

## 关键决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-07-29 | 换 6b 权重 + 第一次启动下载 | APK 省 ~175MB |
| 2026-07-29 | 用 kata-analyze 代替自建形势判断 | KataGo 精度远高于启发式 |
| 2026-07-29 | AHQ Go UI 布局 | 用户习惯一致 |
| 2026-07-30 | 设置用 SharedPreferences | 重启保留配置 |
| 2026-07-30 | 单 ABI (arm64-v8a) | 省 8.8MB，覆盖绝大多数设备 |

## 参考资源

- KataGo GTP 文档: https://github.com/lightvector/KataGo/blob/master/docs/GTP_Extensions.md
- Katago 权重: https://katagotraining.org/networks/
- 6b 权重: https://media.katagotraining.org/uploaded/networks/models/kata1/kata1-b6c96-s175395328-d26788732.txt.gz
- AhQ Go 官网: http://www.ezandroid.cn
