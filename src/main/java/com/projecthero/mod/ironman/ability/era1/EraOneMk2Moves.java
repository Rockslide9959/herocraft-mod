package com.projecthero.mod.ironman.ability.era1;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.ability.IronManComicAccess;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManDash;
import com.projecthero.mod.ironman.ability.era1.EraOneMk12State.Rt;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I: the Mk II GOLDEN AVENGER moves (plan: {@code docs/IRONMAN_ERA1_PLAN.md}, kit ids {@code e1mk2_*}).
 * The Mk I rebuilt in gold: the first palm repulsors, a gold uppercut and a slam, the boot-jet hover (its only sustained
 * air time), the jet dash and roller-skate jets, the chest-lamp glare, magnets, and the shared Reactor Overload.
 */
public final class EraOneMk2Moves {
	public static final double REPULSOR_RANGE = 30.0;
	public static final double CHARGED_RANGE = 40.0;
	public static final double UPPERCUT_REACH = 4.0;
	/** The uppercut throws the mob straight up at this speed (~5 blocks), less its knockback resistance. */
	public static final double UPPERCUT_LAUNCH = 1.1;
	public static final double GLARE_RANGE = 12.0;
	public static final double GLARE_HALF_DEG = 35.0;
	public static final double PULL_RADIUS = 14.0;
	/** Magnetic Shield: projectiles within this many blocks are turned back. */
	public static final double SHIELD_RADIUS = 3.5;
	/** Jet Skates: Speed IV while they run. */
	public static final int SKATES_SPEED_AMP = 3;
	private static final DustParticleOptions GOLD = new DustParticleOptions(new org.joml.Vector3f(1f, 0.8f, 0.2f), 1.4f);

	private EraOneMk2Moves() {
	}

	static void init() {
		EraOneMk12State.initialize();
		IronManEraOne.register("e1mk2_repulsor", EraOneMk1Moves.tap(EraOneMk2Moves::repulsor));
		IronManEraOne.register("e1mk2_charged_repulsor", EraOneMk2Moves::chargedRepulsor);
		IronManEraOne.register("e1mk2_gold_uppercut", EraOneMk1Moves.tap(EraOneMk2Moves::goldUppercut));
		IronManEraOne.register("e1mk2_ground_pound", EraOneMk1Moves.tap(EraOneMk2Moves::groundPound));
		IronManEraOne.register("e1mk2_boot_hover", EraOneMk1Moves.tap(EraOneMk2Moves::bootHover));
		IronManEraOne.register("e1mk2_jet_dash", EraOneMk1Moves.tap((p, suit, m) -> IronManDash.start(p, m.damage(), 0f, 0)));
		IronManEraOne.register("e1mk2_golden_glare", EraOneMk1Moves.tap(EraOneMk2Moves::goldenGlare));
		IronManEraOne.register("e1mk2_jet_skates", EraOneMk1Moves.tap(EraOneMk2Moves::jetSkates));
		IronManEraOne.register("e1mk2_magnetic_pull", EraOneMk1Moves.tap(EraOneMk2Moves::magneticPull));
		IronManEraOne.register("e1mk2_magnetic_shield", EraOneMk1Moves.tap(EraOneMk2Moves::magneticShield));
		IronManEraOne.register("e1mk2_reactor_overload", EraOneMk1Moves.tap(EraOneMk1Moves::reactorOverload));
	}

	// ================================================================== R: repulsor / charged repulsor

	/** 12, a single palm bolt at the aim (the lock first). */
	private static boolean repulsor(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		IronManComicAccess.repulsor(p, m.damage(), REPULSOR_RANGE, IronManComicAccess.BEAM_REPULSOR);
		return true;
	}

	/**
	 * HOLD: the press commits (pays, cooldown) and spins the palm up; the release fires -- 26 after a 1 s charge, less
	 * (down to half) if let go sooner. A 3 s hold fires on its own.
	 */
	private static boolean chargedRepulsor(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		if (pressed) {
			if (r.chargeStart != 0L) {
				return false;
			}
			r.chargeStart = now;
			IronManSounds.move(p, IronManSounds.REPULSOR_CHARGE, 0.7f, 0.7f);
			return true;
		}
		if (r.chargeStart == 0L) {
			return false;
		}
		fireCharged(p, r, now);
		return false;
	}

