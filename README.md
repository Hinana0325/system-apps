# 自研系统应用套件（第一批 + 第二批 + 第三批）

十三个普通可安装应用 + 一个共用设计系统模块。Kotlin + Jetpack Compose + Material 3，界面为简体中文，中性灰白主题（与 PocketVM 的灰白主题同一组色值，无暖色）。无联网、无统计、无广告。

## 包含什么

| 模块 | 包名 | 做什么 |
|---|---|---|
| `core-ui` | `cn.xinxitong.coreui` | 共用灰白主题（浅色/深色/跟随系统）、顶栏、设置行、开关行、搜索框、空状态、确认对话框、信息卡片；DataStore 主题偏好（十三个应用共用同一个偏好） |
| `app-launcher` | `cn.xinxitong.launcher` | 启动器：按字母顺序排列全部可启动应用、图标/名称、搜索过滤、时钟日期、长按打开“应用信息”或卸载、点按启动 |
| `app-settings` | `cn.xinxitong.settings` | 设置：网络（Wi-Fi/蓝牙只读状态 + 跳系统设置）、显示（主题切换、亮度）、声音（媒体/铃声/闹钟音量）、存储（内部存储用量）、应用列表、关于（机型、Android 版本、内核版本、总内存，全部读自真实系统） |
| `app-files` | `cn.xinxitong.files` | 文件管理：浏览目录、按名称递归搜索、按名称/日期/大小排序、新建文件夹、重命名、删除（确认）、复制/剪切/粘贴、文件信息、用 FileProvider 打开文件 |
| `app-phone` | `cn.xinxitong.phone` | 电话：拨号盘（长按 0 输入 +）、直接拨打（需 CALL_PHONE 权限，否则退回系统拨号盘）、通话记录（读 CallLog，姓名查通讯录，点按回拨） |
| `app-contacts` | `cn.xinxitong.contacts` | 联系人：读系统联系人、按首字母分组、搜索、详情（号码/邮箱）、点号码拨打或发信息、新建联系人走系统新建界面 |
| `app-messages` | `cn.xinxitong.messages` | 信息：短信会话列表（读系统短信数据库）、会话内收发、长短信分段发送、发送/送达回执如实显示、新建信息可从联系人选号码 |
| `app-camera` | `cn.xinxitong.camera` | 相机：CameraX 预览、前后切换、闪光灯（仅设备支持时）、拍照存入 MediaStore（Pictures/XinXiTong）、上一张缩略图点按查看 |
| `app-gallery` | `cn.xinxitong.gallery` | 相册：MediaStore 图片+视频网格（Coil 加载）、按相册分组、最新在前、大图查看、视频用系统播放器打开、删除（系统确认）与分享 |
| `app-browser` | `cn.xinxitong.browser` | 浏览器：WebView 真实浏览、地址栏（网址直达/搜索走 Bing）、前进/后退/刷新、历史记录与书签（DataStore 持久化，可清除）、在外部浏览器中打开 |
| `app-clock` | `cn.xinxitong.clock` | 时钟：实时时钟（本机系统时间）、闹钟（DataStore 存储 + AlarmManager 安排 + 通知提醒，精确闹钟权限受限时退回非精确）、秒表（计次）、倒计时（结束发通知） |
| `app-calculator` | `cn.xinxitong.calculator` | 计算器：真实表达式求值（优先级/括号/百分号/一元负号正确，除零报错）、实时预览结果、计算历史（DataStore 持久化，可复用/清除） |
| `app-calendar` | `cn.xinxitong.calendar` | 日历：月视图（周一起始，事件打点）、读系统日历提供方的日程、点日程走系统查看器、新建日程走系统插入界面（不直接写日历库） |
| `app-notes` | `cn.xinxitong.notes` | 备忘录：本地 JSON 文件存储（应用专属目录）、置顶优先排序、搜索、编辑/删除（确认）、置顶/取消、纯文本分享、字数与行数统计 |

## 版本

Kotlin 2.1.0、AGP 8.7.3、Compose BOM 2024.10.01、CameraX 1.3.4、Coil 2.7.0、kotlinx-serialization-json 1.7.3、compileSdk 35、minSdk 29、targetSdk 35、Gradle 8.11.1（wrapper 已包含）。新增版本记录在 `gradle/libs.versions.toml`。

## 构建

需要 JDK 17 和 Android SDK 35（`ANDROID_HOME` 或 `local.properties` 里的 `sdk.dir`）。

```bash
cd system-apps
./gradlew :app-launcher:assembleDebug :app-settings:assembleDebug :app-files:assembleDebug :app-phone:assembleDebug :app-contacts:assembleDebug :app-messages:assembleDebug :app-camera:assembleDebug :app-gallery:assembleDebug :app-browser:assembleDebug :app-clock:assembleDebug :app-calculator:assembleDebug :app-calendar:assembleDebug :app-notes:assembleDebug
./gradlew testDebugUnitTest   # 单元测试：主题偏好映射、启动器排序、系统信息解析、文件排序/复制/搜索、号码规范化、联系人分组、短信会话聚合、闪光模式、相册排序/分组、浏览器输入解析与历史/书签去重、时钟格式化与闹钟下次触发、表达式求值、日历月视图与事件归属、备忘录排序/搜索/存储往返
```

