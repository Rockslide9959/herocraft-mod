package com.projecthero.mod.titanshifter;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanRockEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan revamp): the Beast Titan's rocks. Every rock is a real flying {@link TitanRockEntity} drawn as a
 * cluster of the ground it was torn out of: a boulder (Boulder Throw / Volley) bursts on the first mob or block it meets
 * -- full damage in its radius, shrapnel (half damage) out to twice that, a ring of dust and a spray of real debris
 * chunks; the chunks of Rock Scatter and Pitcher's Fastball each hit what they touch and crumble. While a boulder is in
 * the hand it is not an entity: the Titan's own {@code boulder} bone draws it ({@link TitanFormEntity#FLAG_HOLDING_BOULDER}
 * / {@link TitanFormEntity#FLAG_HOLDING_ROCKS}), so it follows the clip exactly; the entity is born at the release.
 *
 * <p>Ripping the ground up removes a few real blocks only when the terrain-damage config and the mobGriefing gamerule
 * both allow it ({@link TitanKit#chargeMayBreak}); otherwise the rocks are conjured and the ground stays. At most
 * {@link #cap()} rocks fly at once server-wide (cosmetic debris is skipped past it); they are never saved.
 */
public final class TitanBoulders {
	/** Legacy (v0.16.7 dev builds): the old block-display boulders; any still loaded is removed. */
	public static final String TAG = "projecthero_titan_boulder";
	/** The most rocks in flight at once (every Beast together; v0.16.7 Titan fixes: beastTitan.maxRocksInFlight in the config). */
	public static int cap() {
		return Math.max(1, TitanShifterConfig.beastTitan().maxRocksInFlight);
	}
	public static final float BOULDER_GRAVITY = 0.05f;

	private static final List<TitanRockEntity> LIVE = new ArrayList<>();

	private TitanBoulders() {
	}

	public static void initialize() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TitanBoulders::sweep);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.BlockDisplay && entity.getTags().contains(TAG)) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearSessionState());
	}

	/**
	 * A rock that flew into a chunk that is not entity-ticking (past the simulation distance) would hang there frozen:
	 * it is removed instead.
	 */
	private static void sweep(net.minecraft.server.MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		LIVE.removeIf(r -> {
			if (r.isRemoved()) {
				return true;
			}
			if (r.level() instanceof ServerLevel sl && !sl.isPositionEntityTicking(r.blockPosition())) {
				r.discard();
				return true;
			}
			return false;
		});
	}

	/** ServerStateReset: forget every rock (they are never saved, so they went with the world). */
	public static void clearSessionState() {
		LIVE.clear();
	}

	/** Rocks in flight now. */
	public static int liveCount() {
		LIVE.removeIf(TitanRockEntity::isRemoved);
		return LIVE.size();
	}

	/** Adds {@code r} to the world unless {@link #cap()} rocks already fly (a damaging rock always flies). */
	public static boolean spawn(ServerLevel level, TitanRockEntity r) {
		if (liveCount() >= cap() && r.damage() <= 0.0f && !r.isBoulder()) {
			return false;
		}
		if (!level.addFreshEntity(r)) {
			return false;
		}
		LIVE.add(r);
		return true;
	}

	// ---------------- particles ----------------

	/** How far the rocks' particles reach (vanilla stops at 32 blocks; a Titan's fight is seen from further off). */
	private static final double FX_RANGE = 96.0;

	/** {@code ServerLevel#sendParticles}, but sent to every player within {@link #FX_RANGE}. */
	public static void fx(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, y, z) < FX_RANGE * FX_RANGE) {
				level.sendParticles(p, particle, true, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	/** A ring of {@code count} puffs of {@code particle} round {@code c}, rolling outward. */
	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int count) {
		for (int i = 0; i < count; i++) {
			double ang = (Math.PI * 2.0 * i) / count;
			fx(level, particle, c.x + Math.cos(ang) * radius, c.y + 0.2, c.z + Math.sin(ang) * radius, 1, 0.2, 0.1, 0.2, 0.04);
		}
	}

	// ---------------- the stone ----------------

	/** The block the Beast rips up: the ground under it when that is plain natural stuff, else cobblestone. */
	public static BlockState groundState(TitanFormEntity form) {
		ServerLevel level = (ServerLevel) form.level();
		BlockPos below = BlockPos.containing(form.getX(), form.getY() - 0.5, form.getZ());
		return natural(level, below, level.getBlockState(below)) ? level.getBlockState(below) : Blocks.COBBLESTONE.defaultBlockState();
	}

	/** Plain ground: not air, fluid or a block entity, and from dirt-soft to stone-hard. */
	static boolean natural(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(level, pos)) {
			return false;
		}
		float h = s.getDestroySpeed(level, pos);
		return h >= 0.3f && h <= 3.0f;
	}

	private static final BlockState[] STONES = { Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
			Blocks.ANDESITE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
			Blocks.COBBLESTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState() };

	/** One chunk's stone: mostly the ground itself, the rest cobble / stone / dirt. */
	public static BlockState debrisState(BlockState ground, RandomSource random) {
		return random.nextFloat() < 0.55f ? ground : STONES[random.nextInt(STONES.length)];
	}

	/**
	 * Tears up to {@code max} real blocks out of the surface round {@code at} (only when the terrain config and the
	 * mobGriefing gamerule allow it; never under the Titan's own feet). Returns how many went.
	 */
	public static int ripGround(ServerLevel level, TitanFormEntity form, Vec3 at, double radius, int max) {
		if (max <= 0 || !TitanKit.chargeMayBreak(level)) {
			return 0;
		}
		int removed = 0;
		BlockPos c = BlockPos.containing(at);
		int r = (int) Math.ceil(radius);
		RandomSource rnd = level.random;
		java.util.Set<Long> dug = new java.util.HashSet<>();
		for (int tries = 0; tries < max * 4 && removed < max; tries++) {
			int dx = rnd.nextInt(r * 2 + 1) - r, dz = rnd.nextInt(r * 2 + 1) - r;
			// one block a column: a shallow scoop, never a pit
			if (dx * dx + dz * dz > radius * radius || !dug.add(((long) dx << 32) ^ (dz & 0xFFFFFFFFL))) {
				continue;
			}
			// the top solid block of this column, near the given height
			BlockPos top = null;
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos p = c.offset(dx, dy, dz);
				if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
					top = p;
					break;
				}
			}
			if (top == null || form.getBoundingBox().inflate(0.5).contains(Vec3.atCenterOf(top).add(0, 0.5, 0))) {
				continue;
			}
			BlockState s = level.getBlockState(top);
			if (!natural(level, top, s)) {
				continue;
			}
			level.destroyBlock(top, false);
			removed++;
		}
		return removed;
	}

	/** Ground torn up at {@code at}: a burst of the block, a crack of stone and gravel. */
	public static void ripFx(ServerLevel level, Vec3 at, BlockState state, float size) {
		fx(level, new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.3, at.z, (int) (30 * size), size, 0.3,
				size, 0.2);
		fx(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, (int) (3 * size), size * 0.6, 0.1, size * 0.6, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0f, 0.4f);
	}

	// ---------------- the boulder in the hand ----------------

	/**
	 * Where a rock leaves the Beast's right hand at the release of the overarm throw: forward of the right shoulder, at
	 * shoulder height (the {@code boulder_throw} / {@code fastball} release pose).
	 */
	public static Vec3 releasePos(TitanFormEntity form) {
		Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
		Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
		return form.position().add(right.scale(form.getBbWidth() * 0.22)).add(fwd.scale(form.getBbWidth() * 0.55))
				.add(0, form.getBbHeight() * 0.78, 0);
	}

	/** The boulder appears in the hand ({@code rocks}: a handful of stones rather than one boulder) and the ground is torn. */
	public static void pickUp(TitanFormEntity form, boolean rocks, int ripBlocks) {
		ServerLevel level = (ServerLevel) form.level();
		Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
		Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
		Vec3 spot = form.position().add(fwd.scale(form.getBbWidth() * 0.6)).add(right.scale(form.getBbWidth() * 0.25));
		ripFx(level, spot, groundState(form), rocks ? 1.0f : 1.6f);
		ripGround(level, form, spot, rocks ? 1.5 : 2.2, ripBlocks);
		if (rocks) {
			form.setHoldingRocks(true);
		} else {
			form.setHoldingBoulder(true);
		}
	}

	/**
	 * A boulder leaves the hand now toward {@code aim} (an arc that comes down on it) at about {@code speed} blocks a
	 * tick. {@code last}: the hand is empty afterwards.
	 */
	public static TitanRockEntity throwBoulder(TitanFormEntity form, ServerPlayer owner, float scale, float damage, double radius, Vec3 aim,
			double speed, Vec3 from, boolean last) {
		ServerLevel level = (ServerLevel) form.level();
		if (last) {
			form.setHoldingBoulder(false);
			form.setHoldingRocks(false);
		}
		Vec3 dir = aim.subtract(from);
		double dist = Math.max(1.0, dir.length());
		double t = Math.max(4.0, dist / speed);
		// scaled up for the 0.995 air drag so the arc still comes down on the aim
		double k = 0.995;
		Vec3 vel = dir.scale(1.0 / t).add(0, BOULDER_GRAVITY * t * 0.5, 0).scale(t * (1.0 - k) / (1.0 - Math.pow(k, t)));
		TitanRockEntity r = TitanRockEntity.create(level, TitanRockEntity.KIND_BOULDER, groundState(form), scale, from, vel, BOULDER_GRAVITY)
				.thrownBy(owner, form, damage, 1.8, null).boulderRadius(radius).maxAge(100).seed(form.getId());
		spawn(level, r);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.3f);
		return r;
	}

	// ---------------- the burst ----------------

	/**
	 * A boulder bursts at {@code at}: full damage in its radius, shrapnel (half damage) out to twice that, a shockwave of
	 * dust, a spray of real debris chunks and (config) weak blocks broken.
	 */
	public static void impact(TitanRockEntity b, ServerPlayer owner, TitanFormEntity form, Vec3 at) {
		ServerLevel level = (ServerLevel) b.level();
		double radius = Math.max(1.5, b.scale() * 1.3);
		float scale = b.scale();
		BlockState state = b.block();
		fx(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2 + (int) scale, radius * 0.3, 0.3, radius * 0.3, 0.0);
		fx(level, new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, (int) (40 * scale),
				radius * 0.5, 0.6, radius * 0.5, 0.35);
		fx(level, ParticleTypes.POOF, at.x, at.y, at.z, (int) (8 * scale), radius * 0.4, 0.4, radius * 0.4, 0.08);
		// the shockwave: a ring of dust rolling out over the ground
		Vec3 ground = at.add(0, -0.2, 0);
		ring(level, ground, radius * 1.0, ParticleTypes.CAMPFIRE_COSY_SMOKE, (int) (14 * scale));
		ring(level, ground, radius * 1.8, ParticleTypes.POOF, (int) (16 * scale));
		ring(level, ground, radius * 1.3, ParticleTypes.EXPLOSION, 4 + (int) (scale * 2));
		ring(level, ground, radius * 1.4, new BlockParticleOption(ParticleTypes.BLOCK, state), (int) (20 * scale));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 3.0f, 0.6f);
		// the boulder breaks into real chunks of itself
		RandomSource rnd = level.random;
		int chunks = 4 + Math.round(scale * 2.5f);
		for (int i = 0; i < chunks; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double out = 0.25 + rnd.nextDouble() * 0.35;
			Vec3 v = new Vec3(Math.cos(a) * out, 0.35 + rnd.nextDouble() * 0.4, Math.sin(a) * out);
			float s = scale * (0.18f + rnd.nextFloat() * 0.22f);
			spawn(level, TitanRockEntity.create(level, TitanRockEntity.KIND_CHUNK, i % 3 == 0 ? debrisState(state, rnd) : state, s,
					at.add(v.scale(1.5)).add(0, 0.4, 0), v, 0.06f).thrownBy(owner, form, 0.0f, 0.0, null).maxAge(40));
		}
		if (owner != null) {
			double shrapnel = b.isBoulder() ? boulderRadius(b) * 2.0 : radius;
			double core = boulderRadius(b);
			for (LivingEntity e : AbilityHelpers.living(level, at, shrapnel, e -> e != form && e != owner
					&& com.projecthero.mod.combat.HeroTargets.canHarm(owner, e))) {
				boolean inCore = e.getBoundingBox().distanceToSqr(at) <= core * core;
				float base = inCore ? b.damage() : b.damage() * 0.5f;
				if (form != null) {
					TitanCombat.hit(form, owner, e, base, inCore ? 1.8 : 0.8, inCore ? 0.5 : 0.2, at);
				} else {
					float dmg = TitanCombat.isBoss(e) ? Math.min(base, e.getMaxHealth() * (float) TitanShifterConfig.damage().bossMaxFractionPerHit)
							: base;
					AbilityHelpers.hurtBurst(owner, e, dmg);
				}
			}
		}
		TitanCombat.impactBlocks(level, at, boulderRadius(b));
		TitanCombat.shake(level, at, 0.3f + 0.1f * scale, 10);
	}

	private static double boulderRadius(TitanRockEntity b) {
		return b.boulderRadiusOr(Math.max(1.5, b.scale() * 1.3));
	}
}
