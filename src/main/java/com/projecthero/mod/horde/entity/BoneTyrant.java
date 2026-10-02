package com.projecthero.mod.horde.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.horde.entity.skeleton.HordeSkeleton;
import com.projecthero.mod.horde.entity.skeleton.SkeletonHordeEntityTypes;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
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
 * v0.14.16: the Skeleton Horde's boss, rebuilt -- a seven-block skeletal lich-king with his own GeckoLib model
 * ({@code geo/bone_tyrant.geo.json}: a crowned skull with burning soul eyes, a see-through ribcage round a glowing soul
 * core, spiked pauldrons, a tattered cape and a bone greatsword), a full set of animations and ten attacks over three
 * phases ({@link BoneTyrantCombat}). He is meant to be the hardest of the three horde bosses, and outclasses the Titan:
 * 2,000 health (v0.14.17; plus 600 for each extra fighter, up to 6,000) against the Titan's 1,800, 16 armour and 12 toughness
 * against its 10 and 8, and heavier hits (a 24 sweep, a 36 charge). Fire can't touch him, nor wither or poison; he
 * can't be knocked back and never despawns. He has his own boss bar, which names his phase.
 *
 * <p>Death keeps vanilla's bookkeeping ({@code super.die}: kill credit, XP, the dead flag -- the horde raid polls
 * {@code isAlive}/{@code isDeadOrDying}, so its victory fires at once) but replaces the 20-tick tip-over with a 4 s
 * collapse, after which he bursts into bone dust. Everything he raised crumbles with him.
 */
public class BoneTyrant extends Monster implements GeoEntity {
	/** The geo model is authored two blocks tall; the SCALE attribute makes him ~7 (the hit-box follows). */
	public static final float SCALE = 3.5f;
	public static final double BASE_HEALTH = 2000.0; // v0.14.17: was 2400
	public static final double HEALTH_PER_FIGHTER = 600.0;
	public static final double MAX_HEALTH_CAP = 6000.0;
	public static final double ARMOR = 16.0;
	public static final double ARMOR_TOUGHNESS = 12.0;
	public static final double MELEE_DAMAGE = 28.0; // v0.14.16 (merge): above the Titan even with an old 26-punch config
	public static final int MAX_MINIONS = 8;
	public static final int DEATH_TICKS = 80;

	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(BoneTyrant.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(BoneTyrant.class, EntityDataSerializers.BYTE);
	private static final net.minecraft.resources.ResourceLocation ENRAGED_SPEED = ProjectHeroMod.id("bone_tyrant_enraged");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final BoneTyrantCombat combat = new BoneTyrantCombat(this);
	private final List<UUID> minions = new ArrayList<>();
	private EventBossBar bossBar;
	private int clipHoldTicks;

	public BoneTyrant(EntityType<? extends BoneTyrant> type, Level level) {
		super(type, level);
		this.xpReward = 400;
		setPersistenceRequired();
		refreshDimensions(); // the SCALE attribute sizes his hit-box
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, MELEE_DAMAGE)
				.add(Attributes.ARMOR, ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 64.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Sets his health for {@code players} fighting him: 2,000 alone, 600 more per extra fighter, at most 6,000. */
	public void configure(int players) {
		double hp = Math.min(MAX_HEALTH_CAP, BASE_HEALTH + HEALTH_PER_FIGHTER * Math.max(0, players - 1));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public BoneTyrantCombat combat() {
		return combat;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BUSY, false);
		builder.define(DATA_PHASE, (byte) 1);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Monster.class)); // a stray arrow from his own horde is no reason to turn
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setHealth(getMaxHealth());
		return out;
	}

