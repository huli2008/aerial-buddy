<div align="center">

<img src="assets/icon-preview.png" width="110" alt="Aerial 管家">

# Aerial 管家 · Aerial Buddy

**手机管电视：扫一下二维码就绑上，壁纸、屏保、装 App 全在手机上。**

[Aerial 电视桌面](https://github.com/pengchujin/aerial-tv) 的配套遥控 App ——
针对 **雷鸟（FFALCON）/ TCL** 电视做过实测：这类固件禁掉了 `adb install`，本 App 用电视自带的
「多屏互动」通道把 APK 推过去安装。

Android 5.0+ · 单 APK 375 KB · MIT

</div>

---

## 适用机型

| 机型 / 平台 | 状态 | 说明 |
|---|---|---|
| **雷鸟 FFALCON（MT5879 / tcl_mt5879_cn，Android 11）** | ✅ 实机验证 | 扫码绑定、换壁纸、T-Cast 装 APK 全部跑通 |
| **TCL（m7642，Android 9）** | ✅ 实机验证 | 同上；此机型 `pm install` 被固件禁用，必须走多屏互动 |
| 其他装了 Aerial 1.7+ 的 Android TV | ⚠️ 大部分可用 | 绑定与管理走 Aerial 的 8790 接口；「安装 APK」仅 TCL/雷鸟系固件可用 |

> 前提：电视上已经装好 **Aerial**（见下一节），并且手机与电视在**同一 Wi-Fi**。

## 先装 Aerial（guard 版）

本 App 只负责「管理」和「推送安装」，**不包含 Aerial 本体**。电视上要先有 Aerial：

👉 **[huli2008/aerial-guard-tcl](https://github.com/huli2008/aerial-guard-tcl)** —— 雷鸟 / TCL 专用 guard 包。
Release 里的文件是 `Aerial-1.86-guard-TCL-MT5879.apk`（3,159,168 字节，SHA-256 `bb1e8bc6…5e36`）。

装法（用本 App 完成）：

1. 手机与电视连同一 Wi-Fi；
2. 本 App「扫描」→ 点出现的电视 → 拍电视「个性化」页的二维码完成绑定；
3. 点该电视卡片的「安装 APK」→ 选刚下载的 guard 包 → 电视上按「右键 → 同意」；
4. 电视上打开 Aerial，按提示设为默认桌面。

> 安装前建议核对哈希：Windows 用 `certutil -hashfile <apk> SHA256`，macOS/Linux 用 `shasum -a 256`。

## 功能

- **自动扫描**：扫描手机所在 /24 网段，靠 `GET :8790/api/state → 404 "Not found"` 的指纹识别 Aerial 电视，不会把别的服务当成电视。
- **扫码绑定**：拍 / 选电视「控制中心 → 个性化」右侧的二维码，自动解析 `http://<IP>:8790/<token>/` 并保存。
- **点卡片进管理**：直接打开这台电视自己的配置页（壁纸、图标、布局、儿童模式都在里头）。
- **换壁纸**：原生列表列出电视上全部壁纸（航拍、我的视频轮播、每个自定义视频、每日一图、内置渐变），点一下 `POST api/wallpaper` 立刻切换。
- **应用列表 / 卸载**：点「应用」列出电视上 Aerial 认得的应用（名称 + 包名），选中即可通过多屏互动卸载。
- **安装 APK**：选手机里的 APK → 解析包名/版本 → 手机开临时 HTTP 服务 → T-Cast 推送到电视 → 电视上按「右键 → 同意」即装。
- **毛玻璃 UI**：极光 / 夜幕 / 晨雾 三套主题。

## 下载

**[⬇ aerial-buddy-v0.4.0.apk](releases/aerial-buddy-v0.4.0.apk)**（约 378 KB，已签名，自签名需允许「仍要安装」）

直链：<https://github.com/huli2008/aerial-buddy/raw/main/releases/aerial-buddy-v0.4.0.apk>

## 使用

1. 手机与电视连同一 Wi-Fi；
2. 点「扫描」：直接在列表里加出找到的电视（无需选菜单）；
3. 点这台电视的卡片 → 拍电视「个性化」页的二维码 → 自动授权并进入管理页；
4. 管理页里「添加照片 / 添加视频」可直接从手机相册上传（WebView 文件选择已实现）；
5. 回列表：点卡片进管理页；「壁纸」换壁纸；「安装 APK」推送安装。

## 应用管理

- **列表来源**：Aerial 的 `GET api/state` 里 `apps[]`（它在桌面上展示的全部应用，含被隐藏的），
  每项包含 `key`（包名）、`label`（系统名）、`name`（自定义名）。
- **卸载**：走多屏互动 `223>>UNINSTALL>>包名>>名称`，电视执行后回 `223>>UNINSTALL>>包名>>名称>>状态码`；
  App 会再拉一次 `api/state` 核对包名是否消失，据此判断成功与否。
- 系统应用 / 受固件保护的包可能卸载失败，App 会把电视返回的状态码一并提示。

## 它怎么和电视通信

| 通道 | 端口 | 用途 |
|---|---|---|
| Aerial 配置服务 | 8790 | `GET api/state`、`POST api/wallpaper`、`api/screensaver`、`api/upload`…；token 在二维码 URL 里 |
| T-Cast（多屏互动） | 6553 | `223>>INSTALL>>包名>>名称>>url>>2>>版本>>…`，电视自行下载并安装 |
| 手机侧临时供包 | 8765 | App 内置的极简 HTTP 服务，仅供电视下载 APK |

### 为什么安装必须走 T-Cast

雷鸟/TCL 固件在 PackageManager 层禁用了 pm 安装，`adb install` 会报
`INSTALL_FAILED_VERIFICATION_FAILURE` 或 `install apk has be disabled from pm by system default!`。
电视自带的「多屏互动」以 system 身份安装，所以 App 直接说这套协议。
**声明了 HOME 的 APK 会被 TCL 安装器拒绝**（Aerial 官方为此提供 guard 版），这是固件行为，无法绕过。

## 与 aerial-guard-tcl 的分工

| 仓库 | 负责 | 不负责 |
|---|---|---|
| **[aerial-guard-tcl](https://github.com/huli2008/aerial-guard-tcl)** | 分发 guard 安装包、版本与哈希、机型适配说明 | 传输与安装动作 |
| **aerial-buddy**（本仓库） | 扫描绑定、T-Cast 推送安装、壁纸/屏保/应用管理 | 提供 Aerial 安装包 |

| 场景 | 操作 |
|---|---|
| 第一次装 Aerial | 管家「安装 APK」→ 选 guard 包 |
| 升级 Aerial | 下载新 tag 的 guard 包 → 管家再推一次（同签名可覆盖） |
| 日常管理 | 卡片上：换壁纸 / 管理页 / 刷新状态 |
| 换电视 | 「扫描」→ 点新电视 → 扫一次二维码 |

## 从源码构建

需要 JDK 17 + Android build-tools + platform（不需要 Gradle）：

```powershell
powershell -ExecutionPolicy Bypass -File android\build-apk.ps1 `
  -JavaHome   'C:\jdk-17' `
  -BuildTools 'C:\sdk\build-tools\34.0.0' `
  -Platform   'C:\sdk\platforms\android-33\android.jar'
```

依赖：`libs/core-3.5.3.jar`（ZXing core，二维码解码，纯 Java）。

## 目录

```
android/app/src/main/java/com/aerial/buddy/
  MainActivity.java   设备列表、扫描、扫码绑定、换壁纸、安装流程
  WebActivity.java    内嵌电视配置页（含文件选择回调）
  AerialApi.java      8790 配置接口
  Tcast.java          多屏互动推送（6553）
  ApkServer.java      手机侧临时 HTTP 供包（8765）
  Scanner.java        局域网扫描（8790 + 指纹）
  Qr.java             ZXing 解码 + 地址归一化
  Theme.java / Backdrop.java / Store.java / Device.java
```

## 已知限制

- 扫码是「拍照 / 相册 + 解码」，不是实时取景。
- 扫描只能找到 IP：token 只存在于二维码中（Aerial 对无 token 路径一律 404），所以首次授权仍需扫一次码；之后每台电视都不用再扫。
- 安装功能仅 TCL / 雷鸟系固件可用；其他品牌请用「管理」页或电视自带安装器。
- 换路由 / 重装 Aerial 后 token 会变，需要重新扫码。

## 免责声明

非官方工具，与 Aerial 作者无关；仅调用电视自己开放的局域网接口，请在自有设备上使用。

## License

[MIT](LICENSE)
