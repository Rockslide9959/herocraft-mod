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
		/** HELD: where it hangs. */
		Vec3 home = Vec3.ZERO;
		float damage;

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

	/** How many pieces she holds up (the Nausea weighs them): everything not yet falling or thrown. */
	public static int held(ServerPlayer p) {
		return count(p, HELD) + count(p, CLOUD) + count(p, SHIELD);
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
		ServerLevel level = p.serverLevel();
		if (!liftable(level, pos) || !mayTake(p, pos) || count(p, HELD) >= ZeroGravityConfig.TOUCH_BLOCKS_MAX) {
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

	/** Release: every touched block drops. Returns how many. */
	static int dropHeld(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		int n = 0;
		for (Piece pc : s.pieces) {
			if (pc.kind == HELD) {
				pc.kind = FALL;
				pc.kindSince = p.level().getGameTime();
				pc.vel = Vec3.ZERO;
				n++;
			}
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
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hitAt, hitAt).inflate(splash),
				e -> e != p && ZeroGravityCombat.isTarget(p, e))) {
			ZeroGravityCombat.strike(p, e, hitAt, pc.damage, pc.kind == THROWN ? 0.6 : 0.3, 0.15);
		}
		level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 0.75f);
		if (pc.kind == THROWN || pc.scale > 0.9f) {
			level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.8f, 1.0f);
			level.sendParticles(ParticleTypes.EXPLOSION, hitAt.x, hitAt.y + 0.2, hitAt.z, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.CLOUD, hitAt.x, hitAt.y + 0.2, hitAt.z, 4, 0.3, 0.1, 0.3, 0.02);
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
