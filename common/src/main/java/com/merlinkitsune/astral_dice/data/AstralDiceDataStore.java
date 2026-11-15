package com.merlinkitsune.astral_dice.data;

/**
 * 数据包快照的**唯一内存入口**：铁砧（{@link AnvilUpgradeData}）与战利品注入（{@link LootInjectionData}）。
 *
 * <p>两类快照各是一份 {@code static volatile} 的**不可变对象整体替换**，读方永远拿到自洽的一份；
 * 加载前 / 数据包删光时返回安全兜底（铁砧 = 旧硬编码数值，战利品 = 零规则），**不抛异常**。
 *
 * <p>写入只允许来自服务端数据重载（{@code event/AstralDataReloadHandler} 注册的两个 reload listener），
 * 不在别处调用 {@code replace*}。
 */
public final class AstralDiceDataStore {

    private static volatile AnvilUpgradeData anvil = AnvilUpgradeData.fallback();
    private static volatile LootInjectionData loot = LootInjectionData.empty();
    private static volatile boolean anvilLoaded;
    private static volatile boolean lootLoaded;

    private AstralDiceDataStore() {
    }

    /** 当前铁砧快照；未加载 ⇒ {@link AnvilUpgradeData#fallback()}（与旧硬编码逐字一致）。 */
    public static AnvilUpgradeData anvil() {
        return anvil;
    }

    /** 当前战利品注入快照；未加载 ⇒ 空规则。 */
    public static LootInjectionData loot() {
        return loot;
    }

    /** 铁砧数值是否来自数据包（false = 正在用内置兜底）。 */
    public static boolean anvilLoaded() {
        return anvilLoaded;
    }

    /** 战利品规则是否来自数据包。 */
    public static boolean lootLoaded() {
        return lootLoaded;
    }

    public static void replaceAnvil(AnvilUpgradeData data) {
        anvil = data == null ? AnvilUpgradeData.fallback() : data;
        anvilLoaded = data != null;
    }

    public static void replaceLoot(LootInjectionData data) {
        loot = data == null ? LootInjectionData.empty() : data;
        lootLoaded = data != null;
    }

    /** 重载时没找到任何铁砧文件 ⇒ 退回内置兜底数值。 */
    public static void clearAnvil() {
        anvil = AnvilUpgradeData.fallback();
        anvilLoaded = false;
    }

    /** 重载时没找到任何战利品文件 ⇒ 退回零规则。 */
    public static void clearLoot() {
        loot = LootInjectionData.empty();
        lootLoaded = false;
    }
}
