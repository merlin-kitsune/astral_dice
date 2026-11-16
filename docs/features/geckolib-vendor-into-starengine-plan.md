# GeckoLib 核心下沉到 StarEngine（各线内嵌 + 对外 API）实施方案

## 0. 结论先行

把 GeckoLib 的动画/渲染核心（除 example 包、网络层、mixin 外）源码级【包名重定位】后搬进 F:/MCProject/starengine_next，由 starengine 的四个平台子项目各自编译进 starengine-*.jar；消费方 astral_dice_multiloader-next 的各线全部卸掉 GeckoLib 依赖与硬前置声明，改用 starengine 提供的同名 API（命名空间已重定位），并保留 triggerAnim 的服务端→追踪客户端同步语义（由 starengine 自己实现的网络 SPI 承担）。

为什么必须重定位包名（而不是原包名塞进 starengine）：原包名（v4 software.bernie.geckolib、v5 com.geckolib）与玩家自装的真 GeckoLib FQN 相同，同 classpath 下会【同名类先到先得 + 两份 mixin config 同名被静默跳过】，版本错位时还会 NoSuchMethodError / VerifyError。重定位后二者是两套互不干扰的类，玩家装不装 GeckoLib 都不影响本模组，其他模组想用 GeckoLib 也能照装 —— 这是最大兼容性的唯一安全形态。

## 1. 目标与非目标

### 目标
1. 各线（neoforge-1.21.1 / forge-1.20.1 / fabric-1.20.1 / neoforge-26.1.2）的模组 jar 不再需要玩家安装 GeckoLib；mods.toml / neoforge.mods.toml / fabric.mod.json 里的 geckolib 依赖块全部删除。
2. 动画 API 成为 starengine 的公开契约，供 TLMDice、ADExtra（未来的生物模组）直接 compileOnly starengine 使用；API 形态与上游一致（GeoEntity / AnimatableInstanceCache / AnimationController / RawAnimation / PlayState / GeoModel / GeoEntityRenderer / GeckoLibUtil），只换包名。
3. 现有 SykeTentacleEntity 的 idle + 受伤触发 attack 动画行为逐字保持（含服务端触发→客户端播放的同步）。
4. 玩家同装真 GeckoLib（任意版本）不崩、不冲突、不抢类。

