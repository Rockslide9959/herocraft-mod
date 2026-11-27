package com.projecthero.mod.worthy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.titanshifter.TitanCombat;
import com.projecthero.mod.worthy.WorthyKit.Kit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier: the moves of the three lent-weapon kits (see {@link WorthyKit} for the key table, {@link WorthyConfig}
 * for every number). Server side; all per-player state lives in {@link WorthyState} (cooldowns, the busy lock, the animation, the
 * timed move running, the thrown weapon) -- no static collections.
 * <p>
 * Rules shared by every move: squad-safe targeting ({@link HeroTargets#canHarm}: never himself, squadmates, protected pets, creative
 * players, PvP off), a boss takes at most {@link WorthyConfig#BOSS_MAX_FRACTION_PER_HIT} of its max health per hit and is never knocked
 * about, a move with nothing to hit costs no cooldown. Thrown weapons really leave the hand ({@link ThorThrown}, Thor's own safety
 * net: persistent, handed back before death, never rebuilt) and the client draws their flight from {@link WorthyState}.
 */
public final class WorthyMoves {
	private static final ResourceLocation MELEE_ID = ResourceLocation.fromNamespaceAndPath("projecthero", "worthy_melee");

	private WorthyMoves() {
	}

	// ================================================================== shared helpers

	static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	/** Cooldown + busy lock; false (nothing spent) if either is not ready. */
	static boolean begin(ServerPlayer p, String id, int cooldown, int lock) {
		WorthyState s = WorthyState.of(p);
		long now = now(p);
		if (s.busyUntil > now) {
			return false;
		}
		String key = WorthyKit.cooldownKey(id);
		Long ready = s.readyAt.get(key);
		if (ready != null && ready > now) {
			return false;
		}
		WorthyState.edit(p, n -> {
			n.readyAt.put(key, now + cooldown);
			n.busyUntil = now + lock;
		});
		return true;
	}

	/** Gives a move's cooldown back (it found nothing to hit). */
	static void refund(ServerPlayer p, String id) {
		String key = WorthyKit.cooldownKey(id);
		WorthyState.edit(p, n -> {
			n.readyAt.remove(key);
			n.busyUntil = 0L;
		});
	}

	/** Ticks left on {@code id}'s cooldown (the HUD; both sides). */
	public static int cooldownRemaining(net.minecraft.world.entity.player.Player p, String id) {
		Long ready = WorthyState.of(p).readyAt.get(WorthyKit.cooldownKey(id));
		return ready == null ? 0 : (int) Math.max(0L, ready - p.level().getGameTime());
	}

	static void anim(ServerPlayer p, int animId) {
		long now = now(p);
		boolean off = WorthyLending.lentHand(p) == InteractionHand.OFF_HAND;
		WorthyState.edit(p, n -> {
			n.anim = animId;
			n.animStart = now;
			n.animOff = off;
		});
	}

	static void startSeq(ServerPlayer p, int seq, @Nullable LivingEntity target, Vec3 at, int weapon) {
		long now = now(p);
		WorthyState.edit(p, n -> {
			n.seq = seq;
			n.seqStart = now;
			n.seqTarget = target == null ? -1 : target.getId();
			n.seqX = at.x;
			n.seqY = at.y;
			n.seqZ = at.z;
			n.seqWeapon = weapon;
		});
	}

	static void endSeq(ServerPlayer p) {
		WorthyState.edit(p, n -> {
			n.seq = WorthyState.SEQ_NONE;
			n.seqTarget = -1;
		});
	}

	static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= WorthyConfig.BOSS_HEALTH || TitanCombat.isBoss(e);
	}

	static boolean canHit(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && HeroTargets.canHarm(p, e);
	}

	/** One hit: squad-safe, boss-capped, then the knockback / lift (never on a boss). */
	static boolean hit(ServerPlayer p, LivingEntity target, float damage, Vec3 origin, double knockback, double lift) {
		if (!canHit(p, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * WorthyConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		boolean landed = AbilityHelpers.hurtBurst(p, target, Math.max(1.0f, dmg)) || AbilityHelpers.hurtLands(p, target, Math.max(1.0f, dmg));
		if (!landed) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift != 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		p.serverLevel().sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.3);
		return true;
	}

	/** Everyone within {@code radius} of {@code center}; 60% at the edge. Returns how many were hit. */
	static int radial(ServerPlayer p, Vec3 center, double radius, float damage, double knockback, double lift, @Nullable LivingEntity skip) {
		int n = 0;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), e -> canHit(p, e))) {
			if (e == skip) {
				continue;
			}
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (d > radius) {
				continue;
			}
			float f = (float) (1.0 - 0.4 * (d / radius));
			if (hit(p, e, damage * f, center, knockback, lift)) {
				n++;
			}
		}
		return n;
	}

	/** Everyone in front within {@code range} and the cone ({@code minDot} = cosine of its half angle). */
	static int cone(ServerPlayer p, double range, double minDot, float damage, double knockback, double lift, boolean falloff) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = flat(p.getLookAngle());
		int n = 0;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> canHit(p, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			Vec3 flatTo = flat(to);
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, eye));
			if (d > range || (flatTo.lengthSqr() > 1.0e-4 && flatTo.dot(look) < minDot && d > 1.2)) {
				continue;
			}
			float f = falloff ? (float) (1.0 - 0.4 * Math.min(1.0, d / range)) : 1.0f;
			if (hit(p, e, damage * f, p.position(), knockback, lift)) {
				n++;
			}
		}
		return n;
	}

	static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0.0, v.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** The enemy he is facing within {@code range}: the crosshair first, else the nearest one in a narrow cone. */
	@Nullable
	static LivingEntity facing(ServerPlayer p, double range) {
		LivingEntity ray = AbilityHelpers.raycastEntity(p, range);
		if (ray != null && canHit(p, ray)) {
			return ray;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> canHit(p, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, eye));
			if (d > range || to.lengthSqr() < 1.0e-6 || to.normalize().dot(look) < 0.6 || !p.hasLineOfSight(e)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	@Nullable
	static LivingEntity seqTarget(ServerPlayer p, WorthyState s) {
		Entity e = s.seqTarget < 0 ? null : p.level().getEntity(s.seqTarget);
		return e instanceof LivingEntity l && l.isAlive() ? l : null;
	}

	/** Hostiles around {@code c} that a bolt may pick, nearest first. */
	static List<LivingEntity> hostiles(ServerPlayer p, Vec3 c, double radius) {
		List<LivingEntity> out = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
				e -> canHit(p, e) && HeroTargets.isHostile(p, e)));
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
		return out;
	}

	static void noFall(ServerPlayer p) {
		long until = now(p) + WorthyConfig.NO_FALL_TICKS;
		WorthyState.edit(p, n -> n.noFallUntil = Math.max(n.noFallUntil, until));
	}

	static void launchSelf(ServerPlayer p, Vec3 v) {
		AbilityHelpers.launchSelf(p, v);
	}

	// ================================================================== dispatch

	/** One key's move for {@code kit} (from {@link WorthyKit#handle} and the right-click throw). */
	public static void fire(ServerPlayer p, Kit kit, String id) {
		boolean axe = kit.axe();
		switch (id) {
			case WorthyKit.HAMMER_COMBO -> hammerCombo(p);
			case WorthyKit.THUNDER_UPPERCUT -> uppercut(p, WorthyKit.THUNDER_UPPERCUT, WorthyConfig.UPPERCUT_CD, WorthyConfig.UPPERCUT_DAMAGE, false);
			case WorthyKit.CALL_LIGHTNING -> callLightning(p);
			case WorthyKit.THUNDERCLAP -> thunderclap(p);
			case WorthyKit.THUNDER_SLAM -> slam(p, WorthyKit.THUNDER_SLAM, WorthyConfig.SLAM_CD, WorthyState.SEQ_SLAM, false);
			case WorthyKit.WORTHY_STORM -> worthyStorm(p);
			case WorthyKit.HAMMER_DASH, WorthyKit.AXE_DASH -> dash(p, id, axe);
			case WorthyKit.HAMMER_LAUNCH, WorthyKit.STORM_LEAP -> launch(p, id);
			case WorthyKit.WHIRL_GUARD, WorthyKit.AXE_GUARD -> guard(p, id, false);
			case WorthyKit.LIGHTNING_GUARD -> guard(p, id, true);
			case WorthyKit.RALLY_BOLT -> rally(p, axe);
			case WorthyKit.HAMMER_THROW, WorthyKit.AXE_THROW -> weaponThrow(p, id, axe, kit.dual);
			case WorthyKit.AXE_CLEAVES -> axeCleaves(p);
			case WorthyKit.RISING_CLEAVE -> uppercut(p, WorthyKit.RISING_CLEAVE, WorthyConfig.RISING_CLEAVE_CD, WorthyConfig.RISING_CLEAVE_DAMAGE, true);
			case WorthyKit.LIGHTNING_CLEAVE -> lightningCleave(p);
			case WorthyKit.EXECUTIONERS_CHOP -> executionersChop(p);
			case WorthyKit.STORM_CRASH -> slam(p, WorthyKit.STORM_CRASH, WorthyConfig.CRASH_CD, WorthyState.SEQ_CRASH, true);
			case WorthyKit.VERDICT -> verdict(p);
			case WorthyKit.SHIELD_AND_WEAPON -> shieldAndWeapon(p, axe);
			case WorthyKit.BASH_LAUNCHER -> bashLauncher(p, axe);
			case WorthyKit.CHARGED_SHIELD_THROW -> chargedShieldThrow(p);
			case WorthyKit.TWIN_THROW -> twinThrow(p, axe);
			case WorthyKit.LIGHTNING_SHIELD_SLAM -> slam(p, WorthyKit.LIGHTNING_SHIELD_SLAM, WorthyConfig.SHIELD_SLAM_CD, WorthyState.SEQ_SHIELD_SLAM, axe);
			case WorthyKit.THUNDER_CLAP -> thunderClap(p, axe);
			case WorthyKit.SHIELD_THROW -> SuperSoldierAbilities.shieldThrow(p);
			default -> {
			}
		}
	}

	// ================================================================== Mjolnir only

	static void hammerCombo(ServerPlayer p) {
		LivingEntity t = facing(p, WorthyConfig.HAMMER_COMBO_RANGE);
		if (t == null || !begin(p, WorthyKit.HAMMER_COMBO, WorthyConfig.HAMMER_COMBO_CD, WorthyConfig.HAMMER_COMBO_INTERVAL * 2 + 6)) {
			return;
		}
		anim(p, WorthyState.ANIM_HAMMER_COMBO);
		startSeq(p, WorthyState.SEQ_HAMMER_COMBO, t, t.position(), ThorWeapon.MJOLNIR.ordinal());
		comboSwing(p, t, 0);
	}

	private static void comboSwing(ServerPlayer p, LivingEntity t, int step) {
		boolean finisher = step >= 2;
		if (t.distanceTo(p) > WorthyConfig.HAMMER_COMBO_RANGE + 1.5) {
			return;
		}
		hit(p, t, finisher ? WorthyConfig.HAMMER_COMBO_FINISHER : WorthyConfig.HAMMER_COMBO_HIT, p.position(), finisher ? 1.1 : 0.35,
				finisher ? 0.25 : 0.0);
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		WorthyFx.crackle(level, c, 4 + step * 4, 0.3, false);
		WorthyFx.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, finisher ? 0.75f : 1.0f);
		if (finisher) {
			radial(p, t.position(), WorthyConfig.HAMMER_COMBO_BURST_RADIUS, WorthyConfig.HAMMER_COMBO_BURST, 0.6, 0.2, t);
			WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.45f, 1.5f);
			WorthyFx.sound(level, c, SoundEvents.MACE_SMASH_GROUND, 0.8f, 1.0f);
		}
	}

	static void uppercut(ServerPlayer p, String id, int cd, float damage, boolean axe) {
		LivingEntity t = facing(p, WorthyConfig.UPPERCUT_RANGE);
		if (t == null || !begin(p, id, cd, 10)) {
			return;
		}
		anim(p, WorthyState.ANIM_UPPERCUT);
		hit(p, t, damage, p.position(), 0.25, WorthyConfig.UPPERCUT_LIFT);
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.4, 0);
		WorthyFx.crackle(level, c, 14, 0.35, axe);
		WorthyFx.arc(level, c, c.add(0, 2.2, 0), axe);
		WorthyFx.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.35f, 1.7f);
	}

	static void callLightning(ServerPlayer p) {
		LivingEntity t = facing(p, WorthyConfig.CALL_LIGHTNING_RANGE);
		Vec3 at;
		if (t != null) {
			at = t.position();
		} else {
			at = AbilityHelpers.aimPoint(p, WorthyConfig.CALL_LIGHTNING_RANGE);
		}
		if (!begin(p, WorthyKit.CALL_LIGHTNING, WorthyConfig.CALL_LIGHTNING_CD, 8)) {
			return;
		}
		anim(p, WorthyState.ANIM_CALL_LIGHTNING);
		ServerLevel level = p.serverLevel();
		WorthyFx.bolt(level, p, at);
		WorthyFx.crackle(level, at.add(0, 0.5, 0), 24, 0.6, false);
		if (t != null) {
			if (hit(p, t, WorthyConfig.CALL_LIGHTNING_DAMAGE, at, 0.2, 0.0) && !isBoss(t)) {
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WorthyConfig.CALL_LIGHTNING_STUN_TICKS, 3, false, true), p);
			}
		}
		radial(p, at, WorthyConfig.CALL_LIGHTNING_SPLASH_RADIUS, WorthyConfig.CALL_LIGHTNING_SPLASH, 0.4, 0.1, t);
	}

	static void thunderclap(ServerPlayer p) {
		if (!begin(p, WorthyKit.THUNDERCLAP, WorthyConfig.THUNDERCLAP_CD, 12)) {
			return;
		}
		anim(p, WorthyState.ANIM_THUNDERCLAP);
		ServerLevel level = p.serverLevel();
		Vec3 look = flat(p.getLookAngle());
		cone(p, WorthyConfig.THUNDERCLAP_RANGE, WorthyConfig.THUNDERCLAP_MIN_DOT, WorthyConfig.THUNDERCLAP_DAMAGE, WorthyConfig.THUNDERCLAP_KNOCKBACK,
				0.3, true);
		for (int i = 1; i <= 6; i++) {
			Vec3 at = p.position().add(look.scale(i * WorthyConfig.THUNDERCLAP_RANGE / 6.0));
			WorthyFx.crackle(level, at.add(0, 0.2, 0), 6, 0.4, false);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 3, 0.4, 0.05, 0.4, 0.05);
		}
		WorthyFx.sound(level, p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.9f);
		WorthyFx.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER, 0.5f, 1.3f);
		WorthyFx.shake(level, p.position(), 0.35f, 6, 10.0);
	}

	/** Z (all three kits): a hop, the slam on landing. Thunder Slam / Storm Crash / Lightning Shield Slam differ in numbers and look. */
	static void slam(ServerPlayer p, String id, int cd, int seq, boolean axe) {
		if (!begin(p, id, cd, 10)) {
			return;
		}
		anim(p, WorthyState.ANIM_SLAM_RISE);
		Vec3 look = flat(p.getLookAngle());
		launchSelf(p, new Vec3(look.x * 0.35, WorthyConfig.SLAM_HOP, look.z * 0.35));
		noFall(p);
		startSeq(p, seq, null, p.position(), axe ? 1 : 0);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
	}

	private static void slamImpact(ServerPlayer p, int seq, boolean axe) {
		anim(p, WorthyState.ANIM_SLAM_HIT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		double r;
		float dmg;
		double kb;
		if (seq == WorthyState.SEQ_CRASH) {
			r = WorthyConfig.CRASH_RADIUS;
			dmg = WorthyConfig.CRASH_DAMAGE;
			kb = WorthyConfig.CRASH_KNOCKBACK;
		} else if (seq == WorthyState.SEQ_SHIELD_SLAM) {
			r = WorthyConfig.SLAM_RADIUS;
			dmg = WorthyConfig.SHIELD_SLAM_DAMAGE + (axe ? WorthyConfig.AXE_BONUS : 0f);
			kb = WorthyConfig.SLAM_KNOCKBACK;
		} else {
			r = WorthyConfig.SLAM_RADIUS;
			dmg = WorthyConfig.SLAM_DAMAGE;
			kb = WorthyConfig.SLAM_KNOCKBACK;
		}
		radial(p, c, r, dmg, kb, WorthyConfig.SLAM_LIFT, null);
		WorthyFx.ring(level, c, r * 0.5, axe);
		WorthyFx.ring(level, c, r, axe);
		// ground lightning: two small bolts on the ring (the shield slam), the crash's one heavy bolt at the centre
		if (seq == WorthyState.SEQ_CRASH) {
			WorthyFx.bolt(level, p, c.add(flat(p.getLookAngle()).scale(1.5)));
		} else if (seq == WorthyState.SEQ_SHIELD_SLAM) {
			Vec3 side = flat(p.getLookAngle()).yRot((float) (Math.PI / 2));
			WorthyFx.bolt(level, p, c.add(side.scale(r * 0.7)));
			WorthyFx.bolt(level, p, c.subtract(side.scale(r * 0.7)));
		}
		WorthyFx.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, axe ? 0.75f : 0.9f);
		WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.6f, 1.2f);
		WorthyFx.shake(level, c, 0.45f, 8, 12.0);
	}

	static void worthyStorm(ServerPlayer p) {
		if (!begin(p, WorthyKit.WORTHY_STORM, WorthyConfig.ULTIMATE_CD, WorthyConfig.STORM_BURST_T + 4)) {
			return;
		}
		anim(p, WorthyState.ANIM_WORTHY_STORM);
		startSeq(p, WorthyState.SEQ_WORTHY_STORM, null, p.position(), 0);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
	}

	static void dash(ServerPlayer p, String id, boolean axe) {
		if (!begin(p, id, WorthyConfig.DASH_CD, 6)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		if (WorthyFlight.isFlying(p)) {
			long until = now(p) + WorthyConfig.FLIGHT_BOOST_TICKS;
			WorthyState.edit(p, n -> n.boostUntil = until);
			WorthyFx.sound(level, p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.8f, axe ? 0.8f : 1.1f);
			WorthyFx.crackle(level, p.position().add(0, 1, 0), 18, 0.5, axe);
			return;
		}
		anim(p, WorthyState.ANIM_DASH);
		Vec3 look = flat(p.getLookAngle());
		double speed = axe ? WorthyConfig.DASH_SPEED_STORMBREAKER : WorthyConfig.DASH_SPEED_MJOLNIR;
		launchSelf(p, new Vec3(look.x * speed, 0.18, look.z * speed));
		noFall(p);
		WorthyFx.arc(level, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(look.scale(speed * 4)), axe);
		WorthyFx.sound(level, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.7f, axe ? 0.9f : 1.2f);
	}

	static void launch(ServerPlayer p, String id) {
		if (!begin(p, id, WorthyConfig.LAUNCH_CD, WorthyConfig.LAUNCH_FLIGHT_T + 2)) {
			return;
		}
		anim(p, WorthyState.ANIM_LAUNCH);
		startSeq(p, WorthyState.SEQ_LAUNCH, null, p.position(), 0);
		noFall(p);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 0.9f, 1.1f);
	}

	static void guard(ServerPlayer p, String id, boolean shield) {
		int ticks = shield ? WorthyConfig.LIGHTNING_GUARD_TICKS : WorthyConfig.GUARD_TICKS;
		if (!begin(p, id, shield ? WorthyConfig.LIGHTNING_GUARD_CD : WorthyConfig.GUARD_CD, 6)) {
			return;
		}
		anim(p, shield ? WorthyState.ANIM_LIGHTNING_GUARD : WorthyState.ANIM_GUARD);
		long until = now(p) + ticks;
		WorthyState.edit(p, n -> {
			n.guardUntil = until;
			n.guardShield = shield;
		});
		WorthyFx.sound(p.serverLevel(), p.position(), shield ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
	}

	/** Called from the hurt hook while {@link WorthyState#guardUntil} runs: a hit from the front is blocked (and answered). */
	static boolean guardBlocks(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		WorthyState s = WorthyState.of(p);
		if (s.guardUntil <= now(p) || WorthyKit.kit(p) == Kit.NONE) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Vec3 to = flat(from.subtract(p.position()));
		if (to.dot(flat(p.getLookAngle())) < 0.0) {
			return false; // from behind: the guard covers the front
		}
		ServerLevel level = p.serverLevel();
		Entity direct = source.getDirectEntity();
		if (direct instanceof net.minecraft.world.entity.projectile.Projectile proj) {
			proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.8));
			proj.setOwner(p);
			proj.hurtMarked = true;
		} else if (source.getEntity() instanceof LivingEntity attacker && attacker.distanceTo(p) < 5.0) {
			hit(p, attacker, s.guardShield ? WorthyConfig.LIGHTNING_GUARD_SHOCK : WorthyConfig.GUARD_SHOCK, p.position(), 0.6, 0.15);
			WorthyFx.arc(level, p.position().add(0, 1.1, 0), attacker.position().add(0, attacker.getBbHeight() * 0.5, 0), false);
		}
		WorthyFx.crackle(level, p.position().add(0, 1.1, 0).add(flat(p.getLookAngle()).scale(0.7)), 8, 0.25, false);
		WorthyFx.sound(level, p.position(), s.guardShield ? SoundEvents.SHIELD_BLOCK : SoundEvents.ANVIL_PLACE, 0.8f, 1.4f);
		return true;
	}

	static void rally(ServerPlayer p, boolean axe) {
		if (!begin(p, WorthyKit.RALLY_BOLT, WorthyConfig.RALLY_CD, 10)) {
			return;
		}
		anim(p, WorthyState.ANIM_RALLY);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		WorthyFx.bolt(level, p, c);
		WorthyFx.crackle(level, c.add(0, 1.0, 0), 40, 0.6, axe);
		for (net.minecraft.world.entity.player.Player ally : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
				p.getBoundingBox().inflate(WorthyConfig.RALLY_RADIUS), a -> a == p || Squads.areAllies(p, a))) {
			ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, WorthyConfig.RALLY_TICKS, 0, false, true), p);
			ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, WorthyConfig.RALLY_TICKS, 0, false, true), p);
		}
		for (LivingEntity e : hostiles(p, c, WorthyConfig.RALLY_PUSH_RADIUS)) {
			if (!isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, WorthyConfig.RALLY_PUSH);
			}
		}
		WorthyFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.0f);
	}

	// ================================================================== the thrown weapon (all kits)

	static void weaponThrow(ServerPlayer p, String id, boolean axe, boolean dual) {
		InteractionHand hand = WorthyLending.lentHand(p);
		if (hand == null || p.getItemInHand(hand).isEmpty() || WorthyState.of(p).throwWeapon >= 0) {
			return;
		}
		double range = axe ? WorthyConfig.AXE_THROW_RANGE : WorthyConfig.HAMMER_THROW_RANGE;
		if (!begin(p, id, axe ? WorthyConfig.AXE_THROW_CD : WorthyConfig.HAMMER_THROW_CD, 8)) {
			return;
		}
		anim(p, WorthyState.ANIM_THROW);
		Vec3 to = aimEnd(p, range);
		int pierce = axe ? WorthyConfig.AXE_THROW_PIERCE : WorthyConfig.HAMMER_THROW_PIERCE;
		float dmg = axe ? WorthyConfig.AXE_THROW_DAMAGE : WorthyConfig.HAMMER_THROW_DAMAGE;
		startThrow(p, to, WorthyConfig.THROW_OUT_TICKS, 0, WorthyConfig.THROW_BACK_TICKS, pierce, dmg);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.TRIDENT_THROW.value(), 1.0f, axe ? 0.7f : 0.9f);
	}

	/** Where a throw along his aim stops: the first block, or {@code range} out; aimed at the crosshair's enemy when there is one. */
	static Vec3 aimEnd(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		LivingEntity ray = AbilityHelpers.raycastEntity(p, range);
		if (ray != null && canHit(p, ray)) {
			dir = ray.getBoundingBox().getCenter().subtract(eye).normalize();
		}
		Vec3 end = eye.add(dir.scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.BLOCK) {
			end = hit.getLocation().subtract(dir.scale(0.3));
		}
		return end;
	}

	/**
	 * The lent weapon leaves his hand ({@link ThorThrown}) for {@code out + hold + back} ticks: out to {@code to} hitting up to
	 * {@code pierce} enemies on the way, hanging there {@code hold} ticks, then home.
	 */
	static boolean startThrow(ServerPlayer p, Vec3 to, int out, int hold, int back, int pierce, float damage) {
		InteractionHand hand = WorthyLending.lentHand(p);
		ThorWeapon weapon = hand == null ? null : ThorWeapon.of(p.getItemInHand(hand));
		if (weapon == null) {
			return false;
		}
		InteractionHand left = ThorThrown.launch(p, weapon, out + hold + back);
		if (left == null) {
			return false;
		}
		Vec3 from = p.getEyePosition().add(0, -0.35, 0).add(flat(p.getLookAngle()).scale(0.4));
		long now = now(p);
		WorthyState.edit(p, n -> {
			n.throwWeapon = weapon.ordinal();
			n.throwStart = now;
			n.throwOut = Math.max(1, out);
			n.throwHold = Math.max(0, hold);
			n.throwBack = Math.max(1, back);
			n.throwOff = left == InteractionHand.OFF_HAND;
			n.fromX = from.x;
			n.fromY = from.y;
			n.fromZ = from.z;
			n.toX = to.x;
			n.toY = to.y;
			n.toZ = to.z;
			n.throwPierce = pierce;
			n.throwDamage = damage;
			n.throwHit.clear();
		});
		return true;
	}

	/** Stops drawing / hitting with the thrown weapon (it is back in his hand, or the loan ended). */
	public static void cancelThrow(ServerPlayer p) {
		if (WorthyState.of(p).throwWeapon >= 0) {
			WorthyState.edit(p, n -> {
				n.throwWeapon = -1;
				n.throwHit.clear();
			});
		}
	}

	private static void tickThrow(ServerPlayer p, WorthyState s) {
		if (s.throwWeapon < 0) {
			return;
		}
		long age = now(p) - s.throwStart;
		int total = s.throwOut + s.throwHold + s.throwBack;
		ThorWeapon weapon = ThorWeapon.values()[Math.max(0, Math.min(1, s.throwWeapon))];
		if (age > total + 2 || age < 0 || !ThorThrown.of(p).has(weapon)) {
			cancelThrow(p);
			return;
		}
		if (age < 1 || age > s.throwOut || s.throwPierce <= 0) {
			return;
		}
		Vec3 from = new Vec3(s.fromX, s.fromY, s.fromZ);
		Vec3 to = new Vec3(s.toX, s.toY, s.toZ);
		Vec3 a = from.lerp(to, (age - 1) / (double) s.throwOut);
		Vec3 b = from.lerp(to, age / (double) s.throwOut);
		boolean axe = weapon == ThorWeapon.STORMBREAKER;
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 2, 0.08, 0.08, 0.08, 0.02);
		List<Integer> newHits = new ArrayList<>();
		int pierce = s.throwPierce;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.0), e -> canHit(p, e))) {
			if (pierce <= 0 || s.throwHit.contains(e.getId())) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(0.45);
			if (!box.contains(b) && box.clip(a, b).isEmpty()) {
				continue;
			}
			if (hit(p, e, s.throwDamage, a, axe ? 0.5 : 0.8, 0.15)) {
				newHits.add(e.getId());
				pierce--;
				WorthyFx.crackle(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 10, 0.3, axe);
				WorthyFx.sound(level, e.position(), SoundEvents.TRIDENT_HIT, 0.9f, axe ? 0.7f : 1.0f);
				if (axe && !isBoss(e)) {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WorthyConfig.AXE_THROW_SLOW_TICKS, 1, false, true), p);
				}
			}
		}
		if (!newHits.isEmpty()) {
			int left = pierce;
			WorthyState.edit(p, n -> {
				n.throwHit.addAll(newHits);
				n.throwPierce = left;
			});
		}
	}

	// ================================================================== Stormbreaker only

	static void axeCleaves(ServerPlayer p) {
		if (!begin(p, WorthyKit.AXE_CLEAVES, WorthyConfig.AXE_CLEAVES_CD, WorthyConfig.AXE_CLEAVE_INTERVAL + 8)) {
			return;
		}
		anim(p, WorthyState.ANIM_AXE_CLEAVES);
		startSeq(p, WorthyState.SEQ_AXE_CLEAVES, null, p.position(), 1);
		cleave(p, WorthyConfig.AXE_CLEAVE_FIRST, false);
	}

	private static void cleave(ServerPlayer p, float damage, boolean second) {
		cone(p, WorthyConfig.AXE_CLEAVE_RANGE, WorthyConfig.AXE_CLEAVE_MIN_DOT, damage, second ? 1.1 : 0.5, second ? 0.2 : 0.0, false);
		ServerLevel level = p.serverLevel();
		Vec3 look = flat(p.getLookAngle());
		Vec3 c = p.position().add(0, 1.0, 0).add(look.scale(1.6));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.4, 0.1, 0.4, 0);
		WorthyFx.crackle(level, c, second ? 14 : 8, 0.6, true);
		WorthyFx.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, second ? 0.6f : 0.75f);
		if (second) {
			WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.4f, 1.4f);
		}
	}

	static void lightningCleave(ServerPlayer p) {
		if (!begin(p, WorthyKit.LIGHTNING_CLEAVE, WorthyConfig.LIGHTNING_CLEAVE_CD, 10)) {
			return;
		}
		anim(p, WorthyState.ANIM_LIGHTNING_CLEAVE);
		ServerLevel level = p.serverLevel();
		Vec3 look = flat(p.getLookAngle());
		Vec3 start = p.position();
		Vec3 end = start.add(look.scale(WorthyConfig.LIGHTNING_CLEAVE_LENGTH));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(WorthyConfig.LIGHTNING_CLEAVE_WIDTH + 1.0, 2.0,
				WorthyConfig.LIGHTNING_CLEAVE_WIDTH + 1.0), e -> canHit(p, e))) {
			Vec3 rel = e.position().subtract(start);
			double along = rel.dot(look);
			double side = flat(rel).lengthSqr() < 1.0e-6 ? 0.0 : Math.sqrt(Math.max(0.0, new Vec3(rel.x, 0, rel.z).lengthSqr() - along * along));
			if (along < 0.0 || along > WorthyConfig.LIGHTNING_CLEAVE_LENGTH || side > WorthyConfig.LIGHTNING_CLEAVE_WIDTH + e.getBbWidth() * 0.5) {
				continue;
			}
			hit(p, e, WorthyConfig.LIGHTNING_CLEAVE_DAMAGE, start, 0.5, 0.35);
		}
		for (int i = 1; i <= 8; i++) {
			Vec3 at = start.add(look.scale(i * WorthyConfig.LIGHTNING_CLEAVE_LENGTH / 8.0));
			WorthyFx.crackle(level, at.add(0, 0.3, 0), 8, 0.3, true);
		}
		WorthyFx.arc(level, start.add(0, 0.3, 0), end.add(0, 0.3, 0), true);
		WorthyFx.bolt(level, p, end);
		WorthyFx.sound(level, start, SoundEvents.MACE_SMASH_GROUND, 1.0f, 0.7f);
	}

	static void executionersChop(ServerPlayer p) {
		LivingEntity t = facing(p, WorthyConfig.EXEC_CHOP_RANGE);
		if (t == null || !begin(p, WorthyKit.EXECUTIONERS_CHOP, WorthyConfig.EXEC_CHOP_CD, 14)) {
			return;
		}
		anim(p, WorthyState.ANIM_EXEC_CHOP);
		Vec3 to = t.position().subtract(p.position());
		Vec3 dir = flat(to);
		double d = Math.sqrt(to.x * to.x + to.z * to.z);
		launchSelf(p, new Vec3(dir.x * Math.min(1.4, 0.25 + d * 0.13), 0.55, dir.z * Math.min(1.4, 0.25 + d * 0.13)));
		noFall(p);
		startSeq(p, WorthyState.SEQ_EXEC_CHOP, t, t.position(), 1);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
	}

	private static void chopLand(ServerPlayer p, @Nullable LivingEntity t) {
		anim(p, WorthyState.ANIM_SLAM_HIT);
		ServerLevel level = p.serverLevel();
		if (t != null && t.distanceTo(p) < 3.5) {
			float dmg = WorthyConfig.EXEC_CHOP_DAMAGE;
			if (t.getHealth() < t.getMaxHealth() * WorthyConfig.EXEC_CHOP_LOW_HEALTH) {
				dmg *= WorthyConfig.EXEC_CHOP_LOW_MULT;
			}
			hit(p, t, dmg, p.position(), 0.6, 0.0);
			WorthyFx.bolt(level, p, t.position());
		}
		WorthyFx.ring(level, p.position(), 2.0, true);
		WorthyFx.sound(level, p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
		WorthyFx.shake(level, p.position(), 0.3f, 6, 8.0);
	}

	static void verdict(ServerPlayer p) {
		LivingEntity t = facing(p, WorthyConfig.VERDICT_RANGE);
		if (t == null || !begin(p, WorthyKit.VERDICT, WorthyConfig.ULTIMATE_CD, WorthyConfig.VERDICT_THROW_T + 6)) {
			return;
		}
		anim(p, WorthyState.ANIM_VERDICT);
		startSeq(p, WorthyState.SEQ_VERDICT, t, t.position(), 1);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
	}

	// ================================================================== shield + weapon

	static void shieldAndWeapon(ServerPlayer p, boolean axe) {
		LivingEntity t = facing(p, WorthyConfig.SHIELD_STRIKE_RANGE);
		if (t == null || !begin(p, WorthyKit.SHIELD_AND_WEAPON, WorthyConfig.SHIELD_STRIKE_CD, WorthyConfig.SHIELD_STRIKE_GAP + 8)) {
			return;
		}
		anim(p, WorthyState.ANIM_SHIELD_STRIKE);
		startSeq(p, WorthyState.SEQ_SHIELD_STRIKE, t, t.position(), axe ? 1 : 0);
		if (hit(p, t, WorthyConfig.SHIELD_STRIKE_BASH, p.position(), 0.6, 0.0) && !isBoss(t)) {
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, true), p);
		}
		WorthyFx.sound(p.serverLevel(), t.position(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f);
	}

	static void bashLauncher(ServerPlayer p, boolean axe) {
		LivingEntity t = facing(p, WorthyConfig.SHIELD_STRIKE_RANGE);
		if (t == null || !begin(p, WorthyKit.BASH_LAUNCHER, WorthyConfig.BASH_LAUNCH_CD, WorthyConfig.BASH_LAUNCH_SPIKE_T + 4)) {
			return;
		}
		anim(p, WorthyState.ANIM_BASH_LAUNCH);
		startSeq(p, WorthyState.SEQ_BASH_LAUNCH, t, t.position(), axe ? 1 : 0);
		hit(p, t, WorthyConfig.BASH_LAUNCH_DAMAGE, p.position(), 0.2, WorthyConfig.UPPERCUT_LIFT);
		WorthyFx.sound(p.serverLevel(), t.position(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		WorthyFx.sound(p.serverLevel(), t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.9f);
	}

	static void chargedShieldThrow(ServerPlayer p) {
		if (!begin(p, WorthyKit.CHARGED_SHIELD_THROW, WorthyConfig.CHARGED_THROW_CD, 6)) {
			return;
		}
		// the busy lock is ours; the throw itself is the Super Soldier's own (aim, assist, bounces, the catch)
		if (!SuperSoldierAbilities.shieldThrow(p)) {
			refund(p, WorthyKit.CHARGED_SHIELD_THROW);
			return;
		}
		anim(p, WorthyState.ANIM_SHIELD_THROW);
		long until = now(p) + WorthyConfig.CHARGED_TICKS;
		WorthyState.edit(p, n -> n.chargedUntil = until);
		ServerLevel level = p.serverLevel();
		WorthyFx.crackle(level, p.getEyePosition().add(p.getLookAngle()), 20, 0.3, false);
		WorthyFx.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER, 0.5f, 1.6f);
	}

	/** {@code AFTER_DAMAGE}: a charged shield of {@code owner}'s hit {@code victim} -- lightning leaps to up to two more. */
	static void chain(ServerPlayer owner, LivingEntity victim) {
		if (WorthyState.of(owner).chargedUntil <= now(owner)) {
			return;
		}
		ServerLevel level = owner.serverLevel();
		Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		int n = 0;
		for (LivingEntity e : hostiles(owner, from, WorthyConfig.CHAIN_RADIUS)) {
			if (e == victim || n >= WorthyConfig.CHAIN_TARGETS) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (hit(owner, e, WorthyConfig.CHAIN_DAMAGE, from, 0.3, 0.1)) {
				WorthyFx.arc(level, from, to, false);
				n++;
			}
		}
		WorthyFx.crackle(level, from, 10, 0.3, false);
		WorthyFx.sound(level, from, SoundEvents.TRIDENT_THUNDER, 0.3f, 1.8f);
	}

	static void twinThrow(ServerPlayer p, boolean axe) {
		if (WorthyState.of(p).throwWeapon >= 0 || !begin(p, WorthyKit.TWIN_THROW, WorthyConfig.TWIN_THROW_CD, 8)) {
			return;
		}
		boolean shield = SuperSoldierAbilities.shieldThrow(p);
		Vec3 to = aimEnd(p, axe ? WorthyConfig.AXE_THROW_RANGE : WorthyConfig.HAMMER_THROW_RANGE);
		boolean weapon = startThrow(p, to, WorthyConfig.THROW_OUT_TICKS, 0, WorthyConfig.THROW_BACK_TICKS,
				axe ? WorthyConfig.AXE_THROW_PIERCE : WorthyConfig.HAMMER_THROW_PIERCE,
				(axe ? WorthyConfig.AXE_THROW_DAMAGE : WorthyConfig.HAMMER_THROW_DAMAGE));
		if (!shield && !weapon) {
			refund(p, WorthyKit.TWIN_THROW);
			return;
		}
		anim(p, WorthyState.ANIM_TWIN_THROW);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
	}

	static void thunderClap(ServerPlayer p, boolean axe) {
		if (!begin(p, WorthyKit.THUNDER_CLAP, WorthyConfig.ULTIMATE_CD, WorthyConfig.CLAP_T + 8)) {
			return;
		}
		anim(p, WorthyState.ANIM_CLAP);
		startSeq(p, WorthyState.SEQ_CLAP, null, p.position(), axe ? 1 : 0);
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f);
	}

	private static void clapImpact(ServerPlayer p, boolean axe) {
		ServerLevel level = p.serverLevel();
		Vec3 look = flat(p.getLookAngle());
		cone(p, WorthyConfig.CLAP_RANGE, WorthyConfig.CLAP_MIN_DOT, WorthyConfig.CLAP_DAMAGE + (axe ? WorthyConfig.AXE_BONUS : 0f),
				WorthyConfig.CLAP_KNOCKBACK, WorthyConfig.CLAP_LIFT, true);
		Vec3 c = p.position().add(0, 1.2, 0).add(look.scale(0.8));
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		for (int i = 1; i <= 10; i++) {
			double dist = i * WorthyConfig.CLAP_RANGE / 10.0;
			double half = dist * Math.tan(Math.acos(WorthyConfig.CLAP_MIN_DOT));
			Vec3 mid = p.position().add(look.scale(dist));
			Vec3 side = look.yRot((float) (Math.PI / 2));
			for (int k = -2; k <= 2; k++) {
				Vec3 at = mid.add(side.scale(half * k / 2.0));
				WorthyFx.crackle(level, at.add(0, 0.4, 0), 2, 0.2, axe);
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 1, 0.1, 0.1, 0.1, 0.05);
			}
		}
		for (int i = 1; i <= WorthyConfig.CLAP_BOLTS; i++) {
			WorthyFx.bolt(level, p, p.position().add(look.scale(i * WorthyConfig.CLAP_RANGE / (WorthyConfig.CLAP_BOLTS + 0.5))));
		}
		WorthyFx.sound(level, c, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
		WorthyFx.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
		WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 1.0f, 0.9f);
		WorthyFx.shake(level, c, 0.8f, 14, 18.0);
	}

	// ================================================================== per tick

	/** Every server tick for every player (cheap with no kit): melee, the timed move, the thrown weapon. */
	public static void tick(ServerPlayer p) {
		reconcileMelee(p);
		WorthyState s = WorthyState.of(p);
		if (s == WorthyState.EMPTY) {
			return;
		}
		tickThrow(p, s);
		if (s.seq != WorthyState.SEQ_NONE) {
			if (WorthyKit.kit(p) == Kit.NONE && s.seq != WorthyState.SEQ_VERDICT) {
				endSeq(p); // the weapon left his hands (called back, dropped): the move stops
			} else {
				tickSequence(p, WorthyState.of(p));
			}
		}
	}

	private static void tickSequence(ServerPlayer p, WorthyState s) {
		long t = now(p) - s.seqStart;
		ServerLevel level = p.serverLevel();
		boolean axe = s.seqWeapon == 1;
		LivingEntity target = seqTarget(p, s);
		switch (s.seq) {
			case WorthyState.SEQ_HAMMER_COMBO -> {
				if (target == null) {
					endSeq(p);
				} else if (t == WorthyConfig.HAMMER_COMBO_INTERVAL) {
					comboSwing(p, target, 1);
				} else if (t == WorthyConfig.HAMMER_COMBO_INTERVAL * 2L) {
					comboSwing(p, target, 2);
					endSeq(p);
				}
			}
			case WorthyState.SEQ_AXE_CLEAVES -> {
				if (t == WorthyConfig.AXE_CLEAVE_INTERVAL) {
					cleave(p, WorthyConfig.AXE_CLEAVE_SECOND, true);
					endSeq(p);
				}
			}
			case WorthyState.SEQ_SLAM, WorthyState.SEQ_CRASH, WorthyState.SEQ_SHIELD_SLAM -> {
				if (t >= 3 && (p.onGround() || p.isInWater() || t >= WorthyConfig.SLAM_MAX_TICKS)) {
					slamImpact(p, s.seq, axe);
					endSeq(p);
				} else if (t == 6 && !p.onGround()) {
					// the drive down
					Vec3 v = p.getDeltaMovement();
					launchSelf(p, new Vec3(v.x, -1.4, v.z));
				}
			}
			case WorthyState.SEQ_WORTHY_STORM -> tickWorthyStorm(p, t, level);
			case WorthyState.SEQ_EXEC_CHOP -> {
				boolean close = target != null && target.distanceTo(p) < 2.4;
				if (t >= 3 && (close || p.onGround() || t >= 25)) {
					chopLand(p, target);
					endSeq(p);
				}
			}
			case WorthyState.SEQ_VERDICT -> tickVerdict(p, t, target, level);
			case WorthyState.SEQ_SHIELD_STRIKE -> {
				if (t == WorthyConfig.SHIELD_STRIKE_GAP) {
					if (target != null && target.distanceTo(p) < WorthyConfig.SHIELD_STRIKE_RANGE + 1.5) {
						hit(p, target, WorthyConfig.SHIELD_STRIKE_WEAPON + (axe ? WorthyConfig.AXE_BONUS : 0f), p.position(), 1.1, 0.25);
						Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
						WorthyFx.crackle(level, c, 14, 0.35, axe);
						WorthyFx.sound(level, c, SoundEvents.MACE_SMASH_GROUND, 0.9f, axe ? 0.8f : 1.0f);
						WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.35f, 1.6f);
					}
					endSeq(p);
				}
			}
			case WorthyState.SEQ_BASH_LAUNCH -> {
				if (t == WorthyConfig.BASH_LAUNCH_SPIKE_T) {
					if (target != null && target.distanceTo(p) < 7.0) {
						hit(p, target, WorthyConfig.BASH_LAUNCH_SPIKE + (axe ? WorthyConfig.AXE_BONUS : 0f), p.position(), 0.0, -0.9);
						Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
						WorthyFx.arc(level, p.getEyePosition(), c, axe);
						WorthyFx.crackle(level, c, 16, 0.35, axe);
						WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 0.5f, 1.4f);
					}
					endSeq(p);
				}
			}
			case WorthyState.SEQ_CLAP -> {
				if (t == WorthyConfig.CLAP_T) {
					clapImpact(p, axe);
					endSeq(p);
				} else if (t < WorthyConfig.CLAP_T && t % 3 == 0) {
					WorthyFx.crackle(level, p.position().add(0, 1.3, 0), 6, 0.4, axe);
				}
			}
			case WorthyState.SEQ_LAUNCH -> {
				if (t < 4 && t % 2 == 0) {
					WorthyFx.crackle(level, p.position().add(0, 2.2, 0), 6, 0.3, s.seqWeapon == 1);
				}
				if (t == 4) {
					Vec3 look = flat(p.getLookAngle());
					launchSelf(p, new Vec3(look.x * WorthyConfig.LAUNCH_FORWARD, WorthyConfig.LAUNCH_UP, look.z * WorthyConfig.LAUNCH_FORWARD));
					WorthyFx.sound(level, p.position(), SoundEvents.BREEZE_JUMP, 1.0f, 1.0f);
				} else if (t >= WorthyConfig.LAUNCH_FLIGHT_T) {
					if (!p.onGround() && !WorthyFlight.isFlying(p)) {
						WorthyFlight.start(p, false);
					}
					endSeq(p);
				}
			}
			default -> endSeq(p);
		}
	}

	private static void tickWorthyStorm(ServerPlayer p, long t, ServerLevel level) {
		Vec3 c = p.position();
		if (t < WorthyConfig.STORM_CHARGE_T && t % 2 == 0) {
			WorthyFx.crackle(level, c.add(0, 2.4, 0), 6, 0.3, false);
		}
		if (t == WorthyConfig.STORM_CHARGE_T) {
			WorthyFx.bolt(level, p, c); // the bolt into the raised hammer
			WorthyFx.crackle(level, c.add(0, 1.2, 0), 40, 0.6, false);
		}
		long since = t - WorthyConfig.STORM_FIRST_BOLT_T;
		if (since >= 0 && since % WorthyConfig.STORM_BOLT_EVERY == 0 && since / WorthyConfig.STORM_BOLT_EVERY < WorthyConfig.STORM_BOLTS) {
			List<LivingEntity> foes = hostiles(p, c, WorthyConfig.STORM_RADIUS);
			if (!foes.isEmpty()) {
				LivingEntity e = foes.get((int) (since / WorthyConfig.STORM_BOLT_EVERY) % foes.size());
				WorthyFx.bolt(level, p, e.position());
				hit(p, e, WorthyConfig.STORM_BOLT_DAMAGE, e.position(), 0.3, 0.1);
				WorthyFx.arc(level, c.add(0, 2.3, 0), e.position().add(0, e.getBbHeight() * 0.5, 0), false);
			}
		}
		if (t == WorthyConfig.STORM_BURST_T) {
			radial(p, c, WorthyConfig.STORM_BURST_RADIUS, WorthyConfig.STORM_BURST_DAMAGE, 1.4, 0.45, null);
			WorthyFx.ring(level, c, WorthyConfig.STORM_BURST_RADIUS * 0.5, false);
			WorthyFx.ring(level, c, WorthyConfig.STORM_BURST_RADIUS, false);
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
			WorthyFx.sound(level, c, SoundEvents.TRIDENT_THUNDER, 1.0f, 0.8f);
			WorthyFx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.2f);
			WorthyFx.shake(level, c, 0.7f, 12, 16.0);
			endSeq(p);
		}
	}

	private static void tickVerdict(ServerPlayer p, long t, @Nullable LivingEntity target, ServerLevel level) {
		Vec3 at = target != null ? target.position() : new Vec3(WorthyState.of(p).seqX, WorthyState.of(p).seqY, WorthyState.of(p).seqZ);
		if (t == WorthyConfig.VERDICT_THROW_T) {
			Vec3 to = target != null ? target.getBoundingBox().getCenter() : at.add(0, 1, 0);
			int out = WorthyConfig.VERDICT_STRIKE_T - WorthyConfig.VERDICT_THROW_T;
			int back = 8;
			int hold = Math.max(0, WorthyConfig.VERDICT_HOME_T - WorthyConfig.VERDICT_STRIKE_T - back);
			if (!startThrow(p, to, out, hold, back, 0, 0f)) {
				endSeq(p);
				return;
			}
			WorthyFx.sound(level, p.position(), SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.6f);
		} else if (t == WorthyConfig.VERDICT_STRIKE_T) {
			WorthyFx.bolt(level, p, at);
			if (target != null) {
				if (hit(p, target, WorthyConfig.VERDICT_DAMAGE, p.position(), 0.0, 0.0) && !isBoss(target)) {
					target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 5, false, true), p);
				}
			}
			WorthyFx.crackle(level, at.add(0, 1, 0), 30, 0.5, true);
			WorthyFx.sound(level, at, SoundEvents.TRIDENT_HIT, 1.0f, 0.6f);
		} else if (t == WorthyConfig.VERDICT_BURST_T) {
			radial(p, at, WorthyConfig.VERDICT_BURST_RADIUS, WorthyConfig.VERDICT_BURST_DAMAGE, 1.2, 0.4, target);
			for (int i = 0; i < 3; i++) {
				double a = i * Math.PI * 2.0 / 3.0;
				WorthyFx.bolt(level, p, at.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5));
			}
			WorthyFx.ring(level, at, WorthyConfig.VERDICT_BURST_RADIUS, true);
			WorthyFx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.1f);
			WorthyFx.shake(level, at, 0.6f, 10, 14.0);
		} else if (t >= WorthyConfig.VERDICT_HOME_T) {
			endSeq(p);
		}
	}

	// ================================================================== melee with the lent weapon

	/** Left click with a lent weapon in the main hand hits for exactly {@link WorthyConfig#MJOLNIR_MELEE} / {@code STORMBREAKER_MELEE}. */
	static void reconcileMelee(ServerPlayer p) {
		AttributeInstance attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack == null) {
			return;
		}
		ItemStack main = p.getMainHandItem();
		boolean lent = WorthyLending.isLent(p, main) && com.projecthero.mod.supersoldier.SuperSoldier.hasPower(p);
		AttributeModifier cur = attack.getModifier(MELEE_ID);
		if (!lent) {
			if (cur != null) {
				attack.removeModifier(MELEE_ID);
			}
			return;
		}
		boolean axe = ThorWeapon.of(main) == ThorWeapon.STORMBREAKER;
		double item = axe ? com.projecthero.mod.stormbreaker.StormbreakerItem.ATTACK_DAMAGE_BONUS : WorthyConfig.MJOLNIR_ITEM_BONUS;
		double target = axe ? WorthyConfig.STORMBREAKER_MELEE : WorthyConfig.MJOLNIR_MELEE;
		double delta = target - (attack.getBaseValue() + item + SuperSoldierConfig.ARMED_BONUS);
		if (cur == null || cur.amount() != delta) {
			attack.addOrUpdateTransientModifier(new AttributeModifier(MELEE_ID, delta, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
