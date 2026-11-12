package com.projecthero.mod.raven;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.entity.DarkBoltEntity;
import com.projecthero.mod.raven.entity.RavenDebrisEntity;
import com.projecthero.mod.raven.entity.SoulSelfEntity;
import com.projecthero.mod.raven.network.RavenFxPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: every move ({@code docs/TEEN_TITANS_DESIGN.md}, the Raven section). {@link RavenAbilityManager} routes the keys here;
 * the running parts (the telekinetic hold, thrown creatures, the Grasp's claws, the Wave's lift, the Dash and the Step, the orbiting
 * debris, the shield, the Wrath's tentacles, the chant) tick in {@link #tick}. All of it is per player in maps that hold entities,
 * cleared by {@link #clearSessionState} (from {@code ServerStateReset}).
 */
public final class RavenMoves {
	private RavenMoves() {
	}

	// ---------------------------------------------------------------- running state

	/** Telekinesis: what she holds and how far out. */
	private static final class Hold {
		Entity target;
		double dist;
		long start;
	}

	/** A creature she threw: it takes the hit when it lands or strikes something. */
	private static final class Thrown {
		final LivingEntity e;
		int ticks;
		double speed;

		Thrown(LivingEntity e) {
			this.e = e;
		}
	}

	/** The Grasp's claws holding a creature in place. */
	private record Pin(LivingEntity e, Vec3 at, long until) {
	}

	/** A creature in the Wave's (or the Wrath's) grip: up, held, slammed. */
	private static final class Lift {
		final LivingEntity e;
		final double baseY;
		final long start;
		final int rise;
		final int hold;
		final float damage;
		final double height;
		final boolean wrath;
		long slamAt = -1L;
		double groundY;
		boolean done;

		Lift(LivingEntity e, long start, int rise, int hold, double height, float damage, boolean wrath) {
			this.e = e;
			this.baseY = e.getY();
			this.start = start;
			this.rise = rise;
			this.hold = hold;
			this.height = height;
			this.damage = damage;
			this.wrath = wrath;
		}
	}

	private record Dash(Vec3 from, Vec3 to, long start) {
	}

	private record Step(LivingEntity target, long at) {
	}

	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Thrown>> THROWN = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Pin>> PINS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Lift>> LIFTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Step> STEPS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<RavenDebrisEntity>> DEBRIS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> DEBRIS_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> WRATH_START = new ConcurrentHashMap<>();

	static void clearSessionState() {
		HOLDS.clear();
		THROWN.clear();
		PINS.clear();
		LIFTS.clear();
		DASHES.clear();
		STEPS.clear();
		DEBRIS.clear();
		DEBRIS_UNTIL.clear();
		WRATH_START.clear();
	}

	static void forget(UUID id) {
		HOLDS.remove(id);
		THROWN.remove(id);
		PINS.remove(id);
		LIFTS.remove(id);
		DASHES.remove(id);
		STEPS.remove(id);
		DEBRIS.remove(id);
		DEBRIS_UNTIL.remove(id);
		WRATH_START.remove(id);
	}

	/** Death, a new world, a revoke: everything running ends (held things drop, debris shatters). */
	static void endAll(ServerPlayer p) {
		UUID id = p.getUUID();
		Hold h = HOLDS.remove(id);
		if (h != null && h.target instanceof RavenDebrisEntity d && d.isAlive() && d.level() instanceof ServerLevel sl) {
			d.shatter(sl);
		}
		List<RavenDebrisEntity> list = DEBRIS.remove(id);
		if (list != null) {
			for (RavenDebrisEntity d : list) {
				if (d.isAlive() && d.level() instanceof ServerLevel sl) {
					d.shatter(sl);
				}
			}
		}
		THROWN.remove(id);
		PINS.remove(id);
		LIFTS.remove(id);
		DASHES.remove(id);
		STEPS.remove(id);
		DEBRIS_UNTIL.remove(id);
		WRATH_START.remove(id);
	}

	// ---------------------------------------------------------------- shared

	private static boolean ready(ServerPlayer p, String id) {
		if (Raven.meditating(p) && !RavenAbilityManager.MEDITATE.equals(id)) {
			Raven.say(p, "message.projecthero.raven.meditating", ChatFormatting.GRAY);
			return false;
		}
		int cd = Raven.cooldownRemaining(p, id);
		if (cd > 0) {
			Raven.say(p, "message.projecthero.raven.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.raven.ability." + id),
					(cd + 19) / 20);
			return false;
		}
		if (!RavenAbilityManager.allowed(p, id)) {
			Raven.say(p, "message.projecthero.raven.need_rage", ChatFormatting.DARK_RED);
			return false;
		}
		if (!Raven.hasEnergy(p, RavenAbilityManager.costNow(p, id))) {
			Raven.say(p, "message.projecthero.raven.no_energy", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	private static void pay(ServerPlayer p, String id, float cost, int cooldown) {
		Raven.spendEnergy(p, cost);
		if (cooldown > 0) {
			Raven.startCooldown(p, id, cooldown);
		}
		Raven.cast(p);
	}

	static void fx(ServerPlayer p, RavenFxPayload payload) {
		for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
			if (viewer != p && ServerPlayNetworking.canSend(viewer, RavenFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
		if (ServerPlayNetworking.canSend(p, RavenFxPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** The first creature she may harm along her aim within {@code range}, walls in the way. */
	public static LivingEntity aimed(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e), 0.4f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Where her aim lands within {@code range}: a creature, a block, or the end of the range. */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		LivingEntity t = aimed(p, range);
		if (t != null) {
			return t.position().add(0, t.getBbHeight() * 0.55, 0);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
		Raven.sound(p, s, volume, pitch);
	}

	// ---------------------------------------------------------------- taps

	public static boolean use(ServerPlayer p, String id) {
		// Shift+C with debris already orbiting fires one (free, no cooldown check)
		if (RavenAbilityManager.DEBRIS_STORM.equals(id) && debrisCount(p) > 0 && !Raven.meditating(p)) {
			return fireDebris(p);
		}
		if (!ready(p, id)) {
			return false;
		}
		return switch (id) {
			case RavenAbilityManager.DARK_BOLT -> darkBolt(p);
			case RavenAbilityManager.SHADOW_GRASP -> shadowGrasp(p);
			case RavenAbilityManager.SOUL_SELF -> soulSelf(p);
			case RavenAbilityManager.TRIGONS_WRATH -> trigonsWrath(p);
			case RavenAbilityManager.SHADOW_DASH -> shadowDash(p);
			case RavenAbilityManager.SHADOW_STEP -> shadowStep(p);
			case RavenAbilityManager.TELEKINETIC_WAVE -> telekineticWave(p);
			case RavenAbilityManager.DEBRIS_STORM -> debrisStorm(p);
			case RavenAbilityManager.EMPATH_HEAL -> empathHeal(p);
			default -> false;
		};
	}

	/** R: a bolt of black energy (passes through one creature). */
	static boolean darkBolt(ServerPlayer p) {
		DarkBoltEntity.fire(p, p.getViewVector(1f), RavenConfig.BOLT_DAMAGE * Raven.boltMult(p));
		sound(p, RavenSounds.BOLT, 0.35f, 1.5f);
		pay(p, RavenAbilityManager.DARK_BOLT, RavenConfig.BOLT_COST, RavenConfig.BOLT_COOLDOWN);
		Raven.unsettle(p, RavenConfig.BOLT_COMPOSURE);
		return true;
	}

	/** G: claws rise under the creature she looks at and hold it for 2 s. */
	static boolean shadowGrasp(ServerPlayer p) {
		LivingEntity t = aimed(p, RavenConfig.GRASP_RANGE);
		if (t == null) {
			Raven.say(p, "message.projecthero.raven.no_target", ChatFormatting.GRAY);
			return false;
		}
		long now = Raven.now(p);
		Raven.hit(p, t, RavenConfig.GRASP_DAMAGE);
		if (!Raven.isBoss(t)) {
			PINS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Pin(t, t.position(), now + RavenConfig.GRASP_HOLD_TICKS));
		}
		fx(p, new RavenFxPayload(RavenFxPayload.CLAWS, t.getId(), t.position(), new Vec3(t.getBbWidth(), t.getBbHeight(), 0),
				RavenConfig.GRASP_HOLD_TICKS));
		p.level().playSound(null, t.getX(), t.getY(), t.getZ(), RavenSounds.CLAWS, SoundSource.PLAYERS, 0.5f, 0.6f);
		pay(p, RavenAbilityManager.SHADOW_GRASP, RavenConfig.GRASP_COST, RavenConfig.GRASP_COOLDOWN);
		Raven.unsettle(p, RavenConfig.GRASP_COMPOSURE);
		return true;
	}

	/** Shift+G: the raven soul-self flies out along her look. */
	static boolean soulSelf(ServerPlayer p) {
		SoulSelfEntity.release(p);
		sound(p, RavenSounds.SOUL, 0.5f, 0.6f);
		pay(p, RavenAbilityManager.SOUL_SELF, RavenConfig.SOUL_COST, RavenConfig.SOUL_COOLDOWN);
		Raven.unsettle(p, RavenConfig.SOUL_COMPOSURE);
		return true;
	}

	/** Shift+Z (Rage only): Trigon's side breaks through -- a giant cloaked shadow for 15 s. */
	static boolean trigonsWrath(ServerPlayer p) {
		long now = Raven.now(p);
		RavenFlight.stopMeditation(p);
		endHold(p, false);
		RavenState n = Raven.state(p).copy();
		n.wrathUntil = now + RavenConfig.WRATH_TICKS;
		n.composure = 0f;
		n.shield = false;
		Raven.save(p, n);
		WRATH_START.put(p.getUUID(), now);
		Raven.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.WRATH, SoundSource.PLAYERS, 0.5f, 0.7f);
		Raven.darkPuff(level, c, 80, 1.6);
		level.sendParticles(Raven.dust(Raven.RED, 2.0f), c.x, c.y + 1.0, c.z, 30, 1.2, 1.5, 1.2, 0.0);
		pay(p, RavenAbilityManager.TRIGONS_WRATH, 0f, RavenConfig.WRATH_COOLDOWN);
		p.displayClientMessage(Component.translatable("message.projecthero.raven.wrath").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
		return true;
	}

	/** The Wrath ran out: drained -- calm again and no Dark Energy. */
	static void endWrath(ServerPlayer p) {
		WRATH_START.remove(p.getUUID());
		RavenState n = Raven.state(p).copy();
		n.wrathUntil = 0L;
		n.composure = RavenConfig.COMPOSURE_MAX;
		n.energy = 0f;
		Raven.save(p, n);
		Raven.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Raven.darkPuff(level, p.position().add(0, 1.0, 0), 30, 0.8);
		p.displayClientMessage(Component.translatable("message.projecthero.raven.drained").withStyle(ChatFormatting.GRAY), true);
	}

	/** X: a streak of shadow 10 blocks along her look, through creatures, taking no damage. */
	static boolean shadowDash(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 look = p.getViewVector(1f);
		Vec3 to = from;
		// walk out along the look until something solid is in the way
		for (double d = 0.5; d <= RavenConfig.DASH_DISTANCE + 1.0e-6; d += 0.5) {
			Vec3 at = from.add(look.scale(d));
			if (!p.level().noCollision(p, p.getBoundingBox().move(at.subtract(from)))) {
				break;
			}
			to = at;
		}
		long now = Raven.now(p);
		DASHES.put(p.getUUID(), new Dash(from, to, now));
		RavenState n = Raven.state(p).copy();
		n.shadowUntil = now + RavenConfig.DASH_TICKS + 2;
		Raven.save(p, n);
		fx(p, new RavenFxPayload(RavenFxPayload.STREAK, p.getId(), from.add(0, 1.0, 0), to.add(0, 1.0, 0), 14));
		sound(p, RavenSounds.DASH, 0.3f, 0.6f);
		pay(p, RavenAbilityManager.SHADOW_DASH, RavenConfig.DASH_COST, RavenConfig.DASH_COOLDOWN);
		return true;
	}

	/** Shift+X: sink into her shadow and erupt under the creature she looks at; claws launch it. */
	static boolean shadowStep(ServerPlayer p) {
		LivingEntity t = aimed(p, RavenConfig.STEP_RANGE);
		if (t == null) {
			Raven.say(p, "message.projecthero.raven.no_target", ChatFormatting.GRAY);
			return false;
		}
		long now = Raven.now(p);
		STEPS.put(p.getUUID(), new Step(t, now + RavenConfig.STEP_SINK_TICKS));
		RavenState n = Raven.state(p).copy();
		n.shadowUntil = now + RavenConfig.STEP_SINK_TICKS + 4;
		Raven.save(p, n);
		fx(p, new RavenFxPayload(RavenFxPayload.SINK, p.getId(), p.position(), Vec3.ZERO, RavenConfig.STEP_SINK_TICKS + 4));
		sound(p, RavenSounds.DASH, 0.3f, 0.5f);
		pay(p, RavenAbilityManager.SHADOW_STEP, RavenConfig.STEP_COST, RavenConfig.STEP_COOLDOWN);
		Raven.unsettle(p, RavenConfig.STEP_COMPOSURE);
		return true;
	}

	/** C: everything within 8 blocks goes up 4 blocks, hangs for a second, and is slammed down. */
	static boolean telekineticWave(ServerPlayer p) {
		long now = Raven.now(p);
		List<Lift> lifts = LIFTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		for (LivingEntity t : HeroTargets.harmable(p.level(), p, p.getBoundingBox().inflate(RavenConfig.WAVE_RADIUS))) {
			if (t.distanceTo(p) > RavenConfig.WAVE_RADIUS + 0.5) {
				continue;
			}
			lifts.add(new Lift(t, now, RavenConfig.WAVE_RISE_TICKS, RavenConfig.WAVE_HOLD_TICKS, RavenConfig.WAVE_LIFT, RavenConfig.WAVE_DAMAGE, false));
		}
		fx(p, new RavenFxPayload(RavenFxPayload.WAVE, p.getId(), p.position().add(0, 0.1, 0), new Vec3(RavenConfig.WAVE_RADIUS, 0, 0), 16));
		sound(p, RavenSounds.WAVE, 0.4f, 0.7f);
		pay(p, RavenAbilityManager.TELEKINETIC_WAVE, RavenConfig.WAVE_COST, RavenConfig.WAVE_COOLDOWN);
		Raven.unsettle(p, RavenConfig.WAVE_COMPOSURE);
		return true;
	}

	/** Shift+C: tear up 8 nearby blocks into an orbit (energy copies with mob griefing off). */
	static boolean debrisStorm(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		boolean real = RavenRestore.griefing(level);
		BlockPos feet = p.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		int r = RavenConfig.DEBRIS_SEARCH;
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
						continue; // never the ground right under her
					}
					BlockPos pos = feet.offset(dx, dy, dz);
					if (dx * dx + dz * dz > r * r || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
						continue; // only blocks with open air above them (the surface)
					}
					if (real ? RavenRestore.liftable(level, pos) : copyable(level, pos)) {
						found.add(pos);
					}
				}
			}
		}
		if (found.isEmpty()) {
			Raven.say(p, "message.projecthero.raven.no_debris", ChatFormatting.GRAY);
			return false;
		}
		found.sort((a, b) -> Double.compare(a.distSqr(feet), b.distSqr(feet)));
		List<RavenDebrisEntity> list = DEBRIS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		int n = Math.min(RavenConfig.DEBRIS_COUNT, found.size());
		for (int i = 0; i < n; i++) {
			BlockPos pos = found.get(i);
			BlockState state = level.getBlockState(pos);
			if (real) {
				RavenRestore.take(level, pos);
			}
			RavenDebrisEntity d = RavenDebrisEntity.spawn(p, state, Vec3.atBottomCenterOf(pos), !real, RavenDebrisEntity.MODE_ORBIT);
			d.orbitSlot = i;
			list.add(d);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0,
					pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.1);
		}
		DEBRIS_UNTIL.put(p.getUUID(), Raven.now(p) + RavenConfig.DEBRIS_ORBIT_TICKS);
		syncDebris(p);
		sound(p, RavenSounds.DEBRIS, 0.5f, 0.6f);
		sound(p, RavenSounds.GRAB, 0.3f, 0.6f);
		pay(p, RavenAbilityManager.DEBRIS_STORM, RavenConfig.DEBRIS_COST, RavenConfig.DEBRIS_COOLDOWN);
		Raven.unsettle(p, RavenConfig.DEBRIS_COMPOSURE);
		return true;
	}

	/** A block her energy can copy (any solid full block, mob griefing off). */
	private static boolean copyable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return !s.isAir() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level, pos);
	}

	/** Fires one orbiting block at her aim (Shift+C again, or a left click). */
	public static boolean fireDebris(ServerPlayer p) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return false;
		}
		list.removeIf(d -> !d.isAlive() || d.mode != RavenDebrisEntity.MODE_ORBIT);
		if (list.isEmpty()) {
			syncDebris(p);
			return false;
		}
		Vec3 aim = aimPoint(p, 48.0);
		RavenDebrisEntity best = list.get(0);
		double bestDot = -2;
		Vec3 look = p.getViewVector(1f);
		for (RavenDebrisEntity d : list) {
			Vec3 to = d.position().subtract(p.position()).normalize();
			double dot = to.dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		list.remove(best);
		Vec3 from = best.position().add(0, 0.49, 0);
		best.throwAlong(aim.subtract(from), RavenConfig.DEBRIS_SPEED, RavenConfig.DEBRIS_DAMAGE);
		sound(p, RavenSounds.THROW, 0.3f, 1.2f);
		Raven.cast(p);
		syncDebris(p);
		return true;
	}

	public static int debrisCount(Player p) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return 0;
		}
		int n = 0;
		for (RavenDebrisEntity d : list) {
			if (d.isAlive() && d.mode == RavenDebrisEntity.MODE_ORBIT) {
				n++;
			}
		}
		return n;
	}

	private static void syncDebris(ServerPlayer p) {
		int n = debrisCount(p);
		if (Raven.state(p).debris != n) {
			RavenState s = Raven.state(p).copy();
			s.debris = n;
			Raven.save(p, s);
		}
	}

	/** V: take a squadmate's pain (aimed), or heal herself and shake off what ails her. */
	static boolean empathHeal(ServerPlayer p) {
		LivingEntity mate = healTarget(p);
		ServerLevel level = (ServerLevel) p.level();
		if (mate != null) {
			float missing = mate.getMaxHealth() - mate.getHealth();
			float take = Math.min(RavenConfig.HEAL_TAKE_MAX, (float) Math.ceil(missing / 2f));
			take = Math.min(take, Math.max(0f, p.getHealth() - 1f));
			if (take <= 0f) {
				Raven.say(p, missing <= 0f ? "message.projecthero.raven.heal_full" : "message.projecthero.raven.heal_too_weak", ChatFormatting.GRAY);
				return false;
			}
			p.setHealth(p.getHealth() - take);
			p.hurtMarked = true;
			mate.heal(take * 2f);
			Vec3 a = p.position().add(0, 1.1, 0);
			Vec3 b = mate.position().add(0, mate.getBbHeight() * 0.6, 0);
			fx(p, new RavenFxPayload(RavenFxPayload.HEAL, mate.getId(), a, b, 20));
			level.sendParticles(ParticleTypes.END_ROD, b.x, b.y, b.z, 10, 0.3, 0.4, 0.3, 0.02);
			level.sendParticles(Raven.dust(Raven.VOID, 1.0f), a.x, a.y, a.z, 10, 0.3, 0.4, 0.3, 0.0);
			if (mate instanceof ServerPlayer mp) {
				mp.displayClientMessage(Component.translatable("message.projecthero.raven.healed_by", p.getDisplayName())
						.withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			p.displayClientMessage(Component.translatable("message.projecthero.raven.heal_mate", mate.getDisplayName(), Math.round(take))
					.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		} else {
			p.heal(RavenConfig.HEAL_SELF);
			List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
			for (MobEffectInstance e : p.getActiveEffects()) {
				if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					bad.add(e.getEffect());
				}
			}
			bad.forEach(p::removeEffect);
			Vec3 c = p.position().add(0, 1.0, 0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.02);
		}
		sound(p, RavenSounds.HEAL, 0.4f, 1.3f);
		pay(p, RavenAbilityManager.EMPATH_HEAL, RavenConfig.HEAL_COST, RavenConfig.HEAL_COOLDOWN);
		return true;
	}

	/** The squadmate she's aiming at within 16 blocks (null = none). */
	public static LivingEntity healTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(RavenConfig.HEAL_RANGE));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof Player other && other != p && other.isAlive() && Squads.areAllies(p, other), 0.5f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	// ---------------------------------------------------------------- holds

	public static boolean startHold(ServerPlayer p, String id) {
		if (RavenAbilityManager.MEDITATE.equals(id)) {
			if (Raven.inWrath(p)) {
				return false;
			}
			endHold(p, false);
			RavenFlight.startMeditation(p);
			sound(p, RavenSounds.CHANT, 0.3f, 0.6f);
			return true;
		}
		if (!ready(p, id)) {
			return false;
		}
		if (RavenAbilityManager.DARK_SHIELD.equals(id)) {
			RavenState n = Raven.state(p).copy();
			n.shield = true;
			n.shieldHp = RavenConfig.SHIELD_ABSORB;
			Raven.save(p, n);
			Raven.cast(p);
			sound(p, RavenSounds.SHIELD_UP, 0.35f, 1.6f);
			return true;
		}
		if (RavenAbilityManager.TELEKINESIS.equals(id)) {
			return grab(p);
		}
		return false;
	}

	public static void release(ServerPlayer p, String id) {
		switch (id) {
			case RavenAbilityManager.MEDITATE -> RavenFlight.stopMeditation(p);
			case RavenAbilityManager.DARK_SHIELD -> dropShield(p, false);
			case RavenAbilityManager.TELEKINESIS -> endHold(p, true);
			default -> {
			}
		}
	}

	static void dropShield(ServerPlayer p, boolean broken) {
		RavenState s = Raven.state(p);
		if (!s.shield) {
			return;
		}
		RavenState n = s.copy();
		n.shield = false;
		n.shieldHp = 0f;
		Raven.save(p, n);
		if (broken) {
			Raven.startCooldown(p, RavenAbilityManager.DARK_SHIELD, RavenConfig.SHIELD_BROKEN_COOLDOWN);
			sound(p, RavenSounds.SHIELD_BREAK, 0.4f, 0.6f);
			Raven.darkPuff((ServerLevel) p.level(), p.position().add(0, 1.0, 0), 30, 1.5);
		}
	}

	/** The dome takes a hit first: returns what gets through to her. */
	static float shieldAbsorb(ServerPlayer p, float amount) {
		RavenState s = Raven.state(p);
		if (!s.shield || amount <= 0f) {
			return amount;
		}
		float soak = Math.min(amount, s.shieldHp);
		RavenState n = s.copy();
		n.shieldHp = s.shieldHp - soak;
		Raven.save(p, n);
		sound(p, RavenSounds.SHIELD_HIT, 0.4f, 0.8f);
		if (n.shieldHp <= 0.01f) {
			dropShield(p, true);
		}
		return amount - soak;
	}

	/** Shift+R down: wrap what she looks at -- a creature, an item, or a block -- in black energy. */
	static boolean grab(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(RavenConfig.TK_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 stop = block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
		AABB box = p.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, stop, box,
				e -> (e instanceof LivingEntity le && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e)) || e instanceof ItemEntity, 0.5f);
		Entity target = null;
		if (hit != null) {
			if (hit.getEntity() instanceof LivingEntity le && Raven.isBoss(le)) {
				Raven.say(p, "message.projecthero.raven.too_strong", ChatFormatting.GRAY);
				return false;
			}
			target = hit.getEntity();
		} else if (block.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = block.getBlockPos();
			boolean real = RavenRestore.griefing(level);
			if (real ? RavenRestore.liftable(level, pos) : copyable(level, pos)) {
				BlockState state = level.getBlockState(pos);
				if (real) {
					RavenRestore.take(level, pos);
				}
				target = RavenDebrisEntity.spawn(p, state, Vec3.atBottomCenterOf(pos), !real, RavenDebrisEntity.MODE_HELD);
			}
		}
		if (target == null) {
			Raven.say(p, "message.projecthero.raven.nothing_to_lift", ChatFormatting.GRAY);
			return false;
		}
		Hold h = new Hold();
		h.target = target;
		h.dist = Math.max(RavenConfig.TK_MIN_HOLD, Math.min(RavenConfig.TK_MAX_HOLD, eye.distanceTo(target.position())));
		h.start = Raven.now(p);
		HOLDS.put(p.getUUID(), h);
		Raven.spendEnergy(p, RavenConfig.TK_COST);
		RavenState n = Raven.state(p).copy();
		n.tkTarget = target.getId();
		Raven.save(p, n);
		Raven.cast(p);
		if (target instanceof LivingEntity) {
			Raven.unsettle(p, RavenConfig.TK_COMPOSURE);
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		Raven.darkPuff(level, c, 16, 0.5);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.GRAB, SoundSource.PLAYERS, 0.35f, 0.7f);
		return true;
	}

	/** The hold ends: {@code throwIt} throws along her look, otherwise it just lets go. */
	static void endHold(ServerPlayer p, boolean throwIt) {
		Hold h = HOLDS.remove(p.getUUID());
		RavenState s = Raven.state(p);
		if (s.tkTarget >= 0) {
			RavenState n = s.copy();
			n.tkTarget = -1;
			Raven.save(p, n);
		}
		if (h == null) {
			return;
		}
		Raven.startCooldown(p, RavenAbilityManager.TELEKINESIS, RavenConfig.TK_COOLDOWN);
		Entity t = h.target;
		if (t == null || !t.isAlive()) {
			return;
		}
		Vec3 look = p.getViewVector(1f);
		if (t instanceof RavenDebrisEntity d) {
			if (throwIt) {
				d.throwAlong(look, RavenConfig.TK_THROW_SPEED, RavenConfig.TK_THROW_DAMAGE);
			} else {
				d.throwAlong(new Vec3(0, -1, 0), 0.2, RavenConfig.TK_THROW_DAMAGE);
			}
		} else if (throwIt) {
			Vec3 v = look.scale(RavenConfig.TK_THROW_SPEED).add(0, 0.15, 0);
			t.setDeltaMovement(v);
			t.hurtMarked = true;
			if (t instanceof LivingEntity le) {
				THROWN.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Thrown(le));
			}
		}
		if (throwIt) {
			sound(p, RavenSounds.THROW, 0.35f, 0.9f);
			Raven.cast(p);
		}
	}

	/** For the tests: the entity Telekinesis holds (null = none). */
	public static Entity held(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		return h == null ? null : h.target;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		long now = Raven.now(p);
		UUID id = p.getUUID();
		ServerLevel level = (ServerLevel) p.level();
		holdTick(p, now);
		// thrown creatures
		List<Thrown> thrown = THROWN.get(id);
		if (thrown != null) {
			for (Iterator<Thrown> it = thrown.iterator(); it.hasNext();) {
				Thrown t = it.next();
				t.ticks++;
				if (!t.e.isAlive()) {
					it.remove();
					continue;
				}
				double sp = t.e.getDeltaMovement().length();
				boolean landed = t.ticks > 2 && (t.e.horizontalCollision || t.e.onGround() || t.e.verticalCollision);
				if (landed || t.ticks > RavenConfig.TK_THROWN_TICKS) {
					float impact = (float) Math.min(RavenConfig.TK_IMPACT_MAX, Math.max(t.speed, sp) * RavenConfig.TK_IMPACT_PER_SPEED);
					Raven.hit(p, t.e, RavenConfig.TK_THROW_DAMAGE + impact);
					Raven.darkPuff(level, t.e.position().add(0, 0.5, 0), 14, 0.5);
					it.remove();
				} else {
					t.speed = Math.max(t.speed * 0.9, sp);
				}
			}
			if (thrown.isEmpty()) {
				THROWN.remove(id);
			}
		}
		// the Grasp's pins
		List<Pin> pins = PINS.get(id);
		if (pins != null) {
			pins.removeIf(pin -> !pin.e.isAlive() || now >= pin.until);
			for (Pin pin : pins) {
				pin.e.setDeltaMovement(Vec3.ZERO);
				if (pin.e.position().distanceToSqr(pin.at) > 0.01) {
					pin.e.teleportTo(pin.at.x, pin.at.y, pin.at.z);
				}
				pin.e.hurtMarked = true;
			}
			if (pins.isEmpty()) {
				PINS.remove(id);
			}
		}
		liftTick(p, now, level);
		dashTick(p, now);
		stepTick(p, now, level);
		debrisTick(p, now);
		shieldTick(p, level);
		wrathTick(p, now, level);
		// the soft chant while meditating (its words are on her HUD)
		RavenState s = Raven.state(p);
		if (s.meditating && p.tickCount % 30 == 0) {
			int line = (int) ((now / 30) % 3);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), RavenSounds.CHANT, SoundSource.PLAYERS, 0.2f, 0.5f + line * 0.08f);
		}
	}

	private static void holdTick(ServerPlayer p, long now) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null) {
			return;
		}
		Entity t = h.target;
		RavenState s = Raven.state(p);
		boolean creative = p.getAbilities().instabuild;
		if (t == null || !t.isAlive() || t.level() != p.level() || t.distanceTo(p) > RavenConfig.TK_RANGE + 8
				|| now - h.start > RavenConfig.TK_MAX_HOLD_TICKS || (!creative && s.energy <= 0f)) {
			endHold(p, false);
			return;
		}
		if (!creative) {
			s.energy = Math.max(0f, s.energy - RavenConfig.TK_DRAIN_PER_TICK);
		}
		Vec3 point = p.getEyePosition().add(p.getViewVector(1f).scale(h.dist));
		if (t instanceof RavenDebrisEntity d) {
			d.steerTo(point);
		} else {
			Vec3 centre = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 v = point.subtract(centre).scale(0.35);
			if (v.length() > 1.0) {
				v = v.normalize();
			}
			t.setDeltaMovement(v.add(0, t.isNoGravity() ? 0 : 0.08, 0));
			t.hurtMarked = true;
			t.resetFallDistance();
		}
		if (p.tickCount % 2 == 0) {
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			((ServerLevel) p.level()).sendParticles(Raven.dust(Raven.VOID, 1.2f), c.x, c.y, c.z, 3, t.getBbWidth() * 0.6, t.getBbHeight() * 0.5,
					t.getBbWidth() * 0.6, 0.0);
		}
	}

	private static void liftTick(ServerPlayer p, long now, ServerLevel level) {
		List<Lift> lifts = LIFTS.get(p.getUUID());
		if (lifts == null) {
			return;
		}
		for (Lift l : lifts) {
			if (l.done || !l.e.isAlive()) {
				l.done = true;
				continue;
			}
			long age = now - l.start;
			boolean boss = Raven.isBoss(l.e);
			if (age < l.rise + l.hold) {
				if (!boss) {
					double k = Math.min(1.0, (age + 1) / (double) l.rise);
					double wantY = l.baseY + l.height * k;
					AABB moved = l.e.getBoundingBox().move(0, wantY - l.e.getY(), 0);
					if (wantY > l.e.getY() && level.noCollision(l.e, moved)) {
						l.e.teleportTo(l.e.getX(), wantY, l.e.getZ());
					}
					l.e.setDeltaMovement(0, 0, 0);
					l.e.hurtMarked = true;
					l.e.resetFallDistance();
				}
				if (age % 4 == 0) {
					Vec3 c = l.e.position().add(0, l.e.getBbHeight() * 0.5, 0);
					level.sendParticles(Raven.dust(l.wrath ? Raven.RED : Raven.VOID, 1.3f), c.x, c.y, c.z, 4, l.e.getBbWidth() * 0.5,
							l.e.getBbHeight() * 0.5, l.e.getBbWidth() * 0.5, 0.0);
				}
			} else if (l.slamAt < 0) {
				l.slamAt = now;
				if (!boss) {
					// slammed: half way down this tick, onto the ground the next (driven, so no fall damage on top of the slam)
					l.groundY = groundBelow(level, l.e);
					double mid = (l.e.getY() + l.groundY) * 0.5;
					l.e.teleportTo(l.e.getX(), mid, l.e.getZ());
					l.e.setDeltaMovement(0, -1.5, 0);
					l.e.hurtMarked = true;
					l.e.resetFallDistance();
				}
			}
			if (l.slamAt >= 0 && (boss || now - l.slamAt >= 1)) {
				if (!boss) {
					l.e.teleportTo(l.e.getX(), l.groundY, l.e.getZ());
					l.e.setDeltaMovement(0, 0, 0);
					l.e.hurtMarked = true;
				}
				l.e.resetFallDistance();
				Raven.hit(p, l.e, l.damage);
				Vec3 c = l.e.position();
				level.sendParticles(Raven.dust(l.wrath ? Raven.RED : Raven.VOID, 1.8f), c.x, c.y + 0.2, c.z, 20, 0.6, 0.1, 0.6, 0.0);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
				level.playSound(null, c.x, c.y, c.z, l.wrath ? RavenSounds.WRATH_SLAM : RavenSounds.SLAM, SoundSource.PLAYERS, 0.3f, 0.8f);
				if (l.wrath && l.e instanceof ServerPlayer victim) {
					victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, RavenConfig.WRATH_FEAR_TICKS, 0), p);
					victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RavenConfig.WRATH_FEAR_TICKS, 1), p);
					victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RavenConfig.WRATH_FEAR_TICKS, 1), p);
				}
				l.done = true;
			}
		}
		lifts.removeIf(l -> l.done);
		if (lifts.isEmpty()) {
			LIFTS.remove(p.getUUID());
		}
	}

	/** The top of the ground under {@code e} (its feet land there), at most 24 blocks down. */
	private static double groundBelow(ServerLevel level, Entity e) {
		Vec3 from = e.position();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? from.y : hit.getLocation().y;
	}

	private static void dashTick(ServerPlayer p, long now) {
		Dash d = DASHES.get(p.getUUID());
		if (d == null) {
			return;
		}
		long step = now - d.start + 1;
		double k = Math.min(1.0, step / (double) RavenConfig.DASH_TICKS);
		Vec3 at = d.from.add(d.to.subtract(d.from).scale(k));
		p.teleportTo(at.x, at.y, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		Raven.darkPuff((ServerLevel) p.level(), at.add(0, 1.0, 0), 6, 0.3);
		if (k >= 1.0) {
			DASHES.remove(p.getUUID());
		}
	}

	private static void stepTick(ServerPlayer p, long now, ServerLevel level) {
		Step st = STEPS.get(p.getUUID());
		if (st == null) {
			return;
		}
		if (now < st.at) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			level.sendParticles(Raven.dust(Raven.VOID, 1.6f), p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.5, 0.05, 0.5, 0.0);
			return;
		}
		STEPS.remove(p.getUUID());
		LivingEntity t = st.target;
		if (!t.isAlive() || t.level() != p.level() || t.distanceTo(p) > RavenConfig.STEP_RANGE + 6) {
			return;
		}
		// erupt just behind it (from her side), on the ground
		Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
		Vec3 dir = away.lengthSqr() < 1.0e-4 ? p.getViewVector(1f).multiply(1, 0, 1).normalize() : away.normalize();
		Vec3 spot = t.position().add(dir.scale(t.getBbWidth() * 0.5 + 0.9));
		AABB box = p.getBoundingBox().move(spot.subtract(p.position()));
		if (!level.noCollision(p, box)) {
			spot = t.position().subtract(dir.scale(t.getBbWidth() * 0.5 + 0.9));
			if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) {
				spot = t.position();
			}
		}
		Vec3 from = p.position();
		Vec3 face = t.position().subtract(spot);
		float yaw = (float) (Math.toDegrees(Math.atan2(face.z, face.x)) - 90.0);
		p.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), yaw, p.getXRot());
		p.resetFallDistance();
		Raven.hit(p, t, RavenConfig.STEP_DAMAGE);
		if (!Raven.isBoss(t)) {
			t.setDeltaMovement(t.getDeltaMovement().x * 0.2, RavenConfig.STEP_LAUNCH, t.getDeltaMovement().z * 0.2);
			t.hurtMarked = true;
		}
		fx(p, new RavenFxPayload(RavenFxPayload.CLAWS, t.getId(), t.position(), new Vec3(t.getBbWidth(), t.getBbHeight(), 1), 12));
		fx(p, new RavenFxPayload(RavenFxPayload.STREAK, p.getId(), from.add(0, 0.1, 0), spot.add(0, 0.1, 0), 10));
		level.playSound(null, spot.x, spot.y, spot.z, RavenSounds.CLAWS, SoundSource.PLAYERS, 0.5f, 0.5f);
		Raven.darkPuff(level, spot.add(0, 0.5, 0), 24, 0.6);
	}

	private static void debrisTick(ServerPlayer p, long now) {
		List<RavenDebrisEntity> list = DEBRIS.get(p.getUUID());
		if (list == null) {
			return;
		}
		Long until = DEBRIS_UNTIL.get(p.getUUID());
		if (until != null && now >= until) {
			for (RavenDebrisEntity d : list) {
				if (d.isAlive() && d.mode == RavenDebrisEntity.MODE_ORBIT) {
					d.throwAlong(new Vec3(0, -1, 0), 0.3, RavenConfig.DEBRIS_DAMAGE);
				}
			}
			list.clear();
		}
		list.removeIf(d -> !d.isAlive() || d.mode != RavenDebrisEntity.MODE_ORBIT);
		syncDebris(p);
		if (list.isEmpty()) {
			DEBRIS.remove(p.getUUID());
			DEBRIS_UNTIL.remove(p.getUUID());
		}
	}

	private static void shieldTick(ServerPlayer p, ServerLevel level) {
		RavenState s = Raven.state(p);
		if (!s.shield) {
			return;
		}
		if (!p.getAbilities().instabuild) {
			s.energy = Math.max(0f, s.energy - RavenConfig.SHIELD_DRAIN_PER_TICK);
			if (s.energy <= 0f) {
				dropShield(p, false);
				Raven.say(p, "message.projecthero.raven.no_energy", ChatFormatting.GRAY);
				return;
			}
		}
		double r = RavenConfig.SHIELD_RADIUS * p.getScale();
		Vec3 c = p.position().add(0, 1.0, 0);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 1.0),
				e -> e.position().distanceTo(c) <= r + 0.5 && !(e instanceof DarkBoltEntity))) {
			Entity owner = proj.getOwner();
			if (owner == p || (owner != null && Squads.areAllies(p, owner))) {
				continue;
			}
			Vec3 at = proj.position();
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.4f), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
			level.playSound(null, at.x, at.y, at.z, RavenSounds.SHIELD_HIT, SoundSource.PLAYERS, 0.35f, 1.0f);
			proj.discard();
			absorbed++;
		}
	}

	/** For the tests: projectiles swallowed by Dark Shields so far. */
	public static int absorbed;

	private static void wrathTick(ServerPlayer p, long now, ServerLevel level) {
		Long start = WRATH_START.get(p.getUUID());
		if (start == null) {
			return;
		}
		long age = now - start;
		if (age % 5 == 0) {
			Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
			level.sendParticles(Raven.dust(Raven.VOID, 2.5f), c.x, c.y, c.z, 8, p.getBbWidth() * 0.7, p.getBbHeight() * 0.4, p.getBbWidth() * 0.7, 0.0);
		}
		for (int slam : RavenConfig.WRATH_SLAMS) {
			if (age != slam) {
				continue;
			}
			double r = RavenConfig.WRATH_RADIUS;
			List<Lift> lifts = LIFTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
			Vec3 from = p.position().add(0, p.getBbHeight() * 0.45, 0);
			for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(r))) {
				if (t.distanceTo(p) > r + p.getBbWidth()) {
					continue;
				}
				lifts.add(new Lift(t, now, 6, RavenConfig.WRATH_LIFT_TICKS - 6, 3.0, RavenConfig.WRATH_DAMAGE, true));
				fx(p, new RavenFxPayload(RavenFxPayload.TENTACLE, t.getId(), from, Vec3.ZERO, RavenConfig.WRATH_LIFT_TICKS + 8));
			}
			level.playSound(null, p.getX(), p.getY(), p.getZ(), RavenSounds.WRATH_SLAM, SoundSource.PLAYERS, 0.35f, 0.6f);
		}
	}
}
