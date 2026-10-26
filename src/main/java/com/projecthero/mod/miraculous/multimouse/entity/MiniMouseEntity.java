package com.projecthero.mod.miraculous.multimouse.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;
import com.projecthero.mod.miraculous.multimouse.MultimouseMoves;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: a mini Multimouse -- a tiny copy of her (the client draws it with her own skin, the suit, the ears, belt and
 * tail; {@code MultimouseConfig.MINI_SCALE} through the generic.scale attribute). Two kinds:
 * <ul>
 *   <li>{@link #MULTITUDE} -- one of the ten copies of the special: follows her, and goes for whatever she hits or
 *       looks at (else the nearest threat to her); {@code MINI_BITE} a bite, {@code MINI_HEALTH} health;</li>
 *   <li>{@link #SWARM} -- one of Mouse Swarm's three: darts at its given target for {@code SWARM_TICKS}, then pops.</li>
 * </ul>
 * Bites go out as HER damage ({@code playerAttack(owner)}), so a boss's threat table counts them as hers. Only a
 * creature's hit hurts a copy (never falls, walls, fire: it is a little piece of magic); killed, it pops in a puff of
 * pink sparkle. Never saved, never drops anything; gone the moment she detransforms, leaves or dies.
 */
public class MiniMouseEntity extends PathfinderMob implements OwnableEntity {
	public static final int MULTITUDE = 0;
	public static final int SWARM = 1;

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MiniMouseEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(MiniMouseEntity.class, EntityDataSerializers.INT);
	/** 1 while it runs back to her (recombining). */
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(MiniMouseEntity.class, EntityDataSerializers.BOOLEAN);

	private int life;
	private int maxLife = Integer.MAX_VALUE;
	private float bite = MultimouseConfig.MINI_BITE;
	private int forcedTarget = -1;
	private long nextBite;

	public MiniMouseEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		setPersistenceRequired();
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MultimouseConfig.MINI_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.ATTACK_DAMAGE, MultimouseConfig.MINI_BITE)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.SCALE, MultimouseConfig.MINI_SCALE)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(KIND, MULTITUDE);
		builder.define(RETURNING, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	// ---------------------------------------------------------------- set up

	public void setup(ServerPlayer owner, int kind, int lifeTicks, float biteDamage, @Nullable LivingEntity target) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		entityData.set(KIND, kind);
		this.maxLife = lifeTicks;
		this.bite = biteDamage;
		this.forcedTarget = target == null ? -1 : target.getId();
		setCustomName(owner.getName());
		setCustomNameVisible(false);
		setYRot(owner.getYRot());
		yBodyRot = owner.getYRot();
		yHeadRot = owner.getYRot();
	}

	@Override
	public @Nullable UUID getOwnerUUID() {
		return entityData.get(OWNER).orElse(null);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public boolean returning() {
		return entityData.get(RETURNING);
	}

	public float biteDamage() {
		return bite;
	}

	/** Multitude is recombining: run back to her (no more biting). */
	public void runBack() {
		entityData.set(RETURNING, true);
		setTarget(null);
		forcedTarget = -1;
	}

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
		life++;
		ServerPlayer owner = owner();
		if (owner == null || !owner.isAlive() || owner.level() != level() || !Miraculous.transformedAs(owner, Kwami.MULLO) || life >= maxLife) {
			pop();
			return;
		}
		double toOwner = distanceToSqr(owner);
		if (toOwner > 28 * 28) {
			teleportTo(owner.getX(), owner.getY(), owner.getZ());
			getNavigation().stop();
		}
		if (returning()) {
			if (life % 4 == 0) {
				getNavigation().moveTo(owner, 1.7);
			}
			return;
		}
		if (life % 5 == 1) {
			LivingEntity t = pickTarget(owner);
			setTarget(t);
		}
		LivingEntity t = getTarget();
		if (t != null && (!t.isAlive() || !HeroTargets.canHarm(owner, t))) {
			setTarget(null);
			t = null;
		}
		if (t == null) {
			if (life % 8 == 0 && toOwner > 3 * 3) {
				getNavigation().moveTo(owner, 1.4);
			}
			return;
		}
		getLookControl().setLookAt(t, 30f, 30f);
		double reach = 0.35 + t.getBbWidth() * 0.5 + getBbWidth();
		Vec3 me = position().add(0, getBbHeight() * 0.5, 0);
		boolean close = Math.sqrt(AbilityHelpers.distanceSqToBox(t, me)) <= reach;
		if (close) {
			getNavigation().stop();
			long now = level().getGameTime();
			if (now >= nextBite) {
				nextBite = now + MultimouseConfig.MINI_BITE_EVERY;
				swing(InteractionHand.MAIN_HAND, true);
				MultimouseMoves.bite(owner, this, t, bite);
			}
		} else if (life % 4 == 0 || getNavigation().isDone()) {
			getNavigation().moveTo(t, 1.6);
		}
	}

	/** Its target: the swarm's own, else what she is fighting / looking at, else (Multitude) the nearest threat to her. */
	private @Nullable LivingEntity pickTarget(ServerPlayer owner) {
		if (forcedTarget >= 0 && level().getEntity(forcedTarget) instanceof LivingEntity f && f.isAlive() && HeroTargets.canHarm(owner, f)) {
			return f;
		}
		LivingEntity hurt = owner.getLastHurtMob();
		if (hurt != null && hurt.isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() <= 100 && !(hurt instanceof MiniMouseEntity)
				&& HeroTargets.canHarm(owner, hurt) && hurt.distanceToSqr(owner) < 24 * 24) {
			return hurt;
		}
		LivingEntity looked = AbilityHelpers.raycastEntity(owner, 24.0);
		if (looked != null && !(looked instanceof MiniMouseEntity) && HeroTargets.isHostile(owner, looked)) {
			return looked;
		}
		LivingEntity current = getTarget();
		if (current != null && current.isAlive() && HeroTargets.canHarm(owner, current) && current.distanceToSqr(owner) < 16 * 16) {
			return current;
		}
		if (kind() != MULTITUDE) {
			return null;
		}
		LivingEntity best = null;
		double bestD = 12 * 12;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(12.0),
				e -> !(e instanceof MiniMouseEntity) && HeroTargets.isHostile(owner, e))) {
			double d = e.distanceToSqr(owner);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Gone in a puff of pink sparkle (timed out, recombined, or her suit is off). */
	public void pop() {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			MultimouseMoves.sparkle(sl, position().add(0, getBbHeight() * 0.5, 0), 10);
		}
		discard();
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
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity)) {
			return false; // falls, walls, fire: nothing but a creature touches a copy
		}
		ServerPlayer owner = owner();
		if (attacker instanceof MiniMouseEntity || (owner != null && (attacker == owner || Squads.areAllies(owner, attacker)))) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel sl) {
			MultimouseMoves.sparkle(sl, position().add(0, getBbHeight() * 0.5, 0), 16);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6f, 1.9f);
			discard();
		}
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
		if (id != null && other instanceof Player p && p.getUUID().equals(id)) {
			return true;
		}
		if (other instanceof MiniMouseEntity m && id != null && id.equals(m.getOwnerUUID())) {
			return true;
		}
		return super.isAlliedTo(other);
	}
}
