package com.projecthero.mod.quirk.darkshadow;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow's twelve moves, the Shadow meter, Berserk, and the per-player sessions of the moves that run
 * over time (Flurry, Dive, Grab, Swallow, Rampage). Every public move is called by {@link DarkShadowKit#handle} once its
 * cooldown is ready, and by the gametests directly. Sessions hold entity ids, never entities.
 *
 * <pre>
 *   R  Shadow Strike         Shift+R  Shadow Flurry
 *   G  Shadow Guard (hold)   Shift+G  Shadow Counter
 *   X  Black Fallen Angel    Shift+X  Shadow Dive
 *   Z  Shadow Grab           Shift+Z  Grab and Throw
 *   V  Abyssal Rampage       Shift+V  Abyssal Swallow
 *   C  Black Ankh (toggle)   Shift+C  Calm (works while berserk)
 * </pre>
 */
public final class DarkShadowMoves {
	static final ResourceLocation ANKH_DAMAGE_ID = PowerToggles.id("quirk_dark_shadow_ankh_damage");
	static final ResourceLocation ANKH_ARMOR_ID = PowerToggles.id("quirk_dark_shadow_ankh_armor");
	static final ResourceLocation ANKH_TOUGHNESS_ID = PowerToggles.id("quirk_dark_shadow_ankh_toughness");

	static final class Session {
		int flurryTarget = -1;
		int flurryLeft;
		long flurryNext;
		long diveUntil;
		final Set<Integer> dived = new HashSet<>();
		int grabTarget = -1;
		long grabStart;
		Vec3 grabFrom = Vec3.ZERO;
		int swallowTarget = -1;
		long swallowUntil;
		long rampageUntil;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** Players whose flight the wings lent (creative / other powers keep theirs). */
	private static final Set<UUID> FLIGHT_GRANTED = ConcurrentHashMap.newKeySet();

	private DarkShadowMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
		FLIGHT_GRANTED.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
		FLIGHT_GRANTED.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	/** Where Dark Shadow comes out of: the upper back. */
	public static Vec3 back(ServerPlayer p) {
		return p.position().add(0, 1.25, 0).subtract(flatLook(p).scale(0.25));
	}

	/** Dark Shadow reaches for {@code at}: the model stretches there (synced) and the pose plays. */
	static void reach(ServerPlayer p, Vec3 at, int anim) {
		Quirks.setVec(p, DarkShadow.VEC_REACH, at);
		Quirks.setVal(p, DarkShadow.V_REACH_AT, now(p));
		Quirks.setAnim(p, anim);
		DarkShadowCombat.tendril(p.serverLevel(), back(p), at);
	}

	private static @Nullable LivingEntity byId(ServerPlayer p, int id) {
		if (id < 0) {
			return null;
		}
		Entity e = p.serverLevel().getEntity(id);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private static float s(ServerPlayer p) {
		return DarkShadowCombat.strength(p);
	}

	// ================================================================ R

	/** Dark Shadow lunges up to {@link DarkShadowConfig#STRIKE_RANGE} blocks. Returns the creature it hit, or null. */
	public static @Nullable LivingEntity strike(ServerPlayer p) {
		LivingEntity t = DarkShadowCombat.aimed(p, DarkShadowConfig.STRIKE_RANGE, DarkShadowConfig.STRIKE_ASSIST_DEGREES);
		Vec3 at = t != null ? DarkShadowCombat.center(t) : AbilityHelpers.aimPoint(p, DarkShadowConfig.STRIKE_RANGE);
		reach(p, at, DarkShadow.ANIM_STRIKE);
		Quirks.setCooldown(p, DarkShadowConfig.STRIKE, DarkShadowConfig.STRIKE_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.9f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
		if (t != null && DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.STRIKE_DAMAGE * s(p), DarkShadowConfig.STRIKE_KNOCKBACK, 0.15)) {
			return t;
		}
		DarkShadowCombat.smoke(p.serverLevel(), at, 6, 0.3);
		return null;
	}

	/** A rapid string of {@link DarkShadowConfig#FLURRY_HITS} strikes on the target ({@link #tick}). False without one. */
	public static boolean flurry(ServerPlayer p) {
		LivingEntity t = DarkShadowCombat.aimed(p, DarkShadowConfig.FLURRY_RANGE, DarkShadowConfig.STRIKE_ASSIST_DEGREES + 4.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_target", ChatFormatting.DARK_PURPLE);
			return false;
		}
		Session s = session(p);
		s.flurryTarget = t.getId();
		s.flurryLeft = DarkShadowConfig.FLURRY_HITS;
		s.flurryNext = now(p);
		Quirks.setVal(p, DarkShadow.V_FLURRY, now(p) + (long) DarkShadowConfig.FLURRY_HITS * DarkShadowConfig.FLURRY_INTERVAL + 4);
		Quirks.setCooldown(p, DarkShadowConfig.FLURRY, DarkShadowConfig.FLURRY_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.2f);
		flurryStep(p, s);
		return true;
	}

	private static void flurryStep(ServerPlayer p, Session s) {
		if (s.flurryLeft <= 0 || now(p) < s.flurryNext) {
			return;
		}
		LivingEntity t = byId(p, s.flurryTarget);
		if (t == null || t.distanceTo(p) > DarkShadowConfig.FLURRY_RANGE + 2.0) {
			endFlurry(p, s);
			return;
		}
		s.flurryLeft--;
		s.flurryNext = now(p) + DarkShadowConfig.FLURRY_INTERVAL;
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_FLURRY);
		boolean last = s.flurryLeft == 0;
		DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.FLURRY_DAMAGE * s(p), last ? 0.8 : 0.05, 0.0);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.8f + 0.1f * s.flurryLeft);
		if (last) {
			endFlurry(p, s);
		}
	}

	private static void endFlurry(ServerPlayer p, Session s) {
		s.flurryTarget = -1;
		s.flurryLeft = 0;
		Quirks.setVal(p, DarkShadow.V_FLURRY, 0L);
	}

	// ================================================================ G

	/** Shadow Guard goes up (held). */
	public static void guardOn(ServerPlayer p) {
		if (DarkShadow.guarding(p)) {
			return;
		}
		Quirks.setVal(p, DarkShadow.V_GUARD, Math.max(1L, now(p)));
		Quirks.setAnim(p, DarkShadow.ANIM_GUARD);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 0.6f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(flatLook(p).scale(0.6)).add(0, 1.0, 0), 10, 0.45);
	}

	/** The key is released (or the Guard timed out): it comes down and the cooldown starts. */
	public static void guardOff(ServerPlayer p) {
		if (!DarkShadow.guarding(p)) {
			return;
		}
		Quirks.setVal(p, DarkShadow.V_GUARD, 0L);
		Quirks.setCooldown(p, DarkShadowConfig.GUARD, DarkShadowConfig.GUARD_COOLDOWN);
	}

	/** Shadow Counter: a short parry window; the next hit is swallowed and answered with a strike. */
	public static void counter(ServerPlayer p) {
		Quirks.setVal(p, DarkShadow.V_COUNTER, now(p) + DarkShadowConfig.COUNTER_WINDOW);
		Quirks.setAnim(p, DarkShadow.ANIM_GUARD);
		Quirks.setCooldown(p, DarkShadowConfig.COUNTER, DarkShadowConfig.COUNTER_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 0.9f, 1.3f);
	}

	/** Did a hit come from in front of the body? (Its source position, else the attacker's.) */
	static boolean fromFront(ServerPlayer p, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null && source.getEntity() != null) {
			from = source.getEntity().position();
		}
		if (from == null) {
			return false;
		}
		Vec3 to = new Vec3(from.x - p.getX(), 0, from.z - p.getZ());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		Vec3 facing = Vec3.directionFromRotation(0f, p.getYHeadRot());
		return to.normalize().dot(facing) >= Math.cos(Math.toRadians(DarkShadowConfig.GUARD_ARC_DEGREES));
	}

	/** The Guard / the Counter on an incoming hit. */
	static float incoming(ServerPlayer p, DamageSource source, float amount) {
		long now = now(p);
		if (DarkShadow.val(p, DarkShadow.V_COUNTER) > now && source.getEntity() instanceof LivingEntity attacker && attacker != p) {
			Quirks.setVal(p, DarkShadow.V_COUNTER, 0L);
			reach(p, DarkShadowCombat.center(attacker), DarkShadow.ANIM_COUNTER);
			DarkShadowCombat.strike(p, attacker, p.position(), DarkShadowConfig.COUNTER_DAMAGE * s(p), DarkShadowConfig.COUNTER_KNOCKBACK, 0.25);
			AbilityHelpers.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.6f);
			AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 1.0f, 0.6f);
			return 0f;
		}
		if (DarkShadow.guarding(p) && fromFront(p, source)) {
			DarkShadowCombat.smoke(p.serverLevel(), p.position().add(flatLook(p).scale(0.6)).add(0, 1.1, 0), 6, 0.3);
			AbilityHelpers.sound(p, SoundEvents.SHIELD_BLOCK, 0.8f, 0.5f);
			if (source.getDirectEntity() instanceof Projectile) {
				return 0f;
			}
			return amount * (1f - DarkShadowConfig.GUARD_BLOCK);
		}
		return amount;
	}

	// ================================================================ X

	/** Black Fallen Angel on / off: shadow wings, flight. Returns the new state. */
	public static boolean toggleAngel(ServerPlayer p) {
		if (DarkShadow.angel(p)) {
			angelOff(p, true);
			Quirks.setCooldown(p, DarkShadowConfig.ANGEL, DarkShadowConfig.ANGEL_COOLDOWN);
			return false;
		}
		Quirks.setVal(p, DarkShadow.V_ANGEL, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_ANGEL);
		openWings(p);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.45, 0));
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.2f, 0.5f);
		DarkShadowCombat.smoke(p.serverLevel(), back(p), 18, 0.6);
		return true;
	}

	private static void openWings(ServerPlayer p) {
		if (!p.getAbilities().mayfly) {
			FLIGHT_GRANTED.add(p.getUUID());
			p.getAbilities().mayfly = true;
		}
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(DarkShadowConfig.ANGEL_FLY_SPEED);
		p.onUpdateAbilities();
	}

	static void angelOff(ServerPlayer p, boolean announce) {
		if (DarkShadow.angel(p)) {
			Quirks.setVal(p, DarkShadow.V_ANGEL, 0L);
		}
		if (p.getAbilities().getFlyingSpeed() == DarkShadowConfig.ANGEL_FLY_SPEED) {
			p.getAbilities().setFlyingSpeed(0.05f);
		}
		if (FLIGHT_GRANTED.remove(p.getUUID())) {
			boolean airborne = !p.onGround();
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.resetFallDistance();
			if (airborne) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DarkShadowConfig.ANGEL_SAFE_FALL_TICKS, 0, false, false, true));
			}
		}
		p.onUpdateAbilities();
		if (announce) {
			DarkShadowCombat.smoke(p.serverLevel(), back(p), 10, 0.5);
		}
	}

	/** A fast dash the way you look; whatever it passes through is struck. */
	public static void dive(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		AbilityHelpers.launchSelf(p, look.scale(DarkShadowConfig.DIVE_SPEED));
		Session s = session(p);
		s.diveUntil = now(p) + DarkShadowConfig.DIVE_TICKS;
		s.dived.clear();
		Quirks.setVal(p, DarkShadow.V_DIVE, s.diveUntil);
		Quirks.setAnim(p, DarkShadow.ANIM_DIVE);
		Quirks.setCooldown(p, DarkShadowConfig.DIVE, DarkShadowConfig.DIVE_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 0.8f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1, 0), 12, 0.4);
		diveStep(p, s);
	}

	private static void diveStep(ServerPlayer p, Session s) {
		if (now(p) > s.diveUntil) {
			return;
		}
		p.resetFallDistance();
		Vec3 c = p.position().add(0, 0.9, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, DarkShadowConfig.DIVE_HIT_RADIUS + 0.8)) {
			if (s.dived.add(e.getId())) {
				DarkShadowCombat.strike(p, e, c, DarkShadowConfig.DIVE_DAMAGE * s(p), 0.6, 0.2);
			}
		}
		DarkShadowCombat.smoke(p.serverLevel(), c, 4, 0.35);
		if (now(p) >= s.diveUntil) {
			Quirks.setVal(p, DarkShadow.V_DIVE, 0L);
		}
	}

	// ================================================================ Z

	private static @Nullable LivingEntity grabbable(ServerPlayer p) {
		LivingEntity t = DarkShadowCombat.aimed(p, DarkShadowConfig.GRAB_RANGE, DarkShadowConfig.GRAB_ASSIST_DEGREES);
		if (t == null) {
			Quirks.say(p, "message.projecthero.dark_shadow.no_target", ChatFormatting.DARK_PURPLE);
		}
		return t;
	}

	private static Vec3 holdPoint(ServerPlayer p) {
		return p.position().add(flatLook(p).scale(DarkShadowConfig.GRAB_HOLD_DISTANCE)).add(0, 0.1, 0);
	}

	/** Snatch a creature and pull it to you (over {@link DarkShadowConfig#GRAB_PULL_TICKS}). Bosses are only struck. */
	public static @Nullable LivingEntity grab(ServerPlayer p) {
		LivingEntity t = grabbable(p);
		if (t == null) {
			return null;
		}
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_GRAB);
		Quirks.setCooldown(p, DarkShadowConfig.GRAB, DarkShadowConfig.GRAB_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.9f, 0.9f);
		DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.GRAB_DAMAGE * s(p), 0.0, 0.0);
		if (DarkShadowCombat.isBoss(t) || !AbilityHelpers.isValidGrabTarget(t, p)) {
			return t;
		}
		Session s = session(p);
		s.grabTarget = t.getId();
		s.grabStart = now(p);
		s.grabFrom = t.position();
		Quirks.setVal(p, DarkShadow.V_GRAB, t.getId() + 1L);
		Quirks.setVal(p, DarkShadow.V_GRAB_END, now(p) + DarkShadowConfig.GRAB_PULL_TICKS + 4);
		grabStep(p, s);
		return t;
	}

	private static void grabStep(ServerPlayer p, Session s) {
		if (s.grabTarget < 0) {
			return;
		}
		LivingEntity t = byId(p, s.grabTarget);
		long age = now(p) - s.grabStart;
		if (t == null || age > DarkShadowConfig.GRAB_PULL_TICKS) {
			if (t != null) {
				t.setDeltaMovement(Vec3.ZERO);
				t.hurtMarked = true;
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
			}
			s.grabTarget = -1;
			Quirks.setVal(p, DarkShadow.V_GRAB, 0L);
			Quirks.setVal(p, DarkShadow.V_GRAB_END, 0L);
			return;
		}
		float k = Math.min(1f, (age + 1f) / DarkShadowConfig.GRAB_PULL_TICKS);
		Vec3 to = s.grabFrom.lerp(holdPoint(p), k * k * (3f - 2f * k));
		t.teleportTo(to.x, to.y, to.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		t.resetFallDistance();
		DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.center(t), 3, 0.25);
	}

	/** Snatch it and hurl it the way you look. */
	public static @Nullable LivingEntity grabThrow(ServerPlayer p) {
		LivingEntity t = grabbable(p);
		if (t == null) {
			return null;
		}
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_THROW);
		Quirks.setCooldown(p, DarkShadowConfig.THROW, DarkShadowConfig.THROW_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 0.9f, 0.7f);
		if (DarkShadowCombat.isBoss(t) || !AbilityHelpers.isValidGrabTarget(t, p)) {
			DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.THROW_DAMAGE * s(p), 0.0, 0.0);
			return t;
		}
		Vec3 hold = holdPoint(p).add(0, 0.6, 0);
		t.teleportTo(hold.x, hold.y, hold.z);
		DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.THROW_DAMAGE * s(p), 0.0, 0.0);
		Vec3 v = p.getLookAngle().scale(DarkShadowConfig.THROW_SPEED).add(0, DarkShadowConfig.THROW_LIFT, 0);
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		t.hasImpulse = true;
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
		DarkShadowCombat.smoke(p.serverLevel(), hold, 10, 0.4);
		return t;
	}

	// ================================================================ V

	/** Abyssal Rampage: in darkness only, 6 s of uncontrolled frenzy all around you. False if refused (too bright). */
	public static boolean rampage(ServerPlayer p) {
		if (!DarkShadowCombat.inDarkness(p)) {
			Quirks.say(p, "message.projecthero.dark_shadow.too_bright", ChatFormatting.DARK_PURPLE);
			return false;
		}
		Session s = session(p);
		s.rampageUntil = now(p) + DarkShadowConfig.RAMPAGE_TICKS;
		Quirks.setVal(p, DarkShadow.V_RAMPAGE, s.rampageUntil);
		Quirks.setAnim(p, DarkShadow.ANIM_RAMPAGE);
		Quirks.setCooldown(p, DarkShadowConfig.RAMPAGE, DarkShadowConfig.RAMPAGE_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_AMBIENT, 1.2f, 0.5f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.2, 0), 30, 1.2);
		return true;
	}

	/** Every {@code interval}: Dark Shadow lashes out at up to {@code n} creatures around (frenzy rules). */
	private static int frenzy(ServerPlayer p, double radius, int n, float damage, double knock, int interval) {
		if ((now(p) % interval) != 0) {
			return 0;
		}
		List<LivingEntity> targets = DarkShadowCombat.frenzyTargets(p, radius);
		int hit = 0;
		for (LivingEntity t : targets) {
			if (hit >= n) {
				break;
			}
			if (DarkShadowCombat.strike(p, t, p.position(), damage, knock, 0.2)) {
				hit++;
				if (hit == 1) {
					reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_FRENZY_HIT);
				} else {
					DarkShadowCombat.tendril(p.serverLevel(), back(p), DarkShadowCombat.center(t));
				}
			}
		}
		if (hit > 0) {
			AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 1.0f, 0.5f + p.getRandom().nextFloat() * 0.3f);
		}
		DarkShadowCombat.smoke(p.serverLevel(), back(p).add(0, 0.5, 0), 6, 0.7);
		return hit;
	}

	/** Abyssal Swallow: Dark Shadow swallows a creature and holds it 5 s, chewing on it. Bosses refuse. */
	public static @Nullable LivingEntity swallow(ServerPlayer p) {
		LivingEntity t = DarkShadowCombat.aimed(p, DarkShadowConfig.SWALLOW_RANGE, DarkShadowConfig.GRAB_ASSIST_DEGREES);
		if (t == null || DarkShadowCombat.isBoss(t) || !AbilityHelpers.isValidGrabTarget(t, p)) {
			Quirks.say(p, "message.projecthero.dark_shadow.cant_swallow", ChatFormatting.DARK_PURPLE);
			return null;
		}
		Session s = session(p);
		s.swallowTarget = t.getId();
		s.swallowUntil = now(p) + DarkShadowConfig.SWALLOW_TICKS;
		Quirks.setVal(p, DarkShadow.V_SWALLOW, t.getId() + 1L);
		Quirks.setVal(p, DarkShadow.V_SWALLOW_END, s.swallowUntil);
		reach(p, DarkShadowCombat.center(t), DarkShadow.ANIM_SWALLOW);
		Quirks.setCooldown(p, DarkShadowConfig.SWALLOW, DarkShadowConfig.SWALLOW_COOLDOWN);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_BITE, 1.2f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EAT, 1.0f, 0.5f);
		return t;
	}

	/** Where the swallowed creature is held: in Dark Shadow's maw, up and in front of you. */
	public static Vec3 mawPoint(ServerPlayer p) {
		return p.position().add(flatLook(p).scale(1.6)).add(0, 1.4, 0);
	}

	private static void swallowStep(ServerPlayer p, Session s) {
		if (s.swallowTarget < 0) {
			return;
		}
		LivingEntity t = byId(p, s.swallowTarget);
		if (t == null || now(p) >= s.swallowUntil) {
			endSwallow(p, s, t);
			return;
		}
		Vec3 at = mawPoint(p).subtract(0, t.getBbHeight() * 0.5, 0);
		t.teleportTo(at.x, at.y, at.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		t.resetFallDistance();
		long age = s.swallowUntil - now(p);
		if (age % DarkShadowConfig.SWALLOW_INTERVAL == 0) {
			DarkShadowCombat.strike(p, t, p.position(), DarkShadowConfig.SWALLOW_DAMAGE * s(p), 0.0, 0.0);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EAT, 0.6f, 0.6f);
		}
		DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.center(t), 4, 0.35);
	}

	private static void endSwallow(ServerPlayer p, Session s, @Nullable LivingEntity t) {
		s.swallowTarget = -1;
		Quirks.setVal(p, DarkShadow.V_SWALLOW, 0L);
		Quirks.setVal(p, DarkShadow.V_SWALLOW_END, 0L);
		if (t != null) {
			t.setDeltaMovement(flatLook(p).scale(0.6).add(0, 0.2, 0));
			t.hurtMarked = true;
			DarkShadowCombat.smoke(p.serverLevel(), DarkShadowCombat.center(t), 12, 0.4);
		}
	}

	// ================================================================ C

	/** Black Ankh on / off: shadow armour with claws. Returns the new state. */
	public static boolean toggleAnkh(ServerPlayer p) {
		if (DarkShadow.ankh(p)) {
			ankhOff(p);
			Quirks.setCooldown(p, DarkShadowConfig.ANKH, DarkShadowConfig.ANKH_COOLDOWN);
			return false;
		}
		Quirks.setVal(p, DarkShadow.V_ANKH, 1L);
		Quirks.setAnim(p, DarkShadow.ANIM_ANKH);
		ankhStats(p);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_AMBIENT, 0.7f, 0.6f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.0, 0), 20, 0.5);
		return true;
	}

	static void ankhStats(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ANKH_DAMAGE_ID, Math.round(DarkShadowConfig.ANKH_DAMAGE * s(p) * 10.0) / 10.0,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR, ANKH_ARMOR_ID, DarkShadowConfig.ANKH_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, ANKH_TOUGHNESS_ID, DarkShadowConfig.ANKH_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
	}

	static void ankhOff(ServerPlayer p) {
		if (DarkShadow.ankh(p)) {
			Quirks.setVal(p, DarkShadow.V_ANKH, 0L);
		}
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ANKH_DAMAGE_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR, ANKH_ARMOR_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, ANKH_TOUGHNESS_ID);
	}

	/** Calm: a flash of light settles Dark Shadow -- the meter drops, Berserk and Rampage end. Works while berserk. */
	public static void calm(ServerPlayer p) {
		Quirks.setMeter(p, Math.min(Quirks.meter(p), DarkShadowConfig.CALM_METER));
		Session s = session(p);
		s.rampageUntil = 0L;
		Quirks.setVal(p, DarkShadow.V_RAMPAGE, 0L);
		endSwallow(p, s, byId(p, s.swallowTarget));
		if (DarkShadow.on(p, DarkShadow.V_BERSERK)) {
			Quirks.setVal(p, DarkShadow.V_BERSERK, 0L);
			Quirks.edit(p, n -> n.overloadUntil = 0L);
		}
		Quirks.setAnim(p, DarkShadow.ANIM_CALM);
		Quirks.setCooldown(p, DarkShadowConfig.CALM, DarkShadowConfig.CALM_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.getEyePosition().add(0, 0.4, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 24, 0.6, 0.6, 0.6, 0.08);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_DEATH, 0.6f, 1.4f);
		Quirks.say(p, "message.projecthero.dark_shadow.calm", ChatFormatting.LIGHT_PURPLE);
	}

	// ================================================================ the meter, Berserk, the running moves

	/** The Shadow meter step for {@code light} (0..15), per tick. */
	public static float meterStep(int light) {
		return (DarkShadowConfig.LIGHT_PIVOT - light) * DarkShadowConfig.METER_RATE;
	}

	public static void tick(ServerPlayer p) {
		long now = now(p);
		int light = DarkShadowCombat.lightAt(p);
		boolean berserkFlag = DarkShadow.on(p, DarkShadow.V_BERSERK);
		boolean overloaded = Quirks.overloaded(p);
		if (berserkFlag && !overloaded) {
			// the Berserk burnt out on its own
			Quirks.setVal(p, DarkShadow.V_BERSERK, 0L);
			Quirks.setMeter(p, DarkShadowConfig.BERSERK_AFTER_METER);
			berserkFlag = false;
		}
		if (berserkFlag) {
			Quirks.setMeter(p, 100f);
			frenzy(p, DarkShadowConfig.BERSERK_RADIUS, 1, DarkShadowConfig.BERSERK_DAMAGE * DarkShadowConfig.STRENGTH_MAX,
					DarkShadowConfig.BERSERK_KNOCKBACK, DarkShadowConfig.BERSERK_INTERVAL);
			if (now % 40 == 0) {
				AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.7f);
			}
			return;
		}
		float m = Quirks.addMeter(p, meterStep(light));
		if (m >= 100f && light <= DarkShadowConfig.DARK_LIGHT && !overloaded) {
			goBerserk(p);
			return;
		}
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			flurryStep(p, s);
			diveStep(p, s);
			grabStep(p, s);
			swallowStep(p, s);
			if (s.rampageUntil > now) {
				frenzy(p, DarkShadowConfig.RAMPAGE_RADIUS, DarkShadowConfig.RAMPAGE_TARGETS_PER_HIT, DarkShadowConfig.RAMPAGE_DAMAGE * s(p),
						DarkShadowConfig.RAMPAGE_KNOCKBACK, DarkShadowConfig.RAMPAGE_INTERVAL);
			} else if (s.rampageUntil != 0L) {
				s.rampageUntil = 0L;
				Quirks.setVal(p, DarkShadow.V_RAMPAGE, 0L);
			}
		}
		long guard = DarkShadow.val(p, DarkShadow.V_GUARD);
		if (guard != 0L && now - guard > DarkShadowConfig.GUARD_MAX_TICKS) {
			guardOff(p);
		}
		if (DarkShadow.ankh(p) && now % DarkShadowConfig.ANKH_REFRESH == 0) {
			ankhStats(p);
		}
		if (DarkShadow.angel(p)) {
			if (!p.getAbilities().mayfly) {
				openWings(p);
			}
			p.resetFallDistance();
		}
		// the smoke that always clings to it (cheap: a few times a second)
		if (now % 7 == 0) {
			Vec3 b = back(p).add(0, 0.45, 0);
			p.serverLevel().sendParticles(DarkShadowCombat.SHADOW, b.x, b.y, b.z, 1, 0.25, 0.2, 0.25, 0.0);
		}
	}

	/** Berserk: Dark Shadow breaks loose for {@link DarkShadowConfig#BERSERK_TICKS}. */
	public static void goBerserk(ServerPlayer p) {
		Quirks.overload(p, DarkShadowConfig.BERSERK_TICKS);
		Quirks.setVal(p, DarkShadow.V_BERSERK, 1L);
		Quirks.setMeter(p, 100f);
		Quirks.say(p, "message.projecthero.quirk.overloaded.dark_shadow", ChatFormatting.DARK_PURPLE);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_ROAR, 0.5f, 1.4f);
		DarkShadowCombat.smoke(p.serverLevel(), p.position().add(0, 1.4, 0), 40, 1.4);
	}

	/** Everything running ends (overload, revoke, death, Erased): the wings, the armour, held creatures, the Guard. */
	public static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			endFlurry(p, s);
			s.diveUntil = 0L;
			if (s.grabTarget >= 0) {
				s.grabTarget = -1;
			}
			endSwallow(p, s, byId(p, s.swallowTarget));
			s.rampageUntil = 0L;
		}
		angelOff(p, false);
		ankhOff(p);
		for (String k : new String[] { DarkShadow.V_GUARD, DarkShadow.V_COUNTER, DarkShadow.V_RAMPAGE, DarkShadow.V_GRAB, DarkShadow.V_GRAB_END,
				DarkShadow.V_DIVE, DarkShadow.V_FLURRY }) {
			if (DarkShadow.val(p, k) != 0L) {
				Quirks.setVal(p, k, 0L);
			}
		}
	}

	/** The holder is the Dark Shadow user and not Erased (used by the kit before the tick). */
	static boolean active(ServerPlayer p) {
		return Quirks.activeAs(p, Quirk.DARK_SHADOW);
	}
}
