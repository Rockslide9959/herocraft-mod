package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.renarouge.RenaRougeCombat;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeIllusions;
import com.projecthero.mod.squad.Squads;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: an illusion of Rena Rouge -- a player-shaped copy of her (the client draws it with her own skin model and the
 * suit, the fox ears and tail, the Flute). Four kinds:
 * <ul>
 *   <li>{@link #DECOY} -- left behind by Fox Dash; stands where she was, mobs keep hitting it;</li>
 *   <li>{@link #DOUBLE} -- the Illusion Double; fights for her at {@link RenaRougeConfig#DOUBLE_DAMAGE_SHARE} of her
 *       damage and draws the mobs' attention;</li>
 *   <li>{@link #MIRAGE} -- the five Mirage copies; wander about drawing all the aggro;</li>
 *   <li>{@link #FEINT} -- the copy a feinted hit lands on; dissolves at once.</li>
 * </ul>
 * A player's hit dissolves an illusion at once; a mob's hit costs it one of its {@code hits}, the last one dissolves it.
 * Dissolving: {@link RenaRougeConfig#DISSOLVE_TICKS} of fading into orange light, then gone. It is never saved, never
 * drops anything, and is dropped the moment its owner detransforms, leaves or dies.
 */
public class RenaIllusionEntity extends PathfinderMob implements OwnableEntity {
	public static final int DECOY = 0;
	public static final int DOUBLE = 1;
	public static final int MIRAGE = 2;
	public static final int FEINT = 3;

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(RenaIllusionEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RenaIllusionEntity.class, EntityDataSerializers.INT);
	/** tickCount the dissolve began at; -1 while whole. */
	private static final EntityDataAccessor<Integer> DISSOLVE = SynchedEntityData.defineId(RenaIllusionEntity.class, EntityDataSerializers.INT);
	private int life;
	private int maxLife = 20;
	private int hitsLeft = 1;
	private float meleeDamage = 2f;

	public RenaIllusionEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		setPersistenceRequired();
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(KIND, DECOY);
		builder.define(DISSOLVE, -1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new KindGoal(DOUBLE, new MeleeAttackGoal(this, 1.25, true)));
		goalSelector.addGoal(4, new KindGoal(MIRAGE, new WaterAvoidingRandomStrollGoal(this, 0.9, 0.08f)));
	}

	/** A goal that only runs for one kind of illusion (the goals are set up before the kind is known). */
	private final class KindGoal extends Goal {
		private final int kind;
		private final Goal inner;

		KindGoal(int kind, Goal inner) {
			this.kind = kind;
			this.inner = inner;
			setFlags(inner.getFlags());
		}

		@Override
		public boolean canUse() {
			return kind() == kind && !dissolving() && inner.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return kind() == kind && !dissolving() && inner.canContinueToUse();
		}

		@Override
		public void start() {
			inner.start();
		}

		@Override
		public void stop() {
			inner.stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return inner.requiresUpdateEveryTick();
		}

		@Override
		public void tick() {
			inner.tick();
		}
	}

	// ---------------------------------------------------------------- set up

	/** Binds the illusion to {@code owner}: its kind, how long it lasts, how many mob hits it takes. */
	public void setup(ServerPlayer owner, int kind, int lifeTicks, int hits) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		entityData.set(KIND, kind);
		this.maxLife = lifeTicks;
		this.hitsLeft = Math.max(1, hits);
		if (kind == DOUBLE) {
			meleeDamage = (float) Math.max(1.0, owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * RenaRougeConfig.DOUBLE_DAMAGE_SHARE);
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(meleeDamage);
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
		}
		setCustomName(owner.getName());
		setCustomNameVisible(false);
	}

	public Optional<UUID> ownerId() {
		return entityData.get(OWNER);
	}

	@Override
	public @Nullable UUID getOwnerUUID() {
		return ownerId().orElse(null);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public boolean dissolving() {
		return entityData.get(DISSOLVE) >= 0;
	}

	/** 0 whole .. 1 gone (client-safe). */
	public float dissolveProgress(float partial) {
		int at = entityData.get(DISSOLVE);
		if (at < 0) {
			return 0f;
		}
		return Math.min(1f, (tickCount - at + partial) / RenaRougeConfig.DISSOLVE_TICKS);
	}

	public int life() {
		return life;
	}

	public int hitsLeft() {
		return hitsLeft;
	}

	public float meleeDamage() {
		return meleeDamage;
	}

	/** Its owner (works for gametest mock players too, which are not in the player list). */
	public @Nullable ServerPlayer owner() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		UUID id = getOwnerUUID();
		return id != null && sl.getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
	}

	// ---------------------------------------------------------------- the tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		if (dissolving()) {
			setDeltaMovement(getDeltaMovement().multiply(0.5, 0.5, 0.5));
			getNavigation().stop();
			if (dissolveProgress(0f) >= 1f) {
				discard();
			}
			return;
		}
		life++;
		ServerPlayer owner = owner();
		if (owner == null || !owner.isAlive() || owner.level() != level() || !Miraculous.transformedAs(owner, Kwami.TRIXX)) {
			dissolve();
			return;
		}
		if (life >= maxLife) {
			dissolve();
			return;
		}
		if (kind() != FEINT && life % 5 == 1) {
			RenaRougeIllusions.drawAggro(this, owner);
		}
		if (kind() == DOUBLE) {
			fight(owner);
		}
		if (life % 8 == 0 && level() instanceof ServerLevel sl) {
			// the faint shimmer that gives an illusion away to a sharp eye
			sl.sendParticles(RenaRougeCombat.ORANGE, getX(), getY() + 0.3 + random.nextDouble() * 1.4, getZ(), 1, 0.25, 0.1, 0.25, 0.0);
		}
	}

	/** The Double: goes for the nearest threat to her within 16 blocks, else stays at her side. */
	private void fight(ServerPlayer owner) {
		LivingEntity t = getTarget();
		if (t != null && (!t.isAlive() || !HeroTargets.canHarm(owner, t) || t.distanceToSqr(this) > 24 * 24)) {
			setTarget(null);
			t = null;
		}
		if (t == null && life % 10 == 0) {
			LivingEntity best = null;
			double bestD = 16 * 16;
			for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16.0),
					e -> e != owner && !(e instanceof RenaIllusionEntity) && HeroTargets.isHostile(owner, e))) {
				double d = e.distanceToSqr(this);
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
			if (best != null) {
				setTarget(best);
			}
		}
		if (getTarget() == null && life % 10 == 0 && distanceToSqr(owner) > 5 * 5) {
			getNavigation().moveTo(owner, 1.2);
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		ServerPlayer owner = owner();
		if (owner == null || !(target instanceof LivingEntity le)) {
			return false;
		}
		swing(InteractionHand.MAIN_HAND);
		return RenaRougeCombat.strike(owner, le, position(), meleeDamage, 0.4, 0.0, level().damageSources().mobAttack(this), false);
	}

	// ---------------------------------------------------------------- getting hit

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved()) {
			return false;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			discard();
			return true;
		}
		if (dissolving()) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity)) {
			return false; // fire, falls, cactus: nothing real touches an illusion
		}
		ServerPlayer owner = owner();
		if (owner != null && (attacker == owner || Squads.areAllies(owner, attacker))) {
			return false;
		}
		if (attacker instanceof Player || --hitsLeft <= 0) {
			dissolve();
		} else if (level() instanceof ServerLevel sl) {
			// a flicker: the copy wavers but holds
			RenaRougeCombat.glow(sl, position().add(0, 1.0, 0), 6, 0.3);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
			hurtTime = 6;
			hurtDuration = 6;
		}
		return true;
	}

	/** Fades into orange light over {@link RenaRougeConfig#DISSOLVE_TICKS}; whatever was after it loses interest. */
	public void dissolve() {
		if (dissolving() || isRemoved()) {
			return;
		}
		entityData.set(DISSOLVE, tickCount);
		setNoAi(true);
		noPhysics = true;
		setDeltaMovement(Vec3.ZERO);
		if (level() instanceof ServerLevel sl) {
			RenaRougeCombat.dissolveBurst(sl, position(), getBbHeight());
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9f, 1.7f);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6f, 1.5f);
			for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32.0), m -> m.getTarget() == this)) {
				m.setTarget(null);
			}
		}
	}

	@Override
	public boolean isPickable() {
		return !dissolving() && super.isPickable();
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return !dissolving() && super.canBeSeenAsEnemy();
	}

	@Override
	public boolean isPushable() {
		return !dissolving() && super.isPushable();
	}

	// ---------------------------------------------------------------- never a real creature

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
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		UUID id = getOwnerUUID();
		if (id != null && other instanceof Player p && (p.getUUID().equals(id))) {
			return true;
		}
		if (other instanceof RenaIllusionEntity ri && id != null && id.equals(ri.getOwnerUUID())) {
			return true;
		}
		return super.isAlliedTo(other);
	}
}
