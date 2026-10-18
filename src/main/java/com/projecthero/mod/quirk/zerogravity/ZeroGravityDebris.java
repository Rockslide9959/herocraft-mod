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
import com.projecthero.mod.hero.power.p05.GeoTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
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
 * v0.16.7: Zero Gravity's floating rubble -- the Meteor Shower, the Debris Shield and the Uravity Meteor. Every piece is
 * a vanilla block DISPLAY (no physics, no collision: it can never place a block), moved here every tick and removed when
 * its move ends. The ground it came from is emptied through {@link GeoTerrain#pocket} (natural, breakable blocks with no
 * block entity -- never a chest, a spawner or bedrock -- only where she may build and terrain damage is on) and put back
 * exactly as it was after {@link ZeroGravityConfig#RUBBLE_RESTORE_TICKS} (and on server stop). With terrain damage off
 * the rubble is only a copy of the ground and nothing is taken.
 *
 * <p>The display entities carry {@link ZeroGravity#TAG_DEBRIS}: one found on load that no session owns (the server
 * stopped mid-move) is removed ({@link #cleanup}).
 */
public final class ZeroGravityDebris {
	static final int SHOWER = 0;
	static final int SHIELD = 1;
	static final int METEOR = 2;

	static final class Piece {
		final int id;
		final UUID uuid;
		final BlockState state;
		final int kind;
		final int slot;
		final long born;
		Vec3 pos;
		Vec3 vel = Vec3.ZERO;
		/** Shower: 0 hovering, 1 flying. */
		int phase;
		long fireAt;
		/** Meteor: the piece's place in the 3x3 slab. */
		double ox, oz;
		Vec3 from = Vec3.ZERO;

		Piece(Display.BlockDisplay d, BlockState state, int kind, int slot, long born) {
			this.id = d.getId();
			this.uuid = d.getUUID();
			this.state = state;
			this.kind = kind;
			this.slot = slot;
			this.born = born;
			this.pos = d.position();
		}
	}

	/** The Uravity Meteor's slab (one per player at a time). */
	static final class Meteor {
		final List<Piece> pieces = new ArrayList<>();
		int targetId = -1;
		Vec3 point = Vec3.ZERO;
		long born;
		/** 0 rising, 1 holding, 2 falling. */
		int phase;
		double fall;
	}

	static final class Session {
		final List<Piece> shower = new ArrayList<>();
		int showerTarget = -1;
		Vec3 showerPoint = Vec3.ZERO;
		final List<Piece> shield = new ArrayList<>();
		long shieldUntil;
		@Nullable Meteor meteor;
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

	/** How many pieces she holds up (the Nausea counts them). */
	public static int count(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		return s.shower.size() + s.shield.size() + (s.meteor == null ? 0 : s.meteor.pieces.size());
	}

	public static boolean shieldUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && !s.shield.isEmpty() && s.shieldUntil > p.level().getGameTime();
	}

	public static boolean showerRunning(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && !s.shower.isEmpty();
	}

	public static boolean meteorRunning(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.meteor != null;
	}

	/** The display entities of her live pieces (tests, FX). */
	public static List<Display.BlockDisplay> displays(ServerPlayer p) {
		List<Display.BlockDisplay> out = new ArrayList<>();
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return out;
		}
		List<Piece> all = new ArrayList<>(s.shower);
		all.addAll(s.shield);
		if (s.meteor != null) {
			all.addAll(s.meteor.pieces);
		}
		for (Piece pc : all) {
			if (p.serverLevel().getEntity(pc.id) instanceof Display.BlockDisplay d) {
				out.add(d);
			}
		}
		return out;
	}

	// ================================================================ the ground

	/**
	 * Takes the loose top block of the column at {@code column} (searching a few blocks up and down for the surface):
	 * the block state of the rubble, or null if there is no natural ground there. Taken for real (and restored after
	 * {@code ttl}) when she may edit it; otherwise only copied.
	 */
	static @Nullable BlockState takeSurface(ServerPlayer p, BlockPos column, int ttl, @Nullable List<BlockPos> taken) {
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
			// never the block she (or anything standing right on it) is standing on
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(-0.05, 0.0, -0.05)).isEmpty()) {
				return null;
			}
			if (GeoTerrain.canEdit(p, level, pos) && GeoTerrain.pocket(level, pos, null, ttl, 0)) {
				if (taken != null) {
					taken.add(pos.immutable());
				}
				level.levelEvent(2001, pos, Block.getId(state));
			}
			return state;
		}
		return null;
	}

	/** Up to {@code n} pieces of rubble from the loose ground around her (any shortfall is plain cobblestone dust). */
	static List<BlockState> gather(ServerPlayer p, int n, int ttl, @Nullable List<BlockPos> taken) {
		List<BlockState> out = new ArrayList<>();
		BlockPos feet = p.blockPosition();
		int r = ZeroGravityConfig.RUBBLE_RADIUS;
		// a deterministic spiral-ish order: the nearest columns first, skipping her own
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
		// spread the picks around her: every other column of the sorted ring
		for (int pass = 0; pass < 2 && out.size() < n; pass++) {
			for (int i = pass; i < cols.size() && out.size() < n; i += 2) {
				BlockState st = takeSurface(p, cols.get(i), ttl, taken);
				if (st != null) {
					out.add(st);
				}
			}
		}
		while (out.size() < n) {
			out.add(Blocks.COBBLESTONE.defaultBlockState());
		}
		return out;
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

	private static void tumble(ServerLevel level, Piece pc, float scale, long now) {
		if ((now + pc.slot) % 3 != 0 || !(level.getEntity(pc.id) instanceof Display.BlockDisplay d)) {
			return;
		}
		float a = (now - pc.born) * 0.12f + pc.slot;
		setSpin(d, new Quaternionf().rotateXYZ(a * 0.7f, a, a * 0.4f), scale);
	}

	private static void move(ServerLevel level, Piece pc, Vec3 to) {
		pc.pos = to;
		Entity e = level.getEntity(pc.id);
		if (e != null) {
			e.moveTo(to.x, to.y, to.z, e.getYRot(), e.getXRot());
		}
	}

	private static void remove(ServerLevel level, Piece pc, boolean crumble) {
		LIVE.remove(pc.uuid);
		Entity e = level.getEntity(pc.id);
		if (e != null) {
			e.discard();
		}
		if (crumble) {
			level.levelEvent(2001, BlockPos.containing(pc.pos), Block.getId(pc.state));
		}
	}

	private static void removeAll(ServerLevel level, List<Piece> list, boolean crumble) {
		for (Piece pc : list) {
			remove(level, pc, crumble);
		}
		list.clear();
	}

	// ================================================================ X: Meteor Shower

	/** Lifts the rubble; it hovers about her, then rains on {@code target} (or {@code point}). Returns the pieces. */
	static int startShower(ServerPlayer p, @Nullable LivingEntity target, Vec3 point, @Nullable List<BlockPos> taken) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		removeAll(level, s.shower, true);
		long now = level.getGameTime();
		List<BlockState> rubble = gather(p, ZeroGravityConfig.SHOWER_PIECES, ZeroGravityConfig.RUBBLE_RESTORE_TICKS, taken);
		for (int i = 0; i < rubble.size(); i++) {
			double a = i * Math.PI * 2.0 / rubble.size();
			Vec3 at = p.position().add(Math.cos(a) * 2.4, 0.3, Math.sin(a) * 2.4);
			Display.BlockDisplay d = spawn(level, rubble.get(i), at, 0.6f);
			if (d == null) {
				continue;
			}
			Piece pc = new Piece(d, rubble.get(i), SHOWER, i, now);
			pc.fireAt = now + ZeroGravityConfig.SHOWER_HOVER_TICKS + (long) i * ZeroGravityConfig.SHOWER_FIRE_GAP;
			s.shower.add(pc);
		}
		s.showerTarget = target == null ? -1 : target.getId();
		s.showerPoint = point;
		s.hit.clear();
		return s.shower.size();
	}

	private static Vec3 showerAim(ServerPlayer p, Session s) {
		if (s.showerTarget >= 0 && p.serverLevel().getEntity(s.showerTarget) instanceof LivingEntity le && le.isAlive()) {
			return ZeroGravityCombat.center(le);
		}
		return s.showerPoint;
	}

	private static void tickShower(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		Iterator<Piece> it = s.shower.iterator();
		Vec3 aim = showerAim(p, s);
		while (it.hasNext()) {
			Piece pc = it.next();
			tumble(level, pc, 0.6f, now);
			if (now - pc.born > ZeroGravityConfig.SHOWER_LIFE) {
				remove(level, pc, true);
				it.remove();
				continue;
			}
			if (pc.phase == 0) {
				double a = pc.slot * Math.PI * 2.0 / ZeroGravityConfig.SHOWER_PIECES + (now - pc.born) * 0.08;
				Vec3 hover = p.position().add(Math.cos(a) * 1.7, 2.7 + 0.25 * Math.sin(a * 2.0), Math.sin(a) * 1.7);
				move(level, pc, pc.pos.lerp(hover, 0.25));
				if (now >= pc.fireAt) {
					pc.phase = 1;
					pc.vel = aim.subtract(pc.pos).normalize().scale(ZeroGravityConfig.SHOWER_SPEED);
					level.playSound(null, pc.pos.x, pc.pos.y, pc.pos.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.5f, 0.7f);
				}
				continue;
			}
			// flying, homing a little -- until it is nearly there (then straight on, into the ground if it missed)
			if (pc.pos.distanceToSqr(aim) > 2.25) {
				Vec3 want = aim.subtract(pc.pos).normalize().scale(ZeroGravityConfig.SHOWER_SPEED);
				pc.vel = pc.vel.lerp(want, 0.15);
			}
			if (pc.vel.lengthSqr() < 0.25) {
				pc.vel = new Vec3(pc.vel.x, -ZeroGravityConfig.SHOWER_SPEED, pc.vel.z);
			}
			Vec3 next = pc.pos.add(pc.vel);
			if (impactAlong(p, s, pc.pos, next, ZeroGravityConfig.SHOWER_DAMAGE, ZeroGravityConfig.SHOWER_SPLASH, 0.4)) {
				remove(level, pc, true);
				it.remove();
				continue;
			}
			move(level, pc, next);
			ZeroGravityCombat.trail(level, next);
		}
	}

	/** A piece flying {@code a}->{@code b}: hits the first creature or block on the way. True if it struck. */
	private static boolean impactAlong(ServerPlayer p, Session s, Vec3 a, Vec3 b, float damage, double splash, double knock) {
		ServerLevel level = p.serverLevel();
		Vec3 hitAt = null;
		AABB box = new AABB(a, b).inflate(0.6);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> ZeroGravityCombat.isTarget(p, e))) {
			if (e.getBoundingBox().inflate(0.35).clip(a, b).isPresent() || e.getBoundingBox().inflate(0.35).contains(b)) {
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
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hitAt, hitAt).inflate(splash),
				e -> ZeroGravityCombat.isTarget(p, e))) {
			ZeroGravityCombat.strike(p, e, hitAt, damage, knock, 0.15);
		}
		level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.9f, 0.8f);
		ZeroGravityCombat.burst(level, hitAt, 4, 0.3);
		return true;
	}

	// ================================================================ Shift+X: Debris Shield

	static int startShield(ServerPlayer p, @Nullable List<BlockPos> taken) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		removeAll(level, s.shield, true);
		long now = level.getGameTime();
		List<BlockState> rubble = gather(p, ZeroGravityConfig.SHIELD_PIECES, ZeroGravityConfig.RUBBLE_RESTORE_TICKS, taken);
		for (int i = 0; i < rubble.size(); i++) {
			double a = i * Math.PI * 2.0 / rubble.size();
			Vec3 at = p.position().add(Math.cos(a) * 2.0, 0.2, Math.sin(a) * 2.0);
			Display.BlockDisplay d = spawn(level, rubble.get(i), at, 0.55f);
			if (d != null) {
				s.shield.add(new Piece(d, rubble.get(i), SHIELD, i, now));
			}
		}
		s.shieldUntil = now + ZeroGravityConfig.SHIELD_TICKS;
		return s.shield.size();
	}

	private static void tickShield(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		if (now >= s.shieldUntil) {
			removeAll(level, s.shield, true);
			return;
		}
		int n = Math.max(1, s.shield.size());
		for (Piece pc : s.shield) {
			double a = pc.slot * Math.PI * 2.0 / n + (now - pc.born) * 0.22;
			Vec3 at = p.position().add(Math.cos(a) * ZeroGravityConfig.SHIELD_RADIUS, 1.0 + 0.35 * Math.sin(a * 2.0 + pc.slot),
					Math.sin(a) * ZeroGravityConfig.SHIELD_RADIUS);
			move(level, pc, pc.pos.lerp(at, 0.5));
			tumble(level, pc, 0.55f, now);
		}
		// projectiles that come close are stopped by the rubble
		Vec3 c = p.position().add(0, 1.0, 0);
		double r = ZeroGravityConfig.SHIELD_CATCH_RADIUS;
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 next = pr.position().add(pr.getDeltaMovement());
			if (pr.position().distanceTo(c) <= r || next.distanceTo(c) <= r) {
				catchProjectile(level, pr);
			}
		}
	}

	static void catchProjectile(ServerLevel level, Projectile pr) {
		Vec3 at = pr.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0f, 0.9f);
		ZeroGravityCombat.burst(level, at, 5, 0.2);
		pr.discard();
	}

	// ================================================================ Shift+V: Uravity Meteor

	/** Rips up a 3x3 slab of ground before her; it rises above {@code target} and comes down on it. */
	static int startMeteor(ServerPlayer p, @Nullable LivingEntity target, Vec3 point, @Nullable List<BlockPos> taken) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		if (s.meteor != null) {
			removeAll(level, s.meteor.pieces, true);
		}
		Meteor m = new Meteor();
		long now = level.getGameTime();
		m.born = now;
		m.targetId = target == null ? -1 : target.getId();
		m.point = point;
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
		BlockPos mid = BlockPos.containing(p.position().add(f.scale(3.0))).below();
		int slot = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState st = takeSurface(p, mid.offset(dx, 0, dz), ZeroGravityConfig.METEOR_RESTORE_TICKS, taken);
				if (st == null) {
					st = Blocks.STONE.defaultBlockState();
				}
				Vec3 at = Vec3.atCenterOf(mid.offset(dx, 0, dz));
				Display.BlockDisplay d = spawn(level, st, at, 1.0f);
				if (d == null) {
					continue;
				}
				Piece pc = new Piece(d, st, METEOR, slot++, now);
				pc.ox = dx;
				pc.oz = dz;
				pc.from = at;
				m.pieces.add(pc);
			}
		}
		s.meteor = m;
		s.hit.clear();
		level.playSound(null, mid.getX() + 0.5, mid.getY() + 1.0, mid.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
		return m.pieces.size();
	}

	private static Vec3 meteorAim(ServerPlayer p, Meteor m) {
		if (m.targetId >= 0 && p.serverLevel().getEntity(m.targetId) instanceof LivingEntity le && le.isAlive()) {
			m.point = le.position();
		}
		return m.point;
	}

	private static void tickMeteor(ServerPlayer p, Session s, long now) {
		Meteor m = s.meteor;
		ServerLevel level = p.serverLevel();
		long age = now - m.born;
		if (age > ZeroGravityConfig.METEOR_LIFE) {
			removeAll(level, m.pieces, true);
			s.meteor = null;
			return;
		}
		Vec3 aim = meteorAim(p, m);
		double topY = aim.y + ZeroGravityConfig.METEOR_HEIGHT;
		if (age < ZeroGravityConfig.METEOR_RISE_TICKS) {
			float t = Mth.clamp(age / (float) ZeroGravityConfig.METEOR_RISE_TICKS, 0f, 1f);
			float e = t * t * (3f - 2f * t);
			for (Piece pc : m.pieces) {
				Vec3 top = new Vec3(aim.x + pc.ox, topY, aim.z + pc.oz);
				Vec3 arc = pc.from.lerp(top, e).add(0, Math.sin(e * Math.PI) * 2.0, 0);
				move(level, pc, arc);
			}
			if (age % 4 == 0) {
				ZeroGravityCombat.burst(level, m.pieces.isEmpty() ? aim : m.pieces.get(0).pos, 3, 0.8);
			}
			return;
		}
		if (age < ZeroGravityConfig.METEOR_RISE_TICKS + ZeroGravityConfig.METEOR_HOLD_TICKS) {
			for (Piece pc : m.pieces) {
				move(level, pc, new Vec3(aim.x + pc.ox, topY, aim.z + pc.oz));
			}
			return;
		}
		// falling: faster every tick; the slab lands as one
		m.phase = 2;
		m.fall = Math.min(2.2, m.fall + 0.18);
		boolean landed = false;
		Vec3 impact = null;
		for (Piece pc : m.pieces) {
			Vec3 next = new Vec3(pc.pos.x, pc.pos.y - m.fall, pc.pos.z);
			BlockHitResult bh = level.clip(new ClipContext(pc.pos, next.add(0, -0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() != HitResult.Type.MISS) {
				landed = true;
				impact = bh.getLocation();
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pc.pos, next).inflate(0.5),
					e -> ZeroGravityCombat.isTarget(p, e))) {
				landed = true;
				impact = next;
			}
			move(level, pc, next);
		}
		if (landed) {
			Vec3 c = impact == null ? aim : new Vec3(aim.x, impact.y, aim.z);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(ZeroGravityConfig.METEOR_RADIUS, 2.5,
					ZeroGravityConfig.METEOR_RADIUS), e -> ZeroGravityCombat.isTarget(p, e))) {
				if (s.hit.add(e.getId())) {
					ZeroGravityCombat.strike(p, e, c, ZeroGravityConfig.METEOR_DAMAGE, ZeroGravityConfig.METEOR_KNOCKBACK, 0.35);
				}
			}
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.8f);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 1.0, 0.3, 1.0, 0.0);
			ZeroGravityCombat.burst(level, c.add(0, 0.5, 0), 20, 1.4);
			removeAll(level, m.pieces, true);
			s.meteor = null;
		}
	}

	// ================================================================ tick / stop

	static void tick(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		long now = p.level().getGameTime();
		if (!s.shower.isEmpty()) {
			tickShower(p, s, now);
		}
		if (!s.shield.isEmpty()) {
			tickShield(p, s, now);
		}
		if (s.meteor != null) {
			tickMeteor(p, s, now);
		}
	}

	/** Drops every piece (the ground keeps its own restore clock). */
	static void dropAll(ServerPlayer p) {
		Session s = SESSIONS.remove(p.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		removeAll(level, s.shower, true);
		removeAll(level, s.shield, true);
		if (s.meteor != null) {
			removeAll(level, s.meteor.pieces, true);
		}
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
