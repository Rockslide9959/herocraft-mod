package com.projecthero.mod.sentinel.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.sentinel.SentinelPurgeEvent;
import com.projecthero.mod.sentinel.SentinelTargets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.15.1: what every Sentinel Program robot shares -- the Drone, the Sentinel and Master Mold.
 * <ul>
 *   <li><b>The purge link</b>: a robot sent by a {@link SentinelPurgeEvent} remembers it and removes itself if the purge
 *       no longer exists (ended while its chunk was unloaded) -- the same "orphan guard" as the Parademons.</li>
 *   <li><b>Target priority</b> (v0.15.3: spread aggro): every 4-6 seconds it re-weighs everyone in range with
 *       {@link #threatScore} -- mutants first, then superhumans, then anyone else, weighed against distance, sight, the
 *       damage they've dealt it lately and how long it has already been on them (after ~11 s it strongly prefers someone
 *       else) -- and switches at once to anyone who clearly out-damages its current target. It announces each lock-on
 *       ("MUTANT DETECTED").</li>
 *   <li><b>Aimed shots</b> (v0.15.3): its beams are {@link SentinelAimedShot}s -- a lagging aiming line, a lock, then the
 *       beam along the locked line -- so they can be dodged.</li>
 *   <li><b>Machines</b>: they never hurt each other, take no fall damage, can't burn or be poisoned.</li>
 * </ul>
 */
