package com.projecthero.mod.hero.power.p22;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis, Shift+Z: a Treant -- a walking tree that fights for its grower for {@link #LIFETIME} ticks. The
 * player model in a bark skin with a leafy crown (a regular summon, so player-model style). It slams for
 * {@link PlantManipulationHandlers#TREANT_DAMAGE} with a heavy shove, <b>taunts</b> hostiles within
 * {@link #TAUNT_RADIUS} so they turn on it instead of you, and heals on living ground. Like the Shadow Servant it never
 * targets its owner, the owner's squad or (PvP off) any player, never saves and drops nothing.
 */
public class TreantEntity extends PathfinderMob {
	public static final int LIFETIME = 45 * 20;
	public static final double TAUNT_RADIUS = 8.0;
	private static final double LEASH = 32.0;

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(TreantEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	/** Ticks lived, synced so the client can grow it out of the ground and wither it away. */
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(TreantEntity.class, EntityDataSerializers.INT);

	private int life;

	public TreantEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, PlantManipulationHandlers.TREANT_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, PlantManipulationHandlers.TREANT_DAMAGE)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(LIFE, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true));
		goalSelector.addGoal(2, new FollowOwnerGoal(this));
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	public void bind(ServerPlayer owner) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
	}

	public Optional<UUID> ownerId() {
		return entityData.get(OWNER);
	}

	/** Ticks lived (both sides). */
	public int lifeTicks() {
		return entityData.get(LIFE);
	}

	public ServerPlayer owner() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		return ownerId().map(id -> sl.getServer().getPlayerList().getPlayer(id)).orElse(null);
	}

	/** The one rule: may this Treant ever fight {@code t}? */
	public boolean mayTarget(LivingEntity t) {
		if (t == null || t == this) {
			return false;
		}
		ServerPlayer owner = owner();
		if (owner == null) {
			return false;
		}
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
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()), target.getX(),
					target.getY() + target.getBbHeight() * 0.5, target.getZ(), 14, 0.3, 0.4, 0.3, 0.1);
			sl.sendParticles(ChloroParticles.LEAF, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 6, 0.4, 0.3,
					0.4, 0.02);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
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
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		life++;
		entityData.set(LIFE, life);
		ServerPlayer owner = owner();
		if (life > LIFETIME || owner == null || !owner.isAlive() || owner.level() != level()) {
			wither();
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
		if (life % 20 == 0) {
			taunt(owner);
			if (getHealth() < getMaxHealth() && level() instanceof ServerLevel sl
					&& PlantManipulationHandlers.onNatureGround(sl, blockPosition())) {
				heal(1.0f);
			}
		}
		if (life % 6 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(ChloroParticles.LEAF, getX(), getY() + getBbHeight() * 0.95, getZ(), 1, 0.45, 0.2, 0.45, 0.0);
		}
	}

	/** Hostiles near it (not bosses) turn on the Treant. */
	private void taunt(ServerPlayer owner) {
		for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(TAUNT_RADIUS),
				m -> m != this && m.isAlive() && com.projecthero.mod.combat.HeroTargets.isHostile(owner, m)
						&& !com.projecthero.mod.titanshifter.TitanCombat.isBoss(m))) {
			if (m.getTarget() != this) {
				m.setTarget(this);
			}
		}
	}

	/** Whatever hurt the owner, else what the owner is hitting, else the nearest hostile within 16 blocks. */
	private void retarget(ServerPlayer owner) {
		LivingEntity current = getTarget();
		if (current != null && current.isAlive() && mayTarget(current) && current.distanceToSqr(this) < 24.0 * 24.0) {
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

	/** Sinks back into the ground in a shower of leaves. */
	public void wither() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(ChloroParticles.LEAF, getX(), getY() + 1.6, getZ(), 30, 0.6, 0.8, 0.6, 0.03);
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()), getX(), getY() + 1.0,
					getZ(), 24, 0.4, 0.8, 0.4, 0.1);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		}
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide) {
			wither();
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOOD_BREAK;
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
	public boolean isAlliedTo(Entity other) {
		if (other instanceof Player p && ownerId().map(p.getUUID()::equals).orElse(false)) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	/** Lumber back to the owner when there is nothing to fight. */
	private static final class FollowOwnerGoal extends Goal {
		private final TreantEntity treant;
		private int repath;

		FollowOwnerGoal(TreantEntity treant) {
			this.treant = treant;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = treant.owner();
			return treant.getTarget() == null && owner != null && treant.distanceToSqr(owner) > 5.0 * 5.0;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = treant.owner();
			return treant.getTarget() == null && owner != null && treant.distanceToSqr(owner) > 3.0 * 3.0;
		}

		@Override
		public void tick() {
			ServerPlayer owner = treant.owner();
			if (owner == null) {
				return;
			}
			treant.getLookControl().setLookAt(owner, 10.0f, treant.getMaxHeadXRot());
			if (--repath <= 0) {
				repath = 10;
				treant.getNavigation().moveTo(owner, 1.1);
			}
		}

		@Override
		public void stop() {
			treant.getNavigation().stop();
		}
	}
}
