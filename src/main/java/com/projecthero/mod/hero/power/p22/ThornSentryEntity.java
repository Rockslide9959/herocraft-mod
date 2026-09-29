package com.projecthero.mod.hero.power.p22;

import java.util.UUID;

import com.projecthero.mod.squad.Squads;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (batch E): Plant Manipulation's H -- a living turret of cactus, moss and flowering azalea. For
 * {@link #LIFETIME} ticks it turns to the nearest hostile it can see within {@link #RANGE} blocks and spits a thorn
 * ({@link ThornProjectile}) at it every {@link #FIRE_INTERVAL} ticks. It only ever targets hostile mobs (or a mob
 * currently targeting its owner) -- never its owner, never a squad-mate, never another player. One per player;
 * never saved, and it withers the moment its owner is gone, dead or far away.
 */
public class ThornSentryEntity extends Entity {
	public static final int LIFETIME = 300;
	public static final double RANGE = 14.0;
	public static final int FIRE_INTERVAL = 10;
	public static final float THORN_DAMAGE = 5.0f;

	/** Bumped on every shot -- the client plays a little recoil pulse off it. */
	private static final EntityDataAccessor<Integer> SHOTS = SynchedEntityData.defineId(ThornSentryEntity.class,
			EntityDataSerializers.INT);

	private UUID owner;
	/** The live owner entity (never saved -- the sentry is not either); UUID lookup is the fallback. */
	private ServerPlayer ownerRef;
	private int age;
	private int targetId = -1;

	public ThornSentryEntity(EntityType<? extends ThornSentryEntity> type, Level level) {
		super(type, level);
	}

	public void setOwner(ServerPlayer player) {
		this.owner = player.getUUID();
		this.ownerRef = player;
	}

	public UUID ownerId() {
		return owner;
	}

	public int shots() {
		return this.entityData.get(SHOTS);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SHOTS, 0);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	@Override
	public void tick() {
		super.tick();
		// settle onto the ground (it never moves sideways)
		Vec3 v = getDeltaMovement();
		if (!onGround() || v.y > 0) {
			setDeltaMovement(0, Math.max(-0.6, v.y - getGravity()), 0);
			move(MoverType.SELF, getDeltaMovement());
		} else {
			setDeltaMovement(Vec3.ZERO);
		}
		if (level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		age++;
		ServerPlayer player = ownerRef != null && !ownerRef.isRemoved() ? ownerRef
				: owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (age > LIFETIME || (age > 2 && (player == null || !player.isAlive() || player.distanceToSqr(this) > 64.0 * 64.0))) {
			wither(level);
			return;
		}
		if (age % 20 == 0) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.1, getZ(), 1, 0.25, 0.2, 0.25, 0.0);
		}
		LivingEntity target = targetId < 0 ? null : level.getEntity(targetId) instanceof LivingEntity le ? le : null;
		if (age % 4 == 0 || target == null || !target.isAlive()) {
			target = findTarget(level, player);
			targetId = target == null ? -1 : target.getId();
		}
		if (target == null) {
			return;
		}
		Vec3 muzzle = muzzle();
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
		Vec3 d = aim.subtract(muzzle);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		setYRot(yaw);
		setYHeadRot(yaw);
		if (age % FIRE_INTERVAL == 0) {
			ThornProjectile.fire(level, player, this, muzzle, aim, THORN_DAMAGE);
			this.entityData.set(SHOTS, shots() + 1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.PLAYERS, 0.8f, 1.5f);
		}
	}

	public Vec3 muzzle() {
		return position().add(0, 1.05, 0);
	}

	/** The nearest visible hostile in range -- never the owner, a squad-mate, a player, or an armour stand. */
	public LivingEntity findTarget(ServerLevel level, ServerPlayer player) {
		Vec3 from = muzzle();
		AABB box = getBoundingBox().inflate(RANGE);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (!isHostileTo(e, player)) {
				continue;
			}
			Vec3 eye = e.position().add(0, e.getBbHeight() * 0.6, 0);
			double dd = eye.distanceToSqr(from);
			if (dd > RANGE * RANGE || dd >= bestD) {
				continue;
			}
			if (level.clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			best = e;
			bestD = dd;
		}
		return best;
	}

	/** Target rule, public for the gametests. */
	public static boolean isHostileTo(LivingEntity e, ServerPlayer owner) {
		if (e instanceof Player || e instanceof ArmorStand || !e.isAlive()) {
			return false; // players are never shot -- not the owner, not squad-mates, not anyone
		}
		if (owner != null && (e == owner || Squads.areAllies(owner, e))) {
			return false;
		}
		return e instanceof Enemy || (owner != null && e instanceof Mob m && m.getTarget() == owner);
	}

	private void wither(ServerLevel level) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()),
				getX(), getY() + 0.5, getZ(), 20, 0.3, 0.4, 0.3, 0.1);
		level.sendParticles(ParticleTypes.COMPOSTER, getX(), getY() + 0.8, getZ(), 12, 0.3, 0.4, 0.3, 0.0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		discard();
	}

	/** Withers every sentry {@code player} owns (a new one replaces the old; power removal). */
	public static void removeFor(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		for (ThornSentryEntity s : level.getEntities(PlantEntities.THORN_SENTRY,
				e -> e.ownerRef == player || (e.ownerRef == null && player.getUUID().equals(e.owner)))) {
			s.wither(level);
		}
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
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 64.0 * 64.0;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	/** Used by the renderer: 0..1 grow-in over the first 10 ticks and wither-out over the last 20. */
	public float sizeFactor(float partialTick) {
		float t = tickCount + partialTick;
		float in = Math.min(1.0f, t / 10.0f);
		float out = Math.min(1.0f, Math.max(0.0f, (LIFETIME + 2 - t) / 20.0f));
		return Math.min(in, out);
	}
}
