package com.projecthero.mod.hero.power.p09;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cryokinesis: a flying piece of ice, drawn by {@code IceBoltRenderer}.
 * <ul>
 * <li>{@link #BOLT} -- Ice Bolt (R): a long faceted ice lance, straight and fast, 10 damage + 1 Frost;</li>
 * <li>{@link #SHARD} -- Ice Shards (Shift+R): a small jagged shard, short-lived, 4 damage + 1 Frost (each shard lands
 * its own hit, so a point-blank volley can freeze a target solid outright);</li>
 * <li>{@link #DEBRIS} -- a harmless chunk of ice flung off a Shattered target.</li>
 * </ul>
 */
public class IceBoltEntity extends ThrowableProjectile {
	public static final int BOLT = 0;
	public static final int SHARD = 1;
	public static final int DEBRIS = 2;
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(IceBoltEntity.class, EntityDataSerializers.INT);
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

	private float damage;
	private int frost;
	private int life = 30;

	public IceBoltEntity(EntityType<? extends IceBoltEntity> type, Level level) {
		super(type, level);
	}

	public IceBoltEntity(Level level, LivingEntity shooter, int kind, float damage, int frost, int life) {
		super(CryoEntities.ICE_BOLT, shooter, level);
		this.entityData.set(KIND, kind);
		this.damage = damage;
		this.frost = frost;
		this.life = life;
	}

	/** A loose chunk of ice (no owner, no hit). */
	public static IceBoltEntity debris(Level level, Vec3 at, Vec3 velocity) {
		IceBoltEntity e = new IceBoltEntity(CryoEntities.ICE_BOLT, level);
		e.entityData.set(KIND, DEBRIS);
		e.life = 14 + level.random.nextInt(8);
		e.setPos(at.x, at.y, at.z);
		e.setDeltaMovement(velocity);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, BOLT);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	@Override
	protected double getDefaultGravity() {
		return switch (kind()) {
			case SHARD -> 0.015;
			case DEBRIS -> 0.05;
			default -> 0.0;
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (kind() != DEBRIS && tickCount % 2 == 0) {
				server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.0);
			}
			if (tickCount > life) {
				if (kind() != DEBRIS) {
					server.sendParticles(ICE_DUST, getX(), getY(), getZ(), 5, 0.1, 0.1, 0.1, 0.05);
				}
				discard();
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return kind() != DEBRIS && !(e instanceof IceBoltEntity) && !(e instanceof FrostShellEntity)
				&& !(e instanceof IceSpikeEntity) && super.canHitEntity(e);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel) || result.getEntity() == getOwner()) {
			return;
		}
		if (getOwner() instanceof ServerPlayer p && result.getEntity() instanceof LivingEntity e) {
			// every shard of a volley lands (hurtBurst skips the hit window a shard ahead of it opened)
			boolean landed = kind() == SHARD ? AbilityHelpers.hurtBurst(p, e, damage) : AbilityHelpers.hurtLands(p, e, damage);
			if (landed || e instanceof net.minecraft.world.entity.player.Player) {
				FrostStacks.add(p, e, frost);
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (kind() == DEBRIS) {
			if (result.getType() == HitResult.Type.BLOCK) {
				discard();
			}
			return;
		}
		if (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == getOwner()) {
			return;
		}
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			Vec3 c = result.getLocation();
			server.sendParticles(ICE_DUST, c.x, c.y, c.z, kind() == BOLT ? 14 : 5, 0.15, 0.15, 0.15, 0.1);
			server.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, kind() == BOLT ? 6 : 2, 0.2, 0.2, 0.2, 0.03);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
					kind() == BOLT ? 0.7f : 0.3f, 1.5f + server.random.nextFloat() * 0.3f);
			discard();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	}
}
