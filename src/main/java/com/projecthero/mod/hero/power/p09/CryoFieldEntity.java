package com.projecthero.mod.hero.power.p09;

import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cryokinesis hold moves that sit on the world for a while, drawn by {@code CryoFieldRenderer}. Never saved;
 * discarded with their caster ({@link CryoHoldMoves#endAll}).
 * <ul>
 * <li>{@link #GROUND} -- <b>Frozen Ground</b> (hold G): a sheet of ice spreading over the ground in a
 * {@link CryokinesisHandlers#GROUND_RADIUS}-block radius round where it was cast, frost crystals scattered over it.
 * Enemies standing on it are slowed (Slowness II) and slip about, and take 1 Frost every 2 s.</li>
 * <li>{@link #BLIZZARD} -- <b>Blizzard</b> (hold Z): a swirling column of snow {@link CryokinesisHandlers#BLIZZARD_RADIUS}
 * blocks round under a churning cloud. Enemies inside get Slowness III, 1 Frost a second and blinding snow (players
 * Blindness, mobs lose their target).</li>
 * </ul>
 * Bosses take the Frost but are never slowed, slid or blinded. Both last {@link CryokinesisHandlers#FIELD_TICKS}.
 */
public class CryoFieldEntity extends Entity {
	public static final int GROUND = 0;
	public static final int BLIZZARD = 1;
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CryoFieldEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(CryoFieldEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(CryoFieldEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	/** Client-side cache for the renderer (the ground cells the sheet lies on). */
	public Object renderCache;

	public CryoFieldEntity(EntityType<? extends CryoFieldEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static CryoFieldEntity spawn(ServerLevel level, ServerPlayer owner, int kind, Vec3 at, float radius, int life) {
		CryoFieldEntity e = new CryoFieldEntity(CryoEntities.CRYO_FIELD, level);
		e.entityData.set(KIND, kind);
		e.entityData.set(RADIUS, radius);
		e.entityData.set(LIFE, life);
		e.owner = owner.getUUID();
		e.moveTo(at.x, at.y, at.z);
		return level.addFreshEntity(e) ? e : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, GROUND);
		builder.define(RADIUS, 5.0f);
		builder.define(LIFE, 160);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public float radius() {
		return entityData.get(RADIUS);
	}

	public int life() {
		return entityData.get(LIFE);
	}

	public UUID owner() {
		return owner;
	}

	/** Whether {@code e} is inside this field (on the sheet / in the storm). */
	public boolean contains(LivingEntity e) {
		double dx = e.getX() - getX();
		double dz = e.getZ() - getZ();
		float r = radius();
		if (dx * dx + dz * dz > r * r) {
			return false;
		}
		double dy = e.getY() - getY();
		return kind() == GROUND ? dy > -2.5 && dy < 2.5 && (e.onGround() || dy < 0.6)
				: dy > -3.0 && dy < CryokinesisHandlers.BLIZZARD_HEIGHT;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) {
			return;
		}
		ServerPlayer p = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
		if (tickCount > life() || p == null || !p.isAlive() || p.level() != sl) {
			end(sl);
			return;
		}
		if (kind() == GROUND) {
			groundTick(sl, p);
		} else {
			blizzardTick(sl, p);
		}
	}

	private void groundTick(ServerLevel sl, ServerPlayer p) {
		float r = radius();
		float reach = r * Math.min(1.0f, tickCount / 14.0f);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, position(), r + 1.0)) {
			if (!contains(e) || e.distanceToSqr(getX(), e.getY(), getZ()) > reach * reach) {
				continue;
			}
			boolean boss = !CryokinesisHandlers.notBoss(e);
			if (!boss) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
				// slipping on the ice: every so often the feet go out sideways
				if ((tickCount + e.getId()) % 16 == 0 && e.onGround()) {
					double a = sl.random.nextDouble() * Math.PI * 2;
					Vec3 v = e.getDeltaMovement();
					AbilityHelpers.push(e, new Vec3(Math.cos(a) * 0.32 + v.x * 0.6, 0.0, Math.sin(a) * 0.32 + v.z * 0.6));
					sl.sendParticles(CryokinesisHandlers.ICE_DUST, e.getX(), e.getY() + 0.05, e.getZ(), 4, 0.2, 0.02, 0.2, 0.05);
				}
			}
			if (tickCount % CryokinesisHandlers.GROUND_FROST_TICKS == CryokinesisHandlers.GROUND_FROST_TICKS / 2) { // 1 s in, then every 2 s
				FrostStacks.add(p, e, 1);
			}
		}
		if (tickCount % 4 == 0) {
			double a = sl.random.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(sl.random.nextDouble()) * reach;
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX() + Math.cos(a) * d, getY() + 0.15, getZ() + Math.sin(a) * d, 2, 0.3, 0.05, 0.3, 0.0);
		}
		if (tickCount == 1) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
		}
	}

	private void blizzardTick(ServerLevel sl, ServerPlayer p) {
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, position(), radius() + CryokinesisHandlers.BLIZZARD_HEIGHT)) {
			if (!contains(e)) {
				continue;
			}
			boolean boss = !CryokinesisHandlers.notBoss(e);
			if (!boss) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 2);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 40, 0);
				if (e instanceof Mob mob && (tickCount + e.getId()) % 40 == 0 && mob.getTarget() != null) {
					mob.setTarget(null); // lost in the snow
				}
			}
			if (tickCount % CryokinesisHandlers.BLIZZARD_FROST_TICKS == CryokinesisHandlers.BLIZZARD_FROST_TICKS / 2) {
				FrostStacks.add(p, e, 1);
				e.clearFire();
			}
		}
		if (tickCount % 2 == 0) {
			float r = radius();
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + CryokinesisHandlers.BLIZZARD_HEIGHT * 0.5, getZ(), 6,
					r * 0.5, CryokinesisHandlers.BLIZZARD_HEIGHT * 0.3, r * 0.5, 0.08);
		}
		if (tickCount % 20 == 1) {
			sl.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.WEATHER, 1.6f, 0.4f);
			sl.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.25f, 0.5f);
		}
	}

	/** Melts away (the sheet cracks, the storm blows out). */
	public void end(ServerLevel sl) {
		if (isRemoved()) {
			return;
		}
		float r = radius();
		if (kind() == GROUND) {
			sl.sendParticles(CryokinesisHandlers.ICE_DUST, getX(), getY() + 0.1, getZ(), 40, r * 0.5, 0.05, r * 0.5, 0.05);
		} else {
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 3, getZ(), 40, r * 0.5, 2.0, r * 0.5, 0.1);
		}
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
