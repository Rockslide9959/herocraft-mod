package com.projecthero.mod.miraculous.renarouge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.renarouge.entity.MirageFxEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaFoxEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaIllusionEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaRougeEntityTypes;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the eight illusions of the Mirage wheel, server side -- what each one does to the mobs while it runs
 * ({@link RenaRougeConfig#MIRAGE_TICKS}), and the shatter at the end (an orange flash, every mob it fooled dazed for
 * {@link RenaRougeConfig#MIRAGE_DAZE_TICKS}). See {@link MirageIllusion} and {@code docs/MIRACULOUS_RENA_ROUGE.md}.
 *
 * <p><b>Bosses are never immune</b> (user): they panic, chase the lure, fight the others... A boss that cannot really
 * walk (movement speed at most {@link RenaRougeConfig#MIRAGE_IMMOBILE_SPEED}, e.g. Galactus) stops attacking instead
 * (its target is refused for as long). Target refusals go through {@code PunisherControl.suppressTarget} and
 * {@link #refusesTarget} (both asked by {@code MobMindLockMixin} at {@code Mob#setTarget}), so no goal can race them.
 *
 * <p>Performance: the work runs every 5 or 10 ticks over one box query; the big visuals are a single
 * {@link MirageFxEntity} drawn client-side; particles are count + spread bursts.
 *
 * <p>The running casts are static (keyed by player) and dropped by {@code RenaRougeKit#clearSessionState} / {@code forget};
 * the Pitfall's blocks are in the {@link GeoTerrain} ledger, which also puts them back if the server stops.
 */
public final class RenaMirage {
	/** One running illusion. */
	static final class Cast {
		final UUID owner;
		final MirageIllusion kind;
		final ServerLevel level;
		long until;
		Vec3 center;
		/** Mobs it fooled (entity ids): dazed when it ends. */
		final Set<Integer> affected = new HashSet<>();
		/** The Pitfall's dug cells, and its box (the dug volume). */
		final List<BlockPos> pit = new ArrayList<>();
		@Nullable AABB pitBox;
		int fx = -1;

		Cast(UUID owner, MirageIllusion kind, ServerLevel level, long until, Vec3 center) {
			this.owner = owner;
			this.kind = kind;
			this.level = level;
			this.until = until;
			this.center = center;
		}
	}

	private static final Map<UUID, Cast> RUNNING = new ConcurrentHashMap<>();

	private RenaMirage() {
	}

	static void clearSessionState() {
		RUNNING.clear();
	}

	static void forget(UUID id) {
		RUNNING.remove(id);
	}

	public static boolean running(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c != null && c.until > p.level().getGameTime();
	}

	public static @Nullable MirageIllusion runningKind(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c == null ? null : c.kind;
	}

	/** Game time the running illusion ends (0: none). */
	public static long until(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c == null ? 0L : c.until;
	}

	/** Test hook: the mobs the running illusion has fooled so far. */
	public static Set<Integer> affected(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c == null ? Set.of() : Set.copyOf(c.affected);
	}

	/** Test hook: the Pitfall's dug cells. */
	public static List<BlockPos> pitCells(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c == null ? List.of() : List.copyOf(c.pit);
	}

	/** Test hook: the running illusion's centre. */
	public static @Nullable Vec3 center(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		return c == null ? null : c.center;
	}

	/** Test hook: the running illusion ends on her next tick. */
	public static void endSoonForTest(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		if (c != null) {
			c.until = p.level().getGameTime();
		}
	}

	// ================================================================ helpers

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** She or one of her squad. */
	private static boolean isUs(ServerLevel level, UUID owner, Entity e) {
		if (!(e instanceof Player tp)) {
			return false;
		}
		if (tp.getUUID().equals(owner)) {
			return true;
		}
		Player o = level.getPlayerByUUID(owner);
		return o != null && Squads.areAllies(o, tp);
	}

	/** The mobs an illusion works on round {@code c}: hostile to her (monsters, or anything after her / her squad), bosses too. */
	static List<Mob> enemies(ServerPlayer p, Vec3 c, double r) {
		ServerLevel level = p.serverLevel();
		return level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r, Math.max(8.0, r * 0.5), r),
				m -> m.isAlive() && !(m instanceof RenaIllusionEntity) && !(m instanceof RenaFoxEntity) && m.distanceToSqr(c) <= r * r
						&& (m instanceof Enemy || (m.getTarget() != null && isUs(level, p.getUUID(), m.getTarget()))) && HeroTargets.canHarm(p, m));
	}

	/** A boss that can't really walk (Galactus...): it only stops attacking. */
	static boolean immobile(Mob m) {
		return m.isNoAi() || m.getAttributeValue(Attributes.MOVEMENT_SPEED) <= RenaRougeConfig.MIRAGE_IMMOBILE_SPEED;
	}

	private static void walk(Mob m, Vec3 to, double speed) {
		if (!immobile(m)) {
			m.getNavigation().moveTo(to.x, to.y, to.z, speed);
		}
	}

	private static void drop(Mob m) {
		if (m.getTarget() != null) {
			m.setTarget(null);
		}
		if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** The point on the ground at or under {@code v} (up to 8 blocks down). */
	static Vec3 ground(ServerLevel level, Vec3 v) {
		BlockPos pos = BlockPos.containing(v.x, v.y + 0.5, v.z);
		for (int i = 0; i < 10; i++) {
			BlockPos below = pos.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return new Vec3(v.x, pos.getY(), v.z);
			}
			pos = below;
		}
		return v;
	}

	/** Where she aims an aimed illusion: the block she looks at (or the end of the look) within {@link RenaRougeConfig#MIRAGE_AIM_RANGE}, on the ground. */
	public static Vec3 aimPoint(ServerPlayer p) {
		Vec3 v = AbilityHelpers.aimPoint(p, RenaRougeConfig.MIRAGE_AIM_RANGE);
		return ground(p.serverLevel(), v);
	}

	private static @Nullable MirageFxEntity fx(ServerPlayer p, int kind, Vec3 at, float radius) {
		ServerLevel level = p.serverLevel();
		MirageFxEntity e = RenaRougeEntityTypes.MIRAGE_FX.create(level);
		if (e == null) {
			return null;
		}
		e.setup(p.getUUID(), kind, radius, RenaRougeConfig.MIRAGE_TICKS);
		e.setPos(at.x, at.y, at.z);
		e.setFacing(p.getYRot());
		level.addFreshEntity(e);
		return e;
	}

	// ================================================================ start

	/**
	 * The bubble popped: {@code kind} starts at {@code at} (the pop point) / {@code aim} (where she aimed when she picked it).
	 * The Miraculous countdown has been started by the caller.
	 */
	static void start(ServerPlayer p, MirageIllusion kind, Vec3 at, Vec3 aim) {
		end(p, false);
		ServerLevel level = p.serverLevel();
		long now = now(p);
		Vec3 center = kind.aimed ? aim : p.position();
		Cast c = new Cast(p.getUUID(), kind, level, now + RenaRougeConfig.MIRAGE_TICKS, center);
		RUNNING.put(p.getUUID(), c);
		Miraculous.setVal(p, RenaRouge.V_MIRAGE, c.until);
		Miraculous.setVal(p, RenaRouge.V_MIRAGE_KIND, kind.ordinal() + 1L);
		switch (kind) {
			case FOXES -> startFoxes(p, c);
			case MONSTER -> {
				Vec3 f = flatLook(p);
				Vec3 side = ground(level, p.position().add(-f.z * 4.0, 1.5, f.x * 4.0));
				c.center = side;
				MirageFxEntity e = fx(p, MirageFxEntity.MONSTER, side, (float) RenaRougeConfig.MONSTER_RADIUS);
				c.fx = e == null ? -1 : e.getId();
				level.playSound(null, side.x, side.y, side.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0f, 0.7f);
				level.playSound(null, side.x, side.y, side.z, SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 2.0f, 0.5f);
				monster(p, c);
			}
			case VANISH -> startVanish(p, c);
			case ZONE -> {
				MirageFxEntity e = fx(p, MirageFxEntity.ZONE, center, (float) RenaRougeConfig.ZONE_RADIUS);
				c.fx = e == null ? -1 : e.getId();
				level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
				zone(p, c);
			}
			case LURE -> {
				MirageFxEntity e = fx(p, MirageFxEntity.LURE, center, 2.5f);
				c.fx = e == null ? -1 : e.getId();
				level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 1.2f);
				lure(p, c);
			}
			case PITFALL -> startPit(p, c);
			case ECLIPSE -> {
				MirageFxEntity e = fx(p, MirageFxEntity.ECLIPSE, center, (float) RenaRougeConfig.ECLIPSE_RADIUS);
				c.fx = e == null ? -1 : e.getId();
				level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.4f);
				eclipse(p, c);
			}
			case MIRRORS -> {
				MirageFxEntity e = fx(p, MirageFxEntity.MIRRORS, center, 7f);
				c.fx = e == null ? -1 : e.getId();
				for (Mob m : enemies(p, center, RenaRougeConfig.MIRRORS_RADIUS)) {
					c.affected.add(m.getId());
					if (isUs(level, p.getUUID(), m.getTarget())) {
						drop(m);
					}
				}
				level.playSound(null, center.x, center.y, center.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 2.0f, 0.8f);
				mirrors(p, c);
			}
		}
	}

	/** 1. Army of Foxes: the classic Mirage (copies, wall, confusion) and the foxes leaping out of the light. */
	private static void startFoxes(ServerPlayer p, Cast c) {
		RenaRougeMoves.classicMirage(p);
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		for (int i = 0; i < RenaRougeConfig.FOX_COUNT; i++) {
			RenaFoxEntity fox = RenaRougeEntityTypes.FOX.create(level);
			if (fox == null) {
				continue;
			}
			double a = Math.atan2(f.z, f.x) + Math.PI * 2 * i / RenaRougeConfig.FOX_COUNT;
			Vec3 spot = ground(level, p.position().add(Math.cos(a) * 1.8, 1.0, Math.sin(a) * 1.8));
			if (!level.noCollision(fox, fox.getType().getDimensions().makeBoundingBox(spot))) {
				spot = p.position();
			}
			float yaw = (float) Math.toDegrees(Math.atan2(spot.z - p.getZ(), spot.x - p.getX())) - 90f;
			fox.moveTo(spot.x, spot.y, spot.z, yaw, 0f);
			fox.setYHeadRot(yaw);
			fox.setup(p, RenaRougeConfig.MIRAGE_TICKS);
			// the leap out
			fox.setDeltaMovement(Math.cos(a) * 0.35, 0.42, Math.sin(a) * 0.35);
			level.addFreshEntity(fox);
		}
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, p.getX(), p.getY() + 0.5, p.getZ(), 30, 1.5, 0.3, 1.5, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FOX_AGGRO, SoundSource.PLAYERS, 1.5f, 1.0f);
	}

	/** 3. Vanishing Act: the mobs lose her and her squad; copies of her sprint off to draw them away. */
	private static void startVanish(ServerPlayer p, Cast c) {
		ServerLevel level = p.serverLevel();
		List<RenaIllusionEntity> runners = new ArrayList<>();
		Vec3 f = flatLook(p);
		for (int i = 0; i < RenaRougeConfig.VANISH_RUNNERS; i++) {
			double a = Math.atan2(f.z, f.x) + Math.PI + (i - (RenaRougeConfig.VANISH_RUNNERS - 1) / 2.0) * 1.6;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f;
			RenaIllusionEntity r = RenaRougeIllusions.spawn(p, RenaIllusionEntity.RUNNER, p.position(), yaw, RenaRougeConfig.MIRAGE_TICKS,
					RenaRougeConfig.MIRAGE_HITS);
			if (r != null) {
				r.runFrom(dir);
				runners.add(r);
			}
		}
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RenaRougeConfig.VANISH_RADIUS),
				m -> m.isAlive() && m.getTarget() != null && isUs(level, p.getUUID(), m.getTarget()))) {
			c.affected.add(m.getId());
			drop(m);
			m.getNavigation().stop();
			if (!runners.isEmpty()) {
				RenaIllusionEntity r = runners.get(level.random.nextInt(runners.size()));
				if (RenaRougeCombat.isBoss(m)) {
					RenaRougeIllusions.taunt(m, r);
				} else if (RenaRougeCombat.isFoolable(p, m)) {
					RenaRougeIllusions.retarget(m, r);
				}
			}
		}
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.2, 1.0, 1.2, 0.0);
		level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.8, 0.8, 0.8, 0.02);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.5f, 1.2f);
	}

	/**
	 * 6. Pitfall: a real {@link RenaRougeConfig#PIT_SIZE}-square pit, {@link RenaRougeConfig#PIT_DEPTH} deep, dug at the aim
	 * through the {@link GeoTerrain} ledger (natural ground only -- never a container, block entity or bedrock -- with
	 * her build rights, spawn protection and claims respected), never under her or a squadmate; drawn over by an
	 * illusory floor.
	 */
	private static void startPit(ServerPlayer p, Cast c) {
		ServerLevel level = p.serverLevel();
		int half = RenaRougeConfig.PIT_SIZE / 2;
		BlockPos base = BlockPos.containing(c.center); // the air cell over the ground
		int top = base.getY() - 1;
		BlockState look = level.getBlockState(base.below());
		if (look.isAir() || !look.isSolidRender(level, base.below())) {
			look = Blocks.GRASS_BLOCK.defaultBlockState();
		}
		int minX = base.getX() - half, minZ = base.getZ() - half;
		List<Player> us = new ArrayList<>();
		for (Player pl : level.players()) {
			if (isUs(level, p.getUUID(), pl)) {
				us.add(pl);
			}
		}
		for (int x = minX; x < minX + RenaRougeConfig.PIT_SIZE; x++) {
			for (int z = minZ; z < minZ + RenaRougeConfig.PIT_SIZE; z++) {
				AABB column = new AABB(x, top - RenaRougeConfig.PIT_DEPTH, z, x + 1, top + 3, z + 1);
				boolean ours = false;
				for (Player pl : us) {
					if (pl.getBoundingBox().intersects(column)) {
						ours = true;
					}
				}
				if (ours) {
					continue; // never under her or a squadmate
				}
				for (int d = 0; d < RenaRougeConfig.PIT_DEPTH; d++) {
					BlockPos pos = new BlockPos(x, top - d, z);
					if (!GeoTerrain.canEdit(p, level, pos) || !GeoTerrain.diggable(level, pos)
							|| !GeoTerrain.pocket(level, pos, null, RenaRougeConfig.MIRAGE_TICKS + 200, 0)) {
						break;
					}
					c.pit.add(pos.immutable());
				}
			}
		}
		if (c.pit.isEmpty()) {
			Miraculous.say(p, "message.projecthero.rena_rouge.pit_refused", ChatFormatting.GOLD);
		}
		c.pitBox = new AABB(minX, top - RenaRougeConfig.PIT_DEPTH + 1, minZ, minX + RenaRougeConfig.PIT_SIZE, top + 1, minZ + RenaRougeConfig.PIT_SIZE);
		c.center = new Vec3(minX + RenaRougeConfig.PIT_SIZE / 2.0, top + 1, minZ + RenaRougeConfig.PIT_SIZE / 2.0);
		MirageFxEntity e = fx(p, MirageFxEntity.PIT, new Vec3(minX, top, minZ), RenaRougeConfig.PIT_SIZE);
		if (e != null) {
			e.setPit(look, RenaRougeConfig.PIT_SIZE);
			c.fx = e.getId();
		}
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, c.center.x, c.center.y + 0.2, c.center.z, 30, 1.2, 0.1, 1.2, 0.0);
		level.playSound(null, c.center.x, c.center.y, c.center.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
		pit(p, c);
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		Cast c = RUNNING.get(p.getUUID());
		if (c == null) {
			return;
		}
		long now = now(p);
		if (c.level != p.level() || now >= c.until) {
			end(p, true);
			return;
		}
		switch (c.kind) {
			case FOXES -> {
			}
			case MONSTER -> {
				if (now % 10 == 0) {
					monster(p, c);
				}
			}
			case VANISH -> {
				if (now % 10 == 0) {
					vanish(p, c);
				}
			}
			case ZONE -> {
				if (now % 5 == 0) {
					zone(p, c);
				}
			}
			case LURE -> {
				if (now % 10 == 0) {
					lure(p, c);
				}
			}
			case PITFALL -> {
				liftUsOut(p, c);
				if (now % 10 == 0) {
					pit(p, c);
				}
			}
			case ECLIPSE -> {
				c.center = p.position();
				if (c.level.getEntity(c.fx) instanceof MirageFxEntity e) {
					e.setPos(c.center.x, c.center.y, c.center.z);
				}
				if (now % 10 == 0) {
					eclipse(p, c);
				}
			}
			case MIRRORS -> mirrors(p, c); // every tick, but it only searches for a mob whose fight was broken off
		}
	}

	/** 2. Monster: everything within {@link RenaRougeConfig#MONSTER_RADIUS} panics and runs from it. */
	private static void monster(ServerPlayer p, Cast c) {
		for (Mob m : enemies(p, c.center, RenaRougeConfig.MONSTER_RADIUS)) {
			c.affected.add(m.getId());
			PunisherControl.suppressTarget(m, 25);
			Vec3 away = m.position().subtract(c.center);
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : away.normalize();
			walk(m, m.position().add(away.scale(RenaRougeConfig.MONSTER_FLEE)), 1.5);
		}
	}

	/** 3. Vanishing Act, every half second: whatever still holds her (or a squadmate) lets go. */
	private static void vanish(ServerPlayer p, Cast c) {
		ServerLevel level = p.serverLevel();
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RenaRougeConfig.VANISH_RADIUS),
				m -> m.isAlive() && m.getTarget() != null && isUs(level, p.getUUID(), m.getTarget()) && !mayRetaliate(m, m.getTarget()))) {
			c.affected.add(m.getId());
			drop(m);
		}
	}

	private static boolean mayRetaliate(Mob m, LivingEntity target) {
		return m.getLastHurtByMob() == target && m.tickCount - m.getLastHurtByMobTimestamp() < RenaRougeConfig.VANISH_RETALIATE_TICKS;
	}

	/** 4. Mirage Zone: mobs inside try to leave; mobs outside won't walk in. */
	private static void zone(ServerPlayer p, Cast c) {
		double r = RenaRougeConfig.ZONE_RADIUS;
		for (Mob m : enemies(p, c.center, r + 4.0)) {
			Vec3 flat = new Vec3(m.getX() - c.center.x, 0, m.getZ() - c.center.z);
			double d = flat.length();
			Vec3 out = d < 1.0e-3 ? new Vec3(1, 0, 0) : flat.scale(1.0 / d);
			if (d < r) {
				c.affected.add(m.getId());
				PunisherControl.suppressTarget(m, 15);
				walk(m, c.center.add(out.scale(r + 3.0)), 1.4);
				continue;
			}
			// outside: a path that runs into the fire is dropped, and so is a chase into it
			boolean in = false;
			Path path = m.getNavigation().getPath();
			if (path != null) {
				for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 6); i++) {
					var n = path.getNode(i);
					double dx = n.x + 0.5 - c.center.x, dz = n.z + 0.5 - c.center.z;
					if (dx * dx + dz * dz < r * r) {
						in = true;
						break;
					}
				}
			}
			Vec3 v = m.getDeltaMovement();
			if (!in && d < r + 1.5 && v.x * out.x + v.z * out.z < -0.01) {
				in = true;
			}
			if (in) {
				c.affected.add(m.getId());
				m.getNavigation().stop();
				PunisherControl.suppressTarget(m, 15);
				m.setDeltaMovement(out.x * 0.15, v.y, out.z * 0.15);
			}
		}
	}

	/** 5. Golden Lure: every mob within {@link RenaRougeConfig#GOLD_RADIUS} drops its target and crowds round the prize. */
	private static void lure(ServerPlayer p, Cast c) {
		for (Mob m : enemies(p, c.center, RenaRougeConfig.GOLD_RADIUS)) {
			c.affected.add(m.getId());
			PunisherControl.suppressTarget(m, 25);
			if (m.distanceToSqr(c.center) > 2.5 * 2.5) {
				walk(m, c.center, 1.2);
			} else {
				m.getNavigation().stop();
				m.getLookControl().setLookAt(c.center.x, c.center.y + 0.5, c.center.z);
			}
		}
	}

	/** 6. Pitfall: the mobs nearby make for the "floor" -- straight across it, and in. */
	private static void pit(ServerPlayer p, Cast c) {
		if (c.pitBox == null || c.pit.isEmpty()) {
			return;
		}
		AABB box = c.pitBox;
		for (Mob m : enemies(p, c.center, RenaRougeConfig.PIT_STEER_RADIUS)) {
			if (m.getBoundingBox().intersects(box)) {
				c.affected.add(m.getId()); // in: let it rage down there
				continue;
			}
			c.affected.add(m.getId());
			PunisherControl.suppressTarget(m, 15);
			walk(m, new Vec3(c.center.x, box.minY, c.center.z), 1.2);
			// at the edge: the "floor" takes it
			double ex = Math.max(box.minX - m.getX(), Math.max(0, m.getX() - box.maxX));
			double ez = Math.max(box.minZ - m.getZ(), Math.max(0, m.getZ() - box.maxZ));
			if (ex < 1.2 && ez < 1.2 && !immobile(m)) {
				Vec3 to = new Vec3(c.center.x - m.getX(), 0, c.center.z - m.getZ()).normalize().scale(0.25);
				m.setDeltaMovement(to.x, m.getDeltaMovement().y, to.z);
			}
		}
	}

	/** 6. Pitfall: she and her squad never fall into it (lifted back out onto the rim). */
	private static void liftUsOut(ServerPlayer p, Cast c) {
		if (c.pitBox == null || c.pit.isEmpty()) {
			return;
		}
		AABB box = c.pitBox;
		for (Player pl : c.level.getEntitiesOfClass(Player.class, box.inflate(0.0, 0.0, 0.0), pl -> isUs(c.level, p.getUUID(), pl))) {
			if (pl.getY() >= box.maxY - 0.01 || !(pl instanceof ServerPlayer sp)) {
				continue;
			}
			Vec3 flat = new Vec3(pl.getX() - c.center.x, 0, pl.getZ() - c.center.z);
			flat = flat.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : flat.normalize();
			double out = RenaRougeConfig.PIT_SIZE / 2.0 + 0.8;
			Vec3 rim = new Vec3(c.center.x + flat.x * out, box.maxY, c.center.z + flat.z * out);
			sp.teleportTo(c.level, rim.x, rim.y, rim.z, sp.getYRot(), sp.getXRot());
			sp.resetFallDistance();
			c.level.sendParticles(RenaRougeCombat.ORANGE, rim.x, rim.y + 0.5, rim.z, 8, 0.3, 0.4, 0.3, 0.0);
		}
	}

	/** 7. Eclipse: enemies inside the night lose anything farther than {@link RenaRougeConfig#ECLIPSE_SIGHT}. */
	private static void eclipse(ServerPlayer p, Cast c) {
		double s = RenaRougeConfig.ECLIPSE_SIGHT;
		for (Mob m : enemies(p, c.center, RenaRougeConfig.ECLIPSE_RADIUS)) {
			c.affected.add(m.getId());
			LivingEntity t = m.getTarget();
			if (t != null && m.distanceToSqr(t) > s * s) {
				drop(m);
				m.getNavigation().stop();
			}
		}
	}

	/** 8. Hall of Mirrors: every mirrored mob is on another mirrored mob, all the time. */
	private static void mirrors(ServerPlayer p, Cast c) {
		ServerLevel level = p.serverLevel();
		List<Mob> mobs = new ArrayList<>();
		for (int id : c.affected) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				mobs.add(m);
			}
		}
		c.affected.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
		if (mobs.size() < 2) {
			for (Mob m : mobs) {
				PunisherControl.suppressTarget(m, RenaRougeConfig.MIRRORS_RETHINK + 2); // nobody else to see as her
			}
			return;
		}
		for (Mob m : mobs) {
			LivingEntity t = m.getTarget();
			if (t instanceof Mob tm && tm.isAlive() && c.affected.contains(tm.getId()) && tm != m && tm.distanceToSqr(m) < 24 * 24) {
				continue;
			}
			Mob other = mobs.stream().filter(o -> o != m).min(Comparator.comparingDouble(o -> o.distanceToSqr(m))).orElse(null);
			if (other == null) {
				continue;
			}
			if (RenaRougeCombat.isBoss(m)) {
				RenaRougeIllusions.taunt(m, other);
				if (m.getTarget() != other) {
					m.setTarget(other);
				}
			} else {
				RenaRougeIllusions.retarget(m, other);
			}
		}
		if (level.getGameTime() % 20 == 0) {
			for (Mob m : mobs) {
				level.sendParticles(RenaRougeCombat.ORANGE, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 3, 0.25, 0.1, 0.25, 0.0);
			}
		}
	}

	// ================================================================ the end

	/**
	 * The illusion ends: everything it made goes (the beast, the fire, the foxes, the runners...), the Pitfall fills
	 * back exactly, and with {@code shatter} it breaks in an orange flash and every mob it fooled is dazed (slow, no
	 * target) for {@link RenaRougeConfig#MIRAGE_DAZE_TICKS}.
	 */
	public static void end(ServerPlayer p, boolean shatter) {
		Cast c = RUNNING.remove(p.getUUID());
		if (c == null) {
			return;
		}
		ServerLevel level = c.level;
		if (Miraculous.holds(p, Kwami.TRIXX)) {
			Miraculous.setVal(p, RenaRouge.V_MIRAGE, 0L);
			Miraculous.setVal(p, RenaRouge.V_MIRAGE_KIND, 0L);
		}
		if (c.fx >= 0 && level.getEntity(c.fx) instanceof MirageFxEntity e) {
			e.discard();
		}
		RenaRougeMoves.stopConfusion(p);
		AABB all = p.getBoundingBox().inflate(256.0);
		for (RenaFoxEntity f : level.getEntitiesOfClass(RenaFoxEntity.class, all, f -> p.getUUID().equals(f.getOwnerUUID()))) {
			f.pop();
		}
		for (RenaIllusionEntity i : level.getEntitiesOfClass(RenaIllusionEntity.class, all, i -> p.getUUID().equals(i.getOwnerUUID())
				&& (i.kind() == RenaIllusionEntity.MIRAGE || i.kind() == RenaIllusionEntity.RUNNER))) {
			i.dissolve();
		}
		// the pit fills back bottom-up (whatever fell in is lifted out layer by layer)
		List<BlockPos> cells = new ArrayList<>(c.pit);
		cells.sort(Comparator.comparingInt(BlockPos::getY));
		for (BlockPos pos : cells) {
			GeoTerrain.restoreNow(level, pos);
		}
		if (!shatter) {
			return;
		}
		Vec3 at = c.center;
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, at.x, at.y + 1.0, at.z, 60, 2.5, 1.2, 2.5, 0.0);
		level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 1.0, at.z, 30, 2.5, 1.2, 2.5, 1.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.2f, 0.7f);
		for (int id : c.affected) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				daze(m);
			}
		}
	}

	/** Dazed for {@link RenaRougeConfig#MIRAGE_DAZE_TICKS}: Slowness II, no target. */
	static void daze(Mob m) {
		AbilityHelpers.applyControl(m, MobEffects.MOVEMENT_SLOWDOWN, RenaRougeConfig.MIRAGE_DAZE_TICKS, 1);
		PunisherControl.suppressTarget(m, RenaRougeConfig.MIRAGE_DAZE_TICKS);
		m.getNavigation().stop();
		if (m.level() instanceof ServerLevel sl) {
			sl.sendParticles(RenaRougeCombat.ORANGE, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
		}
	}

	// ================================================================ hooks

	/**
	 * Asked by {@code MobMindLockMixin} for every {@code Mob#setTarget} (free while no illusion runs): Vanishing Act --
	 * she and her squad can't be picked (unless one of them just hit this mob); Hall of Mirrors -- a mirrored mob can't
	 * pick them; Eclipse -- a mob inside the night can't pick anything farther than {@link RenaRougeConfig#ECLIPSE_SIGHT}.
	 */
	public static boolean refusesTarget(Mob mob, LivingEntity target) {
		if (RUNNING.isEmpty() || target == null || mob instanceof RenaIllusionEntity || mob instanceof RenaFoxEntity) {
			return false;
		}
		for (Cast c : RUNNING.values()) {
			if (c.level != mob.level()) {
				continue;
			}
			switch (c.kind) {
				case VANISH -> {
					if (isUs(c.level, c.owner, target) && !mayRetaliate(mob, target)) {
						return true;
					}
				}
				case MIRRORS -> {
					if (c.affected.contains(mob.getId()) && isUs(c.level, c.owner, target)) {
						return true;
					}
				}
				case ECLIPSE -> {
					double r = RenaRougeConfig.ECLIPSE_RADIUS;
					double s = RenaRougeConfig.ECLIPSE_SIGHT;
					if (mob.distanceToSqr(c.center) <= r * r && mob.distanceToSqr(target) > s * s && !isOwnedBy(target, c.owner)) {
						return true;
					}
				}
				default -> {
				}
			}
		}
		return false;
	}

	private static boolean isOwnedBy(Entity e, UUID owner) {
		UUID o = HeroTargets.ownerOf(e);
		return o != null && o.equals(owner);
	}

	/** {@code ENTITY_LOAD}: a mob's arrow / fireball loosed inside an Eclipse flies off course. */
	public static void onEntityLoad(Entity e) {
		if (RUNNING.isEmpty() || !(e instanceof Projectile pr) || !(pr.getOwner() instanceof Mob m) || m instanceof RenaFoxEntity) {
			return;
		}
		for (Cast c : RUNNING.values()) {
			if (c.kind != MirageIllusion.ECLIPSE || c.level != e.level()) {
				continue;
			}
			double r = RenaRougeConfig.ECLIPSE_RADIUS;
			if (m.distanceToSqr(c.center) > r * r) {
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			double sp = v.length();
			if (sp < 1.0e-3) {
				return;
			}
			double max = Math.toRadians(RenaRougeConfig.ECLIPSE_SCATTER_DEGREES);
			double yaw = Math.atan2(v.z, v.x) + (m.getRandom().nextDouble() * 2 - 1) * max;
			double pitch = Math.asin(Math.max(-1, Math.min(1, v.y / sp))) + (m.getRandom().nextDouble() * 2 - 1) * max * 0.5;
			pr.setDeltaMovement(Math.cos(pitch) * Math.cos(yaw) * sp, Math.sin(pitch) * sp, Math.cos(pitch) * Math.sin(yaw) * sp);
			return;
		}
	}

	/** Her swing (a strike, the sweep, a melee hit): the Mirage copies swing with her. */
	public static void onSwing(ServerPlayer p) {
		if (!running(p) || runningKind(p) != MirageIllusion.FOXES) {
			return;
		}
		for (RenaIllusionEntity e : RenaRougeIllusions.own(p, 48.0)) {
			if (e.kind() == RenaIllusionEntity.MIRAGE) {
				e.mimicSwing(p);
			}
		}
	}
}