产物在各模块的 `build/outputs/apk/debug/*-debug.apk`。

## 安装

```bash
adb install app-launcher/build/outputs/apk/debug/app-launcher-debug.apk
adb install app-settings/build/outputs/apk/debug/app-settings-debug.apk
adb install app-files/build/outputs/apk/debug/app-files-debug.apk
adb install app-phone/build/outputs/apk/debug/app-phone-debug.apk
adb install app-contacts/build/outputs/apk/debug/app-contacts-debug.apk
adb install app-messages/build/outputs/apk/debug/app-messages-debug.apk
adb install app-camera/build/outputs/apk/debug/app-camera-debug.apk
adb install app-gallery/build/outputs/apk/debug/app-gallery-debug.apk
adb install app-browser/build/outputs/apk/debug/app-browser-debug.apk
adb install app-clock/build/outputs/apk/debug/app-clock-debug.apk
adb install app-calculator/build/outputs/apk/debug/app-calculator-debug.apk
adb install app-calendar/build/outputs/apk/debug/app-calendar-debug.apk
adb install app-notes/build/outputs/apk/debug/app-notes-debug.apk
```

设为默认启动器：安装后按 Home 键，在系统弹出的选择器里选“启动器”并选“始终”；或在“设置 → 应用 → 默认应用 → 主屏幕应用”里选择。

## 诚实的限制

- 这是**普通应用**，不是 priv-app，也没有替换 `system.img`。因此：
  - “设置”**不能直接开关 Wi-Fi/蓝牙**（普通应用无此权限，相关 API 也已废弃/受限），只显示真实状态并跳转系统设置面板；亮度调节需要用户在系统里授予“修改系统设置”权限，否则只读。
  - “文件管理”浏览全部存储需要用户在系统“所有文件访问权限”里授权；未授权时只能浏览应用专属目录，界面会明确提示并给出授权入口。
  - “设置 → 应用列表”用了 `QUERY_ALL_PACKAGES` 权限（侧载/定制系统可用；在 Play 上架会受限）。
- 图标加载失败时启动器显示名称首字兜底，不会丢掉该应用。
- 所有显示的值都读自真实系统（PackageManager、AudioManager、StatFs、`/proc/version`、`/proc/meminfo` 等）；读不到的显示“不可用”，不编造数值。
- 本机（构建此代码的虚拟机）在编写时没有 Java/Android SDK，**没有实际跑通过构建**（第三批同样未构建、未跑测试）；请在有 SDK 的机器上按上面的命令构建，构建失败时以 Gradle 的报错为准。

## 第二批与第三批应用的权限与限制

| 应用 | 需要的权限（Manifest 声明 = 代码实际请求） | 限制 |
|---|---|---|
| 电话 | `CALL_PHONE`、`READ_CALL_LOG`、`READ_CONTACTS` | 未授予 `CALL_PHONE` 时拨打退回系统拨号盘；通话记录姓名查询需要 `READ_CONTACTS`，无此权限时只显示号码；无电话功能的设备上通话记录为空 |
| 联系人 | `READ_CONTACTS` | 只读；新建/编辑联系人走系统界面，本应用不直接写联系人数据库 |
| 信息 | `READ_SMS`、`SEND_SMS`、`READ_CONTACTS`（仅选联系人时） | 普通应用，未声明为默认短信应用：送达报告与写入行为取决于系统与运营商，界面如实显示回执、不宣称默认应用行为；无电话功能的设备上会话列表为空 |
| 相机 | `CAMERA` | CameraX 1.3.4；无摄像头设备（如未配置摄像头的模拟器）显示明确的不可用状态，不画假取景器；闪光灯仅设备支持时可用 |
| 相册 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（API 33+），`READ_EXTERNAL_STORAGE`（≤32） | 删除他人应用创建的媒体时，API 30+ 会弹出系统删除确认；视频用系统播放器打开 |
| 浏览器 | `INTERNET` | 是 WebView 外壳（系统 WebView 引擎），不是自研浏览器引擎；无网络时显示 WebView 自身的错误状态，不画假页面；搜索走 Bing，未宣称自研搜索引擎；历史/书签只存本机 DataStore |
| 时钟 | `SCHEDULE_EXACT_ALARM`、`USE_EXACT_ALARM`、`POST_NOTIFICATIONS`、`VIBRATE`、`RECEIVE_BOOT_COMPLETED` | 闹钟依赖精确闹钟权限：未授予时退回非精确安排（Doze 下时间可能不精确，界面会说明）；通知需通知权限；开机后重新安排已启用的闹钟；倒计时在应用进程被回收后可能中断 |
| 计算器 | 无 | 不联网；百分号按“除以 100”计算；结果四舍五入到 10 位小数后去尾零 |
| 日历 | `READ_CALENDAR`、`WRITE_CALENDAR` | 只读系统日历提供方：设备上没有日历/日程时显示空状态，不编造示例日程；新建日程走系统插入界面，本应用不直接写日历库 |
| 备忘录 | 无 | 笔记以 JSON 存在应用专属 files 目录（notes.json），不联网、不离开本机；标题取自正文首行 |
