package com.projecthero.mod.quirk.darkshadow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp: Dark Shadow's twelve moves, the light / darkness tracking, the Shadow meter, Berserk, and
 * the per-player sessions of the moves that run over time. Every public move is called by {@link DarkShadowKit#handle}
 * once its cooldown is ready, and by the gametests directly. Sessions hold entity ids, never entities.
 *
 * <pre>
 *   R  Shadow Strike (Black Ankh: claw dash)   Shift+R  Dark Fang
 *   G  Shadow Grab (lift + slam)               Shift+G  Shadow Shield (hold)
 *   X  Abyssal Black Ankh (glide; movement)    Shift+X  Fetch (carries you / your items)
 *   Z  Shadow Scout (marks)                    Shift+Z  Sunshine Ankh (the nova)
 *   V  Black Ankh (armour mode)                Shift+V  Recall
 *   C  Berserk on purpose                      Shift+C  Light Flare (works while berserk)
 * </pre>
 */
public final class DarkShadowMoves {
	static final ResourceLocation ANKH_DAMAGE_ID = PowerToggles.id("quirk_dark_shadow_ankh_damage");
	static final ResourceLocation ANKH_ARMOR_ID = PowerToggles.id("quirk_dark_shadow_ankh_armor");
	static final ResourceLocation ANKH_TOUGHNESS_ID = PowerToggles.id("quirk_dark_shadow_ankh_toughness");
	static final ResourceLocation ANKH_REACH_ID = PowerToggles.id("quirk_dark_shadow_ankh_reach");

	static final class Session {
		float dark = -1f;
		long darkSyncedAt;
		int fangTarget = -1;
		int fangLeft;
		long fangNext;
		int grabTarget = -1;
		long grabStart;
		Vec3 grabFrom = Vec3.ZERO;
		boolean slamming;
		long dashUntil;
		final Set<Integer> dashed = new HashSet<>();
		long scoutStart;
		Vec3 scoutFrom = Vec3.ZERO;
		Vec3 scoutTo = Vec3.ZERO;
		boolean scoutMarked;
		int fetchMode;
		long fetchStart;
		Vec3 fetchTo = Vec3.ZERO;
		final List<Integer> fetchItems = new ArrayList<>();
		long berserkSince;
		// v0.17.1 revamp
		/** G is held (a grab past its pull becomes a carry: Shadow Toss). */
		boolean gHeld;
		boolean carrying;
		long carryStart;
		/** The thrown body (id), when it was thrown, and the bodies its landing splashed. */
		int thrownId = -1;
		long thrownAt;
		/** X: pressed at (the tap is Shadow Lunge on release, the hold Black Fallen Angel). */
		long xAt = -1L;
		boolean xFired;
		/** Shadow Lunge: where it yanks you and since when. */
		Vec3 lungeTo = Vec3.ZERO;
		long lungeAt = -1L;
		/** Abyssal Rampage started at (its three swings land on the clip's contact ticks), and its combo bonus. */
		long rampageAt = -1L;
		float rampageMult = 1f;
		/** Shadow Scout's marks: entity id -> until. */
		final Map<Integer, Long> marks = new java.util.HashMap<>();
		/** Abyssal Black Body's claw dive: direction, end and what it has already raked. */
		Vec3 diveDir = Vec3.ZERO;
		long diveUntil;
		final Set<Integer> diveHit = new HashSet<>();
	}

	/** v0.17.1 Night owl: night vision while in Black Ankh (refreshed, ambient, never longer than this). */
	static final int NIGHT_OWL_TICKS = 260;

	/** v0.17.1: Abyssal Black Body's size (Attributes.SCALE, ADD_MULTIPLIED_BASE: x1.6). */
	public static final ResourceLocation BLACKBODY_SCALE_ID = PowerToggles.id("quirk_dark_shadow_blackbody_scale");

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private DarkShadowMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static DarkShadowConfig.Moves mv() {
		return DarkShadowConfig.get().moves;
	}

	private static DarkShadowConfig.Meter mt() {
		return DarkShadowConfig.get().meter;
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static float s(ServerPlayer p) {
		return DarkShadowCombat.strength(p);
	}

	private static void push(ServerPlayer p, float amount) {
		int light = DarkShadowCombat.lightAt(p);
		// v0.17.1: a move's push is paced like the climb (DarkShadowMeter.BUILD)
		float paced = com.projecthero.mod.quirk.meter.QuirkMeters.pace(p, amount);
		Quirks.setMeter(p, DarkShadowCombat.clampMeterInLight(light, Quirks.meter(p) + paced));
	}

	/** Dark Shadow reaches for {@code at}: the model stretches there (synced), the pose plays, an after-image streaks out. */
	static void reach(ServerPlayer p, Vec3 at, int anim) {
		Quirks.setVec(p, DarkShadow.VEC_REACH, at);
		Quirks.setVal(p, DarkShadow.V_REACH_AT, now(p));
		Quirks.setAnim(p, anim);
		DarkShadowCombat.streak(p.serverLevel(), DarkShadowCombat.shadowPos(p), at);
		// v0.17.1: the shadow's tendril snaking out to it (a toolkit ribbon every viewer sees)
		QuirkFx.ribbon(p.serverLevel(), DarkShadowCombat.back(p), at).style(QuirkFx.RIBBON_SHADOW).colours(0x2A0E44, 0x0A0410).size(0.32f).life(9).spawn();
	}

	private static @Nullable LivingEntity byId(ServerPlayer p, int id) {
		if (id < 0) {
			return null;
		}
		Entity e = p.serverLevel().getEntity(id);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	// ================================================================ R: Shadow Strike / Dark Fang

	/** A long-reach lunging claw strike (in Black Ankh: {@link #ankhDash}). Returns the creature it hit, or null. */
	public static @Nullable LivingEntity strike(ServerPlayer p) {
		if (DarkShadow.ankh(p)) {
			ankhDash(p);
			return null;
		}
		double range = DarkShadowCombat.range(p, mv().strikeRangeMin, mv().strikeRangeMax);
		LivingEntity t = DarkShadowCombat.aimed(p, range, mv().strikeAssistDegrees);
		Vec3 at = t != null ? DarkShadowCombat.center(t) : AbilityHelpers.aimPoint(p, range);
		reach(p, at, DarkShadow.ANIM_STRIKE);
		Quirks.setCooldown(p, DarkShadowConfig.STRIKE, mv().strikeCooldown);
		push(p, mt().strikePush);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.9f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 0.8f, 0.9f);
		if (t != null && DarkShadowCombat.strike(p, t, p.position(), mv().strikeDamage * s(p), mv().strikeKnockback, 0.15)) {
			return t;
		}
		DarkShadowCombat.smoke(p.serverLevel(), at, 6, 0.3);
		return null;
	}

	/** Black Ankh: R is a clawed dash the way you look, raking what you pass. */
	public static void ankhDash(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(-0.2, Math.min(0.35, look.y)), look.z).normalize();
		AbilityHelpers.launchSelf(p, dir.scale(mv().ankhDashSpeed).add(0, 0.1, 0));
		Session s = session(p);
		s.dashUntil = now(p) + mv().ankhDashTicks;
		s.dashed.clear();
		Quirks.setVal(p, DarkShadow.V_DASH, s.dashUntil);
		Quirks.setAnim(p, DarkShadow.ANIM_ANKH_DASH);
		Quirks.setCooldown(p, DarkShadowConfig.STRIKE, mv().ankhDashCooldown);
		push(p, mt().strikePush);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
		dashStep(p, s);
	}

	private static void dashStep(ServerPlayer p, Session s) {
		if (s.dashUntil == 0L) {
			return;
		}
		if (now(p) > s.dashUntil) {
			s.dashUntil = 0L;
			Quirks.setVal(p, DarkShadow.V_DASH, 0L);
			return;
		}
		p.resetFallDistance();
		Vec3 c = p.position().add(0, 0.9, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, 2.2)) {
			if (s.dashed.add(e.getId())) {
				DarkShadowCombat.strike(p, e, c, mv().ankhDashDamage * s(p), 0.7, 0.2);
			}
		}
		DarkShadowCombat.smoke(p.serverLevel(), c, 5, 0.35);
	}

	/** Dark Fang: it lunges and savages the target with {@code fangBites} rapid bites. False without a target. */
	public static boolean fang(ServerPlayer p) {
		double range = DarkShadowCombat.range(p, mv().fangRangeMin, mv().fangRangeMax);
		LivingEntity t = DarkShadowCombat.aimed(p, range, mv().strikeAssistDegrees + 4.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_target", ChatFormatting.DARK_PURPLE);
			return false;
		}
		Session s = session(p);
		s.fangTarget = t.getId();
		s.fangLeft = mv().fangBites;
		s.fangNext = now(p);
		Quirks.setVal(p, DarkShadow.V_FANG, now(p) + (long) mv().fangBites * mv().fangInterval + 6);
		Quirks.setCooldown(p, DarkShadowConfig.FANG, mv().fangCooldown);
		com.projecthero.mod.quirk.QuirkCombos.used(p, DarkShadowConfig.FANG);
		push(p, mt().fangPush);
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_FANG);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(flatLook(p).scale(0.45)).add(0, 0.12, 0));
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 1.2f);
		fangStep(p, s);
		return true;
	}

	private static void fangStep(ServerPlayer p, Session s) {
		if (s.fangLeft <= 0 || now(p) < s.fangNext) {
			return;
		}
		LivingEntity t = byId(p, s.fangTarget);
		double range = DarkShadowCombat.range(p, mv().fangRangeMin, mv().fangRangeMax) + 2.0;
		if (t == null || t.distanceTo(p) > range) {
			endFang(p, s);
			return;
		}
		s.fangLeft--;
		s.fangNext = now(p) + mv().fangInterval;
		Quirks.setVec(p, DarkShadow.VEC_REACH, DarkShadowCombat.center(t));
		boolean last = s.fangLeft == 0;
		DarkShadowCombat.strike(p, t, p.position(), mv().fangDamage * s(p), last ? 1.0 : 0.05, last ? 0.3 : 0.0);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.8f, 0.8f + 0.15f * s.fangLeft);
		AbilityHelpers.sound(p, SoundEvents.EVOKER_FANGS_ATTACK, 0.5f, 1.4f);
		if (last) {
			endFang(p, s);
		}
	}

	private static void endFang(ServerPlayer p, Session s) {
		s.fangTarget = -1;
		s.fangLeft = 0;
		Quirks.setVal(p, DarkShadow.V_FANG, 0L);
	}

	// ================================================================ G: Shadow Grab / Shadow Shield

	private static Vec3 holdPoint(ServerPlayer p, double extraLift) {
		return p.position().add(flatLook(p).scale(mv().grabHoldDistance)).add(0, mv().grabLift + extraLift, 0);
	}

	/** It wraps a creature in shadow, hoists it and slams it into the ground. Bosses are only struck. Null without a target. */
	public static @Nullable LivingEntity grab(ServerPlayer p) {
		double range = DarkShadowCombat.range(p, mv().grabRangeMin, mv().grabRangeMax);
		LivingEntity t = DarkShadowCombat.aimed(p, range, mv().strikeAssistDegrees);
		if (t == null) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_target", ChatFormatting.DARK_PURPLE);
			return null;
		}
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_GRAB);
		Quirks.setCooldown(p, DarkShadowConfig.GRAB, mv().grabCooldown);
		push(p, mt().grabPush);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.9f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.6f);
		DarkShadowCombat.strike(p, t, p.position(), mv().grabDamage * s(p), 0.0, 0.0);
		if (DarkShadowCombat.isBoss(t) || !AbilityHelpers.isValidGrabTarget(t, p) || !t.isAlive()) {
			return t;
		}
		Session s = session(p);
		s.grabTarget = t.getId();
		s.grabStart = now(p);
		s.grabFrom = t.position();
		s.slamming = false;
		Quirks.setVal(p, DarkShadow.V_GRAB, t.getId() + 1L);
		grabStep(p, s);
		return t;
	}

	private static void grabStep(ServerPlayer p, Session s) {
		if (s.grabTarget < 0) {
			return;
		}
		LivingEntity t = byId(p, s.grabTarget);
		long age = now(p) - s.grabStart;
		int pull = mv().grabPullTicks, slam = mv().grabSlamTicks;
		if (t == null) {
			endGrab(p, s);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 to;
		if (age <= pull) {
			float k = Math.min(1f, (age + 1f) / pull);
			to = s.grabFrom.lerp(holdPoint(p, 0), k * k * (3f - 2f * k));
		} else if (s.carrying || (s.gHeld && !s.slamming)) {
			// v0.17.1 Shadow Toss: G still held -- it carries the creature overhead until G is let go (then it is thrown)
			if (!s.carrying) {
				s.carrying = true;
				s.carryStart = now(p);
				Quirks.setVal(p, DarkShadow.V_TOSS, 1L);
				AbilityHelpers.sound(p, DarkShadowSounds.GROWL, 0.9f, 1.0f);
			}
			if (now(p) - s.carryStart > mv().tossMaxCarry) {
				toss(p);
				return;
			}
			to = holdPoint(p, -0.6 + 0.15 * Math.sin((now(p) - s.carryStart) * 0.25));
		} else if (age < pull + slam) {
			if (!s.slamming) {
				s.slamming = true;
				Quirks.setAnim(p, DarkShadow.ANIM_SLAM);
				AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.5f);
			}
			float k = (age - pull) / (float) slam;
			to = holdPoint(p, k < 0.6f ? 1.4 * (k / 0.6f) : 1.4 * (1f - (k - 0.6f) / 0.4f) - 0.2);
		} else {
			slamDown(p, t);
			endGrab(p, s);
			return;
		}
		t.teleportTo(to.x, to.y, to.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		t.resetFallDistance();
		Quirks.setVec(p, DarkShadow.VEC_REACH, DarkShadowCombat.center(t));
		level.sendParticles(DarkShadowCombat.SHADOW, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4,
				t.getBbWidth() * 0.5, 0.0);
	}

	/** The slam: down to the ground below, a heavy hit, a splash round the impact. */
	private static void slamDown(ServerPlayer p, LivingEntity t) {
		ServerLevel level = p.serverLevel();
		Vec3 from = t.position();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
		Vec3 ground = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from.add(0, -1, 0);
		t.teleportTo(ground.x, ground.y, ground.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		t.resetFallDistance();
		DarkShadowCombat.strike(p, t, ground, mv().grabSlamDamage * s(p), 0.0, 0.0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, ground.add(0, 0.5, 0), mv().grabSplashRadius)) {
			if (e != t) {
				DarkShadowCombat.strike(p, e, ground, mv().grabSplashDamage * s(p), 0.8, 0.35);
			}
		}
		BlockState below = level.getBlockState(BlockPos.containing(ground.add(0, -0.2, 0)));
		if (!below.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), ground.x, ground.y + 0.1, ground.z, 40, 1.0, 0.1, 1.0, 0.15);
		}
		DarkShadowCombat.smoke(level, ground.add(0, 0.4, 0), 24, 0.9);
		level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 0.3, ground.z, 1, 0, 0, 0, 0);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 1.2f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.5f);
	}

	private static void endGrab(ServerPlayer p, Session s) {
		s.grabTarget = -1;
		s.slamming = false;
		s.carrying = false;
		if (DarkShadow.on(p, DarkShadow.V_GRAB)) {
			Quirks.setVal(p, DarkShadow.V_GRAB, 0L);
		}
		if (DarkShadow.val(p, DarkShadow.V_TOSS) == 1L) {
			Quirks.setVal(p, DarkShadow.V_TOSS, 0L);
		}
	}

	/** G down: Shadow Grab (held on past the pull, it carries the creature: {@link #toss} on release). */
	public static @Nullable LivingEntity gDown(ServerPlayer p) {
		session(p).gHeld = true;
		return grab(p);
	}

	/** G up: the Shield comes down; a carried creature is thrown. */
	public static void gUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.gHeld = false;
			if (s.carrying) {
				toss(p);
			}
		}
		shieldOff(p);
	}

	/**
	 * Shadow Toss: the carried creature is hurled along your aim; where it lands (or hits a wall, or after a moment's flight)
	 * it takes {@link DarkShadowConfig.Moves#tossDamage} and the impact splashes what stands near. False if nothing was carried.
	 */
	public static boolean toss(ServerPlayer p) {
		Session s = session(p);
		LivingEntity t = byId(p, s.grabTarget);
		if (!s.carrying || t == null) {
			endGrab(p, s);
			return false;
		}
		Vec3 look = p.getLookAngle();
		Vec3 v = new Vec3(look.x, Math.max(-0.3, look.y) + 0.25, look.z).normalize().scale(mv().tossSpeed);
		endGrab(p, s);
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		t.hasImpulse = true;
		s.thrownId = t.getId();
		s.thrownAt = now(p);
		Quirks.setVal(p, DarkShadow.V_TOSS, 2L);
		reach(p, DarkShadowCombat.center(t).add(v.scale(2)), DarkShadow.ANIM_TOSS);
		push(p, mt().tossPush);
		Quirks.setCooldown(p, DarkShadowConfig.GRAB, mv().grabCooldown);
		AbilityHelpers.sound(p, DarkShadowSounds.WHOOSH, 1.2f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.5f);
		QuirkFx.trail(p.serverLevel(), t).style(QuirkFx.RIBBON_SHADOW).colours(0x1A0A2A, 0x07030C).size(0.5f).size2(8f).life(mv().tossFlightTicks + 6)
				.vec(new Vec3(0, t.getBbHeight() * 0.5, 0)).spawn();
		return true;
	}

	private static void tossStep(ServerPlayer p, Session s) {
		if (s.thrownId < 0) {
			return;
		}
		long age = now(p) - s.thrownAt;
		Entity e = p.serverLevel().getEntity(s.thrownId);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			s.thrownId = -1;
			Quirks.setVal(p, DarkShadow.V_TOSS, 0L);
			return;
		}
		t.resetFallDistance();
		boolean landed = age > 2 && (t.onGround() || t.horizontalCollision || t.verticalCollision);
		if (!landed && age < mv().tossFlightTicks) {
			return;
		}
		s.thrownId = -1;
		Quirks.setVal(p, DarkShadow.V_TOSS, 0L);
		ServerLevel level = p.serverLevel();
		Vec3 at = t.position();
		DarkShadowCombat.strike(p, t, at.subtract(t.getDeltaMovement()), mv().tossDamage * s(p), 0.3, 0.2);
		for (LivingEntity o : AbilityHelpers.enemiesAround(p, at.add(0, 0.5, 0), mv().tossSplashRadius)) {
			if (o != t) {
				DarkShadowCombat.strike(p, o, at, mv().tossSplashDamage * s(p), 0.8, 0.35);
			}
		}
		QuirkFx.decal(level, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_POOL).colour(0x0C0614).size(2.6f).life(160).spawn();
		QuirkFx.ring(level, at.add(0, 0.1, 0)).colours(0x6A3CBF, 0x12081E).size((float) mv().tossSplashRadius + 1f).thickness(0.6f).life(12).ground()
				.spawn();
		for (int i = 0; i < 4; i++) {
			QuirkFx.flipbook(level, at.add((level.random.nextDouble() - 0.5) * 2, 0.4 + level.random.nextDouble(), (level.random.nextDouble() - 0.5) * 2))
					.style(QuirkFx.BOOK_INK).size(1.2f).life(14).spawn();
		}
		QuirkCamera.shakeAround(level, at, 14, 0.4f, 8);
		QuirkSounds.play(level, at, QuirkSounds.IMPACT_HEAVY, 1.1f, 0.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	/** Shadow Shield goes up (held). */
	public static void shieldOn(ServerPlayer p) {
		if (DarkShadow.shielding(p)) {
			return;
		}
		Quirks.setVal(p, DarkShadow.V_SHIELD, Math.max(1L, now(p)));
		Quirks.setAnim(p, DarkShadow.ANIM_SHIELD);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 1.0f, 0.6f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.0, 0), 18, 0.6);
	}

	/** The key is released (or the Shield timed out): it unwraps and the cooldown starts. */
	public static void shieldOff(ServerPlayer p) {
		if (!DarkShadow.shielding(p)) {
			return;
		}
		Quirks.setVal(p, DarkShadow.V_SHIELD, 0L);
		Quirks.setCooldown(p, DarkShadowConfig.SHIELD, mv().shieldCooldown);
	}

	/** The fraction of a hit the Shield soaks now (more in the dark). */
	public static float shieldBlock(ServerPlayer p) {
		return Mth.lerp(DarkShadow.darkness(p), mv().shieldBlockMin, mv().shieldBlockMax);
	}

	/** The Shield on an incoming hit: it wraps all round, so every side counts; projectiles stop dead. */
	static float incoming(ServerPlayer p, DamageSource source, float amount) {
		if (tryCatch(p, source, amount)) {
			return 0f; // v0.17.1: it caught the fall
		}
		if (!DarkShadow.shielding(p) || source.getEntity() == p) {
			return amount;
		}
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.0, 0), 8, 0.5);
		AbilityHelpers.sound(p, SoundEvents.SHIELD_BLOCK, 0.8f, 0.5f);
		if (source.getDirectEntity() instanceof Projectile) {
			return 0f;
		}
		return amount * (1f - shieldBlock(p));
	}

	// ================================================================ X: Abyssal Black Ankh / Fetch

	/** Abyssal Black Ankh on / off: Black Ankh plus the wings (gliding flight). Returns the new state. */
	public static boolean toggleAbyssal(ServerPlayer p) {
		if (DarkShadow.abyssal(p)) {
			abyssalOff(p);
			Quirks.setCooldown(p, DarkShadowConfig.ABYSSAL, mv().abyssalCooldown);
			return false;
		}
		if (!DarkShadow.ankh(p)) {
			ankhOn(p);
		}
		Quirks.setVal(p, DarkShadow.V_ABYSSAL, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_ABYSSAL);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, mv().abyssalLaunch, p.getDeltaMovement().z));
		} else if (!p.isInWater()) {
			p.startFallFlying();
		}
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.3f, 0.45f);
		AbilityHelpers.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.2f);
		DarkShadowCombat.feathers(p.serverLevel(), p.position().add(0, 1.3, 0).subtract(flatLook(p).scale(0.4)), 12, 0.08);
		return true;
	}

	static void abyssalOff(ServerPlayer p) {
		if (DarkShadow.abyssal(p)) {
			Quirks.setVal(p, DarkShadow.V_ABYSSAL, 0L);
			if (p.isFallFlying()) {
				p.stopFallFlying();
			}
			p.resetFallDistance();
			if (!p.onGround()) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
		}
	}

	private static void abyssalTick(ServerPlayer p) {
		p.resetFallDistance();
		if (!p.isFallFlying() && !p.onGround() && !p.isInWater() && p.getDeltaMovement().y < 0.1 && !p.getAbilities().flying) {
			p.startFallFlying();
		}
		if (p.isFallFlying() && now(p) % 2 == 0) {
			// the wings beat harder in the dark: a steady push along the look, up to a cap
			double boost = Mth.lerp(DarkShadow.darkness(p), mv().abyssalBoostMin, mv().abyssalBoostMax) * 2.0;
			Vec3 v = p.getDeltaMovement();
			Vec3 nv = v.add(p.getLookAngle().scale(boost));
			if (nv.length() > mv().abyssalMaxSpeed) {
				nv = nv.normalize().scale(Math.max(v.length(), mv().abyssalMaxSpeed));
			}
			AbilityHelpers.addImpulse(p, nv.subtract(v));
		}
		if (p.isFallFlying() && now(p) % 3 == 0) {
			Vec3 b = DarkShadowCombat.back(p);
			p.serverLevel().sendParticles(DarkShadowCombat.SHADOW, b.x, b.y, b.z, 3, 0.8, 0.2, 0.8, 0.0);
		}
	}

	/**
	 * Fetch: items near where you look fly to you; otherwise, at a block, it carries you there. 0 = nothing, 1 = you,
	 * 2 = items.
	 */
	public static int fetch(ServerPlayer p) {
		double range = DarkShadowCombat.range(p, mv().fetchRangeMin, mv().fetchRangeMax);
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
		Session s = session(p);
		s.fetchItems.clear();
		double r = mv().fetchItemRadius;
		for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(eye, at).inflate(r))) {
			if (distToSegment(it.position(), eye, at) <= r && it.position().distanceTo(at) <= r * 2.5) {
				s.fetchItems.add(it.getId());
			}
		}
		if (!s.fetchItems.isEmpty()) {
			s.fetchMode = 2;
			s.fetchTo = at;
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			s.fetchMode = 1;
			s.fetchTo = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.7));
		} else {
			Quirks.say(p, "message.projecthero.dark_shadow.nothing_to_fetch", ChatFormatting.DARK_PURPLE);
			return 0;
		}
		s.fetchStart = now(p);
		Quirks.setVal(p, DarkShadow.V_FETCH, s.fetchMode);
		Quirks.setVal(p, DarkShadow.V_FETCH_AT, now(p));
		reach(p, s.fetchTo, DarkShadow.ANIM_FETCH);
		Quirks.setCooldown(p, DarkShadowConfig.FETCH, mv().fetchCooldown);
		push(p, mt().fetchPush);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 0.9f, 0.8f);
		return s.fetchMode;
	}

	private static double distToSegment(Vec3 q, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double k = len2 < 1.0e-6 ? 0 : Mth.clamp(q.subtract(a).dot(ab) / len2, 0, 1);
		return q.distanceTo(a.add(ab.scale(k)));
	}

	private static void fetchStep(ServerPlayer p, Session s) {
		if (s.fetchMode == 0) {
			return;
		}
		long age = now(p) - s.fetchStart;
		ServerLevel level = p.serverLevel();
		if (s.fetchMode == 2) {
			float k = Math.min(1f, (age + 1f) / mv().fetchTicks);
			Vec3 hand = p.position().add(0, 1.0, 0);
			for (int id : s.fetchItems) {
				if (level.getEntity(id) instanceof ItemEntity it && it.isAlive()) {
					Vec3 to = it.position().lerp(hand, k);
					it.teleportTo(to.x, to.y, to.z);
					it.setDeltaMovement(Vec3.ZERO);
					it.setNoPickUpDelay();
					level.sendParticles(DarkShadowCombat.SHADOW, to.x, to.y + 0.2, to.z, 2, 0.1, 0.1, 0.1, 0.0);
				}
			}
			if (k >= 1f) {
				endFetch(p, s);
			}
			return;
		}
		// carrying you: a steady pull to the point, wrapped in shadow
		Vec3 to = s.fetchTo;
		Vec3 d = to.subtract(p.position());
		if (d.length() < 1.2 || age > mv().fetchMaxTicks) {
			p.setDeltaMovement(p.getDeltaMovement().scale(0.3));
			p.hurtMarked = true;
			endFetch(p, s);
			return;
		}
		Vec3 v = d.normalize().scale(Math.min(1.6, 0.6 + d.length() * 0.25)).add(0, 0.08, 0);
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
		Quirks.setVec(p, DarkShadow.VEC_REACH, to);
		level.sendParticles(DarkShadowCombat.SHADOW, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.0);
	}

	private static void endFetch(ServerPlayer p, Session s) {
		s.fetchMode = 0;
		s.fetchItems.clear();
		if (DarkShadow.on(p, DarkShadow.V_FETCH)) {
			Quirks.setVal(p, DarkShadow.V_FETCH, 0L);
		}
	}

	// ================================================================ Z: Shadow Scout / Sunshine Ankh

	/** It flies out along your look, hovers, marks every creature near its path and comes back. */
	public static void scout(ServerPlayer p) {
		double range = DarkShadowCombat.range(p, mv().scoutRangeMin, mv().scoutRangeMax);
		Vec3 to = AbilityHelpers.aimPoint(p, range).add(0, 2.0, 0); // it flies above the ground
		Session s = session(p);
		s.scoutStart = now(p);
		s.scoutFrom = DarkShadowCombat.shadowPos(p);
		s.scoutTo = to;
		s.scoutMarked = false;
		Quirks.setVal(p, DarkShadow.V_SCOUT, now(p));
		Quirks.setVec(p, DarkShadow.VEC_SCOUT, to);
		Quirks.setAnim(p, DarkShadow.ANIM_SCOUT);
		Quirks.setCooldown(p, DarkShadowConfig.SCOUT, mv().scoutCooldown);
		push(p, mt().scoutPush);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.2f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_AMBIENT, 0.8f, 0.6f);
	}

	/** The scout's total flight, ticks. */
	public static int scoutTicks() {
		return mv().scoutOutTicks * 2 + mv().scoutHoverTicks;
	}

	private static void scoutStep(ServerPlayer p, Session s) {
		if (s.scoutStart == 0L) {
			return;
		}
		long age = now(p) - s.scoutStart;
		int out = mv().scoutOutTicks;
		ServerLevel level = p.serverLevel();
		if (age >= scoutTicks()) {
			s.scoutStart = 0L;
			Quirks.setVal(p, DarkShadow.V_SCOUT, 0L);
			Quirks.setVec(p, DarkShadow.VEC_SCOUT, null);
			return;
		}
		Vec3 at = age < out ? s.scoutFrom.lerp(s.scoutTo, age / (float) out)
				: age < out + mv().scoutHoverTicks ? s.scoutTo : s.scoutTo.lerp(DarkShadowCombat.shadowPos(p), (age - out - mv().scoutHoverTicks) / (float) out);
		level.sendParticles(DarkShadowCombat.SHADOW, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.0);
		if (!s.scoutMarked && age >= out) {
			s.scoutMarked = true;
			int n = 0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.scoutFrom, s.scoutTo).inflate(mv().scoutRadius),
					e -> e != p && e.isAlive() && DarkShadowCombat.isTarget(p, e))) {
				boolean near = e.position().distanceTo(s.scoutTo) <= mv().scoutRadius
						|| distToSegment(e.position(), s.scoutFrom, s.scoutTo) <= mv().scoutPathRadius;
				if (near) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, mv().scoutGlowTicks, 0, false, false), p);
					Vec3 c = DarkShadowCombat.center(e);
					level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 0.6, c.z, 2, 0.1, 0.1, 0.1, 0.02);
					// v0.17.1: marked -- a violet eye glyph over it (client) and +15% from you for 10 s
					s.marks.put(e.getId(), now(p) + mv().scoutGlowTicks);
					n++;
				}
			}
			syncMarks(p, s);
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y, at.z, 20, 2.0, 1.0, 2.0, 0.02);
			AbilityHelpers.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.6f);
			Quirks.say(p, "message.projecthero.dark_shadow.scouted", ChatFormatting.DARK_PURPLE, n);
		}
	}

	/** Sunshine Ankh: a burst of shadow feathers all round you. Returns how many it hit. */
	public static int nova(ServerPlayer p) {
		double radius = DarkShadowCombat.range(p, mv().novaRadiusMin, mv().novaRadiusMax);
		Vec3 c = p.position().add(0, 1.0, 0);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, radius)) {
			if (DarkShadowCombat.strike(p, e, c, mv().novaDamage * s(p), mv().novaKnockback, 0.45)) {
				hit++;
			}
		}
		Quirks.setAnim(p, DarkShadow.ANIM_NOVA);
		Quirks.setCooldown(p, DarkShadowConfig.NOVA, mv().novaCooldown);
		push(p, mt().novaPush);
		ServerLevel level = p.serverLevel();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			for (double r = 1.2; r <= radius; r += 1.8) {
				level.sendParticles(DarkShadowCombat.SHADOW, c.x + Math.cos(a) * r, c.y - 0.4 + r * 0.05, c.z + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0.0);
			}
		}
		DarkShadowCombat.feathers(level, c, 28, 0.25);
		// v0.17.1: a dark shock ring and a burst of black feather flipbooks
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0x9A6AEF, 0x10061A).size((float) radius).thickness(0.8f).life(14).ground().spawn();
		QuirkFx.ring(level, c).colours(0x6A3CBF, 0x10061A).size((float) radius * 0.8f).thickness(0.4f).life(12).spawn();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			QuirkFx.flipbook(level, c.add(Math.cos(a) * radius * 0.5, 0.2 * (i % 3), Math.sin(a) * radius * 0.5)).style(QuirkFx.BOOK_INK).size(1.3f)
					.life(14).spawn();
		}
		QuirkCamera.shake(p, 0.3f, 8);
		AbilityHelpers.sound(p, DarkShadowSounds.ROAR, 0.7f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.5f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_DEATH, 1.0f, 0.5f);
		return hit;
	}

	// ================================================================ v0.17.1 X: Shadow Lunge (tap) / Black Fallen Angel (hold)

	/** X down (no Shift): the tap waits for the release; held past {@link DarkShadowConfig.Moves#holdTicks} it is the glide. */
	static void xDown(ServerPlayer p) {
		Session s = session(p);
		s.xAt = now(p);
		s.xFired = false;
		Quirks.setVal(p, DarkShadow.V_HOLD_X, now(p));
	}

	/** X up: the tap fires Shadow Lunge; after a hold the wings fold. */
	static void xUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.xAt < 0L) {
			return;
		}
		boolean fired = s.xFired;
		s.xAt = -1L;
		s.xFired = false;
		Quirks.setVal(p, DarkShadow.V_HOLD_X, 0L);
		if (fired) {
			if (DarkShadow.abyssal(p) && !DarkShadow.blackBody(p)) {
				abyssalOff(p);
				Quirks.setCooldown(p, DarkShadowConfig.ABYSSAL, mv().abyssalCooldown);
			}
		} else if (Quirks.ready(p, DarkShadowConfig.LUNGE)) {
			lunge(p);
		}
	}

	private static void xHoldStep(ServerPlayer p, Session s) {
		if (s.xAt < 0L || s.xFired || now(p) - s.xAt < mv().holdTicks) {
			return;
		}
		s.xFired = true;
		if (!DarkShadow.abyssal(p) && Quirks.ready(p, DarkShadowConfig.ABYSSAL)) {
			toggleAbyssal(p);
		}
	}

	/**
	 * Shadow Lunge: it grabs a point {@link DarkShadowConfig.Moves#lungeRange} blocks ahead (where you look, short of a wall) and
	 * yanks you there. False if there is no room.
	 */
	public static boolean lunge(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(mv().lungeRange));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 to = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.7)) : end;
		to = to.subtract(0, 1.0, 0); // where his feet go
		if (to.distanceTo(p.position()) < 2.5) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_room", ChatFormatting.DARK_PURPLE);
			return false;
		}
		Session s = session(p);
		s.lungeTo = to;
		s.lungeAt = now(p);
		Quirks.setVal(p, DarkShadow.V_LUNGE, now(p) + mv().lungeTicks + 6);
		reach(p, to.add(0, 1.0, 0), DarkShadow.ANIM_LUNGE);
		Quirks.setCooldown(p, DarkShadowConfig.LUNGE, mv().lungeCooldown);
		push(p, mt().lungePush);
		AbilityHelpers.sound(p, DarkShadowSounds.WHOOSH, 1.0f, 1.1f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 0.8f, 0.8f);
		return true;
	}

	private static void lungeStep(ServerPlayer p, Session s) {
		if (s.lungeAt < 0L) {
			return;
		}
		long k = now(p) - s.lungeAt;
		p.resetFallDistance();
		if (k < 2) {
			return; // its claws bite into the point first
		}
		Vec3 d = s.lungeTo.subtract(p.position());
		if (d.length() < 1.1 || k > mv().lungeTicks + 6) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.35).add(0, 0.1, 0));
			s.lungeAt = -1L;
			Quirks.setVal(p, DarkShadow.V_LUNGE, 0L);
			return;
		}
		if (k == 2) {
			QuirkFx.trail(p.serverLevel(), p).style(QuirkFx.RIBBON_SHADOW).colours(0x1A0A2A, 0x050208).size(0.55f).size2(7f).life(mv().lungeTicks + 8)
					.vec(new Vec3(0, 1.0, 0)).spawn();
			QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		}
		Vec3 v = d.normalize().scale(Math.min(2.2, Math.max(1.1, d.length() / 2.5))).add(0, 0.06, 0);
		AbilityHelpers.launchSelf(p, v);
		Quirks.setVec(p, DarkShadow.VEC_REACH, s.lungeTo.add(0, 1.0, 0));
		p.serverLevel().sendParticles(QuirkParticles.SHADOW_MOTE, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.01);
	}

	// ================================================================ v0.17.1 C: Abyssal Rampage

	/** Abyssal Rampage: braced, he lets it swing three great claw arcs in front of him (7 / 7 / 11, 5 blocks). */
	public static void rampage(ServerPlayer p) {
		Session s = session(p);
		s.rampageAt = now(p);
		// v0.17.1 combo route "Ravening Feast": Dark Fang, then Abyssal Rampage within 2 s -- +30% on all three swings
		s.rampageMult = com.projecthero.mod.quirk.QuirkCombos.mult(com.projecthero.mod.quirk.QuirkCombos.used(p, DarkShadowConfig.RAMPAGE));
		Quirks.setVal(p, DarkShadow.V_RAMPAGE, now(p) + mv().rampageHit3 + 6);
		Quirks.setVec(p, DarkShadow.VEC_REACH, p.position().add(flatLook(p).scale(1.2)).add(0, 1.2, 0));
		Quirks.setVal(p, DarkShadow.V_REACH_AT, now(p));
		Quirks.setAnim(p, DarkShadow.ANIM_RAMPAGE);
		Quirks.setCooldown(p, DarkShadowConfig.RAMPAGE, mv().rampageCooldown);
		push(p, mt().rampagePush);
		AbilityHelpers.sound(p, DarkShadowSounds.GROWL, 1.1f, 0.8f);
	}

	/** The swing at the contact ticks: every target in front within reach. Returns how many each swing hit (tests: all three). */
	private static void rampageStep(ServerPlayer p, Session s) {
		if (s.rampageAt < 0L) {
			return;
		}
		long k = now(p) - s.rampageAt;
		int swing = k == mv().rampageHit1 ? 1 : k == mv().rampageHit2 ? 2 : k == mv().rampageHit3 ? 3 : 0;
		if (swing > 0) {
			rampageSwing(p, swing);
		}
		if (k > mv().rampageHit3 + 6) {
			s.rampageAt = -1L;
			Quirks.setVal(p, DarkShadow.V_RAMPAGE, 0L);
		}
	}

	/** One of the three claw arcs (1, 2: the sweeps; 3: the overhead slam). Returns how many it hit. */
	public static int rampageSwing(ServerPlayer p, int swing) {
		ServerLevel level = p.serverLevel();
		Session ss = SESSIONS.get(p.getUUID());
		float dmg = (swing == 1 ? mv().rampageDamage1 : swing == 2 ? mv().rampageDamage2 : mv().rampageDamage3) * s(p) * (ss == null ? 1f : ss.rampageMult);
		Vec3 f = flatLook(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		double reach = mv().rampageReach;
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(f.scale(reach * 0.5)), reach + 1.0)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.lengthSqr() > 0.09 && to.normalize().dot(f) < (swing == 3 ? 0.55 : 0.15)) {
				continue;
			}
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > reach) {
				continue;
			}
			Vec3 side = new Vec3(-f.z, 0, f.x).scale(swing == 1 ? -1 : 1);
			Vec3 from = swing == 3 ? e.position().add(0, 2, 0).subtract(f) : e.position().subtract(side.scale(2));
			if (DarkShadowCombat.strike(p, e, from, dmg, swing == 3 ? 0.4 : 0.9, swing == 3 ? 0.1 : 0.3)) {
				n++;
			}
		}
		// the black crescent: five ribbon segments along the arc, and its violet edge
		Vec3 right = new Vec3(-f.z, 0, f.x);
		Vec3[] pts = new Vec3[6];
		for (int i = 0; i < pts.length; i++) {
			double u = i / (double) (pts.length - 1);
			if (swing == 3) {
				double a = Mth.lerp(u, -0.35, 1.45); // over the head and down in front
				pts[i] = c.add(f.scale(1.0 + Math.sin(a) * 2.6)).add(0, Math.cos(a) * 2.6, 0);
			} else {
				double a = Mth.lerp(u, -1.25, 1.25) * (swing == 1 ? -1 : 1);
				pts[i] = c.add(f.scale(Math.cos(a) * 3.2)).add(right.scale(Math.sin(a) * 3.2)).add(0, 0.6 - 0.5 * u, 0);
			}
		}
		for (int i = 0; i < pts.length - 1; i++) {
			QuirkFx.ribbon(level, pts[i], pts[i + 1]).style(QuirkFx.RIBBON_SHADOW).colours(0x12061E, 0x050208).size(0.7f - 0.08f * Math.abs(i - 2))
					.life(9).spawn();
		}
		QuirkFx.ribbon(level, pts[0], pts[pts.length - 1]).style(QuirkFx.RIBBON_LIGHT).colours(0xB890FF, 0x5A2ACF).size(0.08f).life(7).spawn();
		if (swing == 3) {
			Vec3 at = p.position().add(f.scale(2.6));
			QuirkFx.decal(level, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x120A18).size(2.2f).life(120).spawn();
			QuirkFx.ring(level, at.add(0, 0.1, 0)).colours(0x8A5ADF, 0x12081E).size(3.5f).thickness(0.5f).life(12).ground().spawn();
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0f, 0.8f);
		} else if (n > 0) {
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		AbilityHelpers.sound(p, DarkShadowSounds.WHOOSH, 1.1f, swing == 3 ? 0.7f : 0.95f + 0.1f * swing);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.7f, 0.6f);
		return n;
	}

	// ================================================================ v0.17.1 Plus Ultra: Abyssal Black Body

	/**
	 * Abyssal Black Body: Dark Shadow fully merges with him -- a towering winged black-bird armour (x1.6, the Ankh upgraded,
	 * glowing eyes) -- for {@link DarkShadowConfig.Moves#blackBodyTicks}: fast flight, every attack key a claw dive
	 * ({@link #clawDive}), Shadow frozen at Ravenous (no Berserk). Afterwards it is spent: hidden for 5 s.
	 */
	public static void blackBody(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		if (DarkShadow.on(p, DarkShadow.V_BERSERK)) {
			Quirks.setVal(p, DarkShadow.V_BERSERK, 0L);
			Quirks.setVal(p, DarkShadow.V_UNLEASH, 0L);
			Quirks.edit(p, n -> n.overloadUntil = 0L);
		}
		if (DarkShadow.recalled(p)) {
			Quirks.setVal(p, DarkShadow.V_RECALL, 0L);
		}
		Session s = session(p);
		endFang(p, s);
		endGrab(p, s);
		endFetch(p, s);
		shieldOff(p);
		Quirks.setVal(p, DarkShadow.V_BLACKBODY, now(p) + mv().blackBodyTicks);
		Quirks.setVal(p, DarkShadow.V_HIDE_LOCK, 0L);
		if (!DarkShadow.ankh(p)) {
			ankhOn(p);
		}
		Quirks.setVal(p, DarkShadow.V_ABYSSAL, 1L);
		Quirks.setMeter(p, mt().blackBodyMeter);
		Quirks.setAnim(p, DarkShadow.ANIM_BLACKBODY);
		AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 1.15, p.getDeltaMovement().z));
		Vec3 c = p.position().add(0, 1.2, 0);
		// the merge: a storm of black feathers, a dark shock ring, and two violet eye trails that follow him
		for (int i = 0; i < 10; i++) {
			double a = i * Math.PI / 5;
			QuirkFx.flipbook(level, c.add(Math.cos(a) * 1.6, (i % 3) * 0.6 - 0.4, Math.sin(a) * 1.6)).style(QuirkFx.BOOK_INK).size(1.6f).life(18).spawn();
		}
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xB890FF, 0x14081E).size(9f).thickness(1.0f).life(18).ground().spawn();
		QuirkFx.ring(level, c).colours(0xD0B0FF, 0x3A1A6A).size(5f).thickness(0.5f).life(14).flags(QuirkFx.F_VERTICAL).vec(flatLook(p)).spawn();
		for (int side = -1; side <= 1; side += 2) {
			QuirkFx.trail(level, p).style(QuirkFx.RIBBON_LIGHT).colours(0xE0C8FF, 0x7A3CFF).size(0.1f).size2(9f).life(mv().blackBodyTicks)
					.vec(new Vec3(side * 0.2, 2.5, 0.42)).spawn();
		}
		DarkShadowCombat.feathers(level, c, 40, 0.3);
		QuirkSounds.play(level, c, DarkShadowSounds.ROAR, 1.6f, 0.75f);
		AbilityHelpers.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.7f);
		QuirkCamera.preset(p, QuirkCamera.Preset.ULTIMATE);
	}

	private static void blackBodyTick(ServerPlayer p, Session s, long now) {
		long until = DarkShadow.val(p, DarkShadow.V_BLACKBODY);
		if (until == 0L) {
			return;
		}
		if (now >= until) {
			blackBodyEnd(p);
			return;
		}
		Quirks.setMeter(p, mt().blackBodyMeter); // frozen at Ravenous: it can't go berserk
		if (!DarkShadow.ankh(p)) {
			ankhOn(p);
		}
		if (!DarkShadow.abyssal(p)) {
			Quirks.setVal(p, DarkShadow.V_ABYSSAL, 1L);
		}
		p.resetFallDistance();
		if (!p.isFallFlying() && !p.onGround() && !p.isInWater() && !p.getAbilities().flying) {
			p.startFallFlying();
		}
		if (p.isFallFlying() && now % 2 == 0 && s.diveUntil <= now) {
			// fast flight: the great wings drive along the look
			Vec3 v = p.getDeltaMovement();
			Vec3 nv = v.add(p.getLookAngle().scale(mv().blackBodyBoost * 2.0));
			if (nv.length() > mv().blackBodyMaxSpeed) {
				nv = nv.normalize().scale(Math.max(v.length(), mv().blackBodyMaxSpeed));
			}
			AbilityHelpers.addImpulse(p, nv.subtract(v));
		}
		if (now % 3 == 0) {
			Vec3 b = DarkShadowCombat.back(p);
			p.serverLevel().sendParticles(QuirkParticles.SHADOW_MOTE, b.x, b.y + 0.4, b.z, 4, 1.4, 0.5, 1.4, 0.01);
			p.serverLevel().sendParticles(DarkShadowCombat.VIOLET, b.x, b.y + 0.4, b.z, 1, 1.2, 0.4, 1.2, 0.0);
		}
		if (now % 60 == 0) {
			AbilityHelpers.sound(p, DarkShadowSounds.GROWL, 0.8f, 0.7f);
		}
	}

	/** Black Body is spent: the armour falls away and Dark Shadow is hidden for 5 s (it comes back out on its own). */
	static void blackBodyEnd(ServerPlayer p) {
		if (DarkShadow.val(p, DarkShadow.V_BLACKBODY) == 0L) {
			return;
		}
		Quirks.setVal(p, DarkShadow.V_BLACKBODY, 0L);
		Quirks.setVal(p, DarkShadow.V_DIVE, 0L);
		Session s = session(p);
		s.diveUntil = 0L;
		ServerLevel level = p.serverLevel();
		DarkShadowCombat.smoke(level, p.position().add(0, 1.2, 0), 30, 1.0);
		stopMoves(p);
		Quirks.setVal(p, DarkShadow.V_RECALL, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_RECALL);
		Quirks.setVal(p, DarkShadow.V_HIDE_LOCK, now(p) + mv().blackBodyHiddenTicks);
		Quirks.setMeter(p, Math.min(Quirks.meter(p), 30f));
		if (!p.onGround()) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
		}
		Quirks.say(p, "message.projecthero.dark_shadow.spent", ChatFormatting.DARK_PURPLE);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_DEATH, 0.9f, 0.6f);
	}

	/** A claw dive in Black Body: along the look, raking everything it passes ({@link DarkShadowConfig.Moves#diveDamage} each). */
	public static void clawDive(ServerPlayer p) {
		Session s = session(p);
		Vec3 dir = p.getLookAngle().normalize();
		s.diveDir = dir;
		s.diveUntil = now(p) + mv().diveTicks;
		s.diveHit.clear();
		Quirks.setVal(p, DarkShadow.V_DIVE, s.diveUntil);
		Quirks.setAnim(p, DarkShadow.ANIM_DIVE);
		Quirks.setCooldown(p, DarkShadowConfig.DIVE, mv().diveCooldown);
		AbilityHelpers.launchSelf(p, dir.scale(mv().diveSpeed));
		QuirkFx.trail(p.serverLevel(), p).style(QuirkFx.RIBBON_SHADOW).colours(0x1A0A2A, 0x050208).size(0.9f).size2(8f).life(mv().diveTicks + 10)
				.vec(new Vec3(0, 1.4, 0)).spawn();
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		AbilityHelpers.sound(p, DarkShadowSounds.WHOOSH, 1.3f, 0.7f);
		diveStep(p, s);
	}

	private static void diveStep(ServerPlayer p, Session s) {
		if (s.diveUntil == 0L) {
			return;
		}
		if (now(p) > s.diveUntil) {
			s.diveUntil = 0L;
			Quirks.setVal(p, DarkShadow.V_DIVE, 0L);
			return;
		}
		AbilityHelpers.launchSelf(p, s.diveDir.scale(mv().diveSpeed));
		p.resetFallDistance();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, mv().diveRadius + p.getBbWidth() * 0.5)) {
			if (s.diveHit.add(e.getId()) && DarkShadowCombat.strike(p, e, c.subtract(s.diveDir), mv().diveDamage, 1.0, 0.3)) {
				QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
			}
		}
	}

	private static void tickBlackBodyScale(ServerPlayer p, boolean on) {
		var inst = p.getAttribute(Attributes.SCALE);
		if (inst == null) {
			return;
		}
		AttributeModifier m = inst.getModifier(BLACKBODY_SCALE_ID);
		double cur = m == null ? 0.0 : m.amount();
		double target = on ? mv().blackBodyScale : 0.0;
		if (Math.abs(cur - target) < 1.0e-4) {
			return;
		}
		double next = cur < target ? Math.min(target, cur + 0.05) : Math.max(target, cur - 0.05);
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			double w = 0.6 * (1.0 + next), h = 1.8 * (1.0 + next);
			AABB box = new AABB(p.getX() - w / 2, p.getY(), p.getZ() - w / 2, p.getX() + w / 2, p.getY() + h, p.getZ() + w / 2);
			if (!p.level().noCollision(p, box)) {
				return;
			}
		}
		if (next <= 1.0e-4) {
			PowerToggles.clearModifier(p, Attributes.SCALE, BLACKBODY_SCALE_ID);
		} else {
			PowerToggles.modifier(p, Attributes.SCALE, BLACKBODY_SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	// ================================================================ v0.17.1 Shadow Scout marks / the fall catch

	private static void syncMarks(ServerPlayer p, Session s) {
		long now = now(p);
		s.marks.values().removeIf(t -> t <= now);
		long until = 0L;
		int i = 0;
		java.util.Map<String, Long> put = new java.util.HashMap<>();
		for (Map.Entry<Integer, Long> e : s.marks.entrySet()) {
			if (i >= DarkShadow.MAX_MARKS) {
				break;
			}
			put.put(DarkShadow.V_MARK_PREFIX + i, e.getKey() + 1L);
			until = Math.max(until, e.getValue());
			i++;
		}
		final long u = until;
		Quirks.edit(p, n -> {
			for (int k = 0; k < DarkShadow.MAX_MARKS; k++) {
				n.vals.remove(DarkShadow.V_MARK_PREFIX + k);
			}
			n.vals.putAll(put);
			if (u > 0L) {
				n.vals.put(DarkShadow.V_MARK_UNTIL, u);
			} else {
				n.vals.remove(DarkShadow.V_MARK_UNTIL);
			}
		});
	}

	/**
	 * The fall catch: a fall of more than 6 blocks (over 3 damage) with Shadow above 30 -- it catches you (no fall damage);
	 * once per 20 s. Returns true when it caught you.
	 */
	public static boolean tryCatch(ServerPlayer p, DamageSource source, float amount) {
		if (!source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || amount < mv().catchMinDamage || Quirks.meter(p) <= mv().catchMinMeter
				|| DarkShadow.recalled(p) || DarkShadow.berserk(p) || !Quirks.ready(p, DarkShadowConfig.CATCH)) {
			return false;
		}
		Quirks.setCooldown(p, DarkShadowConfig.CATCH, mv().catchCooldown);
		Quirks.setVec(p, DarkShadow.VEC_REACH, p.position());
		Quirks.setVal(p, DarkShadow.V_REACH_AT, now(p));
		Quirks.setAnim(p, DarkShadow.ANIM_CATCH);
		ServerLevel level = p.serverLevel();
		QuirkFx.decal(level, p.position().add(0, 0.05, 0)).style(QuirkFx.DECAL_POOL).colour(0x0C0614).size(2.2f).life(80).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0x6A3CBF, 0x0C0614).size(2.8f).thickness(0.6f).life(14).ground()
				.spawn();
		level.sendParticles(QuirkParticles.SHADOW_MOTE, p.getX(), p.getY() + 0.4, p.getZ(), 20, 0.7, 0.2, 0.7, 0.02);
		AbilityHelpers.sound(p, DarkShadowSounds.WHOOSH, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.1f, 0.6f);
		Quirks.say(p, "message.projecthero.dark_shadow.caught", ChatFormatting.DARK_PURPLE);
		return true;
	}

	// ================================================================ V: Black Ankh / Recall

	/** Black Ankh on / off (off also folds the wings). Returns the new state. */
	public static boolean toggleAnkh(ServerPlayer p) {
		if (DarkShadow.ankh(p)) {
			ankhOff(p);
			Quirks.setCooldown(p, DarkShadowConfig.ANKH, mv().ankhCooldown);
			AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.5f);
			DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.0, 0), 16, 0.5);
			return false;
		}
		ankhOn(p);
		return true;
	}

	static void ankhOn(ServerPlayer p) {
		Quirks.setVal(p, DarkShadow.V_ANKH, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_ANKH);
		ankhStats(p);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_AMBIENT, 0.7f, 0.5f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.0, 0), 12, 0.5);
	}

	static void ankhStats(ServerPlayer p) {
		float d = DarkShadow.darkness(p);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ANKH_DAMAGE_ID, Math.round(mv().ankhDamage * s(p) * 10.0) / 10.0,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR, ANKH_ARMOR_ID, Math.round(Mth.lerp(d, mv().ankhArmorMin, mv().ankhArmorMax)),
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, ANKH_TOUGHNESS_ID, mv().ankhToughness, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, ANKH_REACH_ID, mv().ankhReach, AttributeModifier.Operation.ADD_VALUE);
	}

	static void ankhOff(ServerPlayer p) {
		abyssalOff(p);
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isAmbient() && nv.getDuration() <= NIGHT_OWL_TICKS) {
			p.removeEffect(MobEffects.NIGHT_VISION); // Night owl ends with the armour
		}
		if (DarkShadow.ankh(p)) {
			Quirks.setVal(p, DarkShadow.V_ANKH, 0L);
		}
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ANKH_DAMAGE_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR, ANKH_ARMOR_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, ANKH_TOUGHNESS_ID);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, ANKH_REACH_ID);
	}

	/** Recall on / off: tucked away, nothing works, the meter drains fast; Shift+V again calls it back. */
	public static boolean toggleRecall(ServerPlayer p) {
		if (DarkShadow.recalled(p)) {
			Quirks.setVal(p, DarkShadow.V_RECALL, 0L);
			Quirks.setAnim(p, DarkShadow.ANIM_SUMMON);
			Quirks.setCooldown(p, DarkShadowConfig.RECALL, mv().recallCooldown);
			AbilityHelpers.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.8f);
			AbilityHelpers.sound(p, SoundEvents.PHANTOM_AMBIENT, 1.0f, 0.7f);
			DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.back(p), 8, 0.35);
			return false;
		}
		stopMoves(p);
		Quirks.setVal(p, DarkShadow.V_RECALL, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_RECALL);
		Quirks.setCooldown(p, DarkShadowConfig.RECALL, mv().recallCooldown);
		AbilityHelpers.sound(p, SoundEvents.SCULK_BLOCK_CHARGE, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 0.8f, 0.7f);
		DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.back(p), 14, 0.3);
		return true;
	}

	/**
	 * v0.16.7 (user) "make pressing N hide dark shadow": N hides it (the Recall state: it tucks away into your back, isn't
	 * drawn, no move works, Shadow drains, it can't go berserk) and calls it back out. Shift+V is the same toggle. Calling
	 * it out always works; hiding waits out the short Recall cooldown, and a berserk shadow won't listen (only light calms
	 * it). Returns true if the state changed.
	 */
	public static boolean toggleHidden(ServerPlayer p) {
		if (!DarkShadow.is(p)) {
			return false;
		}
		if (DarkShadow.recalled(p)) {
			if (DarkShadow.running(p, DarkShadow.V_HIDE_LOCK)) {
				Quirks.say(p, "message.projecthero.dark_shadow.spent", ChatFormatting.DARK_PURPLE);
				return false; // spent after Abyssal Black Body: it comes back out on its own
			}
			toggleRecall(p);
			return true;
		}
		if (DarkShadow.blackBody(p)) {
			return false; // it IS your body right now
		}
		if (DarkShadow.on(p, DarkShadow.V_BERSERK)) {
			Quirks.say(p, "message.projecthero.dark_shadow.wont_hide", ChatFormatting.DARK_PURPLE);
			return false;
		}
		if (!Quirks.ready(p, DarkShadowConfig.RECALL)) {
			return false;
		}
		toggleRecall(p);
		return true;
	}

	// ================================================================ C: Berserk on purpose / Light Flare

	/**
	 * v0.17.1 Unleash (Shift+C): a CONTROLLED Berserk, only in darkness -- {@link DarkShadowConfig.Moves#unleashTicks} of it
	 * that never hits you or your squad, then the meter drops to 40 (light still ends it early). False if refused.
	 */
	public static boolean unleash(ServerPlayer p) {
		if (!DarkShadowCombat.inDarkness(p)) {
			Quirks.say(p, "message.projecthero.dark_shadow.too_bright", ChatFormatting.DARK_PURPLE);
			return false;
		}
		Quirks.setCooldown(p, DarkShadowConfig.UNLEASH, mv().berserkCooldown);
		goBerserk(p, 2);
		Quirks.setVal(p, DarkShadow.V_UNLEASH, now(p) + mv().unleashTicks);
		Quirks.setAnim(p, DarkShadow.ANIM_UNLEASH);
		QuirkFx.ring(p.serverLevel(), p.position().add(0, 0.05, 0)).colours(0xFF4A60, 0x1A0612).size(7f).thickness(0.9f).life(16).ground().spawn();
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		return true;
	}

	/** A light source to hand (held) or daylight on you: the flare can be lit. */
	public static boolean canFlare(ServerPlayer p) {
		if (DarkShadowCombat.heldLight(p) > 0) {
			return true;
		}
		return p.level().isDay() && p.level().canSeeSky(BlockPos.containing(p.getEyePosition()));
	}

	/** Light Flare: a flash that blinds nearby mobs, calms it (ends Berserk) and keeps it small for a moment. */
	public static boolean flare(ServerPlayer p) {
		if (!canFlare(p)) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_light", ChatFormatting.DARK_PURPLE);
			return false;
		}
		boolean wasBerserk = DarkShadow.on(p, DarkShadow.V_BERSERK);
		Quirks.setVal(p, DarkShadow.V_FLARE, now(p) + mv().flareLightTicks);
		if (wasBerserk) {
			endBerserk(p);
		}
		Quirks.setMeter(p, Math.min(Quirks.meter(p), mv().flareMeter));
		Session s = session(p);
		s.dark = 0f;
		syncDark(p, s, true);
		Quirks.setAnim(p, DarkShadow.ANIM_FLARE);
		Quirks.setCooldown(p, DarkShadowConfig.FLARE, mv().flareCooldown);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.getEyePosition().add(0, 0.5, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.8, 0.8, 0.8, 0.15);
		level.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y, c.z, 20, 1.5, 1.0, 1.5, 0.1);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, mv().flareRadius)) {
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, mv().flareBlindTicks, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, mv().flareBlindTicks / 2, 1);
		}
		AbilityHelpers.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 1.6f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_HURT, 0.8f, 1.6f); // the whimper
		Quirks.say(p, "message.projecthero.dark_shadow.calm", ChatFormatting.LIGHT_PURPLE);
		return true;
	}

	// ================================================================ Berserk

	/** Berserk: it breaks loose ({@code why}: 1 the meter, 2 on purpose) until light falls on it. */
	public static void goBerserk(ServerPlayer p, int why) {
		Quirks.overload(p, 20 * 60 * 20); // "until light returns": the light check below ends it
		Quirks.setVal(p, DarkShadow.V_BERSERK, why);
		Quirks.setMeter(p, 100f);
		Quirks.setAnim(p, DarkShadow.ANIM_BERSERK);
		session(p).berserkSince = now(p);
		Quirks.say(p, "message.projecthero.quirk.overloaded.dark_shadow", ChatFormatting.DARK_PURPLE);
		AbilityHelpers.sound(p, DarkShadowSounds.ROAR, 1.5f, 0.9f);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.5f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.6, 0), 24, 1.4);
	}

	/** Light calmed it: Berserk ends, the meter settles, it shrinks with a whimper. */
	public static void endBerserk(ServerPlayer p) {
		Quirks.setVal(p, DarkShadow.V_BERSERK, 0L);
		Quirks.setVal(p, DarkShadow.V_UNLEASH, 0L);
		Quirks.edit(p, n -> n.overloadUntil = 0L);
		Quirks.setMeter(p, Math.min(Quirks.meter(p), DarkShadowConfig.get().berserk.afterMeter));
		Quirks.setAnim(p, DarkShadow.ANIM_CALM);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_DEATH, 0.9f, 1.5f);
		AbilityHelpers.sound(p, SoundEvents.WOLF_WHINE, 0.9f, 0.6f);
		DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.shadowPos(p), 10, 0.6);
		Quirks.say(p, "message.projecthero.dark_shadow.calm", ChatFormatting.LIGHT_PURPLE);
	}

	private static void berserkTick(ServerPlayer p, int light) {
		DarkShadowConfig.Berserk b = DarkShadowConfig.get().berserk;
		long now = now(p);
		if (light >= DarkShadowConfig.get().light.calmLight) {
			endBerserk(p);
			return;
		}
		// v0.17.1 Unleash: the controlled Berserk ends on its own, with the meter at 40
		long unleash = DarkShadow.val(p, DarkShadow.V_UNLEASH);
		boolean controlled = unleash != 0L;
		if (controlled && now >= unleash) {
			endBerserk(p);
			Quirks.setMeter(p, mt().unleashAfter);
			return;
		}
		if (!Quirks.overloaded(p)) {
			Quirks.overload(p, 20 * 60 * 20);
		}
		Quirks.setMeter(p, 100f);
		ServerLevel level = p.serverLevel();
		if (now % b.interval == 0) {
			int hit = 0;
			for (LivingEntity t : DarkShadowCombat.frenzyTargets(p, b.radius)) {
				if (hit >= b.targetsPerSwing) {
					break;
				}
				if (DarkShadowCombat.strike(p, t, p.position(), b.damage * b.strength, b.knockback, 0.25)) {
					if (hit == 0) {
						reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_FRENZY_HIT);
					} else {
						DarkShadowCombat.streak(level, DarkShadowCombat.shadowPos(p), DarkShadowCombat.center(t));
					}
					hit++;
				}
			}
			if (hit > 0) {
				AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 1.1f, 0.5f + p.getRandom().nextFloat() * 0.2f);
			}
		}
		Session s = session(p);
		if (!controlled && (now - s.berserkSince) % b.selfInterval == b.selfInterval - 1) {
			// it turns on its own user -- never below selfFloor HP
			float dmg = Math.min(b.selfDamage, Math.max(0f, p.getHealth() - b.selfFloor));
			if (dmg > 0.05f) {
				p.hurt(p.damageSources().magic(), dmg);
				Vec3 c = p.position().add(0, 1.2, 0);
				level.sendParticles(DarkShadowCombat.BLOOD, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.0);
				AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 1.0f, 0.4f);
			}
		}
		if (now % 50 == 0) {
			AbilityHelpers.sound(p, DarkShadowSounds.GROWL, 1.2f, 0.7f); // the growl
		}
		if (now % 4 == 0) {
			Vec3 c = DarkShadowCombat.shadowPos(p).add(0, 1.2, 0);
			level.sendParticles(DarkShadowCombat.SHADOW_BIG, c.x, c.y, c.z, 4, 1.2, 1.2, 1.2, 0.0);
		}
	}

	// ================================================================ the tick

	/** The Shadow meter's step for a light level, per tick (before Ankh / Recall). */
	public static float meterStep(int light, float meter) {
		DarkShadowConfig.Light l = DarkShadowConfig.get().light;
		DarkShadowConfig.Meter m = mt();
		if (light >= l.calmLight) {
			return -m.brightFall;
		}
		if (light <= l.pitchDarkMeter) {
			return m.pitchRise;
		}
		return meter < m.dimCap ? Math.min(m.dimRise, m.dimCap - meter) : 0f;
	}

	private static void syncDark(ServerPlayer p, Session s, boolean force) {
		long synced = DarkShadow.val(p, DarkShadow.V_DARK);
		long want = Math.round(s.dark * 1000f);
		boolean edge = (want == 0L || want == 1000L) && synced != want;
		if (force || edge || Math.abs(want - synced) >= 40) {
			Quirks.setVal(p, DarkShadow.V_DARK, want);
		}
	}

	public static void tick(ServerPlayer p) {
		long now = now(p);
		Session s = session(p);
		int light = DarkShadowCombat.lightAt(p);
		DarkShadowConfig.Light lc = DarkShadowConfig.get().light;
		// the darkness it feels: smoothed, synced in steps
		float target = DarkShadowCombat.darknessFor(light);
		s.dark = s.dark < 0f ? target : s.dark + (target - s.dark) * lc.smoothing;
		if (Math.abs(target - s.dark) < 0.01f) {
			s.dark = target;
		}
		syncDark(p, s, false);

		// v0.17.1: spent after Abyssal Black Body -- it comes back out on its own when the lock runs out
		long hideLock = DarkShadow.val(p, DarkShadow.V_HIDE_LOCK);
		if (hideLock != 0L && now >= hideLock) {
			Quirks.setVal(p, DarkShadow.V_HIDE_LOCK, 0L);
			if (DarkShadow.recalled(p)) {
				toggleRecall(p);
			}
		}
		tickBlackBodyScale(p, DarkShadow.blackBody(p));
		if (DarkShadow.ankh(p) && (now % 80 == 0 || !p.hasEffect(MobEffects.NIGHT_VISION))) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_OWL_TICKS, 0, true, false, true)); // Night owl
		}
		if (!s.marks.isEmpty() && now % 20 == 0 && s.marks.values().stream().anyMatch(t -> t <= now)) {
			syncMarks(p, s);
		}
		if (DarkShadow.val(p, DarkShadow.V_BLACKBODY) != 0L) {
			blackBodyTick(p, s, now);
			diveStep(p, s);
			return;
		}

		if (DarkShadow.on(p, DarkShadow.V_BERSERK)) {
			berserkTick(p, light);
			return;
		}
		// the meter
		float m = Quirks.meter(p);
		float step = meterStep(light, m);
		if (DarkShadow.recalled(p)) {
			step = -mt().recallFall;
		} else if (step > 0f && DarkShadow.ankh(p)) {
			step *= mt().ankhRiseFactor;
		}
		m = Quirks.addMeterPaced(p, step); // v0.17.1: the climb in the dark x DarkShadowMeter.BUILD
		if (light >= lc.calmLight && m > 99f) {
			Quirks.setMeter(p, 99f);
			m = 99f;
		}
		if (m >= 100f && !DarkShadow.recalled(p)) {
			goBerserk(p, 1);
			return;
		}

		fangStep(p, s);
		grabStep(p, s);
		dashStep(p, s);
		fetchStep(p, s);
		scoutStep(p, s);
		xHoldStep(p, s);
		lungeStep(p, s);
		rampageStep(p, s);
		tossStep(p, s);

		long shield = DarkShadow.val(p, DarkShadow.V_SHIELD);
		if (shield != 0L) {
			if (now - shield > mv().shieldMaxTicks) {
				shieldOff(p);
			} else {
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 0, false, false, false));
				}
				if (now % 20 == 0) {
					push(p, mt().shieldPushPerSecond);
				}
				if (now % 4 == 0) {
					Vec3 c = p.position().add(0, 1.0, 0);
					p.serverLevel().sendParticles(DarkShadowCombat.SHADOW, c.x, c.y, c.z, 3, 0.55, 0.7, 0.55, 0.0);
				}
			}
		}
		if (DarkShadow.ankh(p) && now % mv().ankhRefresh == 0) {
			ankhStats(p);
		}
		if (DarkShadow.abyssal(p)) {
			abyssalTick(p);
		}
	}

	/** The moves in flight end (recall, overload): sessions, the Shield, the armour, the wings. */
	static void stopMoves(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			endFang(p, s);
			endGrab(p, s);
			endFetch(p, s);
			s.dashUntil = 0L;
			s.lungeAt = -1L;
			s.rampageAt = -1L;
			s.diveUntil = 0L;
			s.thrownId = -1;
			s.xAt = -1L;
			s.gHeld = false;
			if (s.scoutStart != 0L) {
				s.scoutStart = 0L;
				Quirks.setVal(p, DarkShadow.V_SCOUT, 0L);
				Quirks.setVec(p, DarkShadow.VEC_SCOUT, null);
			}
		}
		ankhOff(p);
		for (String k : new String[] { DarkShadow.V_SHIELD, DarkShadow.V_DASH, DarkShadow.V_FANG, DarkShadow.V_LUNGE, DarkShadow.V_RAMPAGE,
				DarkShadow.V_TOSS, DarkShadow.V_DIVE, DarkShadow.V_HOLD_X }) {
			if (DarkShadow.val(p, k) != 0L) {
				Quirks.setVal(p, k, 0L);
			}
		}
	}

	/** Everything ends (overload, revoke, death, Erased). Berserk keeps its flag (the overload is the Berserk). */
	public static void stopAll(ServerPlayer p) {
		stopMoves(p);
		// v0.17.1: Abyssal Black Body never outlives a death / logout / dimension change (and its size comes straight off)
		if (DarkShadow.val(p, DarkShadow.V_BLACKBODY) != 0L) {
			Quirks.setVal(p, DarkShadow.V_BLACKBODY, 0L);
		}
		PowerToggles.clearModifier(p, Attributes.SCALE, BLACKBODY_SCALE_ID);
	}
}
