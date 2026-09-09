# Social Comment Collector

全新 Android 评论采集 APP。GitHub 是唯一代码 Source of Truth。

## T0001 范围

建立可持续开发的 Kotlin / Android Views + XML 骨架：统一链接输入、首页按钮、
安全的空白 WebView、Room 数据库、ViewModel / Repository、中文与英文资源及基础测试。
当前不包含真实平台采集、URL 平台识别、登录业务、评论翻页或正式 Markdown 导出。

## 技术基线

| 项目 | 固定版本 |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.27 |
| Room | 2.6.1 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| Build Tools | 35.0.0 |
| namespace / applicationId | com.socialcommentcollector.app |

## 构建与测试

**GitHub Actions 是权威 build/test environment。** Codex 本地构建不作为权威证据，
也不要求 Codex 受限环境成功解析 Maven 依赖。有效项目版本必须已提交并推送，
验收必须对应远端具体 commit SHA 的 CI 结果。

在具备 JDK 17、SDK Platform 35 和 Build Tools 35.0.0 的环境中使用仓库内 Wrapper：

```sh
./gradlew --version
./gradlew test
./gradlew assembleDebug
# 完整构建（包括 lint 等 Gradle build 默认检查）
./gradlew build
# 仅编译设备测试，不代表已经运行
./gradlew assembleDebugAndroidTest
# 有 emulator / device 后运行
./gradlew connectedDebugAndroidTest
```

CI 在 `ticket/**` push 和面向 `main` 的 pull request 上运行。当前流程验证工具链、
unit tests、Debug APK 构建，并编译 instrumented tests。设备测试暂不在第一版 CI 运行。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。
下载入口：本仓库 **Actions → 对应 commit 的 Android CI run → Artifacts →
social-comment-collector-debug-apk**。测试报告和 Room schema 位于同一 run 的
`t0001-verification-reports` artifact；保留 14 天。

只有该 commit 的 CI GREEN 且 test、assembleDebug、APK artifact 全部确认，才可声明
T0001 Implementation Complete。这不等于 T0001 Done。

**Manual Device Verification Pending**：安装、启动、WebView 初始化、Room 运行时测试
及中英文切换仍须在真实设备或 emulator 上确认。
