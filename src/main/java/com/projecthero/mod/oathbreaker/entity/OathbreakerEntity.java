package com.projecthero.mod.oathbreaker.entity;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.oathbreaker.OathbreakerTuning;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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
 * The Oathbreaker: a 4-block-tall fallen death knight, summoned by using a Knight's Soul on a respawn
 * anchor (see {@code KnightsSoulItem}/{@code OathbreakerSummon}). v0.14.0 rework: a three-phase duel boss
 * with a hidden poise meter.
 *
 * <p>This class owns the lifecycle (spawn, death, loot, boss bar), the {@link Phase} and its sync, poise
 * and stagger, and the GeckoLib controllers. Every attack lives in {@link OathbreakerCombat}. No vanilla
 * AI ever lands damage -- {@link #doHurtTarget} always refuses; the goals only move him between attacks.
 * Every tunable number is in {@link OathbreakerTuning}; see {@code docs/OATHBREAKER_REFERENCE.md}.
 */
public class OathbreakerEntity extends Monster implements GeoEntity {
	/** The geo model is authored at vanilla-player proportions (2 blocks tall); the renderer stretches
	 * it to {@link #getBbHeight()} the same way {@code TitanFormRenderer}/{@code BehemothRenderer} do. */
	public static final float MODEL_HEIGHT = 2.0f;
	public static final float HEIGHT = 4.0f;
	public static final float WIDTH = 1.2f;

	/** The three combat phases. Synced so {@code OathbreakerRenderer} can pick the right texture/glowmask;
	 * never regresses once advanced. */
	public enum Phase { KNIGHT, FORSWORN, OATHLESS }

	private static final EntityDataAccessor<Byte> DATA_PHASE =
			SynchedEntityData.defineId(OathbreakerEntity.class, EntityDataSerializers.BYTE);
	/** True while a triggered clip (attack, stagger, spawn, flinch, death) owns the pose -- see {@link #syncBusy}. */
	private static final EntityDataAccessor<Boolean> DATA_BUSY =
			SynchedEntityData.defineId(OathbreakerEntity.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final OathbreakerCombat combat = new OathbreakerCombat(this);

	/** Hidden poise meter (server only) -- see {@link #drainPoise}. */
	private float poise = OathbreakerTuning.POISE_MAX;
	private int ticksSinceDamage;
	private int staggerTicksLeft;
	private int flinchTicksLeft;

	/** Ticks left in the crouched spawn pose; &gt;0 means AI/targeting/combat are all still asleep and the
	 * entity is invulnerable -- see {@link #spawnIn} and {@link #tickSpawn}. */
	private int spawnTicksLeft;
	private int deathTicks = -1;

	private EventBossBar bossBar;

	public OathbreakerEntity(EntityType<? extends OathbreakerEntity> type, Level level) {
		super(type, level);
		this.xpReward = OathbreakerTuning.XP_REWARD;
	}

	/** Called the instant this entity is added to the world by {@code OathbreakerSummon}: starts it
	 * crouched and inert for {@link OathbreakerTuning#SPAWN_TICKS} before AI wakes up, so the summon's
	 * buildup (rumble, zoom, particles, a 5 s wait) is followed by an actual crouch-to-standing rise. */
	public void spawnIn() {
		spawnTicksLeft = OathbreakerTuning.SPAWN_TICKS;
		setNoAi(true);
		setInvulnerable(true);
		triggerAnim("action", "spawn");
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, OathbreakerTuning.MAX_HEALTH_BASE)
				.add(Attributes.MOVEMENT_SPEED, OathbreakerTuning.MOVEMENT_SPEED_PHASE1)
				.add(Attributes.FOLLOW_RANGE, OathbreakerTuning.FOLLOW_RANGE)
				.add(Attributes.ARMOR, OathbreakerTuning.ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, OathbreakerTuning.ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, OathbreakerTuning.KNOCKBACK_RESISTANCE_BASE)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, (byte) Phase.KNIGHT.ordinal());
		builder.define(DATA_BUSY, false);
	}

	// ---------------------------------------------------------------- phases

	/** v0.13.17: every hit he lands is scaled by his phase (see {@link OathbreakerTuning#PHASE_2_DAMAGE_MULTIPLIER}). */
	public float phaseDamageMultiplier() {
		return switch (getPhase()) {
			case KNIGHT -> 1.0f;
			case FORSWORN -> OathbreakerTuning.PHASE_2_DAMAGE_MULTIPLIER;
			case OATHLESS -> OathbreakerTuning.PHASE_3_DAMAGE_MULTIPLIER;
		};
	}

	public Phase getPhase() {
		return Phase.values()[this.entityData.get(DATA_PHASE)];
	}

	private static final String TAG_PHASE = "OathbreakerPhase";

	/** The phase is saved: without it a boss reloaded in phase 2/3 came back as a phase-1 knight and
	 * replayed the whole Oath Shattered sequence. (Attack/stagger state still isn't saved -- a reload
	 * mid-attack simply resumes at idle.) */
	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte(TAG_PHASE, (byte) getPhase().ordinal());
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Spawn, stagger and the phase transitions all run on vanilla flags (NoAI, Invulnerable, NoGravity
		// for scripted moves, a raised knockback-resistance base) that vanilla SAVES -- but their timers
		// aren't saved, so a world saved mid-stagger would reload a permanently frozen, invulnerable boss.
		// None of those states ever legitimately survives a reload: start clean.
		setNoAi(false);
		setInvulnerable(false);
		setNoGravity(false);
		setHyperArmor(false);
		// v0.13.19: attribute BASE values are saved too, so a boss already in a world would keep the old 48-block
		// follow range forever. Always the current tuning value.
		var follow = getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(OathbreakerTuning.FOLLOW_RANGE);
		}
		if (tag.contains(TAG_PHASE)) {
			int ordinal = tag.getByte(TAG_PHASE);
			if (ordinal > 0 && ordinal < Phase.values().length) {
				advancePhase(Phase.values()[ordinal]);
			}
		}
	}

	/**
	 * Checked every combat tick against the current health fraction; never regresses. Crossing 60% starts
	 * the scripted "Oath Shattered" sequence (the phase itself only flips at its sword-plunge beat, which is
	 * also when the texture swaps). A single huge hit that skips straight past 25% still plays phase 2's
	 * transition first; the phase-3 check picks up on the next tick after it ends.
	 */
	private void checkPhaseTransition(ServerLevel server) {
		if (transitionTicksLeft > 0) {
			return;
		}
		Phase current = getPhase();
		float healthFraction = getHealth() / getMaxHealth();
		if (current == Phase.KNIGHT && healthFraction <= OathbreakerTuning.PHASE_2_HEALTH_FRACTION) {
			beginOathShattered(server);
		} else if (current == Phase.FORSWORN && healthFraction <= OathbreakerTuning.PHASE_3_HEALTH_FRACTION) {
			beginEnrage(server);
		}
	}

	// ---------------------------------------------------------------- Enrage (phase 2 -> 3)

	/** Which scripted transition is running (both share the transition timer). */
	private boolean enraging;

	/** A 1.5s roaring stance -- per spec NOT invulnerable (he can be hit throughout), but no AI and no
	 * stagger. The brighter phase-3 texture swaps in at the roar's peak. */
	private void beginEnrage(ServerLevel server) {
		combat.cancel();
		staggerTicksLeft = 0;
		enraging = true;
		transitionTicksLeft = OathbreakerTuning.ENRAGE_TICKS;
		getNavigation().stop();
		setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
		setNoAi(true);
		setHyperArmor(true);
		triggerAnim("action", "enrage");
		server.playSound(null, blockPosition(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 2.0f, 0.6f);
		syncBusy();
	}

	private void tickEnrage(ServerLevel server) {
		int elapsed = OathbreakerTuning.ENRAGE_TICKS - transitionTicksLeft + 1;
		transitionTicksLeft--;
		if (elapsed == OathbreakerTuning.ENRAGE_ROAR_TICKS) {
			advancePhase(Phase.OATHLESS);
			Vec3 c = position();
			com.projecthero.mod.oathbreaker.OathbreakerFx.shake(server, c, OathbreakerTuning.ENRAGE_SHAKE_INTENSITY,
					OathbreakerTuning.ENRAGE_SHAKE_TICKS);
			server.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
			server.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.5f);
			server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.6, getZ(), 70,
					getBbWidth() * 0.6, getBbHeight() * 0.35, getBbWidth() * 0.6, 0.12);
			com.projecthero.mod.oathbreaker.OathbreakerFx.ring(server, ParticleTypes.SOUL, c.add(0, 0.3, 0), 3.0, 24, 0.08);
		}
		if (transitionTicksLeft <= 0) {
			transitionTicksLeft = 0;
			enraging = false;
			setNoAi(false);
			setHyperArmor(false);
		}
	}

	// ---------------------------------------------------------------- Execution: holding a victim

	/** Only the Execution's grabbed victim may ride him; nothing else can mount a boss. */
	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return combat.isExecutionVictim(passenger) && getPassengers().isEmpty();
	}

	/** The grabbed victim is held up in front of him at chest height, not seated on his head. */
	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
		if (!hasPassenger(passenger)) {
			return;
		}
		Vec3 f = combat.forward();
		Vec3 p = position().add(f.scale(OathbreakerTuning.EXECUTION_HOLD_FORWARD))
				.add(0, getBbHeight() * OathbreakerTuning.EXECUTION_HOLD_HEIGHT_FRACTION, 0);
		callback.accept(passenger, p.x, p.y, p.z);
	}

	/**
	 * Always false. Vanilla treats a mob with exactly one player riding it like a horse: when that player
	 * logs out (or the world saves) the mob is written INTO the player's save file and removed from the
	 * world. A player logging out mid-Execution would have carried the boss off with them. Never a mount.
	 */
	@Override
	public boolean hasExactlyOnePlayerPassenger() {
		return false;
	}

	/** Disconnect hook (see {@code ProjectHeroMod}): a player logging out mid-grab is released first. */
	public static void releaseIfHeld(net.minecraft.world.entity.player.Player player) {
		if (player.getVehicle() instanceof OathbreakerEntity boss) {
			boss.combat.releaseVictim();
		}
	}

	/** The Execution escape rule (and anything else that must break him outright). */
	void breakPoise(ServerLevel server) {
		if (canBeStaggered()) {
			beginStagger(server);
		}
	}

	// ---------------------------------------------------------------- "Oath Shattered" (phase 1 -> 2)

	/** Ticks left in a scripted phase transition; &gt;0 means invulnerable, no AI, no stagger. */
	private int transitionTicksLeft;
	/** Ticks since the plunge (the shockwave's expanding front), or -1 when no shockwave is running. */
	private int shockwaveTick = -1;
	private final java.util.Set<Integer> shockwaveHit = new java.util.HashSet<>();

	private void beginOathShattered(ServerLevel server) {
		combat.cancel();
		staggerTicksLeft = 0;
		transitionTicksLeft = OathbreakerTuning.TRANSITION_TICKS;
		getNavigation().stop();
		setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
		setNoAi(true);
		setInvulnerable(true);
		setHyperArmor(true);
		triggerAnim("action", "phase_transition");
		server.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2.0f, 0.5f);
		server.playSound(null, blockPosition(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 1.4f, 0.5f);
		syncBusy();
	}

	private void tickTransition(ServerLevel server) {
		int elapsed = OathbreakerTuning.TRANSITION_TICKS - transitionTicksLeft + 1;
		transitionTicksLeft--;
		if (elapsed < OathbreakerTuning.TRANSITION_PLUNGE_TICKS && tickCount % 3 == 0) {
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(),
					4, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.03);
		}
		if (elapsed == OathbreakerTuning.TRANSITION_PLUNGE_TICKS) {
			// the sword goes into the ground: the oath breaks, the soul-fire comes through
			advancePhase(Phase.FORSWORN);
			shockwaveTick = 0;
			shockwaveHit.clear();
			Vec3 c = position();
			com.projecthero.mod.oathbreaker.OathbreakerFx.shake(server, c, OathbreakerTuning.TRANSITION_SHAKE_INTENSITY,
					OathbreakerTuning.TRANSITION_SHAKE_TICKS);
			com.projecthero.mod.oathbreaker.OathbreakerFx.zoom(server, c, OathbreakerTuning.TRANSITION_ZOOM,
					OathbreakerTuning.TRANSITION_ZOOM_TICKS);
			server.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
			server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
			server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.5f);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.3, getZ(), 60, 0.6, 0.2, 0.6, 0.15);
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 30, 0.5, 1.0, 0.5, 0.1);
		}
		if (shockwaveTick >= 0) {
			tickShockwave(server);
		}
		if (transitionTicksLeft <= 0) {
			transitionTicksLeft = 0;
			shockwaveTick = -1;
			setNoAi(false);
			setInvulnerable(false);
			setHyperArmor(false);
			poise = OathbreakerTuning.POISE_MAX;
			ticksSinceDamage = 0;
			server.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}

	/** The plunge shockwave: a soul-fire ring expanding out to {@link OathbreakerTuning#SHOCKWAVE_RADIUS},
	 * hitting each player once as its front passes them -- 8 damage and a big outward shove. */
	private void tickShockwave(ServerLevel server) {
		shockwaveTick++;
		double max = OathbreakerTuning.SHOCKWAVE_RADIUS;
		double r = max * Math.min(1.0, shockwaveTick / (double) OathbreakerTuning.SHOCKWAVE_EXPAND_TICKS);
		Vec3 c = position();
		com.projecthero.mod.oathbreaker.OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 0.15, 0), r,
				Math.max(8, (int) (r * 7)), 0.02);
		for (net.minecraft.world.entity.player.Player player : server.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
				getBoundingBox().inflate(max, 3.0, max), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			double dx = player.getX() - c.x;
			double dz = player.getZ() - c.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d <= r && shockwaveHit.add(player.getId())) {
				if (player.hurt(damageSources().mobAttack(this), OathbreakerTuning.SHOCKWAVE_DAMAGE * phaseDamageMultiplier())) {
					double len = Math.max(1.0e-3, d);
					player.knockback(OathbreakerTuning.SHOCKWAVE_KNOCKBACK, -dx / len, -dz / len);
					player.setDeltaMovement(player.getDeltaMovement().add(0, OathbreakerTuning.SHOCKWAVE_LIFT, 0));
					player.hurtMarked = true;
				}
			}
		}
		if (shockwaveTick >= OathbreakerTuning.SHOCKWAVE_EXPAND_TICKS) {
			shockwaveTick = -1;
		}
	}

	public boolean isTransitioning() {
		return transitionTicksLeft > 0;
	}

	private void advancePhase(Phase next) {
		this.entityData.set(DATA_PHASE, (byte) next.ordinal());
		double speed = switch (next) {
			case KNIGHT -> OathbreakerTuning.MOVEMENT_SPEED_PHASE1;
			case FORSWORN -> OathbreakerTuning.MOVEMENT_SPEED_PHASE2;
			case OATHLESS -> OathbreakerTuning.MOVEMENT_SPEED_PHASE3;
		};
		var speedAttribute = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speedAttribute != null) {
			speedAttribute.setBaseValue(speed);
		}
	}

	int attackCooldownTicksForPhase() {
		return switch (getPhase()) {
			case KNIGHT -> OathbreakerTuning.ATTACK_COOLDOWN_TICKS_PHASE1;
			case FORSWORN -> OathbreakerTuning.ATTACK_COOLDOWN_TICKS_PHASE2;
			case OATHLESS -> OathbreakerTuning.ATTACK_COOLDOWN_TICKS_PHASE3;
		};
	}

	/** Hyper armor: knockback resistance raised for the duration of a wind-up or active strike, so a
	 * player can never knock him out of his own swing. Poise is unaffected -- see {@link #drainPoise}. */
	void setHyperArmor(boolean active) {
		var resistance = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resistance != null) {
			resistance.setBaseValue(active ? OathbreakerTuning.KNOCKBACK_RESISTANCE_HYPER_ARMOR
					: OathbreakerTuning.KNOCKBACK_RESISTANCE_BASE);
		}
	}

	// ---------------------------------------------------------------- goals / movement

	/** Server-side "a scripted sequence owns him right now" -- the movement goals stand down. */
	private boolean isBusy() {
		return combat.isAttacking() || staggerTicksLeft > 0 || spawnTicksLeft > 0 || transitionTicksLeft > 0
				|| deathTicks >= 0;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		// Chasing/closing distance only (its own attack call always no-ops -- see doHurtTarget). v0.14.0:
		// stands down for the whole of every attack. Before, it kept re-pathing toward the target every few
		// ticks, so he crept forward mid-wind-up and a "dodged" strike could still follow you.
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return !isBusy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !isBusy() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0) {
			@Override
			public boolean canUse() {
				return !isBusy() && super.canUse();
			}
		});
		// v0.13.19: no HurtByTargetGoal any more. It only retargets when it STARTS, so while it was running with
		// one player as the target nobody else's hits registered ("my friend lures him one way and I can spam hit
		// him and he never turns round"). Retaliation and switching are the threat table's job now
		// (OathbreakerCombat#noteDamageTaken / OathbreakerThreat). This goal only picks someone up when he has
		// nobody: the nearest player within FOLLOW_RANGE (50), line of sight NOT required, and -- mustSee false --
		// never dropped for stepping out of view, only for leaving the follow range or becoming invalid.
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false) {
			{
				this.targetConditions = TargetingConditions.forCombat().range(OathbreakerTuning.FOLLOW_RANGE).ignoreLineOfSight()
						.selector(OathbreakerCombat::isValidTarget);
			}

			@Override
			public boolean canUse() {
				LivingEntity current = getTarget();
				// the threat system may have set a target directly: don't overwrite it with "nearest"
				return (current == null || !current.isAlive()) && super.canUse();
			}
		});
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return false; // every hit comes from OathbreakerCombat
	}

	/** Leaps and slams are scripted flights -- he never takes fall damage from his own moves. */
	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(WIDTH, HEIGHT);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		syncBusy();
		if (spawnTicksLeft > 0) {
			tickSpawn(server);
			return;
		}
		if (deathTicks >= 0) {
			return; // dying: no combat, no stagger, no phase changes
		}
		if (flinchTicksLeft > 0) {
			flinchTicksLeft--;
		}
		combat.tickHazards(server);
		if (transitionTicksLeft > 0) {
			if (enraging) {
				tickEnrage(server);
			} else {
				tickTransition(server);
			}
			updateBossBar(server);
			return;
		}
		if (staggerTicksLeft > 0) {
			tickStagger(server);
			updateBossBar(server);
			return;
		}
		tickPoiseRegen();
		checkPhaseTransition(server);
		if (transitionTicksLeft > 0) {
			updateBossBar(server);
			return;
		}
		combat.tick(server);
		updateBossBar(server);
	}

	/**
	 * v0.14.0: tells the client a scripted clip currently owns the pose, so {@link #mainPredicate} stops
	 * the looping idle/walk controller. Without it the client (which never sees the server-only attack
	 * state) kept playing idle/walk underneath every triggered clip -- and since both controllers key the
	 * same bones, walk/idle won: the v0.13.7 Stance Dash wind-up screenshot shows the arm hanging at the
	 * hip instead of drawn back overhead. Almost certainly the real reason the v0.13.7 attacks "didn't
	 * really work".
	 */
	private void syncBusy() {
		boolean busy = isBusy() || flinchTicksLeft > 0;
		if (this.entityData.get(DATA_BUSY) != busy) {
			this.entityData.set(DATA_BUSY, busy);
		}
	}

	private void tickSpawn(ServerLevel server) {
		spawnTicksLeft--;
		if (tickCount % 4 == 0) {
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.3, getZ(),
					6, getBbWidth() * 0.4, getBbHeight() * 0.2, getBbWidth() * 0.4, 0.02);
		}
		if (spawnTicksLeft <= 0) {
			setNoAi(false);
			setInvulnerable(false);
			server.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2.0f, 0.5f);
			server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() * 0.4, getZ(), 1, 0, 0, 0, 0);
			updateBossBar(server);
		}
	}

	// ---------------------------------------------------------------- poise / stagger

	/** Can a poise break happen right now? Never mid-spawn, mid-stagger, mid-transition or while dying. */
	private boolean canBeStaggered() {
		return spawnTicksLeft <= 0 && staggerTicksLeft <= 0 && transitionTicksLeft <= 0 && deathTicks < 0
				&& !isDeadOrDying();
	}

	private void tickPoiseRegen() {
		if (poise < OathbreakerTuning.POISE_MAX && ++ticksSinceDamage >= OathbreakerTuning.POISE_REGEN_DELAY_TICKS) {
			poise = OathbreakerTuning.POISE_MAX;
		}
	}

	/** Drains poise one-for-one with the raw (pre-armor) damage. Hyper armor does not protect poise --
	 * hitting him mid-wind-up is exactly how you break him. */
	private void drainPoise(ServerLevel server, float rawAmount) {
		ticksSinceDamage = 0;
		if (rawAmount <= 0.0f || !canBeStaggered()) {
			return;
		}
		poise -= rawAmount;
		if (poise <= 0.0f) {
			beginStagger(server);
		}
	}

	private void beginStagger(ServerLevel server) {
		combat.cancel();
		setHyperArmor(false);
		staggerTicksLeft = OathbreakerTuning.STAGGER_TICKS;
		poise = 0.0f;
		getNavigation().stop();
		setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
		setNoAi(true);
		triggerAnim("action", "stagger");
		server.playSound(null, blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.0f, 0.55f);
		server.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.6f, 0.6f);
		server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(),
				30, getBbWidth() * 0.5, getBbHeight() * 0.25, getBbWidth() * 0.5, 0.35);
		server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(),
				16, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
		syncBusy();
	}

	private void tickStagger(ServerLevel server) {
		staggerTicksLeft--;
		if (tickCount % 5 == 0) {
			// his soul leaking out while he's down -- a visual "hit him now" cue
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.45, getZ(),
					3, getBbWidth() * 0.3, getBbHeight() * 0.15, getBbWidth() * 0.3, 0.02);
		}
		if (staggerTicksLeft <= 0) {
			staggerTicksLeft = 0;
			poise = OathbreakerTuning.POISE_MAX;
			ticksSinceDamage = 0;
			setNoAi(false);
			server.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.6f, 0.6f);
		}
	}

	/** Test/debug visibility into the hidden meter. */
	public float getPoise() {
		return poise;
	}

	// ---------------------------------------------------------------- test hooks (GameTests live in another package)

	/** Starts the named {@code OathbreakerCombat.Attack} right now, bypassing selection. Server only. */
	public void debugBeginAttack(String attackName) {
		if (level() instanceof ServerLevel server) {
			combat.debugBegin(server, OathbreakerCombat.Attack.valueOf(attackName));
		}
	}

	/** The running attack's name, or null when idle. */
	public String debugActiveAttack() {
		OathbreakerCombat.Attack a = combat.activeAttack();
		return a == null ? null : a.name();
	}

	public int debugAttackStep() {
		return combat.activeStep();
	}

	/** Cancels any attack and keeps him from starting another for {@code ticks}. */
	public void debugHoldAttacks(int ticks) {
		combat.holdAttacks(ticks);
	}

	public OathbreakerThreat debugThreat() {
		return combat.threat();
	}

	public boolean isStaggered() {
		return staggerTicksLeft > 0;
	}

	// ---------------------------------------------------------------- incoming damage

	/**
	 * The entry point for parry and poise. {@code actuallyHurt} only ever sees the armor-reduced amount,
	 * but poise drains on the raw incoming damage ("before armor"), so it's captured here before vanilla
	 * applies armor/toughness. Also where the +30% stagger vulnerability is applied, and where the Oath
	 * Guard gets first refusal on a hit. Mirrors vanilla's own invulnerability-frame rule: a hit inside
	 * the i-frame window only counts the amount by which it beats the previous hit, for poise as for health.
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!(level() instanceof ServerLevel server)) {
			return super.hurt(source, amount);
		}
		if (deathTicks < 0 && spawnTicksLeft <= 0) {
			if (combat.tryParry(server, source)) {
				return false;
			}
		}
		float dealt = staggerTicksLeft > 0 ? amount * OathbreakerTuning.STAGGER_DAMAGE_MULTIPLIER : amount;
		boolean inIframes = invulnerableTime > 10;
		float lastHurtBefore = lastHurt;
		boolean landed = super.hurt(source, dealt);
		if (landed) {
			float raw = inIframes ? amount - lastHurtBefore : amount;
			combat.noteDamageTaken(server, source, raw);
			drainPoise(server, raw);
		}
		return landed;
	}

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		super.actuallyHurt(source, amount);
		// Flinch only when idle -- never mid-attack (hyper armor) and never over the stagger clip.
		if (!combat.isAttacking() && staggerTicksLeft <= 0 && spawnTicksLeft <= 0
				&& random.nextInt(OathbreakerTuning.FLINCH_ONE_IN) == 0) {
			flinchTicksLeft = OathbreakerTuning.FLINCH_TICKS;
			triggerAnim("action", "hit");
		}
		if (level() instanceof ServerLevel server) {
			updateBossBar(server);
		}
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	// ---------------------------------------------------------------- boss bar

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false);
		}
		return bossBar;
	}

	private void updateBossBar(ServerLevel server) {
		if (isRemoved() || isDeadOrDying()) {
			return;
		}
		String nameKey = switch (getPhase()) {
			case KNIGHT -> "entity.projecthero.oathbreaker";
			case FORSWORN -> "boss.projecthero.oathbreaker.forsworn";
			case OATHLESS -> "boss.projecthero.oathbreaker.oathless";
		};
		bossBar().update(server, blockPosition(), OathbreakerTuning.BOSS_BAR_RADIUS,
				Component.translatable(nameKey).withStyle(net.minecraft.ChatFormatting.DARK_RED),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- death

	/**
	 * v0.14.0: calls {@code super.die} now. The old override skipped it entirely and rolled the loot table by
	 * hand, so the boss never set vanilla's dead flag, never credited the kill (advancements, kill score) and
	 * never dropped its XP reward at all. Vanilla's death path rolls this entity type's default loot table,
	 * which is the same {@code projecthero:entities/oathbreaker}, so the manual roll is gone too.
	 */
	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		combat.cancel(); // also lets go of any Execution victim
		staggerTicksLeft = 0;
		transitionTicksLeft = 0;
		deathTicks = 0; // "dying" -- the timing itself is vanilla's deathTime, see #tickDeath
		super.die(source);
		setTarget(null);
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		triggerAnim("action", "death");
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 3.0f, 0.5f);
			server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
			bossBar().clear(server);
			Component line = Component.translatable("message.projecthero.oathbreaker.fulfilled")
					.withStyle(net.minecraft.ChatFormatting.DARK_AQUA, net.minecraft.ChatFormatting.ITALIC);
			double r = OathbreakerTuning.DEATH_MESSAGE_RADIUS;
			for (net.minecraft.server.level.ServerPlayer player : server.players()) {
				if (player.distanceToSqr(this) <= r * r) {
					player.displayClientMessage(line, true);
				}
			}
		}
	}

	/**
	 * The 3s death: the {@code death} clip takes him down to both knees with the sword planted and his head
	 * bowed, and from {@link OathbreakerTuning#DEATH_FADE_START_TICKS} his soul streams out of him, thicker
	 * every tick, while the renderer fades the body to nothing. Replaces vanilla's 20-tick tip-over-and-poof
	 * (the renderer also zeroes the tip-over rotation). Runs on both sides; removal is server-only.
	 */
	@Override
	protected void tickDeath() {
		++this.deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (deathTime >= OathbreakerTuning.DEATH_FADE_START_TICKS && deathTime % 2 == 0) {
			float k = (deathTime - OathbreakerTuning.DEATH_FADE_START_TICKS)
					/ (float) (OathbreakerTuning.DEATH_TICKS - OathbreakerTuning.DEATH_FADE_START_TICKS);
			int n = 2 + (int) (k * 10);
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.4, getZ(), n,
					getBbWidth() * 0.45, getBbHeight() * 0.3, getBbWidth() * 0.45, 0.05);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.3, getZ(), Math.max(1, n / 2),
					getBbWidth() * 0.4, getBbHeight() * 0.25, getBbWidth() * 0.4, 0.02);
		}
		if (deathTime >= OathbreakerTuning.DEATH_TICKS && !isRemoved()) {
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 50,
					getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.12);
			server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.5f, 0.8f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	/** Every one-shot clip the server may trigger on the "action" controller. */
	private static final String[] ACTION_CLIPS = {
			"spawn", "hit", "death", "stagger", "phase_transition",
			"windup_dash", "dash_attack", "post_dash",
			"combo_windup_1", "combo_strike_1", "combo_windup_2", "combo_strike_2",
			"combo_windup_3", "combo_strike_3", "combo_windup_4", "combo_strike_4",
			"guard_stance", "riposte", "backstep",
			"leap_windup", "leap_air", "leap_land",
			"dash_feint", "combo_windup_5", "combo_strike_5",
			"soul_rend_windup", "soul_rend_strike",
			"chain_throw", "chain_pull", "chain_recover",
			"enrage", "judgement_rise", "judgement_hang", "judgement_slam",
			"execution_windup", "execution_lunge", "execution_hold", "execution_impale", "execution_whiff",
			"whirlwind_windup", "whirlwind_strike", "geyser_windup", "geyser_plunge", "geyser_bowed",
	};
	/** Triggered clips that loop until the next trigger replaces them (their length is decided in Java). */
	private static final String[] ACTION_LOOPS = {
			"chain_hold", "combo_hold_1", "combo_hold_2", "combo_hold_3", "combo_hold_4", "combo_hold_5",
	};

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Transition 1 tick for triggered attack clips (v0.13.7: at 4, the blend ate a third of every
		// short beat) and 2 for the looping idle/walk controller.
		AnimationController<OathbreakerEntity> action = new AnimationController<>(this, "action", 1, state -> PlayState.STOP);
		for (String name : ACTION_CLIPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.oathbreaker." + name));
		}
		for (String name : ACTION_LOOPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenLoop("animation.oathbreaker." + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 2, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<OathbreakerEntity> state) {
		if (isDeadOrDying() || deathTicks >= 0) {
			return PlayState.STOP;
		}
		if (this.entityData.get(DATA_BUSY)) {
			return PlayState.STOP; // a triggered clip on the "action" controller owns the pose -- see #syncBusy
		}
		// v0.13.9: the walk clip's stride is authored for his phase-1 speed; phase 2 walks ~25% faster, so the
		// clip plays 1.25x to keep his feet planted. Only ever changes with the phase -- which happens while
		// this controller is stopped for the transition clip -- so it never jumps a loop mid-stride.
		state.getController().setAnimationSpeed(getPhase() == Phase.FORSWORN ? 1.25 : 1.0);
		if (state.getLimbSwingAmount() > 0.04f) {
			// phase 3: a heavier forward-leaning run, sword dragging behind, instead of the measured walk
			return state.setAndContinue(RawAnimation.begin().thenLoop(getPhase() == Phase.OATHLESS
					? "animation.oathbreaker.run" : "animation.oathbreaker.walk"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.oathbreaker.idle"));
	}
}
