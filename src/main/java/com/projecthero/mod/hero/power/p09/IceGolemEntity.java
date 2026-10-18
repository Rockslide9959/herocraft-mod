package com.projecthero.mod.hero.power.p09;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cryokinesis hold-C: an <b>Ice Golem</b> -- a golem of clear and packed ice (the iron golem's body, recast in
 * ice by {@code IceGolemRenderer}, with crystals growing from its shoulders and back and a cold glow in its eyes) that
 * fights for its caster for {@link CryokinesisHandlers#GOLEM_TICKS}. It is an iron golem underneath for its walk and
 * its two-armed overhead punch, but none of the village logic: it picks its own targets (whatever hurt its owner,
 * whatever its owner is fighting, else the nearest hostile -- the shared {@code HeroTargets} rules), every punch adds
 * {@link CryokinesisHandlers#GOLEM_FROST} Frost, and it <b>never</b> targets or hurts its owner, the owner's squad or
 * (PvP off) any player. Being {@link OwnableEntity}, the owner's own moves never hurt it. Never saved, no drops; it
 * shatters when its time is up, its owner is gone or the power is switched.
 */
public class IceGolemEntity extends IronGolem implements OwnableEntity {
	private static final double LEASH = 32.0;
	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(IceGolemEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private int life;

	public IceGolemEntity(EntityType<? extends IronGolem> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setPlayerCreated(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, CryokinesisHandlers.GOLEM_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ATTACK_DAMAGE, CryokinesisHandlers.GOLEM_DAMAGE)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		// none of the iron golem's village goals: it fights for its caster and follows him
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
		goalSelector.addGoal(2, new FollowOwnerGoal(this));
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	public void bind(ServerPlayer owner) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
	}

	@Override
	public UUID getOwnerUUID() {
		return entityData.get(OWNER).orElse(null);
	}

	public ServerPlayer owner() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		UUID id = getOwnerUUID();
		return id == null ? null : sl.getServer().getPlayerList().getPlayer(id);
	}

	public int life() {
		return life;
	}

	/** The one rule: may this golem ever fight {@code t}? */
	public boolean mayTarget(LivingEntity t) {
		if (t == null || t == this) {
			return false;
		}
		ServerPlayer owner = owner();
		return owner != null && com.projecthero.mod.combat.HeroTargets.canHarm(owner, t) && !Squads.areAllies(owner, t);
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(target != null && !mayTarget(target) ? null : target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return mayTarget(target) && super.canAttack(target);
	}

	@Override
	public boolean canAttackType(EntityType<?> type) {
		return true; // whom it may fight is mayTarget's call
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (target instanceof LivingEntity le && !mayTarget(le)) {
			return false;
		}
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity le && level() instanceof ServerLevel sl) {
			ServerPlayer owner = owner();
			if (owner != null) {
				FrostStacks.add(owner, le, CryokinesisHandlers.GOLEM_FROST);
			}
			sl.sendParticles(CryokinesisHandlers.ICE_DUST, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 14,
					0.3, 0.3, 0.3, 0.1);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.7f, 0.6f);
		}
		return hit;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity src = source.getEntity();
		ServerPlayer owner = owner();
		if (src != null && owner != null && (src == owner || Squads.areAllies(owner, src))) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS; // no iron-ingot repairs, no flowers
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		life++;
		ServerPlayer owner = owner();
		if (life > CryokinesisHandlers.GOLEM_TICKS || owner == null || !owner.isAlive() || owner.level() != level()) {
			shatter();
			return;
		}
		if (distanceToSqr(owner) > LEASH * LEASH) {
			Vec3 near = owner.position().add(owner.getLookAngle().multiply(-2.0, 0, -2.0));
			teleportTo(near.x, owner.getY(), near.z);
			getNavigation().stop();
		}
		if (life % 10 == 0) {
			retarget(owner);
		}
		if (life % 5 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.4, getZ(), 2, 0.5, 0.8, 0.5, 0.0);
		}
	}

	/** Whatever hurt the owner, else what the owner is hitting, else the nearest hostile within 16 blocks. */
	private void retarget(ServerPlayer owner) {
		LivingEntity current = getTarget();
		if (current != null && mayTarget(current) && current.isAlive() && current.distanceToSqr(this) < 24.0 * 24.0) {
			return;
		}
		LivingEntity pick = null;
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && mayTarget(attacker)) {
			pick = attacker;
		}
		LivingEntity victim = owner.getLastHurtMob();
		if (pick == null && victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && mayTarget(victim)) {
			pick = victim;
		}
		if (pick == null) {
			double best = 16.0 * 16.0;
			for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(16.0),
					m -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, m))) {
				double d = m.distanceToSqr(this);
				if (d < best && mayTarget(m)) {
					best = d;
					pick = m;
				}
			}
		}
		setTarget(pick);
	}

	/** Bursts into chunks of ice. */
	public void shatter() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(CryokinesisHandlers.ICE_DUST, getX(), getY() + 1.3, getZ(), 60, 0.6, 1.0, 0.6, 0.15);
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.3, getZ(), 30, 0.6, 1.0, 0.6, 0.05);
			for (int i = 0; i < 8; i++) {
				double a = sl.random.nextDouble() * Math.PI * 2;
				Vec3 v = new Vec3(Math.cos(a) * 0.3, 0.2 + sl.random.nextDouble() * 0.3, Math.sin(a) * 0.3);
				sl.addFreshEntity(IceBoltEntity.debris(sl, position().add(0, 1.2 + sl.random.nextDouble(), 0), v));
			}
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.4f, 0.5f);
		}
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide) {
			shatter();
		}
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
		if (other instanceof Player p && p.getUUID().equals(getOwnerUUID())) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	/** Lumber back to the owner when there is nothing to fight. */
	private static final class FollowOwnerGoal extends Goal {
		private final IceGolemEntity golem;
		private int repath;

		FollowOwnerGoal(IceGolemEntity golem) {
			this.golem = golem;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = golem.owner();
			return golem.getTarget() == null && owner != null && golem.distanceToSqr(owner) > 4.5 * 4.5;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = golem.owner();
			return golem.getTarget() == null && owner != null && golem.distanceToSqr(owner) > 3.0 * 3.0;
		}

		@Override
		public void tick() {
			ServerPlayer owner = golem.owner();
			if (owner == null) {
				return;
			}
			golem.getLookControl().setLookAt(owner, 10.0f, golem.getMaxHeadXRot());
			if (--repath <= 0) {
				repath = 10;
				golem.getNavigation().moveTo(owner, 1.2);
			}
		}

		@Override
		public void stop() {
			golem.getNavigation().stop();
		}
	}
}
