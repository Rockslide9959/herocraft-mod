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
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry: the golden kit (routed by {@link SentryAbilityManager}). Every move checks its cooldown and pays its Sun here; the
 * running ones (Sunbeam, Molecular Grip, Sun Dive, A Million Suns, Sentinel Shield) are carried by {@link #tick}. Session maps are keyed
 * by player id only (never a world or an entity) and cleared on server stop ({@link #clearSessionState}).
 */
public final class SentryMoves {
	/** Golden Strike's combo: the step (0..2) and when the last strike landed. */
	private static final Map<UUID, int[]> COMBO = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> COMBO_AT = new ConcurrentHashMap<>();
	/** Which key holds the Sunbeam / the grip (released = the move ends). */
	private static final Map<UUID, AbilitySlot> BEAM_KEY = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> GRIP_HELD = new ConcurrentHashMap<>();
	/** Sun Dive running since. */
	private static final Map<UUID, Long> DIVING = new ConcurrentHashMap<>();

	private SentryMoves() {
	}

	static void clearSessionState() {
		COMBO.clear();
		COMBO_AT.clear();
		BEAM_KEY.clear();
		GRIP_HELD.clear();
		DIVING.clear();
	}

	static void forget(UUID id) {
		COMBO.remove(id);
		COMBO_AT.remove(id);
		BEAM_KEY.remove(id);
		GRIP_HELD.remove(id);
		DIVING.remove(id);
	}

	/** Diving right now (no fall damage). */
	public static boolean diving(Player player) {
		return DIVING.containsKey(player.getUUID());
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
					case SentryAbilityManager.GOLDEN_STRIKE -> goldenStrike(p);
					case SentryAbilityManager.SUNBEAM -> startBeam(p, slot);
					case SentryAbilityManager.GRIP -> startGrip(p);
					case SentryAbilityManager.UNMAKE -> unmake(p);
					case SentryAbilityManager.SUN_DIVE -> sunDive(p);
					case SentryAbilityManager.MILLION_SUNS -> startSuns(p);
					case SentryAbilityManager.HEALING_LIGHT -> healingLight(p);
					case SentryAbilityManager.SHIELD -> sentinelShield(p);
					default -> false;
				};
				if (fired && !SentryAbilityManager.SUNBEAM.equals(id) && !SentryAbilityManager.GRIP.equals(id)
						&& !SentryAbilityManager.MILLION_SUNS.equals(id)) {
					Sentry.startCooldown(p, id, SentryAbilityManager.maxCooldown(id));
				}
			}
		}
	}

	/** A key came up: held moves end (the beam, the grip throws, Calm stops). */
	static void release(ServerPlayer p, AbilitySlot slot) {
		UUID id = p.getUUID();
		if (BEAM_KEY.get(id) == slot) {
			stopBeam(p);
		}
		if (slot == AbilitySlot.SLOT_2 && GRIP_HELD.remove(id) != null) {
			throwGripped(p);
		}
		if (slot == AbilitySlot.SLOT_5 && Sentry.state(p).calming) {
			calm(p, false);
		}
	}

	// ---------------------------------------------------------------- R: Golden Strike

	/** A heavy punch; every 3rd a golden shockwave. True if it swung (a miss still swings). */
	static boolean goldenStrike(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.STRIKE_COST)) {
			return false;
		}
		long now = p.level().getGameTime();
		UUID id = p.getUUID();
		Long last = COMBO_AT.get(id);
		int[] step = COMBO.computeIfAbsent(id, k -> new int[1]);
		if (last == null || now - last > SentryConfig.STRIKE_COMBO_WINDOW) {
			step[0] = 0;
		}
		COMBO_AT.put(id, now);
		p.swing(step[0] % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
		ServerLevel level = p.serverLevel();
		LivingEntity t = target(p, SentryConfig.STRIKE_REACH);
		boolean third = step[0] == 2;
		step[0] = (step[0] + 1) % 3;
		Vec3 at = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0) : p.getEyePosition().add(p.getLookAngle().scale(2.5));
		if (t != null) {
			Sentry.hit(p, t, third ? SentryConfig.STRIKE_THIRD_DAMAGE : SentryConfig.STRIKE_DAMAGE, false);
			AbilityHelpers.knockbackFrom(t, p.position(), third ? 1.6 : 0.7);
		}
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.5f), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
		Sentry.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.8f);
		if (third) {
			Vec3 c = t != null ? t.position() : p.position().add(p.getLookAngle().multiply(2, 0, 2));
			double r = SentryConfig.STRIKE_WAVE_RADIUS;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> Sentry.areaVictim(p, e)
					&& e.distanceToSqr(c) <= r * r && e != t)) {
				Sentry.hit(p, e, SentryConfig.STRIKE_WAVE_DAMAGE, false);
				AbilityHelpers.knockbackFrom(e, c, 1.0);
			}
			SentryFx.send(level, SentryFxPayload.RING, c.add(0, 0.1, 0), (float) r, 12);
			Sentry.sound(p, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
		}
		return true;
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

	// ---------------------------------------------------------------- Shift+R: Sunbeam

	static boolean startBeam(ServerPlayer p, AbilitySlot slot) {
		if (!Sentry.hasSun(p, SentryAbilityManager.cost(SentryAbilityManager.SUNBEAM))) {
			Sentry.say(p, "message.projecthero.sentry.no_sun", ChatFormatting.GRAY);
			return false;
		}
		SentryState n = Sentry.state(p).copy();
		n.beamStart = Math.max(1L, p.level().getGameTime());
		Sentry.save(p, n);
		BEAM_KEY.put(p.getUUID(), slot);
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
		return true;
	}

	static void stopBeam(ServerPlayer p) {
		BEAM_KEY.remove(p.getUUID());
		SentryState s = Sentry.state(p);
		if (s.beamStart == 0L) {
			return;
		}
		SentryState n = s.copy();
		n.beamStart = 0L;
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.SUNBEAM, SentryConfig.BEAM_COOLDOWN);
		Sentry.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
	}

	/** Where the beam starts: between his hands, held out in front of his chest. */
	public static Vec3 beamOrigin(Player p) {
		return p.getEyePosition().add(0, -0.35, 0).add(p.getLookAngle().scale(0.6));
	}

	private static void tickBeam(ServerPlayer p, SentryState s, long now) {
		long age = now - s.beamStart;
		if (age > SentryConfig.BEAM_MAX_TICKS || !BEAM_KEY.containsKey(p.getUUID())) {
			stopBeam(p);
			return;
		}
		Vec3 from = beamOrigin(p);
		Vec3 dir = p.getLookAngle();
		BeamClash.report(p, from, dir, SentryConfig.BEAM_RANGE, SentryConfig.BEAM_CLASH_POWER, Sentry.GOLD);
		ServerLevel level = p.serverLevel();
		Vec3 end = from.add(dir.scale(SentryConfig.BEAM_RANGE));
		BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != p, 0.5f);
		if (eh != null) {
			end = eh.getLocation();
		}
		if (age % SentryConfig.BEAM_DAMAGE_INTERVAL == 0 && eh != null && eh.getEntity() instanceof LivingEntity le) {
			Sentry.hit(p, le, SentryConfig.BEAM_DAMAGE, false);
			le.igniteForSeconds(2f);
		}
		if (age % 2 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.03);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.2f), end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.0);
		}
		if (age % 5 == 0 && age > 0) {
			if (!Sentry.spendSun(p, SentryConfig.BEAM_COST_PER_SECOND / 4f)) {
				stopBeam(p);
				return;
			}
			Sentry.addVoid(p, SentryConfig.BEAM_VOID_PER_SECOND / 4f);
		}
	}

	// ---------------------------------------------------------------- G: Molecular Grip

	/** Big things can't be lifted (they are held in place and only torn). */
	static boolean liftable(LivingEntity e) {
		return e.getMaxHealth() < 300f && e.getBbHeight() < 4.0f && !(e instanceof Player pl && pl.getAbilities().invulnerable);
	}

	static boolean startGrip(ServerPlayer p) {
		LivingEntity t = target(p, SentryConfig.GRIP_RANGE);
		if (t == null || !Sentry.areaVictim(p, t)) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Sentry.spendSun(p, SentryConfig.GRIP_COST)) {
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

	private static LivingEntity gripped(ServerPlayer p, SentryState s) {
		if (s.gripTarget < 0) {
			return null;
		}
		Entity e = p.level().getEntity(s.gripTarget);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private static void endGrip(ServerPlayer p) {
		GRIP_HELD.remove(p.getUUID());
		SentryState s = Sentry.state(p);
		if (s.gripTarget < 0) {
			return;
		}
		SentryState n = s.copy();
		n.gripTarget = -1;
		n.gripStart = 0L;
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.GRIP, SentryConfig.GRIP_COOLDOWN);
	}

	private static void tickGrip(ServerPlayer p, SentryState s, long now) {
		LivingEntity t = gripped(p, s);
		if (t == null) {
			endGrip(p);
			return;
		}
		long age = now - s.gripStart;
		ServerLevel level = p.serverLevel();
		if (liftable(t)) {
			Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(4.0)).subtract(0, t.getBbHeight() * 0.5, 0);
			Vec3 pull = hold.subtract(t.position()).scale(0.35);
			t.setDeltaMovement(pull);
			t.hurtMarked = true;
			t.resetFallDistance();
		}
		if (age % 3 == 0) {
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.3f), c.x, c.y, c.z, 6, t.getBbWidth() * 0.6, t.getBbHeight() * 0.5, t.getBbWidth() * 0.6, 0.0);
		}
		if (GRIP_HELD.containsKey(p.getUUID()) && age >= SentryConfig.GRIP_TEAR_TICKS) {
			tear(p, t);
			endGrip(p);
			return;
		}
		if (age >= SentryConfig.GRIP_MAX_TICKS) {
			endGrip(p);
		}
	}

	/** Holding G: the molecules come apart. */
	static void tear(ServerPlayer p, LivingEntity t) {
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		Sentry.hit(p, t, SentryConfig.GRIP_TEAR_DAMAGE, false);
		Sentry.addVoid(p, SentryConfig.GRIP_TEAR_VOID);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.4, 0.5, 0.4, 0.12);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.0f), c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0.0);
		Sentry.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 0.6f);
	}

	/** G let go before the tear: the creature is thrown along his look. */
	static void throwGripped(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		LivingEntity t = gripped(p, s);
		if (t != null && p.level().getGameTime() - s.gripStart < SentryConfig.GRIP_TEAR_TICKS) {
			if (liftable(t)) {
				t.setDeltaMovement(p.getLookAngle().scale(SentryConfig.GRIP_THROW_SPEED).add(0, 0.3, 0));
				t.hurtMarked = true;
			}
			Sentry.hit(p, t, SentryConfig.GRIP_THROW_DAMAGE, false);
			Sentry.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		}
		endGrip(p);
	}

	// ---------------------------------------------------------------- Shift+G: Unmake

	/** Whether Unmake (or the Void) may take this block: no bedrock / unbreakables, no block entities, no portals. */
	public static boolean unmakeable(ServerLevel level, BlockPos pos, BlockState st) {
		if (st.isAir() || st.getDestroySpeed(level, pos) < 0f || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
			return false;
		}
		return !st.is(Blocks.BEDROCK) && !st.is(Blocks.NETHER_PORTAL) && !st.is(Blocks.END_PORTAL) && !st.is(Blocks.END_GATEWAY)
				&& !st.is(Blocks.END_PORTAL_FRAME) && !st.is(com.projecthero.mod.sentry.block.SentryBlocks.VOID_MATTER);
	}

	static boolean unmake(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		if (!AbilityHelpers.canGrief()) {
			Sentry.say(p, "message.projecthero.sentry.no_terrain", ChatFormatting.GRAY);
			return false;
		}
		BlockHitResult bh = AbilityHelpers.raycastBlock(p, SentryConfig.UNMAKE_RANGE);
		if (bh == null || bh.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		if (!Sentry.hasSun(p, SentryConfig.UNMAKE_COST)) {
			Sentry.say(p, "message.projecthero.sentry.no_sun", ChatFormatting.GRAY);
			return false;
		}
		BlockPos c = bh.getBlockPos();
		Direction face = bh.getDirection();
		int n = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos pos = switch (face.getAxis()) {
					case X -> c.offset(0, a, b);
					case Y -> c.offset(a, 0, b);
					case Z -> c.offset(a, b, 0);
				};
				BlockState st = level.getBlockState(pos);
				if (!level.isLoaded(pos) || !unmakeable(level, pos, st) || !level.mayInteract(p, pos)) {
					continue;
				}
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.04);
				level.sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
				n++;
			}
		}
		if (n == 0) {
			return false;
		}
		Sentry.spendSun(p, SentryConfig.UNMAKE_COST);
		Sentry.addVoid(p, SentryConfig.UNMAKE_VOID);
		level.playSound(null, c, SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- Z: Sun Dive

	static boolean sunDive(ServerPlayer p) {
		if (p.onGround()) {
			Sentry.say(p, "message.projecthero.sentry.dive_ground", ChatFormatting.GRAY);
			return false;
		}
		if (!Sentry.spendSun(p, SentryConfig.DIVE_COST)) {
			return false;
		}
		SentryFlight.stop(p);
		DIVING.put(p.getUUID(), p.level().getGameTime());
		Sentry.sound(p, SoundEvents.ELYTRA_FLYING, 0.6f, 1.6f);
		return true;
	}

	private static void tickDive(ServerPlayer p, long now) {
		Long start = DIVING.get(p.getUUID());
		if (start == null) {
			return;
		}
		boolean timeout = now - start > SentryConfig.DIVE_MAX_TICKS;
		if ((p.onGround() || p.isInWater() || timeout) && now - start > 1) {
			DIVING.remove(p.getUUID());
			slam(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x * 0.45, -1.0, look.z * 0.45).normalize();
		AbilityHelpers.launchSelf(p, dir.scale(SentryConfig.DIVE_SPEED));
		ServerLevel level = p.serverLevel();
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.6, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.02);
	}

	/** The golden crater. */
	static void slam(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		double r = SentryConfig.DIVE_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> Sentry.areaVictim(p, e)
				&& e.distanceToSqr(c) <= r * r)) {
			Sentry.hit(p, e, SentryConfig.DIVE_DAMAGE, false);
			AbilityHelpers.knockbackFrom(e, c, 1.4);
			AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
		}
		p.resetFallDistance();
		SentryFx.send(level, SentryFxPayload.CRATER, c.add(0, 0.05, 0), (float) r, 30);
		SentryFx.send(level, SentryFxPayload.RING, c.add(0, 0.1, 0), (float) r + 1f, 14);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 3, 1.2, 0.2, 1.2, 0.0);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.0f), c.x, c.y + 0.3, c.z, 50, r * 0.5, 0.3, r * 0.5, 0.0);
		Sentry.sound(p, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.1f);
	}

	// ---------------------------------------------------------------- Shift+Z: A Million Suns

	static boolean startSuns(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.SUNS_COST)) {
			return false;
		}
		stopBeam(p);
		SentryState n = Sentry.state(p).copy();
		n.sunsStart = Math.max(1L, p.level().getGameTime());
		Sentry.save(p, n);
		Sentry.startCooldown(p, SentryAbilityManager.MILLION_SUNS, SentryConfig.SUNS_COOLDOWN);
		SentryFx.send(p.serverLevel(), SentryFxPayload.SUNS_CHARGE, p.position().add(0, 1.0, 0), 3f, SentryConfig.SUNS_CHARGE_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.5f);
		return true;
	}

	private static void tickSuns(ServerPlayer p, SentryState s, long now) {
		long age = now - s.sunsStart;
		ServerLevel level = p.serverLevel();
		// he hangs in the air while the light gathers
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, age < 15 ? 0.08 : 0.0, v.z * 0.3));
		if (age % 4 == 0) {
			double rr = 3.0 - 2.5 * Math.min(1.0, age / (double) SentryConfig.SUNS_CHARGE_TICKS);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 10, rr, rr, rr, 0.0);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.8f), p.getX(), p.getY() + 1.0, p.getZ(), 8, rr, rr, rr, 0.0);
		}
		if (age % 20 == 0) {
			Sentry.sound(p, SoundEvents.BEACON_AMBIENT, 1.6f, 0.6f + age / 100f);
		}
		if (age < SentryConfig.SUNS_CHARGE_TICKS) {
			return;
		}
		SentryState n = Sentry.state(p).copy();
		n.sunsStart = 0L;
		Sentry.save(p, n);
		millionSuns(p);
	}

	/** The explosion of light: 140 at the centre falling to 70 at the 12-block edge. */
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
			AbilityHelpers.knockbackFrom(e, c, 2.5 * (1.0 - f * 0.5));
			e.igniteForSeconds(4f);
		}
		Sentry.addVoid(p, SentryConfig.SUNS_VOID);
		SentryFx.send(level, SentryFxPayload.SUNS, c, (float) r, 40, null, p.getId());
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 200, r * 0.4, r * 0.3, r * 0.4, 0.4);
		level.sendParticles(Sentry.dust(Sentry.GOLD, 2.5f), c.x, c.y, c.z, 150, r * 0.5, r * 0.3, r * 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.5f);
		return n;
	}

	// ---------------------------------------------------------------- C: Healing Light

	/** Whom Healing Light helps: him, squadmates, his own pets, villagers and golems. */
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

	static boolean healingLight(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.HEAL_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.HEAL_RADIUS;
		int others = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e.isAlive()
				&& e.distanceToSqr(p) <= r * r && healable(p, e))) {
			e.heal(SentryConfig.HEAL_AMOUNT);
			Set<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new HashSet<>();
			for (MobEffectInstance fx : e.getActiveEffects()) {
				if (fx.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					bad.add(fx.getEffect());
				}
			}
			bad.forEach(e::removeEffect);
			e.clearFire();
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 6, 0.4, 0.5, 0.4, 0.0);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.2f), c.x, c.y, c.z, 10, 0.4, 0.6, 0.4, 0.0);
			if (e != p) {
				others++;
			}
		}
		Sentry.addVoid(p, SentryConfig.HEAL_VOID + SentryConfig.HEAL_VOID_PER_ALLY * others);
		SentryFx.send(level, SentryFxPayload.RING, p.position().add(0, 0.1, 0), (float) r, 16);
		Sentry.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+C: Sentinel Shield

	static boolean sentinelShield(ServerPlayer p) {
		if (!Sentry.spendSun(p, SentryConfig.SHIELD_COST)) {
			return false;
		}
		SentryState n = Sentry.state(p).copy();
		n.shieldUntil = p.level().getGameTime() + SentryConfig.SHIELD_TICKS;
		Sentry.save(p, n);
		SentryFx.send(p.serverLevel(), SentryFxPayload.SHIELD, p.position(), (float) SentryConfig.SHIELD_RADIUS, SentryConfig.SHIELD_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
		return true;
	}

	/** Whether a projectile is his or a friend's (the dome lets those through). */
	static boolean friendly(ServerPlayer p, Projectile proj) {
		Entity owner = proj.getOwner();
		return owner == p || owner instanceof Player op && Squads.areAllies(p, op);
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
		double r = SentryConfig.SHIELD_RADIUS;
		Vec3 c = p.position().add(0, 1.0, 0);
		List<Projectile> list = level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r + 1.0),
				e -> e.isAlive() && e.distanceToSqr(c) <= (r + 0.5) * (r + 0.5) && !friendly(p, e));
		for (Projectile proj : list) {
			level.sendParticles(Sentry.dust(Sentry.GOLD, 1.4f), proj.getX(), proj.getY(), proj.getZ(), 8, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
			proj.discard();
		}
		if (!list.isEmpty()) {
			Sentry.sound(p, SoundEvents.SHIELD_BLOCK, 0.8f, 1.4f);
		}
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

	/** Shift+V: v0.17.5 rework -- the trial ({@link LetItOut}); winning it merges him with the Void. */
	static void letItOut(ServerPlayer p) {
		LetItOut.begin(p);
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		long now = p.level().getGameTime();
		if (s.beamStart != 0L) {
			tickBeam(p, s, now);
		}
		s = Sentry.state(p);
		if (s.gripTarget >= 0) {
			tickGrip(p, s, now);
		}
		s = Sentry.state(p);
		if (s.sunsStart != 0L) {
			tickSuns(p, s, now);
		}
		s = Sentry.state(p);
		if (s.shieldUntil != 0L) {
			tickShield(p, s, now);
		}
		tickDive(p, now);
		if (s.calming && now % 4 == 0) {
			p.serverLevel().sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.6, 0.8, 0.6, 0.0);
			if (!p.onGround()) {
				Vec3 v = p.getDeltaMovement();
				AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.5, Math.max(-0.02, v.y * 0.5), v.z * 0.5)); // floats while meditating
			}
		}
	}
}