	// ---------------------------------------------------------------- the tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		combat.tick(server);
		if (clipHoldTicks > 0) {
			clipHoldTicks--;
		}
		syncBusy();
		updateBossBar(server);
		if (tickCount % 20 == 0) {
			pruneMinions(server);
		}
		if (tickCount % 12 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002 && onGround()) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.6f, 0.4f);
		}
		if (combat.phase() >= 3 && tickCount % 4 == 0) {
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.62, getZ(), 1, 0.6, 0.6, 0.6, 0.01);
		}
	}

	/** Phase 3: 30% faster. */
	private void enrage() {
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(ENRAGED_SPEED) == null) {
			speed.addTransientModifier(new AttributeModifier(ENRAGED_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Called by the combat when he crosses into {@code phase} (2 at 66%, 3 at 33%). */
	void onPhaseChanged(ServerLevel level, int phase) {
		entityData.set(DATA_PHASE, (byte) phase);
		if (phase >= 3) {
			enrage();
		}
		Component title = Component.translatable("boss.projecthero.bone_tyrant.phase" + phase);
		for (Player p : level.players()) {
			if (p.distanceToSqr(this) < 64 * 64) {
				p.displayClientMessage(title.copy().withStyle(phase >= 3 ? net.minecraft.ChatFormatting.DARK_PURPLE : net.minecraft.ChatFormatting.WHITE), true);
			}
		}
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (combat.isRoaring() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.WITHER) || effect.is(MobEffects.POISON)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	@Override
	public float getVoicePitch() {
		return 0.55f;
	}

	// ---------------------------------------------------------------- minions

	/** Raises up to {@code count} of the horde's skeletons round him (never past {@link #MAX_MINIONS} alive). */
	public int raiseMinions(ServerLevel level, int count, int phase) {
		int room = MAX_MINIONS - liveMinions(level);
		int raised = 0;
		for (int i = 0; i < Math.min(count, room); i++) {
			EntityType<? extends Mob> type = switch (phase >= 3 ? i % 4 : phase == 2 ? i % 3 : i % 2) {
				case 0 -> SkeletonHordeEntityTypes.BONE_RUNNER;
				case 1 -> SkeletonHordeEntityTypes.BLIGHT_ARCHER;
				case 2 -> SkeletonHordeEntityTypes.BONE_KNIGHT;
				default -> SkeletonHordeEntityTypes.BONE_BRUTE;
			};
			Mob m = type.create(level);
			if (m == null) {
				continue;
			}
			double a = i * Math.PI * 2 / Math.max(1, count) + getRandom().nextDouble() * 0.5;
			Vec3 at = position().add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0);
			var ground = BoneTyrantCombat.groundAt(level, at.add(0, 1, 0));
			if (ground != null) {
				at = Vec3.atBottomCenterOf(ground);
			}
			m.moveTo(at.x, at.y, at.z, getRandom().nextFloat() * 360f, 0);
			if (m instanceof HordeSkeleton s) {
				s.setHordeWave(4 + phase * 2);
			}
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			if (getTarget() != null) {
				m.setTarget(getTarget());
			}
			level.addFreshEntity(m);
			minions.add(m.getUUID());
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 12, 0.3, 0.5, 0.3, 0.03);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y + 0.2, at.z,
					12, 0.3, 0.1, 0.3, 0.1);
			raised++;
		}
		return raised;
	}

	public int liveMinions(ServerLevel level) {
		pruneMinions(level);
		return minions.size();
	}

	private void pruneMinions(ServerLevel level) {
		minions.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
	}

	/** Every minion he raised crumbles. */
	public void dismissMinions(ServerLevel level) {
		for (UUID id : minions) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				level.sendParticles(ParticleTypes.WHITE_ASH, m.getX(), m.getY() + 0.8, m.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
				m.discard();
			}
		}
		minions.clear();
	}

	// ---------------------------------------------------------------- busy flag / clips

	/**
	 * Plays one of his clips on the "action" controller and holds the busy flag, which stands the looping idle/walk
	 * controller down (otherwise it silently overrides every attack on the client -- the Oathbreaker lesson).
	 */
	public void playClip(String clip) {
		triggerAnim("action", clip);
		syncBusy();
	}

	/** {@link #playClip} for a clip outside any attack, held for {@code ticks} (debug / harness use). */
	public void playClip(String clip, int ticks) {
		clipHoldTicks = ticks;
		playClip(clip);
	}

	void syncBusy() {
		boolean busy = combat.isAttacking() || clipHoldTicks > 0 || isDeadOrDying();
		if (entityData.get(DATA_BUSY) != busy) {
			entityData.set(DATA_BUSY, busy);
		}
	}

	public boolean isBusy() {
		return entityData.get(DATA_BUSY);
	}

	// ---------------------------------------------------------------- boss bar

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6, false);
		}
		return bossBar;
	}

	private void updateBossBar(ServerLevel server) {
		if (isRemoved() || isDeadOrDying() || tickCount % 4 != 0) {
			return;
		}
		int phase = combat.phase();
		bossBar().setColor(phase >= 3 ? BossEvent.BossBarColor.PURPLE : phase == 2 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE);
		bossBar().update(server, blockPosition(), 64.0, Component.translatable("boss.projecthero.bone_tyrant.bar." + phase),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		if (dead || isRemoved()) {
			return;
		}
		combat.cancel(true);
		super.die(source);
		setTarget(null);
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		triggerAnim("action", "death");
		syncBusy();
		if (level() instanceof ServerLevel server) {
			if (bossBar != null) {
				bossBar.clear(server);
			}
			dismissMinions(server);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 3.0f, 0.5f);
		}
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Vec3 mid = position().add(0, getBbHeight() * 0.35, 0);
		if (deathTime % 4 == 0 && deathTime < DEATH_TICKS - 10) {
			server.sendParticles(ParticleTypes.SOUL, mid.x, mid.y, mid.z, 3, getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
		}
		if (deathTime == 44 || deathTime == 60) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), mid.x, mid.y, mid.z,
					160, getBbWidth() * 0.8, getBbHeight() * 0.3, getBbWidth() * 0.8, 0.2);
			server.sendParticles(ParticleTypes.SOUL, mid.x, mid.y + 1, mid.z, 40, 1.0, 1.5, 1.0, 0.08);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 3.0f, 0.4f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server) {
			if (bossBar != null) {
				bossBar.clear(server);
			}
			if (reason.shouldDestroy()) {
				dismissMinions(server);
			}
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("TyrantPhase", (byte) combat.phase());
		ListTag list = new ListTag();
		for (UUID u : minions) {
			list.add(NbtUtils.createUUID(u));
		}
		tag.put("TyrantMinions", list);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// attacks and roars run on timers that aren't saved: never come back frozen mid-move
		setNoAi(false);
		setInvulnerable(false);
		int phase = Math.max(1, Math.min(3, tag.getByte("TyrantPhase")));
		combat.setPhaseSilently(phase);
		entityData.set(DATA_PHASE, (byte) phase);
		if (phase >= 3) {
			enrage();
		}
		minions.clear();
		ListTag list = tag.getList("TyrantMinions", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			minions.add(NbtUtils.loadUUID(list.get(i)));
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	/** Every clip on the "action" controller (attack clips are named after {@link BoneTyrantCombat.Attack#clip}). */
	public static final String[] ACTION_CLIPS = { "sweep", "cleave", "quake", "volley", "summon", "grave_step", "storm", "cage",
			"charge_windup", "charge_end", "soul_beam", "roar", "death" };

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<BoneTyrant> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
		for (String name : ACTION_CLIPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.bone_tyrant." + name));
		}
		action.triggerableAnim("charge_run", RawAnimation.begin().thenLoop("animation.bone_tyrant.charge_run"));
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<BoneTyrant> state) {
		if (isDeadOrDying() || entityData.get(DATA_BUSY)) {
			return PlayState.STOP;
		}
		if (state.getLimbSwingAmount() > 0.04f) {
			state.getController().setAnimationSpeed(phase() >= 3 ? 1.3 : 1.0);
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.bone_tyrant.walk"));
		}
		state.getController().setAnimationSpeed(1.0);
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.bone_tyrant.idle"));
	}

	/** True for players a quake or rain should treat as enemies (everyone but creative / spectators). */
	static boolean enemy(LivingEntity e) {
		return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
	}
}
