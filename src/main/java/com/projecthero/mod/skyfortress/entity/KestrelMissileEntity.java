package com.projecthero.mod.skyfortress.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: one of Admiral Kestrel's <b>micro-missiles</b>. Flies {@link #SPEED} blocks a tick and turns gently toward
 * its target ({@link #TURN} of the gap each tick) for {@link #LIFE} ticks; touching anything -- a block, an entity, or
 * coming within 1.2 of its target -- bursts it: {@link #damage} to every living thing within {@link #RADIUS} except the
 * Admiral and her drones. Never breaks blocks. Drawn as a rocket with a smoke trail.
 */
public class KestrelMissileEntity extends Projectile implements ItemSupplier {
	public static final double SPEED = 0.75;
	public static final double TURN = 0.12;
	public static final int LIFE = 90;
	public static final double RADIUS = 2.5;

	private int targetId = -1;
	public float damage = 6.0f;
	private int age;

	public KestrelMissileEntity(EntityType<? extends KestrelMissileEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	/** Aims the missile: its target (may be null = straight on). */
	public void setTarget(Entity target) {
		this.targetId = target == null ? -1 : target.getId();
	}

	public int age() {
		return age;
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(Items.FIREWORK_ROCKET);
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		Vec3 v = getDeltaMovement();
		if (level() instanceof ServerLevel server) {
			Entity target = targetId >= 0 ? server.getEntity(targetId) : null;
			if (target != null && target.isAlive()) {
				Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position());
				if (to.lengthSqr() < 1.2 * 1.2) {
					burst(server);
					return;
				}
				Vec3 want = to.normalize().scale(SPEED);
				v = v.add(want.subtract(v).scale(TURN));
				v = v.normalize().scale(SPEED);
			}
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if (hit.getType() != HitResult.Type.MISS || age > LIFE) {
				burst(server);
				return;
			}
		} else {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.01, 0);
			if (age % 2 == 0) {
				level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0, 0);
			}
		}
		setDeltaMovement(v);
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		ProjectileUtil.rotateTowardsMovement(this, 0.5f);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && !(e instanceof AdmiralKestrelBoss) && !(e instanceof KestrelMissileEntity)
				&& !e.getTags().contains(AdmiralKestrelBoss.DRONE_TAG);
	}

	/** Bursts here. Returns how many were hurt. */
	public int burst(ServerLevel level) {
		Vec3 at = position();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.4f + random.nextFloat() * 0.2f);
		Entity owner = getOwner();
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS),
				e -> e.isAlive() && !(e instanceof AdmiralKestrelBoss) && !e.getTags().contains(AdmiralKestrelBoss.DRONE_TAG)
						&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			if (e.distanceToSqr(at) > (RADIUS + 0.6) * (RADIUS + 0.6)) {
				continue;
			}
			e.hurt(owner instanceof LivingEntity l ? damageSources().mobProjectile(this, l) : damageSources().explosion(this, null), damage);
			n++;
		}
		discard();
		return n;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}
}
