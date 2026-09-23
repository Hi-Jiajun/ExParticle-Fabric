# Modrinth 上传素材（ExParticle Fabric 移植版）

> 这份文件只用于上传/发布时复制粘贴，不参与构建。

## 名称 / 简介

- **名称**：`ExParticle (Fabric)`
- **Summary（英文，Modrinth 摘要）**：
  `Unofficial Fabric 1.21.10 port of ExParticle — expression-driven particle effects (normal / conditional / parameter / image / video / text).`
- **Summary（中文）**：
  `ExParticle 的 Fabric 1.21.10 移植版（非官方）：用数学表达式驱动粒子运动，支持 normal / conditional / parameter / image / video / text 等命令族。`

## 分类与标签（建议）

- **Project type**：Mod
- **Loaders**：Fabric
- **Game versions**：1.21.10
- **Environment**：**Client**（必选）；Server 侧未做完整验证，建议先只勾 Client，描述里说明"服务端安装未验证"
- **Categories**：`utility`（可选 `decoration`，看你想怎么归类）
- **License**：`LGPL-3.0-only`（**必须**，见下）

## 依赖（Dependencies）

- **必需**：`Fabric API`
- **可选**：`Fabric Loader ≥ 0.19.5`、`Java ≥ 21`
- **可选功能**：JavaCV（使用 `video` 命令时，把 javacv / javacpp / ffmpeg 的 jar 放进 `<gamedir>/javacv/`）

## 正文描述（可直接粘贴，Markdown）

```markdown
ExParticle 的 **Fabric 1.21.10 移植版**（非官方，基于上游 [hackermdch/ExParticle](https://github.com/hackermdch/ExParticle) v1.5.2，许可证同为 **LGPL-3.0-only**）。

用数学表达式驱动粒子运动：把形状、运动、颜色写成表达式，粒子按表达式逐 tick 演算。

## 命令族（与上游一一对应）

`normal` / `conditional` / `parameter` / `polar-parameter` / `tick-*` / `rgba-*` /
`custom-normal|parameter|image|conditional|video` / `image(-matrix)` / `video(-matrix)` / `text` /
`group remove|change` / `clear-particle|clear-cache` / `global-variable` / `user-function`

```
/particlex normal minecraft:end_rod ~ ~1 ~ 1 1 1 1 0 0 0 1 1 1 500 600 "null" 1
/particlex text minecraft:end_rod ~ ~1 ~ "AB" 3 "null" 10 0 0 0 600
/particlex image minecraft:end_rod ~ ~1 ~ "test.png" 1 0 0 0 not 10 0 0 0 600
```

- `image` / `video` 的文件放 `<gamedir>/particleImages`、`<gamedir>/particleVideos`
- `video` 需要自行安装 JavaCV（jar 放 `<gamedir>/javacv/`）

## 与上游的差异（重要）

1. **`text` 用自管 GPU 离屏光栅化实现**：MC 1.21.6 起移除了上游用的 `TextureTarget`/旧投影 API，所以这里改成"自建 FBO + 像素坐标直投 + `glReadPixels` 回读"，GPU 失败自动回退 CPU 软件光栅化。
2. **`video` 的 JavaCV 装载换成 child-first 类加载器**：只需 5 个核心 jar（上游走模块层，需要整条 JavaCV 模块链 + JavaFX），并修掉了上游 `Java2DFrameConverter.convert()` 在部分 JavaCV/FFmpeg 组合下必 NPE 的问题（改用 `copy()`）。
3. **粗体文本的观感与 1.21.1 上游不同**：MC 1.21.10 把粗体从"偏移画两遍"改成"单遍加厚"，本移植跟随 1.21.10 的原版渲染。
4. 与上游做过逐条差分：17 条用例中 16 条粒子数**逐位一致**（其余一条即上述 bold，属 MC 版本语义）。

## 致谢

- 原作者 **hackermdch**（上游 [ExParticle](https://github.com/hackermdch/ExParticle)，LGPL-3.0-only）
- 本移植为修改版，源码与修改说明随仓库提供（LGPL 要求）
```

## 首个版本 changelog（建议文案）

```markdown
### v1.5.2-fabric.1

- 上游 ExParticle v1.5.2 完整移植到 **Minecraft 1.21.10 / Fabric**（Loader ≥ 0.19.5，Java ≥ 21）
- 全命令族可用：normal / conditional / parameter / tick / rgba / custom-* / image(-matrix) / video(-matrix) / text / group / clear-* / global-variable / user-function
- `text`：自管 GPU 离屏光栅化（1.21.6+ 已移除上游的 TextureTarget 路径），CPU 光栅化作为回退
- `video`：child-first 类加载器装载 JavaCV（只需 5 个核心 jar），并修掉 `convert()` 的 NPE
- 与上游差分：17 条用例 16 条粒子数逐位一致；bold 差异为 MC 1.21.10 的版本语义
```

## 合规与礼仪（上传前务必确认）

1. **许可证必须选 LGPL-3.0-only**：本移植是上游的衍生作品，LGPL 要求修改版仍以同许可发布，并**提供源码**（仓库链接填到 Modrinth 的 Source 字段）。
2. **署名**：描述与 `fabric.mod.json` 里都要写明基于 hackermdch 的上游项目；不要暗示这是官方版本（标题带 "(Fabric)"、描述里写 "非官方 / unofficial"）。
3. **图标要自己画**：不要直接使用上游的项目图标（图片素材通常不在代码许可证覆盖范围内）。仓库里的 `icon.png` 是为本移植新画的，可直接用。
4. **不要上传上游的 jar**，只上传本仓库构建出的 Fabric 版 jar。
5. 上传前确认 jar 里没有个人配置：本仓库已在 `.gitignore` 里排除本机专用的 `gradle-mirror.init.gradle`，`gradle.properties` 也不再含个人代理设置。
