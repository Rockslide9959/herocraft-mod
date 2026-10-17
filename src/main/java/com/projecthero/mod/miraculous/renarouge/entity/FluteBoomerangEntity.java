package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.renarouge.RenaRougeCombat;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeMoves;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, G: the thrown Flute, spinning like a boomerang. Outbound it follows a bowed arc ({@link RenaRougeConfig#THROW_RANGE}
 * out, {@link RenaRougeConfig#THROW_CURVE} to the side at its widest) over {@link RenaRougeConfig#THROW_OUT_TICKS}, then
 * homes back to her hand; every creature it passes through takes {@link RenaRougeConfig#THROW_DAMAGE} once on the way out
 * and once on the way back. A wall turns it back early. Caught: {@link RenaRougeMoves#fluteCaught}.
 */
public class FluteBoomerangEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FluteBoomerangEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private Vec3 start = Vec3.ZERO;
	private Vec3 fwd = new Vec3(0, 0, 1);
	private Vec3 side = new Vec3(1, 0, 0);
	private int age;
	private boolean returning;
	private final Set<Integer> hitOut = new HashSet<>();
	private final Set<Integer> hitBack = new HashSet<>();

	public FluteBoomerangEntity(EntityType<? extends FluteBoomerangEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER_ID, -1);
	}

	/** Thrown by {@code thrower} from {@code from} along {@code look} (curving to her right). */
	public void launch(ServerPlayer thrower, Vec3 from, Vec3 look) {
		owner = thrower.getUUID();
		entityData.set(OWNER_ID, thrower.getId());
		start = from;
		Vec3 flat = new Vec3(look.x, 0, look.z);
		fwd = look.normalize();
		Vec3 right = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0, flat.x).normalize();
		side = right;
		setPos(from.x, from.y, from.z);
	}

	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	public boolean returning() {
		return returning;
	}

	public int age() {
		return age;
	}

	private @Nullable ServerPlayer ownerPlayer() {
		if (owner == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
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
			double t = Math.min(1.0, age / (double) RenaRougeConfig.THROW_OUT_TICKS);
			double out = RenaRougeConfig.THROW_RANGE * Math.sin(t * Math.PI / 2.0);
			double bow = RenaRougeConfig.THROW_CURVE * Math.sin(t * Math.PI);
			next = start.add(fwd.scale(out)).add(side.scale(bow));
			HitResult wall = level().clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (wall.getType() != HitResult.Type.MISS) {
				next = wall.getLocation().add(prev.subtract(next).normalize().scale(0.2));
				returning = true;
				level().playSound(null, next.x, next.y, next.z, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.7f, 1.4f);
			} else if (t >= 1.0) {
				returning = true;
			}
		} else {
			Vec3 hand = p.getEyePosition().add(0, -0.35, 0);
			Vec3 to = hand.subtract(prev);
			double d = to.length();
			if (d < 1.4 || age > RenaRougeConfig.THROW_MAX_TICKS) {
				RenaRougeMoves.fluteCaught(p, this);
				discard();
				return;
			}
			next = prev.add(to.scale(Math.min(1.0, RenaRougeConfig.THROW_RETURN_SPEED / d)));
		}
		setPos(next.x, next.y, next.z);
		// everything along this tick's stretch of the flight
		AABB sweep = new AABB(prev, next).inflate(0.7);
		Set<Integer> hit = returning ? hitBack : hitOut;
		for (Entity e : level().getEntities(this, sweep, e -> AbilityHelpers.asLiving(e) != null && e != p)) {
			LivingEntity le = AbilityHelpers.asLiving(e);
			if (le == null || hit.contains(le.getId()) || !RenaRougeCombat.isTarget(p, le)) {
				continue;
			}
			if (e.getBoundingBox().inflate(0.5).clip(prev, next).isEmpty() && !e.getBoundingBox().inflate(0.5).contains(next)) {
				continue;
			}
			hit.add(le.getId());
			if (RenaRougeCombat.strike(p, le, prev, RenaRougeConfig.THROW_DAMAGE, 0.35, 0.05)) {
				level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
		}
		if (age % 3 == 0) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25f, 1.9f);
		}
		if (level() instanceof ServerLevel sl && age % 2 == 0) {
			sl.sendParticles(RenaRougeCombat.ORANGE, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
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