### 非目标（明确不做）
- 不做 jarJar / include 内嵌（本仓门禁禁止 META-INF/jarjar/* 与 META-INF/jars/*.jar）。
- 不搬 example 包、不搬网络层、不搬 mixin（mixin 按 §6 的最小必要集例外机制处理，默认一条不装）。
- 不支持各线之外的 MC 版本；不做上游同步自动化（只做一次性 pin + 指纹记录）。
- 不动 CurseForge、不改发布通道、不动 models/ 下他人资产。

## 2. 事实基线（已实读，方案建立在此之上）

| 线 | MC | GeckoLib 坐标（现） | 上游分支 | 发布 jar 内 class |
|---|---|---|---|---|
| forge-1.20.1 | 1.20.1 | build.gradle:244 aC5KMoNg = 4.8.4-forge | 1.20.1 @ 7d6f32db | 260（example 58 / network 23 / mixin 3；mclib 走 jar-in-jar 69）|
| fabric-1.20.1 | 1.20.1 | build.gradle:158 SD2lOScS = 4.8.4-fabric | 同上 | 同族 |
| neoforge-1.21.1 | 1.21.1 | build.gradle:191 Grwn5rUB = 4.9.3-neo | 1.21.1 @ cce09809 | 263（network 17 / mixin 7）|
| neoforge-26.1.2 | 26.1.2 | build.gradle:223 xfVfPcoC = 5.5.2-neo | 26.1 @ d91c9b36 | 403（包根 com/geckolib；network 18 / mixin 18）|

- 上游无 tag，只有分支（git ls-remote 实读）；取源必须以【分支 + 与发布 jar 对齐的提交】为准，并把 commit SHA 写进 vendored/SOURCE.md。
- 许可 MIT：重定位/再分发合法，但必须整份保留版权与许可声明。
- 消费方 Java 侧只用到 9 个符号、12 个文件（每线 3 个：entity/SykeTentacleEntity.java、client/SykeTentacleModel.java、client/SykeTentacleRenderer.java）。
- v4 与 v5 API 差异（PlayState 迁到 animation.object、AnimatableManager 迁到 animatable.manager、GeoModel 改 GeoRenderState→Identifier、GeoEntityRenderer 变 T+R extends EntityRenderState 并带 createRenderState / submit）只在各自线内部成立，不做跨线统一。
- starengine 现状：common/ 53 java 是四平台共享源码目录（不是 Gradle 子项目），每平台 src 只有 8 个类；库内零 mixin/AT、零网络层 —— 这两样都是本方案要新增的基础设施。

## 3. 包名与命名空间映射（一次性定死）

| 上游 | 重定位后 | 适用线 |
|---|---|---|
| software.bernie.geckolib | com.merlinkitsune.starengine.geckolib | v4 资产线（forge-1.20.1 / fabric-1.20.1 / neoforge-1.21.1）|
| com.geckolib | com.merlinkitsune.starengine.geckolib5 | v5 资产线（neoforge-26.1.2）|
| com.eliotlash.mclib | com.merlinkitsune.starengine.mclib | 仅 1.20.1（4.8.4 的 Molang 依赖，jar-in-jar 69 class 也要搬）|
| mixin 配置名 geckolib.mixins.json | starengine.geckolib.mixins.json | 仅当触发 §6 例外时存在 |

资源侧：assets/geckolib/**（上游示例 geo/animation/texture/lang）不搬；消费方自己的 assets/astral_dice/{geo,animations,textures/entity} 不受影响。

## 4. 目录与构建接线（starengine 侧）

F:/MCProject/starengine_next/
  vendored/SOURCE.md                 # 每线：上游 repo / 分支 / commit SHA / 发布 jar sha1 / 许可
  vendored/1.20.1/common/java/...    # v4 loader-agnostic 主体（重定位后）
  vendored/1.20.1/forge/java/...     # v4 forge 平台层（我们改写的 adapter）
  vendored/1.20.1/fabric/java/...    # v4 fabric 平台层
  vendored/1.21.1/common/java/...
  vendored/1.21.1/neoforge/java/...
  vendored/26.1.2/common/java/...
  vendored/26.1.2/neoforge/java/...
  tools/vendor_geckolib.py           # 取源→裁剪→重定位→落盘（干跑/幂等/清单）
  tools/verify_vendored_geckolib.py  # 门禁：包名残留 / jarjar / 许可 / 文件清单哈希

构建接线：每个平台 build.gradle 的 sourceSets.main.java.srcDir 追加 ../vendored/【mc】/common/java 与 ../vendored/【mc】/【loader】/java（沿用现有 common/ 的既有做法）。注意 common/ 现在禁用 Java 21 语法，vendored 目录按各自上游语言级别编译（1.20.1 = 17，1.21.1 = 21，26.1.2 与 n26 toolchain 对齐）。

新增基础设施（starengine 原本没有）：
1. mixin 能力：net.neoforged.moddev 的 mixin { add sourceSets.main, starengine.refmap.json; config starengine.geckolib.mixins.json }（forge/neo 线按各自写法）；1.20.1 forge 侧 refmap 必须由本仓 AP 生成（重定位后上游 refmap 的 key 是旧 mixin FQN，必然作废）。
2. AT 能力：把上游 META-INF/accesstransformer.cfg 里与本方案保留代码实际相关的条目合并进 starengine 自己的 AT 文件（v4 forge 侧是 SRG 名，v5 是 Mojmap 名，逐线核对，不整份照搬）。
3. 网络 SPI（替代被排除的网络层，见 §5）。

## 5. 网络层的替代设计（triggerAnim 同步不能丢）

上游 GeoEntity#triggerAnim 在服务端会走 GeckoLibServices.NETWORK.triggerEntityAnim(...) 广播给追踪该实体的客户端。网络层被排除后，改为 starengine 自有 SPI：

common: com/merlinkitsune/starengine/animation/AnimationSync.java
  interface AnimationSync { void triggerEntity(Object entity, String controller, String anim); }
  静态 INSTANCE（平台注册；未注册时退化为仅本地调用）
每平台 adapter:
  neoforge-1.21.1 / neoforge-26.1.2 : CustomPacketPayload + PacketDistributor.sendToPlayersTrackingEntityAndSelf
  forge-1.20.1                     : SimpleChannel + PacketDistributor.TRACKING_ENTITY_AND_SELF
  fabric-1.20.1                    : ServerPlayNetworking.send（PayloadTypeRegistry.playS2C）
client 侧统一: 收到后 entity.getAnimatableInstanceCache() -> AnimatableManager.triggerAnim(controller, anim)

API 兼容层：GeoEntity#triggerAnim(controller, anim) 保持原签名（消费方 12 个文件的调用行零改动），内部改为走 AnimationSync.INSTANCE 或（客户端/未注册时）本地直调。这样 TLMDice / ADExtra 拿到的是同一套语义。

## 6. mixin 排除的风险闸门（默认零 mixin，破了才补最小集）

用户要求除 example / 网络层 / mixin 外都内嵌，因此首版不带任何 mixin，然后按下面闸门实机判定：

| 代 | 可能走失的能力 | 缺失后现象 | 处置 |
|---|---|---|---|
| v4（1.20.1 / 1.21.1）| TextureManagerMixin、LevelRendererMixin、HumanoidArmorLayerMixin、BlockEntityWithoutLevelRendererMixin | 实体本体渲染与动画不受影响；glowmask / 盔甲 / 特殊物品渲染走失 | 本模组当前无 glowmask，首版不补；未来要用再按最小集补 |
| v4 common | AbstractContainerMenuMixin / ItemStackMixin / LivingEntityMixin | 物品/实体的 data ticket 同步走失（实体动画触发同步由 §5 自有 SPI 承担，不依赖它）| 首版不补；若 §9 实机发现动画数据不同步，优先补 LivingEntityMixin |
| v5（26.1.2）| EntityRenderStateMixin / EntityRendererMixin / EntityRenderState 接口注入 | 可能整只实体不出渲染或渲染无动画（v5 把动画数据挂到 render state）| 唯一高概率必须补的一组：先不带 mixin 试；若实体静止/不可见，补 EntityRenderStateMixin + EntityRendererMixin + interface_injections.json，其余仍不带 |

闸门判据：/summon astral_dice:sykestentacle 后实体可见、idle 循环播放；对其造成伤害后播放 attack 并且客户端能看到（不是只有服务端内部状态变化）。

## 7. 消费方改造（astral_dice_multiloader-next）

每线 7 处、共 28 处改动：
1. build.gradle：删除 GeckoLib 依赖行（n21 :191、forge :244、fabric :158、n26 :223）。
2. src/main/templates/META-INF/{mods.toml,neoforge.mods.toml}：删除 modId=geckolib 依赖块（n21 :120、forge :170、n26 :156）；fabric 无 geckolib depends，核对 fabric.mod.json 的 depends 是否只剩 starengine 区间。
3. 各线 gradle.properties：starengine_version 由 26.4-SNAPSHOT.1 → 26.4-SNAPSHOT.2；fabric 的 starengine_version_range 同步 >=26.4-SNAPSHOT.2。
4. 12 个 java 文件的 import 全量替换（software.bernie.geckolib → com.merlinkitsune.starengine.geckolib；n26 的 com.geckolib → com.merlinkitsune.starengine.geckolib5）。
5. SykeTentacleEntity 的【同步：triggerAnim 走 GeckoLib 自己的网络通道】注释改写为 starengine AnimationSync 的说明（该说法不再成立，注释必须跟代码一致）。
6. 文档：docs/features/ 新增一节，记录【GeckoLib 已下沉 starengine、玩家不再需要安装】+ 版本契约 + 回滚方法。
7. AGENTS.md / docs/agent/PROJECT-REFERENCE.md 里关于 GeckoLib 前置的描述同步订正（只改与本次事实相关的行）。

## 8. 版本与提交纪律

- starengine：lib_version 与四平台 mod_version 一并 26.4-SNAPSHOT.1 → 26.4-SNAPSHOT.2（该仓每提交 bump 一档，且必须同时改 mod_version，否则消费方 [starengine_version,27.0.0) 判据会 FML 拒载）。
- 两个仓分别提交，先 starengine（含 publishToMavenLocal 产物），再消费方；不 push，等用户裁决。
- 提交拆分：starengine 侧 feat(animation): vendor geckolib core (relocated)；消费方侧 feat(deps): drop geckolib prerequisite, use starengine animation API。

## 9. 分阶段执行与验收闸门

### Phase 0 — 取源与指纹（只读为主）
- clone 上游到 temp/geckolib-upstream/（不进仓），checkout 1.20.1 / 1.21.1 / 26.1 三个分支。
- 用【发布 jar 的类名清单 vs 源码目录文件清单】对账（脚本比对 FQN 集合），确认分支与 4.8.4 / 4.9.3 / 5.5.2 对齐；把 commit SHA、jar sha1、class 计数写进 vendored/SOURCE.md。
- 闸门：三份对账 FQN 差集为空（或差集全部落在 example/network/mixin 这些要排除的目录内）。

### Phase 1 — 重定位工具（tools/vendor_geckolib.py）
- 能力：取源目录 → 按【保留/排除】白名单裁剪 → 包名与 import 重写 → 落盘到 vendored/【ver】/【part】/java → 输出清单（相对路径 + sha256 + 来源 commit）。
- 幂等：重跑不产生 diff；--dry-run 只打印计划；--check 校验磁盘与清单一致。
- 闸门：--check OK；grep -r 旧包名 vendored/ 只剩注释/许可说明（0 代码命中）。

### Phase 2 — 平台层改写与编译（最大工作量）
- 用 starengine 自己的 adapter 替掉上游 platform/loading 里依赖 loader 的初始化与注册：GeoRenderProvider、实体渲染器注册、GeckoLib.initialize()、AT 生效点。
- 逐线 compileJava 打通（1.20.1 两平台先做，因为 MC API 差异最大；再做 1.21.1；最后 26.1.2）。
- 闸门：四平台 compileJava 全绿；且每线都跑 publishToMavenLocal。

### Phase 3 — 网络 SPI 与客户端播放
- 落 §5 的四平台 adapter + 客户端 handler。
- 闸门：证明【服务端触发 → 客户端 AnimatableManager.triggerAnim 被调用】（临时日志行，验证后保留为 debug 级或移除）。

### Phase 4 — mixin 闸门实测（§6）
- n21 / forge / fabric / n26 各起一次客户端，/summon + 伤害触发，截图取证。
- 只有当 §6 现象出现才引入对应最小 mixin 集，并重跑 refmap/AT 门禁。

### Phase 5 — 消费方迁移与构建
- 按 §7 改 28 处 + 12 个 java 文件。
- 闸门：各线 build 全绿；产物 jar 扫包：software/bernie、com/geckolib、META-INF/jarjar/*、META-INF/jars/*.jar == 0。

### Phase 6 — 实机与共存验证（各线各一轮）
1. 自足性：玩家侧不装 GeckoLib，实体可见 + idle + 受伤 attack + 客户端可见。
2. 共存性：把真 GeckoLib 4.9.3 / 4.8.4 / 5.5.2 的 jar 塞进 run/*/mods，同样操作，要求不崩、不抢类、动画照播（日志里应同时出现 geckolib 模组加载行与 starengine 动画行）。
3. 记录读数：Loading N mods、starengine 版本行、动画观察截图（scripts/test/mt_capture.py）。
- 闸门：8 组（4 线 × 自足/共存）全绿，截图与日志各留证据路径。

