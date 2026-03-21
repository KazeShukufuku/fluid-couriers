# Fluid Couriers — FluidLogistics × Package Couriers 兼容模组

非常感谢[一只小扳手114](https://center.mcmod.cn/859660/)提供的1.21.1版本[无人机物流](https://www.curseforge.com/minecraft/mc-mods/create-mobile-packages)兼容帮助！

## 解决的问题

| 问题 | 说明 |
|------|------|
| **显示问题** | `cmpackagecouriers` 的 **便携式仓库管理器（Portable Stock Ticker）** 打开连接到 `fluidlogistics` 流体打包机（FluidPackager）库存时，界面显示的是「压缩储罐（Compressed Tank）」物品图标，而不是流体图标 |
| **下单问题** | 界面无法正常下单流体，因为便携式管理器使用的 `GenericLogisticsManager` 代码路径不会触发 fluidlogistics 的流体处理 Mixin |

## 修复内容

### 1. Mixin：`PortableStockTickerScreenMixin`（客户端）

注入到 `PortableStockTickerScreen.renderItemEntry()` 和 `renderForeground()`：

- 检测到虚拟 `CompressedTankItem`（代表流体）时，用 **`FluidSlotRenderer`** 渲染流体图标  
- 数量栏用 **`FluidSlotAmountRenderer`** 显示 mB 单位  
- 悬停提示改为显示**流体名称 + mB 数量**

### 2. Mixin：`StockCheckingItemMixin`（服务端 / 通用）

注入到 `StockCheckingItem.broadcastPackageRequest()`：

- 当订单包含流体物品时，将请求路由到 `LogisticsManager.broadcastPackageRequest()`  
- 这样 fluidlogistics 的 `LogisticsManagerMixin` 就能正确处理流体订单，通过 `IFluidPackager.processFluidRequest()` 发给流体打包机

---

## 项目结构

```
fluid-couriers/
├── src/main/java/dev/fluidcouriers/
│   ├── FluidCouriers.java
│   └── mixin/
│       ├── StockCheckingItemMixin.java          (通用)
│       └── client/PortableStockTickerScreenMixin.java  (客户端)
├── src/main/resources/
│   ├── fluidcouriers.mixins.json
│   ├── pack.mcmeta
│   └── META-INF/mods.toml
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## 构建步骤

### 前置条件

| 工具 | 版本 |
|------|------|
| Java Development Kit (JDK) | **21** （必须，不能用 Java 8） |
| 网络连接 | 构建时需要从 Maven 仓库下载依赖（Forge、CurseMaven、zznty maven） |

> **注意：** 如果你的 `JAVA_HOME` 仍指向 Java 8，请先切换：
> ```bat
> set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x
> ```

### 第一步：运行构建

在本目录打开命令提示符（使用 Java 21）：

```bat
gradlew.bat build
```

首次运行会下载 Gradle、ForgeGradle 插件、Forge 及 Minecraft 文件（约 1-2 GB，需要较长时间）。

### 第二步：获取 JAR

构建成功后，输出 JAR 位于（文件名含版本号）：

```
build/libs/fluidcouriers-<version>.jar
```

### 第三步：安装

将 `build/libs/fluidcouriers-<version>.jar` 复制到 Minecraft 实例的 `mods/` 文件夹，
确保以下模组都存在：

- `create-xxx.jar`
- `create-more-package-couriers-xxx.jar`
- `create-fluidlogistic-xxx.jar`

---

## 运行时要求

| 模组 | 版本 |
|------|------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.213 |
| Create | 6.0.9+ |
| CM Package Couriers | 2.0.1+ |
| FluidLogistics | 0.6.0+ |

---

## 技术细节

### 为什么显示问题会发生？

fluidlogistics 的 `StockKeeperRequestScreenMixin` 只修补了 Create 的 `StockKeeperRequestScreen`（固定式仓库管理员），没有修补 cmpackagecouriers 的 `PortableStockTickerScreen`，因此便携版看到的是原始 `CompressedTankItem` 物品。

### 为什么下单问题会发生？

fluidlogistics 的 `LogisticsManagerMixin` 修补了 `LogisticsManager.broadcastPackageRequest()`，当订单包含虚拟 `CompressedTankItem` 时转发给 `IFluidPackager.processFluidRequest()`。

但便携式管理器走的是 CFA 的 `GenericLogisticsManager.broadcastPackageRequest()` 路径，完全绕过了这个修补，所以流体请求永远无法到达流体打包机。

本模组的修补：当检测到流体物品时，将请求转回 `LogisticsManager.broadcastPackageRequest()`，让 fluidlogistics 的 Mixin 生效。

---

## 许可证

本项目采用 MIT 许可证，详见 `LICENSE`。

## 第三方与商标声明

详见 `THIRD_PARTY_AND_TRADEMARKS.md`。

## AI 生成声明

详见 `AI_DISCLOSURE.md`。

## 依赖许可证边界说明

本项目仓库与发布产物（`fluidcouriers-*.jar`）仅包含 `dev.fluidcouriers` 命名空间下的实现代码，
以 MIT 许可证授权。

本项目运行依赖 Forge、Create、CM Package Couriers、FluidLogistics、Create Factory Abstractions。
这些依赖由用户自行获取并独立授权，不属于本项目 MIT 授权范围；使用时请分别遵守其各自许可证条款。
