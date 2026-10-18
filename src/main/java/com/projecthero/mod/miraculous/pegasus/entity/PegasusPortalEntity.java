package com.projecthero.mod.miraculous.pegasus.entity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.entity.KwamiEntity;
import com.projecthero.mod.miraculous.pegasus.PegasusCombat;
import com.projecthero.mod.miraculous.pegasus.PegasusConfig;
import com.projecthero.mod.miraculous.pegasus.PegasusMoves;
import com.projecthero.mod.miraculous.pegasus.PegasusTeleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: one of Pegasus's portals -- a golden-white hexagonal ring with a swirling, shimmering surface (drawn by
 * {@code PegasusPortalRenderer}), {@link #radius} blocks from its centre (the entity's position) to a corner, facing
 * {@link #normal} (its yaw / pitch, synced). It grows out of a point of light over {@link PegasusConfig#PORTAL_OPEN_TICKS}
 * and shrinks away at the end of its {@link #lifeTicks}.
 *
 * <p>A <b>transporting</b> portal (Voyage, Linked Portals) sends whatever passes through its disc -- players, mobs,
 * items, arrows; never a boss, a Kwami or another portal -- to its exit: a precomputed safe spot ({@link #setExit}) in
 * the same world, or (Voyage, players only) another one, re-checked with {@link PegasusTeleport#fits} as it goes. A
 * traveller can't go back through for {@link PegasusConfig#PASS_COOLDOWN} ({@link #PASSED}). The others (Portal Hop,
 * Portal Trap, the Portal Shield) are moved by the moves themselves.
 *
 * <p>Never saved. The live ones are kept in {@link #ACTIVE} for the clean-up (detransform, logout, ServerStateReset).
 */
public class PegasusPortalEntity extends Entity {
	public static final int HOP = 0;
	public static final int TRAP_LOW = 1;
	public static final int TRAP_HIGH = 2;
	public static final int VOYAGE = 3;
	public static final int SHIELD = 4;
	public static final int SHIELD_EXIT = 5;
	public static final int LINK = 6;

	public static final Set<PegasusPortalEntity> ACTIVE = ConcurrentHashMap.newKeySet();
	/** traveller UUID -> server tick before which it may not take a portal again. */
	public static final Map<UUID, Integer> PASSED = new ConcurrentHashMap<>();

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FACE_YAW = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FACE_PITCH = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(PegasusPortalEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private int age;
	// the exit (transporting portals)
	private @Nullable ResourceKey<Level> exitDim;
	private @Nullable Vec3 exitFeet;
	private float exitYaw;
	private boolean playersOnly;
	/** Keep the chunk under it loaded while it is open (a Voyage end far from anyone). */
	private boolean holdChunk;

	public PegasusPortalEntity(EntityType<? extends PegasusPortalEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, HOP);
		builder.define(RADIUS, 1.0f);
		builder.define(FACE_YAW, 0f);
		builder.define(FACE_PITCH, 0f);
		builder.define(LIFE, 40);
		builder.define(OWNER_ID, -1);
	}

	/** A portal of {@code kind} centred on {@code center}, facing yaw / pitch, {@code radius} to a corner, open {@code life} ticks. */
	public void setup(ServerPlayer by, int kind, Vec3 center, float yaw, float pitch, float radius, int life) {
		owner = by.getUUID();
		entityData.set(OWNER_ID, by.getId());
		entityData.set(KIND, kind);
		entityData.set(RADIUS, radius);
		entityData.set(LIFE, life);
		face(yaw, pitch);
		setPos(center.x, center.y, center.z);
	}

	/** Where whatever passes through comes out: {@code feet} (already checked safe for the opener) in {@code dim}. */
	public void setExit(ResourceKey<Level> dim, Vec3 feet, float yaw, boolean playersOnly) {
		this.exitDim = dim;
		this.exitFeet = feet;
		this.exitYaw = yaw;
		this.playersOnly = playersOnly;
	}

	public void holdChunk() {
		holdChunk = true;
	}

	public void face(float yaw, float pitch) {
		entityData.set(FACE_YAW, yaw);
		entityData.set(FACE_PITCH, pitch);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public float radius() {
		return entityData.get(RADIUS);
	}

	public float faceYaw() {
		return entityData.get(FACE_YAW);
	}

	public float facePitch() {
		return entityData.get(FACE_PITCH);
	}

	public int lifeTicks() {
		return entityData.get(LIFE);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	public @Nullable UUID ownerId() {
		return owner;
	}

	public int age() {
		return age;
	}

	public @Nullable Vec3 exitFeet() {
		return exitFeet;
	}

	public boolean transports() {
		return exitFeet != null && exitDim != null;
	}

	/** The way it faces (unit vector). */
	public Vec3 normal() {
		return Vec3.directionFromRotation(facePitch(), faceYaw());
	}

	/** 0..1: how open it is now (it grows in and shrinks away). */
	public float openness(float partial) {
		float a = age + partial;
		float open = Mth.clamp(a / PegasusConfig.PORTAL_OPEN_TICKS, 0f, 1f);
		float close = Mth.clamp((lifeTicks() - a) / PegasusConfig.PORTAL_OPEN_TICKS, 0f, 1f);
		return Math.min(open, close);
	}

	/** Ends it now (it shrinks away over the last few ticks). */
	public void closeSoon() {
		int left = lifeTicks() - age;
		if (left > PegasusConfig.PORTAL_OPEN_TICKS) {
			entityData.set(LIFE, age + PegasusConfig.PORTAL_OPEN_TICKS);
		}
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (level().isClientSide) {
			return;
		}
		if (age >= lifeTicks()) {
			discard();
			return;
		}
		ACTIVE.add(this);
		ServerLevel sl = (ServerLevel) level();
		if (holdChunk && (age == 1 || age % 100 == 0)) {
			sl.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(blockPosition()), 2, blockPosition());
		}
		// the shimmer: motes of gold and white light round the rim
		if (age % 2 == 0) {
			Vec3 n = normal();
			Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 a = n.cross(u).normalize();
			Vec3 b = n.cross(a).normalize();
			double ang = random.nextDouble() * Math.PI * 2;
			double r = radius() * openness(0f) * 0.92;
			Vec3 p = position().add(a.scale(Math.cos(ang) * r)).add(b.scale(Math.sin(ang) * r));
			sl.sendParticles(PegasusCombat.GOLD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			if (age % 6 == 0) {
				sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.01);
			}
		}
		if (transports() && openness(0f) > 0.6f) {
			passThrough(sl);
		}
	}

	/** Everything crossing the disc this tick goes to the exit. */
	private void passThrough(ServerLevel sl) {
		Vec3 c = position();
		Vec3 n = normal();
		double r = radius();
		int tick = sl.getServer().getTickCount();
		for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(r + 2.0), e -> canPass(e))) {
			Integer until = PASSED.get(e.getUUID());
			if (until != null && tick < until) {
				continue;
			}
			Vec3 ec = e.getBoundingBox().getCenter();
			Vec3 prev = new Vec3(e.xo, e.yo + e.getBbHeight() * 0.5, e.zo);
			double d1 = ec.subtract(c).dot(n);
			double d0 = prev.subtract(c).dot(n);
			Vec3 lateral = ec.subtract(c).subtract(n.scale(d1));
			if (lateral.length() > r * 0.92) {
				continue;
			}
			boolean crossed = (d0 > 0) != (d1 > 0) || Math.abs(d1) < 0.4;
			if (crossed) {
				send(sl, e, tick);
			}
		}
	}

	private boolean canPass(Entity e) {
		if (!e.isAlive() || e.isSpectator() || e.isPassenger() || e.isVehicle() || e instanceof PegasusPortalEntity || e instanceof KwamiEntity
				|| e instanceof HorseshoeEntity) {
			return false;
		}
		if (e instanceof LivingEntity le && PegasusCombat.isBoss(le)) {
			return false; // bosses stay in their arenas
		}
		return !playersOnly || e instanceof ServerPlayer;
	}

	/** Sends {@code e} to the exit (refused, with a fizzle, if the spot is no longer safe or loaded). */
	public boolean send(ServerLevel from, Entity e, int tick) {
		if (exitDim == null || exitFeet == null) {
			return false;
		}
		ServerLevel to = from.getServer().getLevel(exitDim);
		if (to == null || (to != from && !(e instanceof ServerPlayer))) {
			return false;
		}
		BlockPos exitPos = BlockPos.containing(exitFeet);
		if (!to.hasChunkAt(exitPos)) {
			to.getChunk(exitPos); // the other end of a Voyage: loaded (its ticket keeps it so) before anyone goes
		}
		Vec3 feet = exitFeet;
		if (!PegasusTeleport.fits(to, e, feet)) {
			feet = PegasusTeleport.landing(to, e, exitFeet, 1);
		}
		PASSED.put(e.getUUID(), tick + PegasusConfig.PASS_COOLDOWN);
		if (feet == null) {
			from.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.3, 0.2, 0.01);
			return false;
		}
		Vec3 out = Vec3.directionFromRotation(0f, exitYaw);
		double speed = Math.min(1.0, e.getDeltaMovement().horizontalDistance());
		Vec3 start = e.position();
		// his own Voyage carrying him across worlds: the world change must not close it behind him
		boolean ownCrossing = to != from && e.getUUID().equals(owner);
		if (ownCrossing) {
			PegasusMoves.CROSSING.add(e.getUUID());
		}
		boolean moved;
		try {
			moved = PegasusTeleport.move(e, to, feet, exitYaw, e.getXRot(), out.scale(speed).add(0, Math.max(0.0, e.getDeltaMovement().y), 0));
		} finally {
			if (ownCrossing) {
				PegasusMoves.CROSSING.remove(e.getUUID());
			}
		}
		if (moved && ownCrossing && e instanceof ServerPlayer sp) {
			PegasusMoves.crossed(sp, sp.level().getGameTime() + (lifeTicks() - age));
		}
		if (moved) {
			from.sendParticles(PegasusCombat.GOLD_BIG, start.x, start.y + e.getBbHeight() * 0.5, start.z, 10, 0.25, 0.4, 0.25, 0.0);
			to.sendParticles(PegasusCombat.GOLD_BIG, feet.x, feet.y + e.getBbHeight() * 0.5, feet.z, 10, 0.25, 0.4, 0.25, 0.0);
			from.playSound(null, start.x, start.y, start.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
			to.playSound(null, feet.x, feet.y, feet.z, SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
		return moved;
	}

	@Override
	public void remove(RemovalReason reason) {
		ACTIVE.remove(this);
		super.remove(reason);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return new AABB(position(), position()).inflate(radius() + 0.5);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
