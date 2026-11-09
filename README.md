# ⚠️ 本项目全部都是由 AI 生成

## 当前版本

- 版本：`v1.2.66`
- APK：`/storage/emulated/0/Download/YunoTools-v1.2.66.apk`
- 状态：已完成编译、签名和本地安装包导出
- 变更重点：
  - 新增独立 API 壁纸功能页，与“当前壁纸”功能完全独立。
  - API 壁纸分类改为分组卡片：通用壁纸、二次元、人物写真、4K 超清四组，子项区分电脑横屏、手机竖屏、JK 写真、动漫 4K、风景 4K。
  - 新增 gz360 影视站点应用内 WebView 页，避免跳转外部浏览器。
  - 新增一言工具页、音乐通知灵动岛布局，优化黑丝图片页与设置页样式。
  - 音乐下载目录统一到公共目录 /storage/emulated/0/Music/YunoTools，并补充下载记录入口。
> 从代码编写、UI 设计到功能实现，本项目的每一行代码均由 AI 辅助生成。人类开发者仅负责需求描述与验收。

---

# YunoTools

YunoTools 是一个基于原生 Kotlin + Android SDK 的 Android 工具箱应用，面向日常处理、媒体播放、图像处理和轻量信息查询场景。当前版本重点修复了指南针海拔/天气的可见性，并把在线音乐改为基于 wyapi.toubiec.cn / nextmusic.toubiec.cn 的网易云解析方案，搜索阶段会探测播放直链，只展示可播放结果。

## 下载

- 安装包：[release/YunoTools-v1.2.66.apk](release/YunoTools-v1.2.66.apk)
- 当前导出包：`/storage/emulated/0/Download/YunoTools-v1.2.66.apk`

## 核心功能

### 音乐播放器
- 本地音乐扫描与播放
- 在线音乐网易云解析
- wyapi.toubiec.cn / nextmusic.toubiec.cn 网易云解析接口
- 可播放直链获取
- 收藏、下载和歌词
- 系统媒体识别与播放状态同步
- 底部导航音频条样式配置

### 日常工具
- 全能计算器
- 每日一题
- 指南针海拔和温度
- Base64 编码解码
- 订阅管理
- 横屏时钟
- 手持弹幕

### 图像工具
- 图像压缩
- 二维码生成
- 九宫格切图
- 黑丝/白丝图片切换、加载、保存与分享

### 媒体工具
- 音频分离
- 视频剪切
- 改视频 MD5

### 其他能力
- 短视频去水印
- AI 聊天（暂时关闭）
- 解析历史
- 自定义头像
- 主题切换
- 图片背景配置
- 主题、音乐特效与功能参数设置

## 技术栈

- 语言：Kotlin
- UI：XML Layout + Material Design 3 + 原生动态 View
- 网络：Retrofit2 + OkHttp3
- 图片加载：Glide
- 音视频播放：AndroidX Media3 (ExoPlayer)
- 通知与媒体控制：AndroidX MediaSession + NotificationCompat
- 二维码：ZXing
- 主题：ThemeApplier + 图片背景

## 项目结构

```text
app/src/main/java/com/yuno/tools/
├── MainActivity.kt              # 主界面、底部导航和音乐播放逻辑
├── YunoApp.kt                   # Application
├── data/                        # 设置存储、数据模型和通用数据层
├── network/                     # 网络请求与解析接口
├── ui/
│   ├── image/                   # 图像处理
│   ├── media/                   # 媒体处理
│   ├── profile/                 # 个人中心、主题、设置和历史
│   └── tools/                   # 日常工具与功能页面
└── util/                        # 主题、剪贴板、下载、音乐搜索等工具类
```

## 构建

```bash
./gradlew assembleRelease
```

签名发布包由项目 keystore 生成，当前发布文件为：

```text
release/YunoTools-v1.2.66.apk
```

## 版本说明
### v1.2.66
- 新增独立 API 壁纸页，分类以分组卡片呈现，普通壁纸、二次元、JK、4K 清晰可辨。
- 六个接口分类保持独立：普通壁纸、电脑横屏、手机竖屏、JK 写真、动漫 4K、风景 4K。
- 新增 gz360 应用内网页、一言工具页与音乐通知灵动岛布局。
- 修复个人中心绑定与音乐下载目录问题，统一导出到公共 Music 目录。
- 版本号同步为 versionCode 266 / versionName 1.2.66，正式包已重新编译、签名并通过 V2/V3 校验。

## 维护约定

- 每次同步 GitHub 时同步更新本介绍。
- 版本历史只保留最新一个版本条目。
- AI 生成警示必须始终置顶。
