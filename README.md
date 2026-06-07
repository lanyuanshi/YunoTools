# YunoTools

一个功能丰富的 Android 工具箱应用，基于原生 Kotlin + Android SDK 开发。

## 功能列表

### 首页
- **短视频去水印** - 支持抖音/快手/小红书/B站等短视频解析去水印
- **随机一言** - 每日一句励志语录

### 图像处理
- **二维码生成** - 文字/链接转二维码
- **九宫格切图** - 朋友圈九宫格切图

### 媒体处理
- **音频分离** - 从视频中提取音频
- **改视频MD5** - 修改视频文件MD5值

### 更多工具
- **横屏时钟** - 横屏显示当前时间

## 技术栈

- **语言**: Kotlin
- **UI**: XML Layout + Material Design 3
- **网络**: Retrofit2 + OkHttp3
- **图片加载**: Glide
- **视频播放**: AndroidX Media3 (ExoPlayer)
- **二维码**: ZXing

## 项目结构

```
app/src/main/java/com/yuno/tools/
├── MainActivity.kt              # 主界面
├── YunoApp.kt                   # Application
├── data/
│   ├── ApiService.kt            # API接口
│   ├── RetrofitClient.kt        # 网络客户端
│   └── VideoParseResult.kt      # 数据模型
├── ui/
│   ├── video/                   # 视频相关
│   ├── image/                   # 图像处理
│   ├── media/                   # 媒体处理
│   └── tools/                   # 其他工具
└── util/
    ├── ClipboardUtil.kt         # 剪贴板工具
    └── DownloadUtil.kt          # 下载工具
```

## 安装

### 方法1: Android Studio
1. 克隆仓库
2. 用 Android Studio 打开项目
3. 同步 Gradle
4. 编译并运行

### 方法2: 命令行
```bash
./gradlew assembleDebug
```

## 配置API

修改 `RetrofitClient.kt` 中的 `BASE_URL` 为你自己的解析API地址。

```kotlin
private const val BASE_URL = "https://your-api-domain.com/"
```

## 许可证

MIT License