### Phase 7 — 收尾
- 文档（starengine README 的动画 API 用法 + 消费方 features 文档 + vendored/SOURCE.md）。
- 门禁复跑：python tools/check_lang_sync.py、verify_tag_parity.py、verify_vendored_geckolib.py。
- 独立 subagent 对账（不喂结论，只给判据命令），再由用户裁决是否提交/推送。

## 10. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| v5 渲染强依赖 mixin（EntityRenderState 接口注入）| n26 实体不出渲染 | §6 例外机制，只补 2 条 mixin + interface_injections.json |
| 1.20.1 4.8.4 的 mclib 有 Molang 表达式解析的运行时耦合 | forge/fabric 1.20.1 动画解析失败 | 一并 vendor mclib（69 class，重定位）；若仍有反射/字符串引用，按运行期报错点补 |
| facade 重定向后 triggerAnim 语义与上游偏差 | 动画触发不同步 | Phase 3 显式断言；必要时在 consumer 侧补自有 TriggerAnimPayload（消费方已有网络体系，成本低） |
| 上游代码体量大（约 700+ 文件、5–6 万行）| 仓体积与编译时间上升 | 只编本平台目录；不做各线共享避免 MC API 串味；编译时间在 Phase 2 实测记录 |
| 3 条 MC 版本的 vanilla API 差异引发编译风暴 | Phase 2 工期不可控 | 分线推进，每线单独闸门；先 1.20.1（两平台同源）验证方法，再复制到 1.21.1 / 26.1.2 |

