package com.projecthero.mod.ultron.entity;

import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: a <b>relay pylon</b> -- a node of Ultron's network. A ~4-block tower that rises out of the ground round the
 * uplink, with a pulsing red eye and a thin red beam to the uplink (drawn by the client from the synced uplink position).
 * It never moves and has no brain; it is a target.
 * <ul>
 *   <li>While it stands, Ultron's units within 16 blocks of it repair themselves (the uprising applies that).</li>
 *   <li>While any stands, the boss can't come (the uprising's gate), and Ultron Prime can jump to it when his body falls.</li>
 *   <li>Destroyed, it bursts in an EMP that stuns every robot within 12 blocks for 3 seconds.</li>
 * </ul>
 */
public class UltronPylonEntity extends UltronRobot {
	public static final int RISE_TICKS = 40;
	public static final float EYE_HEIGHT = 3.75f;
	private static final EntityDataAccessor<BlockPos> DATA_UPLINK = SynchedEntityData.defineId(UltronPylonEntity.class, EntityDataSerializers.BLOCK_POS);
	private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(UltronPylonEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> DATA_RESERVE = SynchedEntityData.defineId(UltronPylonEntity.class, EntityDataSerializers.BOOLEAN);

	public UltronPylonEntity(EntityType<? extends UltronPylonEntity> type, Level level) {
		super(type, level);
		this.xpReward = 15;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, UltronConfig.arena().pylonHealth)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_UPLINK, BlockPos.ZERO);
		builder.define(DATA_BORN, 0L);
		builder.define(DATA_RESERVE, false);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.DRONE; // never drawn with a skin (the pylon has its own renderer)
	}

	@Override
	protected void registerGoals() {
	}

	/** Sets it up: its uplink, its health for {@code fighters}, whether it is one of Prime's reserve bodies. */
	public void configure(BlockPos uplink, double health, boolean reserve) {
		entityData.set(DATA_UPLINK, uplink.immutable());
		entityData.set(DATA_RESERVE, reserve);
		entityData.set(DATA_BORN, level().getGameTime());
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	public BlockPos uplink() {
		return entityData.get(DATA_UPLINK);
	}

	public boolean isReserve() {
		return entityData.get(DATA_RESERVE);
	}

	/** How far through its rise out of the ground (0..1), for the renderer. */
	public float rise(float partialTick) {
		long born = entityData.get(DATA_BORN);
		float age = (float) (level().getGameTime() - born) + partialTick;
		return born == 0L ? 1f : Math.max(0f, Math.min(1f, age / RISE_TICKS));
	}

	/** Where its eye is (the beam to the uplink starts here). */
	public Vec3 eye() {
		return position().add(0, EYE_HEIGHT, 0);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		if (level() instanceof ServerLevel server && !isDeadOrDying()) {
			float r = rise(0f);
			if (r < 1f && tickCount % 2 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.5, 0.1, 0.5, 0.01);
				server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + r * EYE_HEIGHT, getZ(), 2, 0.4, 0.2, 0.4, 0.01);
			}
			if (tickCount % 40 == 0) {
				server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.8f, 0.6f);
			}
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			emp(server);
		}
	}

	/** The EMP burst: every robot within {@link UltronConfig.Arena#empRadius} is stunned. */
	public void emp(ServerLevel server) {
		UltronConfig.Arena cfg = UltronConfig.arena();
		Vec3 c = eye();
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.5f);
		UltronFx.forced(server, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		UltronFx.forced(server, UltronFx.CYAN, c.x, c.y, c.z, 60, cfg.empRadius * 0.3, 1.0, cfg.empRadius * 0.3, 0.1);
		UltronFx.forced(server, ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 60, 2.0, 1.5, 2.0, 0.5);
		UltronFx.ring(server, UltronFx.CYAN, position().add(0, 0.5, 0), cfg.empRadius, 48);
		int ticks = cfg.empSeconds * 20;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.empRadius), x -> x instanceof UltronRobot)) {
			if (e != this && !(e instanceof UltronPylonEntity) && e.distanceToSqr(this) <= cfg.empRadius * cfg.empRadius) {
				((UltronRobot) e).stun(ticks);
			}
		}
		for (ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 64 * 64) {
				JarvisDialogue.speak(p, "ultron_pylon_down");
			}
		}
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server && !isRemoved()) {
			if (deathTime % 3 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 4, 0.4, 1.2, 0.4, 0.02);
			}
			if (deathTime >= 12) {
				UltronFx.wreck(server, position().add(0, 2, 0), 2.0);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5f;
	}


	/** Test hook: is {@code e} within this pylon's repair field? */
	public boolean repairs(LivingEntity e) {
		double r = UltronConfig.arena().repairRadius;
		return e.distanceToSqr(this) <= r * r;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.put("Uplink", NbtUtils.writeBlockPos(uplink()));
		tag.putLong("Born", entityData.get(DATA_BORN));
		tag.putBoolean("Reserve", isReserve());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		NbtUtils.readBlockPos(tag, "Uplink").ifPresent(p -> entityData.set(DATA_UPLINK, p));
		entityData.set(DATA_BORN, tag.getLong("Born"));
		entityData.set(DATA_RESERVE, tag.getBoolean("Reserve"));
	}

}
