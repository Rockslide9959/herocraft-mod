package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.ability.IronManComicState.AreaKind;
import com.projecthero.mod.ironman.ability.IronManComicState.HoldKind;
import com.projecthero.mod.ironman.ability.IronManComicState.Rt;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 COMIC-ACCURATE IRON MAN REWORK: every move of the nine comic kits ({@link IronManComicKit}). Numbers come from
 * the kit table ({@code move.damage()} is what lands; the Hydro x1.5 underwater and the Hulkbuster x1.5 on bosses / the
 * Hulk are applied once in {@code IronManDamage}). No energy anywhere -- a move only needs its cooldown and integrity.
 *
 * <p>{@link #fire} is the single dispatch: it returns {@code true} when the move actually went off (the router then
 * starts its cooldown). TAP moves only ever see the press; HOLD moves see the press and the release.
 */
public final class IronManComicMoves {
	/** The Hulkbuster's Hydraulic Brace lets 30% of a hit through (70% reduction). */
	public static final float BRACE_FACTOR = 0.3f;
	public static final int BRACE_TICKS = 5 * 20;
	/** The Mark 3 / Mark 6 Energy Shields: 75% / 80% of every hit stopped, 6 s. */
	public static final float MK3_SHIELD_FACTOR = 0.25f;
	public static final float MK6_SHIELD_FACTOR = 0.2f;
	public static final int SHIELD_TICKS = 6 * 20;
	public static final int CLOAK_TICKS = 10 * 20;
	public static final int CHAMELEON_TICKS = 6 * 20;
	public static final int GLIDE_TICKS = 10 * 20;
	/** The Replacement Pod brings back this share of the Hulkbuster's max integrity. */
	public static final float POD_REPAIR_FRACTION = 0.30f;
	public static final int POD_DELAY_TICKS = 20; // the Hulkbuster replacement_pod clip's clamp tick
	/** JARVIS-scanned enemies take +25% for this long. */
	public static final int SCAN_TICKS = 15 * 20;

	private IronManComicMoves() {
	}

	/**
	 * Moves that still work on a disabled (0 integrity) suit: the Hulkbuster's Replacement Pod, and (v0.16.7 (user)) its C
	 * key -- the hold is Sentry Mode, the way out of a wrecked suit; the tap (the brace) refuses on its own.
	 */
	static boolean worksWhileDisabled(IronManComicKit.Move m) {
		return "mk8_replacement_pod".equals(m.id()) || "mk8_hydraulic_brace".equals(m.id());
	}

	/**
	 * v0.16.7 (user) Era II energy: moves the router never refuses for energy -- the Replacement Pod (the field repair, it
	 * must work on a dead reactor too) and the Hulkbuster's C (its hold is Sentry Mode, free; its tap -- the brace -- checks
	 * and pays its own {@link #BRACE_ENERGY}).
	 */
	static boolean skipsEnergyGate(IronManComicKit.Move m) {
		return worksWhileDisabled(m) || "mk8_hydraulic_brace".equals(m.id()) || "mk6_energy_shield".equals(m.id());
	}

	/** v0.16.7 (user): the Silver Centurion's C -- tap = Energy Shield, held 1 s = Overload Burst (own cooldowns / energy). */
	public static final int SHIELD_COOLDOWN_TICKS = 15 * 20;
	public static final float SHIELD_ENERGY = 25f;
	public static final int BURST_COOLDOWN_TICKS = 14 * 20;
	public static final float BURST_ENERGY = 30f;
	public static final float BURST_DAMAGE = 30f;

	/** v0.16.7 (user): the Hulkbuster's C held this long (1 s) is Sentry Mode instead of the Hydraulic Brace. */
	public static final int SENTRY_HOLD_TICKS = 20;
	/** v0.16.7 (user): the Hydraulic Brace's own cooldown and energy (kept here: its C key also has to reach Sentry Mode). */
	public static final int BRACE_COOLDOWN_TICKS = 25 * 20;
	public static final float BRACE_ENERGY = 15f;

	/** Dispatch one move edge. Returns true when it went off (start its cooldown). */
	static boolean fire(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		if (!IronManArmor.hasChestplate(p, suit.id()) && !m.id().startsWith("mk1_rocket") && !"mk1_glide".equals(m.id())) {
			if (pressed) {
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			}
			return false;
		}
		// v0.16.7 Era I: every Era I move lives in its suit's own class (ability.era1)
		if (com.projecthero.mod.ironman.ability.era1.IronManEraOne.has(m.id())) {
			return com.projecthero.mod.ironman.ability.era1.IronManEraOne.fire(p, suit, m, pressed);
		}
		// v0.16.7 (user) Era II stage 3: the Mk IX Inferno's kit lives in IronManMark9 (heat sink, stream, bombs, pools)
		if (m.id().startsWith("mk9_")) {
			return IronManMark9.fire(p, suit, m, pressed);
		}
		float d = m.damage();
		if (m.input() == IronManComicKit.Input.HOLD) {
			return switch (m.id()) {
				case "mk1_flamethrower" -> {
					IronManAbilities.flamethrowerKey(p, suit, pressed);
					yield false;
				}
				case "mk2_repulsor" -> {
					if (pressed) {
						// v0.16.7 (user) Era II: the shared repulsor slot keeps its own cooldowns -- the router checked the
						// energy on the press, the shot pays it here
						com.projecthero.mod.ironman.ability.era1.IronManEraOne.payAmount(p, suit, IronManComicKit.energyCost(m.id()));
					}
					IronManAbilities.repulsorSlot(p, suit, pressed);
					yield false;
				}
				case "mk2_tractor_beam" -> tractor(p, pressed, d);
				case "mk8_hydraulic_brace" -> braceOrSentry(p, suit, pressed); // v0.16.7 (user): the Hulkbuster's C
				case "mk6_energy_shield" -> shieldOrBurst(p, suit, pressed); // v0.16.7 (user): the Silver Centurion's C
				default -> false;
			};
		}
		if (!pressed) {
			return false;
		}
		return switch (m.id()) {
			// ---------------- Mark 1
			case "mk1_iron_punch" -> {
				IronManAbilities.strongPunch(p, suit);
				yield true;
			}
			case "mk1_ground_pound" -> groundPound(p);
			case "mk1_smoke_screen" -> smokeScreen(p);
			case "mk1_rocket_hop" -> rocketHop(p);
			case "mk1_glide" -> glide(p);
			case "mk1_shoulder_rocket" -> rocket(p, d, d * 0.7f, 2.5f, 0, 0f);
			case "mk1_cluster_rocket" -> rocket(p, d, d * 0.6f, 1.6f, 3, d);
			case "mk1_electromagnet" -> electromagnet(p, 14.0, true);
			case "mk1_magnetic_repel" -> repel(p, 7.0, 1.8, d);
			// ---------------- Mark 1 V2
			case "mk1v2_palm_repulsor" -> {
				IronManComicFx.repulsor(p, d, 30.0, IronManComicFx.BEAM_REPULSOR);
				yield true;
			}
			case "mk1v2_dash" -> IronManDash.start(p, d, 0f, 0);
			case "mk1v2_magnet_pull" -> magnetPull(p, d);
			case "mk1v2_magnet_push" -> pressor(p, 10.0, 35.0, d, 2.0, IronManAbilityFx.COMIC_MAGNET);
			case "mk1v2_rocket_skates" -> skates(p);
			case "mk1v2_spin_kick" -> spinKick(p, d);
			case "mk1v2_lamp_flash" -> lampFlash(p, 12.0, 60.0, 80);
			case "mk1v2_searchlight" -> searchlight(p, 40.0, 14.0, 12 * 20, IronManComicFx.BEAM_LIGHT);
			case "mk1v2_oil_slick" -> oilSlick(p);
			case "mk1v2_ignite_slick" -> igniteSlick(p, d);
			// ---------------- Mark 2
			case "mk2_dash" -> IronManDash.start(p, d, 0f, 0);
			case "mk2_pressor_blast" -> pressor(p, 12.0, 28.0, d, 2.2, IronManAbilityFx.COMIC_MAGNET);
			case "mk2_flares" -> IronManFlares.fire(p, true, 0, d);
			case "mk2_supersonic" -> IronManFlares.supersonicBoost(p, 20 * 20, 0f);
			case "mk2_unibeam" -> unibeam(p, d, 40.0, 1.2, IronManComicFx.BEAM_UNIBEAM);
			case "mk2_unibeam_flash" -> unibeamFlash(p, d);
			case "mk2_highlight" -> {
				IronManAbilities.toggleMobHighlight(p, suit);
				yield true;
			}
			case "mk2_jarvis_scan" -> jarvisScan(p, suit);
			case "mk2_wrist_laser" -> wristLaser(p, d); // v0.16.7 (user)
			// ---------------- Mark 3
			case "mk3_rapid_repulsors" -> rapidRepulsors(p, d, 3);
			case "mk3_dash" -> IronManDash.start(p, d, 0f, 0);
			case "mk3_pulse_bolts" -> pulseBolt(p, d, 20f);
			case "mk3_sonic_clap" -> IronManSonicClap.fire(p, d, 0f, 0);
			case "mk3_micro_missiles" -> missileVolley(p, 4, d, 3, 40.0);
			case "mk3_missile_barrage" -> missileVolley(p, 8, d, 2, 48.0);
			case "mk3_tri_unibeam" -> unibeam(p, d, 40.0, 1.5, IronManComicFx.BEAM_CHARGED);
			case "mk3_unibeam_sweep" -> unibeamSweep(p, d);
			case "mk3_energy_shield" -> energyShield(p, MK3_SHIELD_FACTOR);
			case "mk3_shield_bash" -> shieldBash(p, d);
			// ---------------- Mark 4 HYDRO
			case "mk4_sonar_pulse" -> sonarPulse(p, d);
			case "mk4_sonar_mark" -> searchlight(p, 40.0, 30.0, 20 * 20, IronManComicFx.BEAM_PULSE);
			case "mk4_torpedo" -> torpedo(p, d);
			case "mk4_depth_charge" -> depthCharge(p, d);
			case "mk4_propeller_dash" -> propellerDash(p, d);
			case "mk4_whirlpool" -> whirlpool(p);
			case "mk4_headlamp_beam" -> headlamp(p, d);
			case "mk4_floodlight" -> lampFlash(p, 18.0, 35.0, 100);
			case "mk4_ballast_anchor" -> ballast(p, d);
			case "mk4_pressure_crush" -> pressureCrush(p, d);
			// ---------------- Mark 5 STEALTH
			case "mk5_silenced_repulsor" -> silencedRepulsor(p, d);
			case "mk5_silent_takedown" -> silentTakedown(p, d);
			case "mk5_chameleon_cloak" -> cloak(p, CLOAK_TICKS);
			case "mk5_light_bend_blink" -> blink(p, 10.0);
			case "mk5_sensor_jammer" -> jammer(p);
			case "mk5_emp_pulse" -> emp(p, d);
			case "mk5_low_glow_shot" -> lowGlow(p, d);
			case "mk5_marked_shot" -> markedShot(p);
			case "mk5_holo_decoy" -> decoy(p);
			case "mk5_decoy_swap" -> decoySwap(p);
			// ---------------- Mark 6 SILVER CENTURION
			case "mk6_repulsor", "mk7_repulsor" -> {
				IronManComicFx.repulsor(p, d, 50.0, IronManComicFx.BEAM_REPULSOR);
				yield true;
			}
			case "mk6_dash", "mk7_dash" -> IronManDash.start(p, d, 0f, 0);
			case "mk6_pulse_bolts" -> pulseBolt(p, d, 26f);
			case "mk6_pulse_storm" -> pulseStorm(p, d);
			case "mk6_ricochet" -> ricochet(p, d);
			case "mk6_chameleon" -> cloak(p, CHAMELEON_TICKS);
			case "mk6_heavy_unibeam" -> unibeam(p, d, 44.0, 1.8, IronManComicFx.BEAM_CHARGED);
			case "mk6_unibeam_overcharge" -> overcharge(p, d);
			case "mk6_energy_shield" -> energyShield(p, MK6_SHIELD_FACTOR);
			case "mk6_overload_burst" -> burst360(p, 8.0, d, 2.0, false);
			case "mk6_deploy_blades" -> IronManCenturionBlades.toggle(p); // v0.16.7 (user)
			case "mk6_blade_whirl" -> IronManCenturionBlades.whirl(p, d); // v0.16.7 (user)
			// ---------------- Mark 7 SPACE
			case "mk7_vacuum_missiles" -> vacuumMissiles(p, d);
			case "mk7_orbital_strike" -> orbitalStrike(p, d);
			case "mk7_booster_rockets" -> booster(p);
			case "mk7_meteor_dive" -> meteorDive(p);
			case "mk7_solar_beam" -> solarBeam(p, d);
			case "mk7_solar_flare" -> burst360(p, 10.0, d, 1.4, true);
			case "mk7_gravity_tether" -> tether(p, d);
			case "mk7_zero_g_field" -> zeroG(p);
			case "mk7_singularity" -> singularity(p, d); // v0.16.7 (user)
			// ---------------- Mark 8 HULKBUSTER
			case "mk8_punch_combo" -> punchCombo(p, d);
			case "mk8_rocket_fist" -> rocketFist(p, d);
			case "mk8_pile_driver" -> pileDriver(p, d);
			case "mk8_earthquake_stomp" -> earthquake(p, d);
			case "mk8_grab_pound" -> grab(p, HoldKind.GRAB_POUND, d);
			case "mk8_grab_throw" -> grab(p, HoldKind.GRAB_THROW, d);
			case "mk8_mega_beam" -> megaBeam(p);
			case "mk8_missile_swarm" -> missileVolley(p, 10, d, 1, 56.0, HB_MISSILES_FIRST, IronManAbilityFx.HB_MISSILE_SWARM, HB_MISSILES_TICKS);
			case "mk8_palm_cannon" -> palmCannon(p, d); // v0.16.7 (user)
			case "mk8_replacement_pod" -> replacementPod(p, suit);
			default -> false;
		};
	}

