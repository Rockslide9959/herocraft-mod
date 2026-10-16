package com.projecthero.mod.hero.power.p24;

import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Manipulation's R / Shift+R (v0.16.5): a crescent of compressed air flying straight along the aim. It passes
 * <b>through</b> everything it can harm, cutting each creature once, and breaks up on the first block it meets or at
 * the end of its range. The client draws the swept crescent ({@code WindBladeRenderer}) from the synced size and roll.
 */
public class WindBladeEntity extends Entity {
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(WindBladeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(WindBladeEntity.class, EntityDataSerializers.FLOAT);

	private UUID owner;
	private float damage;
	private int life;
	private final Set<Integer> cut = new HashSet<>();

	public WindBladeEntity(EntityType<? extends WindBladeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * A blade from {@code owner}'s eyes along {@code dir}: {@code speed} blocks a tick for {@code range} blocks, {@code
	 * damage} to each creature it passes through. {@code size} scales the crescent (1 = Wind Blade); {@code roll} tilts it
	 * (radians, 0 = flat).
	 */
	public WindBladeEntity(Level level, ServerPlayer owner, Vec3 dir, double speed, double range, float damage, float size,
			float roll) {
		this(WindEntities.BLADE, level);
		this.owner = owner.getUUID();
		this.damage = damage;
		this.life = Math.max(1, (int) Math.ceil(range / speed));
		Vec3 eye = owner.getEyePosition().add(dir.scale(0.6)).subtract(0, 0.15, 0);
		Vec3 v = dir.normalize().scale(speed);
		float yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
		float pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		moveTo(eye.x, eye.y, eye.z, yaw, pitch);
		setDeltaMovement(v);
		entityData.set(SIZE, size);
		entityData.set(ROLL, roll);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SIZE, 1.0f);
		builder.define(ROLL, 0.0f);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public float roll() {
		return entityData.get(ROLL);
	}

	public float damage() {
		return damage;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 from = position();
		Vec3 v = getDeltaMovement();
		Vec3 to = from.add(v);
		if (!(level() instanceof ServerLevel level)) {
			setPos(to.x, to.y, to.z); // the client flies it along too (the server corrects it every tick)
			return;
		}
		ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (p == null || --life < 0) {
			breakUp(level, from, false);
			return;
		}
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : to;
		cutAlong(level, p, from, end);
		if (wall.getType() == HitResult.Type.BLOCK) {
			breakUp(level, end, true);
			return;
		}
		setPos(to.x, to.y, to.z);
	}

	/** Every creature the crescent sweeps through between {@code a} and {@code b} takes the blade's damage, once each. */
	private void cutAlong(ServerLevel level, ServerPlayer p, Vec3 a, Vec3 b) {
		double half = 1.15 * size();
		Vec3 mid = a.add(b).scale(0.5);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, mid, a.distanceTo(b) * 0.5 + half + 0.6)) {
			if (e == p || cut.contains(e.getId())) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (distanceToSegment(c, a, b) > half + e.getBbWidth() * 0.5 + 0.3) {
				continue;
			}
			cut.add(e.getId());
			if (AbilityHelpers.hurtBurst(p, e, damage)) {
				Vec3 push = getDeltaMovement().normalize().scale(0.35);
				e.push(push.x, 0.12, push.z);
				e.hurtMarked = true;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f,
						1.4f + random.nextFloat() * 0.3f);
			}
		}
	}

	static double distanceToSegment(Vec3 c, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double t = len2 < 1.0e-6 ? 0 : Mth.clamp(c.subtract(a).dot(ab) / len2, 0, 1);
		return c.distanceTo(a.add(ab.scale(t)));
	}

	private void breakUp(ServerLevel level, Vec3 at, boolean wall) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, wall ? 8 : 4, 0.3 * size(), 0.15, 0.3 * size(), 0.04);
		if (wall) {
			level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		discard();
	}

	/** How many creatures this blade has cut so far (gametests). */
	public int cutCount() {
		return cut.size();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96 * 96;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
