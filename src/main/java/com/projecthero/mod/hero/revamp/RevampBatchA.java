package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
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

		// v0.16.5: Flight's Barrel Roll only dodges projectiles now -- FlightHandlers#damageFactor, read by HeroDamageRules
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}

	private static void registerVisuals() {
		// 01 Super Strength: red veins light up under the skin during Maximum Effort
		MutationVisuals.registerFlag("p01.effort", p -> owns(p, SuperStrengthHandlers.KEY) && SuperStrengthHandlers.maxEffortActive(p));

		// 02 Laser Vision: v0.14.5 -- no eye glow any more; its beam flag lives in v0145/LaserVisionV0145

		// 03 Flight: v0.16.5 rework -- the wind sheath, hover, slipstream, afterburner and carry values live in FlightHandlers
		FlightHandlers.registerVisuals();

		// 04 Super Speed: v0.14.5 -- its after-image trail flags live in v0145.SuperSpeedV0145

		// 12 Super Regeneration: v0.14.5 -- its veins flag lives in v0145.SuperRegenerationV0145

	}

	private static void registerMeters() {
		// 01 Super Strength: AbilityHud draws this power's bars itself (charged punch / leap / effort / rush) and
		// skips registered meters for it -- see docs/revamp/batch_a.md.

		// 02 Laser Vision: v0.14.5 -- its meters live in v0145/LaserVisionV0145

		// 03 Flight: v0.16.5 rework -- the always-on Lift Hairline + the Orbital Drop / Carry timers
		FlightHandlers.registerMeters();

		// 04 Super Speed: v0.14.5 -- meters registered in v0145.SuperSpeedV0145

		// 12 Super Regeneration: v0.14.5 -- no meters (the revive-charge dots are drawn on the HUD's name line)

	}
}
