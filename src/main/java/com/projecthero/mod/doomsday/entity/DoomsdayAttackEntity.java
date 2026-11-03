package com.projecthero.mod.doomsday.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.doomsday.DoomsdayFx;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Doomsday's moves out in the world, one small entity per piece (never saved):
 * <ul>
 *   <li>{@link #SPIKE} -- a bone spike in the ground: glowing cracks for {@code warn} ticks, then it bursts up (damage and
 *       a launch within 0.9 blocks) and stands for a second before crumbling. Bone Eruption's line toward the target.</li>
 *   <li>{@link #SHARD} -- a thrown bone shard (Bone Volley): flies straight, hits the first thing it touches.</li>
 *   <li>{@link #RING} -- Earthshatter's ground wave: a ring growing from his feet; anyone on the ground at its edge is hit
 *       once. Jump it.</li>
 * </ul>
 */
public class DoomsdayAttackEntity extends Entity {
	public static final byte SPIKE = 0, SHARD = 1, RING = 2;
	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(DoomsdayAttackEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_WARN = SynchedEntityData.defineId(DoomsdayAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DoomsdayAttackEntity.class, EntityDataSerializers.FLOAT);

	private UUID ownerId;
	private float damage;
	private double maxRadius;
	private double speed;
	private final Set<UUID> hit = new HashSet<>();

	public DoomsdayAttackEntity(EntityType<? extends DoomsdayAttackEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static DoomsdayAttackEntity spike(ServerLevel level, DoomsdayEntity owner, Vec3 at, int warn, float damage) {
		DoomsdayAttackEntity e = DoomsdayEntityTypes.ATTACK.create(level);
		e.setPos(at.x, at.y, at.z);
		e.entityData.set(DATA_KIND, SPIKE);
		e.entityData.set(DATA_WARN, warn);
		e.ownerId = owner.getUUID();
		e.damage = damage;
		level.addFreshEntity(e);
		return e;
	}

	public static DoomsdayAttackEntity shard(ServerLevel level, DoomsdayEntity owner, Vec3 from, Vec3 velocity, float damage) {
		DoomsdayAttackEntity e = DoomsdayEntityTypes.ATTACK.create(level);
		e.setPos(from.x, from.y, from.z);
		e.entityData.set(DATA_KIND, SHARD);
		e.setDeltaMovement(velocity);
		e.ownerId = owner.getUUID();
		e.damage = damage;
		level.addFreshEntity(e);
		return e;
	}

	public static DoomsdayAttackEntity ring(ServerLevel level, DoomsdayEntity owner, Vec3 at, double maxRadius, double speed, float damage) {
		DoomsdayAttackEntity e = DoomsdayEntityTypes.ATTACK.create(level);
		e.setPos(at.x, at.y, at.z);
		e.entityData.set(DATA_KIND, RING);
		e.ownerId = owner.getUUID();
		e.damage = damage;
		e.maxRadius = maxRadius;
		e.speed = speed;
		level.addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, SPIKE);
		builder.define(DATA_WARN, 10);
		builder.define(DATA_RADIUS, 0f);
	}

	public byte kind() {
		return entityData.get(DATA_KIND);
	}

	public int warn() {
		return entityData.get(DATA_WARN);
	}

	public float ringRadius() {
		return entityData.get(DATA_RADIUS);
	}

	private LivingEntity owner(ServerLevel level) {
		return ownerId != null && level.getEntity(ownerId) instanceof LivingEntity le ? le : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			if (kind() == SHARD) {
				Vec3 v = getDeltaMovement();
				setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
			}
			return;
		}
		switch (kind()) {
			case SPIKE -> tickSpike(level);
			case SHARD -> tickShard(level);
			default -> tickRing(level);
		}
	}

	private void tickSpike(ServerLevel level) {
		int warn = warn();
		if (tickCount < warn) {
			if (tickCount % 3 == 0) {
				level.sendParticles(DoomsdayFx.RED, getX(), getY() + 0.05, getZ(), 2, 0.3, 0.02, 0.3, 0);
				level.sendParticles(DoomsdayFx.STONE, getX(), getY() + 0.1, getZ(), 1, 0.25, 0.02, 0.25, 0);
			}
			return;
		}
		if (tickCount == warn) {
			LivingEntity owner = owner(level);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.6f, 0.6f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.4f, 0.5f);
			level.sendParticles(DoomsdayFx.BONE, getX(), getY() + 0.8, getZ(), 10, 0.25, 0.6, 0.25, 0.05);
			DoomsdayFx.forced(level, DoomsdayFx.DIRT, getX(), getY() + 0.2, getZ(), 12, 0.4, 0.2, 0.4, 0.2);
			for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(0.9, 2.2, 0.9)
					.move(0, 1.0, 0), DoomsdayEntity::canTarget)) {
				if (DoomsdayEntity.hit(level, owner, t, damage)) {
					t.setDeltaMovement(t.getDeltaMovement().add(0, 0.9, 0));
					t.hurtMarked = true;
				}
			}
		}
		if (tickCount > warn + 24) {
			level.sendParticles(DoomsdayFx.BONE, getX(), getY() + 0.6, getZ(), 6, 0.2, 0.5, 0.2, 0.02);
			discard();
		}
	}

	private void tickShard(ServerLevel level) {
		Vec3 from = position();
		Vec3 v = getDeltaMovement();
		Vec3 to = from.add(v);
		LivingEntity owner = owner(level);
		HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (block.getType() != HitResult.Type.MISS) {
			to = block.getLocation();
		}
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.6), DoomsdayEntity::canTarget)) {
			AABB fat = t.getBoundingBox().inflate(0.35);
			if (fat.contains(from) || fat.clip(from, to).isPresent()) {
				if (DoomsdayEntity.hit(level, owner, t, damage)) {
					t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
				}
				level.sendParticles(DoomsdayFx.BONE, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
				discard();
				return;
			}
		}
		if (block.getType() != HitResult.Type.MISS) {
			level.sendParticles(DoomsdayFx.BONE, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.05);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.BONE_BLOCK_HIT, SoundSource.HOSTILE, 1.0f, 0.8f);
			discard();
			return;
		}
		setPos(to.x, to.y, to.z);
		setDeltaMovement(v.add(0, -0.015, 0));
		if (tickCount > 60) {
			discard();
		}
	}

	private void tickRing(ServerLevel level) {
		double r = Math.min(maxRadius, tickCount * speed);
		entityData.set(DATA_RADIUS, (float) r);
		LivingEntity owner = owner(level);
		Vec3 c = position();
		if (tickCount % 2 == 0) {
			DoomsdayFx.ring(level, DoomsdayFx.DIRT, c.add(0, 0.2, 0), r, (int) Math.min(160, r * 5 + 8));
			DoomsdayFx.ring(level, DoomsdayFx.ASH, c.add(0, 0.5, 0), r, (int) Math.min(100, r * 3 + 6));
		}
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1.0, 3.0, r + 1.0), DoomsdayEntity::canTarget)) {
			if (hit.contains(t.getUUID())) {
				continue;
			}
			double d = Math.sqrt(t.distanceToSqr(c.x, t.getY(), c.z));
			if (Math.abs(d - r) > 1.0) {
				continue;
			}
			// on the ground (within 1.25 of the wave's height and touching ground) -- a jump clears it
			double above = t.getY() - c.y;
			boolean grounded = above < 1.0 && above > -2.0;
			if (!grounded) {
				continue;
			}
			hit.add(t.getUUID());
			if (DoomsdayEntity.hit(level, owner, t, damage)) {
				Vec3 out = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z).normalize();
				t.setDeltaMovement(t.getDeltaMovement().add(out.x * 0.8, 0.55, out.z * 0.8));
				t.hurtMarked = true;
			}
		}
		if (r >= maxRadius) {
			discard();
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	/** The ground under {@code (x, z)} near {@code y} (for placing spikes). */
	public static double ground(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int bx = net.minecraft.util.Mth.floor(x);
		int bz = net.minecraft.util.Mth.floor(z);
		for (int yy = (int) Math.floor(y) + 4; yy > y - 12; yy--) {
			p.set(bx, yy, bz);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return yy + 1;
			}
		}
		return y;
	}
}
