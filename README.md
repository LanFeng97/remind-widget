# 提醒 · RemindWidget

一个把「每天/每隔几天要做的几件事」摆在桌面上的 Android 提醒 App。
4×2 桌面小组件，栏目分层，到点自动浮到最上层，点一下就算做完。

吃药、健身（练胸/练肩/练背各自间隔不同天数）、喝水、喂猫——这类反复发生又有节奏的事情，是它主要服务的场景。

<p align="center">
  <img src="docs/app-home-light.png" width="30%" alt="首页" />
  <img src="docs/widget-light.png" width="30%" alt="桌面小组件" />
  <img src="docs/app-home-dark.png" width="30%" alt="深色模式" />
</p>

## 三条核心设计

**1. 栏目分层，纵向滚动**

每个栏目（吃药/健身/喝水）在小组件里是一个"层"：`● 栏目名 状态` 加底下的事件行。
层与层之间用分隔线和色点区分，整体上下滚动。纵向滚动只能用 `ListView` 集合实现——
`ScrollView` 在桌面组件里收不到拖动事件，这是 RemoteViews 的硬限制。

**2. 到点自动置顶**

排序不是按创建顺序，而是按「今天的计划时刻」：
有事件到点且没做的栏目排最前，整层高亮；全部做完的栏目沉底。
多个栏目同时到点时，按谁的时间更早排。

**3. 间隔 N 天 = 固定锚点**

选「间隔 3 天」并指定锚点日（默认今天），就固定在第 3、6、9… 天触发。
**提前做完不会把周期往前推**——健身类计划需要的是固定节奏，不是"刷完就刷新"。

举例：练胸锚点 = 今天−3 天、练肩 = 今天−2 天、练背 = 今天−1 天，间隔都是 3 天，
那么今天只有「练胸」出现在待办里，另外两个在首页会标成「练肩 明天 20:00 · 练背 11/28 20:00」。

## 功能

- 4×2 桌面小组件：栏目分层、纵向滚动、到点置顶、点击完成、切页看今日已完成
- 四种重复方式：仅一次 / 每天 / 每周指定星期 / 间隔 N 天（固定锚点）
- 精确闹钟（`setExactAndAllowWhileIdle`）+ 高优先级通知，通知上可直接点「完成」
- 开机 / 改时间 / 应用更新后自动重建全部闹钟
- 深浅色跟随系统（App 与小组件都适配）
- 应用内检查更新：读本仓库的 Release，发现新版直接下载并调用系统安装器

## 安装

到 [Releases](../../releases/latest) 下载最新 `RemindWidget-v*.apk` 安装。

### 装完必须开的四项（小米 HyperOS 尤其重要）

App 里 → 右上角齿轮 →「设置」页会实时显示前三项的开启状态：

1. **通知权限** —— 到点弹通知
2. **精确闹钟** —— 不打开的话系统会把 13:00 的提醒推迟到 13:15 之后
3. **省电策略：无限制** —— 受限时后台闹钟会被系统往后推
4. **自启动** —— 小米把开关放在私有设置里，App 读不到状态，只能自己确认

路径参考：`设置 → 应用设置 → 应用管理 → 提醒`，把通知、省电策略、自启动都放过；
再到 `设置 → 应用设置 → 特殊权限 → 闹钟和提醒`允许。最近任务里下拉卡片加锁。

### 加小组件（HyperOS 4.0 实测路径）

App 设置页有「把 4×2 小组件放到桌面」按钮，但**HyperOS 4.0 不支持**这个系统接口：
`requestPinAppWidget` 会返回 `true`，但 logcat 里没有任何 `AppWidgetServiceImpl` 记录——系统直接吞掉了，所以点了就是"没反应"。

手动添加的真实路径（在小米 17 / Android 17 / HyperOS OS4.0 上一步步走出来的）：

```
1. 桌面空白处 双指捏合（像缩小照片那样）
2. 底部点「小部件」
3. 点「全部应用」
4. 一直往下滚到最底部，点「安卓小部件」
5. 继续滚到最底（约 8 屏），找到「提醒」，双击即可放到桌面
```

两个关键坑：

- **小部件面板顶部的搜索框搜不到本应用**。HyperOS 只在小米自家组件池里搜索，
  标准 Android 组件不在这套索引里，必须从底部的「安卓小部件」分类进去找。
- **「安卓小部件」这个分类本身也在「全部应用」列表的最底部**，按拼音排序（`智能助理` 之后）。

### 诊断工具

设置页底部有「小组件诊断」，会显示：

```
系统已注册的组件提供者   119 个
本应用注册数            1 个        ← 0 表示安装包有问题，1 表示系统已识别
声明尺寸                250 × 110 dp
一键添加接口            支持        ← 注意：HyperOS 上"支持"是假象
```

如果「本应用注册数」是 1 但桌面列表里看不到，问题一定在桌面的入口/过滤，不在安装包。

## 构建

要求：JDK 17、Android SDK（compileSdk 35）、Gradle 8.11。

```bash
# local.properties 里写自己的 SDK 路径
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew assembleDebug     # 或用仓库里的 dev-tools/build.ps1（Windows）
```

> 工程路径**不能含中文**，AGP 会直接拒绝构建。

## 结构

```
app/src/main/java/com/remind/widget/
├── core/Schedule.kt          调度纯函数：哪天触发 / 下次何时 / 今天是否到点
├── core/DayPlan.kt           "今天该怎么办"：排序规则，App 与小组件共用
├── core/AlarmScheduler.kt    精确闹钟：排定 / 取消 / 全量重建 / 自测
├── core/Notifier.kt          通知
├── core/UpdateChecker.kt     GitHub Release 检查 + 下载 + 拉起安装器
├── data/                     Room：Group / Event / DoneRecord + Repository
├── widget/                   AppWidgetProvider + RemoteViewsFactory + 渲染
├── receiver/                 闹钟 / 开机 / 小组件点击
└── ui/                       Compose：首页 / 编辑 / 事件定时 / 设置
```

改动调度语义只需要看 `Schedule.kt` 和 `DayPlan.kt` 两个文件。

## 应用内更新

设置页顶部显示当前版本（如 `v1.1.1 (3)`），下面是更新卡：

- App 启动时**静默检查**一次（12 小时节流），有新版本时首页齿轮右上角亮红点
- 手动「检查更新」会拉取本仓库 latest release 的说明和包大小
- 点「下载并安装」→ 下载 APK → 自动拉起系统安装器（首次需允许「安装未知应用」）

发新版：

```powershell
# 1. 改 app/build.gradle.kts 的 versionCode / versionName
.\dev-tools\build.ps1
Copy-Item app\build\outputs\apk\debug\app-debug.apk .\RemindWidget-v1.x.x.apk
gh release create v1.x.x .\RemindWidget-v1.x.x.apk --title "v1.x.x" --notes "改了什么"
```

App 只认 `releases/latest`，不需要改代码。

## 已知平台限制

**「右滑看已完成」改成了点右上角切换。**
Android 15 起 RemoteViews 的白名单里移除了 `HorizontalScrollView`
（`InflateException: Class not allowed to be inflated android.widget.HorizontalScrollView`），
加上桌面组件本身收不到 `onTouch`，"滑动手势翻页"这条路走不通。
现在用两个 `ListView` 叠在同一容器里靠 `setViewVisibility` 切页，功能等价。

## License

MIT
