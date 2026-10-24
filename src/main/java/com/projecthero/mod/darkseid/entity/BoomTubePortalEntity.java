package com.projecthero.mod.darkseid.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.miraculous.pegasus.PegasusTeleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
 * v0.17.1 (user: "change the boom tube to actual portals like Pegasus' portals, make it look like the boom tubes from the
 * comics and movies"): a real, walk-through Boom Tube. A ring of white-hot light with a tunnel of crackling blue energy
 * falling away behind it (drawn by {@code BoomTubePortalRenderer}), {@link #radius} blocks across from its centre, facing
 * its synced yaw / pitch. It bursts open with the Boom, stays open {@link #lifeTicks} and snaps shut.
 *
 * <p>An <b>entrance</b> sends whatever steps through its disc to the exit (precomputed safe feet, re-checked with
 * {@link PegasusTeleport#fits} as each one goes): players to any world, mobs and items only within the same world, never a
 * boss. The <b>exit</b> end only shows where they come out -- a Boom Tube is one-way. A traveller can't take another tube for
 * {@link #PASS_COOLDOWN} ticks. Used by Cyborg's Boom Tube menu and the Mother Box. Never saved.
 */
public class BoomTubePortalEntity extends Entity {
	public static final int OPEN_TICKS = 8;
	public static final int PASS_COOLDOWN = 40;
	/** traveller -> server tick before which it may not take a Boom Tube again. */
	private static final Map<UUID, Integer> PASSED = new ConcurrentHashMap<>();

	private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.45f, 0.75f, 1.0f), 1.4f);
	private static final DustParticleOptions KRACKLE = new DustParticleOptions(new Vector3f(0.05f, 0.08f, 0.25f), 1.8f);

	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BoomTubePortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FACE_YAW = SynchedEntityData.defineId(BoomTubePortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FACE_PITCH = SynchedEntityData.defineId(BoomTubePortalEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BoomTubePortalEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ENTRANCE = SynchedEntityData.defineId(BoomTubePortalEntity.class, EntityDataSerializers.BOOLEAN);

	private int age;
	private @Nullable ResourceKey<Level> exitDim;
	private @Nullable Vec3 exitFeet;
	private float exitYaw;

	public BoomTubePortalEntity(EntityType<? extends BoomTubePortalEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static void clearSessionState() {
		PASSED.clear();
	}

	/**
	 * Opens a one-way Boom Tube: an entrance {@code entranceDist} blocks in front of {@code by} (facing them) and its exit at
	 * {@code feet} in {@code dest}, both open for {@code life} ticks. Returns the entrance.
	 */
	public static BoomTubePortalEntity openPair(ServerPlayer by, ServerLevel dest, Vec3 feet, float exitYaw, int life) {
		ServerLevel from = by.serverLevel();
		Vec3 flat = Vec3.directionFromRotation(0f, by.getYRot());
		Vec3 centre = by.position().add(flat.scale(2.2)).add(0, 1.4, 0);
		BoomTubePortalEntity in = new BoomTubePortalEntity(DarkseidEntityTypes.BOOM_TUBE_PORTAL, from);
		in.setup(centre, by.getYRot() + 180f, 0f, 1.6f, life, true);
		in.exitDim = dest.dimension();
		in.exitFeet = feet;
		in.exitYaw = exitYaw;
		from.addFreshEntity(in);
		BoomTubePortalEntity out = new BoomTubePortalEntity(DarkseidEntityTypes.BOOM_TUBE_PORTAL, dest);
		Vec3 back = Vec3.directionFromRotation(0f, exitYaw);
		out.setup(feet.add(back.scale(-1.2)).add(0, 1.4, 0), exitYaw, 0f, 1.6f, life, false);
		dest.addFreshEntity(out);
		dest.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(BlockPos.containing(feet)), 2, BlockPos.containing(feet));
		boom(from, centre);
		boom(dest, out.position());
		return in;
	}

	private static void boom(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, DarkseidSounds.BOOM_TUBE, SoundSource.PLAYERS, 2.0f, 1.0f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.9, 0.9, 0.9, 0.4);
	}

	public void setup(Vec3 centre, float yaw, float pitch, float radius, int life, boolean entrance) {
		entityData.set(RADIUS, radius);
		entityData.set(FACE_YAW, yaw);
		entityData.set(FACE_PITCH, pitch);
		entityData.set(LIFE, life);
		entityData.set(ENTRANCE, entrance);
		setPos(centre.x, centre.y, centre.z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(RADIUS, 1.6f);
		builder.define(FACE_YAW, 0f);
		builder.define(FACE_PITCH, 0f);
		builder.define(LIFE, 200);
		builder.define(ENTRANCE, true);
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

	public boolean entrance() {
		return entityData.get(ENTRANCE);
	}

	public int age() {
		return age;
	}

	public Vec3 normal() {
		return Vec3.directionFromRotation(facePitch(), faceYaw());
	}

	/** 0..1: bursting open fast, snapping shut at the end. */
	public float openness(float partial) {
		float a = age + partial;
		float open = Mth.clamp(a / OPEN_TICKS, 0f, 1f);
		float close = Mth.clamp((lifeTicks() - a) / (OPEN_TICKS * 0.6f), 0f, 1f);
		return Math.min(open, close);
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (level().isClientSide) {
			return;
		}
		if (age >= lifeTicks()) {
			ServerLevel sl = (ServerLevel) level();
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 20, 0.5, 0.5, 0.5, 0.3);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
			discard();
			return;
		}
		ServerLevel sl = (ServerLevel) level();
		// crackle round the rim: blue sparks and the dark "Kirby krackle"
		if (age % 2 == 0) {
			Vec3 n = normal();
			Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 a = n.cross(u).normalize();
			Vec3 b = n.cross(a).normalize();
			double ang = random.nextDouble() * Math.PI * 2;
			double r = radius() * openness(0f) * (1.0 + random.nextDouble() * 0.25);
			Vec3 p = position().add(a.scale(Math.cos(ang) * r)).add(b.scale(Math.sin(ang) * r));
			sl.sendParticles(random.nextInt(3) == 0 ? KRACKLE : BLUE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
			if (age % 8 == 0) {
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.05);
			}
		}
		if (age % 30 == 0) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
		if (entrance() && exitFeet != null && openness(0f) > 0.7f) {
			passThrough(sl);
		}
	}

	private void passThrough(ServerLevel sl) {
		Vec3 c = position();
		Vec3 n = normal();
		double r = radius();
		int tick = sl.getServer().getTickCount();
		for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(r + 2.0), this::canPass)) {
			Integer until = PASSED.get(e.getUUID());
			if (until != null && tick < until) {
				continue;
			}
			Vec3 ec = e.getBoundingBox().getCenter();
			Vec3 prev = new Vec3(e.xo, e.yo + e.getBbHeight() * 0.5, e.zo);
			double d1 = ec.subtract(c).dot(n);
			double d0 = prev.subtract(c).dot(n);
			Vec3 lateral = ec.subtract(c).subtract(n.scale(d1));
			if (lateral.length() > r * 0.95) {
				continue;
			}
			if ((d0 > 0) != (d1 > 0) || Math.abs(d1) < 0.45) {
				send(sl, e, tick);
			}
		}
		if (PASSED.size() > 64) {
			PASSED.values().removeIf(t -> t < tick);
		}
	}

	private boolean canPass(Entity e) {
		if (!e.isAlive() || e.isSpectator() || e.isPassenger() || e.isVehicle() || e instanceof BoomTubePortalEntity
				|| e instanceof BoomTubeEntity) {
			return false;
		}
		return !(e instanceof LivingEntity le) || !com.projecthero.mod.titanshifter.TitanCombat.isBoss(le);
	}

	private void send(ServerLevel from, Entity e, int tick) {
		ServerLevel to = exitDim == null ? null : from.getServer().getLevel(exitDim);
		if (to == null || (to != from && !(e instanceof ServerPlayer))) {
			return;
		}
		BlockPos exitPos = BlockPos.containing(exitFeet);
		if (!to.hasChunkAt(exitPos)) {
			to.getChunk(exitPos);
		}
		Vec3 feet = exitFeet;
		if (!PegasusTeleport.fits(to, e, feet)) {
			feet = PegasusTeleport.landing(to, e, exitFeet, 1);
		}
		PASSED.put(e.getUUID(), tick + PASS_COOLDOWN);
		if (feet == null) {
			from.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.3, 0.2, 0.01);
			return;
		}
		Vec3 start = e.position();
		Vec3 out = Vec3.directionFromRotation(0f, exitYaw);
		if (PegasusTeleport.move(e, to, feet, exitYaw, e.getXRot(), out.scale(0.4))) {
			e.resetFallDistance();
			from.sendParticles(BLUE, start.x, start.y + e.getBbHeight() * 0.5, start.z, 20, 0.3, 0.5, 0.3, 0.0);
			to.sendParticles(BLUE, feet.x, feet.y + e.getBbHeight() * 0.5, feet.z, 20, 0.3, 0.5, 0.3, 0.0);
			to.sendParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 1, feet.z, 16, 0.4, 0.6, 0.4, 0.2);
			from.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
			to.playSound(null, feet.x, feet.y, feet.z, DarkseidSounds.BOOM_TUBE, SoundSource.PLAYERS, 0.8f, 1.3f);
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return new AABB(position(), position()).inflate(radius() + 4.0);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
