package com.projecthero.mod.quirk.zerogravity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp ({@code docs/QUIRK_REVAMP_V0167.md}): Zero Gravity's twelve moves, the Nausea meter and the per-player
 * sessions of the moves that run over time. Every public move is called by {@link ZeroGravityKit#handle} once its cooldown is
 * ready, and by the gametests directly. User rule: X is the movement key, V the utility key, the rest attacks.
 *
 * <pre>
 *   R  Meteor Shower (R again rains it)   Shift+R  Debris Shield
 *   G  Comet Toss                          Shift+G  Zero Gravity Slam
 *   X  Float Self (toggle)                 Shift+X  Zero-G Leap
 *   Z  Gunhead Martial Arts                Shift+Z  Zero Gravity Field
 *   V  Zero Gravity Touch                  Shift+V  Release
 *   C  Catch                               Shift+C  Settle (works while sick)
 * </pre>
 * What floats is {@link ZeroGravityFloat} (creatures, players, items); blocks are {@link ZeroGravityDebris}.
 */
public final class ZeroGravityMoves {
	static final ResourceLocation LEAP_GRAVITY = ResourceLocation.fromNamespaceAndPath("projecthero", "zero_gravity_leap");

	/** An entity on its way somewhere (the Slam's lift / fall, a Comet Toss). */
	record Watched(int id, long from) {
	}

	static final class Session {
		final List<Watched> lifts = new ArrayList<>();
		final List<Watched> slams = new ArrayList<>();
		final List<Watched> comets = new ArrayList<>();
		int gunTarget = -1;
		int gunStage;
		long gunNext;
		long leapFrom;
		boolean leaping;
		long fieldUntil;
		Vec3 fieldCenter = Vec3.ZERO;
		long settleUntil;
		int streak;
		boolean warned;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ZeroGravityMoves() {
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

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static Vec3 hand(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.35, 0);
	}

	static void syncCount(ServerPlayer p) {
		long n = ZeroGravityFloat.count(p);
		if (Quirks.state(p).val(ZeroGravity.V_FLOATING) != n) {
			Quirks.setVal(p, ZeroGravity.V_FLOATING, n);
		}
		long b = ZeroGravityDebris.held(p);
		if (Quirks.state(p).val(ZeroGravity.V_BLOCKS) != b) {
			Quirks.setVal(p, ZeroGravity.V_BLOCKS, b);
		}
	}

	private static void sting(ServerPlayer p, float scale) {
		Quirks.addMeter(p, ZeroGravityConfig.nausea().perFloat * scale);
	}

	/** Any move but Settle ends a Settle in progress. */
	private static void interruptSettle(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.settleUntil != 0L) {
			s.settleUntil = 0L;
			Quirks.setVal(p, ZeroGravity.V_SETTLE, 0L);
		}
	}

	/** What a touch may reach: a creature she can float (or one she floats), or (to say "immune") a boss. */
	private static @Nullable LivingEntity touchCreature(ServerPlayer p, double reach) {
		return ZeroGravityCombat.aimed(p, reach, ZeroGravityConfig.TOUCH_ASSIST_DEGREES,
				e -> ZeroGravityFloat.canFloat(p, e, false) || ZeroGravityFloat.floatedBy(p, e)
						|| (ZeroGravityCombat.isBoss(e) && ZeroGravityCombat.isTarget(p, e)));
	}

	/** The dropped item nearest her crosshair within reach (and the assist cone), or null. */
	private static @Nullable ItemEntity touchItem(ServerPlayer p, double reach) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(ZeroGravityConfig.TOUCH_ASSIST_DEGREES));
		ItemEntity best = null;
		for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(reach + 1.0), ItemEntity::isAlive)) {
			Vec3 to = it.position().add(0, 0.2, 0).subtract(eye);
			double d = to.length();
			if (d > reach + 0.5 || d < 0.1) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = it;
			}
		}
		return best;
	}

	/** Floats a creature by touch (the cap, the sparkle, the boop, the sting of Nausea). */
	private static boolean touchFloat(ServerPlayer p, LivingEntity e) {
		if (ZeroGravityFloat.floatedBy(p, e)) {
			return true;
		}
		if (ZeroGravityCombat.isBoss(e)) {
			Quirks.say(p, "message.projecthero.zero_gravity.boss_immune", ChatFormatting.LIGHT_PURPLE);
			return false;
		}
		if (ZeroGravityFloat.creatures(p) >= ZeroGravityConfig.TOUCH_MAX) {
			Quirks.say(p, "message.projecthero.zero_gravity.max_floats", ChatFormatting.LIGHT_PURPLE, ZeroGravityConfig.TOUCH_MAX);
			return false;
		}
		if (!ZeroGravityFloat.start(p, e, false)) {
			return false;
		}
		sting(p, 1f);
		ZeroGravityCombat.touchFx(p.serverLevel(), ZeroGravityCombat.center(e));
		syncCount(p);
		return true;
	}

	// ================================================================ V: Zero Gravity Touch / Shift+V: Release

	/**
	 * Her five fingertips: the creature, item or block she touches floats. Creatures first (2 damage to anything she may
	 * harm; a squadmate is just lifted), then a dropped item, then the block she looks at. Returns what floats (an
	 * {@link Entity} or a {@link BlockPos}), or null.
	 */
	public static @Nullable Object touch(ServerPlayer p) {
		interruptSettle(p);
		Quirks.setAnim(p, ZeroGravity.ANIM_TOUCH);
		p.swing(InteractionHand.MAIN_HAND, true);
		Quirks.setCooldown(p, ZeroGravityConfig.TOUCH, ZeroGravityConfig.TOUCH_COOLDOWN);
		LivingEntity e = touchCreature(p, ZeroGravityConfig.TOUCH_REACH);
		if (e != null) {
			if (!isSquadmate(p, e)) {
				ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.TOUCH_DAMAGE, 0.0, 0.0);
			}
			return touchFloat(p, e) ? e : null;
		}
		ItemEntity item = touchItem(p, ZeroGravityConfig.TOUCH_REACH);
		if (item != null && ZeroGravityFloat.start(p, item, false)) {
			sting(p, 0.3f);
			ZeroGravityCombat.touchFx(p.serverLevel(), item.position().add(0, 0.25, 0));
			syncCount(p);
			return item;
		}
		Vec3 eye = p.getEyePosition();
		BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(ZeroGravityConfig.TOUCH_REACH + 0.5)),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (bh.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = bh.getBlockPos();
			if (ZeroGravityDebris.holdBlock(p, pos)) {
				sting(p, 0.5f);
				ZeroGravityCombat.touchFx(p.serverLevel(), Vec3.atCenterOf(pos));
				syncCount(p);
				return pos.immutable();
			}
			Quirks.say(p, "message.projecthero.zero_gravity.cant_lift", ChatFormatting.LIGHT_PURPLE);
		}
		return null;
	}

	/** True if {@code e} is her squadmate (not hurt by the touch). */
	private static boolean isSquadmate(ServerPlayer p, LivingEntity e) {
		return com.projecthero.mod.squad.Squads.areAllies(p, e);
	}

	/**
	 * "Release!" -- fingertips together: every creature and item she floats drops (fall damage from the height), every touched
	 * block falls and smashes, and the Meteor Shower cloud rains. Returns how many things came down.
	 */
	public static int release(ServerPlayer p) {
		interruptSettle(p);
		List<Entity> out = ZeroGravityFloat.releaseAll(p, false);
		int rained = ZeroGravityDebris.rain(p);
		int dropped = ZeroGravityDebris.dropHeld(p);
		session(p).lifts.clear();
		syncCount(p);
		if (rained > 0) {
			Quirks.setVal(p, ZeroGravity.V_CLOUD, 0L);
		}
		int total = out.size() + rained + dropped;
		if (total == 0) {
			Quirks.say(p, "message.projecthero.zero_gravity.nothing_floating", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		Quirks.setAnim(p, rained > 0 ? ZeroGravity.ANIM_RAIN : ZeroGravity.ANIM_RELEASE);
		ServerLevel level = p.serverLevel();
		for (Entity e : out) {
			ZeroGravityCombat.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 6, 0.3);
		}
		ZeroGravityCombat.boop(level, hand(p), 0.7f);
		if (rained > 0) {
			sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.6f);
		}
		Quirks.setCooldown(p, ZeroGravityConfig.RELEASE, ZeroGravityConfig.RELEASE_COOLDOWN);
		return total;
	}

	// ================================================================ R: Meteor Shower / Shift+R: Debris Shield

	/**
	 * The loose ground round her rips up into a rubble cloud high overhead that drifts after her aim; Release (or R again)
	 * rains it. Returns the pieces lifted (or, raining, the pieces that fell).
	 */
	public static int meteorShower(ServerPlayer p, @Nullable List<BlockPos> taken) {
		interruptSettle(p);
		if (ZeroGravityDebris.cloudUp(p)) {
			int n = ZeroGravityDebris.rain(p);
			Quirks.setVal(p, ZeroGravity.V_CLOUD, 0L);
			Quirks.setAnim(p, ZeroGravity.ANIM_RAIN);
			ZeroGravityCombat.boop(p.serverLevel(), hand(p), 0.6f);
			sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.6f);
			syncCount(p);
			return n;
		}
		int n = ZeroGravityDebris.liftCloud(p, taken);
		if (n == 0) {
			Quirks.say(p, "message.projecthero.zero_gravity.no_rubble", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		Quirks.setVal(p, ZeroGravity.V_CLOUD, now(p) + ZeroGravityConfig.CLOUD_MAX_TICKS);
		Quirks.setAnim(p, ZeroGravity.ANIM_SHOWER);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 20, 3.0, 0.1, 3.0, 0.02);
		ZeroGravityCombat.boop(level, p.position(), 0.8f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 0.6f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
		sting(p, 1f);
		Quirks.setCooldown(p, ZeroGravityConfig.SHOWER, ZeroGravityConfig.SHOWER_COOLDOWN);
		syncCount(p);
		return n;
	}

	/** Rubble orbits her for {@link ZeroGravityConfig#SHIELD_TICKS} and stops every projectile that comes near. */
	public static int debrisShield(ServerPlayer p, @Nullable List<BlockPos> taken) {
		interruptSettle(p);
		int n = ZeroGravityDebris.startShield(p, taken);
		Quirks.setVal(p, ZeroGravity.V_SHIELD, now(p) + ZeroGravityConfig.SHIELD_TICKS);
		Quirks.setAnim(p, ZeroGravity.ANIM_SHIELD);
		ZeroGravityCombat.boop(p.serverLevel(), p.position(), 0.9f);
		Quirks.setCooldown(p, ZeroGravityConfig.SHIELD, ZeroGravityConfig.SHIELD_COOLDOWN);
		syncCount(p);
		return n;
	}

	// ================================================================ G: Comet Toss / Shift+G: Zero Gravity Slam

	/** The floated creature / item nearest her crosshair (within the pick cone) and its closeness (dot), or null. */
	private static @Nullable Entity bestFloated(ServerPlayer p, double[] dotOut) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(ZeroGravityConfig.COMET_PICK_DEGREES));
		Entity best = null;
		for (Entity e : ZeroGravityFloat.floating(p)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > ZeroGravityConfig.COMET_PICK_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = e;
			}
		}
		dotOut[0] = best == null ? -2.0 : bestDot;
		return best;
	}

	/**
	 * Throws the floated thing nearest her crosshair -- a creature, an item or a block -- hard at what she aims at; nothing
	 * floated in the cone: whatever she touches is floated and thrown at once. Returns what flew (an Entity, "block"), or null.
	 */
	public static @Nullable Object cometToss(ServerPlayer p) {
		interruptSettle(p);
		double[] dot = new double[1];
		Entity e = bestFloated(p, dot);
		double blockDot = ZeroGravityDebris.bestDot(p);
		if (blockDot > dot[0] && ZeroGravityDebris.throwBest(p)) {
			tossed(p);
			return "block";
		}
		if (e == null) {
			LivingEntity c = touchCreature(p, ZeroGravityConfig.TOUCH_REACH);
			if (c != null && touchFloat(p, c)) {
				e = c;
			} else {
				Object t = touch(p);
				if (t instanceof Entity te) {
					e = te;
				} else if (t instanceof BlockPos && ZeroGravityDebris.throwBest(p)) {
					tossed(p);
					return "block";
				}
			}
		}
		if (e == null) {
			Quirks.say(p, "message.projecthero.zero_gravity.nothing_to_throw", ChatFormatting.LIGHT_PURPLE);
			return null;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, 48.0);
		Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		e.setDeltaMovement(dir.scale(ZeroGravityConfig.COMET_SPEED).add(0, 0.05, 0));
		e.hurtMarked = true;
		ZeroGravityFloat.markFlung(p, e, ZeroGravityConfig.COMET_WATCH_TICKS);
		if (e instanceof LivingEntity) {
			session(p).comets.add(new Watched(e.getId(), now(p)));
		}
		tossed(p);
		return e;
	}

	private static void tossed(ServerPlayer p) {
		Quirks.setAnim(p, ZeroGravity.ANIM_COMET);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 0.8f);
		Quirks.setCooldown(p, ZeroGravityConfig.COMET, ZeroGravityConfig.COMET_COOLDOWN);
		syncCount(p);
	}

	private static void tickComets(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		for (Iterator<Watched> it = s.comets.iterator(); it.hasNext();) {
			Watched w = it.next();
			if (!(level.getEntity(w.id()) instanceof LivingEntity e) || !e.isAlive() || now - w.from() > ZeroGravityConfig.COMET_WATCH_TICKS) {
				it.remove();
				continue;
			}
			if (now - w.from() < 1) {
				continue;
			}
			LivingEntity into = null;
			for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.4), o -> o != e && o != p
					&& ZeroGravityCombat.isTarget(p, o))) {
				into = o;
				break;
			}
			boolean smashed = e.horizontalCollision || e.verticalCollision || into != null;
			if (!smashed) {
				if ((now - w.from()) % 2 == 0) {
					ZeroGravityCombat.trail(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
				}
				continue;
			}
			it.remove();
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			ZeroGravityCombat.strike(p, e, c, ZeroGravityConfig.COMET_DAMAGE, 0.0, 0.0);
			for (LivingEntity o : AbilityHelpers.enemiesAround(p, c, ZeroGravityConfig.COMET_SPLASH)) {
				if (o != e && ZeroGravityCombat.isTarget(p, o)) {
					ZeroGravityCombat.strike(p, o, c, ZeroGravityConfig.COMET_SPLASH_DAMAGE, 0.6, 0.2);
				}
			}
			// it lands: gravity back
			ZeroGravityFloat.release(p, e);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			ZeroGravityCombat.burst(level, c, 10, 0.5);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 1.0f);
			syncCount(p);
		}
	}

	/** A touch, and the creature shoots up {@link ZeroGravityConfig#SLAM_HEIGHT} blocks -- then she lets go and it slams down. */
	public static @Nullable LivingEntity slam(ServerPlayer p) {
		interruptSettle(p);
		Quirks.setAnim(p, ZeroGravity.ANIM_SLAM);
		LivingEntity e = ZeroGravityCombat.aimed(p, ZeroGravityConfig.SLAM_REACH, ZeroGravityConfig.TOUCH_ASSIST_DEGREES,
				x -> ZeroGravityCombat.isTarget(p, x) && (ZeroGravityFloat.canFloat(p, x, true) || ZeroGravityFloat.floatedBy(p, x)
						|| ZeroGravityCombat.isBoss(x)));
		if (e == null) {
			return null;
		}
		if (ZeroGravityCombat.isBoss(e)) {
			Quirks.say(p, "message.projecthero.zero_gravity.boss_immune", ChatFormatting.LIGHT_PURPLE);
			return null;
		}
		if (!ZeroGravityFloat.floatedBy(p, e) && !ZeroGravityFloat.start(p, e, true, 0.0)) {
			return null;
		}
		ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.TOUCH_DAMAGE, 0.0, 0.0);
		ZeroGravityFloat.liftTo(p, e, ZeroGravityConfig.SLAM_HEIGHT, ZeroGravityConfig.SLAM_RISE_SPEED);
		session(p).lifts.add(new Watched(e.getId(), now(p)));
		ZeroGravityCombat.touchFx(p.serverLevel(), ZeroGravityCombat.center(e));
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.3f);
		sting(p, 1f);
		Quirks.setCooldown(p, ZeroGravityConfig.SLAM, ZeroGravityConfig.SLAM_COOLDOWN);
		syncCount(p);
		return e;
	}

	/** Test hook: the lift is done now (let go and slam). */
	public static void slamDropForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			for (Watched w : List.copyOf(s.lifts)) {
				dropSlam(p, s, w, now(p));
			}
			s.lifts.clear();
		}
	}

	private static void dropSlam(ServerPlayer p, Session s, Watched w, long now) {
		if (!(p.serverLevel().getEntity(w.id()) instanceof LivingEntity e) || !e.isAlive()) {
			return;
		}
		ZeroGravityFloat.release(p, e);
		Vec3 v = e.getDeltaMovement();
		e.setDeltaMovement(v.x * 0.2, -ZeroGravityConfig.SLAM_SPEED, v.z * 0.2);
		e.hurtMarked = true;
		s.slams.add(new Watched(e.getId(), now));
		p.serverLevel().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 0.6f);
		syncCount(p);
	}

	private static void tickLifts(ServerPlayer p, Session s, long now) {
		for (Iterator<Watched> it = s.lifts.iterator(); it.hasNext();) {
			Watched w = it.next();
			if (now - w.from() >= ZeroGravityConfig.SLAM_LIFT_TICKS) {
				it.remove();
				dropSlam(p, s, w, now);
			}
		}
	}

	private static void tickSlams(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		for (Iterator<Watched> it = s.slams.iterator(); it.hasNext();) {
			Watched sl = it.next();
			if (!(level.getEntity(sl.id()) instanceof LivingEntity e) || !e.isAlive() || now - sl.from() > ZeroGravityConfig.SLAM_WATCH_TICKS) {
				it.remove();
				continue;
			}
			long age = now - sl.from();
			if (age < 1) {
				continue;
			}
			boolean down = e.onGround() || e.verticalCollisionBelow || (age > 2 && e.getDeltaMovement().y > -0.05);
			if (!down) {
				if (age % 2 == 0) {
					ZeroGravityCombat.trail(level, e.position().add(0, e.getBbHeight(), 0));
				}
				continue;
			}
			it.remove();
			Vec3 c = e.position();
			e.invulnerableTime = 0;
			ZeroGravityCombat.strike(p, e, c, ZeroGravityConfig.SLAM_DAMAGE, 0.0, 0.0);
			for (LivingEntity o : AbilityHelpers.enemiesAround(p, c, ZeroGravityConfig.SLAM_SPLASH_RADIUS)) {
				if (o != e && ZeroGravityCombat.isTarget(p, o)) {
					ZeroGravityCombat.strike(p, o, c, ZeroGravityConfig.SLAM_SPLASH_DAMAGE, 0.6, 0.2);
				}
			}
			BlockPos under = BlockPos.containing(c).below();
			level.levelEvent(2001, under, Block.getId(level.getBlockState(under)));
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			ZeroGravityCombat.burst(level, c.add(0, 0.3, 0), 10, 0.7);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.9f);
		}
	}

	// ================================================================ X: Float Self / Shift+X: Zero-G Leap

	/** Toggle: she floats and glides (Jump rises, Sneak sinks, no input: a slow glide down -- the client moves her). */
	public static boolean floatSelf(ServerPlayer p) {
		interruptSettle(p);
		Quirks.setCooldown(p, ZeroGravityConfig.SELF, ZeroGravityConfig.SELF_COOLDOWN);
		if (ZeroGravityFloat.selfFloating(p)) {
			stopSelf(p);
			ZeroGravityCombat.boop(p.serverLevel(), p.position(), 0.7f);
			return false;
		}
		endLeap(p);
		ZeroGravityFloat.startSelf(p);
		Quirks.setVal(p, ZeroGravity.V_SELF, 1L);
		Quirks.setAnim(p, ZeroGravity.ANIM_SELF);
		ZeroGravityCombat.touchFx(p.serverLevel(), p.position().add(0, 1.0, 0));
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.22, 0));
		return true;
	}

	static void stopSelf(ServerPlayer p) {
		ZeroGravityFloat.stopSelf(p);
		if (Quirks.state(p).val(ZeroGravity.V_SELF) != 0L) {
			Quirks.setVal(p, ZeroGravity.V_SELF, 0L);
		}
	}

	/** A huge weightless leap: her gravity nearly gone until she lands (no fall damage on the way down). */
	public static void leap(ServerPlayer p) {
		interruptSettle(p);
		stopSelf(p);
		Vec3 v = flatLook(p).scale(ZeroGravityConfig.LEAP_FORWARD).add(0, ZeroGravityConfig.LEAP_UP, 0);
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
			g.addTransientModifier(new AttributeModifier(LEAP_GRAVITY, -ZeroGravityConfig.LEAP_GRAVITY_CUT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AbilityHelpers.launchSelf(p, v);
		Session s = session(p);
		s.leaping = true;
		s.leapFrom = now(p);
		Quirks.setVal(p, ZeroGravity.V_LEAP, now(p) + ZeroGravityConfig.LEAP_MAX_TICKS);
		Quirks.setAnim(p, ZeroGravity.ANIM_LEAP);
		ZeroGravityCombat.touchFx(p.serverLevel(), p.position());
		sound(p, SoundEvents.PHANTOM_FLAP, 0.6f, 1.5f);
		Quirks.setCooldown(p, ZeroGravityConfig.LEAP, ZeroGravityConfig.LEAP_COOLDOWN);
	}

	static void endLeap(ServerPlayer p) {
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
		}
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.leaping = false;
		}
		if (Quirks.state(p).val(ZeroGravity.V_LEAP) != 0L) {
			Quirks.setVal(p, ZeroGravity.V_LEAP, 0L);
		}
	}

	public static boolean leaping(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.leaping;
	}

	private static void tickLeap(ServerPlayer p, Session s, long now) {
		p.resetFallDistance();
		long age = now - s.leapFrom;
		if (age % 2 == 0) {
			ZeroGravityCombat.trail(p.serverLevel(), p.position());
		}
		if ((age > 5 && (p.onGround() || p.isInWater())) || age > ZeroGravityConfig.LEAP_MAX_TICKS) {
			endLeap(p);
		}
	}

	// ================================================================ Z: Gunhead Martial Arts / Shift+Z: Field

	/** Gunhead martial arts: a jab, a palm strike, then she grabs it and throws it over her shoulder. Returns the target. */
	public static @Nullable LivingEntity gunhead(ServerPlayer p) {
		interruptSettle(p);
		LivingEntity e = ZeroGravityCombat.aimed(p, ZeroGravityConfig.GUNHEAD_REACH, 25.0, x -> ZeroGravityCombat.isTarget(p, x));
		Quirks.setAnim(p, ZeroGravity.ANIM_GUNHEAD);
		Quirks.setVal(p, ZeroGravity.V_GUNHEAD, now(p) + 2L * ZeroGravityConfig.GUNHEAD_GAP + 8L);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
		if (e == null) {
			Quirks.setCooldown(p, ZeroGravityConfig.GUNHEAD, 20);
			return null;
		}
		Session s = session(p);
		s.gunTarget = e.getId();
		s.gunStage = 0;
		s.gunNext = now(p);
		gunheadHit(p, s);
		Quirks.setCooldown(p, ZeroGravityConfig.GUNHEAD, ZeroGravityConfig.GUNHEAD_COOLDOWN);
		return e;
	}

	private static void gunheadHit(ServerPlayer p, Session s) {
		int stage = s.gunStage;
		if (stage >= ZeroGravityConfig.GUNHEAD_DAMAGE.length || !(p.serverLevel().getEntity(s.gunTarget) instanceof LivingEntity e) || !e.isAlive()
				|| e.distanceTo(p) > ZeroGravityConfig.GUNHEAD_REACH + 2.0) {
			s.gunTarget = -1;
			return;
		}
		boolean last = stage == ZeroGravityConfig.GUNHEAD_DAMAGE.length - 1;
		e.invulnerableTime = 0;
		boolean floating = ZeroGravityFloat.floatedBy(p, e);
		ZeroGravityCombat.strike(p, e, p.position(), ZeroGravityConfig.GUNHEAD_DAMAGE[stage], last ? 0.0 : 0.12, 0.0);
		p.swing(stage % 2 == 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
		if (last && !ZeroGravityCombat.isBoss(e)) {
			// over her shoulder: it lands behind her (a floating one sails off)
			Vec3 back = flatLook(p).scale(-1.0);
			Vec3 v = floating ? back.scale(ZeroGravityConfig.GUNHEAD_FLOAT_THROW).add(0, 0.3, 0)
					: back.scale(ZeroGravityConfig.GUNHEAD_THROW_BACK).add(0, ZeroGravityConfig.GUNHEAD_THROW_UP, 0);
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			if (floating) {
				ZeroGravityFloat.markFlung(p, e, 60);
			}
			Vec3 c = ZeroGravityCombat.center(e);
			p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		sound(p, last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f + 0.15f * stage);
		s.gunStage++;
		s.gunNext = now(p) + ZeroGravityConfig.GUNHEAD_GAP;
		if (s.gunStage >= ZeroGravityConfig.GUNHEAD_DAMAGE.length) {
			s.gunTarget = -1;
		}
	}

	/** Test hook: the next Gunhead beat now. */
	public static void gunheadStepForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.gunTarget >= 0) {
			gunheadHit(p, s);
		}
	}

	/** For a few seconds everything in a small area -- creatures, items -- floats helplessly, then drops. Returns the centre. */
	public static Vec3 field(ServerPlayer p) {
		interruptSettle(p);
		Vec3 aim = AbilityHelpers.aimPoint(p, ZeroGravityConfig.FIELD_RANGE);
		Vec3 c = aim.distanceToSqr(p.position()) < 4.0 ? p.position() : aim;
		// onto the ground
		BlockHitResult bh = p.level().clip(new ClipContext(c.add(0, 1.0, 0), c.add(0, -6.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			c = bh.getLocation();
		}
		Session s = session(p);
		s.fieldCenter = c;
		s.fieldUntil = now(p) + ZeroGravityConfig.FIELD_TICKS;
		Quirks.setVal(p, ZeroGravity.V_FIELD, s.fieldUntil);
		Quirks.setVec(p, ZeroGravity.VEC_FIELD, c);
		Quirks.setAnim(p, ZeroGravity.ANIM_FIELD);
		ServerLevel level = p.serverLevel();
		ZeroGravityCombat.boop(level, c, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
		Quirks.setCooldown(p, ZeroGravityConfig.FIELD, ZeroGravityConfig.FIELD_COOLDOWN);
		fieldPulse(p, s);
		return c;
	}

	/** Floats everything new inside the Field; returns how many. */
	static int fieldPulse(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		AABB box = new AABB(s.fieldCenter, s.fieldCenter).inflate(ZeroGravityConfig.FIELD_RADIUS, 0.5, ZeroGravityConfig.FIELD_RADIUS).expandTowards(0, 4.0, 0);
		int n = 0;
		for (Entity e : level.getEntities(p, box, e -> (e instanceof LivingEntity || e instanceof ItemEntity) && !ZeroGravityFloat.isFloating(e))) {
			if (Math.hypot(e.getX() - s.fieldCenter.x, e.getZ() - s.fieldCenter.z) > ZeroGravityConfig.FIELD_RADIUS) {
				continue;
			}
			if (ZeroGravityFloat.start(p, e, true, ZeroGravityConfig.FIELD_RISE)) {
				ZeroGravityFloat.releaseAt(p, e, s.fieldUntil);
				sting(p, e instanceof ItemEntity ? 0.1f : 0.5f);
				ZeroGravityCombat.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 5, 0.3);
				n++;
			}
		}
		if (n > 0) {
			syncCount(p);
		}
		return n;
	}

	/** Test read: the Field is up. */
	public static boolean fieldUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.fieldUntil > now(p);
	}

	private static void tickField(ServerPlayer p, Session s, long now) {
		if (now >= s.fieldUntil) {
			s.fieldUntil = 0L;
			Quirks.setVal(p, ZeroGravity.V_FIELD, 0L);
			syncCount(p);
			return;
		}
		if (now % 4 == 0) {
			fieldPulse(p, s);
		}
		if (now % 20 == 0) {
			p.serverLevel().playSound(null, s.fieldCenter.x, s.fieldCenter.y, s.fieldCenter.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
	}

	// ================================================================ C: Catch / Shift+C: Settle

	/** Everyone falling near her (herself too) is caught: their fall stops and they float down softly. Returns how many. */
	public static int catchFalling(ServerPlayer p) {
		interruptSettle(p);
		ServerLevel level = p.serverLevel();
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ZeroGravityConfig.CATCH_RADIUS),
				e -> e.isAlive() && !e.isSpectator() && !e.onGround() && !e.isInWater() && !e.isNoGravity() && !ZeroGravityCombat.isBoss(e))) {
			boolean falling = e.fallDistance >= ZeroGravityConfig.CATCH_MIN_FALL || e.getDeltaMovement().y < -ZeroGravityConfig.CATCH_MIN_SPEED;
			if (!falling || e.distanceToSqr(p) > ZeroGravityConfig.CATCH_RADIUS * ZeroGravityConfig.CATCH_RADIUS) {
				continue;
			}
			e.resetFallDistance();
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.3, 0.05, v.z * 0.3);
			e.hurtMarked = true;
			e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ZeroGravityConfig.CATCH_SLOW_FALL_TICKS, 0, false, true, true));
			ZeroGravityCombat.touchFx(level, ZeroGravityCombat.center(e));
			Quirks.addMeter(p, ZeroGravityConfig.CATCH_NAUSEA);
			n++;
		}
		Quirks.setAnim(p, ZeroGravity.ANIM_CATCH);
		if (n == 0) {
			Quirks.say(p, "message.projecthero.zero_gravity.nobody_falling", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
		Quirks.setCooldown(p, ZeroGravityConfig.CATCH, ZeroGravityConfig.CATCH_COOLDOWN);
		return n;
	}

	/**
	 * Settle: everything she holds up is let down SAFELY (no fall damage: creatures float down, blocks dissolve back into the
	 * ground, her own float ends gently), then she kneels and breathes -- Nausea vents fast while she stays put on the ground.
	 * Works while sick. Returns how many things she let down.
	 */
	public static int settle(ServerPlayer p) {
		List<Entity> out = ZeroGravityFloat.releaseAll(p, true);
		int blocks = ZeroGravityDebris.dissolveAll(p);
		boolean self = ZeroGravityFloat.selfFloating(p) || leaping(p);
		stopSelf(p);
		endLeap(p);
		if (self) {
			p.resetFallDistance();
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
		}
		Session s = session(p);
		s.lifts.clear();
		s.slams.clear();
		s.comets.clear();
		s.fieldUntil = 0L;
		s.settleUntil = now(p) + ZeroGravityConfig.SETTLE_TICKS;
		Quirks.edit(p, n -> {
			n.vals.remove(ZeroGravity.V_CLOUD);
			n.vals.remove(ZeroGravity.V_SHIELD);
			n.vals.remove(ZeroGravity.V_FIELD);
		});
		Quirks.setVal(p, ZeroGravity.V_SETTLE, s.settleUntil);
		ServerLevel level = p.serverLevel();
		for (Entity e : out) {
			ZeroGravityCombat.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 6, 0.3);
		}
		ZeroGravityCombat.boop(level, p.position().add(0, 1.0, 0), 0.5f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
		Quirks.setCooldown(p, ZeroGravityConfig.SETTLE, ZeroGravityConfig.SETTLE_COOLDOWN);
		syncCount(p);
		return out.size() + blocks;
	}

	public static boolean settling(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.settleUntil > now(p);
	}

	private static void tickSettle(ServerPlayer p, Session s, long now) {
		if (now >= s.settleUntil || (now - (s.settleUntil - ZeroGravityConfig.SETTLE_TICKS) > 20 && !p.onGround())
				|| Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) > 0.12) {
			s.settleUntil = 0L;
			Quirks.setVal(p, ZeroGravity.V_SETTLE, 0L);
			return;
		}
		if (p.onGround()) {
			Quirks.addMeter(p, -ZeroGravityConfig.nausea().settlePerSecond / 20f);
		}
		if (now % 10 == 0) {
			ZeroGravityCombat.trail(p.serverLevel(), p.position().add(0, 0.2, 0));
		}
	}

	// ================================================================ the tick: Nausea and running moves

	public static void tick(ServerPlayer p) {
		long now = now(p);
		ZeroGravityFloat.tick(p);
		ZeroGravityDebris.tick(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			if (!s.lifts.isEmpty()) {
				tickLifts(p, s, now);
			}
			if (!s.slams.isEmpty()) {
				tickSlams(p, s, now);
			}
			if (!s.comets.isEmpty()) {
				tickComets(p, s, now);
			}
			if (s.gunTarget >= 0 && now >= s.gunNext) {
				gunheadHit(p, s);
			}
			if (s.leaping) {
				tickLeap(p, s, now);
			}
			if (s.fieldUntil != 0L) {
				tickField(p, s, now);
			}
			if (s.settleUntil != 0L) {
				tickSettle(p, s, now);
			}
		}
		if (now % 10 == 0) {
			syncCount(p);
			if (Quirks.state(p).val(ZeroGravity.V_SHIELD) > now && !ZeroGravityDebris.shieldUp(p)) {
				Quirks.setVal(p, ZeroGravity.V_SHIELD, 0L);
			}
			if (Quirks.state(p).val(ZeroGravity.V_CLOUD) != 0L && !ZeroGravityDebris.cloudUp(p)) {
				Quirks.setVal(p, ZeroGravity.V_CLOUD, 0L);
			}
		}
		nausea(p);
	}

	/** The weight she carries now: floated things, blocks held up, and her own body while it floats. */
	public static double weight(ServerPlayer p) {
		double w = ZeroGravityFloat.weight(p) + ZeroGravityDebris.held(p) * ZeroGravityConfig.WEIGHT_RUBBLE;
		if (ZeroGravityFloat.selfFloating(p) || leaping(p)) {
			w += ZeroGravityConfig.nausea().selfWeight;
		}
		return w;
	}

	/** Nausea rises with the weight she floats (and the time it has been floating), falls when nothing floats. */
	static void nausea(ServerPlayer p) {
		ZeroGravityConfig.Nausea cfg = ZeroGravityConfig.nausea();
		double w = weight(p);
		Session s = session(p);
		if (Quirks.overloaded(p)) {
			s.streak = 0;
			Quirks.addMeter(p, -cfg.decayPerSecond / 20f);
			return;
		}
		if (w <= 0.0) {
			s.streak = 0;
			float m = Quirks.addMeter(p, -cfg.decayPerSecond / 20f);
			if (m < 50f) {
				s.warned = false;
			}
			return;
		}
		s.streak++;
		float bonus = 1f + cfg.timeBonusMax * Math.min(1f, s.streak / (float) Math.max(1, cfg.timeBonusTicks));
		float m = Quirks.addMeter(p, (float) (cfg.perWeightPerSecond * w / 20.0 * bonus));
		if (m >= 100f) {
			sick(p);
			return;
		}
		if (m >= ZeroGravityConfig.NAUSEA_WARN && !s.warned) {
			s.warned = true;
			Quirks.say(p, "message.projecthero.zero_gravity.queasy", ChatFormatting.LIGHT_PURPLE);
		}
	}

	/** 100 Nausea: everything drops (stopAll), she is sick and has no moves (but Settle) for a while. */
	public static void sick(ServerPlayer p) {
		int ticks = ZeroGravityConfig.nausea().sickTicks;
		Quirks.overload(p, ticks);
		p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 60, 0, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true, true));
		Quirks.setAnim(p, ZeroGravity.ANIM_SICK);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.quirk.overloaded.zero_gravity")
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		sound(p, SoundEvents.PLAYER_BURP, 1.0f, 0.6f);
		session(p).warned = false;
	}

	/** Everything ends: floats drop, her own float and the leap end, every block goes (the ground put back), timers stop. */
	public static void endAll(ServerPlayer p) {
		boolean self = ZeroGravityFloat.selfFloating(p);
		ZeroGravityFloat.releaseAll(p, false);
		ZeroGravityFloat.stopSelf(p);
		if (self && p.isAlive() && !p.onGround()) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		}
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g != null) {
			g.removeModifier(LEAP_GRAVITY);
		}
		ZeroGravityDebris.dropAll(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.lifts.clear();
			s.slams.clear();
			s.comets.clear();
			s.gunTarget = -1;
			s.leaping = false;
			s.fieldUntil = 0L;
			s.settleUntil = 0L;
		}
		if (Quirks.hasPower(p)) {
			Quirks.edit(p, n -> {
				for (String k : new String[] { ZeroGravity.V_SELF, ZeroGravity.V_LEAP, ZeroGravity.V_FLOATING, ZeroGravity.V_BLOCKS, ZeroGravity.V_SHIELD,
						ZeroGravity.V_CLOUD, ZeroGravity.V_FIELD, ZeroGravity.V_GUNHEAD, ZeroGravity.V_SETTLE }) {
					n.vals.remove(k);
				}
			});
		}
	}
}
