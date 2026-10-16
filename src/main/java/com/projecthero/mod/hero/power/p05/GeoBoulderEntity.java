package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Geokinesis Shift+R -- Boulder Toss: a huge boulder torn out of the ground. While the key is held it is
 * <b>lifted</b>: it heaves up out of the ground in front of you and settles over your head ({@link #LIFT_TICKS}), riding
 * along there. Released, it is thrown where you look and, on whatever it hits, deals {@code damage} to everything within
 * {@link #RADIUS} and <b>shatters</b> into flying shards ({@link GeoRockEntity#shard}). Drawn as a cluster of the
 * ground's own block ({@code GeoBoulderRenderer}).
 */
public class GeoBoulderEntity extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(GeoBoulderEntity.class, EntityDataSerializers.INT);
	/** Lift progress 0..LIFT_TICKS while held; -1 once thrown. */
	private static final EntityDataAccessor<Integer> LIFT = SynchedEntityData.defineId(GeoBoulderEntity.class, EntityDataSerializers.INT);

	public static final int LIFT_TICKS = 12;
	public static final double RADIUS = 3.0;
	public static final float SPEED = 1.5f;
	public static final int SHARDS = 10;

	private float damage;
	private Vec3 liftFrom = Vec3.ZERO;
	private int flight;
	private boolean shattered;

	public GeoBoulderEntity(EntityType<? extends GeoBoulderEntity> type, Level level) {
		super(type, level);
	}

	public static GeoBoulderEntity lift(ServerLevel level, ServerPlayer owner, float damage, BlockState block, Vec3 from) {
		GeoBoulderEntity b = new GeoBoulderEntity(GeoEntityTypes.GEO_BOULDER, level);
		b.setOwner(owner);
		b.damage = damage;
		b.entityData.set(BLOCK, Block.getId(block));
		Item item = block.getBlock().asItem();
		b.setItem(new net.minecraft.world.item.ItemStack(item == Items.AIR ? Items.COBBLESTONE : item));
		b.entityData.set(LIFT, 0);
		b.liftFrom = from;
		b.setPos(from.x, from.y, from.z);
		b.setNoGravity(true);
		return b;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BLOCK, Block.getId(Blocks.STONE.defaultBlockState()));
		builder.define(LIFT, 0);
	}

	public BlockState block() {
		BlockState s = Block.stateById(entityData.get(BLOCK));
		return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
	}

	/** Lift progress 0..1 while held, else 1. */
	public float liftFraction() {
		int l = entityData.get(LIFT);
		return l < 0 ? 1.0f : Math.min(1.0f, l / (float) LIFT_TICKS);
	}

	public boolean held() {
		return entityData.get(LIFT) >= 0;
	}

	public boolean fullyLifted() {
		return entityData.get(LIFT) >= LIFT_TICKS;
	}

	public float damage() {
		return damage;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.03;
	}

	/** Where the boulder rides while held: just over the owner's head. */
	private static Vec3 overhead(Entity owner) {
		return owner.position().add(0, owner.getBbHeight() + 0.05, 0);
	}

	/** Releases a held boulder where the owner looks. */
	public void toss() {
		if (!held() || !(getOwner() instanceof ServerPlayer p)) {
			return;
		}
		entityData.set(LIFT, -1);
		setNoGravity(false);
		Vec3 dir = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(dir.scale(1.6)).add(0, 0.6, 0);
		setPos(from.x, from.y, from.z);
		shoot(dir.x, dir.y + 0.08, dir.z, SPEED, 0.0f);
		hasImpulse = true;
		if (level() instanceof ServerLevel server) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
	}

	@Override
	public void tick() {
		if (held()) {
			baseTick();
			if (!(level() instanceof ServerLevel server)) {
				return;
			}
			if (!(getOwner() instanceof ServerPlayer p) || !p.isAlive() || p.level() != level()) {
				discard();
				return;
			}
			int l = entityData.get(LIFT);
			Vec3 at;
			if (l < LIFT_TICKS) {
				float f = (l + 1) / (float) LIFT_TICKS;
				float ease = f * f * (3 - 2 * f);
				Vec3 top = overhead(p);
				at = liftFrom.lerp(top, ease).add(0, Math.sin(f * Math.PI) * 0.8, 0);
				entityData.set(LIFT, l + 1);
				if (l % 3 == 0) {
					server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), liftFrom.x, liftFrom.y + 0.2, liftFrom.z,
							10, 0.6, 0.1, 0.6, 0.1);
				}
			} else {
				at = overhead(p).add(0, Math.sin(tickCount * 0.25) * 0.05, 0);
			}
			setPos(at.x, at.y, at.z);
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (++flight % 2 == 0) {
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), getX(), getY(), getZ(), 3, 0.4, 0.4, 0.4,
						0.0);
			}
			if (flight > 100) {
				shatter(server, position());
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (held() || shattered) {
			return;
		}
		if (result.getType() == HitResult.Type.ENTITY) {
			Entity hit = ((EntityHitResult) result).getEntity();
			if (hit == getOwner() || hit instanceof GeoRockEntity) {
				return;
			}
		}
		if (level() instanceof ServerLevel server) {
			shatter(server, result.getLocation());
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		// the damage is the shatter's area hit, in onHit
	}

	/** The impact: {@code damage} to everything within 3 blocks, then the boulder bursts into shards. */
	public int shatter(ServerLevel server, Vec3 at) {
		if (shattered) {
			return 0;
		}
		shattered = true;
		int hit = 0;
		if (getOwner() instanceof ServerPlayer p) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, RADIUS)) {
				if (AbilityHelpers.hurtLands(p, e, damage)) {
					hit++;
				}
				AbilityHelpers.knockbackFrom(e, at, 1.1);
			}
			for (int i = 0; i < SHARDS; i++) {
				double a = i / (double) SHARDS * Math.PI * 2 + server.random.nextDouble() * 0.4;
				Vec3 v = new Vec3(Math.cos(a) * 0.45, 0.35 + server.random.nextDouble() * 0.35, Math.sin(a) * 0.45);
				server.addFreshEntity(GeoRockEntity.shard(server, p, block(), at.add(0, 0.6, 0), v));
			}
		}
		server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), at.x, at.y + 0.5, at.z, 70, 1.2, 0.8, 1.2, 0.2);
		server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		server.playSound(null, at.x, at.y, at.z, block().getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.6f, 0.5f);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
		discard();
		return hit;
	}
}
