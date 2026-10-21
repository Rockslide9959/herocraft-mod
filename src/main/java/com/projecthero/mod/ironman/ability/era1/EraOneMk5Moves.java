package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicMk5Access;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I: the Mk V Stealth moves ({@code docs/IRONMAN_ERA1_PLAN.md}, "Mk V Stealth"). Twelve TAP moves; each
 * registers a handler with {@link IronManEraOne#register} in {@link #init} and returns true when it went off (the router
 * then starts its cooldown and pays its energy). Everything is QUIET: low, soft sounds and sparse particles, the suit's
 * own silence (no steps / swim sounds / vibrations) is kept by its mixins.
 *
 * <ul>
 * <li>R silenced_dart: 6 + Poison II 4 s + Slowness II 4 s (40 blocks). Shift sleeper_dart: the mob sleeps 8 s (never
 * bosses / players; any hit wakes it).</li>
 * <li>G silent_takedown: 15, x3 from behind or on an unaware mob. Shift shadow_strike: blink behind the target you look
 * at (16 blocks) + the takedown hit.</li>
 * <li>X light_bend_blink: an 8-block invisible dash. Shift active_camo (toggle, v0.16.7 (user) -- replaced the Silent Glide
 * once the Mk V got real flight): the wearer AND the suit vanish, mobs lose track; drains 3/s, any hit given or taken
 * breaks it for 5 s.</li>
 * <li>Z emp: a 10-block pulse, mobs out 3 s, drones / Ultron / sentries 6 s. Shift decoy: a hologram of you for 10 s that
 * mobs go for (the Mark 5 Holo-Decoy).</li>
 * <li>V radar: mobs within 32 marked 8 s, privately. Shift thermal_vision: living things through walls 10 s, privately.</li>
 * <li>C silent_running (toggle): mobs cannot pick you as a target; attacking breaks it for 5 s.</li>
 * </ul>
 * Running state: {@link EraOneMk5Stealth}.
 */
public final class EraOneMk5Moves {
	public static final double DART_RANGE = 40.0;
	public static final int DART_TICKS = 4 * 20;
	public static final double TAKEDOWN_REACH = 4.5;
	public static final float TAKEDOWN_MULT = 3f;
	public static final double SHADOW_RANGE = 16.0;
	public static final double BLINK_RANGE = 8.0;
	public static final int BLINK_INVIS_TICKS = 2 * 20;
	public static final double EMP_RADIUS = 10.0;
	public static final int EMP_TICKS = 3 * 20;
	public static final int EMP_MACHINE_TICKS = 6 * 20;
	public static final int DECOY_TICKS = 10 * 20;
	public static final double RADAR_RANGE = 32.0;
	public static final int RADAR_TICKS = 8 * 20;

	private EraOneMk5Moves() {
	}

	static void init() {
		EraOneMk5Stealth.initialize();
		IronManEraOne.register("e1mk5_silenced_dart", (p, suit, m, pressed) -> silencedDart(p, m.damage()));
		IronManEraOne.register("e1mk5_sleeper_dart", (p, suit, m, pressed) -> sleeperDart(p));
		IronManEraOne.register("e1mk5_silent_takedown", (p, suit, m, pressed) -> silentTakedown(p, m.damage()));
		IronManEraOne.register("e1mk5_shadow_strike", (p, suit, m, pressed) -> shadowStrike(p, m.damage()));
		IronManEraOne.register("e1mk5_light_bend_blink", (p, suit, m, pressed) -> lightBendBlink(p));
		IronManEraOne.register("e1mk5_active_camo", (p, suit, m, pressed) -> activeCamo(p));
		IronManEraOne.register("e1mk5_emp", (p, suit, m, pressed) -> emp(p));
		IronManEraOne.register("e1mk5_decoy", (p, suit, m, pressed) -> decoy(p));
		IronManEraOne.register("e1mk5_radar", (p, suit, m, pressed) -> radar(p));
		IronManEraOne.register("e1mk5_thermal_vision", (p, suit, m, pressed) -> thermalVision(p));
		IronManEraOne.register("e1mk5_silent_running", (p, suit, m, pressed) -> silentRunning(p));
	}

	/**
	 * The per-tick energy drain of a toggled Mk V move ({@link IronManComicKit#energyPerSecond}: Active Camo 3/s, Silent
	 * Running 2/s). Returns false when the suit cannot pay or is browned out (the status ends).
	 */
	static boolean drain(ServerPlayer p, String moveId) {
		return IronManEraOne.drainTick(p, com.projecthero.mod.ironman.suit.IronManSuits.MARK_V, moveId);
	}

