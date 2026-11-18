package com.projecthero.mod.sentry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry, moveset 2 (2026-11-18, user: "Create a new moveset based on the sentries powers in the movies and comics"): the
 * golden kit, routed by {@link SentryAbilityManager}. Bob Reynolds' comics and Thunderbolts* -- flying punches that send foes through
 * mountains, light from his eyes, molecular disassembly, hurling a foe into the sky, a sonic-boom slam from the air, "a million exploding
 * suns", healing light and the golden aura that turns attacks to light.
 * <ul>
 *   <li><b>R Golden Fury</b>: a three-punch combo; out of reach (up to 9) he lunges in; the 3rd is the Haymaker that launches it far
 *   (a launched creature slamming into a wall takes more).</li>
 *   <li><b>Hold Shift+R Solar Gaze</b>: twin eye beams (beam clash), burning hotter the longer they are held.</li>
 *   <li><b>Hold G Disassemble</b>: lift a creature (bosses pinned), it unravels; held 2 s it comes apart into light. Let go: thrown.</li>
 *   <li><b>Shift+G Skyward Hurl</b>: grab what is in reach and throw it straight up into the sky (bosses are slammed instead).</li>
 *   <li><b>Z Sunfall</b>: leap up (from the ground), then a fist-first dive and a sonic-boom slam: the inner hit, then the outer ring.</li>
 *   <li><b>Shift+Z A Million Suns</b>: a 4 s wind-up rising into the air (damage taken halved), then the explosion of light.</li>
 *   <li><b>C Golden Guardian</b>: heals allies, clears bad effects, Regeneration + Absorption; the undead around burn.</li>
 *   <li><b>Shift+C Molecular Shield</b>: a golden aura -- enemy projectiles turn to light (and Sun), hits taken are cut, melee is
 *   deflected back.</li>
 * </ul>
 * Every move checks its cooldown and pays its Sun here; the running ones are carried by {@link #tick}. Session maps are keyed by player
 * id only (never a world or an entity) and cleared on server stop ({@link #clearSessionState}).
 */
public final class SentryMoves {
	/** Golden Fury's combo: the step (0..2) and when the last punch landed. */
	private static final Map<UUID, int[]> COMBO = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> COMBO_AT = new ConcurrentHashMap<>();
	/** Which key holds the Solar Gaze / Disassemble (released = the move ends). */
	private static final Map<UUID, AbilitySlot> BEAM_KEY = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> GRIP_HELD = new ConcurrentHashMap<>();
	/** Sunfall: {start, phase (0 leap, 1 dive)}. */
	private static final Map<UUID, long[]> SUNFALL = new ConcurrentHashMap<>();
	/** A creature he launched (the Haymaker, Skyward Hurl): its entity id, until when, and whether a wall hurts it. */
	record Launch(int entity, long start, long until, boolean impact) {
	}

	private static final Map<UUID, Launch> LAUNCH = new ConcurrentHashMap<>();

	private SentryMoves() {
	}

	static void clearSessionState() {
		COMBO.clear();
		COMBO_AT.clear();
		BEAM_KEY.clear();
		GRIP_HELD.clear();
		SUNFALL.clear();
		LAUNCH.clear();
	}

	static void forget(UUID id) {
		COMBO.remove(id);
		COMBO_AT.remove(id);
		BEAM_KEY.remove(id);
		GRIP_HELD.remove(id);
		SUNFALL.remove(id);
		LAUNCH.remove(id);
	}

	/** Leaping / diving right now (no fall damage). */
	public static boolean diving(Player player) {
		return SUNFALL.containsKey(player.getUUID());
	}

	/** Sunfall's phase (tests): -1 none, 0 the leap, 1 the dive. */
	public static int sunfallPhase(Player player) {
		long[] s = SUNFALL.get(player.getUUID());
		return s == null ? -1 : (int) s[1];
	}

	/** The creature this player launched last, if it is still flying (tests). */
	public static int launched(Player player) {
		Launch l = LAUNCH.get(player.getUUID());
		return l == null ? -1 : l.entity;
	}

	// ---------------------------------------------------------------- input

	static void use(ServerPlayer p, String id, AbilitySlot slot) {
		SentryState s = Sentry.state(p);
		if (s.calming && !SentryAbilityManager.CALM.equals(id)) {
			Sentry.say(p, "message.projecthero.sentry.calming", ChatFormatting.GRAY);
			return;
		}
		if (s.sunsStart != 0L || s.letOutTrialStart != 0L) {
			return; // A Million Suns is charging / the trial is running: nothing else
		}
		switch (id) {
			case SentryAbilityManager.DASH -> SentryFlight.press(p);
			case SentryAbilityManager.CALM -> calm(p, true);
			case SentryAbilityManager.LET_IT_OUT -> letItOut(p);
			default -> {
				if (Sentry.cooldownRemaining(p, id) > 0) {
					return;
				}
				boolean fired = switch (id) {
					case SentryAbilityManager.GOLDEN_FURY -> goldenFury(p);
					case SentryAbilityManager.SOLAR_GAZE -> startGaze(p, slot);
					case SentryAbilityManager.DISASSEMBLE -> startDisassemble(p);
					case SentryAbilityManager.SKYWARD_HURL -> skywardHurl(p);
					case SentryAbilityManager.SUNFALL -> sunfall(p);
					case SentryAbilityManager.MILLION_SUNS -> startSuns(p);
					case SentryAbilityManager.GOLDEN_GUARDIAN -> goldenGuardian(p);
					case SentryAbilityManager.MOLECULAR_SHIELD -> molecularShield(p);
					default -> false;
				};
				// the held moves start their cooldown when they end
				if (fired && !SentryAbilityManager.SOLAR_GAZE.equals(id) && !SentryAbilityManager.DISASSEMBLE.equals(id)
						&& !SentryAbilityManager.MILLION_SUNS.equals(id)) {
					Sentry.startCooldown(p, id, SentryAbilityManager.maxCooldown(id));
				}
			}
		}
	}

	/** A key came up: held moves end (the gaze, Disassemble throws, Calm stops). */
	static void release(ServerPlayer p, AbilitySlot slot) {
		UUID id = p.getUUID();
		if (BEAM_KEY.get(id) == slot) {
			stopGaze(p);
		}
		if (slot == AbilitySlot.SLOT_2 && GRIP_HELD.remove(id) != null) {
			throwHeld(p);
		}
		if (slot == AbilitySlot.SLOT_5 && Sentry.state(p).calming) {
			calm(p, false);
		}
	}

	/** The living thing he is looking at within {@code range} (a beam-like ray stopped by blocks). */
	static LivingEntity target(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != p, 0.4f);
		return eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Big things can't be lifted or thrown (they are held in place). */
	static boolean liftable(LivingEntity e) {
		return e.getMaxHealth() < 300f && e.getBbHeight() < 4.0f && !(e instanceof Player pl && pl.getAbilities().invulnerable);
	}

	private static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** A creature comes apart into golden light (a kill by Disassemble or the Haymaker). */
	static void disintegrate(ServerLevel level, LivingEntity e) {
		Vec3 c = centre(e);
		SentryFx.send(level, SentryFxPayload.DISINTEGRATE, c, e.getBbHeight(), 30, new Vec3(e.getBbWidth(), 0, 0), -1);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.06);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.8f), c.x, c.y, c.z, 30, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
	}

	// ---------------------------------------------------------------- R: Golden Fury

	/** One punch of the combo (a miss still swings). True if it swung. */
	static boolean goldenFury(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.FURY_COST)) {
			return false;
		}
		long now = p.level().getGameTime();
		UUID id = p.getUUID();
		Long last = COMBO_AT.get(id);
		int[] step = COMBO.computeIfAbsent(id, k -> new int[1]);
		if (last == null || now - last > SentryConfig.FURY_COMBO_WINDOW) {
			step[0] = 0;
		}
		COMBO_AT.put(id, now);
		boolean haymaker = step[0] == 2;
		step[0] = (step[0] + 1) % 3;
		ServerLevel level = p.serverLevel();
		LivingEntity t = target(p, SentryConfig.FURY_LUNGE_RANGE);
		if (t != null && !Sentry.areaVictim(p, t)) {
			t = null;
		}
		if (t != null && p.distanceTo(t) > SentryConfig.FURY_REACH) {
			// out of reach: the golden lunge closes the gap
			Vec3 to = t.position().subtract(p.position());
			double len = to.length();
			AbilityHelpers.launchSelf(p, to.scale(1.0 / len).scale(Math.min(2.4, (len - 1.6) * 0.6)).add(0, 0.1, 0));
			SentryFx.send(level, SentryFxPayload.BOOM, p.position().add(0, 1.0, 0), 1.5f, 8, p.position().add(to), p.getId());
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.5f), p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
		}
		p.swing(haymaker || step[0] == 1 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
		if (haymaker) {
			Sentry.pose(p, SentryState.POSE_HAYMAKER, 8);
		}
		Vec3 at = t != null ? centre(t) : p.getEyePosition().add(p.getLookAngle().scale(2.5));
		if (t != null) {
			boolean landed = Sentry.hit(p, t, haymaker ? SentryConfig.FURY_HAYMAKER_DAMAGE : SentryConfig.FURY_DAMAGE, false);
			if (haymaker && t.isAlive() && liftable(t)) {
				Vec3 look = p.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0, look.z);
				flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : flat.normalize();
				t.setDeltaMovement(flat.scale(SentryConfig.FURY_LAUNCH_SPEED).add(0, SentryConfig.FURY_LAUNCH_LIFT + Math.max(0, look.y), 0));
				t.hurtMarked = true;
				LAUNCH.put(id, new Launch(t.getId(), now, now + SentryConfig.FURY_LAUNCH_TICKS, true));
				SentryFx.send(level, SentryFxPayload.COMET, at, 0.6f, SentryConfig.FURY_LAUNCH_TICKS, at, t.getId());
			} else if (haymaker && !t.isAlive() && landed) {
				disintegrate(level, t);
			} else {
				AbilityHelpers.knockbackFrom(t, p.position(), haymaker ? 1.6 : 0.6);
			}
		}
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.5f), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
		Sentry.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, haymaker ? 0.6f : 0.85f);
		if (haymaker) {
			SentryFx.send(level, SentryFxPayload.BOOM, at, 2.5f, 12, at.add(p.getLookAngle()), p.getId());
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			Sentry.sound(p, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.5f);
		}
		return true;
	}

	/** A launched creature: a golden comet trail; slamming into a wall (Haymaker only) hurts it once more. */
	private static void tickLaunch(ServerPlayer p, long now) {
		Launch l = LAUNCH.get(p.getUUID());
		if (l == null) {
			return;
		}
		Entity e = p.level().getEntity(l.entity);
		if (!(e instanceof LivingEntity le) || !le.isAlive() || now > l.until) {
			LAUNCH.remove(p.getUUID());
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = centre(le);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.01);
		if (l.impact && now - l.start >= 2 && le.horizontalCollision) {
			LAUNCH.remove(p.getUUID());
			Sentry.hit(p, le, SentryConfig.FURY_IMPACT_DAMAGE, false);
			SentryFx.send(level, SentryFxPayload.RING, le.position().add(0, 0.6, 0), 2.5f, 10);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
			BlockPos hitAt = le.blockPosition().relative(le.getDirection());
			BlockState st = level.getBlockState(hitAt);
			if (!st.isAir()) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.2);
			}
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
		} else if (now - l.start >= 4 && le.onGround()) {
			LAUNCH.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- Shift+R: Solar Gaze

	static boolean startGaze(ServerPlayer p, AbilitySlot slot) {
		if (!Sentry.hasSun(p, SentryAbilityManager.cost(SentryAbilityManager.SOLAR_GAZE))) {
			Sentry.say(p, "message.projecthero.sentry.no_sun", ChatFormatting.GRAY);
			return false;
		}
		SentryState n = Sentry.state(p).copy();
		n.beamStart = Math.max(1L, p.level().getGameTime());
		Sentry.save(p, n);
		BEAM_KEY.put(p.getUUID(), slot);
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
		return true;
	}

	static void stopGaze(ServerPlayer p) {
		BEAM_KEY.remove(p.getUUID());
		SentryState s = Sentry.state(p);
		if (s.beamStart == 0L) {
			return;
		}
		SentryState n = s.copy();
		n.beamStart = 0L;
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.SOLAR_GAZE, SentryConfig.GAZE_COOLDOWN);
		Sentry.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.8f);
	}

	/** Where the gaze starts: his eyes (the client draws one beam from each). */
	public static Vec3 gazeOrigin(Player p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.25));
	}

	/** The gaze's damage this tick of holding it (it ramps up past {@link SentryConfig#GAZE_RAMP_TICKS}). */
	public static float gazeDamage(long heldTicks) {
		return heldTicks >= SentryConfig.GAZE_RAMP_TICKS ? SentryConfig.GAZE_DAMAGE * SentryConfig.GAZE_RAMP : SentryConfig.GAZE_DAMAGE;
	}

	private static void tickGaze(ServerPlayer p, SentryState s, long now) {
		long age = now - s.beamStart;
		if (age > SentryConfig.GAZE_MAX_TICKS || !BEAM_KEY.containsKey(p.getUUID())) {
			stopGaze(p);
			return;
		}
		Vec3 from = gazeOrigin(p);
		Vec3 dir = p.getLookAngle();
		BeamClash.report(p, from, dir, SentryConfig.GAZE_RANGE, SentryConfig.GAZE_CLASH_POWER, Sentry.GOLD);
		ServerLevel level = p.serverLevel();
		Vec3 end = from.add(dir.scale(SentryConfig.GAZE_RANGE));
		BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != p, 0.5f);
		if (eh != null) {
			end = eh.getLocation();
		}
		if (age % SentryConfig.GAZE_DAMAGE_INTERVAL == 0 && eh != null && eh.getEntity() instanceof LivingEntity le) {
			Sentry.hit(p, le, gazeDamage(age), false);
			le.igniteForSeconds(3f);
		}
		if (age % 2 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.03);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.2f), end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.0);
			if (age >= SentryConfig.GAZE_RAMP_TICKS) {
				level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.02);
			}
		}
		if (age % 10 == 0) {
			Sentry.sound(p, SoundEvents.BEACON_AMBIENT, 0.5f, age >= SentryConfig.GAZE_RAMP_TICKS ? 2.0f : 1.6f);
		}
		if (age % 5 == 0 && age > 0) {
			if (!Sentry.spendSun(p, SentryConfig.GAZE_COST_PER_SECOND / 4f)) {
				stopGaze(p);
				return;
			}
			Sentry.addVoid(p, SentryConfig.GAZE_VOID_PER_SECOND / 4f);
		}
	}

	// ---------------------------------------------------------------- G: Disassemble

	static boolean startDisassemble(ServerPlayer p) {
		LivingEntity t = target(p, SentryConfig.UNMAKE_RANGE);
		if (t == null || !Sentry.areaVictim(p, t)) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Sentry.spendSun(p, SentryConfig.UNMAKE_COST)) {
			return false;
		}
		SentryState n = Sentry.state(p).copy();
		n.gripTarget = t.getId();
		n.gripStart = Math.max(1L, p.level().getGameTime());
		Sentry.save(p, n);
		GRIP_HELD.put(p.getUUID(), true);
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.4f);
		return true;
	}

	private static LivingEntity held(ServerPlayer p, SentryState s) {
		if (s.gripTarget < 0) {
			return null;
		}
		Entity e = p.level().getEntity(s.gripTarget);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private static void endDisassemble(ServerPlayer p) {
		GRIP_HELD.remove(p.getUUID());
		SentryState s = Sentry.state(p);
		if (s.gripTarget < 0) {
			return;
		}
		SentryState n = s.copy();
		n.gripTarget = -1;
		n.gripStart = 0L;
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.DISASSEMBLE, SentryConfig.UNMAKE_COOLDOWN);
	}

	private static void tickDisassemble(ServerPlayer p, SentryState s, long now) {
		LivingEntity t = held(p, s);
		if (t == null) {
			endDisassemble(p);
			return;
		}
		long age = now - s.gripStart;
		ServerLevel level = p.serverLevel();
		if (liftable(t)) {
			Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(4.0)).subtract(0, t.getBbHeight() * 0.5, 0);
			t.setDeltaMovement(hold.subtract(t.position()).scale(0.35));
			t.hurtMarked = true;
			t.resetFallDistance();
		} else {
			t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 1.0, 0.2)); // pinned where it stands
			t.hurtMarked = true;
		}
		Vec3 c = centre(t);
		float k = Math.min(1f, age / (float) SentryConfig.UNMAKE_TEAR_TICKS);
		if (age % 2 == 0) {
			// the golden field, and motes flaking off as the molecules come loose
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.3f), c.x, c.y, c.z, 4 + (int) (6 * k), t.getBbWidth() * 0.6, t.getBbHeight() * 0.5, t.getBbWidth() * 0.6, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1 + (int) (3 * k), t.getBbWidth() * 0.5, t.getBbHeight() * 0.4, t.getBbWidth() * 0.5, 0.02 + 0.04 * k);
		}
		if (age > 0 && age % SentryConfig.UNMAKE_TICK_INTERVAL == 0) {
			Sentry.hit(p, t, SentryConfig.UNMAKE_TICK_DAMAGE, false); // (a burst hit: lands through the hit window)
			Sentry.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.8f + k);
		}
		if (!t.isAlive()) {
			disintegrate(level, t);
			endDisassemble(p);
			return;
		}
		if (GRIP_HELD.containsKey(p.getUUID()) && age >= SentryConfig.UNMAKE_TEAR_TICKS) {
			tear(p, t);
			endDisassemble(p);
			return;
		}
		if (age >= SentryConfig.UNMAKE_MAX_TICKS) {
			endDisassemble(p);
		}
	}

	/** Held long enough: the molecules come apart. */
	static void tear(ServerPlayer p, LivingEntity t) {
		ServerLevel level = p.serverLevel();
		Vec3 c = centre(t);
		Sentry.hit(p, t, SentryConfig.UNMAKE_TEAR_DAMAGE, false);
		Sentry.addVoid(p, SentryConfig.UNMAKE_TEAR_VOID);
		if (!t.isAlive()) {
			disintegrate(level, t);
		}
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.4, 0.5, 0.4, 0.12);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.0f), c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0.0);
		Sentry.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 0.6f);
	}

	/** G let go before it came apart: thrown along his look. */
	static void throwHeld(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		LivingEntity t = held(p, s);
		if (t != null && p.level().getGameTime() - s.gripStart < SentryConfig.UNMAKE_TEAR_TICKS) {
			Sentry.hit(p, t, SentryConfig.UNMAKE_THROW_DAMAGE, false);
			if (liftable(t)) {
				t.setDeltaMovement(p.getLookAngle().scale(SentryConfig.UNMAKE_THROW_SPEED).add(0, 0.3, 0));
				t.hurtMarked = true;
			}
			Sentry.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		}
		endDisassemble(p);
	}

	// ---------------------------------------------------------------- Shift+G: Skyward Hurl

	static boolean skywardHurl(ServerPlayer p) {
		LivingEntity t = target(p, SentryConfig.HURL_REACH);
		if (t == null || !Sentry.areaVictim(p, t)) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Sentry.spendSun(p, SentryConfig.HURL_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Sentry.pose(p, SentryState.POSE_HURL, 12);
		p.swing(InteractionHand.MAIN_HAND, true);
		Vec3 c = centre(t);
		if (liftable(t)) {
			Sentry.hit(p, t, SentryConfig.HURL_DAMAGE, false);
			if (t.isAlive()) {
				Vec3 look = p.getLookAngle();
				t.setDeltaMovement(look.x * 0.12, SentryConfig.HURL_SPEED, look.z * 0.12);
				t.hurtMarked = true;
				t.resetFallDistance();
				long now = level.getGameTime();
				LAUNCH.put(p.getUUID(), new Launch(t.getId(), now, now + 60, false));
				SentryFx.send(level, SentryFxPayload.COMET, c, 0.7f, 50, c, t.getId());
			}
			Sentry.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.6f);
		} else {
			// too big to throw: slammed down where it stands
			Sentry.hit(p, t, SentryConfig.HURL_BOSS_DAMAGE, false);
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, SentryConfig.HURL_BOSS_SLOW_TICKS, 2);
			SentryFx.send(level, SentryFxPayload.RING, t.position().add(0, 0.1, 0), 3.5f, 12);
			Sentry.sound(p, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.0f);
		}
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.8f), c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.1, t.getZ(), 12, 0.5, 0.1, 0.5, 0.05);
		Sentry.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		return true;
	}

	// ---------------------------------------------------------------- Z: Sunfall

	static boolean sunfall(ServerPlayer p) {
		if (SUNFALL.containsKey(p.getUUID()) || !Sentry.spendSun(p, SentryConfig.SUNFALL_COST)) {
			return false;
		}
		boolean ground = p.onGround();
		SentryFlight.stop(p);
		long now = p.level().getGameTime();
		SUNFALL.put(p.getUUID(), new long[] { now, ground ? 0 : 1 });
		ServerLevel level = p.serverLevel();
		if (ground) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x * 0.5, SentryConfig.SUNFALL_LEAP, p.getDeltaMovement().z * 0.5));
			Sentry.pose(p, SentryState.POSE_LEAP, SentryConfig.SUNFALL_LEAP_TICKS);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.6, 0.1, 0.6, 0.08);
			SentryFx.send(level, SentryFxPayload.RING, p.position().add(0, 0.1, 0), 2.5f, 10);
			Sentry.sound(p, SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.8f);
		} else {
			Sentry.pose(p, SentryState.POSE_DIVE, SentryConfig.SUNFALL_MAX_TICKS);
			Sentry.sound(p, SoundEvents.ELYTRA_FLYING, 0.6f, 1.6f);
		}
		return true;
	}

	private static void tickSunfall(ServerPlayer p, long now) {
		long[] st = SUNFALL.get(p.getUUID());
		if (st == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		long age = now - st[0];
		if (st[1] == 0) {
			// the leap: up until it slows (or the leap time is over), then the dive
			if (age >= SentryConfig.SUNFALL_LEAP_TICKS || (age > 3 && p.getDeltaMovement().y < 0.15)) {
				st[1] = 1;
				st[0] = now;
				Sentry.pose(p, SentryState.POSE_DIVE, SentryConfig.SUNFALL_MAX_TICKS);
				Sentry.sound(p, SoundEvents.ELYTRA_FLYING, 0.6f, 1.6f);
			} else {
				level.sendParticles(Sentry.dust(Sentry.GOLD, 1.2f), p.getX(), p.getY() + 0.5, p.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			}
			return;
		}
		boolean timeout = age > SentryConfig.SUNFALL_MAX_TICKS;
		if ((p.onGround() || p.isInWater() || timeout) && age > 1) {
			SUNFALL.remove(p.getUUID());
			slam(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x * 0.45, -1.0, look.z * 0.45).normalize();
		AbilityHelpers.launchSelf(p, dir.scale(SentryConfig.SUNFALL_SPEED));
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.6, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.02);
		if (age % 3 == 0) {
			SentryFx.send(level, SentryFxPayload.BOOM, p.position().add(0, 0.5, 0), 1.2f, 6, p.position().add(dir), p.getId());
		}
	}

	/** The sonic-boom slam: the inner hit, then the outer ring. Returns how many it hit (tests). */
	public static int slam(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		double r = SentryConfig.SUNFALL_RADIUS;
		double r2 = SentryConfig.SUNFALL_RING_RADIUS;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r2, 4.0, r2),
				e -> Sentry.areaVictim(p, e) && e.distanceToSqr(c) <= r2 * r2)) {
			boolean inner = e.distanceToSqr(c) <= r * r;
			if (Sentry.hit(p, e, inner ? SentryConfig.SUNFALL_DAMAGE : SentryConfig.SUNFALL_RING_DAMAGE, false)) {
				n++;
			}
			AbilityHelpers.knockbackFrom(e, c, inner ? 1.5 : 0.9);
			AbilityHelpers.push(e, new Vec3(0, inner ? 0.55 : 0.25, 0));
		}
		p.resetFallDistance();
		Sentry.pose(p, 0, 0);
		SentryFx.send(level, SentryFxPayload.CRATER, c.add(0, 0.05, 0), (float) r, 30);
		SentryFx.send(level, SentryFxPayload.RING, c.add(0, 0.1, 0), (float) r2, 14);
		SentryFx.send(level, SentryFxPayload.BOOM, c.add(0, 0.3, 0), 3f, 12, c.add(0, 1.3, 0), p.getId());
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 4, 1.4, 0.2, 1.4, 0.0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.0f), c.x, c.y + 0.3, c.z, 60, r * 0.6, 0.3, r * 0.6, 0.0);
		Sentry.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.0f);
		Sentry.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.3f);
		return n;
	}

	// ---------------------------------------------------------------- Shift+Z: A Million Suns

	static boolean startSuns(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.SUNS_COST)) {
			return false;
		}
		stopGaze(p);
		SentryState n = Sentry.state(p).copy();
		n.sunsStart = Math.max(1L, p.level().getGameTime());
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.MILLION_SUNS, SentryConfig.SUNS_COOLDOWN);
		SentryFx.send(p.serverLevel(), SentryFxPayload.SUNS_CHARGE, p.position().add(0, 1.0, 0), 3.5f, SentryConfig.SUNS_CHARGE_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.5f);
		return true;
	}

	/** Charging A Million Suns right now (damage taken is halved). */
	public static boolean chargingSuns(Player p) {
		SentryState s = Sentry.peek(p);
		return s != null && s.sunsStart != 0L;
	}

	private static void tickSuns(ServerPlayer p, SentryState s, long now) {
		long age = now - s.sunsStart;
		ServerLevel level = p.serverLevel();
		// he rises into the air while the light gathers, then hangs there
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, age < 20 ? 0.12 : 0.0, v.z * 0.3));
		if (age % 4 == 0) {
			double rr = 3.5 - 3.0 * Math.min(1.0, age / (double) SentryConfig.SUNS_CHARGE_TICKS);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 10, rr, rr, rr, 0.0);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.8f), p.getX(), p.getY() + 1.0, p.getZ(), 8, rr, rr, rr, 0.0);
		}
		if (age % 20 == 0) {
			Sentry.sound(p, SoundEvents.BEACON_AMBIENT, 1.6f, 0.5f + age / 100f);
		}
		if (age == SentryConfig.SUNS_CHARGE_TICKS - 20) {
			Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 2.0f, 1.8f);
		}
		if (age < SentryConfig.SUNS_CHARGE_TICKS) {
			return;
		}
		SentryState n = Sentry.state(p).copy();
		n.sunsStart = 0L;
		Sentry.save(p, n);
		millionSuns(p);
	}

	/** The explosion of light: 150 at the centre falling to 70 at the 14-block edge. Returns how many it hit. */
	public static int millionSuns(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		double r = SentryConfig.SUNS_RADIUS;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> Sentry.areaVictim(p, e)
				&& e.distanceToSqr(c) <= r * r)) {
			double f = Math.min(1.0, Math.sqrt(e.distanceToSqr(c)) / r);
			float dmg = (float) (SentryConfig.SUNS_DAMAGE + (SentryConfig.SUNS_EDGE_DAMAGE - SentryConfig.SUNS_DAMAGE) * f);
			if (Sentry.hit(p, e, dmg, false)) {
				n++;
			}
			if (!e.isAlive()) {
				disintegrate(level, e);
			}
			AbilityHelpers.knockbackFrom(e, c, 2.5 * (1.0 - f * 0.5));
			e.igniteForSeconds(5f);
		}
		Sentry.addVoid(p, SentryConfig.SUNS_VOID);
		SentryFx.send(level, SentryFxPayload.SUNS, c, (float) r, 40, null, p.getId());
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 200, r * 0.4, r * 0.3, r * 0.4, 0.4);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.5f), c.x, c.y, c.z, 150, r * 0.5, r * 0.3, r * 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
		return n;
	}

	// ---------------------------------------------------------------- C: Golden Guardian

	/** Whom Golden Guardian helps: him, squadmates, his own pets, villagers and golems. */
	static boolean healable(ServerPlayer p, LivingEntity e) {
		if (e == p) {
			return true;
		}
		if (e instanceof Player other) {
			return Squads.areAllies(p, other);
		}
		if (e instanceof TamableAnimal tame && tame.isOwnedBy(p)) {
			return true;
		}
		return e instanceof AbstractVillager || e instanceof IronGolem || HeroTargets.isOwnedBy(e, p);
	}

	static boolean goldenGuardian(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.GUARDIAN_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.GUARDIAN_RADIUS;
		int others = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e.isAlive()
				&& e.distanceToSqr(p) <= r * r)) {
			if (healable(p, e)) {
				e.heal(SentryConfig.GUARDIAN_HEAL);
				Set<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new HashSet<>();
				for (MobEffectInstance fx : e.getActiveEffects()) {
					if (fx.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
						bad.add(fx.getEffect());
					}
				}
				bad.forEach(e::removeEffect);
				e.clearFire();
				e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SentryConfig.GUARDIAN_REGEN_TICKS, 0, false, true, true));
				e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SentryConfig.GUARDIAN_ABSORB_TICKS, 0, false, true, true));
				SentryFx.send(level, SentryFxPayload.PILLAR, e.position(), e.getBbHeight() + 2.5f, 24);
				Vec3 c = centre(e);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 6, 0.4, 0.5, 0.4, 0.0);
				level.sendParticles(Sentry.dust(Sentry.GOLD, 1.2f), c.x, c.y, c.z, 10, 0.4, 0.6, 0.4, 0.0);
				if (e != p) {
					others++;
				}
			} else if (e.isInvertedHealAndHarm() && Sentry.areaVictim(p, e)) {
				// the undead burn in his light
				Sentry.hit(p, e, SentryConfig.GUARDIAN_UNDEAD_DAMAGE, false);
				e.igniteForSeconds(3f);
				Vec3 c = centre(e);
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.3, 0.5, 0.3, 0.02);
			}
		}
		Sentry.addVoid(p, SentryConfig.GUARDIAN_VOID + SentryConfig.GUARDIAN_VOID_PER_ALLY * others);
		Sentry.pose(p, SentryState.POSE_GUARDIAN, 20);
		SentryFx.send(level, SentryFxPayload.RING, p.position().add(0, 0.1, 0), (float) r, 18);
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
		Sentry.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+C: Molecular Shield

	static boolean molecularShield(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.MSHIELD_COST)) {
			return false;
		}
		SentryState n = Sentry.state(p).copy();
		n.shieldUntil = p.level().getGameTime() + SentryConfig.MSHIELD_TICKS;
		Sentry.save(p, n);
		Sentry.pose(p, SentryState.POSE_SHIELD, 12);
		SentryFx.send(p.serverLevel(), SentryFxPayload.SHIELD, p.position(), (float) SentryConfig.MSHIELD_RADIUS, SentryConfig.MSHIELD_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.3f);
		return true;
	}

	/** The aura is up. */
	public static boolean shielded(Player p) {
		SentryState s = Sentry.peek(p);
		return s != null && s.shieldUntil > p.level().getGameTime();
	}

	/** Whether a projectile is his or a friend's (the aura lets those through). */
	static boolean friendly(ServerPlayer p, Projectile proj) {
		Entity owner = proj.getOwner();
		return owner == p || owner instanceof Player op && Squads.areAllies(p, op);
	}

	/** Melee on him while the aura is up: deflected back at the attacker (from {@code SentryDamage}). */
	static void deflect(ServerPlayer victim, DamageSource source) {
		if (!shielded(victim) || !(source.getDirectEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != source.getEntity()
				|| attacker == victim || attacker.distanceToSqr(victim) > 5.0 * 5.0 || !Sentry.areaVictim(victim, attacker)) {
			return;
		}
		if (attacker.invulnerableTime > 0) {
			return; // (once per hit window)
		}
		Sentry.hit(victim, attacker, SentryConfig.MSHIELD_DEFLECT_DAMAGE, false);
		AbilityHelpers.knockbackFrom(attacker, victim.position(), 1.0);
		Vec3 c = centre(attacker);
		victim.serverLevel().sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.08);
		Sentry.sound(victim, SoundEvents.SHIELD_BLOCK, 0.8f, 1.6f);
	}

	private static void tickShield(ServerPlayer p, SentryState s, long now) {
		if (s.shieldUntil <= now) {
			if (s.shieldUntil != 0L) {
				SentryState n = s.copy();
				n.shieldUntil = 0L;
				Sentry.save(p, n);
			}
			return;
		}
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.MSHIELD_RADIUS;
		Vec3 c = p.position().add(0, 1.0, 0);
		List<Projectile> list = level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r + 1.0),
				e -> e.isAlive() && e.distanceToSqr(c) <= (r + 0.5) * (r + 0.5) && !friendly(p, e));
		for (Projectile proj : list) {
			// turned to light
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.4f), proj.getX(), proj.getY(), proj.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.08);
			proj.discard();
			if (!Sentry.free(p)) {
				Sentry.setSun(p, Sentry.state(p).sun + SentryConfig.MSHIELD_SUN_PER_PROJECTILE);
			}
		}
		if (!list.isEmpty()) {
			Sentry.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
		}
		if (now % 6 == 0) {
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 4, r * 0.5, 0.8, r * 0.5, 0.0);
		}
	}

	// ---------------------------------------------------------------- the old Unmake's block rule (still used by the Void's decay)

	/** Whether the Void may take this block: no bedrock / unbreakables, no block entities, no portals. */
	public static boolean unmakeable(ServerLevel level, BlockPos pos, BlockState st) {
		if (st.isAir() || st.getDestroySpeed(level, pos) < 0f || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
			return false;
		}
		return !st.is(Blocks.BEDROCK) && !st.is(Blocks.NETHER_PORTAL) && !st.is(Blocks.END_PORTAL) && !st.is(Blocks.END_GATEWAY)
				&& !st.is(Blocks.END_PORTAL_FRAME) && !st.is(com.projecthero.mod.sentry.block.SentryBlocks.VOID_MATTER);
	}

	// ---------------------------------------------------------------- V: Calm, Shift+V: Let it out

	static void calm(ServerPlayer p, boolean on) {
		SentryState s = Sentry.state(p);
		if (s.calming == on) {
			return;
		}
		SentryState n = s.copy();
		n.calming = on;
		if (on) {
			n.beamStart = 0L;
			n.gripTarget = -1;
		}
		Sentry.save(p, n);
		if (on) {
			BEAM_KEY.remove(p.getUUID());
			GRIP_HELD.remove(p.getUUID());
			Sentry.sound(p, SoundEvents.BEACON_AMBIENT, 0.8f, 1.4f);
		}
	}

	/** Shift+V: the trial ({@link LetItOut}); winning it merges him with the Void. */
	static void letItOut(ServerPlayer p) {
		LetItOut.begin(p);
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		long now = p.level().getGameTime();
		if (s.beamStart != 0L) {
			tickGaze(p, s, now);
		}
		s = Sentry.state(p);
		if (s.gripTarget >= 0) {
			tickDisassemble(p, s, now);
		}
		s = Sentry.state(p);
		if (s.sunsStart != 0L) {
			tickSuns(p, s, now);
		}
		s = Sentry.state(p);
		if (s.shieldUntil != 0L) {
			tickShield(p, s, now);
		}
		tickSunfall(p, now);
		tickLaunch(p, now);
		if (s.calming && now % 4 == 0) {
			p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.6, 0.8, 0.6, 0.0);
			if (!p.onGround()) {
				Vec3 v = p.getDeltaMovement();
				AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.5, Math.max(-0.02, v.y * 0.5), v.z * 0.5)); // floats while meditating
			}
		}
	}
}
