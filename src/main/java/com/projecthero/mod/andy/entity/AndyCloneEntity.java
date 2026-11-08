package com.projecthero.mod.andy.entity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.projecthero.mod.andy.AndyAbilities;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.combat.HeroTargets;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.3: one of Andy's split-off selves -- Dead Quardead's skin, blood and bones (three, ~15 s), or Victor's Division
 * Bullet (a finger grown into a whole, short-lived self). Player-shaped, drawn in the owner's own skin with a tint
 * ({@link #TINT_SKIN}, {@link #TINT_BLOOD}, {@link #TINT_BONE}); fights whatever the owner may harm and is fighting (or what
 * hunts him); bursts into pixels when its time runs out. Never saved; the owner's own moves pass through it.
 */
public class AndyCloneEntity extends PathfinderMob implements OwnableEntity {
	public static final int TINT_SKIN = 0;
	public static final int TINT_BLOOD = 1;
	public static final int TINT_BONE = 2;

	/** Set by the client: bursts the clone into pixels as it disappears. */
	public static Consumer<AndyCloneEntity> clientRemoval = e -> {
	};

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AndyCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> TINT = SynchedEntityData.defineId(AndyCloneEntity.class, EntityDataSerializers.INT);

	private int lifeLeft = AndyConfig.QUARDEAD_TICKS;
	private float hitDamage = AndyConfig.CLONE_DAMAGE;

	public AndyCloneEntity(EntityType<? extends AndyCloneEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.34)
				.add(Attributes.ATTACK_DAMAGE, AndyConfig.CLONE_DAMAGE).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	public static AndyCloneEntity spawn(ServerPlayer owner, double x, double y, double z, int tint, int life, float damage) {
		AndyCloneEntity c = new AndyCloneEntity(AndyEntityTypes.CLONE, owner.level());
		c.entityData.set(OWNER, Optional.of(owner.getUUID()));
		c.entityData.set(TINT, tint);
		c.lifeLeft = life;
		c.hitDamage = damage;
		c.moveTo(x, y, z, owner.getYRot(), 0f);
		c.setYHeadRot(owner.getYRot());
		c.yBodyRot = owner.getYRot();
		owner.level().addFreshEntity(c);
		return c;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(TINT, TINT_SKIN);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	public int tint() {
		return this.entityData.get(TINT);
	}

	@Override
	public UUID getOwnerUUID() {
		return this.entityData.get(OWNER).orElse(null);
	}

	public ServerPlayer ownerPlayer() {
		UUID id = this.getOwnerUUID();
		return id != null && this.level().getPlayerByUUID(id) instanceof ServerPlayer p ? p : null;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide) {
			return;
		}
		ServerPlayer owner = this.ownerPlayer();
		if (--this.lifeLeft <= 0 || owner == null || !owner.isAlive() || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (this.tickCount % 10 == 0) {
			LivingEntity current = this.getTarget();
			if (current == null || !current.isAlive() || !HeroTargets.canHarm(owner, current)) {
				this.setTarget(pick(owner));
			}
			if (this.getTarget() == null && this.distanceToSqr(owner) > 36) {
				this.getNavigation().moveTo(owner, 1.2);
			}
		}
	}

	private LivingEntity pick(ServerPlayer owner) {
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(this.blockPosition()).inflate(16.0),
				e -> e != owner && !(e instanceof AndyCloneEntity) && HeroTargets.isHostile(owner, e))) {
			double d = e.distanceToSqr(this);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		ServerPlayer owner = this.ownerPlayer();
		if (owner == null || !(target instanceof LivingEntity le)) {
			return false;
		}
		this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		return AndyAbilities.strike(owner, le, this.position(), this.hitDamage, 0.5, 0.1);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof Player p && p.getUUID().equals(this.getOwnerUUID())) {
			return false;
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
}
