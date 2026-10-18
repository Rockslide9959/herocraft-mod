package com.projecthero.mod.miraculous.carapace.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.carapace.CarapaceCombat;
import com.projecthero.mod.miraculous.carapace.CarapaceConfig;
import com.projecthero.mod.miraculous.carapace.CarapaceMoves;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, Shift+R: the thrown Shield, spinning flat. It flies at the creature he aimed at (or the spot), and from each
 * creature it hits ({@link CarapaceConfig#THROW_DAMAGE}) ricochets to the nearest other one in sight within
 * {@link CarapaceConfig#THROW_BOUNCE_RANGE}, up to {@link CarapaceConfig#THROW_BOUNCES} in all, then flies home to his
 * arm. A wall sends it home early. Caught: {@link CarapaceMoves#shieldCaught}.
 */
public class ShieldThrowEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ShieldThrowEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private Vec3 aim = Vec3.ZERO;
	private int targetId = -1;
	private int age;
	private boolean returning;
	private final Set<Integer> hit = new HashSet<>();

	public ShieldThrowEntity(EntityType<? extends ShieldThrowEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
	}

	public void launch(ServerPlayer thrower, Vec3 from, Vec3 aimAt, @Nullable LivingEntity first) {
		owner = thrower.getUUID();
		entityData.set(OWNER_ID, thrower.getId());
		aim = aimAt;
		targetId = first == null ? -1 : first.getId();
		setPos(from.x, from.y, from.z);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	/** Test hook: the creatures it has hit so far. */
	public int hits() {
		return hit.size();
	}

	public boolean returning() {
		return returning;
	}

	private @Nullable ServerPlayer ownerPlayer() {
		if (owner == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
	}

	private static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		ServerPlayer p = ownerPlayer();
		if (p == null || !p.isAlive() || p.level() != level()) {
			discard();
			return;
		}
		age++;
		Vec3 prev = position();
		Vec3 next;
		if (!returning) {
			LivingEntity target = targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? le : null;
			if (targetId >= 0 && target == null) {
				// the target died on the way: find another, else home
				target = CarapaceMoves.nextBounce(p, prev, hit);
				targetId = target == null ? -1 : target.getId();
				if (target == null) {
					returning = true;
				}
			}
			Vec3 dest = target != null ? centre(target) : aim;
			Vec3 to = dest.subtract(prev);
			double d = to.length();
			next = d <= CarapaceConfig.THROW_SPEED ? dest : prev.add(to.scale(CarapaceConfig.THROW_SPEED / d));
			HitResult wall = level().clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (wall.getType() != HitResult.Type.MISS) {
				next = wall.getLocation().add(prev.subtract(next).normalize().scale(0.25));
				returning = true;
				level().playSound(null, next.x, next.y, next.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.3f);
			} else if (target != null && (target.getBoundingBox().inflate(0.45).contains(next) || d <= CarapaceConfig.THROW_SPEED)) {
				hit.add(target.getId());
				if (CarapaceCombat.strike(p, target, prev, CarapaceConfig.THROW_DAMAGE, 0.5, 0.1)) {
					level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
					level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.1f);
				}
				LivingEntity after = hit.size() >= CarapaceConfig.THROW_BOUNCES ? null : CarapaceMoves.nextBounce(p, next, hit);
				if (after == null) {
					returning = true;
				} else {
					targetId = after.getId();
				}
			} else if (target == null && d <= CarapaceConfig.THROW_SPEED) {
				returning = true;
			}
		} else {
			Vec3 hand = p.position().add(0, p.getBbHeight() * 0.6, 0);
			Vec3 to = hand.subtract(prev);
			double d = to.length();
			if (d < 1.3 || age > CarapaceConfig.THROW_MAX_TICKS) {
				CarapaceMoves.shieldCaught(p);
				discard();
				return;
			}
			next = prev.add(to.scale(Math.min(1.0, CarapaceConfig.THROW_SPEED * 1.2 / d)));
		}
		if (age > CarapaceConfig.THROW_MAX_TICKS) {
			CarapaceMoves.shieldCaught(p);
			discard();
			return;
		}
		setPos(next.x, next.y, next.z);
		if (age % 3 == 0) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3f, 1.5f);
		}
		if (level() instanceof ServerLevel sl && age % 2 == 0) {
			sl.sendParticles(CarapaceCombat.GREEN, getX(), getY(), getZ(), 1, 0.08, 0.04, 0.08, 0.0);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96 * 96;
	}
}
