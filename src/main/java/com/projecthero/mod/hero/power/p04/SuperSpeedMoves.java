package com.projecthero.mod.hero.power.p04;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.SpeedStreakPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.7 Super Speed attacks and tricks:
 * <ul>
 *   <li>G Blitz -- zip to the enemy under the crosshair (up to 24 blocks) and hit it for 20. 4 s.</li>
 *   <li>Shift+R Mach Punch -- one punch whose damage scales with how fast you are running (v0.14.9: a flat 20; was 12 standing, 30 at full
 *       Overdrive speed), a huge knockback and a shockwave ring. 10 s.</li>
 *   <li>Shift+G Speed Vortex -- 3 s of running circles around yourself: a cyclone that drags everything within 8
 *       blocks round and up, 3 damage every half second, then a final blast (8) that flings them out. 14 s.</li>
 *   <li>Shift+X Speed Sweep -- blink from enemy to enemy through everything hostile within 50 blocks (v0.14.16; was 30)
 *       (one hop every 2 ticks, 15 each) until every one of them has been hit, then back to exactly where you stood.
 *       Untouchable while it runs. 10 s.</li>
 * </ul>
 * Rapid Assault's punches also go through {@link #punchThrough} so each of its four blows lands in full.
 * Shift variants keep their own cooldowns as absolute ready-at game times in the power's resources
 * ({@link #MACH_READY}, {@link #VORTEX_READY}, {@link #SWEEP_READY}); the HUD shows them on the R / G / X boxes.
 */
public final class SuperSpeedMoves {
	public static final int YELLOW = 0xFFD83A;
	public static final int RED = 0xFF3030;

	public static final float BLITZ_DAMAGE = 20.0f;
	public static final double BLITZ_RANGE = 24.0;
	/** v0.14.13: Blitz lands with a shockwave -- every other foe within this many blocks of the target takes this much. */
	public static final double BLITZ_AOE_RADIUS = 4.0;
	public static final float BLITZ_AOE_DAMAGE = 10.0f;
	/** v0.14.13: Blitz hits much harder in knockback (was 0.6; a plain hit is 0.4) and pops the target up a little. */
	public static final double BLITZ_KNOCKBACK = 2.0;
	public static final double BLITZ_AOE_KNOCKBACK = 1.4;

	public static final String MACH_READY = "mach_ready";
	public static final int MACH_CD = 8 * 20;
	public static final float MACH_MIN = 20.0f;
	public static final float MACH_MAX = 20.0f; // v0.14.9: a flat 20 at any speed
	/** Blocks per tick at which the Mach Punch is at full power (~64 blocks/s, Overdrive's top speed). */
	public static final double MACH_FULL_SPEED = 3.2;
	public static final float MACH_SHOCKWAVE = 6.0f;

	public static final String VORTEX_READY = "vortex_ready";
	public static final String VORTEX_LEFT = "vortex_left";
	public static final int VORTEX_CD = 14 * 20;
	public static final int VORTEX_TICKS = 3 * 20;
	public static final double VORTEX_RADIUS = 8.0;
	public static final float VORTEX_TICK_DAMAGE = 3.0f;
	public static final float VORTEX_FINAL_DAMAGE = 8.0f;

	public static final String SWEEP_READY = "sweep_ready";
	public static final int SWEEP_CD = 10 * 20; // v0.14.13: was 20 s
	/** v0.14.16: 50 blocks (was 30, and at most 16 targets -- now every one). */
	public static final double SWEEP_RANGE = 50.0;
	public static final int SWEEP_HOP_TICKS = 2;
	public static final float SWEEP_DAMAGE = 15.0f;
	/** v0.14.16: a target with no free spot to land beside it for this long (sweep ticks) is given up on. */
	public static final int SWEEP_UNREACHABLE_TICKS = 5 * 20;
	/** v0.14.16: failsafe -- however many are left, a sweep ends (and brings you home) after this many ticks. */
	public static final int SWEEP_MAX_TICKS = 60 * 20;
	/** v0.14.16: how many of the nearest targets one hop tries before waiting for the next hop. */
	private static final int SWEEP_TRIES_PER_HOP = 8;

	/**
	 * v0.14.16: who a Speed Sweep still has to hit. Every enemy in range when it starts goes in (a snapshot -- late
	 * arrivals are left alone, so a raid or the night's spawns can't keep it running); one leaves the list when it is
	 * hit, dies, despawns, stops being a foe or runs out of the radius, or when there has been nowhere to land beside it
	 * for {@link #SWEEP_UNREACHABLE_TICKS}. The sweep ends once the list is empty. Pure bookkeeping (no world access),
	 * so it is tested directly.
	 */
	public static final class SweepTargets {
		/** target id -> sweep tick it was first found unreachable (-1 = not unreachable). Insertion order kept. */
		private final Map<Integer, Integer> pending = new java.util.LinkedHashMap<>();
		private final java.util.Set<Integer> hit = new java.util.HashSet<>();

		public SweepTargets(java.util.Collection<Integer> ids) {
			for (Integer id : ids) {
				pending.put(id, -1);
			}
		}

		public List<Integer> pending() {
			return new ArrayList<>(pending.keySet());
		}

		public boolean isPending(int id) {
			return pending.containsKey(id);
		}

		public void hit(int id) {
			if (pending.remove(id) != null) {
				hit.add(id);
			}
		}

		public boolean wasHit(int id) {
			return hit.contains(id);
		}

		public int hitCount() {
			return hit.size();
		}

		/** Gone (dead, despawned, out of the radius, no longer a foe): nothing left to hit. */
		public void drop(int id) {
			pending.remove(id);
		}

		/** No free spot beside it this hop; the clock starts the first time. */
		public void unreachable(int id, int tick) {
			pending.computeIfPresent(id, (k, since) -> since < 0 ? tick : since);
		}

		/** Gives up on every target that has been unreachable for {@link #SWEEP_UNREACHABLE_TICKS}; returns how many. */
		public int dropStale(int tick) {
			int before = pending.size();
			pending.values().removeIf(since -> since >= 0 && tick - since >= SWEEP_UNREACHABLE_TICKS);
			return before - pending.size();
		}

		public boolean done() {
			return pending.isEmpty();
		}

		/** v0.14.21: whether {@code id} has been found with nowhere to land beside it (and not hit since). */
		public boolean isUnreachable(int id) {
			Integer since = pending.get(id);
			return since != null && since >= 0;
		}

		/**
		 * v0.14.21: every target still pending has been tried and found unreachable -- nothing left the sweep can hit, so
		 * it goes home now instead of standing frozen at the last target for {@link #SWEEP_UNREACHABLE_TICKS}.
		 */
		public boolean allUnreachable() {
			return !pending.isEmpty() && pending.values().stream().allMatch(since -> since >= 0);
		}
	}

	private static final class Sweep {
		final ResourceKey<Level> dim;
		final Vec3 origin;
		final float yaw;
		final float pitch;
		final SweepTargets targets;
		int wait;
		/** v0.14.16: ticks the sweep has run (its own clock, for the failsafes). */
		int age;
		/** v0.14.16: X let go since the sweep started -- a fresh press then calls it off. */
		boolean released;

		Sweep(ResourceKey<Level> dim, Vec3 origin, float yaw, float pitch, SweepTargets targets) {
			this.dim = dim;
			this.origin = origin;
			this.yaw = yaw;
			this.pitch = pitch;
			this.targets = targets;
		}
	}

	private static final Map<UUID, Sweep> SWEEPS = new HashMap<>();

	private SuperSpeedMoves() {
	}

	private static String key() {
		return SuperSpeedHandlers.KEY;
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, key(), name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, key(), name, v, 1e12f);
	}

	// ---- shared helpers --------------------------------------------------------------------------

	/**
	 * One blow that always lands in full: the target's hit-invulnerability window is cleared first, so a
	 * burst of punches on the same player or mob each deal their whole damage instead of being swallowed by
	 * the 10-tick damage cooldown. PvP / squad rules still apply ({@link AbilityHelpers#hurt} plus the squad
	 * friendly-fire veto).
	 */
	public static boolean punchThrough(ServerPlayer p, LivingEntity target, float amount) {
		if (target == p || !target.isAlive()) {
			return false;
		}
		int saved = target.invulnerableTime;
		target.invulnerableTime = 0;
		boolean dealt = AbilityHelpers.hurt(p, target, amount);
		if (!dealt) {
			target.invulnerableTime = saved;
		}
		return dealt;
	}

	/** Can a speedster's move go for {@code e}? Never yourself, a squadmate, your own or a squadmate's pet, or (PvP off) a player. */
	public static boolean validFoe(ServerPlayer p, LivingEntity e) {
		// v0.14.20: the shared rule 1 (com.projecthero.mod.combat.HeroTargets#canHarm); a carried target rides you
		return com.projecthero.mod.combat.HeroTargets.canHarm(p, e);
	}

	private static float mult(ServerPlayer p) {
		return SuperSpeedHandlers.overdrive(p) ? 2.0f : 1.0f;
	}

	public static int color(ServerPlayer p) {
		return SuperSpeedHandlers.overdrive(p) ? RED : YELLOW;
	}

	private static boolean shiftReady(ServerPlayer p, String name) {
		return res(p, name) <= p.level().getGameTime();
	}

	private static int shiftRemaining(ServerPlayer p, String name) {
		return (int) Math.max(0, res(p, name) - p.level().getGameTime());
	}

	private static void startShiftCooldown(ServerPlayer p, String name, int baseTicks) {
		set(p, name, p.level().getGameTime() + HeroConfig.get().scaledCooldown(baseTicks));
	}

	private static void cooldownMessage(ServerPlayer p, String moveId, int ticks) {
		p.displayClientMessage(Component.translatable("message.projecthero.ability.on_cooldown",
				Component.translatable("projecthero.power." + key() + ".ability." + moveId),
				String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0f)), true);
	}

	private static void noTarget(ServerPlayer p) {
		p.displayClientMessage(Component.translatable("message.projecthero.speed.no_target"), true);
	}

	/** Sends one after-image effect to the speedster and everyone who can see them (and has the mod). */
	public static void streak(ServerPlayer p, int kind, Vec3 from, Vec3 to, int rgb, int life) {
		float yaw = p.getYRot();
		Vec3 d = to.subtract(from);
		if (kind == SpeedStreakPayload.KIND_STREAK && d.horizontalDistanceSqr() > 1.0e-4) {
			yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
		}
		SpeedStreakPayload payload = new SpeedStreakPayload(p.getId(), kind, from.x, from.y, from.z, to.x, to.y, to.z,
				yaw, rgb, life);
		if (ServerPlayNetworking.canSend(p, SpeedStreakPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
			if (viewer != p && ServerPlayNetworking.canSend(viewer, SpeedStreakPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/** The zip: an after-image streak for mod clients plus a spark line everyone sees. */
	private static void zipTrail(ServerPlayer p, Vec3 from, Vec3 to) {
		int rgb = color(p);
		streak(p, SpeedStreakPayload.KIND_STREAK, from, to, rgb, 16);
		ServerLevel level = p.serverLevel();
		DustParticleOptions dust = new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 0xFF) / 255f,
				((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.3f);
		double len = from.distanceTo(to);
		for (double s = 0; s <= len; s += 0.7) {
			Vec3 at = from.add(to.subtract(from).scale(len < 1.0e-4 ? 0 : s / len));
			level.sendParticles(dust, at.x, at.y + 1.0, at.z, 1, 0.15, 0.4, 0.15, 0.0);
			if (((int) (s / 0.7)) % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 1, 0.2, 0.5, 0.2, 0.02);
			}
		}
	}

	private static void soundAt(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** A free spot to stand right next to {@code t}, preferring the side facing {@code from}. Null if there is none. */
	public static Vec3 strikeSpot(ServerPlayer p, LivingEntity t, Vec3 from) {
		Vec3 dir = new Vec3(t.getX() - from.x, 0, t.getZ() - from.z);
		dir = dir.lengthSqr() < 1.0e-4 ? BatchA.flatLook(p) : dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double gap = t.getBbWidth() * 0.5 + 0.75;
		Vec3 base = t.position();
		Vec3[] offsets = { dir.scale(-gap), dir.scale(gap), side.scale(gap), side.scale(-gap),
				dir.scale(-gap).add(side.scale(gap)), dir.scale(-gap).add(side.scale(-gap)) };
		ServerLevel level = p.serverLevel();
		for (double up : new double[] { 0.0, 1.0 }) {
			for (Vec3 o : offsets) {
				Vec3 c = base.add(o.x, up, o.z);
				if (SafeTeleport.isSafe(level, p, c)) {
					return c;
				}
			}
		}
		return null;
	}

	/** Teleports the speedster to {@code at}, facing {@code face}. */
	private static void zipTo(ServerPlayer p, Vec3 at, Vec3 face) {
		double dx = face.x - at.x;
		double dz = face.z - at.z;
		double dy = face.y - (at.y + p.getEyeHeight());
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
		teleportExact(p, p.serverLevel(), at, yaw, pitch);
	}

	private static void teleportExact(ServerPlayer p, ServerLevel level, Vec3 at, float yaw, float pitch) {
		p.teleportTo(level, at.x, at.y, at.z, yaw, Mth.clamp(pitch, -90f, 90f));
		p.setYHeadRot(yaw);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		p.hurtMarked = true;
	}

	private static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** The foe under the crosshair (generous hit box, stops at walls), else the nearest hostile in a narrow cone. */
	public static LivingEntity aimTarget(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult wall = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB area = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(2.0);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, area, e -> validFoe(p, e))) {
			AABB box = e.getBoundingBox().inflate(0.9);
			var hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
			if (hit.isPresent()) {
				double d = eye.distanceToSqr(hit.get());
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		if (best != null) {
			return best;
		}
		double reach2 = eye.distanceToSqr(end) + 1.0;
		for (LivingEntity e : AbilityHelpers.living(p.serverLevel(), eye, range, e -> com.projecthero.mod.combat.HeroTargets.isHostile(p, e))) { // v0.14.20: auto-aim, rule 2
			double d = eye.distanceToSqr(center(e));
			if (d < bestD && d <= reach2 && BatchA.inCone(p, e, 0.96) && p.hasLineOfSight(e)) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	// ---- G: Blitz ---------------------------------------------------------------------------------

	public static void blitz(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!ctx.cooldownReady()) {
			cooldownMessage(p, "blitz", ctx.cooldownRemaining());
			return;
		}
		LivingEntity target = aimTarget(p, BLITZ_RANGE);
		if (target == null) {
			noTarget(p);
			return;
		}
		Vec3 from = p.position();
		Vec3 spot = strikeSpot(p, target, from);
		if (spot == null) {
			noTarget(p);
			return;
		}
		ServerLevel level = p.serverLevel();
		soundAt(level, from, SoundEvents.BREEZE_SHOOT, 0.9f, 1.7f);
		zipTo(p, spot, center(target));
		zipTrail(p, from, spot);
		punchThrough(p, target, BLITZ_DAMAGE * mult(p));
		AbilityHelpers.knockbackFrom(target, spot, BLITZ_KNOCKBACK);
		AbilityHelpers.push(target, new Vec3(0, 0.35, 0));
		Vec3 c = center(target);
		blitzShockwave(p, target, c);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.35, 0.4, 0.35, 0.4);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.25);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.35f, 1.9f);
		BatchA.play(p, key(), "p04.blitz");
		ctx.triggerCooldown();
	}

	/** v0.14.13: the impact of a Blitz hits every other foe round the target (squad, pets and the speedster are spared). */
	private static void blitzShockwave(ServerPlayer p, LivingEntity target, Vec3 c) {
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : AbilityHelpers.living(level, c, BLITZ_AOE_RADIUS, e -> e != target && validFoe(p, e))) {
			punchThrough(p, e, BLITZ_AOE_DAMAGE * mult(p));
			AbilityHelpers.knockbackFrom(e, c, BLITZ_AOE_KNOCKBACK);
			AbilityHelpers.push(e, new Vec3(0, 0.25, 0));
		}
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * 2.0, c.y - 0.4, c.z + Math.sin(a) * 2.0,
					2, 0.15, 0.1, 0.15, 0.1);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
	}

	// ---- Shift+R: Mach Punch ------------------------------------------------------------------------

	/** Mach Punch damage for a runner moving at {@code blocksPerTick}. */
	public static float machDamage(double blocksPerTick) {
		float frac = (float) Mth.clamp(blocksPerTick / MACH_FULL_SPEED, 0.0, 1.0);
		return MACH_MIN + (MACH_MAX - MACH_MIN) * frac;
	}

	public static void machPunch(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!shiftReady(p, MACH_READY)) {
			cooldownMessage(p, "mach_punch", shiftRemaining(p, MACH_READY));
			return;
		}
		double speed = SuperSpeedHandlers.recentSpeed(p);
		float frac = (float) Mth.clamp(speed / MACH_FULL_SPEED, 0.0, 1.0);
		float damage = machDamage(speed);
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		LivingEntity target = aimTarget(p, 5.0);
		if (target != null && target.distanceToSqr(p) > 36.0) {
			target = null;
		}
		Vec3 impact = target != null ? center(target) : p.getEyePosition().add(look.scale(2.5));
		if (target != null) {
			punchThrough(p, target, damage);
			Vec3 shove = look.scale(2.2 + 2.0 * frac);
			AbilityHelpers.push(target, new Vec3(shove.x, Math.max(0.45, shove.y + 0.45), shove.z));
		}
		for (LivingEntity e : AbilityHelpers.living(level, impact, 4.5, e -> validFoe(p, e))) {
			if (e == target) {
				continue;
			}
			punchThrough(p, e, MACH_SHOCKWAVE);
			AbilityHelpers.knockbackFrom(e, impact, 1.2 + frac);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 2, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 30, 0.6, 0.6, 0.6, 0.5);
		BatchA.ring(level, new Vec3(impact.x, p.getY() + 0.2, impact.z), 0.8, ParticleTypes.CLOUD, 28, 0.55 + 0.4 * frac);
		BatchA.ring(level, new Vec3(impact.x, p.getY() + 0.6, impact.z), 0.6, ParticleTypes.ELECTRIC_SPARK, 20, 0.7);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.7f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
		BatchA.play(p, key(), "p04.mach_punch");
		startShiftCooldown(p, MACH_READY, MACH_CD);
	}

	// ---- Shift+G: Speed Vortex -----------------------------------------------------------------------

	public static boolean vortexActive(ServerPlayer p) {
		return res(p, VORTEX_LEFT) > 0.5f;
	}

	public static void startVortex(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (vortexActive(p)) {
			return;
		}
		if (!shiftReady(p, VORTEX_READY)) {
			cooldownMessage(p, "speed_vortex", shiftRemaining(p, VORTEX_READY));
			return;
		}
		set(p, VORTEX_LEFT, VORTEX_TICKS);
		startShiftCooldown(p, VORTEX_READY, VORTEX_CD);
		BatchA.play(p, key(), "p04.vortex");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.6f);
	}

	/** One tick of a running Speed Vortex (from the power's per-tick hook). */
	public static void vortexTick(ServerPlayer p) {
		int left = Math.round(res(p, VORTEX_LEFT));
		if (left <= 0) {
			return;
		}
		if (!p.isAlive()) {
			set(p, VORTEX_LEFT, 0);
			MutationVisuals.stopIf(p, "p04.vortex");
			return;
		}
		left--;
		set(p, VORTEX_LEFT, left);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		float m = mult(p);
		List<LivingEntity> caught = AbilityHelpers.living(level, c, VORTEX_RADIUS, e -> validFoe(p, e));
		if (left > 0) {
			for (LivingEntity e : caught) {
				Vec3 to = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
				double dist = Math.max(0.1, to.length());
				Vec3 in = to.scale(1.0 / dist);
				Vec3 around = new Vec3(-in.z, 0, in.x);
				double pull = dist > 3.0 ? 0.28 : -0.08;
				double lift = e.getY() < c.y + 3.0 ? 0.14 : -0.02;
				e.setDeltaMovement(in.scale(pull).add(around.scale(0.45)).add(0, lift, 0));
				e.hurtMarked = true;
				if (left % 10 == 0) {
					punchThrough(p, e, VORTEX_TICK_DAMAGE * m);
				}
			}
			// the cyclone: two spiral arms of wind and sparks, and a ring of after-images racing round you
			double t = (VORTEX_TICKS - left) * 0.55;
			for (int arm = 0; arm < 2; arm++) {
				for (int k = 0; k < 6; k++) {
					double h = k * 0.7;
					double r = 1.6 + h * 0.55;
					double a = t + arm * Math.PI + k * 0.5;
					level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r, 1,
							0.05, 0.05, 0.05, 0.01);
				}
			}
			if (left % 2 == 0) {
				double a0 = t;
				double r = 2.2;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a0) * r, c.y + 1.0, c.z + Math.sin(a0) * r,
						3, 0.1, 0.4, 0.1, 0.05);
				streak(p, SpeedStreakPayload.KIND_STREAK, c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r),
						c.add(Math.cos(a0 + 1.3) * r, 0, Math.sin(a0 + 1.3) * r), color(p), 8);
			}
			if (left % 12 == 0) {
				AbilityHelpers.sound(p, SoundEvents.ELYTRA_FLYING, 0.25f, 1.9f);
			}
			return;
		}
		// the end: the cyclone bursts and throws everything out
		for (LivingEntity e : caught) {
			punchThrough(p, e, VORTEX_FINAL_DAMAGE * m);
			Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			out = out.lengthSqr() < 1.0e-4 ? BatchA.flatLook(p) : out.normalize();
			AbilityHelpers.push(e, out.scale(1.6).add(0, 0.9, 0));
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 3, 1.2, 0.6, 1.2, 0);
		BatchA.ring(level, c.add(0, 0.3, 0), 1.0, ParticleTypes.CLOUD, 36, 0.9);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.3f);
		MutationVisuals.stopIf(p, "p04.vortex");
	}

	// ---- Shift+X: Speed Sweep ---------------------------------------------------------------------------

	public static boolean sweeping(Player p) {
		return SWEEPS.containsKey(p.getUUID());
	}

	/** What Speed Sweep goes for: hostiles, anything hunting you, and (PvP on, not squad) players. */
	private static boolean sweepTarget(ServerPlayer p, LivingEntity e) {
		// v0.14.20: a 50-block auto-sweep is rule 2 -- threats only, never the farm (HeroTargets#isHostile)
		return com.projecthero.mod.combat.HeroTargets.isHostile(p, e);
	}

	public static void startSweep(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		Sweep running = SWEEPS.get(p.getUUID());
		if (running != null) {
			// v0.14.16: a fresh X press (let go since it started -- not the key repeat) calls the sweep off and brings you home
			if (running.released) {
				finishSweep(p, running);
			}
			return;
		}
		if (!shiftReady(p, SWEEP_READY)) {
			cooldownMessage(p, "speed_sweep", shiftRemaining(p, SWEEP_READY));
			return;
		}
		List<Integer> found = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.living(p.serverLevel(), p.position(), SWEEP_RANGE, e -> sweepTarget(p, e))) {
			found.add(e.getId());
		}
		if (found.isEmpty()) {
			noTarget(p);
			return;
		}
		SWEEPS.put(p.getUUID(), new Sweep(p.level().dimension(), p.position(), p.getYRot(), p.getXRot(),
				new SweepTargets(found)));
		startShiftCooldown(p, SWEEP_READY, SWEEP_CD);
		BatchA.play(p, key(), "p04.sweep");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 1.4f);
		sweepTick(p);
	}

	/** v0.14.16: X was let go -- from now on a fresh press ends a running sweep. */
	public static void sweepKeyReleased(ServerPlayer p) {
		Sweep s = SWEEPS.get(p.getUUID());
		if (s != null) {
			s.released = true;
		}
	}

	/** v0.14.16: how many targets the running sweep still has to hit (0 if none is running). */
	public static int sweepPending(Player p) {
		Sweep s = SWEEPS.get(p.getUUID());
		return s == null ? 0 : s.targets.pending().size();
	}

	/** v0.14.16: whether the running sweep has hit {@code e} yet. */
	public static boolean sweepHit(Player p, Entity e) {
		Sweep s = SWEEPS.get(p.getUUID());
		return s != null && s.targets.wasHit(e.getId());
	}

	/** v0.14.16: test hook -- ages the running sweep by {@code ticks} (as if that many ticks had passed between hops). */
	public static void ageSweep(Player p, int ticks) {
		Sweep s = SWEEPS.get(p.getUUID());
		if (s != null) {
			s.age += ticks;
		}
	}

	/**
	 * One tick of a running Speed Sweep: every {@link #SWEEP_HOP_TICKS} ticks, a hop to the nearest enemy still to be
	 * hit, and the hit; once every one has been hit (or is gone) -- home. v0.14.16: it no longer ends after one pass
	 * through a fixed list; it ends when the list is empty, or on the failsafes (an enemy with nowhere to land beside it
	 * is given up on after 5 s, and the whole sweep after {@link #SWEEP_MAX_TICKS}), death, a dimension change, or the
	 * power going.
	 */
	public static void sweepTick(ServerPlayer p) {
		Sweep s = SWEEPS.get(p.getUUID());
		if (s == null) {
			return;
		}
		if (!p.isAlive()) {
			SWEEPS.remove(p.getUUID());
			MutationVisuals.stopIf(p, "p04.sweep");
			return;
		}
		p.resetFallDistance();
		if (p.level().dimension() != s.dim) {
			finishSweep(p, s);
			return;
		}
		s.age++;
		if (s.age > SWEEP_MAX_TICKS) {
			finishSweep(p, s);
			return;
		}
		// v0.14.21: who is left is checked EVERY tick, not only on hop ticks -- the moment the last target is hit, dies or
		// despawns the speedster goes home (it used to wait out the hop timer, and then stand frozen at the last target
		// for 5 s whenever a stray it could not land beside was still on the list)
		ServerLevel level = p.serverLevel();
		double leash = (SWEEP_RANGE + 10) * (SWEEP_RANGE + 10);
		List<LivingEntity> left = new ArrayList<>();
		for (int id : s.targets.pending()) {
			Entity e = level.getEntity(id);
			if (!(e instanceof LivingEntity le) || !le.isAlive() || !validFoe(p, le) || le.distanceToSqr(s.origin) > leash) {
				s.targets.drop(id);
			} else {
				left.add(le);
			}
		}
		s.targets.dropStale(s.age);
		left.removeIf(le -> !s.targets.isPending(le.getId()));
		if (left.isEmpty()) {
			finishSweep(p, s);
			return;
		}
		if (--s.wait > 0) {
			p.setDeltaMovement(Vec3.ZERO);
			return;
		}
		s.wait = SWEEP_HOP_TICKS;
		// nearest first, from wherever the last hop left you, so the hops chain across the field instead of zig-zagging;
		// v0.14.21: targets already found unreachable go to the back, so the farther ones get tried before any retry
		Vec3 from = p.position();
		left.sort(java.util.Comparator.<LivingEntity>comparingInt(le -> s.targets.isUnreachable(le.getId()) ? 1 : 0)
				.thenComparingDouble(le -> le.distanceToSqr(from)));
		int tries = 0;
		for (LivingEntity le : left) {
			if (tries++ >= SWEEP_TRIES_PER_HOP) {
				break;
			}
			Vec3 spot = strikeSpot(p, le, from);
			if (spot == null) {
				s.targets.unreachable(le.getId(), s.age);
				continue;
			}
			zipTo(p, spot, center(le));
			zipTrail(p, from, spot);
			punchThrough(p, le, SWEEP_DAMAGE * mult(p));
			s.targets.hit(le.getId());
			AbilityHelpers.knockbackFrom(le, spot, 0.35);
			Vec3 c = center(le);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.35);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.2);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f + level.random.nextFloat() * 0.3f);
			return;
		}
		// v0.14.21: nothing left that can be reached (every target still pending has been tried and had nowhere to land
		// beside it): give up on them and go home now, rather than hold still for the 5 s unreachable timeout
		if (s.targets.allUnreachable()) {
			finishSweep(p, s);
			return;
		}
		// nowhere to land beside any of the nearest this hop: hold still and try the rest on the next one
		p.setDeltaMovement(Vec3.ZERO);
	}

	/** Ends a sweep and puts the speedster back exactly where (and how) they started. */
	private static void finishSweep(ServerPlayer p, Sweep s) {
		SWEEPS.remove(p.getUUID());
		MutationVisuals.stopIf(p, "p04.sweep");
		ServerLevel home = p.getServer() == null ? null : p.getServer().getLevel(s.dim);
		if (home == null || !p.isAlive()) {
			return;
		}
		if (home == p.level()) {
			zipTrail(p, p.position(), s.origin);
		}
		teleportExact(p, home, s.origin, s.yaw, s.pitch);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 2.0f);
	}

	/** Logging out mid-sweep: put the player back home before they are saved. */
	public static void onDisconnect(ServerPlayer p) {
		Sweep s = SWEEPS.remove(p.getUUID());
		if (s != null && p.level().dimension() == s.dim) {
			p.moveTo(s.origin.x, s.origin.y, s.origin.z, s.yaw, s.pitch);
			p.resetFallDistance();
		}
	}

	// ---- upkeep ----------------------------------------------------------------------------------------

	/** Per-tick hook for all of the above (called from the power's passive tick). */
	public static void tick(ServerPlayer p) {
		vortexTick(p);
		sweepTick(p);
	}

	/** Power lost: stop whatever is running (a sweep still brings you home). */
	public static void stopAll(ServerPlayer p) {
		Sweep s = SWEEPS.get(p.getUUID());
		if (s != null) {
			finishSweep(p, s);
		}
		if (vortexActive(p)) {
			set(p, VORTEX_LEFT, 0);
			MutationVisuals.stopIf(p, "p04.vortex");
		}
	}

	public static void clearSessionState() {
		SWEEPS.clear();
	}

	public static void prune(net.minecraft.server.MinecraftServer server) {
		SWEEPS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}
}
