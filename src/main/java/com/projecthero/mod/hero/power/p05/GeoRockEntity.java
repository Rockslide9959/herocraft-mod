package com.projecthero.mod.hero.power.p05;

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
 * v0.16.5 Geokinesis R -- Rock Shot: a stone ripped out of the ground in front of you. It first <b>rises</b> out of the
 * ground to your hand (a few ticks, no collision), then flies where you are looking at that moment. It is drawn as a
 * jagged chunk of the very block it came from ({@code GeoRockRenderer}). The same entity, small, damage-less and short
 * lived, is a <b>shard</b> of a shattered boulder.
 *
 * <p>v0.16.6 hold moves: the same stone is a <b>Stone Barrage</b> stone ({@link #MODE_ORBIT}: it rises out of the ground,
 * circles the hero, then fires at the aim on its own tick) and an <b>Avalanche</b> rock ({@link #MODE_FALL}: dropped out of
 * the sky, heavy gravity, on both sides so the client sim matches).
 */
public class GeoRockEntity extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(GeoRockEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(GeoRockEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> RISE = SynchedEntityData.defineId(GeoRockEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(GeoRockEntity.class, EntityDataSerializers.INT);

	/** Rock Shot flight speed, blocks a tick. */
	public static final float SPEED = 2.2f;
	/** Ticks the stone takes to rise out of the ground to the hand. */
	public static final int RISE_TICKS = 4;

	/** A plain Rock Shot / shard in flight. */
	public static final int MODE_SHOT = 0;
	/** v0.16.6 Stone Barrage: rising into / circling in the orbit round the hero. */
	public static final int MODE_ORBIT = 1;
	/** v0.16.6 Avalanche: a rock falling out of the sky. */
	public static final int MODE_FALL = 2;
	/** Barrage: ticks a stone takes to rise out of the ground into the orbit. */
	public static final int ORBIT_RISE = 8;
	public static final double ORBIT_RADIUS = 1.45;

	private float damage;
	private boolean shard;
	private int age;
	private Vec3 riseFrom = Vec3.ZERO;
	private int orbitSlot;
	private int orbitAge;
	private int fireAt;
	/** A Stone Barrage stone (server side): its hit lands through the target's hurt cooldown. */
	private boolean barrage;

	public GeoRockEntity(EntityType<? extends GeoRockEntity> type, Level level) {
		super(type, level);
	}

	/** A Rock Shot: rises out of the ground at {@code from} to the shooter's hand, then flies. */
	public static GeoRockEntity rockShot(ServerLevel level, ServerPlayer shooter, float damage, BlockState block, Vec3 from) {
		GeoRockEntity r = new GeoRockEntity(GeoEntityTypes.GEO_ROCK, level);
		r.setOwner(shooter);
		r.damage = damage;
		r.setBlock(block);
		r.entityData.set(SCALE, 0.55f);
		r.entityData.set(RISE, RISE_TICKS);
		r.riseFrom = from;
		r.setPos(from.x, from.y, from.z);
		r.setNoGravity(true);
		return r;
	}

	/** A cosmetic shard of a shattered boulder, flying off at {@code velocity}. */
	public static GeoRockEntity shard(ServerLevel level, ServerPlayer owner, BlockState block, Vec3 at, Vec3 velocity) {
		GeoRockEntity r = new GeoRockEntity(GeoEntityTypes.GEO_ROCK, level);
		r.setOwner(owner);
		r.shard = true;
		r.setBlock(block);
		r.entityData.set(SCALE, 0.22f + level.random.nextFloat() * 0.18f);
		r.setPos(at.x, at.y, at.z);
		r.setDeltaMovement(velocity);
		return r;
	}

	/**
	 * v0.16.6 Stone Barrage: stone {@code slot} of five rises out of the ground at {@code from}, joins the orbit round the
	 * shooter and fires at the shooter's aim {@code fireAt} ticks after it was raised.
	 */
	public static GeoRockEntity barrageStone(ServerLevel level, ServerPlayer shooter, float damage, BlockState block, Vec3 from, int slot,
			int fireAt) {
		GeoRockEntity r = new GeoRockEntity(GeoEntityTypes.GEO_ROCK, level);
		r.setOwner(shooter);
		r.damage = damage;
		r.setBlock(block);
		r.entityData.set(SCALE, 0.42f + level.random.nextFloat() * 0.1f);
		r.entityData.set(MODE, MODE_ORBIT);
		r.riseFrom = from;
		r.orbitSlot = slot;
		r.fireAt = fireAt;
		r.barrage = true;
		r.setPos(from.x, from.y, from.z);
		r.setNoGravity(true);
		return r;
	}

	/** v0.16.6 Avalanche: a rock dropped out of the sky at {@code at}, falling at {@code velocity}. */
	public static GeoRockEntity avalancheRock(ServerLevel level, ServerPlayer owner, float damage, BlockState block, Vec3 at, Vec3 velocity) {
		GeoRockEntity r = new GeoRockEntity(GeoEntityTypes.GEO_ROCK, level);
		r.setOwner(owner);
		r.damage = damage;
		r.setBlock(block);
		r.entityData.set(SCALE, 0.5f + level.random.nextFloat() * 0.3f);
		r.entityData.set(MODE, MODE_FALL);
		r.setPos(at.x, at.y, at.z);
		r.setDeltaMovement(velocity);
		return r;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BLOCK, Block.getId(Blocks.STONE.defaultBlockState()));
		builder.define(SCALE, 0.55f);
		builder.define(RISE, 0);
		builder.define(MODE, MODE_SHOT);
	}

	public int mode() {
		return entityData.get(MODE);
	}

	/** Barrage: still circling the hero (not fired yet). */
	public boolean orbiting() {
		return mode() == MODE_ORBIT;
	}

	public boolean falling() {
		return mode() == MODE_FALL;
	}

	/** Barrage: the tick (of the stone's own life) it fires on. */
	public int fireAt() {
		return fireAt;
	}

	private void setBlock(BlockState s) {
		entityData.set(BLOCK, Block.getId(s));
		Item item = s.getBlock().asItem();
		setItem(new net.minecraft.world.item.ItemStack(item == Items.AIR ? Items.COBBLESTONE : item));
	}

	public BlockState block() {
		BlockState s = Block.stateById(entityData.get(BLOCK));
		return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
	}

	public float scale() {
		return entityData.get(SCALE);
	}

	/** Ticks of the rise left (0 once it flies). */
	public int rising() {
		return entityData.get(RISE);
	}

	public boolean isShard() {
		return shard;
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
		return falling() ? 0.06 : shard ? 0.05 : 0.01;
	}

	/** Barrage: circles the hero at chest height until its fire tick, then flies at the aim like a Rock Shot. */
	private void orbitTick() {
		baseTick();
		setDeltaMovement(Vec3.ZERO);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (!(getOwner() instanceof ServerPlayer p) || !p.isAlive() || p.isRemoved() || !GeokinesisHandlers.activeGeo(p)) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.05);
			discard();
			return;
		}
		orbitAge++;
		double a = orbitSlot * (Math.PI * 2 / 5.0) + orbitAge * 0.21;
		Vec3 ring = new Vec3(p.getX() + Math.cos(a) * ORBIT_RADIUS, p.getY() + 1.15 + Math.sin(orbitAge * 0.35 + orbitSlot) * 0.12,
				p.getZ() + Math.sin(a) * ORBIT_RADIUS);
		Vec3 at = ring;
		if (orbitAge <= ORBIT_RISE) {
			float f = orbitAge / (float) ORBIT_RISE;
			at = riseFrom.lerp(ring, f * f * (3 - 2 * f));
			if (orbitAge == 1) {
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), riseFrom.x, riseFrom.y + 0.2, riseFrom.z, 12, 0.25,
						0.1, 0.25, 0.08);
			}
		}
		setPos(at.x, at.y, at.z);
		if (orbitAge >= fireAt) {
			Vec3 aim = AbilityHelpers.aimPoint(p, 40.0);
			Vec3 dir = aim.subtract(position());
			if (dir.lengthSqr() < 1.0e-4) {
				dir = p.getLookAngle();
			}
			dir = dir.normalize();
			entityData.set(MODE, MODE_SHOT);
			setNoGravity(false);
			shoot(dir.x, dir.y, dir.z, SPEED, 0.4f);
			hasImpulse = true;
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.9f, 0.45f);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.6f, 0.7f);
		}
	}

	@Override
	public void tick() {
		if (orbiting()) {
			orbitTick();
			return;
		}
		int rise = rising();
		if (rise > 0) {
			// rising out of the ground to the hand: no collision, no flight yet
			baseTick();
			if (!(level() instanceof ServerLevel server)) {
				return;
			}
			if (!(getOwner() instanceof ServerPlayer p) || !p.isAlive()) {
				discard();
				return;
			}
			Vec3 hand = AbilityHelpers.handPosition(p);
			float f = 1.0f - (rise - 1) / (float) RISE_TICKS;
			Vec3 at = riseFrom.lerp(hand, f * f);
			setPos(at.x, at.y, at.z);
			if (rise == RISE_TICKS) {
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), riseFrom.x, riseFrom.y, riseFrom.z,
						14, 0.25, 0.1, 0.25, 0.08);
			}
			entityData.set(RISE, rise - 1);
			if (rise - 1 == 0) {
				Vec3 dir = p.getLookAngle();
				setNoGravity(false);
				shoot(dir.x, dir.y, dir.z, SPEED, 0.2f);
				hasImpulse = true;
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.4f);
			}
			return;
		}
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (!shard && age % 2 == 0) {
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), getX(), getY(), getZ(), 1, 0.08, 0.08,
						0.08, 0.0);
			}
			if (++age > (shard ? 30 : 80)) {
				discard();
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel) || result.getEntity() == getOwner() || shard) {
			return;
		}
		if (getOwner() instanceof ServerPlayer p && AbilityHelpers.asLiving(result.getEntity()) instanceof LivingEntity e) {
			if (falling()) {
				// v0.16.6 Avalanche: a rock dropping on it -- 6, a little shove down and out
				if (AbilityHelpers.hurtLands(p, e, damage)) {
					AbilityHelpers.push(e, new Vec3(getDeltaMovement().x * 0.1, -0.1, getDeltaMovement().z * 0.1));
				}
				return;
			}
			// a Barrage stone is one of five discrete hits a few ticks apart: each lands its full 8 (never on players' i-frames)
			boolean landed = barrage ? AbilityHelpers.hurtBurst(p, e, damage) : AbilityHelpers.hurtLands(p, e, damage);
			if (landed) {
				AbilityHelpers.knockbackFrom(e, position().subtract(getDeltaMovement()), barrage ? 0.45 : 0.9);
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (result.getType() == HitResult.Type.ENTITY) {
			var hit = ((EntityHitResult) result).getEntity();
			if (hit == getOwner() || hit instanceof GeoRockEntity || hit instanceof GeoBoulderEntity) {
				return;
			}
		}
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			Vec3 c = result.getLocation();
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), c.x, c.y, c.z, shard ? 5 : 20, 0.25, 0.25,
					0.25, 0.12);
			if (!shard) {
				server.playSound(null, c.x, c.y, c.z, block().getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.0f, 0.8f);
				server.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8f, 0.6f);
			}
			discard();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
	}
}