	// ================================================================== MARK 1

	private static boolean groundPound(ServerPlayer p) {
		Vec3 centre = IronManGroundPound.groundBelow(p);
		if (centre == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.ground_pound_no_ground"), true);
			return false;
		}
		IronManComicFx.pose(p, IronManAbilityFx.GROUND_POUND, 14);
		IronManGroundPound.pound(p, centre);
		return true;
	}

	private static boolean smokeScreen(ServerPlayer p) {
		ServerLevel level = IronManComicFx.level(p);
		IronManComicState.area(p, AreaKind.SMOKE, p.position(), 5.0, 8 * 20);
		IronManComicFx.cloud(level, p.position().add(0, 1, 0), ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 30, 1.5, 0.02);
		IronManComicFx.cloud(level, p.position().add(0, 0.5, 0), ParticleTypes.LARGE_SMOKE, 40, 2.0, 0.05);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
		}
		return true;
	}

	private static boolean rocketHop(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		IronManComicFx.launch(p, flat.scale(0.9).add(0, 1.05, 0));
		ServerLevel level = IronManComicFx.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 20, 0.2, 0.05, 0.2, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_LAUNCH, 10);
		IronManComicFx.sound(p, IronManSounds.FLIGHT_BURST, 1.0f, 0.9f);
		return true;
	}

	private static boolean glide(ServerPlayer p) {
		Rt r = IronManComicState.rt(p);
		if (r.glideUntil > p.level().getGameTime()) {
			endGlide(p, r);
			return false;
		}
		if (p.onGround()) {
			// take off into the glide with a small hop
			IronManComicFx.launch(p, p.getDeltaMovement().add(0, 0.7, 0));
		}
		r.glideUntil = p.level().getGameTime() + GLIDE_TICKS;
		IronManComicFx.sound(p, IronManSounds.THRUSTER, 0.7f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.glide_on"), true);
		return true;
	}

	static void tickGlide(ServerPlayer p, Rt r) {
		long age = p.level().getGameTime() - (r.glideUntil - GLIDE_TICKS);
		if (age > 6 && (p.onGround() || p.isInWater())) {
			endGlide(p, r);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
		IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_GLIDE);
		if (p.tickCount % 4 == 0) {
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0e-4) {
				// 8 blocks/s along the look (the Mark 1's flightCruise), the fall held by Slow Falling
				Vec3 v = flat.normalize().scale(0.4);
				IronManComicFx.launch(p, new Vec3(v.x, Math.max(p.getDeltaMovement().y, -0.15), v.z));
			}
			ServerLevel level = IronManComicFx.level(p);
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 3, 0.15, 0.05, 0.15, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY(), p.getZ(), 2, 0.15, 0.05, 0.15, 0.01);
		}
	}

	static void endGlide(ServerPlayer p, Rt r) {
		r.glideUntil = 0L;
		MobEffectInstance sf = p.getEffect(MobEffects.SLOW_FALLING);
		if (sf != null && sf.isAmbient() && !sf.isVisible()) {
			p.removeEffect(MobEffects.SLOW_FALLING);
		}
	}

	/** A shoulder rocket; {@code cluster > 0} splits into that many bomblets of {@code clusterDamage} on impact. */
	private static boolean rocket(ServerPlayer p, float direct, float splash, float radius, int cluster, float clusterDamage) {
		Vec3 from = IronManComicFx.shoulder(p, 0);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 80);
		IronManMissileEntity m = IronManComicFx.missile(p, from.add(dir), dir, 1.4, direct, splash, radius).withBreaksBlocks();
		if (cluster > 0) {
			m.withCluster(cluster, clusterDamage);
		}
		IronManComicFx.spawn(p, m);
		IronManComicFx.pose(p, IronManAbilityFx.ROCKET, 14);
		IronManComicFx.sound(p, IronManSounds.ROCKET_LAUNCH, 1.1f, cluster > 0 ? 0.85f : 1.0f);
		return true;
	}

	/** True for something the electromagnet grabs: anything wearing metal armour, golems, Ultron's robots, Iron Man hardware. */
	static boolean metallic(LivingEntity e) {
		if (e instanceof IronGolem || e instanceof com.projecthero.mod.ultron.entity.UltronRobot) {
			return true;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND }) {
			var item = e.getItemBySlot(slot).getItem();
			if (item instanceof net.minecraft.world.item.ArmorItem a && a.getMaterial().is(net.minecraft.world.item.ArmorMaterials.LEATHER)) {
				continue;
			}
			if (item instanceof net.minecraft.world.item.ArmorItem || item instanceof net.minecraft.world.item.TieredItem
					|| item instanceof net.minecraft.world.item.ShieldItem) {
				return true;
			}
		}
		return false;
	}

	private static boolean electromagnet(ServerPlayer p, double radius, boolean metalOnly) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 to = p.position().add(p.getLookAngle().scale(1.5));
		int n = 0;
		for (LivingEntity e : IronManComicFx.around(p, p.position(), radius)) {
			if (!metalOnly || metallic(e)) {
				IronManComicFx.pull(e, to, 1.4);
				IronManComicFx.line(level, IronManComicFx.palm(p), IronManComicFx.centre(e), ParticleTypes.ELECTRIC_SPARK, 2.0);
				n++;
			}
		}
		n += IronManComicState.pullItems(p, to, radius, 0.9);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_MAGNET, 14);
		IronManComicFx.ring(level, p.position().add(0, 1.0, 0), 2.0, ParticleTypes.ELECTRIC_SPARK, 20);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
		return true;
	}

	static boolean repel(ServerPlayer p, double radius, double strength, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		for (LivingEntity e : IronManComicFx.around(p, p.position(), radius)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, p.position(), strength, 0.45);
		}
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(radius),
				e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile)) {
			Vec3 away = e.position().subtract(p.position()).normalize();
			e.setDeltaMovement(away.scale(1.2).add(0, 0.25, 0));
			e.hurtMarked = true;
		}
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicFx.ring(level, p.position().add(0, 0.8, 0), radius * 0.6, ParticleTypes.ELECTRIC_SPARK, 28);
		IronManComicFx.ring(level, p.position().add(0, 0.8, 0), radius, ParticleTypes.CLOUD, 32);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.6f);
		return true;
	}

	// ================================================================== MARK 1 V2

	private static boolean magnetPull(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicFx.target(p, 24.0);
		ServerLevel level = IronManComicFx.level(p);
		IronManComicState.pullItems(p, p.position(), 10.0, 0.9);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_MAGNET, 14);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.9f);
		if (t == null) {
			return true;
		}
		IronManComicFx.hit(p, t, damage);
		Vec3 to = p.position().add(p.getLookAngle().scale(2.0));
		Vec3 d = to.subtract(t.position());
		double strength = Math.min(2.2, 0.35 + d.length() * 0.12) * (1.0 - Math.min(0.8, t.getAttributeValue(
				net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)));
		t.setDeltaMovement(d.normalize().scale(strength).add(0, 0.35, 0));
		t.hurtMarked = true;
		IronManComicFx.line(level, IronManComicFx.palm(p), IronManComicFx.centre(t), ParticleTypes.ELECTRIC_SPARK, 2.5);
		return true;
	}

	/** A cone shove (magnet push, pressor blast). */
	private static boolean pressor(ServerPlayer p, double range, double halfDeg, float damage, double strength, int pose) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 dir = IronManComicFx.aim(p, range);
		for (LivingEntity e : IronManComicFx.cone(p, eye, dir, range, halfDeg)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, eye, strength, 0.35);
		}
		for (int i = 1; i <= 6; i++) {
			Vec3 c = eye.add(dir.scale(i * range / 6.0));
			double rr = Math.tan(Math.toRadians(halfDeg)) * i * range / 6.0;
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 5, rr * 0.4, rr * 0.4, rr * 0.4, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, rr * 0.4, rr * 0.4, rr * 0.4, 0.05);
		}
		IronManComicFx.pose(p, pose, 12);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 0.9f, 0.8f);
		return true;
	}

	private static boolean skates(ServerPlayer p) {
		Rt r = IronManComicState.rt(p);
		r.skatesUntil = p.level().getGameTime() + 5 * 20;
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5 * 20, 2, true, false, true));
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_LAUNCH, 8);
		IronManComicFx.sound(p, IronManSounds.THRUSTER, 0.8f, 1.4f);
		return true;
	}

	private static boolean spinKick(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		for (LivingEntity e : IronManComicFx.around(p, p.position().add(0, 0.5, 0), 3.5)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, p.position(), 1.4, 0.35);
		}
		IronManComicFx.ring(level, p.position().add(0, 0.4, 0), 2.5, ParticleTypes.FLAME, 24);
		IronManComicFx.ring(level, p.position().add(0, 0.6, 0), 3.0, ParticleTypes.SWEEP_ATTACK, 8);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_SPIN, 12);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
		return true;
	}

	/** A blinding flash in a wide cone (the Mark 1 V2 chest lamp, the Hydro floodlight). */
	static boolean lampFlash(ServerPlayer p, double range, double halfDeg, int blindTicks) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 from = IronManComicFx.chest(p);
		Vec3 dir = p.getLookAngle();
		for (LivingEntity e : IronManComicFx.cone(p, from, dir, range, halfDeg)) {
			IronManComicFx.control(e, MobEffects.BLINDNESS, blindTicks, 0);
			IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			IronManComicFx.control(e, MobEffects.WEAKNESS, blindTicks, 0);
			if (e instanceof Mob m && m.getTarget() == p) {
				m.setTarget(null);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		for (int i = 1; i <= 5; i++) {
			Vec3 c = from.add(dir.scale(i * range / 5.0));
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, i * 0.4, i * 0.3, i * 0.4, 0.01);
		}
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 12);
		IronManComicFx.soundAt(level, from, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
		return true;
	}

	/** A narrow cone that outlines what it finds -- for the wearer only (Searchlight, Sonar Mark). */
	private static boolean searchlight(ServerPlayer p, double range, double halfDeg, int ticks, int beamKind) {
		Vec3 from = IronManComicFx.chest(p);
		Vec3 dir = p.getLookAngle();
		List<LivingEntity> found = IronManComicFx.cone(p, from, dir, range, halfDeg);
		IronManComicState.privateMark(p, found, ticks);
		IronManAbilities.broadcastBeam(p, from, from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, range))), beamKind);
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 10);
		IronManComicFx.sound(p, IronManSounds.SCAN, 0.8f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.marked", found.size()), true);
		return true;
	}

	private static boolean oilSlick(ServerPlayer p) {
		Vec3 at = IronManComicFx.groundAt(p, IronManComicFx.aimPoint(p, 16.0));
		IronManComicState.area(p, AreaKind.SLICK, at, 4.0, 10 * 20);
		ServerLevel level = IronManComicFx.level(p);
		IronManComicFx.line(level, IronManComicFx.palm(p), at, ParticleTypes.SQUID_INK, 2.0);
		IronManComicFx.cloud(level, at.add(0, 0.2, 0), ParticleTypes.SQUID_INK, 30, 1.5, 0.02);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_THROW, 10);
		IronManComicFx.soundAt(level, at, SoundEvents.HONEY_BLOCK_SLIDE, 1.2f, 0.6f);
		return true;
	}

	private static boolean igniteSlick(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		List<IronManComicState.Area> slicks = IronManComicState.areasOf(p, AreaKind.SLICK);
		if (slicks.isEmpty()) {
			// no slick down: spray a short burning one where you aim
			Vec3 at = IronManComicFx.groundAt(p, IronManComicFx.aimPoint(p, 12.0));
			IronManComicState.area(p, AreaKind.BURNING_SLICK, at, 3.0, 4 * 20);
			slicks = List.of();
			IronManComicFx.line(level, IronManComicFx.palm(p), at, ParticleTypes.FLAME, 2.0);
		}
		for (IronManComicState.Area a : slicks) {
			a.kind = AreaKind.BURNING_SLICK;
			a.until = Math.min(a.until, p.level().getGameTime() + 5 * 20);
			for (LivingEntity e : IronManComicFx.around(p, a.centre, a.radius)) {
				IronManComicFx.burn(p, e, damage, 100);
			}
			level.sendParticles(ParticleTypes.FLAME, a.centre.x, a.centre.y + 0.3, a.centre.z, 40, a.radius * 0.5, 0.3, a.radius * 0.5, 0.05);
			IronManComicFx.soundAt(level, a.centre, SoundEvents.FIRECHARGE_USE, 1.2f, 0.8f);
		}
		IronManComicFx.pose(p, IronManAbilityFx.REPULSOR, 10);
		return true;
	}

	// ================================================================== MARK 2

	/** HOLD: lift the target on a beam; release throws it along your aim. */
	private static boolean tractor(ServerPlayer p, boolean pressed, float damage) {
		Rt r = IronManComicState.rt(p);
		if (pressed) {
			LivingEntity t = IronManComicFx.target(p, 24.0);
			if (t == null) {
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
				return false;
			}
			startHold(p, r, HoldKind.TRACTOR, t, 3 * 20);
			IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGE, 0.8f, 0.6f);
			return false;
		}
		if (r.hold != HoldKind.TRACTOR) {
			return false;
		}
		Entity e = p.level().getEntity(r.holdTarget);
		r.hold = null;
		r.holdTarget = -1;
		if (e instanceof LivingEntity t && t.isAlive()) {
			Vec3 look = p.getLookAngle();
			t.setDeltaMovement(look.scale(2.0).add(0, 0.35, 0));
			t.hurtMarked = true;
			t.hasImpulse = true;
			IronManComicFx.hit(p, t, damage);
			r.thrownTarget = t.getId();
			r.thrownUntil = p.level().getGameTime() + 30;
			r.thrownDamage = damage;
			IronManComicFx.pose(p, IronManAbilityFx.COMIC_THROW, 10);
			IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 0.9f, 0.9f);
		}
		return true;
	}

	/** Wrist Laser: the comic gauntlet cutting laser -- 18 + 2 s fire to up to 3 targets in a 32-block line. */
	public static final double WRIST_LASER_RANGE = 32.0;
	public static final int WRIST_LASER_TARGETS = 3;

	private static boolean wristLaser(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 from = IronManComicFx.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), WRIST_LASER_RANGE).normalize();
		List<LivingEntity> hits = IronManComicFx.beam(p, from, dir, WRIST_LASER_RANGE, 0.45, true, IronManComicFx.BEAM_WRIST_LASER, 0f, 0.0);
		int n = 0;
		for (LivingEntity e : hits) {
			if (n++ >= WRIST_LASER_TARGETS) {
				break;
			}
			IronManComicFx.burn(p, e, damage, 40);
			Vec3 c = IronManComicFx.centre(e);
			level.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 8, 0.15, 0.2, 0.15, 0.03);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.1, 0.1, 0.1, 0.2);
		}
		Vec3 end = from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, WRIST_LASER_RANGE)));
		level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(1f, 0.15f, 0.1f), 1.2f), end.x, end.y, end.z, 10, 0.1, 0.1, 0.1, 0.0);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_LASER, 14);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_BLAST, 0.7f, 1.7f);
		IronManComicFx.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 2.0f);
		return true;
	}

	private static boolean unibeam(ServerPlayer p, float damage, double range, double width, int kind) {
		Vec3 from = IronManComicFx.chest(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), range);
		IronManComicFx.beam(p, from, dir, range, width, true, kind, damage, 1.2);
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 16);
		IronManComicFx.sound(p, IronManSounds.UNIBEAM_CHARGE, 1.0f, 1.2f);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 1.1f, 0.7f);
		return true;
	}

	private static boolean unibeamFlash(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 from = IronManComicFx.chest(p);
		for (LivingEntity e : IronManComicFx.cone(p, from, p.getLookAngle(), 8.0, 50.0)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 3);
			IronManComicFx.control(e, MobEffects.WEAKNESS, 60, 1);
			IronManComicFx.control(e, MobEffects.BLINDNESS, 40, 0);
			if (e instanceof Mob m) {
				IronManComicState.stun(m, 50);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0);
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 40, 1.5, 1.0, 1.5, 0.2);
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 10);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 1.0f, 1.4f);
		return true;
	}

	private static boolean jarvisScan(ServerPlayer p, IronManSuit suit) {
		IronManJarvisScan.Report report = IronManJarvisScan.run(p, suit);
		if (report == null) {
			return false;
		}
		Rt r = IronManComicState.rt(p);
		long until = p.level().getGameTime() + SCAN_TICKS;
		List<LivingEntity> hostiles = AbilityHelpers.hostilesAround(p, p.position(), IronManJarvisScan.RADIUS);
		for (LivingEntity e : hostiles) {
			r.scanned.put(e.getId(), until);
		}
		IronManComicState.privateMark(p, hostiles, SCAN_TICKS);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.scanned", hostiles.size())
				.withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	// ================================================================== MARK 3

	private static boolean rapidRepulsors(ServerPlayer p, float damage, int shots) {
		for (int i = 0; i < shots; i++) {
			IronManComicState.later(p, 1 + i * 3, w -> {
				if (IronManComicState.suited(w)) {
					IronManComicFx.repulsor(w, damage, 50.0, IronManComicFx.BEAM_REPULSOR);
				}
			});
		}
		return true;
	}

	/** A pulse bolt: stacks on the target; the third stack detonates for {@code popDamage} around it. */
	private static boolean pulseBolt(ServerPlayer p, float damage, float popDamage) {
		Vec3 from = IronManComicFx.palm(p);
		List<LivingEntity> hits = IronManComicFx.beam(p, from, IronManTargeting.aim(p, from, p.getLookAngle(), 50), 50.0, 0.7, false,
				IronManComicFx.BEAM_PULSE, damage, 0.4);
		IronManComicFx.pose(p, IronManAbilityFx.REPULSOR, 8);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_ZAP, 0.9f, 1.5f);
		if (!hits.isEmpty()) {
			stackPulse(p, hits.get(0), popDamage);
		}
		return true;
	}

	static void stackPulse(ServerPlayer p, LivingEntity e, float popDamage) {
		Rt r = IronManComicState.rt(p);
		long now = p.level().getGameTime();
		int stacks = r.pulseStacks.getOrDefault(e.getId(), 0) + 1;
		ServerLevel level = IronManComicFx.level(p);
		if (stacks >= 3) {
			r.pulseStacks.remove(e.getId());
			r.pulseUntil.remove(e.getId());
			Vec3 c = IronManComicFx.centre(e);
			for (LivingEntity o : IronManComicFx.around(p, c, 3.0)) {
				IronManComicFx.hit(p, o, popDamage);
				IronManComicFx.shove(o, c, 0.8, 0.3);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.DRAGON_BREATH, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.05);
			IronManComicFx.soundAt(level, c, IronManSounds.ENERGY_IMPACT, 1.2f, 0.6f);
		} else {
			r.pulseStacks.put(e.getId(), stacks);
			r.pulseUntil.put(e.getId(), now + 8 * 20);
			level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4 * stacks, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** Homing missiles from alternating shoulders, spread over the front targets. */
	private static boolean missileVolley(ServerPlayer p, int count, float damage, int gapTicks, double range) {
		return missileVolley(p, count, damage, gapTicks, range, 1, IronManAbilityFx.MISSILES, count * gapTicks + 8);
	}

	/** v0.16.1: as above, the first missile leaving {@code firstTick} ticks after the press, playing {@code pose}. */
	private static boolean missileVolley(ServerPlayer p, int count, float damage, int gapTicks, double range, int firstTick, int pose,
			int poseTicks) {
		List<LivingEntity> targets = IronManComicFx.frontTargets(p, range, Math.max(1, Math.min(count, 6)));
		IronManComicFx.pose(p, pose, poseTicks);
		IronManComicFx.sound(p, IronManSounds.MISSILE_POD, 1.0f, 1.0f);
		for (int i = 0; i < count; i++) {
			final int n = i;
			IronManComicState.later(p, firstTick + i * gapTicks, w -> {
				if (!IronManComicState.suited(w)) {
					return;
				}
				Vec3 from = IronManComicFx.shoulder(w, n);
				LivingEntity t = targets.isEmpty() ? null : targets.get(n % targets.size());
				Vec3 dir = t != null ? IronManComicFx.centre(t).subtract(from).normalize().add(0, 0.25, 0).normalize()
						: w.getLookAngle().add(0, 0.15, 0).normalize();
				IronManMissileEntity m = IronManComicFx.missile(w, from, dir, 0.9, damage, damage * 0.4f, 1.2f);
				if (t != null && t.isAlive()) {
					m.withTarget(t);
				} else {
					m.withHoming();
				}
				IronManComicFx.spawn(w, m);
				IronManSounds.play(w, IronManSounds.MISSILE_LAUNCH, 0.7f, 1.1f + (n % 3) * 0.08f);
			});
		}
		return true;
	}

	private static boolean unibeamSweep(ServerPlayer p, float damage) {
		Vec3 from = IronManComicFx.chest(p);
		List<LivingEntity> hits = IronManComicFx.cone(p, from, p.getLookAngle(), 24.0, 55.0);
		Vec3 look = p.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(p, look);
		// the visible sweep: five beams fanned across the arc
		for (int i = -2; i <= 2; i++) {
			Vec3 dir = look.add(right.scale(i * 0.45)).normalize();
			IronManAbilities.broadcastBeam(p, from, from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, 24.0))),
					IronManComicFx.BEAM_UNIBEAM);
		}
		for (LivingEntity e : hits) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, from, 0.9, 0.2);
		}
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 16);
		IronManComicFx.sound(p, IronManSounds.UNIBEAM_LOOP, 1.0f, 1.1f);
		return true;
	}

	private static boolean energyShield(ServerPlayer p, float factor) {
		Rt r = IronManComicState.rt(p);
		r.shieldUntil = p.level().getGameTime() + SHIELD_TICKS;
		r.shieldFactor = factor;
		IronManComicFx.sound(p, IronManSounds.SHIELD_UP, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.barrier_up"), true);
		return true;
	}

	private static boolean shieldBash(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 eye = p.getEyePosition();
		for (LivingEntity e : IronManComicFx.cone(p, eye, p.getLookAngle(), 5.0, 70.0)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, eye, 2.5, 0.4);
			IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
		}
		Vec3 c = eye.add(p.getLookAngle().scale(2.0));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.15);
		IronManComicFx.pose(p, IronManAbilityFx.BARRIER, 10);
		IronManComicFx.sound(p, IronManSounds.SHIELD_DEFLECT, 1.2f, 0.7f);
		return true;
	}

	// ================================================================== MARK 4 HYDRO

	private static boolean sonarPulse(ServerPlayer p, float damage) {
		boolean wet = p.isInWater();
		double radius = wet ? 36.0 : 24.0;
		int stun = wet ? 60 : 40;
		ServerLevel level = IronManComicFx.level(p);
		List<LivingEntity> found = IronManComicFx.around(p, p.position(), radius);
		for (LivingEntity e : found) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, stun, 3);
			if (e instanceof Mob m) {
				IronManComicState.stun(m, stun);
			}
		}
		IronManComicState.privateMark(p, found, 6 * 20);
		for (int i = 1; i <= 4; i++) {
			IronManComicFx.ring(level, p.position().add(0, 1.0, 0), radius * i / 4.0, wet ? ParticleTypes.BUBBLE : ParticleTypes.SCULK_CHARGE_POP,
					16 + i * 8);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.8f);
		return true;
	}

	private static boolean torpedo(ServerPlayer p, float damage) {
		Vec3 from = IronManComicFx.palm(p);
		LivingEntity t = IronManComicFx.target(p, 64.0);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 64);
		IronManMissileEntity m = IronManComicFx.missile(p, from.add(dir), dir, p.isInWater() ? 1.3 : 1.0, damage, damage * 0.4f, 1.6f)
				.withTorpedo();
		if (t != null) {
			m.withTarget(t);
		} else {
			m.withHoming();
		}
		IronManComicFx.spawn(p, m);
		IronManComicFx.pose(p, IronManAbilityFx.ROCKET, 12);
		IronManComicFx.sound(p, IronManSounds.MISSILE_LAUNCH, 1.0f, 0.7f);
		if (p.isInWater()) {
			IronManComicFx.cloud(IronManComicFx.level(p), from, ParticleTypes.BUBBLE, 20, 0.3, 0.1);
		}
		return true;
	}

	private static boolean depthCharge(ServerPlayer p, float damage) {
		Vec3 at = IronManComicFx.aimPoint(p, 20.0);
		ServerLevel level = IronManComicFx.level(p);
		IronManComicFx.line(level, IronManComicFx.palm(p), at, p.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SMOKE, 2.0);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_THROW, 10);
		IronManComicFx.soundAt(level, at, SoundEvents.TNT_PRIMED, 1.0f, 1.2f);
		IronManComicState.later(p, 30, w -> {
			ServerLevel l = IronManComicFx.level(w);
			for (LivingEntity e : IronManComicFx.around(w, at, 5.0)) {
				IronManComicFx.hit(w, e, damage);
				IronManComicFx.shove(e, at, 1.2, 0.6);
			}
			l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			l.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 60, 2.0, 1.0, 2.0, 0.2);
			l.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 80, 2.5, 0.5, 2.5, 0.2);
			IronManComicFx.soundAt(l, at, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.7f);
		});
		return true;
	}

	private static boolean propellerDash(ServerPlayer p, float damage) {
		boolean ok = IronManDash.start(p, damage, 0f, 0);
		if (ok && p.isInWater()) {
			IronManComicFx.cloud(IronManComicFx.level(p), p.position().add(0, 1, 0), ParticleTypes.BUBBLE, 30, 0.5, 0.2);
		}
		return ok;
	}

	private static boolean whirlpool(ServerPlayer p) {
		Vec3 at = IronManComicFx.aimPoint(p, 24.0);
		IronManComicState.area(p, AreaKind.WHIRLPOOL, at, 6.0, 5 * 20);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_MAGNET, 12);
		IronManComicFx.soundAt(IronManComicFx.level(p), at, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.4f, 0.8f);
		return true;
	}

	private static boolean headlamp(ServerPlayer p, float damage) {
		Vec3 from = p.getEyePosition().add(0, 0.15, 0);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 40);
		List<LivingEntity> hits = IronManComicFx.beam(p, from, dir, 40.0, 0.7, false, IronManComicFx.BEAM_LIGHT, damage, 0.5);
		for (LivingEntity e : hits) {
			IronManComicFx.control(e, MobEffects.BLINDNESS, 60, 0);
		}
		IronManComicFx.sound(p, IronManSounds.LASER_START, 0.8f, 1.4f);
		return true;
	}

	private static boolean ballast(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicFx.target(p, 24.0);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		IronManComicFx.hit(p, t, damage);
		IronManComicFx.control(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 6);
		Rt r = IronManComicState.rt(p);
		startHold(p, r, HoldKind.ANCHOR, t, 4 * 20);
		IronManComicFx.line(IronManComicFx.level(p), IronManComicFx.palm(p), IronManComicFx.centre(t), ParticleTypes.CRIT, 2.0);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_THROW, 10);
		IronManComicFx.sound(p, SoundEvents.CHAIN_PLACE, 1.2f, 0.6f);
		return true;
	}

	private static boolean pressureCrush(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 c = p.position();
		for (LivingEntity e : IronManComicFx.around(p, c.add(0, 0.5, 0), 5.0)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
			e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -0.8, 0));
			e.hurtMarked = true;
		}
		IronManComicFx.ring(level, c.add(0, 0.3, 0), 5.0, p.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD, 36);
		IronManComicFx.ring(level, c.add(0, 0.3, 0), 3.0, ParticleTypes.CRIT, 24);
		IronManComicFx.groundRing(level, c, 2.5, 16);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_STOMP, 14);
		IronManComicFx.soundAt(level, c, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
		return true;
	}

	// ================================================================== MARK 5 STEALTH

	/** "Unaware": a mob that is not targeting the wearer (anything else counts as aware). */
	public static boolean unaware(ServerPlayer p, LivingEntity e) {
		return e instanceof Mob m && m.getTarget() != p;
	}

	private static boolean silencedRepulsor(ServerPlayer p, float damage) {
		Vec3 from = IronManComicFx.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 50);
		LivingEntity t = IronManComicFx.target(p, 50.0);
		float dmg = t != null && unaware(p, t) ? damage * 2f : damage;
		IronManComicFx.beam(p, from, dir, 50.0, 0.6, false, IronManComicFx.BEAM_STEALTH, dmg, 0.6);
		IronManComicFx.pose(p, IronManAbilityFx.REPULSOR, 8);
		IronManSounds.play(p, IronManSounds.REPULSOR_ZAP, 0.25f, 1.6f);
		return true;
	}

	private static boolean silentTakedown(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicFx.target(p, 5.0);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		boolean silent = unaware(p, t);
		IronManComicFx.hit(p, t, silent ? damage : damage / 3f);
		ServerLevel level = IronManComicFx.level(p);
		Vec3 c = IronManComicFx.centre(t);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, silent ? 24 : 8, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		IronManComicFx.pose(p, IronManAbilityFx.PUNCH, 10);
		IronManSounds.play(p, SoundEvents.PLAYER_ATTACK_CRIT, 0.3f, 0.8f);
		if (silent) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.takedown"), true);
		}
		return true;
	}

	private static boolean cloak(ServerPlayer p, int ticks) {
		Rt r = IronManComicState.rt(p);
		r.cloakUntil = p.level().getGameTime() + ticks;
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, true, false, false));
		ServerLevel level = IronManComicFx.level(p);
		level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.35, 0.7, 0.35, 0.02);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
		}
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_GUARD, 10);
		IronManSounds.play(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.4f, 1.4f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.cloaked", ticks / 20), true);
		return true;
	}

	private static boolean blink(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double d = Math.max(0.0, IronManComicFx.wallDistance(p, eye, look, range) - 0.8);
		if (d < 1.5) {
			return false;
		}
		Vec3 to = p.position().add(look.scale(d));
		ServerLevel level = IronManComicFx.level(p);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		p.teleportTo(to.x, to.y, to.z);
		p.resetFallDistance();
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, true, false, false));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 16, 0.3, 0.6, 0.3, 0.02);
		IronManSounds.play(p, SoundEvents.ENDERMAN_TELEPORT, 0.25f, 1.6f);
		return true;
	}

	private static boolean jammer(ServerPlayer p) {
		ServerLevel level = IronManComicFx.level(p);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		IronManComicState.area(p, AreaKind.JAM_FIELD, p.position(), 16.0, 4 * 20);
		IronManComicFx.ring(level, p.position().add(0, 1.0, 0), 4.0, ParticleTypes.ELECTRIC_SPARK, 30);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_GUARD, 8);
		IronManSounds.play(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.2f, 2.0f);
		return true;
	}

	/** Robots, turrets and Ultron's machines: what an EMP shuts down. */
	public static boolean robotic(LivingEntity e) {
		return e instanceof com.projecthero.mod.ultron.entity.UltronRobot || e instanceof IronGolem
				|| e instanceof net.minecraft.world.entity.monster.Shulker
				|| e.getClass().getName().contains("ultron") || e.getClass().getName().contains("Sentinel")
				|| e.getClass().getSimpleName().contains("Sentry");
	}

	private static boolean emp(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		for (LivingEntity e : IronManComicFx.around(p, p.position(), 10.0)) {
			if (robotic(e)) {
				IronManComicFx.hit(p, e, damage * 2f);
				if (e instanceof Mob m) {
					IronManComicState.stun(m, 5 * 20);
				}
			} else {
				IronManComicFx.hit(p, e, damage * 0.5f);
				IronManComicFx.control(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
			}
		}
		IronManComicFx.ring(level, p.position().add(0, 1.0, 0), 5.0, ParticleTypes.ELECTRIC_SPARK, 40);
		IronManComicFx.ring(level, p.position().add(0, 1.0, 0), 10.0, ParticleTypes.ELECTRIC_SPARK, 60);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicFx.soundAt(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.4f);
		return true;
	}

	private static boolean lowGlow(ServerPlayer p, float damage) {
		Vec3 from = IronManComicFx.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 96);
		LivingEntity t = IronManComicFx.target(p, 96.0);
		float dmg = t != null && unaware(p, t) ? damage * 1.5f : damage;
		IronManComicFx.beam(p, from, dir, 96.0, 0.35, false, IronManComicFx.BEAM_STEALTH, dmg, 0.3);
		IronManComicFx.pose(p, IronManAbilityFx.CHARGED, 12);
		IronManSounds.play(p, IronManSounds.LASER_END, 0.3f, 1.8f);
		return true;
	}

	private static boolean markedShot(ServerPlayer p) {
		LivingEntity t = IronManComicFx.target(p, 64.0);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		Rt r = IronManComicState.rt(p);
		r.markedTarget = t.getId();
		r.markedHits = 3;
		r.markedUntil = p.level().getGameTime() + 12 * 20;
		IronManComicState.privateMark(p, List.of(t), 12 * 20);
		IronManAbilities.broadcastBeam(p, IronManComicFx.palm(p), IronManComicFx.centre(t), IronManComicFx.BEAM_LASER);
		IronManSounds.play(p, IronManSounds.TARGET_LOCK, 0.6f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.marked_shot"), true);
		return true;
	}

	private static boolean decoy(ServerPlayer p) {
		for (IronManComicState.Area a : IronManComicState.areasOf(p, AreaKind.DECOY)) {
			a.until = 0L; // one decoy at a time
		}
		IronManComicState.area(p, AreaKind.DECOY, p.position(), 2.0, 10 * 20);
		ServerLevel level = IronManComicFx.level(p);
		level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.8, 0.3, 0.02);
		IronManSounds.play(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.4f, 1.3f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.decoy"), true);
		return true;
	}

	private static boolean decoySwap(ServerPlayer p) {
		List<IronManComicState.Area> decoys = IronManComicState.areasOf(p, AreaKind.DECOY);
		ServerLevel level = IronManComicFx.level(p);
		Vec3 here = p.position();
		if (!decoys.isEmpty()) {
			IronManComicState.Area a = decoys.get(0);
			Vec3 there = a.centre;
			if (there.distanceTo(here) > 48.0) {
				return false;
			}
			a.centre = here;
			p.teleportTo(there.x, there.y, there.z);
		} else {
			// no decoy out: leave one where you stand and slip 8 blocks ahead
			IronManComicState.area(p, AreaKind.DECOY, here, 2.0, 8 * 20);
			if (!blink(p, 8.0)) {
				return true;
			}
		}
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.GLOW, here.x, here.y + 1, here.z, 20, 0.3, 0.7, 0.3, 0.02);
		level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.7, 0.3, 0.02);
		IronManSounds.play(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.4f, 1.0f);
		return true;
	}

	// ================================================================== MARK 6 SILVER CENTURION

	private static boolean pulseStorm(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.MINIGUN, 20);
		for (int i = 0; i < 8; i++) {
			IronManComicState.later(p, 1 + i * 2, w -> {
				if (IronManComicState.suited(w)) {
					Vec3 from = IronManComicFx.palm(w);
					Vec3 dir = IronManTargeting.aim(w, from, w.getLookAngle(), 50).add(
							(w.getRandom().nextDouble() - 0.5) * 0.06, (w.getRandom().nextDouble() - 0.5) * 0.06,
							(w.getRandom().nextDouble() - 0.5) * 0.06);
					List<LivingEntity> hits = IronManComicFx.beam(w, from, dir, 50.0, 0.8, false, IronManComicFx.BEAM_PULSE, damage, 0.2);
					if (!hits.isEmpty()) {
						stackPulse(w, hits.get(0), 26f);
					}
					IronManSounds.play(w, IronManSounds.REPULSOR_ZAP, 0.6f, 1.6f);
				}
			});
		}
		return true;
	}

	private static boolean ricochet(ServerPlayer p, float damage) {
		LivingEntity first = IronManComicFx.target(p, 40.0);
		Vec3 from = IronManComicFx.palm(p);
		IronManComicFx.pose(p, IronManAbilityFx.REPULSOR, 10);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_BLAST, 1.0f, 1.2f);
		if (first == null) {
			Vec3 dir = p.getLookAngle();
			IronManAbilities.broadcastBeam(p, from, from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, 40))),
					IronManComicFx.BEAM_REPULSOR);
			return true;
		}
		List<LivingEntity> chain = new ArrayList<>();
		chain.add(first);
		LivingEntity cur = first;
		while (chain.size() < 4) {
			LivingEntity next = null;
			double best = 100.0;
			for (LivingEntity e : IronManComicFx.around(p, IronManComicFx.centre(cur), 10.0)) {
				if (chain.contains(e)) {
					continue;
				}
				double d2 = e.distanceToSqr(cur);
				if (d2 < best) {
					best = d2;
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			chain.add(next);
			cur = next;
		}
		Vec3 a = from;
		float dmg = damage;
		for (LivingEntity e : chain) {
			Vec3 b = IronManComicFx.centre(e);
			IronManAbilities.broadcastBeam(p, a, b, IronManComicFx.BEAM_REPULSOR);
			IronManComicFx.hit(p, e, dmg);
			IronManComicFx.shove(e, a, 0.6, 0.15);
			IronManComicFx.level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 10, 0.2, 0.2, 0.2, 0.1);
			a = b;
			dmg *= 0.85f;
		}
		return true;
	}

	private static boolean overcharge(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 36);
		IronManComicFx.sound(p, IronManSounds.UNIBEAM_CHARGE, 1.2f, 0.7f);
		for (int i = 0; i < 6; i++) {
			IronManComicState.later(p, 1 + i * 5, w -> {
				Vec3 c = IronManComicFx.chest(w);
				IronManComicFx.level(w).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.1);
				IronManComicFx.level(w).sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 4, 0.5, 0.5, 0.5, -0.05);
			});
		}
		IronManComicState.later(p, 30, w -> {
			if (!IronManComicState.suited(w)) {
				return;
			}
			Vec3 from = IronManComicFx.chest(w);
			Vec3 dir = IronManTargeting.aim(w, from, w.getLookAngle(), 50);
			IronManComicFx.beam(w, from, dir, 50.0, 2.5, true, IronManComicFx.BEAM_MEGA, damage, 2.0);
			IronManComicFx.sound(w, IronManSounds.REPULSOR_CHARGED_BLAST, 1.4f, 0.5f);
		});
		return true;
	}

	/**
	 * v0.16.7 (user): the Silver Centurion's C. The press marks the time; the release decides -- under
	 * {@link #SENTRY_HOLD_TICKS} = Energy Shield, longer = Overload Burst. Each keeps its own cooldown and energy, so one
	 * cooling down never blocks the other. True when either went off.
	 */
	private static boolean shieldOrBurst(ServerPlayer p, IronManSuit suit, boolean pressed) {
		Rt r = IronManComicState.rt(p);
		long now = p.level().getGameTime();
		if (pressed) {
			r.cPressAt = now;
			r.cHeld = true;
			return false;
		}
		long at = r.cPressAt;
		boolean held = r.cHeld;
		r.cHeld = false;
		r.cPressAt = 0L; // (a game-time 0 press is real -- the flag says whether C is down)
		if (!held) {
			return false;
		}
		boolean burst = now - at >= SENTRY_HOLD_TICKS;
		String key = burst ? "mk6_overload_burst" : "mk6_energy_shield";
		if (!com.projecthero.mod.ironman.TonyStark.abilityReady(p, suit.id(), key)) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f",
							com.projecthero.mod.ironman.TonyStark.abilityCooldownRemaining(p, suit.id(), key) / 20.0f)), true);
			return false;
		}
		float cost = burst ? BURST_ENERGY : SHIELD_ENERGY;
		if (!com.projecthero.mod.ironman.ability.era1.IronManEraOne.canPayAmount(p, suit, cost)) {
			return false;
		}
		if (burst) {
			burst360(p, 8.0, BURST_DAMAGE, 2.0, false);
		} else {
			energyShield(p, MK6_SHIELD_FACTOR);
		}
		com.projecthero.mod.ironman.TonyStark.triggerCooldown(p, suit.id(), key, burst ? BURST_COOLDOWN_TICKS : SHIELD_COOLDOWN_TICKS);
		com.projecthero.mod.ironman.ability.era1.IronManEraOne.payAmount(p, suit, cost);
		return true;
	}

	/** Gametests: the Silver Centurion's C as a tap ({@code holdTicks} < 20) or a hold. */
	public static boolean centurionCForTests(ServerPlayer p, IronManSuit suit, int holdTicks) {
		IronManComicState.rt(p).cPressAt = p.level().getGameTime() - holdTicks;
		IronManComicState.rt(p).cHeld = true;
		return shieldOrBurst(p, suit, false);
	}

	/** A 360-degree burst around the wearer (Overload Burst; Solar Flare also sets fire and blinds). */
	private static boolean burst360(ServerPlayer p, double radius, float damage, double knock, boolean solar) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (LivingEntity e : IronManComicFx.around(p, c, radius)) {
			if (solar) {
				IronManComicFx.burn(p, e, damage, 100);
				IronManComicFx.control(e, MobEffects.BLINDNESS, 60, 0);
			} else {
				IronManComicFx.hit(p, e, damage);
			}
			IronManComicFx.shove(e, c, knock, 0.4);
		}
		for (int i = 1; i <= 3; i++) {
			IronManComicFx.ring(level, c, radius * i / 3.0, solar ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, 20 + i * 12);
		}
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(solar ? ParticleTypes.LAVA : ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 1.0, 1.0, 1.0, 0.2);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_FLARE, 14);
		IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.0f, solar ? 1.3f : 0.9f);
		return true;
	}

	// ================================================================== MARK 7 SPACE

	private static boolean vacuumMissiles(ServerPlayer p, float damage) {
		List<LivingEntity> targets = IronManComicFx.frontTargets(p, 48.0, 6);
		IronManComicFx.pose(p, IronManAbilityFx.MISSILES, 20);
		IronManComicFx.sound(p, IronManSounds.MISSILE_POD, 1.0f, 0.9f);
		for (int i = 0; i < 6; i++) {
			final int n = i;
			IronManComicState.later(p, 1 + i * 2, w -> {
				if (!IronManComicState.suited(w)) {
					return;
				}
				Vec3 from = IronManComicFx.shoulder(w, n);
				LivingEntity t = targets.isEmpty() ? null : targets.get(n % targets.size());
				Vec3 dir = t != null ? IronManComicFx.centre(t).subtract(from).normalize().add(0, 0.2, 0).normalize()
						: w.getLookAngle().add(0, 0.15, 0).normalize();
				IronManMissileEntity m = IronManComicFx.missile(w, from, dir, 1.0, damage, damage * 0.4f, 1.4f).withVacuum();
				if (t != null && t.isAlive()) {
					m.withTarget(t);
				} else {
					m.withHoming();
				}
				IronManComicFx.spawn(w, m);
				IronManSounds.play(w, IronManSounds.MISSILE_LAUNCH, 0.7f, 1.0f + (n % 3) * 0.1f);
			});
		}
		return true;
	}

	private static boolean orbitalStrike(ServerPlayer p, float damage) {
		Vec3 at = IronManComicFx.groundAt(p, IronManComicFx.aimPoint(p, 64.0));
		ServerLevel level = IronManComicFx.level(p);
		IronManComicFx.ring(level, at.add(0, 0.2, 0), 5.0, new DustParticleOptions(new org.joml.Vector3f(1f, 0.2f, 0.2f), 1.5f), 36);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_SKY, 20);
		IronManSounds.play(p, IronManSounds.TARGET_LOCK, 1.0f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.orbital"), true);
		for (int i = 0; i < 6; i++) {
			IronManComicState.later(p, 1 + i * 5, w -> IronManComicFx.ring(IronManComicFx.level(w), at.add(0, 0.2, 0), 5.0,
					new DustParticleOptions(new org.joml.Vector3f(1f, 0.2f, 0.2f), 1.5f), 36));
		}
		for (int i = 0; i < 12; i++) {
			IronManComicState.later(p, 30 + i * 5, w -> {
				double a = w.getRandom().nextDouble() * Math.PI * 2;
				double r = Math.sqrt(w.getRandom().nextDouble()) * 5.0;
				Vec3 hit = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				Vec3 from = hit.add(0, 32, 0);
				IronManMissileEntity m = IronManComicFx.missile(w, from, new Vec3(0, -1, 0), 2.0, damage, damage * 0.55f, 1.8f);
				IronManComicFx.spawn(w, m);
			});
		}
		return true;
	}

	private static boolean booster(ServerPlayer p) {
		IronManComicFx.launch(p, new Vec3(p.getDeltaMovement().x * 0.3, 2.6, p.getDeltaMovement().z * 0.3));
		ServerLevel level = IronManComicFx.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 40, 0.3, 0.1, 0.3, 0.1);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 30, 0.6, 0.1, 0.6, 0.15);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_LAUNCH, 12);
		IronManComicFx.sound(p, IronManSounds.SUPERSONIC, 1.2f, 1.0f);
		IronManComicState.later(p, 12, w -> {
			if (IronManComicState.suited(w) && !w.onGround()) {
				IronManFlight.setFlying(w, true);
			}
		});
		return true;
	}

	private static boolean meteorDive(ServerPlayer p) {
		Vec3 ground = groundFar(p, 64.0);
		double height = p.getY() - ground.y;
		if (p.onGround() || height < 4.0) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.dive_too_low"), true);
			return false;
		}
		Rt r = IronManComicState.rt(p);
		r.diving = true;
		r.diveStartY = p.getY();
		r.diveStart = p.level().getGameTime();
		if (IronManFlight.isFlying(p)) {
			IronManFlight.setFlying(p, false);
		}
		Vec3 look = p.getLookAngle();
		IronManComicFx.launch(p, new Vec3(look.x * 0.6, -3.0, look.z * 0.6));
		IronManComicFx.sound(p, IronManSounds.SUPERSONIC, 1.2f, 0.7f);
		return true;
	}

	static void tickDive(ServerPlayer p, IronManSuit suit, Rt r) {
		ServerLevel level = IronManComicFx.level(p);
		IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_DIVE);
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.5, p.getZ(), 3, 0.3, 0.6, 0.3, 0.02);
		boolean landed = p.onGround() || p.isInWater() || p.level().getGameTime() - r.diveStart > 100;
		if (!landed) {
			if (p.getDeltaMovement().y > -2.5) {
				IronManComicFx.launch(p, new Vec3(p.getDeltaMovement().x, -3.0, p.getDeltaMovement().z));
			}
			return;
		}
		r.diving = false;
		double fallen = Math.max(0.0, r.diveStartY - p.getY());
		IronManComicKit.Move m = IronManComicKit.byId("mk7_meteor_dive");
		float damage = meteorDamage(m == null ? 15f : m.damage(), fallen);
		double radius = Math.min(8.0, 4.0 + fallen * 0.08);
		Vec3 c = p.position();
		for (LivingEntity e : IronManComicFx.around(p, c.add(0, 0.5, 0), radius)) {
			IronManComicFx.hit(p, e, damage);
			IronManComicFx.shove(e, c, 1.6, 0.8);
		}
		IronManComicFx.groundRing(level, c, radius * 0.5, 20);
		IronManComicFx.groundRing(level, c, radius, 28);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 20, radius * 0.3, 0.2, radius * 0.3, 0.1);
		IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.6f);
		IronManComicFx.pose(p, IronManAbilityFx.GROUND_POUND, 12);
	}

	/** The ground up to {@code reach} blocks straight below (or the position itself with none). */
	private static Vec3 groundFar(ServerPlayer p, double reach) {
		Vec3 from = p.position();
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(from, from.subtract(0, reach, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? from.subtract(0, reach, 0) : hit.getLocation();
	}

	/** The Meteor Dive's impact: {@code base} + 1.5 per block fallen, capped at 80. */
	public static float meteorDamage(float base, double fallen) {
		return (float) Math.min(80.0, base + 1.5 * fallen);
	}

	private static boolean solarBeam(ServerPlayer p, float damage) {
		boolean sun = p.level().isDay() && p.level().canSeeSky(p.blockPosition().above());
		float dmg = sun ? damage * 1.5f : damage;
		Vec3 from = IronManComicFx.chest(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 50);
		List<LivingEntity> hits = IronManComicFx.beam(p, from, dir, 50.0, 1.3, true, IronManComicFx.BEAM_SOLAR, dmg, 1.0);
		for (LivingEntity e : hits) {
			e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), sun ? 80 : 40));
		}
		IronManComicFx.pose(p, IronManAbilityFx.UNIBEAM, 16);
		IronManComicFx.sound(p, IronManSounds.UNIBEAM_CHARGE, 1.0f, sun ? 1.4f : 1.0f);
		return true;
	}

	private static boolean tether(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicFx.target(p, 30.0);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		boolean boss = com.projecthero.mod.kryptonian.KryptonianCombat.isBoss(t);
		IronManComicFx.hit(p, t, damage);
		Rt r = IronManComicState.rt(p);
		startHold(p, r, HoldKind.TETHER, t, boss ? 30 : 4 * 20);
		r.holdAnchor = t.position().add(0, 3.0, 0);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_MAGNET, 12);
		IronManComicFx.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
		return true;
	}

	/**
	 * Singularity: a gravity grenade pitched up to 24 blocks. It lands, drags every enemy within
	 * {@link #SINGULARITY_PULL_RADIUS} into its centre for 1.5 s, then implodes for the move's damage in 4.5 blocks.
	 */
	public static final double SINGULARITY_RANGE = 24.0;
	public static final double SINGULARITY_PULL_RADIUS = 7.0;
	public static final int SINGULARITY_LAND = 6;
	public static final int SINGULARITY_IMPLODE = 38;

	private static boolean singularity(ServerPlayer p, float damage) {
		Vec3 from = IronManComicFx.palm(p);
		Vec3 at = IronManComicFx.aimPoint(p, SINGULARITY_RANGE);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_SINGULARITY, 14);
		IronManComicFx.sound(p, SoundEvents.ENDER_EYE_LAUNCH, 1.0f, 0.6f);
		// the orb's flight: a dark streak along the throw
		for (int i = 1; i <= SINGULARITY_LAND; i++) {
			final double f0 = (i - 1) / (double) SINGULARITY_LAND;
			final double f1 = i / (double) SINGULARITY_LAND;
			IronManComicState.later(p, i, w -> IronManComicFx.line(IronManComicFx.level(w), from.lerp(at, f0).add(0, Math.sin(f0 * Math.PI) * 1.5, 0),
					from.lerp(at, f1).add(0, Math.sin(f1 * Math.PI) * 1.5, 0), ParticleTypes.REVERSE_PORTAL, 4.0));
		}
		IronManComicState.later(p, SINGULARITY_LAND, w -> IronManComicFx.soundAt(IronManComicFx.level(w), at, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.4f));
		// the pull: every 2 ticks, enemies dragged in, a shrinking ring and a dark core
		for (int t = SINGULARITY_LAND + 2; t < SINGULARITY_IMPLODE; t += 2) {
			final double k = (t - SINGULARITY_LAND) / (double) (SINGULARITY_IMPLODE - SINGULARITY_LAND);
			IronManComicState.later(p, t, w -> {
				ServerLevel level = IronManComicFx.level(w);
				for (LivingEntity e : IronManComicFx.around(w, at, SINGULARITY_PULL_RADIUS)) {
					if (!com.projecthero.mod.kryptonian.KryptonianCombat.isBoss(e)) {
						IronManComicFx.pull(e, at, 0.35);
					}
				}
				IronManComicFx.ring(level, at, SINGULARITY_PULL_RADIUS * (1.0 - k) + 0.6, ParticleTypes.REVERSE_PORTAL, 24);
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.3, at.z, 3, 0.15, 0.15, 0.15, 0.0);
				level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.3, at.z, 10, 1.2, 1.2, 1.2, -0.6);
			});
		}
		IronManComicState.later(p, SINGULARITY_IMPLODE, w -> {
			ServerLevel level = IronManComicFx.level(w);
			for (LivingEntity e : IronManComicFx.around(w, at, 4.5)) {
				IronManComicFx.hit(w, e, damage);
				IronManComicFx.shove(e, at, 0.4, 0.6);
			}
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 30, 0.2, 0.2, 0.2, 0.35);
			IronManComicFx.soundAt(level, at, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.5f);
			IronManComicFx.soundAt(level, at, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.5f);
		});
		return true;
	}

	private static boolean zeroG(ServerPlayer p) {
		Vec3 at = IronManComicFx.groundAt(p, IronManComicFx.aimPoint(p, 30.0));
		IronManComicState.area(p, AreaKind.ZERO_G, at, 6.0, 6 * 20);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_MAGNET, 12);
		IronManComicFx.soundAt(IronManComicFx.level(p), at, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f);
		return true;
	}

	// ================================================================== MARK 8 HULKBUSTER
	// Every move plays its clip on the Hulkbuster body (IronManAbilityFx.HB_*, client IronManHulkbusterAnimatable) and lands
	// on the clip's strike tick (scratchpad/hulkbuster_anim_timing.json: the "hit" ticks already include the 4-tick
	// GeckoLib transition), so the blow connects exactly when the fist / foot does.

	/** Hulkbuster strike ticks (server ticks after the key press), from the body's timing table. */
	public static final int[] HB_PUNCH_HITS = { 10, 20, 30 };
	public static final int HB_PUNCH_TICKS = 40;
	public static final int HB_ROCKET_LAUNCH = 16;
	public static final int HB_ROCKET_RETURN = 40;
	public static final int HB_ROCKET_TICKS = 54;
	public static final int HB_PILE_HIT = 20;
	public static final int HB_PILE_TICKS = 36;
	public static final int HB_QUAKE_HIT = 16;
	public static final int HB_QUAKE_TICKS = 32;
	/** Grab: the hands close on the target at 10; the pound / throw clip starts at 16. */
	public static final int HB_GRAB_SEIZE = 10;
	public static final int HB_GRAB_FOLLOW = 16;
	public static final int[] HB_POUND_HITS = { 14, 24 };
	public static final int HB_POUND_TICKS = 34;
	public static final int HB_THROW_HIT = 14;
	public static final int HB_THROW_TICKS = 28;
	public static final int HB_BEAM_START = 14;
	public static final int HB_BEAM_END = 42;
	public static final int HB_BEAM_TICKS = 54;
	/** The beam deals its damage every this many ticks between start and end (5 pulses). */
	public static final int HB_BEAM_PULSE = 7;
	public static final int HB_MISSILES_FIRST = 16;
	public static final int HB_MISSILES_TICKS = 44;
	public static final int HB_POD_TICKS = 44;

	private static boolean hulkbusterOn(ServerPlayer w) {
		return "mark_8".equals(IronManArmor.wornSuitId(w));
	}

	private static boolean punchCombo(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.HB_PUNCH, HB_PUNCH_TICKS);
		IronManComicFx.sound(p, IronManSounds.SERVO, 1.0f, 0.7f);
		for (int i = 0; i < 3; i++) {
			final int step = i + 1;
			IronManComicState.later(p, HB_PUNCH_HITS[i], w -> {
				if (hulkbusterOn(w)) {
					punchStrike(w, step, damage);
				}
			});
		}
		return true;
	}

	/** One blow of the combo: 1 right jab, 2 left hook, 3 right uppercut finisher (x1.45 and a 3-block burst). */
	static void punchStrike(ServerPlayer p, int step, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 fist = eye.add(p.getLookAngle().scale(2.2 * p.getScale())).add(0, -0.5, 0);
		boolean finisher = step == 3;
		float dmg = finisher ? damage * 1.45f : damage;
		List<LivingEntity> hits = IronManComicFx.cone(p, eye, p.getLookAngle(), 5.5, finisher ? 55.0 : 35.0);
		if (finisher) {
			for (LivingEntity e : IronManComicFx.around(p, fist, 3.0)) {
				if (!hits.contains(e)) {
					hits.add(e);
				}
			}
		}
		for (LivingEntity e : hits) {
			IronManComicFx.hit(p, e, dmg);
			IronManComicFx.shove(e, p.position(), finisher ? 2.2 : 1.0, finisher ? 0.7 : 0.2);
		}
		level.sendParticles(finisher ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT, fist.x, fist.y, fist.z, finisher ? 2 : 10, 0.3, 0.3,
				0.3, 0.1);
		IronManComicFx.sound(p, IronManSounds.PUNCH, 1.2f, finisher ? 0.6f : 0.8f);
		if (finisher) {
			IronManComicFx.soundAt(level, fist, SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
		}
	}

	private static boolean rocketFist(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.HB_ROCKET_FIST, HB_ROCKET_TICKS);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGE, 1.0f, 0.6f);
		IronManComicState.later(p, HB_ROCKET_LAUNCH, w -> {
			if (!hulkbusterOn(w)) {
				return;
			}
			Vec3 from = IronManComicFx.palm(w);
			Vec3 dir = IronManTargeting.aim(w, from, w.getLookAngle(), 48);
			com.projecthero.mod.ironman.entity.HulkbusterFistEntity.launch(w, from, dir, damage, HB_ROCKET_RETURN - HB_ROCKET_LAUNCH);
			IronManComicFx.sound(w, IronManSounds.ROCKET_LAUNCH, 1.3f, 0.7f);
		});
		return true;
	}

	private static boolean pileDriver(ServerPlayer p, float damage) {
		if (IronManGroundPound.groundBelow(p) == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.ground_pound_no_ground"), true);
			return false;
		}
		IronManComicFx.pose(p, IronManAbilityFx.HB_PILE_DRIVER, HB_PILE_TICKS);
		IronManComicFx.sound(p, IronManSounds.SERVO, 1.0f, 0.6f);
		IronManComicState.later(p, HB_PILE_HIT, w -> {
			Vec3 c = IronManGroundPound.groundBelow(w);
			if (c == null || !hulkbusterOn(w)) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			for (LivingEntity e : IronManComicFx.around(w, c.add(0, 0.5, 0), 5.0)) {
				IronManComicFx.hit(w, e, damage);
				IronManComicFx.shove(e, c, 1.4, 0.9);
			}
			IronManComicFx.groundRing(level, c, 2.5, 18);
			IronManComicFx.groundRing(level, c, 5.0, 30);
			IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.6f);
			IronManComicFx.soundAt(level, c, SoundEvents.ANVIL_LAND, 1.0f, 0.4f);
		});
		return true;
	}

	/** A shockwave rolling 18 blocks straight ahead along the ground, 3 wide, from the stomp's impact tick. */
	private static boolean earthquake(ServerPlayer p, float damage) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return false;
		}
		Vec3 dir = flat.normalize();
		Vec3 start = p.position();
		java.util.Set<Integer> hit = new java.util.HashSet<>();
		IronManComicFx.pose(p, IronManAbilityFx.HB_EARTHQUAKE, HB_QUAKE_TICKS);
		IronManComicState.later(p, HB_QUAKE_HIT, w -> {
			IronManComicFx.soundAt(IronManComicFx.level(w), start, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.4f);
			IronManComicFx.groundRing(IronManComicFx.level(w), start, 2.0, 16);
		});
		for (int i = 1; i <= 12; i++) {
			final double dist = i * 1.5;
			IronManComicState.later(p, HB_QUAKE_HIT + i, w -> {
				ServerLevel level = IronManComicFx.level(w);
				Vec3 at = IronManComicFx.groundAt(w, start.add(dir.scale(dist)).add(0, 1.5, 0));
				IronManComicFx.groundRing(level, at, 1.2, 8);
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 4, 0.6, 0.1, 0.6, 0.05);
				for (LivingEntity e : IronManComicFx.around(w, at.add(0, 0.8, 0), 2.0)) {
					if (hit.add(e.getId())) {
						IronManComicFx.hit(w, e, damage);
						e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(dir.scale(0.4)).add(0, 0.85, 0));
						e.hurtMarked = true;
					}
				}
				if (dist % 3.0 < 0.1) {
					IronManComicFx.soundAt(level, at, SoundEvents.GENERIC_EXPLODE, 0.5f, 0.6f);
				}
			});
		}
		return true;
	}

	private static boolean grab(ServerPlayer p, HoldKind kind, float damage) {
		LivingEntity t = IronManComicFx.target(p, 5.0 * p.getScale());
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		Rt r = IronManComicState.rt(p);
		int len = HB_GRAB_FOLLOW + (kind == HoldKind.GRAB_POUND ? HB_POUND_TICKS : HB_THROW_HIT);
		startHold(p, r, kind, t, len);
		IronManComicFx.pose(p, IronManAbilityFx.HB_GRAB, HB_GRAB_FOLLOW);
		IronManComicFx.sound(p, IronManSounds.SERVO, 1.0f, 0.7f);
		return true;
	}

	private static boolean megaBeam(ServerPlayer p) {
		Rt r = IronManComicState.rt(p);
		long now = p.level().getGameTime();
		r.channel = "mk8_mega_beam";
		r.channelStart = now + HB_BEAM_START;
		r.channelUntil = now + HB_BEAM_END + 1;
		IronManComicFx.pose(p, IronManAbilityFx.HB_CHEST_BEAM, HB_BEAM_TICKS);
		IronManComicFx.sound(p, IronManSounds.UNIBEAM_CHARGE, 1.4f, 0.6f);
		return true;
	}

	static void tickChannel(ServerPlayer p, IronManSuit suit, Rt r) {
		long now = p.level().getGameTime();
		if (now >= r.channelUntil) {
			r.channel = null;
			IronManSounds.play(p, IronManSounds.UNIBEAM_END, 1.0f, 0.7f);
			return;
		}
		if (now < r.channelStart) {
			return; // the chest plates are still opening (the clip's wind-up)
		}
		Vec3 from = IronManComicFx.chest(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), 44);
		if ((now - r.channelStart) % HB_BEAM_PULSE == 0) {
			IronManComicKit.Move m = IronManComicKit.byId("mk8_mega_beam");
			IronManComicFx.beam(p, from, dir, 44.0, 2.2, true, IronManComicFx.BEAM_MEGA, m == null ? 18f : m.damage(), 0.8);
			IronManSounds.play(p, IronManSounds.BEAM_CRACKLE, 0.9f, 0.6f);
		} else if (now % 2 == 0) {
			IronManAbilities.broadcastBeam(p, from, from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, 44.0))),
					IronManComicFx.BEAM_MEGA);
		}
	}

	/** Palm Cannon: both palms, one heavy blast each (the move's damage) + a 3-block splash of 10 where each lands. */
	public static final int HB_CANNON_FIRE = 16;
	public static final int HB_CANNON_TICKS = 34;
	public static final double HB_CANNON_RANGE = 40.0;

	private static boolean palmCannon(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.HB_PALM_CANNON, HB_CANNON_TICKS);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_CHARGE, 1.4f, 0.5f);
		for (int i = 4; i < HB_CANNON_FIRE; i += 3) {
			IronManComicState.later(p, i, w -> {
				for (int side = 0; side < 2; side++) {
					Vec3 c = cannonPalm(w, side);
					IronManComicFx.level(w).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.08);
				}
			});
		}
		IronManComicState.later(p, HB_CANNON_FIRE, w -> {
			if (!hulkbusterOn(w)) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			for (int side = 0; side < 2; side++) {
				Vec3 from = cannonPalm(w, side);
				Vec3 dir = IronManTargeting.aim(w, from, w.getLookAngle(), HB_CANNON_RANGE).normalize();
				List<LivingEntity> hits = IronManComicFx.beam(w, from, dir, HB_CANNON_RANGE, 1.0, false, IronManComicFx.BEAM_PALM_CANNON, damage, 1.6);
				Vec3 end = hits.isEmpty() ? from.add(dir.scale(IronManComicFx.wallDistance(w, from, dir, HB_CANNON_RANGE)))
						: IronManComicFx.centre(hits.get(0));
				for (LivingEntity e : IronManComicFx.around(w, end, 3.0)) {
					if (!hits.contains(e)) {
						IronManComicFx.hit(w, e, 10f);
						IronManComicFx.shove(e, end, 0.8, 0.3);
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.4, 0.4, 0.4, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 14, 0.2, 0.2, 0.2, 0.2);
			}
			IronManComicFx.sound(w, IronManSounds.REPULSOR_CHARGED_BLAST, 1.6f, 0.55f);
			IronManComicFx.sound(w, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.9f);
			// the recoil shoves the giant suit back a little
			Vec3 back = w.getLookAngle().multiply(1, 0, 1).normalize().scale(-0.35);
			w.setDeltaMovement(w.getDeltaMovement().add(back));
			w.hurtMarked = true;
		});
		return true;
	}

	/** Where one of the Hulkbuster's palms is while it holds the cannon pose: out in front at chest height. */
	private static Vec3 cannonPalm(ServerPlayer w, int side) {
		Vec3 look = w.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(w, look);
		double s = w.getScale();
		return w.getEyePosition().add(0, -0.55 * s, 0).add(look.scale(1.1 * s)).add(right.scale((side == 0 ? 0.42 : -0.42) * s));
	}

	/**
	 * v0.16.7 (user): the Hulkbuster's C. The press only marks the time; the release decides -- held under
	 * {@link #SENTRY_HOLD_TICKS} = the Hydraulic Brace (its own 25 s cooldown and 15 energy), held longer = Sentry Mode
	 * (step out of the suit, free -- it works on a dead reactor too). Returns true only for the brace.
	 */
	private static boolean braceOrSentry(ServerPlayer p, IronManSuit suit, boolean pressed) {
		Rt r = IronManComicState.rt(p);
		long now = p.level().getGameTime();
		if (pressed) {
			r.cPressAt = now;
			r.cHeld = true;
			return false;
		}
		long at = r.cPressAt;
		boolean held = r.cHeld;
		r.cHeld = false;
		r.cPressAt = 0L; // (a game-time 0 press is real -- the flag says whether C is down)
		if (!held) {
			return false;
		}
		if (now - at >= SENTRY_HOLD_TICKS) {
			com.projecthero.mod.ironman.entity.IronManSentryEntity.deploy(p);
			return false;
		}
		if (com.projecthero.mod.ironman.IronManIntegrity.disabled(p, suit.id())) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_disabled").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (!com.projecthero.mod.ironman.TonyStark.abilityReady(p, suit.id(), "mk8_hydraulic_brace")) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f",
							com.projecthero.mod.ironman.TonyStark.abilityCooldownRemaining(p, suit.id(), "mk8_hydraulic_brace") / 20.0f)), true);
			return false;
		}
		if (IronManEnergy.brownedOut(p, suit.id()) || !com.projecthero.mod.ironman.ability.era1.IronManEraOne.canPayAmount(p, suit, BRACE_ENERGY)) {
			return false;
		}
		brace(p);
		com.projecthero.mod.ironman.TonyStark.triggerCooldown(p, suit.id(), "mk8_hydraulic_brace", BRACE_COOLDOWN_TICKS);
		com.projecthero.mod.ironman.ability.era1.IronManEraOne.payAmount(p, suit, BRACE_ENERGY);
		return true;
	}

	/** Gametests: the Hulkbuster's C as a tap ({@code holdTicks} < 20) or a hold. */
	public static boolean hulkbusterCForTests(ServerPlayer p, IronManSuit suit, int holdTicks) {
		IronManComicState.rt(p).cPressAt = p.level().getGameTime() - holdTicks;
		IronManComicState.rt(p).cHeld = true;
		return braceOrSentry(p, suit, false);
	}

	private static boolean brace(ServerPlayer p) {
		Rt r = IronManComicState.rt(p);
		r.braceUntil = p.level().getGameTime() + BRACE_TICKS;
		IronManComicFx.sound(p, IronManSounds.CLAMP, 1.2f, 0.5f);
		IronManComicFx.sound(p, IronManSounds.SERVO, 1.0f, 0.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.brace"), true);
		return true;
	}

	private static boolean replacementPod(ServerPlayer p, IronManSuit suit) {
		float cur = IronManEnergy.integrity(p, suit.id());
		if (cur >= suit.maxIntegrity()) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.pod_not_needed"), true);
			return false;
		}
		IronManComicFx.pose(p, IronManAbilityFx.HB_REPLACEMENT_POD, HB_POD_TICKS);
		IronManSounds.play(p, IronManSounds.TARGET_LOCK, 1.0f, 0.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.pod_inbound"), true);
		String suitId = suit.id();
		for (int i = 0; i < POD_DELAY_TICKS / 2; i++) {
			final int n = i;
			IronManComicState.later(p, 1 + i * 2, w -> {
				// the pod's streak coming down out of the sky onto the suit
				double y = w.getY() + 2.0 * (POD_DELAY_TICKS / 2 - n) + 3.0;
				IronManComicFx.level(w).sendParticles(ParticleTypes.FLAME, w.getX(), y, w.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
				IronManComicFx.level(w).sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), y + 1.5, w.getZ(), 3, 0.3, 0.8, 0.3, 0.02);
			});
		}
		IronManComicState.later(p, POD_DELAY_TICKS, w -> {
			if (!suitId.equals(IronManArmor.wornSuitId(w))) {
				return;
			}
			IronManSuit s = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
			float max = s == null ? IronManEnergy.maxIntegrity(suitId) : s.maxIntegrity();
			IronManEnergy.setIntegrity(w, suitId, Math.min(max, IronManEnergy.integrity(w, suitId) + max * POD_REPAIR_FRACTION));
			ServerLevel level = IronManComicFx.level(w);
			level.sendParticles(ParticleTypes.EXPLOSION, w.getX(), w.getY() + 1, w.getZ(), 2, 0.4, 0.4, 0.4, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, w.getX(), w.getY() + 1, w.getZ(), 40, 0.6, 1.0, 0.6, 0.2);
			IronManComicFx.soundAt(level, w.position(), IronManSounds.POD_LAND, 1.2f, 1.0f);
			IronManSounds.play(w, IronManSounds.POWER_UP, 1.0f, 1.0f);
			w.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.pod_repaired",
					Math.round(POD_REPAIR_FRACTION * 100)).withStyle(ChatFormatting.GREEN), true);
		});
		return true;
	}

	// ================================================================== holds / throws

	private static void startHold(ServerPlayer p, Rt r, HoldKind kind, LivingEntity t, int ticks) {
		long now = p.level().getGameTime();
		r.hold = kind;
		r.holdTarget = t.getId();
		r.holdStart = now;
		r.holdUntil = now + ticks;
		r.holdAnchor = null;
	}

	static void tickHold(ServerPlayer p, IronManSuit suit, Rt r) {
		long now = p.level().getGameTime();
		Entity raw = p.level().getEntity(r.holdTarget);
		if (!(raw instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > 48 * 48) {
			r.hold = null;
			r.holdTarget = -1;
			return;
		}
		ServerLevel level = IronManComicFx.level(p);
		long age = now - r.holdStart;
		boolean over = now >= r.holdUntil;
		switch (r.hold) {
			case TRACTOR -> {
				Vec3 point = p.getEyePosition().add(p.getLookAngle().scale(4.0 * p.getScale()));
				IronManComicFx.pull(t, point, 0.7);
				t.resetFallDistance();
				IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_MAGNET);
				if (age % 2 == 0) {
					IronManAbilities.broadcastBeam(p, IronManComicFx.palm(p), IronManComicFx.centre(t), IronManComicFx.BEAM_TRACTOR);
				}
				if (over) {
					// held too long: it drops (the release still throws if the key is let go later -- nothing to throw)
					r.hold = null;
					r.holdTarget = -1;
				}
			}
			case TETHER -> {
				Vec3 anchor = r.holdAnchor == null ? t.position().add(0, 3.0, 0) : r.holdAnchor;
				IronManComicFx.pull(t, anchor, 0.5);
				t.resetFallDistance();
				if (t instanceof Mob m) {
					m.getNavigation().stop();
				}
				if (age % 20 == 0 && age > 0) {
					IronManComicFx.hit(p, t, 2f);
				}
				if (age % 2 == 0) {
					IronManAbilities.broadcastBeam(p, IronManComicFx.palm(p), IronManComicFx.centre(t), IronManComicFx.BEAM_TRACTOR);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 4, 0.4, 0.4, 0.4,
							0.02);
				}
				if (over) {
					r.hold = null;
					r.holdTarget = -1;
				}
			}
			case ANCHOR -> {
				t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1, 0.2).add(0, t.isInWater() ? -0.35 : -0.25, 0));
				t.hurtMarked = true;
				if (age % 4 == 0) {
					level.sendParticles(t.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.CRIT, t.getX(), t.getY() + 0.3, t.getZ(), 6, 0.3,
							0.2, 0.3, 0.02);
				}
				if (over) {
					r.hold = null;
					r.holdTarget = -1;
				}
			}
			case GRAB_POUND, GRAB_THROW -> {
				boolean pound = r.hold == HoldKind.GRAB_POUND;
				IronManComicKit.Move m = IronManComicKit.byId(pound ? "mk8_grab_pound" : "mk8_grab_throw");
				float dmg = m == null ? 18f : m.damage();
				Vec3 hands = p.getEyePosition().add(p.getLookAngle().scale(1.8 * p.getScale())).add(0, -0.6 * p.getScale(), 0);
				if (age < HB_GRAB_SEIZE) {
					// the hands are still reaching: the target only stops in its tracks
					t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1, 0.2));
					t.hurtMarked = true;
					return;
				}
				t.teleportTo(hands.x, hands.y - t.getBbHeight() * 0.5, hands.z);
				t.setDeltaMovement(Vec3.ZERO);
				t.resetFallDistance();
				if (age == HB_GRAB_SEIZE) {
					IronManComicFx.sound(p, IronManSounds.CLAMP, 1.2f, 0.7f);
				}
				if (age == HB_GRAB_FOLLOW) {
					IronManComicFx.pose(p, pound ? IronManAbilityFx.HB_GRAB_POUND : IronManAbilityFx.HB_GRAB_THROW,
							pound ? HB_POUND_TICKS : HB_THROW_TICKS);
				}
				if (pound) {
					long k = age - HB_GRAB_FOLLOW;
					if (k == HB_POUND_HITS[0] || k == HB_POUND_HITS[1]) {
						IronManComicFx.hit(p, t, dmg);
						level.sendParticles(ParticleTypes.CRIT, hands.x, hands.y, hands.z, 12, 0.3, 0.3, 0.3, 0.2);
						IronManComicFx.soundAt(level, hands, SoundEvents.ANVIL_LAND, 0.6f, 0.7f + k * 0.01f);
					}
					if (over) {
						r.hold = null;
						r.holdTarget = -1;
						IronManComicFx.shove(t, p.position(), 0.8, 0.2);
					}
				} else if (over) {
					// the throw lands on the grab_throw clip's release tick
					r.hold = null;
					r.holdTarget = -1;
					Vec3 look = p.getLookAngle();
					t.setDeltaMovement(look.scale(2.8).add(0, 0.5, 0));
					t.hurtMarked = true;
					t.hasImpulse = true;
					IronManComicFx.hit(p, t, dmg);
					r.thrownTarget = t.getId();
					r.thrownUntil = now + 40;
					r.thrownDamage = dmg;
					IronManComicFx.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
				}
			}
		}
	}

	/** A thrown target hurts again when it smacks into a wall or the ground. */
	static void tickThrown(ServerPlayer p, Rt r) {
		long now = p.level().getGameTime();
		Entity raw = p.level().getEntity(r.thrownTarget);
		if (!(raw instanceof LivingEntity t) || !t.isAlive() || now > r.thrownUntil) {
			r.thrownTarget = -1;
			return;
		}
		if (t.horizontalCollision || t.onGround() && now > r.thrownUntil - 26) {
			r.thrownTarget = -1;
			IronManComicFx.hit(p, t, r.thrownDamage);
			ServerLevel level = IronManComicFx.level(p);
			IronManComicFx.groundRing(level, t.position(), 1.2, 10);
			IronManComicFx.soundAt(level, t.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.8f);
		}
	}

	/** The wearer is a player; never pick a squadmate etc. (helper for tests). */
	static boolean isPlayer(Entity e) {
		return e instanceof Player;
	}
}
