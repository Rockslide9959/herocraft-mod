package com.projecthero.mod.frostgiant.entity;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantAssault;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: what every creature of the Frost Giant Assault shares -- the Rime Hound, the Jotun Raider, the Frost Shaman,
 * the Ice Brute, the Warlord and King Laufey.
 * <ul>
 *   <li><b>The assault link</b>: a giant sent by a {@link FrostGiantAssault} remembers it and removes itself if the assault
 *       no longer exists (the orphan guard).</li>
 *   <li><b>Damage taken</b> ({@link FrostCombat#multiplier}): fire x1.5, lightning x1.25. Immune to freezing, slowness
 *       and fall damage, and never hurt by another of Jotunheim's own.</li>
 *   <li><b>Animation</b>: one synced int -- the playing {@link Clip}'s index in {@link FrostGiantAnims#ALL} (low 10 bits)
 *       and a bump counter above it, so the same move twice still restarts. The client's GeckoLib controller plays it
 *       (blending in for {@link FrostGiantAnims#TRANSITION} ticks), else idle / walk / run by speed. Strikes land on the
 *       contract ticks in {@link FrostGiantAnims}.</li>
 *   <li><b>Moves</b>: subclasses run one move at a time ({@link #startMove} / {@link #moveTick}), resolving it on its
 *       clip's hit tick.</li>
 *   <li><b>Arrival</b>: they climb down from the rift on a beam of frost light ({@link #beginArrival}) and land with a
 *       snow-burst and, for the big ones, a ground shake.</li>
 *   <li><b>Braziers</b>: giants that {@link #huntsBraziers()} go for the nearest lit Rune Brazier whenever no fighter is
 *       within 8 blocks of them.</li>
 *   <li><b>Rally</b> (the Warlord's war cry): +20% damage for 8 s, frost runes swirling round them.</li>
 *   <li><b>Rime Ward</b> (a Shaman's): encased in ice -- invulnerable, then healed -- unless the players break it.</li>
 * </ul>
 * They are Monsters, so the Iron Man / Mind Stone highlights pick them up through the client-side path; nothing here
 * ever sets a server glowing flag (the private-highlight rule).
 */
public abstract class FrostGiantEntity extends Monster implements GeoEntity {
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(FrostGiantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_RALLIED = SynchedEntityData.defineId(FrostGiantEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ARRIVING = SynchedEntityData.defineId(FrostGiantEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID assaultId;
	private int animSeq;
	private int animTicksLeft;
	private Clip serverClip;
	private int rallyTicks;
	private int wardTicks;
	private UUID wardPrison;
	private int deathTicks = -1;
	// arrival down the frost beam
	private Vec3 arriveFrom;
	private Vec3 arriveTo;
	private int arriveTick = -1;
	private int arriveTotal;
	// moves
	protected Clip move;
	protected int moveTick;
	protected LivingEntity moveTarget;
	protected int cooldown = 20;
	private int retargetTimer;
	private int breathTimer;

	// client
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private Clip clientClip;
	private int clientClipStart;
	private int mainClipStart = Integer.MIN_VALUE;
	private boolean clientRunning;
	/** Client: how much the head follows the look target (eased by the renderer, 0..1). */
	public float clientHeadWeight = 1.0f;

	protected FrostGiantEntity(EntityType<? extends FrostGiantEntity> type, Level level) {
		super(type, level);
		this.xpReward = 10;
		this.breathTimer = random.nextInt(60);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANIM, -1 & 0x3FF);
		builder.define(DATA_RALLIED, false);
		builder.define(DATA_ARRIVING, false);
	}

	/** Asset base name ({@link FrostGiantAnims#MODELS}). */
	public abstract String model();

	/** Raiders, Brutes and the Warlord go after the braziers; hounds, shamans and Laufey hunt the fighters. */
	protected boolean huntsBraziers() {
		return false;
	}

	/** A giant that ignores a fighter this close in favour of a lit brazier (the Brute's is shorter). */
	protected double brazierIgnoreRange() {
		return FrostGiantConfig.braziers().ignoreWhenFighterWithin;
	}

	/** One combat step: called every tick while the giant is free to act (not arriving, warded or dying). */
	protected abstract void tickCombat(ServerLevel level, LivingEntity target);

	// ---------------------------------------------------------------- the assault link

	public UUID assaultId() {
		return assaultId;
	}

	public void bindToAssault(UUID id) {
		this.assaultId = id;
	}

	public FrostGiantAssault assault() {
		return assaultId != null && level() instanceof ServerLevel server ? FrostGiantAssault.find(server, assaultId) : null;
	}

	// ---------------------------------------------------------------- animation

	/** Server: play {@code clip} (and hold it {@code clip.length()} ticks, then back to locomotion). */
	public void setAnim(Clip clip) {
		animSeq = (animSeq + 1) & 0x3FFFFF;
		int idx = FrostGiantAnims.indexOf(clip);
		entityData.set(DATA_ANIM, (idx < 0 ? 0x3FF : idx) | (animSeq << 10));
		serverClip = clip;
		animTicksLeft = clip == null || clip.holds() && clip.length() <= 0 ? 0 : clip.length();
	}

	/** Server: back to locomotion now. */
	public void clearAnim() {
		setAnim(null);
	}

	/** The clip synced right now (server or client view), or null for locomotion. */
	public Clip currentClip() {
		return FrostGiantAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
	}

	public Clip clientClip() {
		return clientClip;
	}

	public int clientClipStart() {
		return clientClipStart;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key) && level() != null && level().isClientSide()) {
			clientClip = FrostGiantAnims.byIndex(entityData.get(DATA_ANIM) & 0x3FF);
			clientClipStart = tickCount;
		}
	}

	protected abstract Clip idleClip();

	protected abstract Clip walkClip();

	protected abstract Clip runClip();

	protected abstract Clip deathClip();

	protected abstract Clip hurtClip();

	/** Horizontal speed (blocks/tick) above which the run cycle plays. */
	protected double runAbove() {
		return 0.2;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", FrostGiantAnims.TRANSITION, this::mainPredicate));
	}

	private static final java.util.Map<Clip, RawAnimation> RAW = new java.util.HashMap<>();

	public static RawAnimation raw(Clip clip) {
		synchronized (RAW) {
			return RAW.computeIfAbsent(clip, c -> c.loops() ? RawAnimation.begin().thenLoop(c.id())
					: RawAnimation.begin().thenPlayAndHold(c.id()));
		}
	}

	private PlayState mainPredicate(AnimationState<FrostGiantEntity> state) {
		AnimationController<FrostGiantEntity> controller = state.getController();
		Clip clip = isDeadOrDying() ? deathClip() : clientClip;
		if (clip != null) {
			int start = clip == clientClip ? clientClipStart : Integer.MIN_VALUE + 1;
			if (start != mainClipStart) {
				mainClipStart = start;
				controller.forceAnimationReset();
			}
			return state.setAndContinue(raw(clip));
		}
		mainClipStart = Integer.MIN_VALUE;
		double dx = getX() - xo;
		double dz = getZ() - zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		clientRunning = clientRunning ? speed > runAbove() * 0.85 : speed > runAbove();
		if (speed > 0.02) {
			return state.setAndContinue(raw(clientRunning ? runClip() : walkClip()));
		}
		return state.setAndContinue(raw(idleClip()));
	}

	// ---------------------------------------------------------------- moves

	/** Starts {@code clip} as the current move (synced animation, move clock reset). */
	protected void startMove(Clip clip, LivingEntity target) {
		move = clip;
		moveTick = 0;
		moveTarget = target;
		setAnim(clip);
		getNavigation().stop();
	}

	protected void endMove(int nextCooldown) {
		move = null;
		moveTick = 0;
		moveTarget = null;
		cooldown = nextCooldown;
	}

	public boolean busy() {
		return move != null;
	}

	/** Turns the body and head to face {@code at}. */
	protected void face(Vec3 at) {
		double dx = at.x - getX();
		double dz = at.z - getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	/** The gap between this giant's hit-box and {@code e}'s (0 when touching). */
	protected double gapTo(Entity e) {
		double dx = Math.max(0, Math.abs(e.getX() - getX()) - (e.getBbWidth() + getBbWidth()) / 2);
		double dz = Math.max(0, Math.abs(e.getZ() - getZ()) - (e.getBbWidth() + getBbWidth()) / 2);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Walk towards {@code target} (re-pathing every second; walking straight at it if no path). */
	protected void approach(LivingEntity target, double speedMod) {
		if (tickCount % 10 == 0 || getNavigation().isDone()) {
			boolean pathed = getNavigation().moveTo(target, speedMod);
			if (!pathed) {
				getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speedMod);
			}
		}
		getLookControl().setLookAt(target, 30f, 30f);
	}

	/** Back away from {@code from} (the Shaman keeping her distance). */
	protected void retreatFrom(LivingEntity from, double speedMod) {
		Vec3 away = position().subtract(from.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1.0e-4) {
			away = new Vec3(1, 0, 0);
		}
		Vec3 to = position().add(away.normalize().scale(6));
		if (tickCount % 10 == 0 || getNavigation().isDone()) {
			getNavigation().moveTo(to.x, to.y, to.z, speedMod);
		}
		getLookControl().setLookAt(from, 30f, 30f);
	}

	// ---------------------------------------------------------------- targeting

	/**
	 * Who to fight: the nearest fighter in range, unless this giant {@link #huntsBraziers()} and no fighter is within
	 * {@link #brazierIgnoreRange()} -- then the nearest lit Rune Brazier of its assault. Re-thought every half second.
	 */
	protected LivingEntity chooseTarget(ServerLevel level) {
		LivingEntity current = getTarget();
		if (current != null && !FrostCombat.canTarget(current)) {
			current = null;
			setTarget(null);
		}
		if (--retargetTimer > 0 && current != null) {
			return current;
		}
		retargetTimer = 10;
		Player fighter = FrostCombat.nearestPlayer(level, position(), 64);
		LivingEntity pick = fighter;
		if (huntsBraziers()) {
			double ignore = brazierIgnoreRange();
			if (fighter == null || fighter.distanceToSqr(this) > ignore * ignore) {
				RuneBrazierEntity brazier = nearestLitBrazier(level);
				if (brazier != null) {
					pick = brazier;
				}
			}
		}
		if (pick != current) {
			setTarget(pick);
		}
		return pick;
	}

	/** Test hook: re-think the target right now. */
	public LivingEntity rethinkTargetForTest(ServerLevel level) {
		retargetTimer = 0;
		return chooseTarget(level);
	}

	/** The nearest lit brazier of this giant's assault (or any lit brazier within 64 blocks for a stray giant). */
	public RuneBrazierEntity nearestLitBrazier(ServerLevel level) {
		FrostGiantAssault a = assault();
		List<RuneBrazierEntity> braziers = a != null ? a.liveBraziers(level)
				: level.getEntitiesOfClass(RuneBrazierEntity.class, getBoundingBox().inflate(64));
		RuneBrazierEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (RuneBrazierEntity b : braziers) {
			if (!b.isLit()) {
				continue;
			}
			double d = b.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = b;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void registerGoals() {
		// everything is the tick-driven combat below
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (isDeadOrDying()) {
			return;
		}
		if (tickCount % 40 == 0 && assaultId != null && FrostGiantAssault.find(server, assaultId) == null) {
			discard();
			return;
		}
		if (animTicksLeft > 0 && --animTicksLeft == 0 && serverClip != null && !serverClip.holds()) {
			clearAnim();
		}
		if (rallyTicks > 0) {
			if (--rallyTicks == 0) {
				entityData.set(DATA_RALLIED, false);
			} else if (tickCount % 5 == 0) {
				server.sendParticles(FrostFx.RUNE, getX(), getY() + getBbHeight() * 0.5, getZ(), 3, getBbWidth() * 0.6, getBbHeight() * 0.35,
						getBbWidth() * 0.6, 0.01);
			}
		}
		if (wardTicks > 0) {
			tickWard(server);
		}
		if (--breathTimer <= 0) {
			breathTimer = 40 + random.nextInt(40);
			Vec3 look = Vec3.directionFromRotation(0, yHeadRot);
			double eye = getEyeHeight() * 0.95;
			server.sendParticles(ParticleTypes.SNOWFLAKE, getX() + look.x * getBbWidth() * 0.6, getY() + eye, getZ() + look.z * getBbWidth() * 0.6,
					4, 0.1, 0.05, 0.1, 0.02);
			server.sendParticles(ParticleTypes.CLOUD, getX() + look.x * getBbWidth() * 0.6, getY() + eye, getZ() + look.z * getBbWidth() * 0.6,
					1, 0.05, 0.02, 0.05, 0.005);
		}
		if (getBbHeight() >= 7.0f && onGround() && tickCount % 20 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.004) {
			FrostFx.shake(server, position(), 0.18f, 8, 14.0);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4f, 0.5f);
		}
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isNoAi()) {
			return;
		}
		if (arriveTick >= 0) {
			tickArrival(server);
			return;
		}
		if (wardTicks > 0) {
			getNavigation().stop();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		LivingEntity target = move != null && moveTarget != null && moveTarget.isAlive() ? moveTarget : chooseTarget(server);
		if (move != null) {
			moveTick++;
		}
		tickCombat(server, target);
	}

	// ---------------------------------------------------------------- arrival

	/** Climb down the frost beam from {@code from} (the rift) to {@code to} over {@code ticks}. */
	public void beginArrival(Vec3 from, Vec3 to, int ticks) {
		arriveFrom = from;
		arriveTo = to;
		arriveTotal = Math.max(1, ticks);
		arriveTick = 0;
		entityData.set(DATA_ARRIVING, true);
		setNoGravity(true);
		noPhysics = true;
		moveTo(from.x, from.y, from.z, getYRot(), 0f);
	}

	public boolean isArriving() {
		return level().isClientSide() ? entityData.get(DATA_ARRIVING) : arriveTick >= 0;
	}

	private void tickArrival(ServerLevel server) {
		arriveTick++;
		float t = Math.min(1f, arriveTick / (float) arriveTotal);
		// ease out: fast from the rift, gentle at the bottom
		float e = 1f - (1f - t) * (1f - t);
		Vec3 at = arriveFrom.lerp(arriveTo, e);
		setPos(at.x, at.y, at.z);
		setDeltaMovement(Vec3.ZERO);
		if (arriveTick % 2 == 0) {
			FrostFx.line(server, FrostFx.WHITE, arriveFrom, at.add(0, getBbHeight() * 0.5, 0), 2.5, true);
			server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, getBbWidth() * 0.4, getBbHeight() * 0.4,
					getBbWidth() * 0.4, 0.01);
		}
		if (arriveTick >= arriveTotal) {
			land(server);
		}
	}

	private void land(ServerLevel server) {
		arriveTick = -1;
		entityData.set(DATA_ARRIVING, false);
		setNoGravity(false);
		noPhysics = false;
		if (arriveTo != null) {
			setPos(arriveTo.x, arriveTo.y, arriveTo.z);
		}
		double size = Math.max(0.8, getBbHeight() / 3.0);
		FrostFx.landing(server, position(), size);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, (float) Math.min(2.5, size), 1.4f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
		if (getBbHeight() >= 4.0f) {
			FrostFx.shake(server, position(), Math.min(0.9f, getBbHeight() / 14f), 14, 24.0);
		}
		onLanded(server);
	}

	/** Just touched down (war cries etc.). */
	protected void onLanded(ServerLevel server) {
	}

	// ---------------------------------------------------------------- rally and ward

	/** The Warlord's war cry: +damage for {@code ticks}. */
	public void rally(int ticks) {
		rallyTicks = Math.max(rallyTicks, ticks);
		entityData.set(DATA_RALLIED, true);
	}

	public boolean isRallied() {
		return level().isClientSide() ? entityData.get(DATA_RALLIED) : rallyTicks > 0;
	}

	/** A Shaman's Rime Ward: encased (invulnerable) for {@code ticks}, then healed; {@code prison} is the ice to break. */
	public void ward(int ticks, UUID prison) {
		wardTicks = ticks;
		wardPrison = prison;
		getNavigation().stop();
		move = null;
	}

	public boolean isWarded() {
		return wardTicks > 0;
	}

	/** The ward's ice was broken: the heal is cancelled. */
	public void breakWard() {
		wardTicks = 0;
		wardPrison = null;
	}

	private void tickWard(ServerLevel server) {
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		if (wardPrison != null && !(server.getEntity(wardPrison) instanceof IcePrisonEntity)) {
			breakWard(); // the ice was broken
			return;
		}
		if (--wardTicks == 0) {
			heal((float) (getMaxHealth() * FrostGiantConfig.shaman().wardHeal));
			if (wardPrison != null && server.getEntity(wardPrison) instanceof IcePrisonEntity prison) {
				prison.release(false);
			}
			wardPrison = null;
			server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 4, 0.4, 0.2, 0.4, 0.0);
			FrostFx.shatter(server, position().add(0, getBbHeight() * 0.5, 0), getBbHeight() * 0.4);
		}
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide()) {
			if (isWarded() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false;
			}
			if (isArriving() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false;
			}
			amount *= FrostCombat.multiplier(source) * damageTakenMultiplier(source);
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			if (FrostCombat.isFire(source) && amount > 2f) {
				server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.6, getZ(), 4, getBbWidth() * 0.3, 0.3, getBbWidth() * 0.3, 0.02);
			}
			if (amount >= 8f && move == null && hurtClip() != null && !isDeadOrDying()) {
				setAnim(hurtClip());
			}
			onHurt(server, source, amount);
		}
		return hurt;
	}

	/** Extra multiplier on top of the fire / lightning rules (the Warlord kneeling, Laufey drained). */
	protected float damageTakenMultiplier(DamageSource source) {
		return 1.0f;
	}

	/** After a hit landed (threat tables, staggers...). */
	protected void onHurt(ServerLevel server, DamageSource source, float amount) {
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker != null && attacker != this && FrostCombat.isFrost(attacker)) {
			return true;
		}
		if (source.getDirectEntity() instanceof Projectile proj && FrostCombat.isFrost(proj.getOwner())) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return FrostCombat.isFrost(other) || super.isAlliedTo(other);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !FrostCombat.isFrost(target) && super.canAttack(target);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.MOVEMENT_SLOWDOWN)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return assaultId == null && super.removeWhenFarAway(distanceSq);
	}

	// ---------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			setAnim(deathClip());
			deathTicks = deathClip() == null ? 20 : Math.max(20, deathClip().length());
			FrostGiantAssault a = assault();
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
		}
	}

	public int deathTicksLeft() {
		return deathTicks;
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server && !isRemoved()) {
			int total = deathClip() == null ? 20 : Math.max(20, deathClip().length());
			deathTicks = Math.max(0, total - deathTime);
			if (deathTime % 4 == 0) {
				server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.3, getZ(), 4, getBbWidth() * 0.5,
						getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
			}
			if (deathTime >= total) {
				FrostFx.shatter(server, position().add(0, getBbHeight() * 0.4, 0), Math.max(0.8, getBbHeight() * 0.35));
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	// ---------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.POLAR_BEAR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.POLAR_BEAR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.POLAR_BEAR_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.45f + random.nextFloat() * 0.1f;
	}

	/** A war cry (the shout, frost breath and a few snowflakes). */
	protected void warCrySound(ServerLevel server) {
		server.playSound(null, getX(), getY() + getBbHeight() * 0.8, getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.55f);
		server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.9, getZ(), 16, 0.6, 0.3, 0.6, 0.08);
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (assaultId != null) {
			tag.putUUID("FrostAssault", assaultId);
		}
		tag.putInt("FrostRally", rallyTicks);
		if (arriveTick >= 0 && arriveTo != null) {
			tag.putDouble("FrostArriveX", arriveTo.x);
			tag.putDouble("FrostArriveY", arriveTo.y);
			tag.putDouble("FrostArriveZ", arriveTo.z);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		assaultId = tag.hasUUID("FrostAssault") ? tag.getUUID("FrostAssault") : null;
		rallyTicks = tag.getInt("FrostRally");
		if (tag.contains("FrostArriveX")) {
			// reloaded mid-climb: finish it on the spot
			setPos(tag.getDouble("FrostArriveX"), tag.getDouble("FrostArriveY"), tag.getDouble("FrostArriveZ"));
			setNoGravity(false);
			noPhysics = false;
		}
	}
}