回滚：git revert starengine 侧提交 → publishToMavenLocal 重出 26.4-SNAPSHOT.1 → 消费方恢复 4 处依赖行 / 4 处依赖块 / 12 个 java import 与 pins（用 tools/rollback_geckolib_vendor.sh 固化，避免手改）。

## 11. 需要用户拍板的三个点

1. mixin 例外额度：是否接受【默认零 mixin，破了才按最小集补】（n26 大概率要补 2 条）。
2. mclib 是否一起 vendor（1.20.1 必须，否则 Molang 断裂）——预期是【必须】，确认即可。
3. 工期与铺开顺序：按现状这是 3–5 个工作会话的工程量（Phase 2 占大头）；是否先只做 1.21.1 一条线跑通再铺开（推荐）。

## 12. 执行顺序建议（若批准）

Phase 0 → 1 → 2(仅 neoforge-1.21.1 单线跑通) → 用户验收 → 2(其余各线) → 3 → 4 → 5 → 6 → 7

## 13. 实施记录（2026-11-03，各线全部落地并实机验证）

> 本文件原为计划稿；本节记录实际执行结果与偏差，供后续接手直接对照实物。

### 13.1 各线状态（全部已过实机）

| 线 | starengine 产物 | vendor 规模 | AT/AW | 实机结论 |
|---|---|---|---|---|
| neoforge-1.21.1 | `starengine-neoforge-1.21.1-26.4-SNAPSHOT.1.jar` 596,834 B / 337 class | geckolib 256 class（v4.9.3 语义，无 mclib） | AT 33 行（Mojmap） | 渲染 + `triggerAnim` 发包 + 与真 GeckoLib 4.9.3 共存全绿 |
| forge-1.20.1 | `starengine-forge-1.20.1-…` 556,949 B / 353 class | geckolib 199 + mclib 69 | AT 15 行（**SRG 名**） | 渲染 + 攻击动画全绿；共存只能静态判定（见 13.5） |
| fabric-1.20.1 | `starengine-fabric-1.20.1-…` 595,011 B / 380 class | geckolib 222 + mclib 69 | `geckolib.aw`（intermediary）+ fabric.mod.json `accessWidener` | 渲染 + 攻击动画全绿（依赖 jar 的 AW 在 dev 也被应用） |
| neoforge-26.1.2 | `starengine-neoforge-26.1.2-…` 758,700 B / 568 条目 | geckolib 385（v5） | AT 43 行 + interface_injections.json + 21 个 mixin | 渲染 + 攻击动画 + 与真 GeckoLib 5.5.2 共存全绿 |

