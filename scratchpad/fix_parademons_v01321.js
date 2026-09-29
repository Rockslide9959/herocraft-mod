// v0.13.21: Parademons back to their v0.13.18 strength (config v3 + migration, tests, docs).
const fs=require('fs');
function edit(f, pairs){let raw=fs.readFileSync(f,'utf8');const crlf=raw.includes('\r\n');let s=raw.replace(/\r\n/g,'\n');
 for(const [a,b] of pairs){ if(!s.includes(a)) throw new Error(f+': not found: '+a.slice(0,80)); s=s.replace(a,b);} fs.writeFileSync(f,crlf?s.replace(/\n/g,'\r\n'):s);}
const C='src/main/java/com/projecthero/mod/darkseid/DarkseidConfig.java';
edit(C,[
[` * and Mother Boxes coming back online during the fight. {@link #load} moves a v1
 * file's changed values to the new defaults; everything else in the file is kept.
 */`,` * and Mother Boxes coming back online during the fight. {@link #load} moves a v1
 * file's changed values to the new defaults; everything else in the file is kept.
 *
 * <p>v0.13.21: version 3 -- Parademons back to their v0.13.18 strength (the +20% health / +15% damage is gone; the
 * five waves and the gunners' strafing stay). A v2 file's Parademon stats move to the restored defaults.
 */`],
['private static final int CONFIG_VERSION = 2;','private static final int CONFIG_VERSION = 3;'],
[`		// v0.13.19: +20% health, +15% damage (v0.13.18: 30/6, 24/5, 60/10, 100/15)
		public double standardHealth = 36.0;
		public double standardDamage = 6.9;
		public double rangedHealth = 28.8;
		public float rangedBoltDamage = 5.75f;
		public double eliteHealth = 72.0;
		public double eliteDamage = 11.5;
		public double bruteHealth = 120.0;
		public double bruteDamage = 17.25;`,`		// v0.13.21: back to the v0.13.18 numbers (v0.13.19-20 ran +20% health / +15% damage: 36/6.9, 28.8/5.75,
		// 72/11.5, 120/17.25)
		public double standardHealth = 30.0;
		public double standardDamage = 6.0;
		public double rangedHealth = 24.0;
		public float rangedBoltDamage = 5.0f;
		public double eliteHealth = 60.0;
		public double eliteDamage = 10.0;
		public double bruteHealth = 100.0;
		public double bruteDamage = 15.0;`],
[`		if (c.parademons != null) {
			Parademons d = new Parademons();
			c.parademons.standardHealth = d.standardHealth;`,`		migrateParademonStats(c);
	}

	/**
	 * v2 -> v3 (v0.13.21): the Parademons go back to their v0.13.18 stats; nothing else in the file changes.
	 * Package-visible for the gametest.
	 */
	static void migrateToV3(DarkseidConfig c) {
		ProjectHeroMod.LOGGER.info("[ProjectHero] Darkseid Raid config v{} -> v{}: v0.13.21 Parademons back to their"
				+ " original strength", c.configVersion, CONFIG_VERSION);
		migrateParademonStats(c);
	}

	private static void migrateParademonStats(DarkseidConfig c) {
		if (c.parademons != null) {
			Parademons d = new Parademons();
			c.parademons.standardHealth = d.standardHealth;`],
[`		if (c.configVersion != null && c.configVersion < 2) {
			migrateToV2(c);
			c.configVersion = CONFIG_VERSION;
		}
		return c;`,`		if (c.configVersion != null && c.configVersion < 2) {
			migrateToV2(c);
			c.configVersion = CONFIG_VERSION;
		} else if (c.configVersion != null && c.configVersion < 3) {
			migrateToV3(c);
			c.configVersion = CONFIG_VERSION;
		}
		return c;`],
[`			} else if (instance.configVersion < 2) {
				migrateToV2(instance);
				instance.configVersion = CONFIG_VERSION;
			}`,`			} else if (instance.configVersion < 2) {
				migrateToV2(instance);
				instance.configVersion = CONFIG_VERSION;
			} else if (instance.configVersion < 3) {
				migrateToV3(instance);
				instance.configVersion = CONFIG_VERSION;
			}`],
]);
const T='src/gametest/java/com/projecthero/mod/gametest/DarkseidRaidGameTests.java';
edit(T,[
[`		helper.assertTrue(c.configVersion == 2, "migrated to v2, got " + c.configVersion);`,`		helper.assertTrue(c.configVersion == 3, "migrated to v3, got " + c.configVersion);`],
[`		helper.assertTrue(Math.abs(c.parademons.standardHealth - 36.0) < 1e-6 && Math.abs(c.parademons.bruteDamage - 17.25) < 1e-6,
				"Parademons +20% health / +15% damage");`,`		helper.assertTrue(Math.abs(c.parademons.standardHealth - 30.0) < 1e-6 && Math.abs(c.parademons.bruteDamage - 15.0) < 1e-6,
				"Parademons at their (v0.13.21 restored) original strength");`],
[`	@GameTest(template = EMPTY_STRUCTURE)
	public void parademonsAreTougherThanBefore(GameTestHelper helper) {
		DarkseidConfig.Parademons cfg = DarkseidConfig.parademons();
		helper.assertTrue(cfg.standardHealth >= 30.0 * 1.2 - 1e-6 && cfg.eliteHealth >= 60.0 * 1.2 - 1e-6
				&& cfg.bruteHealth >= 100.0 * 1.2 - 1e-6 && cfg.rangedHealth >= 24.0 * 1.2 - 1e-6, "+20% health");
		helper.assertTrue(cfg.standardDamage >= 6.0 * 1.15 - 1e-6 && cfg.eliteDamage >= 10.0 * 1.15 - 1e-6
				&& cfg.bruteDamage >= 15.0 * 1.15 - 1e-6 && cfg.rangedBoltDamage >= 5.0f * 1.15f - 1e-4, "+15% damage");`,`	@GameTest(template = EMPTY_STRUCTURE)
	public void aVersionTwoConfigFileGetsTheOriginalParademons(GameTestHelper helper) {
		DarkseidConfig c = DarkseidConfig.migrateForTest("{\\"configVersion\\":2,"
				+ "\\"raid\\":{\\"enemyCap\\":34,\\"wave1Standard\\":11},"
				+ "\\"parademons\\":{\\"standardHealth\\":36.0,\\"standardDamage\\":6.9,\\"bruteHealth\\":120.0,\\"rangedBoltDamage\\":5.75}}");
		helper.assertTrue(c.configVersion == 3, "migrated to v3, got " + c.configVersion);
		helper.assertTrue(Math.abs(c.parademons.standardHealth - 30.0) < 1e-6 && Math.abs(c.parademons.standardDamage - 6.0) < 1e-6
				&& Math.abs(c.parademons.bruteHealth - 100.0) < 1e-6 && Math.abs(c.parademons.rangedBoltDamage - 5.0f) < 1e-6,
				"Parademon stats back to v0.13.18");
		helper.assertTrue(c.raid.enemyCap == 34 && c.raid.wave1Standard == 11, "wave numbers untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void parademonsAreBackToTheirOriginalStrength(GameTestHelper helper) {
		DarkseidConfig.Parademons cfg = DarkseidConfig.parademons();
		helper.assertTrue(cfg.standardHealth == 30.0 && cfg.eliteHealth == 60.0 && cfg.bruteHealth == 100.0
				&& cfg.rangedHealth == 24.0, "v0.13.18 health");
		helper.assertTrue(cfg.standardDamage == 6.0 && cfg.eliteDamage == 10.0 && cfg.bruteDamage == 15.0
				&& cfg.rangedBoltDamage == 5.0f, "v0.13.18 damage");`],
]);
edit('docs/DARKSEID_RAID_REFERENCE.md',[
[`**Parademons.** +20% health / +15% damage (standard 36 HP / 6.9, ranged 28.8 HP / 5.75 bolt, elite 72 / 11.5, brute
120 / 17.25).`,`**Parademons.** v0.13.21: back to the v0.13.18 stats (standard 30 HP / 6, ranged 24 HP / 5 bolt, elite 60 / 10, brute
100 / 15; v0.13.19-20 ran +20% health / +15% damage, config v3 migrates a v2 file).`],
]);
console.log('ok');
