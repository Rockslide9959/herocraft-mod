package com.projecthero.mod.power;

import com.projecthero.mod.config.VersionedConfig;

/**
 * v0.16.1: Thor's tunables, at {@code config/projecthero_thor.json} (loaded through {@link VersionedConfig}, see
 * {@code docs/CONFIGS.md}): the thunderstorm buff ({@link ThorStorm}), the Mjolnir + Stormbreaker dual-wield combo
 * ({@link WeaponCombo}), its Twin Thunder Strike finisher ({@link ThorTwinThunder}) and Thunder Field
 * ({@link ThunderField}).
 */
public final class ThorConfig {
	public static final VersionedConfig<ThorConfig> SPEC = VersionedConfig.builder(ThorConfig.class, "projecthero_thor.json", ThorConfig::new)
			.balance("storm")
			.balance("dualCombo")
			.balance("twinThunder")
			.balance("thunderField")
			.introduce(1, "v0.16.1 Thor storm buff, dual-wield combo, Twin Thunder Strike, Thunder Field")
			.build();

	private static ThorConfig instance = new ThorConfig();

	public Integer configVersion;
	public Storm storm = new Storm();
	public DualCombo dualCombo = new DualCombo();
	public TwinThunder twinThunder = new TwinThunder();
	public ThunderFieldCfg thunderField = new ThunderFieldCfg();

	public static final class Storm {
		/** +30%: every hit Thor deals while it thunders where he stands. */
		public double damageBonus = 0.30;
		/** false: indoors / under a roof still counts, as long as the biome has rain or snow and it is thundering. */
		public boolean requireSky = false;
	}

	public static final class DualCombo {
		/** Ticks after the swing has recharged to land the next dual-wield step (1 s). */
		public int windowTicks = 20;
		/** The leaping slam's hop (vertical speed); 0 turns the hop off. */
		public double slamHop = 0.42;
	}

	public static final class TwinThunder {
		public int cooldownTicks = 400;
		public double radius = 6.0;
		public double damage = 20.0;
		/** The ring of lightning that follows the slam. */
		public double ringRadius = 6.5;
		public double ringSplash = 2.0;
		public double ringDamage = 8.0;
		public int ringBolts = 8;
		public double knockUp = 0.7;
		public double knockback = 0.9;
	}

	public static final class ThunderFieldCfg {
		public int cooldownTicks = 300;
		public double range = 24.0;
		public double radius = 5.0;
		public int durationTicks = 80;
		public int pulseTicks = 10;
		public double pulseDamage = 4.0;
		/** Slowness amplifier (0 = Slowness I) while standing in the field. */
		public int slowAmplifier = 1;
	}

	public static ThorConfig get() {
		return instance;
	}

	public static void load() {
		instance = SPEC.load();
	}
}
