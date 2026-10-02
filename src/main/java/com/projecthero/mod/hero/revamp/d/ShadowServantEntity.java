package com.projecthero.mod.hero.revamp.d;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (Shadow Manipulation, N): a Shadow Servant -- a humanoid silhouette of living darkness that fights for
 * its summoner for {@link #LIFETIME} ticks. It picks its own targets (whatever hurt its owner, whatever its owner
 * is fighting, else the nearest hostile) and <b>never</b> targets or hits its owner, the owner's squad, another
 * of the owner's shadows or decoys, or any player at all unless PvP ability damage is allowed. It never saves,
 * never drops anything and dissolves when its time is up or its owner is gone.
 */
public class ShadowServantEntity extends PathfinderMob {
	public static final int LIFETIME = 20 * 20;
	private static final double LEASH = 32.0;

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(ShadowServantEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private int life;

	public ShadowServantEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
		goalSelector.addGoal(2, new FollowOwnerGoal(this));
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	public void bind(ServerPlayer owner, float attackDamage) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		var atk = getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(attackDamage);
		}
	}

	public Optional<UUID> ownerId() {
		return entityData.get(OWNER);
	}

	public ServerPlayer owner() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		return ownerId().map(id -> sl.getServer().getPlayerList().getPlayer(id)).orElse(null);
	}

	public int life() {
		return life;
	}

	/** The one rule: may this servant ever fight {@code t}? */
	public boolean mayTarget(LivingEntity t) {
		if (t == null || t == this) {
			return false;
		}
		ServerPlayer owner = owner();
		if (owner == null) {
			return false;
		}
		// v0.14.20: the shared rule 1 on the owner's behalf -- never the owner, his pets/summons (other servants,
		// mirror images), a squadmate, a creative player or (PvP off) any player. Whom it CHOOSES is rule 2 (retarget).
		return com.projecthero.mod.combat.HeroTargets.canHarm(owner, t) && !Squads.areAllies(owner, t);
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
	public boolean doHurtTarget(Entity target) {
		if (target instanceof LivingEntity le && !mayTarget(le)) {
			return false;
		}
		boolean hit = super.doHurtTarget(target);
		if (hit && level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.5,
					target.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
		}
		return hit;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity src = source.getEntity();
		ServerPlayer owner = owner();
		if (src != null && owner != null && (src == owner || Squads.areAllies(owner, src))) {
			return true; // the owner and their squad cannot hurt it by accident either
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		life++;
		ServerPlayer owner = owner();
		if (life > LIFETIME || owner == null || !owner.isAlive() || owner.level() != level()) {
			dissolve();
			return;
		}
		if (distanceToSqr(owner) > LEASH * LEASH) {
			Vec3 near = owner.position().add(owner.getLookAngle().multiply(-1.5, 0, -1.5));
			teleportTo(near.x, owner.getY(), near.z);
			getNavigation().stop();
		}
		if (life % 10 == 0) {
			retarget(owner);
		}
		if (life % 4 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(BatchDFx.SHADOW, getX(), getY() + 0.1, getZ(), 3, 0.3, 0.05, 0.3, 0.0);
			sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0, getZ(), 1, 0.25, 0.5, 0.25, 0.0);
		}
	}

	/** Whatever hurt the owner, else what the owner is hitting, else the nearest hostile within 16 blocks. */
	private void retarget(ServerPlayer owner) {
		LivingEntity current = getTarget();
		if (current != null && mayTarget(current) && current.distanceToSqr(this) < 24.0 * 24.0) {
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
					m -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, m))) { // v0.14.20: rule 2
				double d = m.distanceToSqr(this);
				if (d < best && mayTarget(m)) {
					best = d;
					pick = m;
				}
			}
		}
		setTarget(pick);
	}

	public void dissolve() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(BatchDFx.SHADOW, getX(), getY() + 1.0, getZ(), 30, 0.35, 0.8, 0.35, 0.02);
			sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0, getZ(), 16, 0.3, 0.7, 0.3, 0.05);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.9f, 0.6f);
		}
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide) {
			dissolve();
		}
	}

	// ---- never a real creature: no loot, no xp, no pickup, no leash, no despawn surprises ----

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
	public boolean isAlliedTo(Entity other) {
		if (other instanceof Player p && ownerId().map(p.getUUID()::equals).orElse(false)) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	/** Trot back to the owner when there is nothing to fight. */
	private static final class FollowOwnerGoal extends Goal {
		private final ShadowServantEntity servant;
		private int repath;

		FollowOwnerGoal(ShadowServantEntity servant) {
			this.servant = servant;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = servant.owner();
			return servant.getTarget() == null && owner != null && servant.distanceToSqr(owner) > 4.0 * 4.0;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = servant.owner();
			return servant.getTarget() == null && owner != null && servant.distanceToSqr(owner) > 2.5 * 2.5;
		}

		@Override
		public void tick() {
			ServerPlayer owner = servant.owner();
			if (owner == null) {
				return;
			}
			servant.getLookControl().setLookAt(owner, 10.0f, servant.getMaxHeadXRot());
			if (--repath <= 0) {
				repath = 10;
				servant.getNavigation().moveTo(owner, 1.25);
			}
		}

		@Override
		public void stop() {
			servant.getNavigation().stop();
		}
	}
}