生成器：`F:/MCProject/starengine_next/tools/vendor_geckolib.py`（`--check` 恒定 `VENDOR_CHECK: OK problems=0`），vendored 树在 `starengine_next/vendored/{1.20.1,mclib,26.1.2}`。

### 13.2 v5（26.1.2）的三处硬差异（计划里没写、实测才暴露）

1. **mixin 不能排除**：NeoForge 26.1.2 运行时**没有 interface injection**（`interface_injections.json` 只在构建期由 NFRT+JST 写进 patched MC jar），`implements GeoRenderState` 真正靠 `mixin/client/{EntityRenderState,BlockEntityRenderState}Mixin` 两个 duck mixin。排除它们 ⇒ 第一帧渲染 `IncompatibleClassChangeError: … does not implement the requested interface …GeoRenderState`。
2. **资源根换代**：v5 `GeckoLibResources` 只扫 `assets/<ns>/geckolib/models/*.geo.json` 与 `assets/<ns>/geckolib/animations/*.animation.json`，v4 的 `geo/`、`animations/` 完全不被识别（`Loaded 0 models and 0 animations` + 后续 `NoSuchElementException: List.getLast` in `AnimationTimeline.create`）。消费方 n26 资产已 `git mv` 到新目录，`SykeTentacleModel` 的两个常量改成裸 `"sykestentacle"`。
3. **与真 GeckoLib 5 同装会撞 default 方法**：两边 `GeoRenderState` 都有同签名 `default float getPartialTick()`，两个 duck mixin 都把它加到同一个 `EntityRenderState` 上 ⇒ `IncompatibleClassChangeError: Conflicting default methods: …GeoRenderState.getPartialTick`。修法 = 在 `EntityRenderStateMixin` 补 `@Unique @Override getPartialTick()`，`BlockEntityRenderStateMixin` 再补 `getAnimatableAge()`（类级方法优先于接口 default）。该补丁写在生成器 patches 里（不手改 vendored 树）。

