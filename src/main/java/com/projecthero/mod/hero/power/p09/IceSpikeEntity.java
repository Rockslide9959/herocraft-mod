package com.projecthero.mod.hero.power.p09;

import java.util.Set;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cryokinesis: one jagged ice spike of Glacier Spikes (Z) or the Absolute Zero crown (Shift+Z), drawn by
 * {@code IceSpikeRenderer} as a cluster of tapered ice crystals. It waits {@code delay} ticks (so a line of them
 * erupts as a travelling wave), bursts out of the ground over 3 ticks, stands, then cracks and sinks away. On eruption
 * a damaging spike hits every enemy over it once per volley ({@code hitSet} is shared by the volley's spikes).
 */
public class IceSpikeEntity extends Entity {
	private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.FLOAT);
	/** Tilt of the spike away from the caster, and its spin (radians). */
	private static final EntityDataAccessor<Float> TILT = SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.FLOAT);
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());

	private ServerPlayer owner;
	private float damage;
	private int frost;
	private double radius = 1.3;
	private Set<LivingEntity> hitSet;

	public IceSpikeEntity(EntityType<? extends IceSpikeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/**
	 * Spawns a spike at {@code at} (its base) facing away from {@code from}. {@code damage} 0 = a harmless spike.
	 */
	public static IceSpikeEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 from, int delay, int life, float height,
			float damage, int frost, Set<LivingEntity> hitSet) {
		IceSpikeEntity e = new IceSpikeEntity(CryoEntities.ICE_SPIKE, level);
		e.moveTo(at.x, at.y, at.z, (float) (Math.atan2(at.z - from.z, at.x - from.x) * 180.0 / Math.PI) - 90.0f, 0.0f);
		e.entityData.set(DELAY, delay);
		e.entityData.set(LIFE, life);
		e.entityData.set(HEIGHT, height);
		e.entityData.set(TILT, 0.12f + level.random.nextFloat() * 0.22f);
		e.entityData.set(SPIN, level.random.nextFloat() * (float) Math.PI * 2);
		e.owner = owner;
		e.damage = damage;
		e.frost = frost;
		e.hitSet = hitSet;
		level.addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DELAY, 0);
		builder.define(LIFE, 40);
		builder.define(HEIGHT, 2.0f);
		builder.define(TILT, 0.2f);
		builder.define(SPIN, 0.0f);
	}

	public int delay() {
		return entityData.get(DELAY);
	}

	public int life() {
		return entityData.get(LIFE);
	}

	public float height() {
		return entityData.get(HEIGHT);
	}

	public float tilt() {
		return entityData.get(TILT);
	}

	public float spin() {
		return entityData.get(SPIN);
	}

	/** How far out of the ground (0..1) the spike is at {@code age} ticks: bursts out over 3, sinks over the last 8. */
	public static float rise(float age, int delay, int life) {
		float t = age - delay;
		if (t <= 0) {
			return 0.0f;
		}
		if (t < 3) {
			float f = t / 3.0f;
			return 1.0f - (1.0f - f) * (1.0f - f) + (float) Math.sin(f * Math.PI) * 0.15f; // overshoots a touch
		}
		float left = life - age;
		return left < 8 ? Math.max(0.0f, left / 8.0f) : 1.0f;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		int delay = delay();
		if (tickCount == delay + 1) {
			erupt(server);
		}
		if (tickCount == life() - 8) {
			server.sendParticles(ICE_DUST, getX(), getY() + height() * 0.5, getZ(), 6, 0.2, height() * 0.3, 0.2, 0.05);
		}
		if (tickCount >= life()) {
			discard();
		}
	}

	private void erupt(ServerLevel server) {
		server.sendParticles(ICE_DUST, getX(), getY() + 0.1, getZ(), 10, 0.35, 0.05, 0.35, 0.15);
		server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + height() * 0.6, getZ(), 4, 0.2, 0.4, 0.2, 0.03);
		if (tickCount % 2 == 0 || damage > 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f,
					0.6f + server.random.nextFloat() * 0.4f);
		}
		if (owner == null || damage <= 0 || hitSet == null) {
			return;
		}
		Vec3 base = position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(owner, base.add(0, 0.8, 0), radius + 0.6)) {
			if (e.getY() > base.y + height() + 0.5 || !hitSet.add(e)) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(owner, e, damage) || e instanceof net.minecraft.world.entity.player.Player) {
				FrostStacks.add(owner, e, frost);
			}
			AbilityHelpers.push(e, new Vec3(0, 0.55, 0));
		}
	}

	@Override
	public boolean isPickable() {
		return false;
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
