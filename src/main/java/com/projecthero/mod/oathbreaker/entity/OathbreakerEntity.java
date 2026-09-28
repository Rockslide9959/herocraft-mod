package com.projecthero.mod.oathbreaker.entity;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
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
 * The Oathbreaker (v0.13.6): a 4-block-tall knight boss, summoned by using a Knight's Soul on a
 * lodestone (see {@code KnightsSoulItem}). No vanilla AI ever lands damage -- {@link #doHurtTarget}
 * always refuses -- every point of damage comes from the hand-timed windup/attack state machine
 * below, the same shape {@code AbyssalBehemothEntity} and {@code TitanEntity} already use for their
 * own telegraphed attacks.
 *
 * <p>Two attacks, both spec'd by name:
 * <ul>
 *   <li><b>Stance Dash</b>: a 2 s wind-up (sword drawn back, stance held), then a fast forward lunge
 *   with a slicing swing for 30 damage, then the stance is held for another 2 s before returning to
 *   normal.</li>
 *   <li><b>Four-Strike Combo</b>: four quick hits in a row, each its own short wind-up then a strike
 *   from a different direction (upper-right, upper-left, a horizontal sweep, an overhead slam), 10
 *   damage each.</li>
 * </ul>
 */
public class OathbreakerEntity extends Monster implements GeoEntity {
	/** The geo model is authored at vanilla-player proportions (2 blocks tall); the renderer stretches
	 * it to {@link #getBbHeight()} the same way {@code TitanFormRenderer}/{@code BehemothRenderer} do. */
	public static final float MODEL_HEIGHT = 2.0f;
	public static final float HEIGHT = 4.0f;
	public static final float WIDTH = 1.2f;

	private static final float MAX_HEALTH = 500.0f;
	/** v0.13.6: "10% faster than normal player walking speed" -- vanilla player walk speed is 0.1. */
	private static final double MOVEMENT_SPEED = 0.11;

	private static final int ATTACK_TRIGGER_RANGE = 5;
	private static final int ATTACK_COOLDOWN_TICKS = 50; // 2.5s between attack sequences

	private static final int STANCE_WINDUP_TICKS = 40; // 2s
	private static final int STANCE_DASH_TICKS = 6;
	private static final int STANCE_POST_TICKS = 40; // 2s
	private static final float STANCE_DAMAGE = 30.0f;
	private static final double STANCE_RANGE = 5.0;
	private static final double STANCE_ARC_DEGREES = 70.0;

	private static final int COMBO_HITS = 4;
	private static final int COMBO_WINDUP_TICKS = 8;
	private static final int COMBO_STRIKE_HOLD_TICKS = 4;
	private static final float COMBO_DAMAGE_PER_HIT = 10.0f;
	private static final double COMBO_RANGE = 3.5;
	private static final double COMBO_ARC_DEGREES = 80.0;

	private enum Attack { STANCE_DASH, COMBO }
	private enum StancePhase { WINDUP, DASH, POST }

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private Attack activeAttack;
	private StancePhase stancePhase;
	private int attackTicks;
	private int comboHitIndex;
	private int attackCooldownTicks;

	private EventBossBar bossBar;

	public OathbreakerEntity(EntityType<? extends OathbreakerEntity> type, Level level) {
		super(type, level);
		this.xpReward = 250;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		// Handles chasing/closing distance only -- its own "attack" call always no-ops below, the real
		// damage comes from #tickCombat.
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return false; // every hit comes from the timed state machine in #tickCombat
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(WIDTH, HEIGHT);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		tickCombat(server);
		updateBossBar(server);
	}

	// ---------------------------------------------------------------- combat state machine

	private void tickCombat(ServerLevel server) {
		if (activeAttack != null) {
			progressAttack(server);
			return;
		}
		if (attackCooldownTicks > 0) {
			attackCooldownTicks--;
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		if (distanceToSqr(target) > (double) (ATTACK_TRIGGER_RANGE * ATTACK_TRIGGER_RANGE)) {
			return;
		}
		beginAttack(server, pickAttack());
	}

	private Attack pickAttack() {
		// Combo is the bread-and-butter move; Stance Dash is the bigger, rarer punish.
		return random.nextFloat() < 0.4f ? Attack.STANCE_DASH : Attack.COMBO;
	}

	private void beginAttack(ServerLevel server, Attack attack) {
		activeAttack = attack;
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		if (getTarget() != null) {
			getLookControl().setLookAt(getTarget(), 30.0f, 30.0f);
		}
		if (attack == Attack.STANCE_DASH) {
			stancePhase = StancePhase.WINDUP;
			attackTicks = STANCE_WINDUP_TICKS;
			triggerAnim("action", "windup_dash");
			server.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.2f, 0.6f);
		} else {
			comboHitIndex = 0;
			attackTicks = COMBO_WINDUP_TICKS;
			triggerAnim("action", "combo_windup_1");
		}
	}

	private void progressAttack(ServerLevel server) {
		if (getTarget() != null) {
			getLookControl().setLookAt(getTarget(), 30.0f, 30.0f);
		}
		attackTicks--;
		if (attackTicks > 0) {
			return;
		}
		if (activeAttack == Attack.STANCE_DASH) {
			progressStanceDash(server);
		} else {
			progressCombo(server);
		}
	}

	private void progressStanceDash(ServerLevel server) {
		switch (stancePhase) {
			case WINDUP -> {
				dashSlice(server);
				stancePhase = StancePhase.DASH;
				attackTicks = STANCE_DASH_TICKS;
				triggerAnim("action", "dash_attack");
			}
			case DASH -> {
				stancePhase = StancePhase.POST;
				attackTicks = STANCE_POST_TICKS;
				triggerAnim("action", "post_dash");
			}
			case POST -> endAttack();
		}
	}

	private void dashSlice(ServerLevel server) {
		Vec3 look = getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		forward = forward.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, getYRot()) : forward.normalize();
		// A real (if short) lunge, purely cosmetic -- the damage sweep below is what actually resolves.
		setDeltaMovement(forward.x * 1.1, getDeltaMovement().y, forward.z * 1.1);
		hasImpulse = true;
		boolean hitAnything = false;
		for (LivingEntity victim : arcTargets(server, forward, STANCE_RANGE, STANCE_ARC_DEGREES)) {
			hitAnything |= strike(victim, STANCE_DAMAGE, forward, 0.9);
		}
		server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
		if (hitAnything) {
			server.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 1.3f);
		}
	}

	private void progressCombo(ServerLevel server) {
		if (comboHitIndex < COMBO_HITS && attackTicksInStrikePhase()) {
			// windup for hit N just finished -- resolve the strike now, then hold briefly before the next
			int n = comboHitIndex + 1;
			Vec3 look = getLookAngle();
			Vec3 forward = new Vec3(look.x, 0.0, look.z);
			forward = forward.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, getYRot()) : forward.normalize();
			boolean hitAnything = false;
			for (LivingEntity victim : arcTargets(server, forward, COMBO_RANGE, COMBO_ARC_DEGREES)) {
				hitAnything |= strike(victim, COMBO_DAMAGE_PER_HIT, forward, 0.6);
			}
			triggerAnim("action", "combo_strike_" + n);
			server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.6f, 1.0f + n * 0.05f);
			if (hitAnything) {
				server.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 1.0f);
			}
			comboHitIndex = n;
			attackTicks = COMBO_STRIKE_HOLD_TICKS;
			if (comboHitIndex >= COMBO_HITS) {
				endAttack();
			}
			return;
		}
		if (comboHitIndex >= COMBO_HITS) {
			endAttack();
			return;
		}
		// Hold elapsed -- start the next hit's wind-up.
		int next = comboHitIndex + 1;
		attackTicks = COMBO_WINDUP_TICKS;
		strikePhasePending = true;
		triggerAnim("action", "combo_windup_" + next);
	}

	/** Tracks whether the current combo sub-timer is counting down a wind-up (about to strike) or a
	 * post-strike hold (about to start the next wind-up) -- {@link #progressCombo} alternates the two. */
	private boolean strikePhasePending = true;

	private boolean attackTicksInStrikePhase() {
		boolean pending = strikePhasePending;
		strikePhasePending = false;
		return pending;
	}

	private void endAttack() {
		activeAttack = null;
		stancePhase = null;
		comboHitIndex = 0;
		strikePhasePending = true;
		attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
	}

	/** Every living, hostile-eligible target within {@code range} of the mouth of a forward-facing cone
	 * ({@code arcDegrees} full width), never the Oathbreaker itself. One AABB-bounded query, never an
	 * unbounded scan. */
	private java.util.List<LivingEntity> arcTargets(ServerLevel server, Vec3 forward, double range, double arcDegrees) {
		double cos = Math.cos(Math.toRadians(arcDegrees / 2.0));
		Vec3 origin = position().add(0, getBbHeight() * 0.5, 0);
		AABB box = getBoundingBox().inflate(range);
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != this && e.isAlive() && e.isPickable() && (!(e instanceof Player p) || !p.isSpectator()))) {
			Vec3 to = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(origin);
			double dist = to.length();
			if (dist < 1.0e-3 || dist > range) {
				continue;
			}
			if (to.scale(1.0 / dist).dot(forward) >= cos) {
				out.add(candidate);
			}
		}
		return out;
	}

	private boolean strike(LivingEntity target, float damage, Vec3 forward, double knockback) {
		if (TitanCombat.isBoss(target)) {
			damage = Math.min(damage, (float) (target.getMaxHealth() * 0.10));
		}
		DamageSource source = damageSources().mobAttack(this);
		if (!target.hurt(source, Math.max(1.0f, damage))) {
			return false;
		}
		target.knockback(knockback, -forward.x, -forward.z);
		return true;
	}

	// ---------------------------------------------------------------- incoming damage

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		super.actuallyHurt(source, amount);
		if (random.nextInt(3) == 0) {
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
		bossBar().update(server, blockPosition(), 48.0,
				Component.translatable("entity.projecthero.oathbreaker").withStyle(net.minecraft.ChatFormatting.DARK_RED),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- death

	private int deathTicks = -1;

	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		setTarget(null);
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		deathTicks = 20;
		triggerAnim("action", "death");
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 3.0f, 0.6f);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5, getZ(), 40,
					getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.08);
			bossBar().clear(server);
			dropLoot(server, source);
		}
	}

	private void dropLoot(ServerLevel server, DamageSource source) {
		var lootTable = server.getServer().reloadableRegistries().getLootTable(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						com.projecthero.mod.ProjectHeroMod.id("entities/oathbreaker")));
		LootParams.Builder params = new LootParams.Builder(server)
				.withParameter(LootContextParams.THIS_ENTITY, this)
				.withParameter(LootContextParams.ORIGIN, position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity());
		for (net.minecraft.world.item.ItemStack stack : lootTable.getRandomItems(params.create(LootContextParamSets.ENTITY))) {
			spawnAtLocation(stack);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (deathTicks > 0) {
			deathTicks--;
		} else if (deathTicks == 0) {
			deathTicks = -1;
			remove(Entity.RemovalReason.KILLED);
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<OathbreakerEntity> action = new AnimationController<>(this, "action", 4, state -> PlayState.STOP);
		for (String name : new String[] { "windup_dash", "dash_attack", "post_dash",
				"combo_windup_1", "combo_strike_1", "combo_windup_2", "combo_strike_2",
				"combo_windup_3", "combo_strike_3", "combo_windup_4", "combo_strike_4", "hit", "death" }) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.oathbreaker." + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 6, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<OathbreakerEntity> state) {
		if (isDeadOrDying() || deathTicks >= 0) {
			return PlayState.STOP;
		}
		if (activeAttack != null) {
			return PlayState.STOP; // the "action" controller owns the whole attack sequence
		}
		if (state.getLimbSwingAmount() > 0.04f) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.oathbreaker.walk"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.oathbreaker.idle"));
	}
}
