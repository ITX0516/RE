# BadukNext — 重制版围棋 AI（badukai 的 UI/交互重写）

技术栈与 badukai **完全相同**：Android（minSdk 26 / targetSdk 34） + Kotlin + Jetpack Compose + Material3，调用原生 KataGo 引擎，通过 GTP 协议通信。

- 包名：`com.badukai.next` （避免与原 badukai `com.badukai` 冲突）
- 应用名：`BadukNext`
- 仅 arm64-v8a ABI 构建

## 与原 badukai 的功能 1:1 对齐

| 功能 | 原 badukai | BadukNext（重制版） |
|---|---|---|
| 9×9 / 13×13 / 19×19 棋盘 | ✔ | ✔ |
| 执黑 / 执白 选择 | ✔ | ✔ |
| 三档 AI：Human (10b) / Superhuman (18b) / Godlike (28b) | ✔ | ✔（字段名/值与 badukai 完全一致：displayName/HUMAN/SUPERHUMAN/GODLIKE、10b.bin/18b.bin/28b.bin、description） |
| 悔棋（一次回双方两步） | ✔ | ✔ |
| Pass / 虚着 | ✔ | ✔ |
| Resign / 认输 | ✔ | ✔ |
| 两虚终局 + final_score 计分 | ✔ | ✔ |
| 最后落子标记 | ✔ | ✔（彩色圆环，不遮挡棋子） |
| 提子数显示 | ✔ | ✔（回合方高亮 + 绿色边框） |
| 新对局对话框 | ✔ | ✔（重排卡片式布局） |
| 引擎通过 GTP 走 `boardsize / clear_board / komi / play / genmove / undo / final_score` | ✔ | ✔ |
| libkatago.so + gtp_static.cfg 放 assets/，所有 .so 放 jniLibs/arm64-v8a/，模型放 assets/models/ | ✔ | ✔（路径与文件名 100% 一致，由 setup-from-badukai.sh 自动同步） |

## UI / 交互重设计要点（相比原 badukai）

1. **深色主题**：默认深色背景（Material 3 dark color scheme），原 badukai 是全浅色。棋盘在深色底上对比更强。
2. **暖色棋盘**：`#E6B878` 偏黄的榧木色（原 `#DEB887` 偏冷 burlywood），配深色网格。
3. **坐标标签**：四周绘制 A..T / 1..N 坐标标签，原 badukai 没有坐标。
4. **阴影棋子**：黑子和白子都有径向渐变 + 投影 + 高光（原只有单色圆+小圆点高光）。
5. **圆形胶囊按钮组**：悔棋 / 虚着 / 认输改为圆形大图标按钮 + 文字说明，更易点击。
6. **回合指示**：顶部有「19×19 · 执黑」等状态，另有 Captures 卡片实时显示当前轮次。
7. **对局结束横幅**：两虚终局 / 认输时顶部绿色文字横幅展示结果。
8. **新对局弹窗**：卡片式执子选择（带先行/后行副标题） + 棋盘大小三选一 chip。
9. **AI 难度弹窗**：Radio button 卡片，含说明文字。
10. **强调色**：清新绿 `#4ADE80`（原 dark gray `#2D2D2D`）。

## 构建步骤（和 badukai 端到端一致）

> 路径、文件名、引擎启动方式**完全对齐 badukai**：
> `assets/libkatago.so`、`assets/gtp_static.cfg`、`assets/models/*.bin.gz`、
> `jniLibs/arm64-v8a/*.so`，所有目录结构与 badukai 相同。

### 前置准备

- **JDK 17**（运行 Gradle 需要）
- **Android SDK**（`compileSdk 34` / `buildTools 34.x`），在项目根写一个
  `local.properties`：
  ```
  sdk.dir=/path/to/your/Android/Sdk
  ```
- **Git**（setup-from-badukai.sh 需要）

### Step 0. 生成 Gradle Wrapper

沙箱环境无法直接写入 gradle-wrapper.jar，本地首次构建前执行一次：

```bash
cd BadukNext
gradle wrapper --gradle-version 8.5
```

### Step 1. 一键把 badukai 的所有二进制拉过来

```bash
# 方式 A：你本地已经有 badukai 仓库了
./setup-from-badukai.sh /absolute/path/to/philippmerz-badukai

# 方式 B：脚本自动从 GitHub clone 到 .badukai-upstream/ 并复制
./setup-from-badukai.sh
```

执行完之后，项目目录结构就和 badukai **100% 对齐**：
- `app/src/main/assets/libkatago.so`
- `app/src/main/assets/gtp_static.cfg`
- `app/src/main/assets/models/10b.bin.gz`（以及 18b、28b 如果 upstream 里有）
- `app/src/main/jniLibs/arm64-v8a/*.so`（一整套 SDL/Qnn/SNPE 运行库）

**用户无需手动下载/拷贝任何文件。**

### Step 2. 构建 APK

```bash
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # release 包（自行配置 signing）
```

### Step 3. 安装到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动时 `KataGoEngine` 会把引擎、配置和模型从 assets 复制到
`/data/data/com.badukai.next/files/`，再通过 `/system/bin/linker64` 启动，
和 badukai 完全一致的启动流程。冷启动加载模型 1~3 秒，后续对局秒切换。

## 核心代码一览

| 文件 | 作用 |
|---|---|
| [GoBoard.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/rules/GoBoard.kt) | 纯逻辑：棋盘、走子合法性、提子、劫争、撤销、历史重放 |
| [KataGoEngine.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/engine/KataGoEngine.kt) | 引擎启动 / 关闭、GTP 命令、输出解析、环境变量设置 |
| [GameViewModel.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/game/GameViewModel.kt) | 状态机：接 UI 事件 → 改本地棋盘 → 同步引擎 → 请求 AI 落子 |
| [BoardCanvas.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/ui/BoardCanvas.kt) | Compose Canvas：棋盘、网格、星位、坐标、阴影棋子、最后落子环、点击手势 |
| [GameScreen.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/ui/GameScreen.kt) | 主界面：Header、Captures、BoardCard、横幅、控制栏圆形按钮、两个 Dialog |
| [Theme.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/ui/Theme.kt) | 调色板 + Material 3 dark/light scheme |
| [MainActivity.kt](file:///workspace/BadukNext/app/src/main/java/com/badukai/next/MainActivity.kt) | 入口：edge-to-edge、ViewModel 生命周期、引擎启停 |
| [gtp_static.cfg](file:///workspace/BadukNext/app/src/main/assets/gtp_static.cfg) | KataGo 默认配置（与 badukai 完全一致） |
| [setup-from-badukai.sh](file:///workspace/BadukNext/setup-from-badukai.sh) | 一键同步 badukai 的 assets / jniLibs 二进制，无需人工拷贝 |
