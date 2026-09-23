[**English**](README_EN.md) | 中文

# ExParticle (Fabric 移植版)

上游：[hackermdch/ExParticle](https://github.com/hackermdch/ExParticle) v1.5.2（NeoForge 1.21.1，LGPL-3.0-only）。
本目录是把它适配到 **Fabric + Minecraft 1.21.10** 的移植工程，许可证跟随上游：**LGPL-3.0-only**，
修改部分同样以 LGPL-3.0-only 提供（`LICENSE.md` 为上游原文）。

## 构建 / 安装

```powershell
cd C:\Users\hiliang\Documents\minecraft\exparticle-fabric
.\gradlew.bat build
# 产物：build\libs\exparticle-fabric-<version>.jar
```

把产物丢进实例的 `mods/` 即可；前置：**MC 1.21.10 + Fabric Loader ≥ 0.19.5 + Fabric API + Java ≥ 21**。
需要 HTTP 代理的环境在命令行补 `-Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port>`（别写进 `gradle.properties`，
那会把个人网络配置带进仓库）。国内可选用仓库外的本地 init 脚本把 Maven Central 镜像插到最前。

## 许可与致谢

- **上游**：[hackermdch/ExParticle](https://github.com/hackermdch/ExParticle) v1.5.2（NeoForge 1.21.1），
  许可证 **LGPL-3.0-only**。
- **本仓库**是它的 **Fabric 1.21.10 移植（非官方）**，属修改版，因此**同样以 LGPL-3.0-only 发布**
  （`LICENSE.md` 为上游原文；修改说明见本 README 全文）。
- 项目图标为本移植新绘制，未使用上游素材。
- 上架素材（Modrinth 文案、分类、依赖、合规注意）见 [`MODRINTH.md`](MODRINTH.md)。

## 移植的关键决策

1. **映射用 Mojang 官方映射**（`loom.officialMojangMappings()`）。
   上游 11.4k 行里对原版的引用基本都是 Mojmap 名字，用 Yarn 会引入成千处无意义改名；
   换映射后只需处理**真正变了的 API**（见下）。
2. **NeoForge 胶水换成兼容层**，而不是重写 21 个 payload / 20 个命令类：
   - `compat/PayloadRegistrar` ← `net.neoforged.neoforge.network.registration.PayloadRegistrar`
     （`playBidirectional` → `PayloadTypeRegistry.playS2C()/playC2S()` + `Client/ServerPlayNetworking.registerGlobalReceiver`）
   - `compat/IPayloadContext` ← `IPayloadContext`（只用到 `enqueueWork` → 对应线程执行）
   - `compat/PacketDistributor` ← `PacketDistributor.sendToPlayersInDimension`
   这样上游文件只改了 import（机械替换 43 个文件），逻辑零改动。
3. **access transformer → access widener**：`src/main/resources/exparticle.accesswidener`
   迁移了上游 AT 的全部条目，并注意 1.21.10 的差异：
   `Particle.x/y/z/xd/yd/zd/age`、**`SingleQuadParticle.rCol/gCol/bCol/alpha`**（1.21.1 时这些在 `Particle` 上）、
   `NativeImage.pixels`（现在是 `long` 指针）、`ParticleEngine.makeParticle`。
   Loom 侧必须在 `build.gradle` 的 `loom { accessWidenerPath = ... }` 里指路径，dev 编译期才生效。
4. **逐粒子自定义 tick 的挂载点变了**：1.21.1 是 `ParticleEngine.tickParticle(Particle)`，
   1.21.10 搬到了 `ParticleGroup.tickParticle(Particle)` → `mixin/ParticleGroupMixin`。
5. **接口注入换成 mixin 实现**：Fabric 没有 NeoForge 的 interface injection，
   由 `ParticleMixin implements IParticle` 提供；颜色/alpha 的读写因字段搬家下移到
   `SingleQuadParticleMixin`（`IParticle` 新增 6 个颜色/alpha 方法，基类给默认空实现）。

## 功能状态（按命令族，逐条实机验证）

| 命令族 | 状态 | 验证方式 |
|---|---|---|
| `normal` | ✅ | 100/1500 颗，颜色覆盖生效（粒子数用 jcmd 直方图核对） |
| `conditional` | ✅ | 体素外壳 `abs(x)==1\|abs(y)==1\|abs(z)==1` + 径向扩散 |
| `parameter` / `polar-parameter` | ✅ | `dis=1.5;s1=t;s2=0` 成环 |
| `tick-parameter` / `tick-polar-parameter` | ✅ | 成环 + `vy=0.006` 三连拍可见逐帧上升（逐 tick 表达式运动） |
| `rgba-parameter` 系列 | ✅ | 颜色/alpha 由表达式给出 |
| `custom-normal` / `custom-*` 系列 | ✅ | `attr` 表达式给出 size/color/age（实测 300 颗） |
| `image` / `image-matrix` | ✅ | 16×16 PNG → 256 颗（`image-matrix` 共用同一路径 + 矩阵参数） |
| `video` / `video-matrix` | ✅ | 见下方「视频接入」，实测 32×18@10fps 视频解出 29/30 帧、按 scaling 生成 144 颗 |
| 表达式引擎（Lexer→Parser→Optimize→**ASM CodeGen→defineHiddenClass**） | ✅ | 上述所有形状/运动都由它驱动 |
| 逐粒子尺寸/颜色/亮度覆盖 | ✅ | 由 `ParticleGroup.tickParticle` 重定向 + `SingleQuadParticle` 字段注入实现 |
| `group remove` / `group change` | ✅ | 带组名生成 100 颗 → `group remove` 后归零 |
| `clear-particle` / `clear-cache` | ✅ | 120 颗 → `clear-particle` 后归零；`clear-cache` 无报错 |
| `global-variable define/undefine/peek` | ✅ | `peek` 经 `QueryPayload` 回执，聊天栏显示 `3.5` |
| `user-function define/undefine/list` | ✅ | `list` 显示 `f(a,b) => (a+b)` |
| `text`（上游无 text-matrix） | ✅ | **GPU 离屏光栅化**（见下方「text 实现方式」）：`"AB"` @scale3 → 342 颗粒子（与光栅化像素数一致，jcmd 直方图核对） |
| `ParallelParticleUpdate` 配置 | ⚠️ 已实现未实测 | `ParticleGroupMixin` 在 `tickParticles` HEAD 做并行 + removeIf；默认配置为 false，故未触发 |
| `maxParticleCount` / `maxParticleTickMillis` | ➖ 无对应物 | 1.21.10 已删除粒子计数历史队列（`countParticles()` 改为实时求和），`MAX_PARTICLES` 成了没人读的私有常量；上游自身也没用到 `maxParticleTickMillis` |

### 视频接入（video）

与上游同一套用户流程：把 JavaCV 的 jar 丢进 `gamedir/javacv/` 即可（上游 README 的 JavaCV 说明照用）。
本机实测的一组（Maven Central，共 ~28 MB）：

```
javacv-1.5.11.jar
javacpp-1.5.11.jar
javacpp-1.5.11-windows-x86_64.jar
ffmpeg-7.1-1.5.11.jar
ffmpeg-7.1-1.5.11-windows-x86_64.jar
```

装载机制（与上游不同，但对外行为一致）：上游在 NeoForge 上用 `sun.misc.Unsafe` + 反射改
`ModuleClassLoader` 把 jar 挂进模块层；Fabric 没有模块层，这里用
`JavaCvSupport`：child-first `URLClassLoader`（搜索路径 = `javacv/*.jar` + 本 mod jar）
定义只引用 JavaCV 的桥接类 `VideoDecoder`。

**踩过的坑（改这里前必读）**：JavaCPP 在子加载器环境下会找不到平台 jar 里的原生库，
回退 `System.loadLibrary("jniavutil")` → `UnsatisfiedLinkError`（同一批 jar 用命令行独立跑却没问题）。
修法是三条一起做，且**必须在任何 JavaCPP 类被初始化之前**：
1. 自己把 `*-<platform>.jar` 里的 DLL 抽到 `gamedir/javacv/natives/`；
2. 把该目录塞进 `java.library.path`；
3. 设 `org.bytedeco.javacpp.pathsFirst=true`
   （JavaCPP 的 `Loader` 会在**类初始化时**缓存这个值，晚设无效——这是最坑的一步）。

另有一处与上游不同的必要修正：抓帧用 `Java2DFrameConverter.copy(frame, image)`（自己建
`TYPE_3BYTE_BGR`），因为 `convert(frame)` 在本机 JavaCV 1.5.11 下会因帧的
`imageDepth/imageChannels`（实测恒为 depth=8、channels=6，与编解码/像素格式无关）推断不出
BufferedImage 类型而 NPE。**换 FFmpeg 6.1.1 也一样**（2026-09-23 补测），所以这不是 FFmpeg 版本问题，
而是 JavaCV 在该环境下对帧通道数的误报；上游用的正是 `convert()`，因此**上游的 video 在这台机器上根本跑不出粒子**
（详见「上游差分测试」一节）。
通道顺序已用纯红/纯蓝测试视频验证（红 → #FD0000，蓝 → #0000FE）。

### 上游差分测试（2026-09-23 晚：NeoForge 1.21.1 vs 本 port）

参照面：PCL 装的 **NeoForge 21.1.251 / MC 1.21.1** + Modrinth 的 **上游 `exparticle-1.5.2.jar`**（306,903 B）。
环境与驱动方式（脚本都在 `_scratch-m3-39\neoforge\`）：

- 该实例目录在 `C:\Program Files` 下、ACL 只给 Users 读权限，所以用 `launch-neoforge.ps1` 自己解析 version json、
  把 `--gameDir` 指到可写的 `_scratch-m3-39\neoforge\gamedir`（**注意：必须带 profile 自己的 `--launchTarget` 等
  game args**，否则 NeoForge 会在 `ImmediateWindowHandler` 里 `List.of(...).contains(null)` 直接 NPE；
  另外 NeoForge 21.1 在 **JDK 25 上也会 NPE**，要用 Java 21 运行时）。
- 测试世界用 vanilla 1.21.1 服务端生成超平坦后复制进 `saves\TestFlat`；上游没有写文件通道，所以用**世界数据包**
  `datapacks\exptest`（`load` 里建记分板、`tick` 里按 tick 数逐条发 `/particlex`，末尾清零循环）驱动，
  再用 `sample.ps1`（每秒 `jcmd GC.class_histogram` 采 `EndRodParticle` 计数）与日志里的 `TEST:` 标记对齐。

结果（同一条命令、同一坐标，单位=粒子数；port 侧用「clear 后取绝对计数」）：

| 用例 | 上游 1.21.1 | 本 port 1.21.10 | |
|---|---|---|---|
| `normal … 500` | 500 | 500 | ✅ |
| `text "AB" 3` | 342 | 342 | ✅ |
| `text "I" 4` | 176 | 176 | ✅ |
| `text "I" 4 italic` | 166 | 166 | ✅ |
| `text "AB" 3 red` | 342 | 342 | ✅ |
| `text "中A文" 3`（跨图集） | 396 | 396 | ✅ |
| `image test.png` | 256 | 256 | ✅ |
| `image-matrix "E4"` | 256 | 256 | ✅ |
| `tick-parameter …` | 63 | 63 | ✅ |
| `custom-image test.png` | 256 | 256 | ✅ |
| `custom-conditional` | 98 | 98 | ✅ |
| `rgba-tick-polar-parameter` | 63 | 63 | ✅ |
| `custom-parameter polar`（约 3 秒的短命爆发） | 63 | 63 | ✅ |
| `normal … 5000`（两侧 `ParallelParticleUpdate=true`） | 5000 | 5000 | ✅ |
| `text "❤" 3`（bitmap 特殊字形） | 306 | 306 | ✅ |
| `text "☃" 3`（同上） | 234 | 234 | ✅ |
| `video test.mp4 0.5` / `video-matrix "E4"` | —（上游自身跑不出结果，见下） | 144 / 144 | ⚠️ 见下 |
| **`text "I" 4 bold`** | **288** | **176** | ⚠️ 版本语义差异 |
| **`text "AB" 3 bold`** | **549** | **342** | ⚠️ 同上 |

**video 为什么没有上游对照（两层原因，都已实测定性）**：
1. **装载层**：上游把 `gamedir/javacv` 里每个 jar 当成模块解析（`ModuleFinder` + `resolveAndBind`），
   `javacv.jar` 的 `module-info` 要求 11 个 bytedeco 模块，还会要 `javafx.graphics`（Mojang 运行时里没有）。
   只放 5 个核心 jar 时报 `java.lang.module.FindException: Module org.bytedeco.librealsense not found`，
   video 整体禁用并提示「JavaCV不可用，如果想使用video命令，请先安装JavaCV」。
   我按 `requires` 补齐到 **33 个 jar（113 MB：11 模块的 base+windows + JavaFX）** 后，装载确实通过了
   （日志里不再有 `Failed to load JavaCV`）。
2. **解码层**：装载成功后上游仍然**一颗粒子都出不来**——因为它用
   `Java2DFrameConverter.convert(frame)`，而在这台机器上（JavaCV 1.5.11）该方法**必 NPE**：
   `Cannot invoke "java.awt.color.ColorSpace.getNumComponents()" because "colorSpace" is null`。
   我用**同一批 jar 在游戏外**复现过：h264/yuv420p、ffv1/bgr0、ffv1/gray 三种视频都是
   `frame: depth=8 channels=6` → `convert()` NPE；换成同期的 FFmpeg 6.1.1 也一样（所以不是 FFmpeg 版本，
   是 JavaCV 在这个环境下对帧通道数的恒定误报）。同一帧用本 port 的
   `Java2DFrameConverter.copy(frame, new TYPE_3BYTE_BGR(...))` 则正常。

  因此：**video 的上游基线在这台机器上不存在**（上游自身的缺陷），本 port 的 `copy()` 修正正是让 video
  可用所需的那一步（实测 `video test.mp4 0.5` → 144 颗、`video-matrix "E4"` → 144 颗）。
  这也解释了上游那条"异常会进聊天栏"的兜底为什么会静默：它的 `ClientMessageUtil` 只覆写了
  `PrintStream.println(Object)`，而 `printStackTrace` 走的是 `println(String)` 重载，所以帧解码线程抛的
  异常既没进聊天栏、也不在我的 stdout 抓取里留下痕迹。

顺带清掉一个历史残留：早先移植阶段留下的 `command.text.unavailable`（"本 Fabric 移植版暂未启用文本粒子"）
在上游 lang 里并不存在，代码也没引用，text 现在也早已实现 —— 已从 `zh_cn`/`en_us` 删除，恢复 lang 与上游同键集。

#### 静态差分：整个代码库逐文件比对（2026-09-23 夜）

把上游 `main` 源码整棵树（92 个 `.java`）拉下来，与 port 的 102 个文件逐文件比对
（脚本 `_scratch-m3-39\neoforge\static-diff.ps1`；比对前剥掉注释、`import`、`package`、空行，
避免中文注释与 import 替换造成噪声；看具体差异用 `show-diff.ps1 -Files '<相对路径>'`）：

- **67 个文件在归一化后逐字相同** —— 印证「只改 import、逻辑零改动」的移植策略。
- **24 个文件有差异，逐个核对后全部可解释**，归成六类：
  ① NeoForge→Fabric 胶水（`ExParticle`/`ExParticleClient` 入口、`CustomArgumentTypes` 的 `DeferredRegister`→
  `ArgumentTypeRegistry`、`Networking`、`ParticleExCommand` 的客户端命令树、mixin 替代事件钩子）；
  ② NeoForge 接口注入 → 显式 `((IParticle) particle).setXxx(...)`（`ParticleUtil`/`CustomParticleBuilder`/
  `GroupChangePayload`/`CustomVideo*Payload`/`ExParticleApi`）；
  ③ 1.21.1→1.21.10 API 改名（`ComponentArgument.getComponent`→`getRawComponent`、
  `NativeImage.getPixelRGBA`+`FastColor.ABGR32`→`getPixel`+`ARGB`，语义等价，已在 1.21.10 字节码里核对）；
  ④ 路径解析（`new File("./particleImages")` → `FabricLoader.getGameDir()`，PCL 的 CWD 是 `.minecraft` 根且不可写）；
  ⑤ JavaCV 装载（上游 `Unsafe`+`ModuleClassLoader` 改模块层 → 本 port 的 child-first `URLClassLoader`）；
  ⑥ text 光栅化整体重写（`TextUtil`，见上文「text 实现方式」）与 `ClientMessageUtil` 的聊天栏修正。
- **11 个 port 独有文件**：`compat/*`（3）、mixin（`ParticleEngineMixin`/`ParticleGroupMixin`/`ParticleMixin`/
  `SimpleAnimatedParticleMixin`/`SingleQuadParticleMixin`）、写文件通道 `NbauroraLiveChannel`、`QueryPayload`、
  `ChildFirstClassLoader`、`ExParticleLog`、`GlTextRasterizer`、`JavaCvSupport`、`VideoDecoder`。
- **抓到并修掉 1 处真实漂移**：`ExParticleApi.genText` 结尾少了上游的 `filter(Objects::nonNull)`
  （其余 4 个同名方法都有）→ 已补齐为 `return ps.stream().filter(Objects::nonNull).toList();`。

#### 收尾：把"没测到的面"逐个关掉（2026-09-23 夜）

1. **`ExParticleApi` 动态自检**（`-Dexparticle.selftest.api=true`，新增 `util/ApiSelfTest`）：API 是给别的
   mod 用的，命令不走它，所以单独跑一遍并与命令侧实测数量交叉核对 —— 全部通过：

   | API | 返回 | 与命令侧交叉核对 |
   |---|---|---|
   | `createParticle` / `bind` | 非 null / ok | —— |
   | `genNormal(count=100)` | 100（nulls=0） | `normal … 100` ✓ |
   | `genCond(abs(x)==1\|…)` | **98**（nulls=0） | `custom-conditional` = 98 ✓ |
   | `genParameter(polar=false/true)` | 63 / 63（nulls=0） | `tick-parameter`/`custom-parameter polar` = 63 ✓ |
   | `genImage(test.png)` | **256**（nulls=0） | `image test.png` = 256 ✓ |
   | `genText("AB",3)` | **342**（nulls=0） | `text "AB" 3` = 342 ✓ |

   （顺带查到：你本机 `nbmachina` 对 `ExParticleApi` 是 0 处引用，所以这条 API 目前没有外部使用者。）
2. **R8 图集分支**（`-Dexparticle.text.forceRed=true`）：原版 1.21.10 的字体图集是 RGBA8，`texel.r` 那条分支
   平时不可达。强制走一次后行为符合预期 —— 覆盖率改取红通道，于是对「RGB=白、A=覆盖率」的 RGBA8 图集整片变白
   （`painted=864` vs 正常 342）。**语义正确、不崩**，只是对 RGBA8 图集不适用（这正是它只在 R8 图集下启用的原因）。
3. **obfuscated 字形**：`{"text":"AB","obfuscated":true}` 正常出粒子（某次实测 208 颗），
   但 MC 每帧随机换字形 → **计数天然不可复现**，只能算"不崩、有输出"。
4. **sprite / object 组件字形**（1.21.10 新增能力）：`{"object":"atlas","atlas":"minecraft:items","sprite":"minecraft:item/diamond","fallback":"?"}`
   → **198 颗粒子 ✓**，说明文本路径对「非 `BakedSheetGlyph` 之外的图集字形」也已覆盖（跨图集分批逻辑同时被用到）。
   注意 1.21.1 没有 object 组件，所以这条**只有 port 侧数据、没有上游基线**；另外 sprite 名要写对
   （`minecraft:item/diamond` ✓；`minecraft:block/stone` 在本机查不到 → 0 颗）。

**最终未覆盖清单**（其余全部已关闭）：多人/服务端（用户要求跳过）、video 的上游数值基线（上游自身在这台机器上
跑不出结果，见上）、`ExParticleApi` 与上游的*动态*对比（只有静态逐字对齐 + 本机自检；上游侧要测得另写 NeoForge 测试 mod）。

**粗体差异的解释（已定案，不是移植缺陷）**：MC 1.21.1 的粗体是**「按 boldOffset 再画一遍」**（两遍叠加，
所以 `I`@4 从 176 涨到 288），而 MC **1.21.10 改成了单遍加厚**：`BakedSheetGlyph.render` 里
`extraThickness(bold)=0.1` + `GlyphInfo.getAdvance(bold)`，没有第二遍（1.21.10 字节码实测）。
本 port 照抄的是**它自己那个版本**的公式，所以 bold 计数等于常规计数（加厚只有 0.1 字体单位 ≈ 0.4px@scale4，
不足以多出整像素）✓。换言之：**「上游代码跑在 1.21.10 上」就会是这个结果**，差分里这条差异属预期。

测量口径的两个坑（都踩过）：① 上游没有写文件通道，命令只能从**服务端**（数据包函数）发，坐标要用绝对坐标；
② `custom-parameter` 这类命令是**短命爆发**（实测 63 → 34 → 0，约 3 秒就没了），采样窗口太晚就会误判成 0。

### text 实现方式（GPU 离屏光栅化，2026-09-23 定稿）

默认路径是**自管 GL 的离屏光栅化**（`util/GlTextRasterizer` + `util/TextUtil`）：自建
program / VAO / VBO / FBO，用**真正的字形图集**在 GPU 上采样出图，`glReadPixels` 回读后按行翻转交给粒子生成。
CPU 软件光栅化（`TextUtil.toImage`）保留为回退路径，两条路输出语义一致。

要点（改这块前必读）：

1. **不借 MC 的 `RenderPass` API**：三条时机全试过，只有第一次能画出内容（完整证据见下方三节历史记录）。
   自管 GL 路径里顶点 shader 直接把**像素坐标**映射到 NDC（y 向下），所以「投影/视口对不对齐」这个问题不存在。
2. **状态纪律**：所有会改动的 GL 状态一律经 `GlStateManager` 存取（它的状态缓存跟着同步），并在 `finally`
   里按原值还原；裸 GL 只用于无状态查询与回读。`GlStateManager` 的方法带 `assertOnRenderThread`，
   非渲染线程调用会直接抛异常 → 调用方自动回退 CPU。
3. **图集格式两种都要管**：R8 图集取 `texel.r` 当覆盖率，RGBA8 直接用 `texel`；图集回读用
   `glGetTexImage`（`GL_RED` 或 `GL_RGBA`），**不要**用 MC 的 `copyTextureToBuffer`（帧级 pass 里会被拒）。
4. **一段文本可能跨多张图集页**（拉丁与 CJK 分属不同 provider），必须按图集分批绘制。
5. **不再需要 `GuiRenderer.prepare` 驱动**（那个 mixin 已删除）：`text` 命令在主线程直接出图即可。
6. **字形四边形照抄原版 `BakedSheetGlyph.render` 的公式**：斜体按 `(1 - 0.25*up/down)` 错切、粗体四周各加厚
   `0.1`（`extraThickness`）、步进用 `GlyphInfo.getAdvance(bold)`（粗体多一个 `boldOffset`）、颜色取
   `Style.getColor()`（默认白）并在 shader 里按 `texel * vertexColor` 相乘。这四处不对齐的话，
   粗体/斜体/彩色文字会和上游（`Font.drawInBatch`）出图不一致 —— 实测三种样式都比“只画字形盒”更贴近原版。
7. **`NativeImage` 的通道序**：`getPixel/setPixel` 收发的都是 **ARGB**（内部自己转 ABGR 内存序），
   千万别再手工换 R/B —— 我按“内存序”猜了一次，结果 CPU 路径的红字变成了蓝字（GPU 侧本来是对的）。
8. 开关：`-Dexparticle.text=cpu|auto|gpu`（默认 auto：GPU 失败静默回退 CPU；`gpu` = 严格模式，失败写聊天栏）、
   `-Dexparticle.text.compare=true`（CPU/GPU 逐像素对照）、`-Dexparticle.gpuText=true`（每秒一次稳定性探针）、
   `-Dexparticle.selftest=true`（进世界后跑一轮自检）。

**实测证据（2026-09-23 最终版，1.21.10-Fabric 0.19.5）**

| 项目 | 结果 |
|---|---|
| 稳定性 | 1 Hz 探针连续 **221 次输出完全一致**（`size=36x24 painted=342 bbox=[0,0 .. 32,20]`）；旧实现是「第一次有内容、之后恒为顶部 4 行常量色带 `0x40004000`」 |
| CPU/GPU 交叉验证 | `"AB"`@3 → 342/342；`"L"`/`"T"`@4 → 176/176；`"A中"`@4（跨图集）→ 480/480；**粗体** `"AB"` → 360/360（图 42x25）；**斜体** → 332/332；**红色** → 342/342 且 `gpuMid=0xffff5555`（ARGB 纯红）；**粗体混排** `"A中"` → 302/302；以上全部 **visibleDiff=0、alphaDiff=0** |
| 行序 / 方向 | **与上游一致：图像 row 0 = 文字底行**（上游 `glReadPixels` 回读后不翻转；粒子侧 `y = row/dpb`，row 越大世界 y 越高 → 世界里正立）。本 port 早先翻了行 = 上下颠倒，已按上游口径改回；ASCII 图现在是「上下镜像」的，这是**预期** |
| 端到端命令 | 写文件驱动 `particlex text … "AB" 3 …` → `jcmd GC.class_histogram` 里 `net.minecraft.class_675` 增量 **342**（= 光栅化像素数）。旧 CPU 路径同一命令只有 38 颗，是「一个图集像素一个点」的点采样 |
| 样式端到端 | 先 `clear-particle` 再单发命令，`class_675` 绝对计数：plain 342 / bold 360 / italic 332 / red 342 / bold 混排 `A中` 302 —— 与光栅化像素数逐一对上 |
| 其它 | `"ExParticle GPU text"`@2 → 796 颗；`"中文字形测试"`@2 → 349 颗；无报错、无回退 |

**三个踩过的坑**：① 图集快照**不能跨调用缓存**——原版字体按需把新字形拼进图集纹理，缓存下来之后
才用到的字形（实测 L/T）在图集快照里是全透明的，CPU 回退直接出 0 像素；② CPU 回退必须
**目标像素驱动**（每个目标像素反查图集坐标），逐图集像素盖方块会在「图集分辨率 ≠ 字形盒尺寸」的
CJK 字形上重复计数（实测 656 vs 480）；③ 用“类直方图差值”量粒子数会被 GC / 上一条命令的残留粒子污染，
**先 `clear-particle` 再取绝对计数**才可靠（同一个斜体命令曾量出 30 和 332 两个值，绝对值法确认是 332）。

#### 与上游 `TextUtil` 的逐条对照（2026-09-23 晚间，拉上游源码核对）

上游源码：`https://raw.githubusercontent.com/hackermdch/ExParticle/main/src/main/java/net/hackermdch/exparticle/util/TextUtil.java`
（本机副本 `_scratch-m3-39\gpu\upstream\TextUtil.java`，仅 40 行）。它的做法是
`TextureTarget(w,h)` + `matrix.setOrtho(0, w, h, 0, 0, 1000)` + `font.drawInBatch(text, 0, 0, -1, false, scale(scale,…), …, NORMAL, 0, 0xf000f0)`
+`glReadPixels` + `memCopy`，最后 `getMainRenderTarget().bindWrite(true)`。

| 上游行为 | 本 port 的处理 |
|---|---|
| `width=(int)(font.width(text)*scale)`、`height=(int)(font.lineHeight*scale)` | **已改成同式**（此前按「ink 高度」裁高度，scale=3 时 24 vs 上游 27，粒子布局会整体差几行） |
| `glReadPixels` 后**不翻转行**（row 0 = 文字底行） | **已改成同式**（早先翻了行 → 世界里上下颠倒） |
| 用 `Font.drawInBatch` 画字 → 自带原版字形几何：斜体错切、粗体加厚、粗体步进、`Style` 颜色、`ColorModulator` / 光照全亮 | 本 port 不借原版管线，改为**逐项照抄公式**：`(1-0.25*up/down)` 错切、`±0.1` 加厚、`getAdvance(bold)`、`Style.getColor()` 相乘（光照按全亮 = 中性，无需采样） |
| `scale == 0` → 1×1 | 同（另外把 `width/height` 兜底为 ≥1，避免 0 尺寸 FBO） |
| 用 `TextureTarget` + `RenderSystem.setProjectionMatrix` 离屏、结束再 `bindWrite(main)` | 1.21.6+ 这两样都没了，只能自管 FBO；本 port 改为**逐个 GL 状态原值还原**（见上文「状态纪律」） |
| 阴影 | 上游 `dropShadow=false`，本 port 同样不画阴影 |
| obfuscated 等「效果字形」（`EffectGlyph` 随机字形） | 未实现（`text` 里用不到；如需对齐要另外处理） |

样式实测（本 port，clear 后取绝对计数）：plain `"AB"`@3 = **342**、bold = 342（图 42×27，粗体加厚已生效）、
italic = 332（错切后边缘被裁剪，与上游同样的裁剪口径）、red = 342 且 `gpuMid=0xffff5555`（ARGB 纯红）、
bold 混排 `"A中"` = 282；CPU/GPU 两路全部 `visibleDiff=0`。截图确认红色文字出的就是**红色粒子**。

> 以下三节是历史记录：**「借 MC 的 RenderPass 做离屏」的完整失败过程**，保留证据用（结论已定：此路不通）。

#### 历史记录：三条时机的失败（2026-09-23 早）

上游实现：`TextureTarget` + 覆盖 RenderSystem 投影 + `Font.drawInBatch` + `glReadPixels`。
在 1.21.10 上这条 GPU 路**试了三种写法都画不出像素**（回读链路本身已自证正常：把清屏色设成已知值，
972/972 像素逐一读回 ✓，而文字始终 0 像素）：

1. payload/tick 阶段直接离屏绘制（等价上游）→ 0 像素；
2. 换 Fabric `HudRenderCallback`（1.21.6+ 它是 GUI「收集阶段」）→ 0 像素；
3. 换 `GuiRenderer.prepare`（GUI「执行阶段」、原版 `PictureInPictureRenderer` 的挂载点），
   并把离屏目标从 `TextureTarget` 换成**与原版 PiP 完全一致的裸 `GpuTexture`**
   （颜色 `USAGE_TEXTURE_BINDING|RENDER_ATTACHMENT` + `RGBA8`；深度 `USAGE_RENDER_ATTACHMENT` + 主目标深度格式）、
   正交投影 + model-view 归单位阵 → 仍然 0 像素。

当时的临时方案是**完全不碰 GPU 渲染管线**的 CPU 光栅化（现在已降级为回退路径）：

- 字形度量与图集 UV：`Font.getGlyph(codepoint, style)` + `BakedSheetGlyph` 的
  `(u0,u1,v0,v1)` 与 `(left,right,up,down)`（这几处是包私有/私有成员，用 access widener 打开）；
- 图集像素：`CommandEncoder.copyTextureToBuffer` + `GpuFence.awaitCompletion` + `mapBuffer`
  回读（字体图集是 `RED8` 时按 `NativeImage.Format.LUMINANCE` 解释，`RED8I`/`RGBA8` 按 RGBA）；
  若回读失败则退回原版字体 dump 用的 `TextureUtil.writeAsPNG` + ImageIO；
- 逐字形按 UV 取像素、按 `left/right/up/down` 定位，y 范围自动归一（不依赖"y 是基线还是顶边"的假设）。

**踩坑**：`new NativeImage(...)` 的内存**不清零**，不显式 `MemoryUtil.memSet(pixels, 0, size)` 的话，
未绘制像素会带垃圾 alpha → 调用方按"alpha≠0 就生成粒子"会凭空多出几百颗粒子（实测 773 vs 期望 38）。

#### 历史记录：GPU 离屏管线的实现与当时的卡点（同日）

已写成的 GPU 路径（`TextUtil.gpuRenderAsync`）：
1. 从字形自己的 `BakedSheetGlyph.renderTypes.select(NORMAL)` 取 **`minecraft:pipeline/text`** 与其顶点格式；
2. 用 `BufferBuilder` 手写字形四边形（格式实测为 `Position,Color,UV0,UV2`，逐个元素补齐：缺一个就抛
   `Missing elements in vertex`）；
3. `VertexFormat.uploadImmediateVertexBuffer` + 自建 `RenderPass`（颜色 `RGBA8` usage 12、深度用主目标深度格式）；
4. `pass.setPipeline` + `RenderSystem.bindDefaultUniforms` + `DynamicTransforms` + 绑定字体图集 sampler + `drawIndexed`。

**两个必须记住的 1.21.6+ 约束**（都是实测栈定位的）：
- `DynamicUniforms.writeTransform(...)` 内部会 **map UBO**，而 **render pass 打开期间禁止任何 mapBuffer** →
  必须在 `createRenderPass` **之前**算好 slice（否则报 `Close the existing render pass before performing additional commands`）；
- MC 的命令编码器在**整帧级的 pass** 里禁止 `mapBuffer/copyTextureToBuffer` 回读（tick、GUI prepare、
  `GuiRenderer.render` HEAD、`GameRenderer.render` HEAD 都试过，全部被拒）→ 回读改为**裸 GL**
  `GL32.glGetTexImage(((GlTexture) colorTexture).glId(), ...)`，绕开编码器策略；
- `QUADS` 的 `MeshData` **没有 indexBuffer**，要与原版一样用 `RenderSystem.getSequentialBuffer(mode)`。

**当前卡点**：上述管线已无异常（日志：`gpu-render: glyphs=2 size=36x27 format=Position,Color,UV0,UV2
pipeline=minecraft:pipeline/text`），但离屏纹理读出来仍是 `painted=0` —— 说明 pass/绘制本身生效了、
像素没落上去。剩余可疑点（按优先级）：离屏 pass 的 viewport/投影状态、`text` 管线对
ModelOffset/Fog 等默认 UBO 的要求、以及 `CachedOrthoProjectionMatrixBuffer` 的 invertY 取值。
诊断入口已留好：`-Dexparticle.selftest=true` 启动会在进世界后自动跑一次 CPU+GPU 对照并打日志。

#### 历史记录：GPU 管线最新进展（同日晚）

**已能画进离屏纹理** ✓ —— 关键是**驱动时机**与**model-view**：
- 驱动点必须是 `GuiRenderer.prepare`（原版 PiP 的离屏时机）。在 tick / `GameRenderer.render` HEAD 驱动时，
  自建 pass 不报错但**一个像素都落不上去**（纹理全 0）；换到 `GuiRenderer.prepare` 后立刻有内容。
- 必须把 model-view **压成单位阵**（GUI 阶段残留的变换会把内容推出纹理，实测去掉就全 0）。
- `writeTransform` 必须在 `createRenderPass` **之前**（它内部 map UBO，pass 内禁止 mapBuffer）。
- `QUADS` 的 MeshData 无 indexBuffer → `RenderSystem.getSequentialBuffer(mode)` + `drawIndexed`。
- vanilla 的 `RenderPass` **没有** `setViewport`（那是 NeoForge 补丁），viewport 由附件尺寸决定。
- 回读用裸 GL `glGetTexImage(((GlTexture) colorTexture).glId(), ...)`，绕开帧级 pass 对 `mapBuffer` 的限制。

当前用 ASCII 像素图（`-Dexparticle.gpuText=true` 时的日志）能直接看到产出：一张 36×27 的离屏纹理里
顶部 3 行整宽 + 左侧 18 宽一块 —— 即**渲染生效但投影缩放尚未与纹理尺寸对齐**（纵向约 9×、横向约 1.5× 偏差，
与 GUI 缩放同源）。因此默认出图仍走 CPU 光栅化（`-Dexparticle.selftest=true` 可验证：日志只有
`text self-test: cpu 36x24 pixels=38`），GPU 路径作为实验分支保留，开关 `-Dexparticle.gpuText=true`。
下一步只需把投影换成自建矩阵（不经 `CachedOrthoProjectionMatrixBuffer`）把缩放对齐即可。

#### 历史记录：GPU 管线稳定性修复（同日，关键）

「同一份代码时好时坏、第二次起必失败」的根因**不是**时机竞争，而是**资源误用**：
MC 的 `VertexFormat.uploadImmediateVertexBuffer(...)` / `uploadImmediateIndexBuffer(...)` 返回的是
**按顶点格式缓存的共享缓冲**（`immediateVertexBuffer` / `immediateIndexBuffer`），我按"自己创建的资源"
把它们 `close()` 了 → 之后每次上传都在 `uploadToBuffer` 抛 `Buffer already closed`。
去掉这两个 close（并在注释里写明不可关闭）后，1 Hz 重复实验 **70 次全成功、0 失败**。
另外两处同类修正：投影 UBO 改为**每次渲染新建**（复用同一个 `CachedOrthoProjectionMatrixBuffer`
也会在第二次 `writeToBuffer` 时报同样错误）；`ensureRenderTarget` 增加 `isClosed()` 检查。

**当前状态**：离屏 pass 现在稳定产出内容，但**坐标空间仍未对齐**——ASCII 像素图呈斜纹/平铺状，
说明实际生效的投影映射的空间与我在"像素空间"里发的顶点不一致（与 GUI 缩放同源）。
下一步只需统一坐标空间：要么按 GUI 空间发顶点（顶点除以 GUI 缩放），要么构造与附件尺寸匹配的投影。
默认出图仍是 CPU 光栅化，GPU 为实验分支（`-Dexparticle.gpuText=true` 每秒跑一次对照）。

**bbox 测量与三个假设的排除（同日，收尾）**：每轮先清空纹理再统计文字像素包围盒，结果稳定复现：
- 第 1 次调用：`bbox=[0,0 .. 35,26]`（覆盖整张 36×27，颜色各异，像真实内容）；
- 之后每次：`bbox=[0,0 .. 35,3]`，且整条带子颜色恒为 `0x40004000`（不是字形）。
据此排除的三个假设：①「投影没生效」→ 显式 `pass.setUniform("Projection", slice)` 覆盖后无变化；
②「GL viewport 继承主帧」→ 裸 `GL11.glViewport(0,0,w,h)` 并还原后无变化；
③「model-view 残留」→ 归单位阵是**必需**的（去掉会全空），但不足以修正缩放。
结论：只剩"每次调用之间的 GPU 状态差异"（第 1 次调用与后续调用的采样/顶点缓冲状态不同），
这部分没有公开 API 可控制。**这不影响交付**——`text` 出图走 CPU 光栅化（输出等价且已验证），
GPU 路径保留为实验分支，随时可用 `-Dexparticle.gpuText=true` 复现上述数据继续研究。

#### 无人值守验证通道（2026-09-23 新增）

**实时命令通道（默认关闭；用 `-Dexparticle.testChannel=true` 启用）**：写 `<gamedir>/config/exparticle-live.txt`
```
seq=7                      # 数字变了才重跑
particlex normal minecraft:end_rod ~ ~1 ~ 1 1 1 1 0 0 0 1 1 1 100 600 "null" 1 mygrp
# 以 # 开头的行忽略；每行一条不带斜杠的命令
```
进度写在同目录 `exparticle-live.out.json`（`{"seq":n,"sent":k,"total":m,"last":"..."}`）。
发送走 `ClientPacketListener.sendCommand`（等同聊天栏执行），每条间隔 1 秒。
> 为什么需要它：本机给 MC 注入键盘输入极不稳定——中文输入法激活时游戏收不到按键（用户提醒可用
> Win+Space 切英文，但实测多次切换仍无效），窗口还常被其它程序压住，所以「写文件驱动」是唯一
> 可靠的批量验证方式。

#### 命令语法要点（踩过的坑，改参数前先看）

| 参数 | 正确写法 | 说明 |
|---|---|---|
| 矩阵（`image-matrix`/`video-matrix`） | `"E4"` 或 `"1,0,0,0,,0,1,0,0,,0,0,1,0,,0,0,0,1"` | **字面矩阵**，不是表达式：`MatrixUtil.toMat(String)` 只认 `E<n>`（n 阶单位阵）或逗号分隔、`",,"` 分行的字面矩阵（外层括号会被剥掉）。给表达式（如 `(x,y,z)=(x,y,z,1)*rotate(...)`）会走到异常分支 |
| `custom-parameter` | 必须带 `<mode>`：`custom-parameter polar <name> <pos> <begin> <end> <expr> [step] [cpt] …` | mode ∈ `normal`/`polar`/`tick`/`tick-polar`；漏掉 mode 会报「错误的命令参数」 |
| `global-variable define` | `global-variable define <int\|double\|quat> <name> "<value>"` | 少了类型（直接写名字+值）会报「未知或不完整的命令」；`peek` 经回执在聊天栏显示值 |
| `user-function define` | `user-function define <name> "<args>" "<body>"` | 参数表与函数体是两个**字符串**参数，例如 `f "a,b" "(a+b)"`；`list` 显示 `f(a,b) => (a+b)` |
| `color4` / `speed3` 类参数 | 必须写满（如 `tick-parameter` 要 `color4(4 个数) + speed3(3 个数)`） | 这两个参数是**贪婪多词**解析器（各吃 1–4 / 1–3 个数字、失败**不回退**），少写一个数字它会去咬下一个参数，报「应为双精度浮点型」 |
| `custom-image` / `custom-conditional` / `custom-normal` / `custom-image-matrix` / `custom-video` / `custom-video-matrix` | 无 mode | 直接按各自参数表 |

实测结果（写文件驱动）：`image-matrix "E4"` → 256 颗（16×16 精确）；`video-matrix "E4"` →
解码 29/30 帧、144 颗；`custom-parameter polar` + `custom-image` + `custom-conditional` 三条
连发 → 354 颗、无报错。

- `-Dexparticle.selftest=true`：进世界后自动跑一次 text 的 CPU/GPU 对照并打日志（含 GPU 的 ASCII 像素图）。
- `-Dexparticle.gpuText=true`：把 GPU 离屏路径纳入对照运行（默认关闭，出图仍走 CPU）。
- `-Dexparticle.selftest.commands=true`：**命令自驱动**——进世界后每 5 秒自动发送一条同族命令变体
  （`normal`/`image-matrix`/`video-matrix`/`custom-parameter`/`custom-image`/`custom-conditional`/
  `tick-parameter`/`rgba-tick-polar-parameter`/`text`/`group change`/`functions`），完全不依赖窗口输入
  （本机窗口经常被其他程序压住、按键送不进去）。
  实测：11 条全部成功发送、除 `functions` 的正常输出外**无任何报错**。
  **注意**：「无报错」≠「真出粒子」——同一轮里 `video-matrix` 没有触发解码日志，说明矩阵类变体的
  表达式还得用文档里的形式（如 `(x,y,z)=(x,y,z,1)*rotate(PI/4,0,0)`）逐个校准后再验，
  否则命令会静默地什么都不做。这一步是后续验证的重点。

## 实测证据（2026-09-23，实机 1.21.10-Fabric 0.19.5）

- 模组加载/注册：`jcmd <pid> GC.class_histogram` 里可见 `ExParticle`、`ExParticleClient`、
  `CustomArgumentTypes` 注册的 11 个 `ArgumentTypeInfo`、`PayloadRegistrar` 的 lambda。
- `/particlex normal minecraft:end_rod ~ ~1 ~ 1 0.6 0.2 1 0 0 0 2 2 2 1500`
  → 1500 颗橙色粒子（颜色覆盖生效）。
- `/particlex tick-polar-parameter minecraft:end_rod ~ ~-0.6 ~ 1 0.4 0.9 1 0 0 0 0 6.283 "dis=1.5;s1=t;s2=0" 0.06 80 400 "vy=0.006" 1`
  → 极坐标成环（表达式引擎实测），三连拍可见环逐帧上升（逐 tick 运动实测）。
- **光影下正常**：`enableShaders=true` + `iterationRP Alpha 0.8.26 hotfix` 下环带发光正常、有 bloom，
  没有出现"被当不透明实体重画"的老问题（粒子走光影包自己的 particle 程序）。
- **`text` 端到端**：写文件驱动 `particlex text minecraft:end_rod ~ ~1 ~ "AB" 3 "null" 10 0 0 0 600`
  → `jcmd GC.class_histogram` 中 `net.minecraft.class_675`（end_rod 粒子类）增量 342；同一轮 1 Hz GPU
  探针连续 124 次输出完全一致（详见「text 实现方式」一节的表格）。

## 已知坑（下次改这个工程先看这里）

1. 聊天里 `polar-parameter` **没有** `cpt` 参数（`cpt` 只属于 `tick-*` 系列）。多写一个数字会报
   "应为双精度浮点型"，且光标停在末尾，很容易误判成 speedStep 的问题。
2. Fabric 客户端命令同名 root 会遮蔽服务端命令树（见上表）。
3. 游戏日志：本机用 `_scratch-m3-39\live\launch-mc.ps1` 起的实例有时写不进
   `logs/latest.log`（疑似 log4j 文件 appender 与残留进程竞争），需要日志时用 `jcmd`/游戏内聊天确认。
4. `text` / GPU 路径的两个坑（**图集不可跨调用缓存**、**CPU 回退要目标像素驱动**）写在
   「text 实现方式」一节末尾，改 `TextUtil` / `GlTextRasterizer` 之前先看。
5. 用 `_scratch-m3-39\gpu\launch.ps1` 起的实例会把 stdout 落到该目录的 `mc-HHmmss.log`；
   想数粒子用 `_scratch-m3-39\gpu\particles.ps1 -Command '...'`（走 jcmd 直方图差值，不依赖窗口）。
