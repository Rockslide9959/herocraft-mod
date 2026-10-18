package com.projecthero.mod.hero.power.p25;

import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.d.MirrorImageEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 hold V -- Water Clone: a copy of the caster made of water (the client draws his own skin as a translucent,
 * rippling blue body -- {@code WaterCloneRenderer}) that fights for {@link WaterHandlers#CLONE_TICKS} ticks. It goes for
 * the nearest threat to its caster (HeroTargets rule 2) and hits for half his melee damage; hostile mobs hunting him (or
 * idle near it) turn on it instead -- bosses keep their own threat table. It has {@link WaterHandlers#CLONE_HEALTH} health,
 * never drowns or burns, and bursts into a splash when its time is up, when it is killed, or when its caster leaves.
 * Never saved, no loot, no xp. It counts as its caster's summon ({@link TraceableEntity}), so his and his squad's moves
 * never hit it.
 */
public class WaterCloneEntity extends PathfinderMob implements TraceableEntity {
	private static final double LURE_RANGE = 16.0;
	private static final double HUNT_RANGE = 16.0;

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(WaterCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private int life;

	public WaterCloneEntity(EntityType<? extends WaterCloneEntity> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		setPersistenceRequired();
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, WaterHandlers.CLONE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** Raises a clone of {@code owner} beside him. */
	public static WaterCloneEntity raise(ServerPlayer owner) {
		ServerLevel level = AbilityHelpers.level(owner);
		WaterCloneEntity e = new WaterCloneEntity(WaterEntities.WATER_CLONE, level);
		e.entityData.set(OWNER, Optional.of(owner.getUUID()));
		Vec3 look = owner.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 at = owner.position().add(side.scale(1.2));
		if (!level.noCollision(e, e.getType().getDimensions().makeBoundingBox(at))) {
			at = owner.position();
		}
		e.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0f);
		e.setYHeadRot(owner.getYRot());
		e.yBodyRot = owner.getYRot();
		level.addFreshEntity(e);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 50, 0.35, 0.8, 0.35, 0.2);
		level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.6, at.z, 20, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.6f);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		goalSelector.addGoal(2, new StayCloseGoal());
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	public Optional<UUID> ownerId() {
		return entityData.get(OWNER);
	}

	/** How long it has fought (server), for the gametests. */
	public int life() {
		return life;
	}

	public void setLife(int ticks) {
		life = ticks;
	}

	@Override
	public Entity getOwner() {
		return owner();
	}

	/** The caster (either side), or null. */
	public Player owner() {
		return ownerId().map(id -> level().getPlayerByUUID(id)).orElse(null);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			if (random.nextInt(3) == 0) {
				level().addParticle(ParticleTypes.DRIPPING_WATER, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.6,
						getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
			}
			return;
		}
		life++;
		setAirSupply(getMaxAirSupply());
		clearFire();
		Player o = owner();
		if (!(o instanceof ServerPlayer owner) || life > WaterHandlers.CLONE_TICKS || !owner.isAlive() || owner.level() != level()
				|| !WaterHandlers.cloneCalled(owner)) {
			burst();
			return;
		}
		if (life % 10 == 1) {
			hunt(owner);
			lure(owner);
		}
	}

	/** It goes for the nearest threat to its caster (rule 2), or keeps its current one while that is still fair game. */
	public LivingEntity hunt(ServerPlayer owner) {
		LivingEntity cur = getTarget();
		if (cur != null && cur.isAlive() && HeroTargets.canHarm(owner, cur) && cur.distanceToSqr(this) < HUNT_RANGE * HUNT_RANGE) {
			return cur;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HUNT_RANGE),
				e -> e != this && !(e instanceof WaterCloneEntity) && !(e instanceof MirrorImageEntity) && HeroTargets.isHostile(owner, e))) {
			double d = e.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		setTarget(best);
		return best;
	}

	/** Hostile mobs after the caster (or idle and close) turn on the clone. Bosses keep their own aggro. Returns how many. */
	public int lure(ServerPlayer owner) {
		int n = 0;
		for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RANGE),
				m -> !(m instanceof WaterCloneEntity) && !(m instanceof MirrorImageEntity) && !WaterHandlers.isBossLike(m)
						&& HeroTargets.isHostile(owner, m))) {
			LivingEntity target = mob.getTarget();
			boolean afterOwner = target == owner;
			boolean idleNear = target == null && mob.distanceToSqr(this) < 10.0 * 10.0;
			if (afterOwner || idleNear) {
				mob.setTarget(this);
				n++;
			}
		}
		return n;
	}

	/** Half the caster's melee damage (his attack attribute, weapon included). */
	public float hitDamage() {
		Player o = owner();
		double base = o == null ? 1.0 : o.getAttributeValue(Attributes.ATTACK_DAMAGE);
		return (float) (base * WaterHandlers.CLONE_DAMAGE_FACTOR);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (!(owner() instanceof ServerPlayer owner) || !(target instanceof LivingEntity le) || !HeroTargets.canHarm(owner, le)) {
			return false;
		}
		boolean dealt = le.hurt(damageSources().mobAttack(this), hitDamage());
		if (dealt) {
			Vec3 push = le.position().subtract(position()).multiply(1, 0, 1);
			if (push.lengthSqr() > 1.0e-4 && !WaterHandlers.isBossLike(le)) {
				push = push.normalize().scale(0.35);
				le.push(push.x, 0.1, push.z);
				le.hurtMarked = true;
			}
			le.clearFire();
			((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 12, 0.25, 0.25,
					0.25, 0.1);
			playSound(SoundEvents.PLAYER_SPLASH, 0.6f, 1.4f);
		}
		return dealt;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypes.DROWN) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(DamageTypes.IN_WALL)) {
			return false;
		}
		if (source.getEntity() instanceof Player p && owner() == p) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		burst();
	}

	/** It bursts into a splash and is gone. */
	public void burst() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			Vec3 c = position().add(0, 1.0, 0);
			sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 80, 0.4, 0.8, 0.4, 0.35);
			sl.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 0.4, c.z, 30, 0.5, 0.5, 0.5, 0.0);
			sl.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.05);
			sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.1f, 1.0f);
			for (Mob mob : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RANGE), m -> m.getTarget() == this)) {
				mob.setTarget(null);
			}
		}
		discard();
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
	}

	@Override
	protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
	}

	@Override
	protected int getBaseExperienceReward() {
		return 0;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		if (other instanceof Player p && ownerId().map(p.getUUID()::equals).orElse(false)) {
			return true;
		}
		if (other instanceof WaterCloneEntity c && c.ownerId().equals(ownerId())) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	/** With nothing to fight it keeps near its caster. */
	private final class StayCloseGoal extends Goal {
		StayCloseGoal() {
			setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Player o = owner();
			return getTarget() == null && o != null && distanceToSqr(o) > 4.0 * 4.0;
		}

		@Override
		public boolean canContinueToUse() {
			Player o = owner();
			return getTarget() == null && o != null && distanceToSqr(o) > 2.5 * 2.5 && !getNavigation().isDone();
		}

		@Override
		public void start() {
			Player o = owner();
			if (o != null) {
				getNavigation().moveTo(o, 1.2);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}
}