public abstract class SentinelRobot extends Monster implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private UUID purgeId;
	private UUID lastAnnouncedTarget;

	protected SentinelRobot(EntityType<? extends SentinelRobot> type, Level level) {
		super(type, level);
	}

	public UUID purgeId() {
		return purgeId;
	}

	public void bindToPurge(UUID id) {
		this.purgeId = id;
	}

	/** How far this robot looks for prey. */
	protected double huntRange() {
		return 48.0;
	}

	/** The name used for its voice lines. */
	protected Component speakerName() {
		return getDisplayName();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (tickCount % 40 == 0 && purgeId != null && SentinelPurgeEvent.find(server, purgeId) == null) {
			discard();
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && (!SentinelTargets.canTarget(target) || distanceToSqr(target) > huntRange() * huntRange())) {
			setTarget(null);
			target = null;
			retargetIn = 0;
		}
		tickAggro(target);
		if (--retargetIn <= 0 || target == null && tickCount % 20 == 0) {
			retarget(server);
			target = getTarget();
		}
		if (target instanceof Player p && !p.getUUID().equals(lastAnnouncedTarget)) {
			lastAnnouncedTarget = p.getUUID();
			SentinelTargets.announceLock(server, speakerName(), p);
		}
	}

	// ---------------------------------------------------------------- v0.15.3: spread aggro

	/** Continuous focus (ticks) on one target after which the robot strongly prefers anyone else (~11 s). */
	public static final int FOCUS_CAP = 220;
	/** Recent damage dealt to it decays by this factor every tick (half-life ~5 s). */
	private static final float DAMAGE_DECAY = 0.993f;
	/** How often (ticks) it re-weighs its prey: 4 to 6 seconds. */
	private static final int RETARGET_MIN = 80, RETARGET_SPREAD = 41;

	private final Map<UUID, Float> recentDamage = new HashMap<>();
	private final Map<UUID, Integer> focus = new HashMap<>();
	private int retargetIn = 20;

	/** Recent damage bookkeeping and focus fatigue: the current target's grows, everyone else's wears off. */
	private void tickAggro(LivingEntity target) {
		UUID cur = target == null ? null : target.getUUID();
		recentDamage.replaceAll((id, d) -> d * DAMAGE_DECAY);
		recentDamage.values().removeIf(d -> d < 0.05f);
		focus.replaceAll((id, t) -> id.equals(cur) ? t + 1 : t - 1);
		focus.values().removeIf(t -> t <= 0);
		if (cur != null) {
			focus.putIfAbsent(cur, 1);
		}
	}

	/**
	 * Re-weighs every valid target in range: the Sentinel Program's priority (mutants, then superhumans, then humans), how
	 * close and visible it is, how much it has hurt the robot lately, and how long the robot has already been on it -- past
	 * {@link #FOCUS_CAP} the current target is heavily discounted, so anyone else nearby gets a turn. A lone target is
	 * always kept.
	 */
	public void retarget(ServerLevel server) {
		double r2 = huntRange() * huntRange();
		java.util.List<LivingEntity> candidates = new java.util.ArrayList<>();
		for (Player p : server.players()) {
			if (SentinelTargets.canTarget(p) && p.distanceToSqr(this) <= r2) {
				candidates.add(p);
			}
		}
		retargetAmong(candidates);
	}

	/** {@link #retarget} over an explicit candidate list (the current target is always considered while valid). */
	public void retargetAmong(java.util.List<? extends LivingEntity> players) {
		retargetIn = RETARGET_MIN + random.nextInt(RETARGET_SPREAD);
		LivingEntity cur = getTarget();
		LivingEntity best = null;
		double bestScore = -Double.MAX_VALUE;
		java.util.List<LivingEntity> candidates = new java.util.ArrayList<>(players);
		if (cur != null && !candidates.contains(cur) && SentinelTargets.canTarget(cur)) {
			candidates.add(cur);
		}
		for (LivingEntity c : candidates) {
			double s = threatScore(c, c == cur);
			if (s > bestScore) {
				bestScore = s;
				best = c;
			}
		}
		if (best != null && best != cur) {
			setTarget(best);
		}
	}

	/** How much the robot wants {@code c} right now (higher wins). */
	public double threatScore(LivingEntity c, boolean current) {
		double s = c instanceof Player p ? SentinelTargets.classify(p).ordinal() * 25.0 : 0.0;
		s -= distanceTo(c) * 0.5;
		if (hasLineOfSight(c)) {
			s += 10.0;
		}
		s += recentDamage.getOrDefault(c.getUUID(), 0f) * 3.0;
		int f = focus.getOrDefault(c.getUUID(), 0);
		s -= f * 0.05; // a slow drift toward fresh prey (-11 at the cap) ...
		if (current) {
			s += f < FOCUS_CAP ? 8.0 : -40.0; // ... a little stickiness, then a hard push off past the cap
		}
		return s;
	}

	/** Records who hurt it; someone who clearly out-damages the current target takes its attention at once. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this
				&& !(attacker instanceof SentinelRobot)) {
			float dealt = recentDamage.merge(attacker.getUUID(), amount, Float::sum);
			LivingEntity cur = getTarget();
			if (cur != attacker && SentinelTargets.canTarget(attacker) && distanceToSqr(attacker) <= huntRange() * huntRange()) {
				float curDealt = cur == null ? 0f : recentDamage.getOrDefault(cur.getUUID(), 0f);
				if (cur == null || dealt >= curDealt * 1.5f + 4.0f) {
					setTarget(attacker);
					retargetIn = RETARGET_MIN + random.nextInt(RETARGET_SPREAD);
				}
			}
		}
		return hurt;
	}

	/** Test/debug: how long (ticks) the robot has been on {@code id}; set it to fake a long focus. */
	public int focusTicks(UUID id) {
		return focus.getOrDefault(id, 0);
	}

	public void setFocusTicks(UUID id, int ticks) {
		focus.put(id, ticks);
	}

	public float recentDamageFrom(UUID id) {
		return recentDamage.getOrDefault(id, 0f);
	}

	// ---------------------------------------------------------------- v0.15.3: the aimed-shot telegraph, synced for drawing

	/** Low nibble: {@link SentinelAimedShot} state; high nibble: the beam kind. */
	private static final EntityDataAccessor<Byte> DATA_SHOT = SynchedEntityData.defineId(SentinelRobot.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Vector3f> DATA_SHOT_FROM = SynchedEntityData.defineId(SentinelRobot.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> DATA_SHOT_TO = SynchedEntityData.defineId(SentinelRobot.class, EntityDataSerializers.VECTOR3);

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHOT, (byte) 0);
		builder.define(DATA_SHOT_FROM, new Vector3f());
		builder.define(DATA_SHOT_TO, new Vector3f());
	}

	void syncShot(int state, SentinelBeamEntity.Kind kind, Vec3 from, Vec3 to) {
		entityData.set(DATA_SHOT, (byte) (state | kind.ordinal() << 4));
		if (state != SentinelAimedShot.STATE_OFF) {
			entityData.set(DATA_SHOT_FROM, from.toVector3f());
			entityData.set(DATA_SHOT_TO, to.toVector3f());
		}
	}

	/** The telegraph / beam state for the renderer (0 = none). */
	public int shotState() {
		return entityData.get(DATA_SHOT) & 0x0F;
	}

	public SentinelBeamEntity.Kind shotKind() {
		int k = entityData.get(DATA_SHOT) >> 4 & 0x0F;
		return k < SentinelBeamEntity.Kind.values().length ? SentinelBeamEntity.Kind.values()[k] : SentinelBeamEntity.Kind.BEAM;
	}

	public Vec3 shotFrom() {
		Vector3f v = entityData.get(DATA_SHOT_FROM);
		return new Vec3(v.x, v.y, v.z);
	}

	public Vec3 shotTo() {
		Vector3f v = entityData.get(DATA_SHOT_TO);
		return new Vec3(v.x, v.y, v.z);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof SentinelRobot || super.isAlliedTo(other);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof SentinelRobot && attacker != this) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.REGENERATION)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (purgeId != null) {
			tag.putUUID("SentinelPurge", purgeId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		purgeId = tag.hasUUID("SentinelPurge") ? tag.getUUID("SentinelPurge") : null;
	}
}
