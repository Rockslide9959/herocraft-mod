package com.projecthero.mod.loot;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: the built-in targets of the hero supplies -- the meters an Energy Cell refills ({@link EnergyCellTargets}),
 * the cooldown systems a Cooldown Stim shortens ({@link CooldownTargets}) and the suit-health systems a Suit Repair Kit
 * mends ({@link SuitRepairTargets}). Order matters for the Energy Cell: the player's Hero-Tier meter comes first, the
 * mutation reserves and the Quirk meter after.
 */
final class SupplyTargetDefaults {
	private SupplyTargetDefaults() {
	}

	static void registerAll() {
		energy();
		cooldowns();
		repairs();
	}

	// ---------------------------------------------------------------- Energy Cell

	private static void energy() {
		E("black_panther", com.projecthero.mod.blackpanther.BlackPanther::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.blackpanther.BlackPanther::kinetic, com.projecthero.mod.blackpanther.BlackPanther::setKinetic,
				p -> com.projecthero.mod.blackpanther.BlackPantherConfig.KINETIC_MAX));
		E("kryptonian", com.projecthero.mod.kryptonian.Kryptonian::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.kryptonian.Kryptonian::solar, com.projecthero.mod.kryptonian.Kryptonian::setSolar,
				p -> com.projecthero.mod.kryptonian.KryptonianConfig.SOLAR_MAX));
		E("nova", com.projecthero.mod.nova.Nova::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.nova.Nova::force, com.projecthero.mod.nova.Nova::setForce,
				p -> com.projecthero.mod.nova.NovaConfig.FORCE_MAX));
		E("saiyan", com.projecthero.mod.saiyan.Saiyan::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.saiyan.Saiyan::ki, com.projecthero.mod.saiyan.Saiyan::setKi, com.projecthero.mod.saiyan.Saiyan::kiMax));
		E("ghost_rider", com.projecthero.mod.ghostrider.GhostRider::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.ghostrider.GhostRider::hellfire, com.projecthero.mod.ghostrider.GhostRider::setHellfire,
				p -> com.projecthero.mod.ghostrider.GhostRiderConfig.HELLFIRE_MAX));
		E("cyborg", com.projecthero.mod.cyborg.Cyborg::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.cyborg.Cyborg::core, com.projecthero.mod.cyborg.Cyborg::setCore,
				p -> com.projecthero.mod.cyborg.CyborgConfig.CORE_MAX));
		E("blue_beetle", com.projecthero.mod.bluebeetle.BlueBeetle::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.bluebeetle.BlueBeetle::energy, com.projecthero.mod.bluebeetle.BlueBeetle::setEnergy,
				p -> com.projecthero.mod.bluebeetle.BlueBeetleConfig.ENERGY_MAX));
		E("stand", com.projecthero.mod.stand.Stands::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.stand.Stands::stamina, com.projecthero.mod.stand.Stands::setStamina,
				p -> com.projecthero.mod.stand.StandConfig.STAMINA_MAX));
		// Hulk: rage only means anything in Hulk form (Banner's is forced to 0)
		E("hulk", com.projecthero.mod.hulk.Hulk::isHulk, EnergyCellTargets.meter(
				com.projecthero.mod.hulk.Hulk::rage, com.projecthero.mod.hulk.Hulk::setRage,
				p -> com.projecthero.mod.hulk.HulkConfig.RAGE_MAX));
		E("moon_knight", com.projecthero.mod.moonknight.MoonKnight::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.moonknight.MoonKnight::vengeance, com.projecthero.mod.moonknight.MoonKnight::setVengeance,
				p -> com.projecthero.mod.moonknight.MoonKnightConfig.VENGEANCE_MAX));
		E("raven", com.projecthero.mod.raven.Raven::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.raven.Raven::energy, com.projecthero.mod.raven.Raven::setEnergy,
				p -> com.projecthero.mod.raven.RavenConfig.ENERGY_MAX));
		E("vision", com.projecthero.mod.vision.Vision::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.vision.Vision::energy, com.projecthero.mod.vision.Vision::setEnergy,
				p -> com.projecthero.mod.vision.VisionConfig.ENERGY_MAX));
		E("starfire", com.projecthero.mod.starfire.Starfire::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.starfire.Starfire::starbolt, com.projecthero.mod.starfire.Starfire::setStarbolt,
				p -> com.projecthero.mod.starfire.StarfireConfig.STARBOLT_MAX));
		E("shazam", com.projecthero.mod.shazam.Shazam::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.shazam.Shazam::lightning, com.projecthero.mod.shazam.Shazam::setLightning,
				p -> com.projecthero.mod.shazam.ShazamConfig.LIGHTNING_MAX));
		E("spin", com.projecthero.mod.spin.Spin::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.spin.Spin::spin, com.projecthero.mod.spin.Spin::setSpin,
				p -> com.projecthero.mod.spin.SpinConfig.SPIN_MAX));
		E("beast_boy", com.projecthero.mod.beastboy.BeastBoy::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.beastboy.BeastBoy::stamina, com.projecthero.mod.beastboy.BeastBoy::setStamina,
				p -> com.projecthero.mod.beastboy.BeastBoyConfig.STAMINA_MAX));
		E("falcon", com.projecthero.mod.falcon.Falcon::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.falcon.Falcon::thrust, com.projecthero.mod.falcon.Falcon::setThrust,
				p -> com.projecthero.mod.falcon.FalconConfig.THRUST_MAX));
		E("silver_surfer", com.projecthero.mod.silversurfer.SilverSurfer::hasPower, EnergyCellTargets.meter(
				com.projecthero.mod.silversurfer.SilverSurfer::cosmic, com.projecthero.mod.silversurfer.SilverSurfer::setCosmic,
				p -> com.projecthero.mod.silversurfer.SilverSurferConfig.COSMIC_MAX));
		E("speed_force", com.projecthero.mod.speedforce.SpeedForceSpeedster::hasPower, EnergyCellTargets.adder(
				com.projecthero.mod.speedforce.SpeedForceSpeedster::meter, com.projecthero.mod.speedforce.SpeedForceSpeedster::addMeter,
				com.projecthero.mod.speedforce.SpeedForceSpeedster::meterMax));
		E("all_might", com.projecthero.mod.allmight.AllMight::hasPower, EnergyCellTargets.meter(
				p -> com.projecthero.mod.allmight.AllMight.timeLeft(p), com.projecthero.mod.allmight.AllMight::setTimer,
				p -> com.projecthero.mod.allmight.AllMight.timerMax()));
		E("green_lantern", com.projecthero.mod.greenlantern.GreenLantern::hasPower, EnergyCellTargets.adder(
				p -> com.projecthero.mod.greenlantern.GreenLantern.state(p).ringCharge,
				com.projecthero.mod.greenlantern.GreenLanternEnergy::addCharge,
				com.projecthero.mod.greenlantern.GreenLanternRanks::maxCharge));
		E("spider_man", com.projecthero.mod.spider.SpiderMan::hasPower, EnergyCellTargets.meter(
				p -> com.projecthero.mod.spider.SpiderMan.state(p).webReserve, com.projecthero.mod.spider.SpiderMan::setWebReserve,
				com.projecthero.mod.spider.SpiderWebReserve::maxFor));
		E("thor", com.projecthero.mod.power.ThorPassives::hasPowerOfThor, EnergyCellTargets.meter(
				com.projecthero.mod.power.StormEnergy::get, com.projecthero.mod.power.StormEnergy::set,
				p -> com.projecthero.mod.power.StormEnergy.MAX));
		E("max_steel", com.projecthero.mod.maxsteel.MaxSteel::hasPower, EnergyCellTargets.meter(
				p -> com.projecthero.mod.maxsteel.MaxSteel.state(p).turboEnergy,
				(p, v) -> {
					var s = com.projecthero.mod.maxsteel.MaxSteel.state(p).copy();
					s.turboEnergy = v;
					com.projecthero.mod.maxsteel.MaxSteel.save(p, s);
				},
				p -> com.projecthero.mod.maxsteel.MaxSteelConfig.MAX_TURBO_ENERGY));
		E("titan_shifter", com.projecthero.mod.titanshifter.TitanShifter::isShifter, EnergyCellTargets.meter(
				com.projecthero.mod.titanshifter.TitanShifter::energy,
				(p, v) -> {
					var s = com.projecthero.mod.titanshifter.TitanShifter.state(p).copy();
					s.energy = v;
					p.setAttached(ModAttachments.TITAN_SHIFTER_STATE, s);
				},
				p -> (float) com.projecthero.mod.titanshifter.TitanShifterConfig.energy().max));
		E("symbiote", com.projecthero.mod.symbiote.Symbiote::hasSymbiote, EnergyCellTargets.meter(
				com.projecthero.mod.symbiote.SymbioteVitalsManager::biomass,
				(p, v) -> {
					var s = com.projecthero.mod.symbiote.SymbioteVitalsManager.vitals(p).copy();
					s.hp = v;
					s.broken = false;
					p.setAttached(ModAttachments.SYMBIOTE_VITALS, s);
				},
				p -> com.projecthero.mod.symbiote.SymbioteVitalsManager.MAX_HP));
		// Omnitrix: tops up the transformation time while transformed
		E("omnitrix", p -> com.projecthero.mod.omnitrix.Omnitrix.hasPower(p) && com.projecthero.mod.omnitrix.Omnitrix.timeLeft(p) > 0,
				EnergyCellTargets.meter(com.projecthero.mod.omnitrix.Omnitrix::timeLeft,
						(p, v) -> com.projecthero.mod.omnitrix.Omnitrix.setTimeLeft(p, Math.round(v)),
						p -> com.projecthero.mod.omnitrix.OmnitrixConfig.TRANSFORM_TICKS));
		// HeroPack mutations: the reserves of the active power with a known max (more via registerExperimental)
		registerExperimental("psi", 1000f);
		registerExperimental("ecell", 1000f);
		registerExperimental("flight", 100f);
		registerExperimental("phase", 100f);
		registerExperimental("sparkle", 100f);
		// Quirks: a "builds" meter (heat, strain...) is eased by 40, a "spends" meter refilled by 40
		E("quirk", com.projecthero.mod.quirk.Quirks::hasPower, (p, fraction) -> {
			var spec = com.projecthero.mod.quirk.meter.QuirkMeters.spec(com.projecthero.mod.quirk.Quirks.quirk(p));
			if (spec == null) {
				return false;
			}
			boolean builds = spec.channelDirection == com.projecthero.mod.quirk.meter.QuirkMeterSpec.Direction.BUILDS;
			float before = com.projecthero.mod.quirk.Quirks.meter(p);
			if (builds ? before <= 0.5f : before >= 99.5f) {
				return false;
			}
			com.projecthero.mod.quirk.Quirks.addMeter(p, (builds ? -100f : 100f) * fraction);
			return true;
		});
	}

	/**
	 * Registers an Energy Cell target for a HeroPack mutation reserve ({@code ExperimentalPowers} resource
	 * {@code <activePower>/<name>}) with a known max. Applies only while that resource exists for the active power.
	 */
	static void registerExperimental(String resource, float max) {
		E("experimental/" + resource, p -> {
			var power = com.projecthero.mod.hero.ExperimentalPowers.getActive(p);
			return power != null && com.projecthero.mod.hero.ExperimentalPowers.state(p).resources.containsKey(power.key() + "/" + resource);
		}, EnergyCellTargets.meter(
				p -> com.projecthero.mod.hero.ExperimentalPowers.getResource(p, com.projecthero.mod.hero.ExperimentalPowers.getActive(p), resource),
				(p, v) -> com.projecthero.mod.hero.ExperimentalPowers.setResource(p, com.projecthero.mod.hero.ExperimentalPowers.getActive(p), resource, v, max),
				p -> max));
	}

	private static void E(String id, java.util.function.Predicate<ServerPlayer> applies, EnergyCellTargets.Refill refill) {
		EnergyCellTargets.register(id, applies, refill);
	}

	// ---------------------------------------------------------------- Cooldown Stim

	private static void cooldowns() {
		C("experimental", ModAttachments.EXPERIMENTAL_STATE, com.projecthero.mod.hero.data.ExperimentalState::copy, s -> s.abilityReadyAt);
		C("all_might", ModAttachments.ALL_MIGHT_STATE, com.projecthero.mod.allmight.data.AllMightState::copy, s -> s.abilityReadyAt);
		C("beast_boy", ModAttachments.BEAST_BOY_STATE, com.projecthero.mod.beastboy.data.BeastBoyState::copy, s -> s.readyAt);
		C("black_panther", ModAttachments.BLACK_PANTHER_STATE, com.projecthero.mod.blackpanther.data.BlackPantherState::copy, s -> s.abilityReadyAt);
		C("blue_beetle", ModAttachments.BLUE_BEETLE_STATE, com.projecthero.mod.bluebeetle.data.BlueBeetleState::copy, s -> s.abilityReadyAt);
		C("cyborg", ModAttachments.CYBORG_STATE, com.projecthero.mod.cyborg.data.CyborgState::copy, s -> s.abilityReadyAt);
		C("doctor_fate", com.projecthero.mod.doctorfate.data.FateAttachments.STATE, com.projecthero.mod.doctorfate.data.FateState::copy, s -> s.abilityReadyAt);
		C("falcon", ModAttachments.FALCON_STATE, com.projecthero.mod.falcon.data.FalconState::copy, s -> s.abilityReadyAt);
		C("ghost_rider", ModAttachments.GHOST_RIDER_STATE, com.projecthero.mod.ghostrider.data.GhostRiderState::copy, s -> s.abilityReadyAt);
		C("green_lantern", ModAttachments.GREEN_LANTERN_STATE, com.projecthero.mod.greenlantern.data.GreenLanternState::copy, s -> s.abilityReadyAt);
		C("hulk", ModAttachments.HULK_STATE, com.projecthero.mod.hulk.data.HulkState::copy, s -> s.abilityReadyAt);
		C("iron_man", ModAttachments.TONY_STARK_STATE, com.projecthero.mod.ironman.data.TonyStarkState::copy, s -> s.abilityReadyAt);
		C("kryptonian", ModAttachments.KRYPTONIAN_STATE, com.projecthero.mod.kryptonian.data.KryptonianState::copy, s -> s.abilityReadyAt);
		C("max_steel", ModAttachments.MAX_STEEL_STATE, com.projecthero.mod.maxsteel.data.MaxSteelState::copy, s -> s.abilityReadyAt);
		C("miraculous", ModAttachments.MIRACULOUS_STATE, com.projecthero.mod.miraculous.data.MiraculousState::copy, s -> s.abilityReadyAt);
		C("moon_knight", ModAttachments.MOON_KNIGHT_STATE, com.projecthero.mod.moonknight.data.MoonKnightState::copy, s -> s.abilityReadyAt);
		C("nova", ModAttachments.NOVA_STATE, com.projecthero.mod.nova.data.NovaState::copy, s -> s.abilityReadyAt);
		C("punisher", ModAttachments.PUNISHER_STATE, com.projecthero.mod.punisher.data.PunisherState::copy, s -> s.abilityReadyAt);
		C("quirk", ModAttachments.QUIRK_STATE, com.projecthero.mod.quirk.data.QuirkState::copy, s -> s.abilityReadyAt);
		C("raven", ModAttachments.RAVEN_STATE, com.projecthero.mod.raven.data.RavenState::copy, s -> s.abilityReadyAt);
		C("saiyan", ModAttachments.SAIYAN_STATE, com.projecthero.mod.saiyan.data.SaiyanState::copy, s -> s.abilityReadyAt);
		C("shazam", ModAttachments.SHAZAM_STATE, com.projecthero.mod.shazam.data.ShazamState::copy, s -> s.abilityReadyAt);
		C("silver_surfer", com.projecthero.mod.silversurfer.SilverSurferAttachments.STATE, com.projecthero.mod.silversurfer.data.SilverSurferState::copy, s -> s.abilityReadyAt);
		C("speed_force", com.projecthero.mod.speedforce.SpeedForceAttachments.STATE, com.projecthero.mod.speedforce.data.SpeedForceState::copy, s -> s.abilityReadyAt);
		C("spider_man", ModAttachments.SPIDER_MAN_STATE, com.projecthero.mod.spider.data.SpiderManState::copy, s -> s.abilityReadyAt);
		C("spin", ModAttachments.SPIN_STATE, com.projecthero.mod.spin.data.SpinState::copy, s -> s.abilityReadyAt);
		C("stand", ModAttachments.STAND_STATE, com.projecthero.mod.stand.data.StandState::copy, s -> s.abilityReadyAt);
		C("starfire", ModAttachments.STARFIRE_STATE, com.projecthero.mod.starfire.data.StarfireState::copy, s -> s.abilityReadyAt);
		C("super_soldier", ModAttachments.SUPER_SOLDIER_STATE, com.projecthero.mod.supersoldier.data.SuperSoldierState::copy, s -> s.abilityReadyAt);
		C("titan_shifter", ModAttachments.TITAN_SHIFTER_STATE, com.projecthero.mod.titanshifter.data.TitanShifterState::copy, s -> s.abilityReadyAt);
		C("undead", ModAttachments.UNDEAD_STATE, com.projecthero.mod.undead.data.UndeadState::copy, s -> s.abilityReadyAt);
		C("vision", com.projecthero.mod.vision.data.VisionAttachments.STATE, com.projecthero.mod.vision.data.VisionState::copy, s -> s.abilityReadyAt);
		C("wolverine", ModAttachments.WOLVERINE_STATE, com.projecthero.mod.wolverine.data.WolverineState::copy, s -> s.abilityReadyAt);

		// Thor: an EnumMap behind a small API -- rebuild it with the halved remainders
		CooldownTargets.register("thor", (p, factor) -> {
			var old = p.getAttached(ModAttachments.COOLDOWNS);
			if (old == null) {
				return 0;
			}
			long now = p.level().getGameTime();
			var fresh = new com.projecthero.mod.attachment.CooldownState();
			int n = 0;
			for (var a : com.projecthero.mod.power.ThorAbility.values()) {
				int r = old.remainingTicks(a, now);
				if (r > 0) {
					int s = (int) Math.ceil(r * factor);
					fresh.trigger(a, now, s);
					if (s < r) {
						n++;
					}
				}
			}
			if (n > 0) {
				p.setAttached(ModAttachments.COOLDOWNS, fresh);
			}
			return n;
		});

		// Symbiote: six slot cooldowns in a list
		CooldownTargets.register("symbiote", (p, factor) -> {
			var st = p.getAttached(ModAttachments.SYMBIOTE_STATE);
			if (st == null || st.abilityCooldowns == null) {
				return 0;
			}
			long now = p.level().getGameTime();
			var c = st.copy();
			List<Long> list = new ArrayList<>(c.abilityCooldowns);
			int n = 0;
			for (int i = 0; i < list.size(); i++) {
				Long v = list.get(i);
				if (v != null && CooldownTargets.isLive(now, v)) {
					list.set(i, CooldownTargets.scaled(now, v, factor));
					n++;
				}
			}
			if (n > 0) {
				c.abilityCooldowns = list;
				p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			}
			return n;
		});

		// Omnitrix: the alien move timers ("<alien>.<move>" only -- the map also holds non-time values) + the recharge
		CooldownTargets.register("omnitrix", (p, factor) -> {
			if (!com.projecthero.mod.omnitrix.Omnitrix.hasPower(p)) {
				return 0;
			}
			long now = p.level().getGameTime();
			var c = com.projecthero.mod.omnitrix.Omnitrix.state(p).copy();
			int n = 0;
			for (var e : c.timers.entrySet()) {
				String key = e.getKey();
				int dot = key.indexOf('.');
				if (dot <= 0 || e.getValue() == null || !CooldownTargets.isLive(now, e.getValue())) {
					continue;
				}
				var kit = com.projecthero.mod.omnitrix.alien.Aliens.kit(key.substring(0, dot));
				if (kit == null) {
					continue;
				}
				boolean isMove = false;
				for (var m : kit.moves()) {
					if (kit.key(m.id()).equals(key)) {
						isMove = true;
						break;
					}
				}
				if (isMove) {
					e.setValue(CooldownTargets.scaled(now, e.getValue(), factor));
					n++;
				}
			}
			if (n > 0) {
				com.projecthero.mod.omnitrix.Omnitrix.save(p, c);
			}
			int recharge = com.projecthero.mod.omnitrix.Omnitrix.rechargeLeft(p);
			if (recharge > 0) {
				com.projecthero.mod.omnitrix.Omnitrix.setRecharge(p, (int) Math.ceil(recharge * factor));
				n++;
			}
			return n;
		});
	}

	private static <S> void C(String id, net.fabricmc.fabric.api.attachment.v1.AttachmentType<S> type, java.util.function.UnaryOperator<S> copy,
			java.util.function.Function<S, java.util.Map<String, Long>> map) {
		CooldownTargets.register(id, CooldownTargets.readyAtMap(type, copy, map));
	}

	// ---------------------------------------------------------------- Suit Repair Kit

	private static void repairs() {
		// Iron Man: the worn suit's integrity (the suit's real "durability" -- the pieces have none)
		SuitRepairTargets.register("iron_man", (p, fraction) -> {
			String suit = com.projecthero.mod.ironman.IronManArmor.wornSuitId(p);
			if (suit == null) {
				return false;
			}
			float max = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity(suit);
			float cur = com.projecthero.mod.ironman.IronManEnergy.integrity(p, suit);
			if (max <= 0f || cur >= max * 0.995f) {
				return false;
			}
			com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, suit, Math.min(max, cur + max * fraction));
			return true;
		});
	}
}
