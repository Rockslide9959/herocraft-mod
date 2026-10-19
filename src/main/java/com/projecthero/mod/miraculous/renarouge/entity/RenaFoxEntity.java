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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7, the Mirage wheel's Army of Foxes: an illusory fox that fights for Rena Rouge. A vanilla fox body (drawn
 * orange and see-through by {@code RenaFoxRenderer}) with its own mind: it chases the nearest enemy within
 * {@link RenaRougeConfig#FOX_SEEK_RANGE}, bites for {@link RenaRougeConfig#FOX_DAMAGE} through {@link RenaRougeCombat#strike}
 * (so never her, a squadmate or a friendly pet), and pulls the mob it bites onto itself. It takes
 * {@link RenaRougeConfig#FOX_HITS} mob hits (a player's hit at once) and then pops into orange mist; it lasts the
 * Mirage and goes with her. Never saved, no loot, no breeding; {@link OwnableEntity}, so {@link HeroTargets} treats it
 * as her summon.
 */
public class RenaFoxEntity extends Fox implements OwnableEntity {
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(RenaFoxEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	/** tickCount the pop began at; -1 while whole. */
	private static final EntityDataAccessor<Integer> POP = SynchedEntityData.defineId(RenaFoxEntity.class, EntityDataSerializers.INT);
	private int life;
	private int maxLife = 600;
	private int hitsLeft = RenaRougeConfig.FOX_HITS;

	public RenaFoxEntity(EntityType<? extends Fox> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setCanPickUpLoot(false);
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Fox.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, RenaRougeConfig.FOX_SPEED)
				.add(Attributes.ATTACK_DAMAGE, RenaRougeConfig.FOX_DAMAGE)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(POP, -1);
	}

	/** None of the wild fox's goals (sleeping, hunting chickens, eating berries): only the fight. */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5, true));
	}

	public void setup(ServerPlayer owner, int lifeTicks) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		maxLife = lifeTicks;
	}

	@Override
	public @Nullable UUID getOwnerUUID() {
		return entityData.get(OWNER).orElse(null);
	}

	public boolean popping() {
		return entityData.get(POP) >= 0;
	}

	/** 0 whole .. 1 gone (client-safe). */
	public float popProgress(float partial) {
		int at = entityData.get(POP);
		return at < 0 ? 0f : Math.min(1f, (tickCount - at + partial) / RenaRougeConfig.DISSOLVE_TICKS);
	}

	public int hitsLeft() {
		return hitsLeft;
	}

	public @Nullable ServerPlayer owner() {
		UUID id = getOwnerUUID();
		return id != null && level() instanceof ServerLevel sl && sl.getPlayerByUUID(id) instanceof ServerPlayer sp ? sp : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		if (popping()) {
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0.4, 0.4, 0.4));
			if (popProgress(0f) >= 1f) {
				discard();
			}
			return;
		}
		life++;
		ServerPlayer owner = owner();
		if (owner == null || !owner.isAlive() || owner.level() != level() || !Miraculous.transformedAs(owner, Kwami.TRIXX) || life >= maxLife) {
			pop();
			return;
		}
		LivingEntity t = getTarget();
		if (t != null && (!t.isAlive() || !HeroTargets.canHarm(owner, t) || t.distanceToSqr(this) > 24 * 24)) {
			setTarget(null);
			t = null;
		}
		if (t == null && life % 10 == 1) {
			t = seek(owner);
			if (t != null) {
				setTarget(t);
			}
		}
		if (t instanceof Mob m && life % 20 == 0) {
			// the bitten one turns on the fox
			LivingEntity mt = m.getTarget();
			if (mt == null || mt == owner || (mt instanceof Player pl && Squads.areAllies(owner, pl))) {
				if (RenaRougeCombat.isBoss(m)) {
					RenaRougeIllusions.taunt(m, this);
				} else if (RenaRougeCombat.isFoolable(owner, m)) {
					RenaRougeIllusions.retarget(m, this);
				}
			}
		}
		if (getTarget() == null && life % 10 == 0 && distanceToSqr(owner) > 6 * 6) {
			getNavigation().moveTo(owner, 1.4);
		}
		if (life % 10 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(RenaRougeCombat.ORANGE, getX(), getY() + 0.4, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** The nearest enemy of hers within {@link RenaRougeConfig#FOX_SEEK_RANGE} (those after her first). */
	private @Nullable LivingEntity seek(ServerPlayer owner) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		double r = RenaRougeConfig.FOX_SEEK_RANGE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
				e -> e != owner && !(e instanceof RenaIllusionEntity) && !(e instanceof RenaFoxEntity) && HeroTargets.isHostile(owner, e))) {
			double d = e.distanceToSqr(this);
			if (d > r * r) {
				continue;
			}
			if (e instanceof Mob m && m.getTarget() == owner) {
				d *= 0.25; // the ones after her first
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		ServerPlayer owner = owner();
		if (owner == null || popping() || !(target instanceof LivingEntity le)) {
			return false;
		}
		swing(InteractionHand.MAIN_HAND);
		boolean hit = RenaRougeCombat.strike(owner, le, position(), RenaRougeConfig.FOX_DAMAGE, 0.2, 0.0, level().damageSources().mobAttack(this), false);
		if (hit) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 0.8f, 1.1f + random.nextFloat() * 0.2f);
		}
		return hit;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved()) {
			return false;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			discard();
			return true;
		}
		if (popping()) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity)) {
			return false; // nothing real touches an illusion
		}
		ServerPlayer owner = owner();
		if (owner != null && (attacker == owner || Squads.areAllies(owner, attacker) || HeroTargets.isOwnedBy(attacker, owner))) {
			return false;
		}
		if (attacker instanceof Player || --hitsLeft <= 0) {
			pop();
		} else if (level() instanceof ServerLevel sl) {
			RenaRougeCombat.glow(sl, position().add(0, 0.4, 0), 5, 0.25);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_HURT, SoundSource.PLAYERS, 0.7f, 1.4f);
			hurtTime = 6;
			hurtDuration = 6;
		}
		return true;
	}

	/** Bursts into orange mist; whatever was after it loses interest. */
	public void pop() {
		if (popping() || isRemoved()) {
			return;
		}
		entityData.set(POP, tickCount);
		setNoAi(true);
		noPhysics = true;
		setDeltaMovement(Vec3.ZERO);
		if (level() instanceof ServerLevel sl) {
			RenaRougeCombat.dissolveBurst(sl, position(), getBbHeight() + 0.4f);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f);
			for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24.0), m -> m.getTarget() == this)) {
				m.setTarget(null);
			}
		}
	}

	@Override
	public boolean isPickable() {
		return !popping() && super.isPickable();
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return !popping() && super.canBeSeenAsEnemy();
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
		return false;
	}

	@Override
	public boolean isFood(net.minecraft.world.item.ItemStack stack) {
		return false;
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
		UUID id = getOwnerUUID();
		if (id != null) {
			if (other instanceof Player p && p.getUUID().equals(id)) {
				return true;
			}
			if (other instanceof RenaIllusionEntity ri && id.equals(ri.getOwnerUUID())) {
				return true;
			}
			if (other instanceof RenaFoxEntity rf && id.equals(rf.getOwnerUUID())) {
				return true;
			}
		}
		return super.isAlliedTo(other);
	}
}
