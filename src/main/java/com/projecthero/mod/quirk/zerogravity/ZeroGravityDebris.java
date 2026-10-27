package com.projecthero.mod.quirk.zerogravity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.math.Transformation;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.16.7 revamp: every BLOCK Zero Gravity floats -- a touched block, the Meteor Shower's rubble cloud, the Debris Shield --
 * and what happens when it comes down. Every piece is a vanilla block DISPLAY (a falling-block look with no physics of its
 * own, so it can never place a block or drop an item), moved here every tick:
 * <ul>
 *   <li>{@link #HELD}: a touched block hanging where she lifted it; {@link #CLOUD}: the Meteor Shower's rubble, risen into a
 *       cloud high over her that drifts toward where she aims; {@link #SHIELD}: orbiting her, stopping projectiles;</li>
 *   <li>{@link #FALL}: let go (Release): it drops under gravity and smashes on whatever it lands on; {@link #THROWN}: Comet
 *       Toss -- it flies along her look and smashes into the first creature or block.</li>
 * </ul>
 * The ground a piece came from is emptied through the {@link GeoTerrain} ledger (only with the terrain-damage config and
 * {@code mobGriefing} on, where she may build; rubble only from natural ground, a touched block any plain full block with no
 * block entity) and put back EXACTLY as it was the moment the piece smashes or is dissolved ({@link GeoTerrain#restoreNow}),
 * on its own safety clock otherwise, and on server stop. Without permission the rubble is only a copy of the ground and nothing
 * is taken; a touched block is refused.
 *
 * <p>The display entities carry {@link ZeroGravity#TAG_DEBRIS}: one found on load that no session owns (the server stopped
 * mid-move) is removed ({@link #cleanup}).
 */
public final class ZeroGravityDebris {
	public static final int HELD = 0;
	public static final int CLOUD = 1;
	public static final int SHIELD = 2;
	public static final int FALL = 3;
	public static final int THROWN = 4;
	/** v0.17.1 Crane: a touched block on its way to where she pointed, to be set down there (placed, not broken). */
	public static final int PLACE = 5;
	/** v0.17.1 Gravity Bomb: part of the 5 x 3 x 5 slab, hanging overhead / falling on her aim as one body. */
	public static final int SLAB = 6;
	public static final int SLAB_FALL = 7;
	/** v0.17.1 Full Release: rubble risen over where it was torn up, hanging until the clap. */
	public static final int LIFT = 8;

	static final class Piece {
		final int id;
		final UUID uuid;
		final BlockState state;
		int kind;
		final int slot;
		final long born;
		long kindSince;
		final float scale;
		Vec3 pos;
		Vec3 vel = Vec3.ZERO;
		/** Where it was lifted from (refilled when it smashes); null = a copy, nothing was taken. */
		@Nullable BlockPos origin;
		/** HELD / LIFT: where it hangs; SLAB: its place in the slab (from the slab's centre). */
		Vec3 home = Vec3.ZERO;
		float damage;
		/** PLACE: the cell it is set down in. */
		@Nullable BlockPos placeAt;

		Piece(Display.BlockDisplay d, BlockState state, int kind, int slot, long born, float scale) {
			this.id = d.getId();
			this.uuid = d.getUUID();
			this.state = state;
			this.kind = kind;
			this.slot = slot;
			this.born = born;
			this.kindSince = born;
			this.scale = scale;
			this.pos = d.position();
		}
	}

	static final class Session {
		final List<Piece> pieces = new ArrayList<>();
		long shieldUntil;
		long cloudUntil;
		Vec3 cloudCenter = Vec3.ZERO;
		final Set<Integer> hit = new HashSet<>();
		/** v0.17.1 Gravity Bomb: the slab hangs until (0 = no slab), its centre, its fall. */
		long bombUntil;
		Vec3 bombCenter = Vec3.ZERO;
		Vec3 bombVel = Vec3.ZERO;
		Vec3 bombTarget = Vec3.ZERO;
		boolean bombFalling;
		long bombFallSince;
		/** The last meteor rumble (one every few ticks, however much lands). */
		long lastRumble;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** Every live piece's display UUID (anything tagged but not here is an orphan). */
	private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();

	private ZeroGravityDebris() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
		LIVE.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	// ================================================================ queries

	/** How many pieces she has (every kind). */
	public static int count(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.pieces.size();
	}

	/** How many pieces of {@code kind}. */
	public static int count(ServerPlayer p, int kind) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		int n = 0;
		for (Piece pc : s.pieces) {
			if (pc.kind == kind) {
				n++;
			}
		}
		return n;
	}

	/**
	 * How many pieces she holds up (the Nausea weighs them): everything not yet falling or thrown. (v0.17.1: the Gravity Bomb's
	 * slab and the Full Release rubble are not counted piece by piece -- each costs one sting when it goes up.)
	 */
	public static int held(ServerPlayer p) {
		return count(p, HELD) + count(p, CLOUD) + count(p, SHIELD);
	}

	/** v0.17.1: the Gravity Bomb's slab is up (hanging or falling). */
	public static boolean bombUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.bombUntil != 0L && (count(p, SLAB) > 0 || count(p, SLAB_FALL) > 0);
	}

	public static boolean shieldUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.shieldUntil > p.level().getGameTime() && count(p, SHIELD) > 0;
	}

	public static boolean cloudUp(ServerPlayer p) {
		return count(p, CLOUD) > 0;
	}

	/** The display entities of her live pieces (tests, FX). */
	public static List<Display.BlockDisplay> displays(ServerPlayer p) {
		List<Display.BlockDisplay> out = new ArrayList<>();
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return out;
		}
		for (Piece pc : s.pieces) {
			if (p.serverLevel().getEntity(pc.id) instanceof Display.BlockDisplay d) {
				out.add(d);
			}
		}
		return out;
	}

	// ================================================================ the ground

	/** She may take real ground here (the terrain-damage config, {@code mobGriefing}, build rights). */
	static boolean mayTake(ServerPlayer p, BlockPos pos) {
		ServerLevel level = p.serverLevel();
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && GeoTerrain.canEdit(p, level, pos);
	}

	/** A block a touch can lift: a plain full block, breakable, not too hard, no block entity, no fluid, not one of the ledger's. */
	static boolean liftable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		float hard = s.getDestroySpeed(level, pos);
		return !s.isAir() && !s.hasBlockEntity() && s.getFluidState().isEmpty() && hard >= 0f && hard <= ZeroGravityConfig.BLOCK_MAX_HARDNESS
				&& s.isCollisionShapeFullBlock(level, pos) && !GeoTerrain.isTemp(level, pos);
	}

	/**
	 * The loose top block of the column at {@code column} (searching a few blocks up and down for the surface): its state and,
	 * when it was really taken, its position. Null if there is no natural ground there.
	 */
	static @Nullable Object[] takeSurface(ServerPlayer p, BlockPos column, int ttl) {
		ServerLevel level = p.serverLevel();
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos pos = column.offset(0, dy, 0);
			BlockState above = level.getBlockState(pos.above());
			if (!(above.isAir() || above.canBeReplaced()) || !above.getFluidState().isEmpty()) {
				continue;
			}
			if (!GeoTerrain.diggable(level, pos) || GeoTerrain.isTemp(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			// never the block anything is standing on
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(-0.05, 0.0, -0.05)).isEmpty()) {
				return null;
			}
			BlockPos taken = null;
			if (mayTake(p, pos) && GeoTerrain.pocket(level, pos, null, ttl, 0)) {
				taken = pos.immutable();
				level.levelEvent(2001, pos, Block.getId(state));
			}
			return new Object[] { state, taken };
		}
		return null;
	}

	// ================================================================ the displays

	private static @Nullable Display.BlockDisplay spawn(ServerLevel level, BlockState state, Vec3 at, float scale) {
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
		if (d == null) {
			return null;
		}
		d.setBlockState(state);
		setSpin(d, new Quaternionf(), scale);
		d.setPosRotInterpolationDuration(2);
		d.moveTo(at.x, at.y, at.z, 0f, 0f);
		d.addTag(ZeroGravity.TAG_DEBRIS);
		LIVE.add(d.getUUID());
		level.addFreshEntity(d);
		return d;
	}

	/** The cube centred on the entity's position, turned by {@code rot}. */
	private static void setSpin(Display.BlockDisplay d, Quaternionf rot, float scale) {
		Vector3f half = new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f);
		rot.transform(half);
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(3);
		d.setTransformation(new Transformation(half, new Quaternionf(rot), new Vector3f(scale, scale, scale), null));
	}

	private static void tumble(ServerLevel level, Piece pc, long now, float speed) {
		if ((now + pc.slot) % 3 != 0 || !(level.getEntity(pc.id) instanceof Display.BlockDisplay d)) {
			return;
		}
		float a = (now - pc.born) * speed + pc.slot;
		setSpin(d, new Quaternionf().rotateXYZ(a * 0.7f, a, a * 0.4f), pc.scale);
	}

	private static void move(ServerLevel level, Piece pc, Vec3 to) {
		pc.pos = to;
		Entity e = level.getEntity(pc.id);
		if (e != null) {
			e.moveTo(to.x, to.y, to.z, e.getYRot(), e.getXRot());
		}
	}

	/** The piece goes: the display is removed, the ground it came from put back at once. */
	private static void remove(ServerLevel level, Piece pc, boolean crumble) {
		LIVE.remove(pc.uuid);
		Entity e = level.getEntity(pc.id);
		if (e != null) {
			e.discard();
		}
		if (crumble) {
			level.levelEvent(2001, BlockPos.containing(pc.pos), Block.getId(pc.state));
		}
		if (pc.origin != null) {
			GeoTerrain.restoreNow(level, pc.origin);
			pc.origin = null;
		}
	}

	private static Piece add(ServerPlayer p, Session s, BlockState state, @Nullable BlockPos origin, Vec3 at, int kind, int slot, float scale) {
		Display.BlockDisplay d = spawn(p.serverLevel(), state, at, scale);
		if (d == null) {
			if (origin != null) {
				GeoTerrain.restoreNow(p.serverLevel(), origin);
			}
			return null;
		}
		Piece pc = new Piece(d, state, kind, slot, p.level().getGameTime(), scale);
		pc.origin = origin;
		s.pieces.add(pc);
		return pc;
	}

	// ================================================================ V: a touched block

	/** Lifts the block at {@code pos} (a touch): it hangs {@link ZeroGravityConfig#BLOCK_RISE} above. False if refused. */
	static boolean holdBlock(ServerPlayer p, BlockPos pos) {
		return holdBlock(p, pos, ZeroGravityConfig.TOUCH_BLOCKS_MAX);
	}

	/** As {@link #holdBlock(ServerPlayer, BlockPos)} with the band's capacity ({@code max}, v0.17.1). */
	static boolean holdBlock(ServerPlayer p, BlockPos pos, int max) {
		ServerLevel level = p.serverLevel();
		if (!liftable(level, pos) || !mayTake(p, pos) || count(p, HELD) >= max) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!GeoTerrain.lift(level, pos, ZeroGravityConfig.BLOCK_SAFETY_TICKS)) {
			return false;
		}
		Session s = session(p);
		Piece pc = add(p, s, state, pos.immutable(), Vec3.atCenterOf(pos), HELD, s.pieces.size(), 0.98f);
		if (pc == null) {
			return false;
		}
		pc.home = Vec3.atCenterOf(pos).add(0, ZeroGravityConfig.BLOCK_RISE, 0);
		pc.damage = ZeroGravityConfig.BLOCK_FALL_DAMAGE;
		return true;
	}

	// ================================================================ R: the Meteor Shower cloud

	/** Rips up the loose ground round her into a cloud high overhead. Returns the pieces (0: no ground to lift). */
	static int liftCloud(ServerPlayer p, @Nullable List<BlockPos> taken) {
		Session s = session(p);
		long now = p.level().getGameTime();
		BlockPos feet = p.blockPosition();
		int r = ZeroGravityConfig.RUBBLE_RADIUS;
		List<BlockPos> cols = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					continue;
				}
				if (dx * dx + dz * dz <= r * r) {
					cols.add(feet.offset(dx, -1, dz));
				}
			}
		}
		cols.sort((a, b) -> Double.compare(a.distSqr(feet), b.distSqr(feet)));
		int n = 0;
		int want = ZeroGravityConfig.SHOWER_PIECES;
		// spread the picks round her: every third column of the sorted rings
		for (int pass = 0; pass < 3 && n < want; pass++) {
			for (int i = pass; i < cols.size() && n < want; i += 3) {
				Object[] got = takeSurface(p, cols.get(i), ZeroGravityConfig.CLOUD_MAX_TICKS + ZeroGravityConfig.FALL_LIFE + 200);
				if (got == null) {
					continue;
				}
				BlockPos origin = (BlockPos) got[1];
				Vec3 at = Vec3.atCenterOf(cols.get(i));
				Piece pc = add(p, s, (BlockState) got[0], origin, at, CLOUD, n, 0.7f + 0.25f * ((n * 7) % 3) / 2f);
				if (pc != null) {
					pc.damage = ZeroGravityConfig.SHOWER_DAMAGE;
					if (origin != null && taken != null) {
						taken.add(origin);
					}
					n++;
				}
			}
		}
		if (n > 0) {
			s.cloudUntil = now + ZeroGravityConfig.CLOUD_MAX_TICKS;
			s.cloudCenter = p.position().add(0, ZeroGravityConfig.CLOUD_HEIGHT, 0);
			s.hit.clear();
		}
		return n;
	}

	/**
	 * The cloud's aim: over the point she looks at (within range), at cloud height above her -- or, under a roof or in a cave,
	 * a block and a half below the ceiling over that point (never lower than 3 blocks up).
	 */
	private static Vec3 cloudTarget(ServerPlayer p) {
		// (backed off the surface she aims at, so a wall she looks at does not count as a ceiling)
		Vec3 aim = AbilityHelpers.aimPoint(p, ZeroGravityConfig.CLOUD_AIM_RANGE).subtract(p.getLookAngle().scale(0.6));
		double base = p.getY() + 1.0;
		Vec3 from = new Vec3(aim.x, base, aim.z);
		Vec3 to = new Vec3(aim.x, p.getY() + ZeroGravityConfig.CLOUD_HEIGHT + 1.5, aim.z);
		BlockHitResult up = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double y = p.getY() + ZeroGravityConfig.CLOUD_HEIGHT;
		if (up.getType() != HitResult.Type.MISS) {
			y = Math.max(p.getY() + 3.0, up.getLocation().y - 1.5);
		}
		return new Vec3(aim.x, y, aim.z);
	}

	/** Release: the cloud rains -- every cloud piece falls. Returns how many. */
	static int rain(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		int n = 0;
		var rnd = p.getRandom();
		for (Piece pc : s.pieces) {
			if (pc.kind == CLOUD) {
				pc.kind = FALL;
				pc.kindSince = p.level().getGameTime();
				// a ragged start, so it rains rather than drops as one slab
				pc.vel = new Vec3((rnd.nextDouble() - 0.5) * 0.1, 0.05 - rnd.nextDouble() * 0.35, (rnd.nextDouble() - 0.5) * 0.1);
				n++;
			}
		}
		s.cloudUntil = 0L;
		return n;
	}

	/**
	 * Release: every touched block comes down WHERE SHE POINTS (v0.17.1, the Crane): aimed at a block, each one glides there
	 * and is set down on it (placed, never broken -- one on top of the other); aimed at a creature, they fly at it and smash;
	 * aimed at nothing (or where she may not build), they simply drop. Returns how many.
	 */
	static int dropHeld(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(ZeroGravityConfig.CRANE_RANGE));
		BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		LivingEntity aimedAt = AbilityHelpers.raycastEntity(p, ZeroGravityConfig.CRANE_RANGE);
		boolean atCreature = aimedAt != null && ZeroGravityCombat.isTarget(p, aimedAt)
				&& (bh.getType() == HitResult.Type.MISS || aimedAt.distanceTo(p) < bh.getLocation().distanceTo(eye));
		BlockPos cell = !atCreature && bh.getType() == HitResult.Type.BLOCK ? bh.getBlockPos().relative(bh.getDirection()) : null;
		int n = 0;
		long now = p.level().getGameTime();
		for (Piece pc : s.pieces) {
			if (pc.kind != HELD) {
				continue;
			}
			pc.kindSince = now;
			n++;
			if (atCreature) {
				Vec3 dir = ZeroGravityCombat.center(aimedAt).subtract(pc.pos);
				pc.kind = THROWN;
				pc.vel = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize().scale(ZeroGravityConfig.COMET_BLOCK_SPEED);
				continue;
			}
			BlockPos at = cell == null ? null : freeCell(p, s, cell);
			if (at != null) {
				pc.kind = PLACE;
				pc.placeAt = at;
			} else {
				pc.kind = FALL;
				pc.vel = Vec3.ZERO;
			}
		}
		return n;
	}

	/** The first free cell at or above {@code cell} (no other crane piece bound there) where she may build, or null. */
	private static @Nullable BlockPos freeCell(ServerPlayer p, Session s, BlockPos cell) {
		ServerLevel level = p.serverLevel();
		for (int up = 0; up < 8; up++) {
			BlockPos c = cell.above(up);
			BlockState st = level.getBlockState(c);
			if (!(st.isAir() || st.canBeReplaced()) || !st.getFluidState().isEmpty()) {
				return null;
			}
			boolean taken = false;
			for (Piece o : s.pieces) {
				if (o.kind == PLACE && c.equals(o.placeAt)) {
					taken = true;
					break;
				}
			}
			if (!taken && mayTake(p, c) && level.getEntitiesOfClass(LivingEntity.class, new AABB(c)).isEmpty()) {
				return c.immutable();
			}
		}
		return null;
	}

	/** Crane: the piece arrives and is set down in its cell (the place it came from stays empty). */
	private static boolean place(ServerPlayer p, Piece pc) {
		ServerLevel level = p.serverLevel();
		BlockPos at = pc.placeAt;
		if (at == null) {
			return false;
		}
		BlockState now = level.getBlockState(at);
		if (!(now.isAir() || now.canBeReplaced()) || !now.getFluidState().isEmpty() || !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) {
			return false;
		}
		level.setBlock(at, pc.state, Block.UPDATE_ALL);
		if (pc.origin != null) {
			GeoTerrain.commit(level, pc.origin); // moved for good: the old cell is not refilled
			pc.origin = null;
		}
		LIVE.remove(pc.uuid);
		Entity e = level.getEntity(pc.id);
		if (e != null) {
			e.discard();
		}
		Vec3 c = Vec3.atCenterOf(at);
		level.playSound(null, c.x, c.y, c.z, pc.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
		ZeroGravitySounds.play(level, c, ZeroGravitySounds.TOUCH, 0.5f, 0.8f);
		ZeroGravityCombat.burst(level, c, 5, 0.35);
		com.projecthero.mod.quirk.fx.QuirkFx.ring(level, Vec3.atBottomCenterOf(at).add(0, 0.02, 0)).colours(0xFFE0F0, 0xFF8FC8).size(1.3f)
				.thickness(0.16f).life(10).ground().spawn();
		return true;
	}

	/** v0.17.1 Shift+R Debris Barrage: every piece she has up fires along her aim as a shotgun. Returns how many fired. */
	static int barrage(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, 48.0);
		var rnd = p.getRandom();
		int n = 0;
		long now = p.level().getGameTime();
		for (Piece pc : s.pieces) {
			if (pc.kind != HELD && pc.kind != CLOUD && pc.kind != SHIELD && pc.kind != LIFT) {
				continue;
			}
			Vec3 dir = aim.subtract(pc.pos);
			dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
			double spread = Math.toRadians(ZeroGravityConfig.BARRAGE_SPREAD_DEGREES);
			dir = dir.yRot((float) ((rnd.nextDouble() - 0.5) * 2 * spread)).xRot((float) ((rnd.nextDouble() - 0.5) * 2 * spread));
			pc.kind = THROWN;
			pc.kindSince = now;
			pc.vel = dir.scale(ZeroGravityConfig.BARRAGE_SPEED);
			pc.damage = ZeroGravityConfig.BARRAGE_DAMAGE;
			n++;
		}
		if (n > 0) {
			s.shieldUntil = 0L;
			s.cloudUntil = 0L;
		}
		return n;
	}

	/**
	 * v0.17.1 Comet Toss with nothing afloat: a chunk of the ground just in front of her tears up and is thrown at her aim
	 * ({@link ZeroGravityConfig#COMET_DAMAGE}). False if there is no loose ground there.
	 */
	static boolean throwChunk(ServerPlayer p) {
		Session s = session(p);
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
		BlockPos col = BlockPos.containing(p.position().add(f.scale(1.6))).below();
		Object[] got = takeSurface(p, col, ZeroGravityConfig.FALL_LIFE + 200);
		if (got == null) {
			return false;
		}
		Vec3 from = p.getEyePosition().add(f.scale(1.2)).add(0, -0.3, 0);
		Piece pc = add(p, s, (BlockState) got[0], (BlockPos) got[1], from, THROWN, s.pieces.size(), ZeroGravityConfig.COMET_CHUNK_SCALE);
		if (pc == null) {
			return false;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, 48.0);
		Vec3 dir = aim.subtract(from);
		pc.vel = (dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize()).scale(ZeroGravityConfig.COMET_BLOCK_SPEED);
		pc.damage = ZeroGravityConfig.COMET_DAMAGE;
		return true;
	}

	// ================================================================ Shift+C: Gravity Bomb (v0.17.1)

	/**
	 * Tears a 5 x 3 x 5 slab of ground out in front of her and floats it up overhead (it follows her aim). Where she may not
	 * dig, the slab is a copy of the ground (nothing is taken). Returns the pieces (0: no ground there).
	 */
	static int liftSlab(ServerPlayer p) {
		Session s = session(p);
		if (s.bombUntil != 0L) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
		Vec3 c = p.position().add(f.scale(ZeroGravityConfig.BOMB_AHEAD));
		BlockPos cc = BlockPos.containing(c);
		// the surface at the slab's centre
		int top = Integer.MIN_VALUE;
		for (int dy = 2; dy >= -5; dy--) {
			BlockPos pos = cc.offset(0, dy, 0);
			if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
				top = pos.getY();
				break;
			}
		}
		if (top == Integer.MIN_VALUE) {
			return 0;
		}
		int half = ZeroGravityConfig.BOMB_WIDTH / 2;
		int ttl = ZeroGravityConfig.BOMB_HOLD_TICKS + ZeroGravityConfig.FALL_LIFE + 200;
		BlockState fallback = level.getBlockState(new BlockPos(cc.getX(), top, cc.getZ()));
		Vec3 centre = new Vec3(cc.getX() + 0.5, top - (ZeroGravityConfig.BOMB_DEPTH - 1) / 2.0 + 0.5, cc.getZ() + 0.5);
		int n = 0;
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				for (int dy = 0; dy > -ZeroGravityConfig.BOMB_DEPTH; dy--) {
					BlockPos pos = new BlockPos(cc.getX() + dx, top + dy, cc.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					BlockPos origin = null;
					boolean standing = dy == 0 && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(-0.05, 0.0, -0.05)).isEmpty();
					if (!standing && GeoTerrain.diggable(level, pos) && !GeoTerrain.isTemp(level, pos) && mayTake(p, pos)
							&& GeoTerrain.pocket(level, pos, null, ttl, 0)) {
						origin = pos.immutable();
					} else if (standing || !GeoTerrain.diggable(level, pos)) {
						state = GeoTerrain.diggable(level, pos) ? state : fallback;
						if (!GeoTerrain.natural(state)) {
							state = fallback;
						}
					}
					Piece pc = add(p, s, state, origin, Vec3.atCenterOf(pos), SLAB, n, 1.0f);
					if (pc != null) {
						pc.home = Vec3.atCenterOf(pos).subtract(centre);
						pc.damage = ZeroGravityConfig.BOMB_DAMAGE;
						n++;
					}
				}
			}
		}
		if (n == 0) {
			return 0;
		}
		s.bombCenter = centre;
		s.bombUntil = level.getGameTime() + ZeroGravityConfig.BOMB_HOLD_TICKS;
		s.bombFalling = false;
		s.bombVel = Vec3.ZERO;
		level.levelEvent(2001, new BlockPos(cc.getX(), top, cc.getZ()), Block.getId(fallback));
		return n;
	}

	/** The slab drops on her aim (Shift+C again, or by itself when it has hung long enough). True if it was up. */
	static boolean dropSlab(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.bombUntil == 0L || s.bombFalling) {
			return false;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, ZeroGravityConfig.BOMB_AIM_RANGE);
		ServerLevel level = p.serverLevel();
		BlockHitResult down = level.clip(new ClipContext(aim.add(0, 1.0, 0), aim.add(0, -24.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		s.bombTarget = down.getType() == HitResult.Type.MISS ? aim : down.getLocation();
		s.bombFalling = true;
		s.bombFallSince = level.getGameTime();
		s.bombVel = new Vec3(0, 0.25, 0);
		for (Piece pc : s.pieces) {
			if (pc.kind == SLAB) {
				pc.kind = SLAB_FALL;
				pc.kindSince = level.getGameTime();
			}
		}
		return true;
	}

	/** Tests: where the slab is aimed / the centre now. */
	public static Vec3 bombCenter(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Vec3.ZERO : s.bombCenter;
	}

	private static void tickSlab(ServerPlayer p, Session s, long now) {
		if (s.bombUntil == 0L) {
			return;
		}
		ServerLevel level = p.serverLevel();
		if (!s.bombFalling) {
			if (now >= s.bombUntil) {
				dropSlab(p);
				return;
			}
			Vec3 aim = AbilityHelpers.aimPoint(p, ZeroGravityConfig.BOMB_AIM_RANGE);
			Vec3 over = p.position().add(0, ZeroGravityConfig.BOMB_HEIGHT, 0).lerp(new Vec3(aim.x, p.getY() + ZeroGravityConfig.BOMB_HEIGHT, aim.z), 0.25);
			s.bombCenter = s.bombCenter.lerp(over, now - (s.bombUntil - ZeroGravityConfig.BOMB_HOLD_TICKS) < 25 ? 0.07 : 0.12);
		} else {
			Vec3 to = s.bombTarget.subtract(s.bombCenter);
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double vy = Math.max(-ZeroGravityConfig.FALL_MAX_SPEED * 1.2, s.bombVel.y - 0.12);
			Vec3 h = flat.scale(0.22);
			if (h.length() > 1.6) {
				h = h.normalize().scale(1.6);
			}
			s.bombVel = new Vec3(h.x, vy, h.z);
			Vec3 next = s.bombCenter.add(s.bombVel);
			double bottom = next.y - ZeroGravityConfig.BOMB_DEPTH / 2.0;
			boolean landed = bottom <= s.bombTarget.y + 0.1 || now - s.bombFallSince > 80;
			if (!landed && now - s.bombFallSince > 3) {
				BlockHitResult bh = level.clip(new ClipContext(s.bombCenter.add(0, -ZeroGravityConfig.BOMB_DEPTH / 2.0, 0), new Vec3(next.x, bottom, next.z),
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				landed = bh.getType() != HitResult.Type.MISS;
			}
			if (landed) {
				bombImpact(p, s, new Vec3(next.x, Math.max(bottom, s.bombTarget.y), next.z));
				return;
			}
			s.bombCenter = next;
		}
	}

	/** The slab lands: 20 in radius 4 (less at the edge, x the band), a dust shockwave, then the ground goes back. */
	private static void bombImpact(ServerPlayer p, Session s, Vec3 at) {
		ServerLevel level = p.serverLevel();
		float mult = com.projecthero.mod.quirk.meter.QuirkMeters.damageMult(p);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ZeroGravityConfig.BOMB_RADIUS, 3.0, ZeroGravityConfig.BOMB_RADIUS),
				e -> e != p && ZeroGravityCombat.isTarget(p, e))) {
			double d = Math.sqrt(e.distanceToSqr(at));
			if (d > ZeroGravityConfig.BOMB_RADIUS + e.getBbWidth()) {
				continue;
			}
			float k = (float) (1.0 - 0.5 * Math.min(1.0, d / ZeroGravityConfig.BOMB_RADIUS));
			e.invulnerableTime = 0;
			ZeroGravityCombat.strike(p, e, at, ZeroGravityConfig.BOMB_DAMAGE * k * mult, 0.9, 0.35);
		}
		BlockState mainState = Blocks.STONE.defaultBlockState();
		for (Iterator<Piece> it = s.pieces.iterator(); it.hasNext();) {
			Piece pc = it.next();
			if (pc.kind == SLAB || pc.kind == SLAB_FALL) {
				mainState = pc.state;
				remove(level, pc, (pc.slot % 3) == 0);
				it.remove();
			}
		}
		s.bombUntil = 0L;
		s.bombFalling = false;
		com.projecthero.mod.quirk.fx.QuirkFx.ring(level, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0xFF8FC8).size(9f).thickness(0.9f).life(16).ground().spawn();
		com.projecthero.mod.quirk.fx.QuirkFx.ring(level, at.add(0, 0.05, 0)).style(com.projecthero.mod.quirk.fx.QuirkFx.RING_SOFT).colours(0xC8B8A8, 0x8A7A6A)
				.size(7f).thickness(1.4f).life(24).ground().spawn();
		com.projecthero.mod.quirk.fx.QuirkFx.shards(level, at.add(0, 0.6, 0)).block(mainState).size(3.2f).count(36).life(36).spawn();
		com.projecthero.mod.quirk.fx.QuirkFx.decal(level, at).style(com.projecthero.mod.quirk.fx.QuirkFx.DECAL_CRACK).colour(0x161210).size(4.5f).life(260)
				.spawn();
		level.sendParticles(com.projecthero.mod.quirk.fx.QuirkParticles.DUST_CHUNK, at.x, at.y + 0.3, at.z, 40, 2.4, 0.3, 2.4, 0.12);
		level.sendParticles(com.projecthero.mod.quirk.fx.QuirkParticles.PINK_SPARKLE, at.x, at.y + 0.8, at.z, 24, 2.5, 0.6, 2.5, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.2, 0.3, 1.2, 0);
		ZeroGravitySounds.play(level, at, ZeroGravitySounds.METEOR, 1.6f, 0.7f);
		com.projecthero.mod.quirk.fx.QuirkSounds.play(level, at, com.projecthero.mod.quirk.fx.QuirkSounds.IMPACT_HEAVY, 1.4f, 0.7f);
		com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(level, at, 24, 0.7f, 14);
		com.projecthero.mod.quirk.fx.QuirkCamera.preset(p, com.projecthero.mod.quirk.fx.QuirkCamera.Preset.FINISHER);
	}

	// ================================================================ Plus Ultra: Full Release (v0.17.1)

	/**
	 * Every loose block round her (within {@code radius}, up to {@code max}) tears up and rises over where it was, hanging
	 * until {@link #crashLifted}. Returns how many.
	 */
	static int liftAround(ServerPlayer p, double radius, int max) {
		Session s = session(p);
		BlockPos feet = p.blockPosition();
		int r = (int) Math.ceil(radius);
		List<BlockPos> cols = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 <= r * r && d2 > 4) {
					cols.add(feet.offset(dx, -1, dz));
				}
			}
		}
		var rnd = p.getRandom();
		java.util.Collections.shuffle(cols, new java.util.Random(rnd.nextLong()));
		int n = 0;
		for (BlockPos col : cols) {
			if (n >= max) {
				break;
			}
			Object[] got = takeSurface(p, col, ZeroGravityConfig.FR_FLOAT_TICKS + ZeroGravityConfig.FALL_LIFE + 200);
			if (got == null) {
				continue;
			}
			Vec3 at = Vec3.atCenterOf(col);
			Piece pc = add(p, s, (BlockState) got[0], (BlockPos) got[1], at, LIFT, n, 0.65f + 0.35f * rnd.nextFloat());
			if (pc != null) {
				pc.home = at.add(0, ZeroGravityConfig.FR_RISE + rnd.nextDouble() * 3.0, 0);
				pc.damage = ZeroGravityConfig.SHOWER_DAMAGE;
				n++;
			}
		}
		return n;
	}

	/** "Release!": the risen rubble rains down -- each piece on the nearest creature under it. Returns how many fell. */
	static int crashLifted(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		int n = 0;
		for (Piece pc : s.pieces) {
			if (pc.kind != LIFT) {
				continue;
			}
			LivingEntity near = null;
			double best = 36.0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pc.pos, pc.pos).inflate(6.0, 14.0, 6.0),
					e -> e != p && ZeroGravityCombat.isTarget(p, e))) {
				double d = Math.pow(e.getX() - pc.pos.x, 2) + Math.pow(e.getZ() - pc.pos.z, 2);
				if (d < best) {
					best = d;
					near = e;
				}
			}
			pc.kind = FALL;
			pc.kindSince = level.getGameTime();
			if (near != null) {
				Vec3 to = near.position().subtract(pc.pos);
				pc.vel = new Vec3(to.x * 0.06, -0.5, to.z * 0.06);
			} else {
				pc.vel = new Vec3(0, -0.4, 0);
			}
			n++;
		}
		return n;
	}

	// ================================================================ G: Comet Toss (a block)

	/** The held / cloud piece nearest her crosshair (within the pick cone), or null. */
	static @Nullable Piece best(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return null;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(ZeroGravityConfig.COMET_PICK_DEGREES));
		Piece best = null;
		for (Piece pc : s.pieces) {
			if (pc.kind != HELD && pc.kind != CLOUD) {
				continue;
			}
			Vec3 to = pc.pos.subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > ZeroGravityConfig.COMET_PICK_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = pc;
			}
		}
		return best;
	}

	/** Angle-closeness (dot with her look) of the best piece, or -2 if none. */
	static double bestDot(ServerPlayer p) {
		Piece pc = best(p);
		if (pc == null) {
			return -2.0;
		}
		Vec3 to = pc.pos.subtract(p.getEyePosition()).normalize();
		return to.dot(p.getLookAngle());
	}

	/** Throws the best piece hard along her aim (from where it is, toward what she aims at). True if one was thrown. */
	static boolean throwBest(ServerPlayer p) {
		Piece pc = best(p);
		if (pc == null) {
			return false;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, 48.0);
		Vec3 dir = aim.subtract(pc.pos);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		pc.kind = THROWN;
		pc.kindSince = p.level().getGameTime();
		pc.vel = dir.scale(ZeroGravityConfig.COMET_BLOCK_SPEED);
		pc.damage = ZeroGravityConfig.COMET_DAMAGE;
		ServerLevel level = p.serverLevel();
		level.playSound(null, pc.pos.x, pc.pos.y, pc.pos.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9f, 0.6f);
		return true;
	}

	// ================================================================ Shift+R: Debris Shield

	static int startShield(ServerPlayer p, @Nullable List<BlockPos> taken) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		for (Iterator<Piece> it = s.pieces.iterator(); it.hasNext();) {
			Piece pc = it.next();
			if (pc.kind == SHIELD) {
				remove(level, pc, true);
				it.remove();
			}
		}
		BlockPos feet = p.blockPosition();
		int n = 0;
		int ring = 0;
		for (int r = 2; r <= 4 && n < ZeroGravityConfig.SHIELD_PIECES; r++) {
			for (int i = 0; i < 8 && n < ZeroGravityConfig.SHIELD_PIECES; i++) {
				double a = i * Math.PI / 4 + r * 0.4;
				BlockPos col = feet.offset((int) Math.round(Math.cos(a) * r), -1, (int) Math.round(Math.sin(a) * r));
				Object[] got = takeSurface(p, col, ZeroGravityConfig.SHIELD_TICKS + 200);
				if (got == null) {
					continue;
				}
				BlockPos origin = (BlockPos) got[1];
				Piece pc = add(p, s, (BlockState) got[0], origin, Vec3.atCenterOf(col), SHIELD, ring++, 0.55f);
				if (pc != null) {
					n++;
					if (origin != null && taken != null) {
						taken.add(origin);
					}
				}
			}
		}
		// any shortfall: a copy of the ground she stands on (nothing taken)
		BlockState fallback = level.getBlockState(feet.below()).isAir() ? Blocks.COBBLESTONE.defaultBlockState() : level.getBlockState(feet.below());
		while (n < ZeroGravityConfig.SHIELD_PIECES) {
			if (add(p, s, fallback, null, p.position().add(0, 0.3, 0), SHIELD, ring++, 0.55f) == null) {
				break;
			}
			n++;
		}
		s.shieldUntil = now + ZeroGravityConfig.SHIELD_TICKS;
		return n;
	}

	static void catchProjectile(ServerLevel level, Projectile pr) {
		Vec3 at = pr.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0f, 0.9f);
		ZeroGravityCombat.burst(level, at, 5, 0.2);
		pr.discard();
	}

	// ================================================================ impacts

	/** A piece moving {@code a}->{@code b}: smashes into the first creature or block on the way. True if it struck. */
	private static boolean impactAlong(ServerPlayer p, Session s, Piece pc, Vec3 a, Vec3 b) {
		ServerLevel level = p.serverLevel();
		Vec3 hitAt = null;
		AABB box = new AABB(a, b).inflate(0.6);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && ZeroGravityCombat.isTarget(p, e))) {
			AABB eb = e.getBoundingBox().inflate(pc.scale * 0.45);
			if (eb.clip(a, b).isPresent() || eb.contains(b)) {
				hitAt = b;
				break;
			}
		}
		if (hitAt == null) {
			BlockHitResult bh = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() == HitResult.Type.MISS) {
				return false;
			}
			hitAt = bh.getLocation();
		}
		double splash = pc.kind == THROWN ? ZeroGravityConfig.COMET_SPLASH
				: pc.damage == ZeroGravityConfig.SHOWER_DAMAGE ? ZeroGravityConfig.SHOWER_SPLASH : ZeroGravityConfig.BLOCK_SPLASH;
		// v0.17.1: a DROP (not a throw) hits harder in the "Pushing it" band (x1.3)
		float mult = pc.kind == THROWN ? 1f : com.projecthero.mod.quirk.meter.QuirkMeters.damageMult(p);
		boolean landed = false;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hitAt, hitAt).inflate(splash),
				e -> e != p && ZeroGravityCombat.isTarget(p, e))) {
			landed |= ZeroGravityCombat.strike(p, e, hitAt, pc.damage * mult, pc.kind == THROWN ? 0.6 : 0.3, 0.15);
		}
		level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 0.75f);
		if (pc.kind == THROWN || pc.scale > 0.9f) {
			level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.7f, 1.0f);
			com.projecthero.mod.quirk.fx.QuirkFx.shards(level, hitAt.add(0, 0.2, 0)).block(pc.state).size(1.3f).count(10).life(22).spawn();
		}
		long now = level.getGameTime();
		if (now - s.lastRumble >= 4) {
			s.lastRumble = now;
			ZeroGravitySounds.play(level, hitAt, ZeroGravitySounds.METEOR, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.25f);
		}
		// every impact: a pink ring and a dust burst
		com.projecthero.mod.quirk.fx.QuirkFx.ring(level, hitAt.add(0, 0.05, 0)).colours(0xFFFFFF, 0xFF8FC8).size(landed ? 2.6f : 1.8f).thickness(0.3f)
				.life(10).ground().spawn();
		level.sendParticles(com.projecthero.mod.quirk.fx.QuirkParticles.DUST_CHUNK, hitAt.x, hitAt.y + 0.2, hitAt.z, 6, 0.35, 0.1, 0.35, 0.08);
		ZeroGravityCombat.burst(level, hitAt, 4, 0.3);
		return true;
	}

	// ================================================================ tick / stop

	static void tick(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.pieces.isEmpty()) {
			return;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		if (s.cloudUntil != 0L && now >= s.cloudUntil) {
			rain(p);
		}
		tickSlab(p, s, now);
		boolean shieldDone = now >= s.shieldUntil;
		Vec3 cloudTo = cloudTarget(p);
		s.cloudCenter = s.cloudCenter.lerp(cloudTo, 0.08);
		int cloudN = Math.max(1, count(p, CLOUD));
		int shieldN = Math.max(1, count(p, SHIELD));
		int ci = 0, si = 0;
		Iterator<Piece> it = s.pieces.iterator();
		while (it.hasNext()) {
			Piece pc = it.next();
			switch (pc.kind) {
				case HELD -> {
					if (pc.pos.distanceToSqr(p.position()) > ZeroGravityConfig.FLOAT_LEASH * ZeroGravityConfig.FLOAT_LEASH) {
						pc.kind = FALL;
						pc.kindSince = now;
						continue;
					}
					double t = (now - pc.born) * 0.06 + pc.slot;
					Vec3 bob = pc.home.add(Math.sin(t) * 0.12, Math.sin(t * 1.3) * 0.15, Math.cos(t) * 0.12);
					move(level, pc, pc.pos.lerp(bob, 0.15));
					tumble(level, pc, now, 0.03f);
					if ((now + pc.slot) % 4 == 0) {
						ZeroGravityCombat.trail(level, pc.pos.add(0, -0.5, 0));
					}
				}
				case CLOUD -> {
					double a = ci * Math.PI * 2.0 / cloudN + (now - pc.born) * 0.025;
					double rr = ZeroGravityConfig.CLOUD_RADIUS * (0.45 + 0.55 * ((ci % 3) / 2.0));
					Vec3 at = s.cloudCenter.add(Math.cos(a) * rr, Math.sin(a * 2.0 + ci) * 0.6 + (ci % 2) * 0.8, Math.sin(a) * rr);
					// rising from the ground first: faster when far
					move(level, pc, pc.pos.lerp(at, now - pc.born < 30 ? 0.09 : 0.2));
					tumble(level, pc, now, 0.08f);
					if ((now + pc.slot) % 5 == 0) {
						ZeroGravityCombat.trail(level, pc.pos);
					}
					ci++;
				}
				case SHIELD -> {
					if (shieldDone) {
						remove(level, pc, true);
						it.remove();
						continue;
					}
					double a = si * Math.PI * 2.0 / shieldN + (now - pc.born) * 0.22;
					Vec3 at = p.position().add(Math.cos(a) * ZeroGravityConfig.SHIELD_RADIUS, 1.0 + 0.35 * Math.sin(a * 2.0 + si),
							Math.sin(a) * ZeroGravityConfig.SHIELD_RADIUS);
					move(level, pc, pc.pos.lerp(at, 0.5));
					tumble(level, pc, now, 0.12f);
					si++;
				}
				case PLACE -> {
					Vec3 to = pc.placeAt == null ? pc.pos : Vec3.atCenterOf(pc.placeAt);
					Vec3 d = to.subtract(pc.pos);
					if (pc.placeAt == null || now - pc.kindSince > ZeroGravityConfig.CRANE_MAX_TICKS) {
						pc.kind = FALL;
						pc.vel = Vec3.ZERO;
						continue;
					}
					if (d.length() < 0.2) {
						if (place(p, pc)) {
							it.remove();
						} else {
							pc.kind = FALL;
							pc.vel = Vec3.ZERO;
						}
						continue;
					}
					move(level, pc, pc.pos.add(d.length() > ZeroGravityConfig.CRANE_SPEED ? d.normalize().scale(ZeroGravityConfig.CRANE_SPEED) : d.scale(0.6)));
					if (d.length() < 1.5 && level.getEntity(pc.id) instanceof Display.BlockDisplay disp) {
						setSpin(disp, new Quaternionf(), pc.scale); // squared up for the landing
					} else {
						tumble(level, pc, now, 0.03f);
					}
					if ((now + pc.slot) % 3 == 0) {
						ZeroGravityCombat.trail(level, pc.pos.add(0, -0.5, 0));
					}
				}
				case SLAB, SLAB_FALL -> {
					double wob = s.bombFalling ? 0.0 : Math.sin((now - pc.born) * 0.08) * 0.04;
					Vec3 at = s.bombCenter.add(pc.home).add(0, wob, 0);
					move(level, pc, pc.kind == SLAB && now - pc.born < 2 ? pc.pos.lerp(at, 0.5) : at);
					if ((now + pc.slot) % 9 == 0 && pc.home.y < -0.5) {
						ZeroGravityCombat.trail(level, pc.pos.add(0, -0.5, 0));
					}
				}
				case LIFT -> {
					double t = (now - pc.born) * 0.05 + pc.slot;
					Vec3 bob = pc.home.add(Math.sin(t) * 0.15, Math.sin(t * 1.3) * 0.2, Math.cos(t) * 0.15);
					move(level, pc, pc.pos.lerp(bob, now - pc.born < 20 ? 0.08 : 0.15));
					tumble(level, pc, now, 0.06f);
					if ((now + pc.slot) % 6 == 0) {
						ZeroGravityCombat.trail(level, pc.pos);
					}
				}
				case FALL, THROWN -> {
					boolean thrown = pc.kind == THROWN;
					if (now - pc.kindSince > (thrown ? ZeroGravityConfig.COMET_WATCH_TICKS + 20 : ZeroGravityConfig.FALL_LIFE)) {
						remove(level, pc, true);
						it.remove();
						continue;
					}
					double g = thrown ? 0.02 : ZeroGravityConfig.FALL_GRAVITY;
					pc.vel = new Vec3(pc.vel.x * 0.99, Math.max(-ZeroGravityConfig.FALL_MAX_SPEED, pc.vel.y - g), pc.vel.z * 0.99);
					Vec3 next = pc.pos.add(pc.vel);
					if (impactAlong(p, s, pc, pc.pos, next)) {
						remove(level, pc, true);
						it.remove();
						continue;
					}
					move(level, pc, next);
					tumble(level, pc, now, thrown ? 0.3f : 0.15f);
					if (thrown || (now % 2 == 0)) {
						ZeroGravityCombat.trail(level, next);
					}
				}
				default -> {
				}
			}
		}
		// projectiles that come close to the shield are stopped by the rubble
		if (!shieldDone && count(p, SHIELD) > 0) {
			Vec3 c = p.position().add(0, 1.0, 0);
			double r = ZeroGravityConfig.SHIELD_CATCH_RADIUS;
			for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), pr -> pr.isAlive() && pr.getOwner() != p)) {
				Vec3 next = pr.position().add(pr.getDeltaMovement());
				if (pr.position().distanceTo(c) <= r || next.distanceTo(c) <= r) {
					catchProjectile(level, pr);
				}
			}
		}
	}

	/** Every piece goes at once, the ground put back (stopAll: death, logout, sickness, Erasure...). */
	static void dropAll(ServerPlayer p) {
		Session s = SESSIONS.remove(p.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		for (Piece pc : s.pieces) {
			remove(level, pc, true);
		}
		s.pieces.clear();
	}

	/** Settle: every piece is lowered away quietly -- dissolved in pink sparkles, the ground put back. Returns how many. */
	static int dissolveAll(ServerPlayer p) {
		Session s = SESSIONS.remove(p.getUUID());
		if (s == null) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		int n = s.pieces.size();
		for (Piece pc : s.pieces) {
			ZeroGravityCombat.burst(level, pc.pos, 4, 0.25);
			remove(level, pc, false);
		}
		s.pieces.clear();
		return n;
	}

	/** SERVER_STOPPING: nothing of hers is saved into the world. */
	static void removeEverything(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> mine = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof Display.BlockDisplay && e.getTags().contains(ZeroGravity.TAG_DEBRIS)) {
					mine.add(e);
				}
			}
			mine.forEach(Entity::discard);
		}
		SESSIONS.clear();
		LIVE.clear();
	}

	/** ENTITY_LOAD: an orphaned piece (no session owns it) is removed. */
	static void cleanup(Entity e) {
		if (e instanceof Display.BlockDisplay && e.getTags().contains(ZeroGravity.TAG_DEBRIS) && !LIVE.contains(e.getUUID())) {
			e.discard();
		}
	}
}
