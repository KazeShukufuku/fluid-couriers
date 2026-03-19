# Fluid Couriers — FluidLogistics × Package Couriers 兼容模组

## 解决的问题

| 问题 | 说明 |
|------|------|
| **显示问题** | 便携式仓库管理器（Portable Stock Ticker）连接到流体打包机时，流体库存显示成「压缩储罐（Compressed Tank）」物品图标 |
| **下单路由** | 便携式/固定式仓库管理器都走 `GenericLogisticsManager` 路径，绕开了 fluidlogistics 针对流体的 Mixin；且部分环境发出的压缩储罐不是虚拟物品，无法被流体路由器识别 |
| **订单封包** | `PortableStockTickerScreen.sendIt()` 里将 UI 订单转换成 `GenericOrder` 时，`GenericKey` 编解码在服务端可能被清空，导致请求丢失 |
| **数量调整** | 流体数量默认按 mB 逐级调整，库存≥1000mB 时滚轮/点击需要过多步数 |

## 修复内容

### 客户端

1) Mixin：`PortableStockTickerScreenMixin`
- 在 `renderItemEntry()` / `renderForeground()` 中检测虚拟 `CompressedTankItem`，用 `FluidSlotRenderer` 渲染流体图标，用 `FluidSlotAmountRenderer` 显示 mB 叠加层，并在悬停时显示「流体名称 + mB 数量」。

2) Mixin：`PortableStockTickerScreenAmountStepMixin`
- 当库存为虚拟流体且可用量 ≥ 1000mB 时，滚轮 / 点击改为按整桶（1000mB）步进，避免逐 mB 点满。

3) Mixin：`PortableStockTickerScreenSendMixin`
- 重写 `sendIt()` 内的订单构建逻辑：优先使用 UI 收集到的条目并将 payload 强制编码为 `ItemKey`，防止 `GenericKey` 在网络包编解码后变成空订单，从而保留流体请求。

### 服务端 / 通用

4) Mixin：`StockCheckingItemMixin`
- 拦截 `StockCheckingItem.broadcastPackageRequest()`，若订单包含压缩储罐则统一改走 `LogisticsManager.broadcastPackageRequest()`，让 fluidlogistics 的 `LogisticsManagerMixin` 处理流体；若储罐非虚拟则先标准化为虚拟以便识别。

5) Mixin：`PortableStockTickerMixin`
- 与 (4) 相同逻辑，覆盖 `PortableStockTicker.broadcastPackageRequest()`：记录订单摘要、标准化非虚拟压缩储罐、转交 `LogisticsManager` 并回写地址到物品。

---

## 项目结构

```
fluid-couriers/
├── src/main/java/dev/fluidcouriers/
│   ├── FluidCouriers.java
│   └── mixin/
│       ├── StockCheckingItemMixin.java                (通用)
│       ├── PortableStockTickerMixin.java              (通用)
│       └── client/
│           ├── PortableStockTickerScreenMixin.java        (渲染与 tooltip)
│           ├── PortableStockTickerScreenAmountStepMixin.java (流体按桶步进)
│           └── PortableStockTickerScreenSendMixin.java       (订单封包)
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
| Java Development Kit (JDK) | **17** （必须，不能用 Java 8） |
| 网络连接 | 构建时需要从 Maven 仓库下载依赖（Forge、CurseMaven、zznty maven） |

> **注意：** 如果你的 `JAVA_HOME` 仍指向 Java 8，请先切换：
> ```bat
> set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x
> ```

### 第一步：运行构建

在本目录打开命令提示符（使用 Java 17）：

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
| Minecraft | 1.20.1 |
| Forge | 47.x |
| Create | 6.0.0+ |
| CM Package Couriers | 2.1.0+ |
| FluidLogistics | 0.6.0+ |

---

## 技术细节

### 为什么显示问题会发生？

fluidlogistics 的界面修补只覆盖固定式仓库管理员 (`StockKeeperRequestScreen`)，未覆盖 `PortableStockTickerScreen`，便携版仍按物品逻辑渲染 `CompressedTankItem`。

### 为什么下单会失败？

fluidlogistics 的 `LogisticsManagerMixin` 只在 `LogisticsManager.broadcastPackageRequest()` 上触发；便携 / 固定仓库管理器默认走 `GenericLogisticsManager.broadcastPackageRequest()`，流体订单绕开了该 Mixin。部分环境还会送出「非虚拟」压缩储罐，进一步导致识别失败。本模组在 `StockCheckingItem`、`PortableStockTicker` 两个路径上拦截并：
- 发现压缩储罐时强制走 `LogisticsManager.broadcastPackageRequest()`；
- 如有必要，将压缩储罐标准化为虚拟物品，确保流体被路由到 `IFluidPackager.processFluidRequest()`。

### 为什么要改订单封包？

`PortableStockTickerScreen.sendIt()` 将 UI 订单转换为 `GenericOrder` 时使用了 `GenericKey`，在部分环境（含额外 GenericKey 编解码器）下网络包到服务端会变成空订单。现在在客户端直接重建一个只含 `ItemKey` 的订单发送，确保流体请求不会被吃掉。

### 为什么改数量步进？

流体库存常以 B 为单位存放，默认每次 ±1mB 滚动过慢；在可用量 ≥ 1000mB 时改为按桶（1000mB）步进，更贴合实际使用。

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
