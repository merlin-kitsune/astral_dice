## StarEngine 1.1.0 (neoforge-26.1.2 / fabric-1.20.1 首次发布)

- 新增 26.1.2 (NeoForge) 与 1.20.1 (Fabric) 两个平台的库产物。
- 添加原因：Astral Dice 1.5.0 起四线统一改为「库为独立外部前置、不再内嵌」，
  CurseForge 是本库 jar 的唯一分发渠道，故本平台必须提供对应文件：
  - `starengine-neoforge-26.1.2-1.1.0.jar`
  - `starengine-fabric-1.20.1-1.1.0.jar`
- 版本号 1.1.0 与其余平台一致；下游 Astral Dice 的依赖区间为 `[1.1.0,2.0.0)`（NeoForge）与
  `>=1.1.0 <2.0.0`（Fabric），本文件满足。
- 从 1.1.0 起本库许可证为 GPL-3.0-only。