package com.projecthero.mod.undead.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.projecthero.mod.undead.UndeadBulletConfig;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.combat.HeroTargets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.3 (user): Shift+C, the Division Bullet's clone -- "a clone appears where the finger lands, the clone lives for 20 seconds
 * and acts as a dog, the clone attacks whatever harms the player or whatever the player hits and just tries to protect the
 * player, clones deal 8 damage per hit, clones just look exactly the same as the player themself ... clone can get killed and has
 * 30hp". Player-shaped and drawn in the owner's own skin, untinted ({@code UndeadCloneRenderer}).
 *
 * <p>Like a tamed wolf: it follows the owner (walks back past {@link UndeadBulletConfig#CLONE_FOLLOW_START}, blinks to him past
 * {@link UndeadBulletConfig#CLONE_TELEPORT}); it goes for whatever last hurt the owner, whatever the owner last hit, whatever
 * hunts the owner and whatever hurts the clone itself -- never the owner, his squad or their pets ({@link HeroTargets#canHarm}),
 * never another of his clones, and nothing the owner is merely near (it is a guard, not a hunter). It bursts into pixels when its
 * {@link UndeadBulletConfig#CLONE_LIFE} runs out or it dies. Never saved; the owner's own moves pass through it.
 */
public class UndeadCloneEntity extends PathfinderMob implements OwnableEntity {
	/** Set by the client: bursts the clone into pixels as it disappears. */
	public static Consumer<UndeadCloneEntity> clientRemoval = e -> {
	};

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(UndeadCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private int lifeLeft = UndeadBulletConfig.CLONE_LIFE;
	private float hitDamage = UndeadBulletConfig.CLONE_DAMAGE;

	public UndeadCloneEntity(EntityType<? extends UndeadCloneEntity> type, Level level) {
		super(type, level);
		this.setPathfindingMalus(PathType.WATER, -1.0f);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, UndeadBulletConfig.CLONE_HEALTH).add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.ATTACK_DAMAGE, UndeadBulletConfig.CLONE_DAMAGE).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	public static UndeadCloneEntity spawn(ServerPlayer owner, double x, double y, double z, int life, float damage) {
		UndeadCloneEntity c = new UndeadCloneEntity(UndeadEntityTypes.CLONE, owner.level());
		c.entityData.set(OWNER, Optional.of(owner.getUUID()));
		c.lifeLeft = life;
		c.hitDamage = damage;
		float yaw = owner.getYRot();
		c.moveTo(x, y, z, yaw, 0f);
		c.setYHeadRot(yaw);
		c.yBodyRot = yaw;
		c.setHealth(c.getMaxHealth());
		owner.level().addFreshEntity(c);
		return c;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(4, new FollowOwnerGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	public UUID getOwnerUUID() {
		return this.entityData.get(OWNER).orElse(null);
	}

	public ServerPlayer ownerPlayer() {
		UUID id = this.getOwnerUUID();
		return id != null && this.level().getPlayerByUUID(id) instanceof ServerPlayer p ? p : null;
	}

	/** Ticks of life left (tests). */
	public int lifeLeft() {
		return this.lifeLeft;
	}

	/** A fair target for this clone: one its owner may harm, alive, not one of his clones. */
	public boolean mayTarget(ServerPlayer owner, LivingEntity e) {
		return e != null && e != this && e != owner && e.isAlive() && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(owner, e);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.updateSwingTime();
		if (this.level().isClientSide) {
			return;
		}
		ServerPlayer owner = this.ownerPlayer();
		if (--this.lifeLeft <= 0 || owner == null || !owner.isAlive() || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (this.tickCount % 5 == 0) {
			LivingEntity current = this.getTarget();
			if (current != null && !this.mayTarget(owner, current)) {
				this.setTarget(null);
				current = null;
			}
			LivingEntity threat = this.threat(owner);
			// the owner's fight comes first: whatever is on him now takes over from a target of its own
			if (threat != null && threat != current && (current == null || threat == owner.getLastHurtByMob())) {
				this.setTarget(threat);
			}
			if (this.getTarget() != null && this.getTarget().distanceToSqr(owner) > 32 * 32) {
				this.setTarget(null); // too far from him: back to his side
			}
		}
	}

	/**
	 * What it should fight for the owner, like a tamed wolf: what just hurt him, what he is fighting, what is hunting him or
	 * what just hurt the clone. Null when he is at peace.
	 */
	public LivingEntity threat(ServerPlayer owner) {
		LivingEntity by = owner.getLastHurtByMob();
		if (this.mayTarget(owner, by)) {
			return by;
		}
		LivingEntity hit = owner.getLastHurtMob();
		if (this.mayTarget(owner, hit) && owner.tickCount - owner.getLastHurtMobTimestamp() <= HeroTargets.RECENT_ATTACK_TICKS) {
			return hit;
		}
		LivingEntity mine = this.getLastHurtByMob();
		if (this.mayTarget(owner, mine)) {
			return mine;
		}
		double r = UndeadBulletConfig.CLONE_GUARD_RANGE;
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (Mob m : this.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(r), m -> m.getTarget() == owner && this.mayTarget(owner, m))) {
			double d = m.distanceToSqr(owner);
			if (d < bestSq) {
				bestSq = d;
				best = m;
			}
		}
		return best;
	}

	/** 8 damage a hit (a boss takes no more than its usual share), credited to the clone so the target turns on it. */
	@Override
	public boolean doHurtTarget(Entity target) {
		ServerPlayer owner = this.ownerPlayer();
		if (owner == null || !(target instanceof LivingEntity le) || !this.mayTarget(owner, le)) {
			return false;
		}
		this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		float d = this.hitDamage;
		if (le.getMaxHealth() >= UndeadConfig.BOSS_HEALTH_THRESHOLD || com.projecthero.mod.titanshifter.TitanCombat.isBoss(le)) {
			d = Math.min(d, le.getMaxHealth() * UndeadConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean hit = le.hurt(this.damageSources().mobAttack(this), Math.max(1f, d));
		if (hit) {
			le.knockback(0.4, this.getX() - le.getX(), this.getZ() - le.getZ());
			if (this.level() instanceof net.minecraft.server.level.ServerLevel level) {
				level.sendParticles(com.projecthero.mod.undead.Undead.BLOOD, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 4, 0.2, 0.25, 0.2, 0.0);
			}
		}
		return hit;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		Entity by = source.getEntity();
		if (by instanceof UndeadCloneEntity || (by instanceof Player p && p.getUUID().equals(this.getOwnerUUID()))) {
			return false; // its owner and its brothers never hurt it
		}
		return super.hurt(source, amount);
	}

	@Override
	protected void tickDeath() {
		if (!this.level().isClientSide) {
			this.remove(RemovalReason.KILLED); // no tip-over: it bursts into pixels at once
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	protected boolean shouldDropLoot() {
		return false;
	}

	@Override
	public void onClientRemoval() {
		clientRemoval.accept(this);
	}

	/** A tamed wolf's follow: back to the owner when he gets away, a blink to his side when far; not while fighting. */
	static final class FollowOwnerGoal extends Goal {
		private final UndeadCloneEntity clone;
		private int recalc;

		FollowOwnerGoal(UndeadCloneEntity clone) {
			this.clone = clone;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer o = this.clone.ownerPlayer();
			return o != null && !o.isSpectator() && this.clone.getTarget() == null
					&& this.clone.distanceToSqr(o) > UndeadBulletConfig.CLONE_FOLLOW_START * UndeadBulletConfig.CLONE_FOLLOW_START;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer o = this.clone.ownerPlayer();
			return o != null && this.clone.getTarget() == null && !this.clone.getNavigation().isDone()
					&& this.clone.distanceToSqr(o) > UndeadBulletConfig.CLONE_FOLLOW_STOP * UndeadBulletConfig.CLONE_FOLLOW_STOP;
		}

		@Override
		public void start() {
			this.recalc = 0;
		}

		@Override
		public void stop() {
			this.clone.getNavigation().stop();
		}

		@Override
		public void tick() {
			ServerPlayer o = this.clone.ownerPlayer();
			if (o == null) {
				return;
			}
			this.clone.getLookControl().setLookAt(o, 10f, this.clone.getMaxHeadXRot());
			if (--this.recalc > 0) {
				return;
			}
			this.recalc = 10;
			if (this.clone.distanceToSqr(o) > UndeadBulletConfig.CLONE_TELEPORT * UndeadBulletConfig.CLONE_TELEPORT) {
				this.blink(o);
			} else {
				this.clone.getNavigation().moveTo(o, 1.25);
			}
		}

		/** To a free spot of floor beside him. */
		private void blink(ServerPlayer o) {
			BlockPos at = o.blockPosition();
			for (int i = 0; i < 10; i++) {
				int dx = this.clone.getRandom().nextIntBetweenInclusive(-3, 3);
				int dz = this.clone.getRandom().nextIntBetweenInclusive(-3, 3);
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					continue;
				}
				BlockPos p = at.offset(dx, 0, dz);
				AABB box = this.clone.getType().getDimensions().makeBoundingBox(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
				if (this.clone.level().noCollision(this.clone, box) && !this.clone.level().getBlockState(p.below()).getCollisionShape(this.clone.level(), p.below()).isEmpty()) {
					this.clone.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, this.clone.getYRot(), this.clone.getXRot());
					this.clone.getNavigation().stop();
					return;
				}
			}
		}
	}
}
