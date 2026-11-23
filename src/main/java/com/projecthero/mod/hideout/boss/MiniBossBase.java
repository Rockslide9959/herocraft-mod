package com.projecthero.mod.hideout.boss;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: what the three new hideout mini-bosses (Crypt Lord, Brood Spawn of Carnage, Oathbound Champion) share: a
 * {@link HideoutBossLink} (core, boss bar, the shared {@code BossThreat} table -- no {@code HurtByTargetGoal} -- and the
 * leash), a {@link HideoutPhase} second phase, and a tiny move system: one synced action byte (the renderer poses from
 * it), a per-move tick counter, melee only while no move runs. Subclasses pick a move ({@link #chooseMove}) and run it
 * ({@link #tickMove}). Allied with every hideout guard; no static state.
 */
public abstract class MiniBossBase extends Monster implements HideoutBoss {
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(MiniBossBase.class, EntityDataSerializers.BYTE);
	public static final byte ACTION_NONE = 0;

	protected final HideoutBossLink link;
	protected final HideoutPhase phase;
	protected int moveTicks;
	/** Client: the tick the current action started (for the pose). */
	public float actionStartTick;

	protected MiniBossBase(EntityType<? extends MiniBossBase> type, Level level, HideoutFaction faction, BossEvent.BossBarColor colour,
			HideoutPhase.Kind phaseKind, int phaseCooldown) {
		super(type, level);
		this.link = new HideoutBossLink(this, faction, colour, this::busy);
		this.phase = new HideoutPhase(this, faction, phaseKind, 0.5f, phaseCooldown);
		this.xpReward = 140;
		setPersistenceRequired();
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	@Override
	public HideoutPhase phase() {
		return phase;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	protected void setAction(byte a) {
		entityData.set(DATA_ACTION, a);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ACTION.equals(accessor)) {
			actionStartTick = tickCount;
		}
	}

	public boolean busy() {
		return action() != ACTION_NONE;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true) {
			@Override
			public boolean canUse() {
				return !busy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !busy() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Its own guards and the other hideout bosses. */
	protected boolean friend(Entity e) {
		if (e == this) {
			return true;
		}
		if (e instanceof HideoutBoss) {
			return true;
		}
		return e instanceof Mob m && (AlarmGuards.isGuard(m) || HideoutGuards.isGuard(m) || m instanceof com.projecthero.mod.hideout.mob.HideoutSoldier);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return friend(other) || super.isAlliedTo(other);
	}

	/** A valid victim for an area move: alive, not a friend, not a creative / spectator player. */
	protected boolean victim(LivingEntity e) {
		return e.isAlive() && !friend(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Damage multiplier for incoming hits (fire weakness etc.). */
	protected float incoming(DamageSource source, float amount) {
		return amount;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() != null && source.getEntity() != this && friend(source.getEntity())) {
			return false;
		}
		boolean hurt = super.hurt(source, incoming(source, amount));
		if (hurt && !level().isClientSide()) {
			link.onHurt(source, amount);
			phase.hurtCheck((ServerLevel) level());
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		link.tick(server);
		phase.tick(server, busy());
		ambient(server);
		tickCooldowns();
		if (busy()) {
			moveTicks++;
			getNavigation().stop();
			LivingEntity t = getTarget();
			if (t != null && lockLook()) {
				getLookControl().setLookAt(t, 30f, 30f);
			}
			tickMove(server, action(), moveTicks);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}
		byte next = chooseMove(server, t, distanceTo(t));
		if (next != ACTION_NONE) {
			start(next);
		}
	}

	/** Starts a move now (test hook too). */
	public void start(byte action) {
		setAction(action);
		moveTicks = 0;
		getNavigation().stop();
		onStart(action);
	}

	protected void end() {
		setAction(ACTION_NONE);
	}

	/** Whether the boss keeps facing its target during a move (a charge does not). */
	protected boolean lockLook() {
		return true;
	}

	protected abstract void tickCooldowns();

	/** The move to start now, or {@link #ACTION_NONE}. */
	protected abstract byte chooseMove(ServerLevel level, LivingEntity target, double distance);

	protected void onStart(byte action) {
	}

	protected abstract void tickMove(ServerLevel level, byte action, int ticks);

	/** Idle particles. */
	protected void ambient(ServerLevel level) {
	}

	/** Horizontal look direction (yaw), normalised. */
	protected Vec3 flatLook() {
		double r = Math.toRadians(getYRot());
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	/** True when {@code e} is within {@code range} and inside a {@code degrees}-wide cone in front of the boss. */
	protected boolean inCone(LivingEntity e, double range, double degrees) {
		Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
		double len = d.length();
		if (len > range + e.getBbWidth() * 0.5) {
			return false;
		}
		if (len < 1.0) {
			return true;
		}
		double dot = d.normalize().dot(flatLook());
		return dot >= Math.cos(Math.toRadians(degrees / 2));
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			link.onDeath(server);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		link.stopSeen(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		link.detach();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
		phase.save(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
		phase.load(tag);
	}
}
