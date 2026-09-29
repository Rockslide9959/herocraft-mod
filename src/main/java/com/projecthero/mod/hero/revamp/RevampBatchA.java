package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
import com.projecthero.mod.hero.power.p13.SuperDurabilityHandlers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.revamp.batcha.BatchAEntities;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * v0.13.22 mutation revamp, batch A -- common-side registration for:
 * <ul>
 * <li>01 Super Strength</li>
 * <li>02 Laser Vision</li>
 * <li>03 Flight</li>
 * <li>04 Super Speed</li>
 * <li>12 Super Regeneration</li>
 * <li>13 Super Durability</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file. The abilities themselves
 * live in each power's handler class ({@code hero/power/pNN}); shared helpers in {@code hero/revamp/batcha}.
 */
public final class RevampBatchA {
	private RevampBatchA() {
	}

	private static boolean owns(ServerPlayer p, String key) {
		return ExperimentalPowers.state(p).ownedPowers.contains(key);
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
		BatchAEntities.register();
		registerVisuals();
		registerMeters();

		// Flight's Barrel Roll: half a second of i-frames (the void and /kill still get through).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && FlightHandlers.rolling(p)
					&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
				return false;
			}
			return true;
		});
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}

	private static void registerVisuals() {
		// 01 Super Strength: red veins light up under the skin during Maximum Effort
		MutationVisuals.registerFlag("p01.effort", p -> owns(p, SuperStrengthHandlers.KEY) && SuperStrengthHandlers.maxEffortActive(p));

		// 02 Laser Vision: the eyes always smoulder; brighter the hotter they run, blazing while a beam fires
		MutationVisuals.registerFlag("p02.eyes", p -> owns(p, LaserVisionHandlers.KEY));
		MutationVisuals.registerValue("p02.eye_glow", p -> {
			if (!owns(p, LaserVisionHandlers.KEY)) {
				return 0;
			}
			if (LaserVisionHandlers.firing(p)) {
				return 1.0;
			}
			return 0.35 + 0.45 * Math.min(1.0, BatchA.res(p, LaserVisionHandlers.KEY, "heat") / LaserVisionHandlers.MAX_HEAT);
		});

		// 03 Flight: a sheath of wind streaks at the upper speed tiers and in Sonic Flight
		MutationVisuals.registerFlag("p03.wind", p -> owns(p, FlightHandlers.KEY)
				&& (BatchA.res(p, FlightHandlers.KEY, "speed_tier") >= 1.5f || BatchA.res(p, FlightHandlers.KEY, "sonic_ticks") > 0.5f));
		MutationVisuals.registerValue("p03.speed", p -> {
			if (!owns(p, FlightHandlers.KEY)) {
				return 0;
			}
			return BatchA.res(p, FlightHandlers.KEY, "sonic_ticks") > 0.5f ? 4 : BatchA.res(p, FlightHandlers.KEY, "speed_tier");
		});

		// 04 Super Speed: yellow lightning crackles over the body in Speed Mode, fiercer in Overdrive
		MutationVisuals.registerFlag("p04.crackle", p -> owns(p, SuperSpeedHandlers.KEY)
				&& (SuperSpeedHandlers.speedMode(p) || SuperSpeedHandlers.overdrive(p)));
		MutationVisuals.registerValue("p04.intensity", p -> {
			if (!owns(p, SuperSpeedHandlers.KEY)) {
				return 0;
			}
			return SuperSpeedHandlers.overdrive(p) ? 2 : SuperSpeedHandlers.speedMode(p) ? 1 : 0;
		});

		// 12 Super Regeneration: green veins while regenerating hard, red while Blood Rage runs
		MutationVisuals.registerFlag("p12.regen", p -> owns(p, SuperRegenerationHandlers.KEY)
				&& (ExperimentalPowers.state(p).activeToggles.contains(SuperRegenerationHandlers.KEY + "/regeneration_mode")
						|| BatchA.res(p, SuperRegenerationHandlers.KEY, "surge_left") > 0.5f));
		MutationVisuals.registerValue("p12.surge", p -> owns(p, SuperRegenerationHandlers.KEY)
				&& BatchA.res(p, SuperRegenerationHandlers.KEY, "surge_left") > 0.5f ? 1 : 0);
		MutationVisuals.registerFlag("p12.rage", p -> owns(p, SuperRegenerationHandlers.KEY)
				&& SuperRegenerationHandlers.bloodRaging(p));

		// 13 Super Durability: a thin metallic shell in Tank Mode / Unbreakable, gilded while Unbreakable
		MutationVisuals.registerFlag("p13.steel", p -> owns(p, SuperDurabilityHandlers.KEY)
				&& (SuperDurabilityHandlers.tankMode(p) || SuperDurabilityHandlers.unbreakable(p)));
		MutationVisuals.registerFlag("p13.gold", p -> owns(p, SuperDurabilityHandlers.KEY) && SuperDurabilityHandlers.unbreakable(p));
	}

	private static void registerMeters() {
		// 01 Super Strength: AbilityHud draws this power's bars itself (charged punch / leap / effort / rush) and
		// skips registered meters for it -- see docs/revamp/batch_a.md.

		// 02 Laser Vision
		MutationMeters.register(new Spec(LaserVisionHandlers.KEY, "heat", Kind.BUILD, Style.GAUGE, "Heat",
				LaserVisionHandlers.MAX_HEAT, 0xFFFF6A2A, true, true));
		MutationMeters.register(new Spec(LaserVisionHandlers.KEY, "overheat", Kind.TIMER, Style.METER, "OVERHEATED",
				60f, 0xFFE03030, false, false));
		MutationMeters.register(new Spec(LaserVisionHandlers.KEY, "lance_charge", Kind.BUILD, Style.HAIRLINE, "Piercing Lance",
				100f, 0xFFFFD27A, false, true));
		MutationMeters.register(new Spec(LaserVisionHandlers.KEY, "ult_charge", Kind.BUILD, Style.SLAB, "Maximum Output — charging",
				100f, 0xFFFF3B1F, false, true));

		// 03 Flight
		MutationMeters.register(new Spec(FlightHandlers.KEY, "speed_tier", Kind.BUILD, Style.METER, "Airspeed",
				3f, 0xFF9FD8FF, false, false));
		MutationMeters.register(new Spec(FlightHandlers.KEY, "sonic_ticks", Kind.TIMER, Style.HAIRLINE, "Sonic Flight",
				500f, 0xFFE8F4FF, false, false));
		MutationMeters.register(new Spec(FlightHandlers.KEY, "slip_ticks", Kind.TIMER, Style.HAIRLINE, "Slipstream",
				240f, 0xFFB8E8D8, false, false));

		// 04 Super Speed
		MutationMeters.register(new Spec(SuperSpeedHandlers.KEY, SuperSpeedHandlers.MOMENTUM, Kind.BUILD, Style.GAUGE, "Momentum",
				SuperSpeedHandlers.MAX_MOMENTUM, 0xFFFFD83A, true, true));
		MutationMeters.register(new Spec(SuperSpeedHandlers.KEY, "overdrive_ticks", Kind.TIMER, Style.SLAB, "Overdrive",
				600f, 0xFFFFB020, false, false));
		MutationMeters.register(new Spec(SuperSpeedHandlers.KEY, "vortex_ticks", Kind.TIMER, Style.HAIRLINE, "Vortex",
				160f, 0xFFD8E8FF, false, false));

		// 12 Super Regeneration
		MutationMeters.register(new Spec(SuperRegenerationHandlers.KEY, SuperRegenerationHandlers.ADRENALINE, Kind.BUILD,
				Style.GAUGE, "Adrenaline", SuperRegenerationHandlers.MAX_ADRENALINE, 0xFFE0303A, true, true));
		MutationMeters.register(new Spec(SuperRegenerationHandlers.KEY, "surge_left", Kind.TIMER, Style.HAIRLINE, "Cellular Surge",
				600f, 0xFF6FE08A, false, false));
		MutationMeters.register(new Spec(SuperRegenerationHandlers.KEY, "rage_left", Kind.TIMER, Style.HAIRLINE, "Blood Rage",
				200f, 0xFFB01020, false, false));

		// 13 Super Durability
		MutationMeters.register(new Spec(SuperDurabilityHandlers.KEY, SuperDurabilityHandlers.IMPACT, Kind.BUILD, Style.GAUGE,
				"Impact", SuperDurabilityHandlers.MAX_IMPACT, 0xFFE8A53A, true, true));
		MutationMeters.register(new Spec(SuperDurabilityHandlers.KEY, SuperDurabilityHandlers.GUARD, Kind.RESERVE, Style.METER,
				"Guard", SuperDurabilityHandlers.GUARD_MAX, 0xFF9AA8B8, false, false));
		MutationMeters.register(new Spec(SuperDurabilityHandlers.KEY, "unbreakable_left", Kind.TIMER, Style.HAIRLINE, "Unbreakable",
				300f, 0xFFFFD86A, false, false));
	}
}