	// ------------------------------------------------------------------ helpers

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5." + key, args), true);
	}

	/** A soft sound: the Mk V is never loud. */
	private static void hush(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
		IronManSounds.play(p, s, volume, pitch);
	}

	/** {@code e}'s facing on the ground plane. */
	private static Vec3 facing(LivingEntity e) {
		float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Is {@code p} behind {@code e} (more than ~105 degrees off its facing)? */
	public static boolean behind(ServerPlayer p, LivingEntity e) {
		Vec3 to = new Vec3(p.getX() - e.getX(), 0, p.getZ() - e.getZ());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		return facing(e).dot(to.normalize()) < -0.25;
	}

	/** Bosses never sleep (the boss checks the other powers use). */
	public static boolean boss(LivingEntity e) {
		return com.projecthero.mod.kryptonian.KryptonianCombat.isBoss(e) || com.projecthero.mod.titanshifter.TitanCombat.isBoss(e);
	}

	/** Drones, Ultron's units, sentries, golems: the EMP shuts them down twice as long. */
	public static boolean machine(LivingEntity e) {
		return IronManComicMk5Access.robotic(e) || e instanceof com.projecthero.mod.sentinel.entity.SentinelRobot;
	}

	/** Where {@code p}'s box fits with its feet at {@code at} (nudged up to a block for a step), or null. */
	private static Vec3 fit(ServerPlayer p, Vec3 at) {
		for (double up = 0.0; up <= 1.01; up += 0.5) {
			Vec3 c = at.add(0, up, 0);
			if (p.level().noCollision(p, p.getBoundingBox().move(c.subtract(p.position())))) {
				return c;
			}
		}
		return null;
	}

	private static void slip(ServerPlayer p, Vec3 to, float yaw, float pitch) {
		p.teleportTo((ServerLevel) p.level(), to.x, to.y, to.z, yaw, pitch);
		p.resetFallDistance();
	}

	/** The takedown blow: {@code damage}, x3 from behind or on an unaware mob. Returns whether it was the silent x3. */
	private static boolean takedownHit(ServerPlayer p, LivingEntity t, float damage) {
		boolean silent = behind(p, t) || IronManComicMk5Access.unaware(p, t);
		IronManComicMk5Access.hit(p, t, silent ? damage * TAKEDOWN_MULT : damage);
		ServerLevel level = IronManComicMk5Access.level(p);
		Vec3 c = IronManComicMk5Access.centre(t);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, silent ? 20 : 8, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		IronManComicMk5Access.pose(p, IronManAbilityFx.PUNCH, 10);
		hush(p, SoundEvents.PLAYER_ATTACK_CRIT, 0.3f, 0.7f);
		if (silent) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.takedown"), true);
		}
		return silent;
	}

	// ================================================================== R

	/** R: a near-silent dart -- 6, Poison II + Slowness II for 4 s on what it hits. Fires even on a miss. */
	static boolean silencedDart(ServerPlayer p, float damage) {
		Vec3 from = IronManComicMk5Access.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), DART_RANGE);
		List<LivingEntity> hits = IronManComicMk5Access.stealthBeam(p, from, dir, DART_RANGE, damage);
		ServerLevel level = IronManComicMk5Access.level(p);
		for (LivingEntity e : hits) {
			IronManComicMk5Access.control(e, MobEffects.POISON, DART_TICKS, 1);
			IronManComicMk5Access.control(e, MobEffects.MOVEMENT_SLOWDOWN, DART_TICKS, 1);
			Vec3 c = IronManComicMk5Access.centre(e);
			level.sendParticles(ParticleTypes.SNEEZE, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.01);
		}
		IronManComicMk5Access.pose(p, IronManAbilityFx.REPULSOR, 8);
		hush(p, SoundEvents.ARROW_SHOOT, 0.15f, 1.9f);
		return true;
	}

	/** Shift+R: the mob sleeps 8 s (no AI, cannot move or attack); never a boss or a player; any hit wakes it. */
	static boolean sleeperDart(ServerPlayer p) {
		LivingEntity aimed = IronManComicMk5Access.target(p, DART_RANGE);
		if (aimed == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		Vec3 from = IronManComicMk5Access.palm(p);
		Vec3 dir = IronManComicMk5Access.centre(aimed).subtract(from).normalize();
		LivingEntity t = aimed;
		List<LivingEntity> line = IronManComicMk5Access.stealthBeam(p, from, dir, DART_RANGE, 0f);
		if (!line.isEmpty()) {
			t = line.get(0); // whatever stood in the way took the dart
		}
		IronManComicMk5Access.pose(p, IronManAbilityFx.REPULSOR, 8);
		hush(p, SoundEvents.ARROW_SHOOT, 0.15f, 1.6f);
		if (t instanceof Player) {
			say(p, "sleep_player");
			return true;
		}
		if (!(t instanceof Mob mob) || boss(t)) {
			say(p, "sleep_resisted");
			return true;
		}
		EraOneMk5Stealth.freeze(mob, EraOneMk5Stealth.SLEEP_TICKS, true);
		Vec3 c = IronManComicMk5Access.centre(mob);
		IronManComicMk5Access.level(p).sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.4, c.z, 8, 0.25, 0.25, 0.25, 0.01);
		say(p, "asleep", EraOneMk5Stealth.SLEEP_TICKS / 20);
		return true;
	}

	// ================================================================== G

	/** G: 15, x3 from behind or on a mob that is not on you. */
	static boolean silentTakedown(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicMk5Access.target(p, TAKEDOWN_REACH);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		takedownHit(p, t, damage);
		return true;
	}

	/** Shift+G: blink behind the target you look at (16 blocks), then the takedown blow. */
	static boolean shadowStrike(ServerPlayer p, float damage) {
		LivingEntity t = IronManComicMk5Access.target(p, SHADOW_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		Vec3 back = facing(t).scale(-1.0);
		Vec3 side = new Vec3(-back.z, 0, back.x);
		double gap = t.getBbWidth() * 0.5 + 0.8;
		Vec3 spot = null;
		for (Vec3 dir : new Vec3[] { back, back.add(side).normalize(), back.subtract(side).normalize(), side, side.scale(-1.0) }) {
			spot = fit(p, t.position().add(dir.scale(gap)));
			if (spot != null) {
				break;
			}
		}
		if (spot == null) {
			say(p, "no_room");
			return false;
		}
		ServerLevel level = IronManComicMk5Access.level(p);
		Vec3 was = p.position();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 12, 0.3, 0.6, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, was.x, was.y + 1, was.z, 8, 0.3, 0.6, 0.3, 0.01);
		Vec3 look = IronManComicMk5Access.centre(t).subtract(spot.add(0, p.getEyeHeight(), 0));
		float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
		slip(p, spot, yaw, pitch);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1, spot.z, 12, 0.3, 0.6, 0.3, 0.02);
		hush(p, SoundEvents.ENDERMAN_TELEPORT, 0.15f, 1.7f);
		takedownHit(p, t, damage);
		return true;
	}

	// ================================================================== X

	/** X: an 8-block dash along the look, invisible for 2 s; whatever was on you loses you. */
	static boolean lightBendBlink(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double d = Math.min(BLINK_RANGE, IronManComicMk5Access.wallDistance(p, eye, look, BLINK_RANGE) - 0.6);
		Vec3 to = null;
		for (; d >= 1.5 && to == null; d -= 0.5) {
			to = fit(p, p.position().add(look.scale(d)));
		}
		if (to == null) {
			say(p, "no_room");
			return false;
		}
		ServerLevel level = IronManComicMk5Access.level(p);
		Vec3 was = p.position();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 14, 0.3, 0.6, 0.3, 0.02);
		level.sendParticles(ParticleTypes.GLOW, was.x, was.y + 1, was.z, 6, 0.25, 0.6, 0.25, 0.01);
		slip(p, to, p.getYRot(), p.getXRot());
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, BLINK_INVIS_TICKS, 0, true, false, false));
		EraOneMk5Stealth.shake(p, 16.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 14, 0.3, 0.6, 0.3, 0.02);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 6);
		hush(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.2f, 1.6f);
		return true;
	}

	/**
	 * Shift+X (toggle, v0.16.7 (user)): Active Camo -- the wearer and the whole suit vanish (Invisibility, and the armour
	 * layers / GeckoLib suit are not drawn on an invisible Iron Man -- {@code HumanoidArmorLayerMixin}); mobs on him lose
	 * him and cannot pick him while it holds. Drains 3 energy/s; a hit given or taken breaks it for 5 s; never while
	 * browned out (the router's {@link IronManEraOne#canPay} refuses the press, {@link #drain} ends it).
	 */
	static boolean activeCamo(ServerPlayer p) {
		ServerLevel level = IronManComicMk5Access.level(p);
		if (EraOneMk5Stealth.camo(p)) {
			EraOneMk5Stealth.setCamo(p, false);
			level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.6, 0.3, 0.01);
			hush(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.15f, 1.2f);
			say(p, "camo_off");
			return true;
		}
		if (IronManEraOne.brownedOut(p, com.projecthero.mod.ironman.suit.IronManSuits.MARK_V)) {
			return false;
		}
		EraOneMk5Stealth.setCamo(p, true);
		int lost = EraOneMk5Stealth.shake(p, EraOneMk5Stealth.RUNNING_SHAKE_RANGE);
		// the shimmer of the suit bending the light round itself -- one soft ripple, then nothing
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.3, 0.7, 0.3, 0.01);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 6);
		hush(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.2f, 1.7f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.camo_on", lost)
				.withStyle(ChatFormatting.DARK_AQUA), true);
		return true;
	}

	// ================================================================== Z

	/** Z: a 10-block pulse -- mobs out 3 s, drones / Ultron units / sentries 6 s (bosses only slowed, half as long). */
	static boolean emp(ServerPlayer p) {
		ServerLevel level = IronManComicMk5Access.level(p);
		int n = 0;
		for (LivingEntity e : IronManComicMk5Access.around(p, p.position(), EMP_RADIUS)) {
			int ticks = machine(e) ? EMP_MACHINE_TICKS : EMP_TICKS;
			if (e instanceof Mob m) {
				if (boss(e)) {
					IronManComicMk5Access.stun(m, ticks / 2);
				} else {
					IronManComicMk5Access.stun(m, ticks);
					EraOneMk5Stealth.freeze(m, ticks, false);
				}
				n++;
			} else {
				IronManComicMk5Access.control(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 3);
			}
		}
		Vec3 c = p.position().add(0, 1.0, 0);
		IronManComicMk5Access.ring(level, c, 3.0, ParticleTypes.ELECTRIC_SPARK, 24);
		IronManComicMk5Access.ring(level, c, 6.5, ParticleTypes.ELECTRIC_SPARK, 40);
		IronManComicMk5Access.ring(level, c, EMP_RADIUS, ParticleTypes.ELECTRIC_SPARK, 60);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		hush(p, SoundEvents.BEACON_DEACTIVATE, 0.45f, 1.6f);
		say(p, "emp", n);
		return true;
	}

	/** Shift+Z: a hologram of you stands 10 s where you are; mobs on you go for it instead (the Mark 5 Holo-Decoy). */
	static boolean decoy(ServerPlayer p) {
		IronManComicMk5Access.decoy(p, DECOY_TICKS);
		ServerLevel level = IronManComicMk5Access.level(p);
		level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.3, 0.8, 0.3, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.25, 0.8, 0.25, 0.0);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 8);
		hush(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.25f, 1.4f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.decoy"), true);
		return true;
	}

	// ================================================================== V

	/** V: every mob within 32 outlined for 8 s -- for the wearer alone. */
	static boolean radar(ServerPlayer p) {
		List<LivingEntity> found = new ArrayList<>();
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RADAR_RANGE),
				m -> m.isAlive() && m.distanceToSqr(p) <= RADAR_RANGE * RADAR_RANGE)) {
			found.add(m);
		}
		IronManComicMk5Access.privateMark(p, found, RADAR_TICKS);
		// the sweep ring is drawn for the wearer only (it would give him away)
		ServerLevel level = IronManComicMk5Access.level(p);
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2 / 32;
			level.sendParticles(p, ParticleTypes.ELECTRIC_SPARK, false, p.getX() + Math.cos(a) * 2.5, p.getY() + 0.2,
					p.getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
		}
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 6);
		p.playNotifySound(IronManSounds.SCAN_CHIRP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.marked", found.size()), true);
		return true;
	}

	/** Shift+V: 10 s of seeing every living thing within 32 through walls -- privately. */
	static boolean thermalVision(ServerPlayer p) {
		EraOneMk5Stealth.startThermal(p);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 6);
		p.playNotifySound(IronManSounds.SCAN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.8f);
		say(p, "thermal", EraOneMk5Stealth.THERMAL_TICKS / 20);
		return true;
	}

	// ================================================================== C

	/** C (toggle): Silent Running -- nothing can pick you as a target until you attack (then it is off for 5 s). */
	static boolean silentRunning(ServerPlayer p) {
		ServerLevel level = IronManComicMk5Access.level(p);
		if (EraOneMk5Stealth.silentRunning(p)) {
			EraOneMk5Stealth.setRunning(p, false);
			level.sendParticles(p, ParticleTypes.GLOW, false, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.6, 0.3, 0.01);
			p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.8f);
			say(p, "running_off");
			return true;
		}
		EraOneMk5Stealth.setRunning(p, true);
		int lost = EraOneMk5Stealth.shake(p, EraOneMk5Stealth.RUNNING_SHAKE_RANGE);
		level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.6, 0.3, 0.01);
		IronManComicMk5Access.pose(p, IronManAbilityFx.COMIC_GUARD, 8);
		p.playNotifySound(SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.running_on", lost)
				.withStyle(ChatFormatting.DARK_AQUA), true);
		return true;
	}
}
