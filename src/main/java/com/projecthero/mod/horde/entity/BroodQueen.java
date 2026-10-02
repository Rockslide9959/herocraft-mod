package com.projecthero.mod.horde.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.titan.TitanHealthCap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
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
 * The Spider Horde's boss. v0.14.12 she was a vanilla spider at 3.5x; <b>v0.14.16</b> she is rebuilt as a GeckoLib boss
 * -- her own model (about 5.5 blocks tall and 7.5 across: eight jointed legs, a bloated abdomen crowned with glowing egg
 * sacs, a chitin crown, fangs and ten burning eyes), her own animations and a telegraphed attack state machine. She is
 * the hardest horde boss: {@value #BASE_HEALTH} health (the Titan has 1,800) plus {@value #HEALTH_PER_FIGHTER} per extra
 * fighter, 18 armour / 10 toughness, immune to knockback, fall damage and fire.
 *
 * <p><b>Three phases</b> (by health: above 2/3, above 1/3, below), each opened by a <b>Shriek</b> (she rears up and
 * screams: everything near is thrown back, fighters are plunged into Darkness, spiderlings pour out; she can't be hurt
 * while she screams). Each phase hits harder (x1.0 / x1.15 / x1.3) and moves faster.
 *
 * <p><b>Attacks</b> -- every one has a wind-up animation and a particle/sound tell:
 * <ol>
 *   <li><b>Fang Lunge</b> (close): rears up, fangs wide, then darts forward and bites -- 26 and Poison II;</li>
 *   <li><b>Leg Sweep</b> (close, red ring on the ground): spins her whole body round -- 22 to everything within 7, flung away;</li>
 *   <li><b>Venom Spray</b> (mid): sprays a sweeping 13-block cone of venom -- 5 every 4 ticks and Poison III;</li>
 *   <li><b>Web Volley</b> (range): three salvos -- a cocooning web at her target (wraps them in cobweb, blinds and roots
 *       them), a fan of big webs round it, and a web for every other fighter;</li>
 *   <li><b>Leap Slam</b> (8-28 blocks, red ring follows the target, then freezes on the jump): 30 at the centre, 14 out to 9;</li>
 *   <li><b>Egg Burst</b> (phase 2+): her abdomen swells and bursts -- a swarm of spiderlings (more for more fighters,
 *       hunters with them later) and an acid splash behind her;</li>
 *   <li><b>Web Ascent</b> (phase 2+): climbs a web line up to 14 blocks into the air, hangs there stalking her target
 *       (a ring marks the spot beneath her), then drops -- 36 at the centre, 16 out to 9, and a ring of cobweb;</li>
 *   <li><b>Acid Rain</b> (phase 2+): bubbling marks under every fighter and round her target turn into acid pools for
 *       8 seconds (4 every half second and poison);</li>
 *   <li><b>Frenzy</b> (phase 3): three lunges back to back.</li>
 * </ol>
 * Nothing is static: her acid pools, brood and attack state live on the entity, so nothing outlives her.
 */
public class BroodQueen extends Spider implements GeoEntity {
	/** Kept for older callers: the v0.14.12 queen was a vanilla spider at this scale; the GeckoLib queen is full size. */
	public static final float SCALE = 1.0f;
	public static final double BASE_HEALTH = 2400.0; // v0.14.17: was 4000
	public static final double HEALTH_PER_FIGHTER = 1200.0;
	public static final float WIDTH = 4.4f;
	public static final float HEIGHT = 3.4f;
	public static final int INTRO_TICKS = 40;
	public static final int DEATH_TICKS = 60;
	public static final int MAX_BROOD = 14;
	public static final double BASE_SPEED = 0.34;
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.08f, 0.08f), 1.6f);
	private static final DustParticleOptions VENOM = new DustParticleOptions(new Vector3f(0.45f, 0.95f, 0.25f), 1.5f);
	private static final DustParticleOptions ACID = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 0.2f), 1.5f);

	/**
	 * Every move: the clip it plays, wind-up / active / recovery ticks, its own cooldown, the first phase it is used in,
	 * the target distances it is picked at and its base pick weight. Clip lengths match these timings.
	 */
	public enum Attack {
		FANG_LUNGE("fang_lunge", 14, 4, 14, 40, 1, 0, 7.5, 5),
		LEG_SWEEP("leg_sweep", 16, 6, 14, 90, 1, 0, 6.5, 4),
		VENOM_SPRAY("venom_spray", 16, 24, 12, 140, 1, 3, 13, 3),
		WEB_VOLLEY("web_volley", 14, 18, 12, 120, 1, 6, 32, 3),
		LEAP_SLAM("leap_windup", 16, 60, 14, 160, 1, 8, 28, 3),
		EGG_BURST("egg_burst", 24, 4, 16, 400, 2, 0, 40, 2),
		WEB_ASCENT("ascend", 14, 110, 14, 360, 2, 0, 30, 2),
		ACID_RAIN("acid_rain", 18, 6, 14, 260, 2, 0, 30, 3),
		FRENZY("frenzy_lunge", 7, 3, 4, 220, 3, 0, 9, 4),
		SHRIEK("shriek", 20, 10, 20, 0, 9, 0, 0, 0);

		public final String clip;
		public final int windup;
		public final int active;
		public final int recovery;
		public final int cooldown;
		public final int minPhase;
		public final double minRange;
		public final double maxRange;
		public final int weight;

		Attack(String clip, int windup, int active, int recovery, int cooldown, int minPhase, double minRange, double maxRange, int weight) {
			this.clip = clip;
			this.windup = windup;
			this.active = active;
			this.recovery = recovery;
			this.cooldown = cooldown;
			this.minPhase = minPhase;
			this.minRange = minRange;
			this.maxRange = maxRange;
			this.weight = weight;
		}

		public int total() {
			return windup + active + recovery;
		}
	}

	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(BroodQueen.class, EntityDataSerializers.BYTE);
	/** True while a triggered clip on the "action" controller owns the pose. */
	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(BroodQueen.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private EventBossBar bossBar;

	// ---- attack state (server only, never static)
	private Attack attack;
	private int attackTick;
	private final int[] cooldowns = new int[Attack.values().length];
	private int globalCooldown = 30;
	private int frenzyCount;
	private final Set<UUID> hitThisAttack = new HashSet<>();
	private boolean introDone;
	private int introTicks;
	private int stuckTicks;
	private int repathTicks;
	private float sprayYaw;
	private Vec3 markedSpot;
	private boolean airborne;
	private int ascentStage; // 0 rise, 1 hang, 2 drop
	private int ascentStageTicks;
	private double ascentTopY;
	private final List<Vec3> acidSpots = new ArrayList<>();
	private final List<AcidPool> pools = new ArrayList<>();
	private final List<UUID> brood = new ArrayList<>();

	/** One acid pool from Acid Rain (or the Egg Burst splash). */
	private static final class AcidPool {
		final Vec3 at;
		final double radius;
		int ticksLeft;

		AcidPool(Vec3 at, double radius, int ticks) {
			this.at = at;
			this.radius = radius;
			this.ticksLeft = ticks;
		}
	}

	public BroodQueen(EntityType<? extends Spider> type, Level level) {
		super(type, level);
		this.xpReward = 600;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Spider.createAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
				.add(Attributes.ATTACK_DAMAGE, 18.0)
				.add(Attributes.ARMOR, 18.0)
				.add(Attributes.ARMOR_TOUGHNESS, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	/** Sets her health for {@code players} fighting her (within the mod's raised health ceiling). */
	public void configure(int players) {
		double hp = Math.min(TitanHealthCap.NEW_MAX_HEALTH_CEILING, BASE_HEALTH + HEALTH_PER_FIGHTER * Math.max(0, players - 1));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_BUSY, false);
	}

	/** 1, 2 or 3. */
	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	public boolean isBusy() {
		return this.entityData.get(DATA_BUSY);
	}

	public Attack currentAttack() {
		return attack;
	}

	public int attackTick() {
		return attackTick;
	}

	public int broodCount() {
		return brood.size();
	}

	public int acidPoolCount() {
		return pools.size();
	}

	/** Damage multiplier for the current phase. */
	public float phaseMultiplier() {
		return switch (phase()) {
			case 2 -> 1.15f;
			case 3 -> 1.3f;
			default -> 1.0f;
		};
	}

	/** The phase her health puts her in. */
	public static int phaseFor(float health, float max) {
		float f = max <= 0 ? 1 : health / max;
		return f > 2f / 3f ? 1 : f > 1f / 3f ? 2 : 3;
	}

	/** Whom her attacks hurt: anything alive that is not a spider, a monster, an armour stand or a creative/spectator player. */
	public static boolean prey(Entity self, LivingEntity e) {
		if (e == self || !e.isAlive() || e instanceof Spider || e instanceof Enemy || e instanceof ArmorStand) {
			return false;
		}
		return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
	}

	// ---------------------------------------------------------------- vanilla hooks

	@Override
	protected void registerGoals() {
		// not super: the vanilla spider's goals give up in daylight and its leap would fight the state machine
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
			SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
		removeAllEffects(); // vanilla's random hard-mode spider buffs (one is invisibility)
		ejectPassengers(); // and its jockey
		return out;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return 0.45f;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0f;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (attack == Attack.SHRIEK && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // she can't be hurt while she screams
		}
		boolean hurt = super.hurt(source, amount);
		// a fighter who hurts her from close by while her target is far away draws her attention
		if (hurt && source.getEntity() instanceof Player p && prey(this, p)) {
			LivingEntity t = getTarget();
			if (t == null || !t.isAlive() || (t != p && distanceToSqr(t) > 14 * 14 && distanceToSqr(p) < 10 * 10)) {
				setTarget(p);
			}
		}
		return hurt;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		combatTick();
	}

	/** One tick of her whole brain (public so the GameTests can drive it on a NoAI queen). */
	public void combatTick() {
		if (!(level() instanceof ServerLevel level) || isDeadOrDying()) {
			return;
		}
		tickPools(level);
		if (tickCount % 10 == 0) {
			updateBossBar(level);
			brood.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
		}
		if (!introDone) {
			introDone = true;
			introTicks = INTRO_TICKS;
			setBusy(true);
			triggerAnim("action", "intro");
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 4.0f, 0.3f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0f, 1.2f);
		}
		if (introTicks > 0) {
			getNavigation().stop();
			if (--introTicks == 0) {
				setBusy(false);
			}
			return;
		}
		LivingEntity target = getTarget();
		boolean hasTarget = target != null && target.isAlive() && prey(this, target);
		if (attack != null) {
			tickAttack(level, hasTarget ? target : null);
			return;
		}
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}
		if (globalCooldown > 0) {
			globalCooldown--;
		}
		int want = phaseFor(getHealth(), getMaxHealth());
		if (want > phase()) {
			enterPhase(level, want);
			return;
		}
		if (!hasTarget) {
			return;
		}
		chase(target);
		if (globalCooldown <= 0) {
			Attack next = choose(level, target);
			if (next != null) {
				start(level, next, target);
			}
		}
	}

	private void enterPhase(ServerLevel level, int phase) {
		this.entityData.set(DATA_PHASE, (byte) phase);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BASE_SPEED * (phase == 3 ? 1.25 : phase == 2 ? 1.1 : 1.0));
		start(level, Attack.SHRIEK, getTarget());
	}

	/** Walks at her target and keeps re-pathing; stuck for 3 s with her target well away, she leaps at it. */
	private void chase(LivingEntity target) {
		double d = distanceTo(target);
		getLookControl().setLookAt(target, 30f, 30f);
		if (d > WIDTH * 0.5 + 2.5) {
			if (--repathTicks <= 0 || getNavigation().isDone()) {
				getNavigation().moveTo(target, 1.0);
				repathTicks = 10;
			}
			if (getNavigation().isDone() || getDeltaMovement().horizontalDistanceSqr() < 0.0004) {
				stuckTicks++;
			} else {
				stuckTicks = Math.max(0, stuckTicks - 1);
			}
		} else {
			getNavigation().stop();
			stuckTicks = 0;
		}
	}

	/** A weighted pick among the moves she can use right now (null: nothing fits, keep walking). */
	private Attack choose(ServerLevel level, LivingEntity target) {
		double d = distanceTo(target);
		boolean los = getSensing().hasLineOfSight(target);
		int nearby = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0), e -> prey(this, e)).size();
		if (stuckTicks > 60 && d > 6 && onGround() && cooldowns[Attack.LEAP_SLAM.ordinal()] <= 0) {
			stuckTicks = 0;
			return Attack.LEAP_SLAM;
		}
		List<Attack> options = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Attack a : Attack.values()) {
			if (a == Attack.SHRIEK || a.minPhase > phase() || cooldowns[a.ordinal()] > 0 || d < a.minRange || d > a.maxRange) {
				continue;
			}
			int w = a.weight;
			switch (a) {
				case VENOM_SPRAY, WEB_VOLLEY -> {
					if (!los) {
						continue;
					}
					if (a == Attack.WEB_VOLLEY && d > 14) {
						w += 3;
					}
				}
				case LEAP_SLAM -> {
					if (!onGround()) {
						continue;
					}
					if (d > 14 || target.getY() > getY() + 4) {
						w += 3;
					}
				}
				case LEG_SWEEP -> w += Math.min(4, nearby - 1) * 2;
				case EGG_BURST -> {
					if (brood.size() >= MAX_BROOD - 3) {
						continue;
					}
				}
				case WEB_ASCENT -> {
					if (!onGround() || headroom(level) < 8) {
						continue;
					}
				}
				default -> {
				}
			}
			if (w <= 0) {
				continue;
			}
			options.add(a);
			weights.add(w);
			total += w;
		}
		if (total <= 0) {
			return null;
		}
		int roll = getRandom().nextInt(total);
		for (int i = 0; i < options.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return options.get(i);
			}
		}
		return options.get(options.size() - 1);
	}

	/** Skips the rise-from-the-ground intro (GameTests). */
	public void skipIntro() {
		introDone = true;
		introTicks = 0;
		setBusy(false);
	}

	/** Starts {@code a} now, regardless of range and cooldown (the GameTests use this too). */
	public void forceAttack(Attack a) {
		if (level() instanceof ServerLevel level) {
			introDone = true;
			introTicks = 0;
			start(level, a, getTarget());
		}
	}

	private void start(ServerLevel level, Attack a, LivingEntity target) {
		attack = a;
		attackTick = 0;
		hitThisAttack.clear();
		airborne = false;
		ascentStage = 0;
		ascentStageTicks = 0;
		acidSpots.clear();
		markedSpot = null;
		if (a != Attack.FRENZY) {
			frenzyCount = 0;
		}
		getNavigation().stop();
		setBusy(true);
		triggerAnim("action", a.clip);
		if (target != null) {
			face(target);
		}
		switch (a) {
			case SHRIEK -> {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 1.5f);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_DEATH, SoundSource.HOSTILE, 3.0f, 0.4f);
				Component name = Component.translatable("boss.projecthero.brood_queen.phase" + phase()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
				for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48.0))) {
					p.displayClientMessage(name, true);
				}
			}
			case ACID_RAIN -> {
				for (LivingEntity e : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32.0), e -> prey(this, e))) {
					acidSpots.add(e.position());
				}
				Vec3 c = target != null ? target.position() : position();
				for (int i = 0; i < 3; i++) {
					double ang = getRandom().nextDouble() * Math.PI * 2;
					double r = 3 + getRandom().nextDouble() * 7;
					acidSpots.add(ground(level, c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)));
				}
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			case EGG_BURST -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 2.5f, 0.5f);
			case LEG_SWEEP -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.6f);
			case WEB_ASCENT -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.8f);
			default -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5f, 0.5f);
		}
	}

	private void finish() {
		Attack done = attack;
		attack = null;
		setNoGravity(false);
		if (done == Attack.FRENZY && frenzyCount < 2 && getTarget() != null && getTarget().isAlive()
				&& level() instanceof ServerLevel level) {
			frenzyCount++;
			start(level, Attack.FRENZY, getTarget());
			return;
		}
		frenzyCount = 0;
		setBusy(false);
		if (done != null) {
			cooldowns[done.ordinal()] = done.cooldown;
		}
		globalCooldown = switch (phase()) {
			case 3 -> 10;
			case 2 -> 16;
			default -> 22;
		};
	}

	/** Skips straight to the recovery of the running attack (the jumps end when she lands, not on a timer). */
	private void endActive() {
		attackTick = attack.windup + attack.active;
	}

	private void tickAttack(ServerLevel level, LivingEntity target) {
		Attack a = attack;
		int t = attackTick++;
		if (t < a.windup) {
			if (target != null && a != Attack.VENOM_SPRAY && a != Attack.ACID_RAIN) {
				face(target);
			}
			windup(level, a, t, target);
		} else if (t < a.windup + a.active) {
			active(level, a, t - a.windup, target);
		} else if (t >= a.total() - 1) {
			finish();
			return;
		}
		if (a != Attack.FANG_LUNGE && a != Attack.FRENZY && a != Attack.LEAP_SLAM && a != Attack.WEB_ASCENT) {
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
		}
	}

	// ---------------------------------------------------------------- wind-ups (the tells)

	private void windup(ServerLevel level, Attack a, int t, LivingEntity target) {
		Vec3 fwd = forward();
		switch (a) {
			case FANG_LUNGE, FRENZY -> {
				if (t % 2 == 0) {
					Vec3 head = headPos(fwd);
					level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 4, 0.5, 0.3, 0.5, 0.1);
					level.sendParticles(VENOM, head.x, head.y - 1.0, head.z, 2, 0.3, 0.2, 0.3, 0);
				}
			}
			case LEG_SWEEP -> {
				if (t % 2 == 0) {
					ring(level, ground(level, position()), 7.0, RED, 28);
				}
			}
			case VENOM_SPRAY -> {
				sprayYaw = getYRot();
				Vec3 head = headPos(fwd);
				level.sendParticles(VENOM, head.x, head.y - 0.6, head.z, 3, 0.3, 0.2, 0.3, 0);
				if (t == 0) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 2.0f, 0.7f);
				}
			}
			case WEB_VOLLEY -> {
				if (t % 3 == 0) {
					Vec3 back = position().add(fwd.scale(-2.6)).add(0, 3.8, 0);
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), back.x, back.y, back.z,
							3, 0.5, 0.4, 0.5, 0.02);
				}
			}
			case LEAP_SLAM -> {
				if (target != null) {
					markedSpot = ground(level, target.position());
				}
				if (markedSpot != null && t % 2 == 0) {
					ring(level, markedSpot, 5.0, RED, 24);
				}
				if (t % 4 == 0) {
					level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 6, WIDTH * 0.4, 0.05, WIDTH * 0.4, 0.02);
				}
			}
			case EGG_BURST -> {
				Vec3 belly = position().add(fwd.scale(-2.4)).add(0, 3.6, 0);
				level.sendParticles(ParticleTypes.ITEM_SLIME, belly.x, belly.y, belly.z, 3, 1.0, 0.6, 1.0, 0.02);
				if (t % 6 == 0) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 2.0f, 0.5f + t * 0.02f);
				}
			}
			case WEB_ASCENT -> {
				if (t % 2 == 0) {
					webLine(level, position().add(0, HEIGHT, 0), 14);
				}
			}
			case ACID_RAIN -> {
				if (t % 3 == 0) {
					for (Vec3 s : acidSpots) {
						ring(level, s, 2.5, ACID, 12);
						level.sendParticles(ParticleTypes.BUBBLE_POP, s.x, s.y + 0.2, s.z, 4, 1.2, 0.05, 1.2, 0.02);
					}
				}
			}
			case SHRIEK -> {
				if (t % 4 == 0) {
					level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + HEIGHT, getZ(), 8, 1.5, 0.6, 1.5, 0.05);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the moves themselves

	private void active(ServerLevel level, Attack a, int t, LivingEntity target) {
		Vec3 fwd = forward();
		float m = phaseMultiplier();
		switch (a) {
			case FANG_LUNGE, FRENZY -> {
				if (t == 0) {
					setDeltaMovement(fwd.x * (a == Attack.FRENZY ? 0.9 : 1.1), 0.2, fwd.z * (a == Attack.FRENZY ? 0.9 : 1.1));
					hasImpulse = true;
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 2.5f, 0.4f);
				}
				Vec3 head = headPos(fwd);
				for (LivingEntity e : preyNear(level, head, 3.6)) {
					if (hitThisAttack.add(e.getUUID())) {
						e.hurt(damageSources().mobAttack(this), (a == Attack.FRENZY ? 18f : 26f) * m);
						e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
						level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
					}
				}
			}
			case LEG_SWEEP -> {
				if (t == 0) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.5f);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.0, getZ(), 12, 4.0, 0.3, 4.0, 0);
				}
				for (LivingEntity e : preyNear(level, position().add(0, 1, 0), 7.0)) {
					if (Math.abs(e.getY() - getY()) < 4 && hitThisAttack.add(e.getUUID())) {
						e.hurt(damageSources().mobAttack(this), 22f * m);
						fling(e, 1.6, 0.55);
					}
				}
			}
			case VENOM_SPRAY -> {
				float yaw = sprayYaw - 25f * Mth.sin((float) (Math.PI * 2 * t / 12.0));
				setYRot(yaw);
				yBodyRot = yaw;
				yHeadRot = yaw;
				Vec3 dir = Vec3.directionFromRotation(10f, yaw);
				Vec3 head = headPos(forward()).add(0, -0.6, 0);
				for (int i = 1; i <= 6; i++) {
					Vec3 p = head.add(dir.scale(i * 2.0));
					double spread = 0.15 * i;
					level.sendParticles(VENOM, p.x, p.y, p.z, 3, spread, spread * 0.5, spread, 0);
					if (i % 2 == 0) {
						level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y, p.z, 1, spread, spread * 0.5, spread, 0.05);
					}
				}
				if (t % 4 == 0) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.5f, 0.5f);
					for (LivingEntity e : preyNear(level, head, 13.0)) {
						Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(head);
						Vec3 flat = new Vec3(to.x, 0, to.z);
						Vec3 dirFlat = new Vec3(dir.x, 0, dir.z);
						if (flat.lengthSqr() < 1.0e-4 || flat.normalize().dot(dirFlat.normalize()) > Math.cos(Math.toRadians(28))) {
							e.invulnerableTime = 0;
							e.hurt(damageSources().mobAttack(this), 5f * m);
							e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 2), this);
						}
					}
				}
			}
			case WEB_VOLLEY -> {
				if (t % 6 == 0 && t <= 12) {
					if (target != null) {
						HordeSpider.spit(this, target, WebShotEntity.Mode.COCOON, 1.0f);
						for (int i = 0; i < 4; i++) {
							HordeSpider.spit(this, target, WebShotEntity.Mode.QUEEN, 9.0f);
						}
					}
					for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32.0), e -> prey(this, e) && e != target)) {
						HordeSpider.spit(this, p, WebShotEntity.Mode.QUEEN, 3.0f);
					}
				}
			}
			case LEAP_SLAM -> tickLeap(level, t, target);
			case EGG_BURST -> {
				if (t == 0) {
					eggBurst(level, target);
				}
			}
			case WEB_ASCENT -> tickAscent(level, t, target);
			case ACID_RAIN -> {
				if (t == 0) {
					Vec3 from = position().add(forward().scale(-2.4)).add(0, 4.0, 0);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 2.5f, 0.4f);
					for (Vec3 s : acidSpots) {
						for (int i = 1; i <= 8; i++) {
							Vec3 p = from.lerp(s, i / 8.0).add(0, Math.sin(Math.PI * i / 8.0) * 3.0, 0);
							level.sendParticles(ACID, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
						}
						pools.add(new AcidPool(s, 2.5, 160));
						level.sendParticles(ParticleTypes.ITEM_SLIME, s.x, s.y + 0.3, s.z, 16, 1.2, 0.2, 1.2, 0.1);
					}
				}
			}
			case SHRIEK -> {
				if (t == 0) {
					shriek(level, target);
				}
			}
		}
	}

	private void tickLeap(ServerLevel level, int t, LivingEntity target) {
		if (t == 0) {
			Vec3 spot = markedSpot != null ? markedSpot : target != null ? target.position() : position().add(forward().scale(10));
			markedSpot = spot;
			Vec3 to = spot.subtract(position());
			double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
			double ticks = Math.max(10.0, horizontal / 1.2);
			// a lob that lands on the spot: ~0.08 gravity
			setDeltaMovement(to.x / ticks, Mth.clamp(0.04 * ticks + to.y / ticks, 0.6, 2.0), to.z / ticks);
			hasImpulse = true;
			airborne = true;
			triggerAnim("action", "leap_air");
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.4f);
			return;
		}
		if (markedSpot != null && t % 3 == 0) {
			ring(level, markedSpot, 5.0, RED, 24);
		}
		if (airborne && ((onGround() && t > 4) || isInWater() || t >= attack.active - 1)) {
			airborne = false;
			slam(level, 30f, 14f, 5.0, 9.0, false);
			endActive();
		}
	}

	private void tickAscent(ServerLevel level, int t, LivingEntity target) {
		ascentStageTicks++;
		switch (ascentStage) {
			case 0 -> {
				if (t == 0) {
					setNoGravity(true);
					ascentTopY = getY() + Math.min(14.0, headroom(level) - 1.0);
					triggerAnim("action", "hang");
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				setDeltaMovement(0, 0.8, 0);
				hasImpulse = true;
				webLine(level, position().add(0, HEIGHT, 0), 10);
				if (getY() >= ascentTopY - 0.5 || ascentStageTicks >= 20) {
					ascentStage = 1;
					ascentStageTicks = 0;
					setDeltaMovement(Vec3.ZERO);
				}
			}
			case 1 -> {
				Vec3 v = Vec3.ZERO;
				if (target != null) {
					Vec3 to = target.position().subtract(position());
					Vec3 flat = new Vec3(to.x, 0, to.z);
					double len = flat.length();
					if (len > 0.3) {
						v = flat.scale(Math.min(0.35, len) / len);
					}
					face(target);
				}
				setDeltaMovement(v.x, (ascentTopY - getY()) * 0.3, v.z);
				hasImpulse = true;
				markedSpot = ground(level, position());
				if (ascentStageTicks % 2 == 0) {
					ring(level, markedSpot, 4.5, RED, 24);
					webLine(level, position().add(0, HEIGHT, 0), 6);
				}
				if (ascentStageTicks % 15 == 0) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.0f, 0.4f);
				}
				if (ascentStageTicks >= 40) {
					ascentStage = 2;
					ascentStageTicks = 0;
					setNoGravity(false);
					setDeltaMovement(0, -2.2, 0);
					hasImpulse = true;
					triggerAnim("action", "drop");
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 3.0f, 0.3f);
				}
			}
			default -> {
				setDeltaMovement(getDeltaMovement().x * 0.5, Math.min(getDeltaMovement().y, -1.6), getDeltaMovement().z * 0.5);
				if (onGround() || isInWater() || ascentStageTicks >= 40) {
					slam(level, 36f, 16f, 4.5, 9.0, true);
					endActive();
				}
			}
		}
		if (t >= attack.active - 1) {
			setNoGravity(false); // never leave her hanging
		}
	}

	/** A landing: {@code inner} damage within {@code r1}, {@code outer} out to {@code r2}, everything flung away. */
	private void slam(ServerLevel level, float inner, float outer, double r1, double r2, boolean webs) {
		float m = phaseMultiplier();
		setNoGravity(false);
		fallDistance = 0;
		triggerAnim("action", "slam");
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.3f);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 5, 2.0, 0.2, 2.0, 0);
		ring(level, position(), r1, ParticleTypes.CLOUD, 32);
		ring(level, position(), r2, ParticleTypes.CLOUD, 40);
		for (LivingEntity e : preyNear(level, position(), r2)) {
			if (Math.abs(e.getY() - getY()) > 5) {
				continue;
			}
			double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
			e.hurt(damageSources().mobAttack(this), (d <= r1 ? inner : outer) * m);
			fling(e, d <= r1 ? 1.3 : 0.9, 0.5);
		}
		if (webs) {
			BlockPos c = blockPosition();
			for (int i = 0; i < 18; i++) {
				double ang = i * Math.PI * 2 / 18;
				double r = 3.5 + getRandom().nextDouble() * 2.5;
				BlockPos p = BlockPos.containing(c.getX() + 0.5 + Math.cos(ang) * r, getY(), c.getZ() + 0.5 + Math.sin(ang) * r);
				if (level.getBlockState(p).isAir()) {
					TempBlocks.place(level, p, Blocks.COBWEB.defaultBlockState(), 100);
				}
			}
		}
	}

	private void eggBurst(ServerLevel level, LivingEntity target) {
		Vec3 belly = position().add(forward().scale(-2.4));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 2.5f, 0.4f);
		level.sendParticles(ParticleTypes.ITEM_SLIME, belly.x, belly.y + 3.0, belly.z, 40, 1.5, 1.0, 1.5, 0.15);
		level.sendParticles(ACID, belly.x, belly.y + 2.0, belly.z, 30, 2.0, 1.0, 2.0, 0);
		int fighters = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0), e -> prey(this, e)).size();
		int n = Math.min(3 + 2 * Math.max(1, fighters), MAX_BROOD - brood.size());
		for (int i = 0; i < n; i++) {
			hatch(level, belly.add(getRandom().nextGaussian(), 1.5, getRandom().nextGaussian()), BroodSpider.Variant.SPIDERLING, target);
		}
		if (phase() >= 2 && brood.size() < MAX_BROOD) {
			hatch(level, belly.add(0, 1.0, 0), BroodSpider.Variant.HUNTER, target);
		}
		if (phase() >= 3 && brood.size() < MAX_BROOD) {
			hatch(level, belly.add(0, 1.0, 0), BroodSpider.Variant.VENOM, target);
		}
		for (LivingEntity e : preyNear(level, belly, 5.0)) {
			e.hurt(damageSources().mobAttack(this), 8f * phaseMultiplier());
			e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
		}
	}

	private void hatch(ServerLevel level, Vec3 at, BroodSpider.Variant variant, LivingEntity target) {
		BroodSpider s;
		if (variant == BroodSpider.Variant.SPIDERLING) {
			s = BroodSpider.spawnSpiderling(level, at, target, getUUID(), true);
		} else {
			s = HordeEntityTypes.BROOD_SPIDER.create(level);
			if (s != null) {
				s.moveTo(at.x, at.y, at.z, getRandom().nextFloat() * 360f, 0f);
				s.setVariant(variant);
				s.finalizeSpawn(level, level.getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
				s.setMother(getUUID());
				if (target != null) {
					s.setTarget(target);
				}
				level.addFreshEntity(s);
			}
		}
		if (s != null) {
			brood.add(s.getUUID());
		}
	}

	private void shriek(ServerLevel level, LivingEntity target) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 1.6f);
		level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + HEIGHT * 0.6, getZ(), 1, 0, 0, 0, 0);
		ring(level, position().add(0, 0.5, 0), 6.0, ParticleTypes.SQUID_INK, 36);
		ring(level, position().add(0, 0.5, 0), 10.0, ParticleTypes.SQUID_INK, 48);
		for (LivingEntity e : preyNear(level, position(), 10.0)) {
			fling(e, 1.8, 0.6);
		}
		for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(28.0), e -> prey(this, e))) {
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
		}
		for (int i = 0; i < 4 && brood.size() < MAX_BROOD; i++) {
			hatch(level, position().add(getRandom().nextGaussian() * 1.5, 1.0, getRandom().nextGaussian() * 1.5),
					BroodSpider.Variant.SPIDERLING, target);
		}
	}

	private void tickPools(ServerLevel level) {
		if (pools.isEmpty()) {
			return;
		}
		boolean hurtTick = tickCount % 10 == 0;
		pools.removeIf(p -> --p.ticksLeft <= 0);
		for (AcidPool p : pools) {
			if (tickCount % 4 == 0) {
				level.sendParticles(ACID, p.at.x, p.at.y + 0.15, p.at.z, 6, p.radius * 0.5, 0.05, p.radius * 0.5, 0);
				level.sendParticles(ParticleTypes.BUBBLE_POP, p.at.x, p.at.y + 0.2, p.at.z, 2, p.radius * 0.4, 0.05, p.radius * 0.4, 0.01);
			}
			if (hurtTick) {
				for (LivingEntity e : preyNear(level, p.at, p.radius + 0.5)) {
					if (Math.abs(e.getY() - p.at.y) < 2.0) {
						e.invulnerableTime = 0;
						e.hurt(damageSources().indirectMagic(this, this), 4f);
						e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), this);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private Vec3 forward() {
		return Vec3.directionFromRotation(0f, getYRot());
	}

	private Vec3 headPos(Vec3 fwd) {
		return position().add(fwd.scale(WIDTH * 0.5 + 1.0)).add(0, 1.4, 0);
	}

	private void face(LivingEntity target) {
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	private void fling(LivingEntity e, double horizontal, double up) {
		Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? forward() : away.normalize();
		e.push(away.x * horizontal, up, away.z * horizontal);
		e.hurtMarked = true;
	}

	private List<LivingEntity> preyNear(ServerLevel level, Vec3 at, double r) {
		return level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r, Math.max(3.0, r * 0.5), r),
				e -> prey(this, e) && e.distanceToSqr(at.x, Mth.clamp(at.y, e.getY(), e.getY() + e.getBbHeight()), at.z) <= r * r);
	}

	/** The ground under (or at) {@code at}. */
	private Vec3 ground(ServerLevel level, Vec3 at) {
		var hit = level.clip(new ClipContext(at.add(0, 2, 0), at.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? at : new Vec3(at.x, hit.getLocation().y, at.z);
	}

	/** Open air straight above her head, up to 16 blocks. */
	private double headroom(ServerLevel level) {
		Vec3 top = position().add(0, HEIGHT, 0);
		var hit = level.clip(new ClipContext(top, top.add(0, 16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? 16.0 : hit.getLocation().y - top.y;
	}

	private void ring(ServerLevel level, Vec3 c, double r, ParticleOptions particle, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
		}
	}

	private void webLine(ServerLevel level, Vec3 from, int length) {
		ItemParticleOption web = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
		for (int i = 0; i < length; i++) {
			level.sendParticles(web, from.x, from.y + i, from.z, 1, 0.05, 0.3, 0.05, 0);
		}
	}

	private void setBusy(boolean busy) {
		this.entityData.set(DATA_BUSY, busy);
	}

	// ---------------------------------------------------------------- boss bar, death, save

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6, true);
		}
		return bossBar;
	}

	private void updateBossBar(ServerLevel level) {
		if (isRemoved() || isDeadOrDying()) {
			return;
		}
		bossBar().update(level, blockPosition(), 64.0,
				Component.translatable("boss.projecthero.brood_queen.phase" + phase()).withStyle(ChatFormatting.DARK_RED),
				getHealth() / getMaxHealth());
	}

	@Override
	public void die(DamageSource source) {
		if (isDeadOrDying() && deathTime > 0) {
			return;
		}
		attack = null;
		pools.clear();
		setNoGravity(false);
		super.die(source);
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		setBusy(true);
		triggerAnim("action", "death");
		if (level() instanceof ServerLevel level) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_DEATH, SoundSource.HOSTILE, 4.0f, 0.3f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 2.5f, 1.4f);
			bossBar().clear(level);
		}
	}

	/** Three seconds: the death clip curls her legs up under her, then she bursts into ichor and is gone. */
	@Override
	protected void tickDeath() {
		++this.deathTime;
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (deathTime % 5 == 0) {
			level.sendParticles(ACID, getX(), getY() + 1.0, getZ(), 6, WIDTH * 0.4, 0.6, WIDTH * 0.4, 0);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
			level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.5, getZ(), 60, WIDTH * 0.5, 1.0, WIDTH * 0.5, 0.15);
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 40, WIDTH * 0.5, 1.0, WIDTH * 0.5, 0.05);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 3.0f, 0.3f);
			level.broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (bossBar != null && level() instanceof ServerLevel level) {
			bossBar.clear(level);
		}
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("QueenPhase", (byte) phase());
		tag.putBoolean("QueenIntroDone", introDone);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("QueenPhase")) {
			this.entityData.set(DATA_PHASE, (byte) Mth.clamp(tag.getByte("QueenPhase"), 1, 3));
		}
		introDone = tag.getBoolean("QueenIntroDone");
	}

	// ---------------------------------------------------------------- GeckoLib

	/** Every one-shot clip the server may trigger on the "action" controller. */
	private static final String[] ACTION_CLIPS = {
			"intro", "death", "shriek", "fang_lunge", "frenzy_lunge", "leg_sweep", "venom_spray", "web_volley", "egg_burst",
			"leap_windup", "slam", "ascend", "acid_rain",
	};
	/** Triggered clips that loop until the next trigger replaces them. */
	private static final String[] ACTION_LOOPS = { "leap_air", "hang", "drop" };

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<BroodQueen> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		for (String name : ACTION_CLIPS) {
			action.triggerableAnim(name, "death".equals(name)
					? RawAnimation.begin().thenPlayAndHold("animation.brood_queen." + name)
					: RawAnimation.begin().thenPlay("animation.brood_queen." + name));
		}
		for (String name : ACTION_LOOPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenLoop("animation.brood_queen." + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<BroodQueen> state) {
		if (isDeadOrDying() || this.entityData.get(DATA_BUSY)) {
			return PlayState.STOP; // a triggered clip on the "action" controller owns the pose
		}
		if (state.getLimbSwingAmount() > 0.04f || state.isMoving()) {
			// the gait is authored for her phase-1 pace; later phases step quicker
			state.getController().setAnimationSpeed(phase() == 3 ? 1.5 : phase() == 2 ? 1.25 : 1.0);
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.brood_queen.walk"));
		}
		state.getController().setAnimationSpeed(1.0);
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.brood_queen.idle"));
	}
}