	static void chargeWindup(ServerPlayer p, Rt r, long now) {
		long held = now - r.chargeStart;
		IronManAbilityFx.hold(p, IronManAbilityFx.CHARGING);
		Vec3 palm = IronManComicAccess.palm(p);
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, palm.x, palm.y, palm.z, (int) Math.min(8, 1 + held / 3), 0.06, 0.06, 0.06, 0.02);
		if (held == EraOneMk12State.CHARGE_TICKS) {
			level.sendParticles(ParticleTypes.END_ROD, palm.x, palm.y, palm.z, 12, 0.1, 0.1, 0.1, 0.05);
			IronManSounds.play(p, IronManSounds.REPULSOR_READY, 0.7f, 1.0f);
		} else if (held % 6 == 0) {
			IronManSounds.loop(p, IronManSounds.REPULSOR_CHARGE, 0.4f, 0.75f + Math.min(0.65f, held / 40f));
		}
		if (held >= EraOneMk12State.CHARGE_MAX_HOLD) {
			fireCharged(p, r, now);
		}
	}

	private static void fireCharged(ServerPlayer p, Rt r, long now) {
		long held = now - r.chargeStart;
		r.chargeStart = 0L;
		IronManComicKit.Move m = IronManComicKit.byId("e1mk2_charged_repulsor");
		float dmg = (m == null ? 26f : m.damage()) * EraOneMk1Moves.chargeShare(held);
		boolean full = held >= EraOneMk12State.CHARGE_TICKS;
		Vec3 from = IronManComicAccess.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), CHARGED_RANGE);
		IronManComicAccess.beam(p, from, dir, CHARGED_RANGE, full ? 1.0 : 0.7, false, IronManComicAccess.BEAM_CHARGED, dmg, full ? 1.8 : 1.0);
		IronManComicAccess.pose(p, IronManAbilityFx.CHARGED, 12);
		IronManComicAccess.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 1.1f, full ? 0.8f : 1.1f);
	}

	// ================================================================== G: gold uppercut / ground pound

	/** 14 and the mob is thrown straight up (~5 blocks). A swing at nothing still goes off. */
	private static boolean goldUppercut(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		LivingEntity t = IronManComicAccess.target(p, UPPERCUT_REACH);
		if (t == null) {
			var close = IronManComicAccess.cone(p, p.getEyePosition(), p.getLookAngle(), UPPERCUT_REACH, 40.0);
			t = close.isEmpty() ? null : close.get(0);
		}
		IronManComicAccess.pose(p, IronManAbilityFx.PUNCH, 12);
		IronManSounds.move(p, IronManSounds.PUNCH, 1.1f, 0.9f);
		if (t == null) {
			IronManSounds.play(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.8f);
			return true;
		}
		IronManComicAccess.hit(p, t, m.damage());
		double resist = 1.0 - Math.min(0.9, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6);
		Vec3 v = t.getDeltaMovement();
		t.setDeltaMovement(v.x * 0.2, UPPERCUT_LAUNCH * resist, v.z * 0.2);
		t.hurtMarked = true;
		t.hasImpulse = true;
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = IronManComicAccess.centre(t);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.3);
		for (int i = 0; i < 6; i++) {
			level.sendParticles(GOLD, c.x, c.y - 0.6 + i * 0.35, c.z, 3, 0.15, 0.05, 0.15, 0.0);
		}
		IronManComicAccess.soundAt(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
		return true;
	}

	/**
	 * From the air (a hover, a jump): the suit drives straight down and slams -- 16 to everything within 4 blocks of the
	 * impact. Fall-safe. Refused on the ground.
	 */
	private static boolean groundPound(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		if (p.onGround() || EraOneMk12State.groundGap(p, 2.0) < 1.2) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.pound_needs_air"), true);
			return false;
		}
		Rt r = EraOneMk12State.rt(p, suit);
		if (r.hoverUntil != 0L) {
			endHover(p, r);
		}
		r.pounding = true;
		r.poundStart = p.level().getGameTime();
		p.resetFallDistance();
		IronManComicAccess.launch(p, new Vec3(0, -2.2, 0));
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_POUND, 14);
		IronManComicAccess.sound(p, IronManSounds.FLIGHT_BURST, 1.0f, 0.6f);
		return true;
	}

	static void poundTick(ServerPlayer p, Rt r, long now) {
		p.resetFallDistance();
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.5, p.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
		level.sendParticles(GOLD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.0);
		boolean landed = p.onGround() || p.isInWater() || EraOneMk12State.groundGap(p, 2.0) < 0.6
				|| now - r.poundStart >= EraOneMk12State.POUND_MAX_TICKS;
		if (!landed) {
			if (p.getDeltaMovement().y > -1.8) {
				IronManComicAccess.launch(p, new Vec3(p.getDeltaMovement().x * 0.5, -2.2, p.getDeltaMovement().z * 0.5));
			}
			return;
		}
		r.pounding = false;
		IronManComicKit.Move m = IronManComicKit.byId("e1mk2_ground_pound");
		float dmg = m == null ? 16f : m.damage();
		Vec3 c = p.position();
		for (LivingEntity e : IronManComicAccess.around(p, c.add(0, 0.5, 0), EraOneMk12State.POUND_RADIUS)) {
			IronManComicAccess.hit(p, e, dmg);
			IronManComicAccess.shove(e, c, 1.3, 0.55);
		}
		IronManComicAccess.groundRing(level, c, 2.0, 16);
		IronManComicAccess.groundRing(level, c, EraOneMk12State.POUND_RADIUS, 26);
		IronManComicAccess.ring(level, c.add(0, 0.3, 0), EraOneMk12State.POUND_RADIUS * 0.7, GOLD, 24);
		IronManComicAccess.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.75f);
		IronManComicAccess.soundAt(level, c, SoundEvents.ANVIL_LAND, 0.8f, 0.55f);
		IronManComicAccess.pose(p, IronManAbilityFx.GROUND_POUND, 12);
		p.resetFallDistance();
	}

	// ================================================================== X: boot hover (jet dash is the shared IronManDash)

	/** 5 s on the boot jets (Space up, Shift down -- the suit's own flight controls); tap again to drop out early. */
	private static boolean bootHover(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		if (r.hoverUntil != 0L) {
			endHover(p, r);
			return false;
		}
		long now = p.level().getGameTime();
		r.hoverUntil = now + EraOneMk12State.HOVER_TICKS;
		if (p.onGround()) {
			IronManComicAccess.launch(p, p.getDeltaMovement().add(0, 0.6, 0));
		}
		IronManFlight.setFlying(p, true);
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 16, 0.2, 0.05, 0.2, 0.04);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.4, 0.05, 0.4, 0.05);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_LAUNCH, 8);
		IronManComicAccess.sound(p, IronManSounds.THRUSTER, 0.9f, 1.1f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.hover_on"), true);
		return true;
	}

	static void hoverTick(ServerPlayer p, IronManSuit suit, Rt r, long now) {
		p.resetFallDistance();
		if (now >= r.hoverUntil || !IronManFlight.isFlying(p) || suit == null || !EraOneMk1Moves.drain(p, suit, "e1mk2_boot_hover")) {
			endHover(p, r);
			return;
		}
		if (now % 2 == 0) {
			ServerLevel level = IronManComicAccess.level(p);
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() - 0.1, p.getZ(), 2, 0.12, 0.05, 0.12, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() - 0.2, p.getZ(), 1, 0.12, 0.05, 0.12, 0.01);
		}
		if (r.hoverUntil - now == 20) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.hover_ending"), true);
		}
	}

	static void endHover(ServerPlayer p, Rt r) {
		r.hoverUntil = 0L;
		if (IronManFlight.isFlying(p)) {
			IronManFlight.setFlying(p, false);
		}
		p.resetFallDistance();
		IronManSounds.play(p, IronManSounds.THRUSTER, 0.5f, 0.6f);
	}

	// ================================================================== Z: golden glare / jet skates

	/** The chest lamp at full blast: everything in a 12-block cone is blinded 4 s and the mobs lose you. */
	private static boolean goldenGlare(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 from = IronManComicAccess.chest(p);
		var dazzled = IronManComicAccess.cone(p, from, p.getLookAngle(), GLARE_RANGE, GLARE_HALF_DEG);
		IronManComicAccess.lampFlash(p, GLARE_RANGE, GLARE_HALF_DEG, EraOneMk12State.GLARE_TICKS);
		Rt r = EraOneMk12State.rt(p, suit);
		long until = p.level().getGameTime() + EraOneMk12State.GLARE_TICKS;
		ServerLevel level = IronManComicAccess.level(p);
		for (LivingEntity e : dazzled) {
			if (e instanceof Mob mob) {
				r.glared.put(mob.getId(), until);
				mob.getNavigation().stop();
			}
			level.sendParticles(GOLD, e.getX(), e.getEyeY(), e.getZ(), 6, 0.2, 0.15, 0.2, 0.0);
		}
		IronManComicAccess.soundAt(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
		return true;
	}

	/** 8 s of jets in the boots: a fast ground glide (Speed IV, steps up full blocks). */
	private static boolean jetSkates(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		EraOneMk12State.startSkates(p, r, p.level().getGameTime());
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EraOneMk12State.SKATES_TICKS, SKATES_SPEED_AMP, true, false, true));
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.05, p.getZ(), 14, 0.3, 0.02, 0.3, 0.04);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.05, p.getZ(), 10, 0.3, 0.02, 0.3, 0.1);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_LAUNCH, 8);
		IronManComicAccess.sound(p, IronManSounds.THRUSTER, 0.8f, 1.4f);
		return true;
	}

	// ================================================================== V: magnetic pull / magnetic shield

	/** Everything loose or alive within 14 blocks is yanked in toward you (no damage). */
	private static boolean magneticPull(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 to = p.position().add(EraOneMk1Moves.flatLook(p).scale(2.0));
		for (LivingEntity e : IronManComicAccess.around(p, p.position(), PULL_RADIUS)) {
			Vec3 d = to.subtract(e.position());
			double len = d.length();
			if (len < 0.8) {
				continue;
			}
			double strength = Math.min(2.2, 0.35 + len * 0.12)
					* (1.0 - Math.min(0.8, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
			e.setDeltaMovement(d.scale(strength / len).add(0, 0.3, 0));
			e.hurtMarked = true;
			e.hasImpulse = true;
			IronManComicAccess.line(level, IronManComicAccess.palm(p), IronManComicAccess.centre(e), ParticleTypes.ELECTRIC_SPARK, 2.0);
		}
		IronManComicAccess.pullItems(p, to, PULL_RADIUS, 0.9);
		IronManComicAccess.ring(level, p.position().add(0, 1.0, 0), 2.0, ParticleTypes.ELECTRIC_SPARK, 20);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_MAGNET, 14);
		IronManComicAccess.soundAt(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.9f);
		return true;
	}

	/** 5 s: arrows and every other projectile are turned back (and never hurt you). */
	private static boolean magneticShield(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		r.shieldUntil = p.level().getGameTime() + EraOneMk12State.SHIELD_TICKS;
		IronManComicAccess.ring(IronManComicAccess.level(p), p.position().add(0, 1.0, 0), SHIELD_RADIUS, ParticleTypes.ELECTRIC_SPARK, 30);
		IronManComicAccess.pose(p, IronManAbilityFx.BARRIER, 10);
		IronManComicAccess.sound(p, IronManSounds.SHIELD_UP, 1.0f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.shield_on"), true);
		return true;
	}

	static void shieldTick(ServerPlayer p, Rt r, long now) {
		ServerLevel level = IronManComicAccess.level(p);
		IronManAbilityFx.hold(p, IronManAbilityFx.BARRIER);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		if (now % 4 == 0) {
			IronManComicAccess.ring(level, c, 1.3, ParticleTypes.ELECTRIC_SPARK, 10);
		}
		if (now % 20 == 0) {
			IronManSounds.play(p, IronManSounds.SHIELD_HUM, 0.35f, 1.3f);
		}
		deflect(p, c);
	}

	/** Turn back every projectile near the wearer that is not already theirs. Returns how many. Public for the tests. */
	public static int deflect(ServerPlayer p, Vec3 c) {
		int n = 0;
		ServerLevel level = IronManComicAccess.level(p);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(SHIELD_RADIUS))) {
			Entity owner = proj.getOwner();
			if (owner == p || proj.isRemoved()) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			Vec3 away = proj.position().subtract(c);
			if (away.lengthSqr() < 1.0e-4) {
				away = p.getLookAngle();
			}
			double speed = Math.max(0.6, v.length() * 0.9);
			proj.setDeltaMovement(away.normalize().scale(speed).add(0, 0.1, 0));
			proj.setOwner(p);
			proj.hurtMarked = true;
			proj.hasImpulse = true;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(), 10, 0.15, 0.15, 0.15, 0.15);
			IronManSounds.play(p, IronManSounds.SHIELD_DEFLECT, 0.8f, 1.3f);
			n++;
		}
		return n;
	}
}
