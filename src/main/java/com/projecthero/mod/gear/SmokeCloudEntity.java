package com.projecthero.mod.gear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the Smoke Bomb's cloud. For its lifetime every mob inside it is blinded and keeps losing its target
 * (goal target, brain attack memory, anger and the "who hurt me" memory), so it wanders instead of chasing. Players are
 * never affected. The smoke itself is client-side particles from this entity's tick (no packets).
 */
public class SmokeCloudEntity extends Entity {
	public static final float RADIUS = 4.0f;
	public static final float UPGRADED_RADIUS = 6.0f;
	public static final int DURATION = 140;
	public static final int UPGRADED_DURATION = 220;

	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.FLOAT);

	private int duration = DURATION;

	public SmokeCloudEntity(EntityType<? extends SmokeCloudEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static SmokeCloudEntity spawn(Level level, Vec3 at, LivingEntity owner, boolean upgraded) {
		SmokeCloudEntity cloud = new SmokeCloudEntity(GearItems.SMOKE_CLOUD, level);
		cloud.setPos(at.x, at.y + 0.1, at.z);
		cloud.entityData.set(DATA_RADIUS, upgraded ? UPGRADED_RADIUS : RADIUS);
		cloud.duration = upgraded ? UPGRADED_DURATION : DURATION;
		level.addFreshEntity(cloud);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8f, 0.5f);
		cloud.affectMobs(); // works the moment it pops
		return cloud;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_RADIUS, RADIUS);
	}

	public float radius() {
		return entityData.get(DATA_RADIUS);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			puff();
			return;
		}
		if (tickCount % 3 == 0) {
			affectMobs();
		}
		if (tickCount > duration) {
			discard();
		}
	}

	private void puff() {
		RandomSource r = random;
		float rad = radius();
		int n = tickCount < 10 ? 14 : 4;
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * rad;
			double y = r.nextDouble() * rad * 0.6;
			level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, getX() + Math.cos(a) * d, getY() + y,
					getZ() + Math.sin(a) * d, (r.nextDouble() - 0.5) * 0.02, 0.003 + r.nextDouble() * 0.01, (r.nextDouble() - 0.5) * 0.02);
		}
		if (tickCount < 6) {
			for (int i = 0; i < 8; i++) {
				level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), (r.nextDouble() - 0.5) * 0.5, r.nextDouble() * 0.2,
						(r.nextDouble() - 0.5) * 0.5);
			}
		}
	}

	/** Blinds every mob in the cloud and wipes its target. */
	public void affectMobs() {
		float rad = radius();
		AABB box = new AABB(position(), position()).inflate(rad, rad * 0.75, rad).move(0, rad * 0.4, 0);
		for (Mob mob : level().getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.distanceToSqr(this) <= rad * rad * 1.3)) {
			blind(mob, 60);
		}
	}

	/** Shared with the Flashbang: blindness + the mob forgets who it was fighting. */
	public static void blind(Mob mob, int blindTicks) {
		mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0, false, true));
		loseTarget(mob);
	}

	/** Bosses (the c:bosses tag, or more than {@link #BOSS_HEALTH} max health) shrug gadgets off: blind, but still locked on. */
	public static boolean resists(Mob mob) {
		return mob.getMaxHealth() > BOSS_HEALTH
				|| mob.getType().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES);
	}

	public static final float BOSS_HEALTH = 100f;

	public static void loseTarget(Mob mob) {
		if (resists(mob)) {
			return;
		}
		mob.setTarget(null);
		mob.setLastHurtByMob(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
		if (mob instanceof NeutralMob n) {
			n.stopBeingAngry();
		}
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
}