### 13.3 fabric 1.20.1 的两处收口

1. 上游把 `GeckoLibNetwork.registerClientReceiverPackets()` 放在 `example` 包的 ClientListener（vendor 时被排除）⇒ 客户端从未注册收包器，服务端 `triggerAnim` 发出后客户端报 `Unknown custom packed identifier: starengine:entity_anim_trigger_sync`。新增 `fabric-1.20.1/src/main/java/com/merlinkitsune/starengine/StarEngineFabricClient.java`（ClientModInitializer）并在 `fabric.mod.json` 的 `entrypoints` 注册 `"client"`。
2. 改完仍不生效的原因不在代码：消费方 `.gradle/loom-cache/remapped_mods/` 里缓存着**旧 remap 副本**（`starengine-fabric-1.20.1-<hash>/26.4-SNAPSHOT.1`），metadata 里 entrypoints 仍是空。处置 = 把该目录整体移入 `temp/quarantine-loom-remap/` 让 Loom 重建；判据是 `FabricLoader.getModContainer("starengine").getMetadata().getEntrypoints("client")` 非空。

### 13.4 编译/发布链

- fabric 1.20.1 上游用 `javax.annotation.@Nullable`，Loom 不透传 JSR-305 ⇒ `fabric-1.20.1/build.gradle` 补 `compileOnly "com.google.code.findbugs:jsr305:3.0.2"`。
- `fabric-1.20.1/src/main/resources/fabric.mod.json` 必须显式 `"accessWidener": "geckolib.aw"`，否则产物里 aw 头部仍是 `v1 named`、生产会 `IllegalAccessError`（重建后应为 `v1 intermediary`）。
- 26.1.2 的 `neoforge.mods.toml` 需 `[[mixins]] config="starengine.geckolib.mixins.json"`；`build.gradle` 另加 `maven.fabricmc.net` 与 `compileOnly 'net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7'`。
- 消费方依赖解析：Maven local 的 `26.4-SNAPSHOT.1` 会被 Gradle 缓存，改库后需 `--refresh-dependencies`（或让 Gradle 过了 snapshot TTL）才能拿到新 jar。

### 13.5 1.20.1 forge 的共存不可在 dev 验证

把发布版 `geckolib-forge-1.20.1-4.8.4.jar` 丢进 `run/1.20.1/mods` 会在 mod 加载期炸：`NoSuchMethodError: 'net.minecraft.client.Minecraft net.minecraft.client.Minecraft.m_91087_()'`——发布 jar 是 SRG 名，dev/userdev 不 remap `mods/` 里的第三方 jar。这是环境限制，不是我们的冲突；该线共存只能靠静态判据（包名已重定位、mixin 配置名不同、AT 不重叠）+ 生产包实测。

### 13.6 回滚

`rm -rf starengine_next/vendored/26.1.2` 并回退 `tools/vendor_geckolib.py` 的 26.1.2 profile（含 13.2.3 的 mixin 补丁）→ 重新生成 → 重编发布。消费方回滚见 §10。

