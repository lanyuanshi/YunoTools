# ⚠️ 本项目全部都是由 AI 生成

## 当前版本

- 版本：`v1.2.47`
- APK：`/storage/emulated/0/Download/YunoTools-v1.2.47.apk`
- 状态：已完成编译、签名和本地安装包导出
- 变更重点：
  - 指南针使用定位经纬度请求网络高程与当前天气，海拔和温度都能稳定显示。
  - 音乐播放器改为基于 `wyapi.toubiec.cn / nextmusic.toubiec.cn` 的网易云解析接口。
  - 保留短视频去水印、AI 聊天、本地音乐、图像处理、媒体处理、日常工具和主题配置等核心能力。

> 从代码编写、UI 设计到功能实现，本项目的每一行代码均由 AI 辅助生成。人类开发者仅负责需求描述与验收。

---

# YunoTools

YunoTools 是一个基于原生 Kotlin + Android SDK 的 Android 工具箱应用，面向日常处理、媒体播放、图像处理和轻量信息查询场景。当前版本重点修复了指南针海拔/天气的可见性，并把在线音乐改为基于 wyapi.toubiec.cn / nextmusic.toubiec.cn 的网易云解析方案，搜索阶段会探测播放直链，只展示可播放结果。

## 下载

- 安装包：[release/YunoTools-v1.2.47.apk](release/YunoTools-v1.2.47.apk)
- 当前导出包：`/storage/emulated/0/Download/YunoTools-v1.2.47.apk`

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
- 以图搜番

### 媒体工具
- 音频分离
- 视频剪切
- 改视频 MD5

### 其他能力
- 短视频去水印
- AI 聊天
- 解析历史
- 自定义头像
- 主题切换
- 图片背景配置
- 模型与功能参数设置

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
release/YunoTools-v1.2.47.apk
```

## 版本说明

### v1.2.47

- 指南针高程和天气显示改为网络查询，提升实际可见性。
- 音乐播放器改为wyapi.toubiec.cn / nextmusic.toubiec.cn 网易云解析接口，并在播放前过滤不可播结果。
- 修复“仅向上”导航音频条样式，保持全宽横向向上柱状频谱，不再包裹播放按钮。
- 删除过期入口和说明，保持 README 与当前代码状态一致。

## 维护约定

- 每次同步 GitHub 时同步更新本介绍。
- 版本历史只保留最新一个版本条目。
- AI 生成警示必须始终置顶。
