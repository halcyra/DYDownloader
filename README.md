# DYDownloader

<p align="center">
  <img src="assets/app-icon.svg" alt="DYDownloader" width="96">
</p>

<p align="center">
  <a href="https://github.com/halcyra/DYDownloader/releases"><img src="https://img.shields.io/github/v/release/halcyra/DYDownloader" alt="Release"></a>
  <a href="app/build.gradle.kts"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0"></a>
</p>

[English](README.en.md)

一个开源免费本地的抖音/TikTok 无水印原图/图集/动图/视频/账号/合集下载工具/安卓APP。

本地解析并直接请求平台接口，支持自定义文件命名，无需第三方解析服务。

<p align="center">
  <a href="https://github.com/halcyra/DYDownloader/releases"><img src="assets/get-it-on-github.svg" alt="Get it on GitHub" width="200" height="60"></a>
</p>

## 使用

1. 下载并安装 APK。
2. 按需在「设置 → Cookies」中登录抖音/TikTok。
3. 粘贴作品、账号或合集的分享链接，解析后选择资源并下载。

Cookies 仅保存在设备本地，仅用于对应平台请求。下载内容保存在本地设备。

## 截图

<p align="center">
  <img src="assets/screenshot-home.png" width="30%" alt="首页">
  <img src="assets/screenshot-resources.jpg" width="30%" alt="资源列表与图集下载">
  <img src="assets/screenshot-settings.png" width="30%" alt="设置">
</p>

## 构建

使用 JDK 21 和 Android SDK 36：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows 使用 `gradlew.bat`。APK 输出至 `app/build/outputs/apk/debug/`。

## 许可

[GPL-3.0](LICENSE)。仅供学习与合法个人使用，请尊重版权和平台规则。
