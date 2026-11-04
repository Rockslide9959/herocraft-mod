package com.projecthero.mod.behemoth.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.behemoth.BehemothCombat;
import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE ABYSSAL BEHEMOTH -- a very rare, endgame Nether world boss (spec: a "massively enhanced, mutated,
 * ancient Ghast-like creature", never a re-skinned Ghast). Fully custom GeckoLib model/animations
 * ({@code geo/abyssal_behemoth.geo.json}), fully custom flight and combat AI -- it registers no vanilla
 * goals at all (see {@link #registerGoals}), the same "a boss this size cannot afford a vanilla A*
 * search" discipline {@code TitanEntity} already established for this mod.
 *
 * <h2>Movement</h2>
 * {@link #aiStep} decides <em>where it wants to be</em> (a hover point relative to its target, shaped
 * by {@link #phase}/{@link #lowAltitudeTicks}); {@link #travel} is the only place that turns that into
 * real velocity, by direct steering (no navigation, no path). This mirrors the AI/physics split vanilla
 * flying mobs already use, just written out explicitly instead of inherited.
 *
 * <h2>Combat</h2>
 * One tick-driven state machine ({@link #chooseAbility}/{@link #tickAbility}), exactly like
 * {@code TitanEntity}'s own {@code Attack} enum -- windup, active, recovery, one shared cooldown map,
 * one shared global lock so two abilities can never overlap. The actual effect of each ability (damage,
 * particles, projectiles) lives in {@link BehemothCombat}; this class only ever decides which one to run
 * and when.
 */
public class AbyssalBehemothEntity extends Monster implements GeoEntity {
	/** Bounding-box size in blocks -- ~2.25x a vanilla Ghast's 4x4x4. A fixed constant, like {@code TitanEntity.SCALE}:
	 *  an {@link EntityType}'s hit-box cannot change at runtime, so this cannot be a live-reloadable config value. */
	public static final float SIZE = 9.0f;
	/** The reference height the geo model itself was built at (blocks) -- {@code BehemothRenderer} scales
	 *  the whole model by {@code SIZE / MODEL_HEIGHT}, the same trick {@code TitanFormRenderer} uses. */
	public static final float MODEL_HEIGHT = 4.5f; // v0.17.2 rebuild: tentacle tips (y=0) to horn tips (72 px)

	public enum Ability { FIREBALL, BARRAGE, MAGMA_RAIN, BEAM, CINDER_TETHER, NETHERSTORM, HELLWIND, SOVEREIGN_DESCENT, SKYFALL }

	private enum Stage { NONE, WINDUP, ACTIVE, RECOVER }

	private record Task(long due, Runnable run) {
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final List<Task> tasks = new ArrayList<>();
	private final Map<Ability, Long> abilityReadyAt = new EnumMap<>(Ability.class);

	private EventBossBar bossBar;
	private int phase = 1;
	private boolean enraged;
	private int combatTicks;

	private Ability activeAbility;
	private Stage stage = Stage.NONE;
	/** v0.15.19: the shared threat table -- it turns on whoever is actually hurting it; an ability being cast finishes first. */
	private final BossThreat threat = new BossThreat(this).filter(this::isValidTarget).holdWhile(() -> activeAbility != null && stage != Stage.NONE);
	private int stageTicks;
	private Ability lastAbility;
	private long globalLockUntil;

	/** >0 while descended for melee (Cinder Tether pull or Sovereign Descent's Ground Assault window). */
	private int lowAltitudeTicks;
	private int netherstormTicksLeft;
	private int barrageShotsLeft;
	private int magmaImpactsLeft;
	private int skyfallShotsLeft;
	private double beamStartYaw;
	private double beamEndYaw;
	private int airborneTargetTicks;

	/** v0.17.2 sight-based aggro: ticks the current target has been out of sight, and where it was last seen. */
	private int unseenTicks;
	private Vec3 lastSeenAt;
	/** v0.17.2 Abyssal Lure: who called it in, and how much longer it hunts them out of sight. */
	private java.util.UUID luredBy;
	private int luredTicks;
	/** v0.17.2 unstick: ticks wedged in terrain / ticks barely moving while it wants to. */
	private int wedgedTicks;
	private int stuckTicks;
	/** v0.17.2: open air above where it wants to hover (re-measured every 10 ticks). */
	private double headroom = 64.0;
	/** v0.17.2: how fast it wanted to go before steering, and how far its hover point was (the stuck test). */
	private double wantedSpeed;
	private double wantedDistance;

	public AbyssalBehemothEntity(EntityType<? extends AbyssalBehemothEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
	}

	public static AttributeSupplier.Builder createAttributes() {
		var stats = BehemothConfig.stats();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, stats.health)
				.add(Attributes.ATTACK_DAMAGE, stats.meleeDamage)
				.add(Attributes.MOVEMENT_SPEED, stats.flightSpeed)
				.add(Attributes.FOLLOW_RANGE, stats.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance);
	}

	// ---------------------------------------------------------------- no vanilla AI at all

	@Override
	protected void registerGoals() {
		// Deliberately empty: no vanilla Goal ever runs any pathfinding search for this boss (see class
		// javadoc). Everything -- targeting, movement, attacks -- is driven from #aiStep/#travel below.
	}

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
		return new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level) {
			@Override
			public boolean isStableDestination(net.minecraft.core.BlockPos pos) {
				return true; // never actually pathfinds -- only exists because Mob requires a non-null navigation
			}
		};
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean isBaby() {
		return false;
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
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(SIZE, SIZE);
	}

	@Override
	public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
		return false; // no vanilla contact-melee -- melee only ever happens through Ground Attack (BehemothCombat)
	}

	// ---------------------------------------------------------------- incoming damage

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		super.actuallyHurt(source, amount);
		if (!level().isClientSide()) {
			threat.record(source, amount);
		}
		if (this.random.nextInt(3) == 0) {
			triggerAnim("action", "hit");
		}
		// Refresh the boss bar the instant a hit lands, rather than waiting up to a tick for the next
		// aiStep -- players reported the bar feeling like it lagged behind the boss's real health.
		if (level() instanceof ServerLevel server) {
			updateBossBar(server);
		}
	}

	// ---------------------------------------------------------------- per-tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		syncMaxHealthWithConfig();
		acquireTarget(server);
		updatePhaseAndEnrage(server);
		tickAirborneTracking();
		runScheduledTasks(server);
		if (lowAltitudeTicks > 0) {
			lowAltitudeTicks--;
		}
		if (netherstormTicksLeft > 0) {
			netherstormTicksLeft--;
			if (tickCount % 10 == 0) {
				BehemothCombat.netherstormPulse(this);
			}
		}
		tickCombat(server);
		updateBossBar(server);
		ambientAura(server);
	}

	/**
	 * v0.13.6: a Behemoth loaded from disk restores its <em>saved</em> {@code MAX_HEALTH} attribute from
	 * NBT, which is whatever the config said when it first spawned -- not today's config. Every past
	 * balance pass that lowered {@link BehemothConfig}'s health (30000 -> 3000 -> 1000) therefore did
	 * nothing for a Behemoth that already existed in a save: it kept its old, far higher max forever, so
	 * real hits landed but barely dented a bar sized for a much bigger number -- reading as "doesn't take
	 * real damage." Reconciling the live attribute to the current config on every tick (current health
	 * rescaled proportionally, so a hurt boss doesn't get topped back up) fixes existing saves too, not
	 * just new spawns.
	 */
	private void syncMaxHealthWithConfig() {
		var maxHealthAttr = getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttr == null) {
			return;
		}
		double configured = BehemothConfig.stats().health;
		double current = maxHealthAttr.getBaseValue();
		if (Math.abs(current - configured) < 1.0e-3) {
			return;
		}
		float frac = current > 0.0 ? getHealth() / (float) current : 1.0f;
		maxHealthAttr.setBaseValue(configured);
		setHealth((float) Mth.clamp(configured * frac, 1.0, configured));
	}

	private void ambientAura(ServerLevel server) {
		if (tickCount % 6 != 0) {
			return;
		}
		double w = getBbWidth();
		Vector3f color = phase >= 3 ? new Vector3f(1.0f, 0.25f, 0.05f) : new Vector3f(0.85f, 0.15f, 0.35f);
		for (int i = 0; i < (phase >= 2 ? 3 : 1); i++) {
			double ox = (server.random.nextDouble() - 0.5) * w;
			double oy = server.random.nextDouble() * getBbHeight();
			double oz = (server.random.nextDouble() - 0.5) * w;
			server.sendParticles(new DustParticleOptions(color, 2.6f), getX() + ox, getY() + oy, getZ() + oz, 1, 0.0, 0.01, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- targeting (spec 26)

	/** v0.15.19: its threat table (tests / debug). */
	public BossThreat threat() {
		return threat;
	}

	/**
	 * v0.17.2 (user: "make it so that he actually spawns in and doesnt just get stuck in blocks and randomly attack players"):
	 * it picks a fight for a REASON. It used to take the nearest player within 90 blocks with no sight check at all, so it
	 * hunted people through solid netherrack (and, spawned on the bedrock roof, everyone under it). Now a fight starts only
	 * when it <em>sees</em> a player within {@code detectionRange} (40), when someone hurts it (the threat table), or when
	 * it is lured ({@link #lure}). A target it cannot see for {@code loseSightTicks} -- who is not hurting it and did not lure
	 * it -- is dropped, and while it cannot see its target it hunts the last place it saw them and never attacks
	 * ({@link #canStrike}).
	 */
	private void acquireTarget(ServerLevel server) {
		// v0.15.19: whoever is hurting it most takes its attention (BossThreat: damage-built, decaying, 20% margin to
		// switch). The sight scan below is only the fallback for an empty table.
		threat.tick();
		var stats = BehemothConfig.stats();
		if (luredTicks > 0) {
			luredTicks--;
		}
		LivingEntity current = getTarget();
		if (current != null) {
			boolean keep = current.isAlive() && isValidTarget(current) && current.level() == level()
					&& current.distanceToSqr(this) <= stats.followRange * stats.followRange;
			if (keep) {
				if (getSensing().hasLineOfSight(current)) {
					unseenTicks = 0;
					lastSeenAt = current.position();
				} else {
					unseenTicks++;
					boolean lured = luredTicks > 0 && current.getUUID().equals(luredBy);
					if (unseenTicks > stats.loseSightTicks && !lured && threat.threatOf(current) < 1.0f) {
						keep = false;
					}
				}
			}
			if (keep) {
				return;
			}
			setTarget(null);
			unseenTicks = 0;
			lastSeenAt = null;
		}
		LivingEntity best = null;
		double bestDist = stats.detectionRange * stats.detectionRange;
		for (Player p : server.players()) {
			if (!isValidTarget(p)) {
				continue;
			}
			double d = p.distanceToSqr(this);
			if (d < bestDist && getSensing().hasLineOfSight(p)) {
				bestDist = d;
				best = p;
			}
		}
		if (best != null) {
			setTarget(best);
			unseenTicks = 0;
			lastSeenAt = best.position();
		}
	}

	/** v0.17.2: it may only start (or land) an attack on a target it can see, within {@code attackRange}. */
	public boolean canStrike(LivingEntity target) {
		double r = BehemothConfig.stats().attackRange;
		return target != null && target.isAlive() && target.level() == level() && distanceToSqr(target) <= r * r
				&& getSensing().hasLineOfSight(target);
	}

	/**
	 * v0.17.2 Abyssal Lure: it was called in by {@code summoner} -- it hunts them for {@code lureHuntTicks} even out of sight
	 * (it still only attacks what it can see).
	 */
	public void lure(LivingEntity summoner) {
		luredBy = summoner.getUUID();
		luredTicks = BehemothConfig.world().lureHuntTicks;
		lastSeenAt = summoner.position();
		unseenTicks = 0;
		if (isValidTarget(summoner)) {
			setTarget(summoner);
		}
	}

	public boolean isLured() {
		return luredTicks > 0;
	}

	private boolean isValidTarget(LivingEntity e) {
		return e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Players within {@code radius} of {@code target} -- used to prefer group-hitting abilities (spec 26). */
	private int countNearby(LivingEntity target, double radius) {
		return level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(radius),
				this::isValidTarget).size();
	}

	/** Ticks every server tick regardless of the combat state machine, so "stayed airborne too long"
	 *  (spec 9: Skyfall) is measured in real time, not in however often {@link #chooseAbility} happens to run. */
	private void tickAirborneTracking() {
		LivingEntity target = getTarget();
		boolean airborne = target != null && !target.onGround() && target.getY() - groundLevelBelow(target) > 4.0;
		airborneTargetTicks = airborne ? airborneTargetTicks + 1 : 0;
	}

	// ---------------------------------------------------------------- phases + enrage (spec 17-20)

	private void updatePhaseAndEnrage(ServerLevel server) {
		double frac = getHealth() / getMaxHealth();
		var ph = BehemothConfig.phases();
		int newPhase = frac <= ph.phase3HealthFraction ? 3 : frac <= ph.phase2HealthFraction ? 2 : 1;
		if (newPhase != phase) {
			phase = newPhase;
			triggerAnim("action", "phase_transition");
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 0.6f);
			BehemothCombat.hellwind(this); // the roar itself pushes everyone back a little -- reads as a real escalation
		}
		if (getTarget() != null) {
			combatTicks++;
			if (!enraged && combatTicks > ph.enrageTicks) {
				enraged = true;
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.8f);
			}
		} else if (combatTicks > 0) {
			combatTicks = Math.max(0, combatTicks - 5); // cools down, doesn't instantly reset -- leaving briefly shouldn't wipe enrage progress
		}
	}

	private double cooldownMultiplier() {
		var ph = BehemothConfig.phases();
		double m = phase == 3 ? ph.phase3CooldownMultiplier : phase == 2 ? ph.phase2CooldownMultiplier : 1.0;
		return enraged ? m * ph.enrageCooldownMultiplier : m;
	}

	private double speedMultiplier() {
		var ph = BehemothConfig.phases();
		double m = phase == 3 ? ph.phase3SpeedMultiplier : phase == 2 ? ph.phase2SpeedMultiplier : 1.0;
		return enraged ? m * ph.enrageSpeedMultiplier : m;
	}

	private float damageMultiplier() {
		var ph = BehemothConfig.phases();
		return (float) (phase == 3 ? ph.phase3DamageMultiplier : phase == 2 ? ph.phase2DamageMultiplier : 1.0);
	}

	// ---------------------------------------------------------------- movement (AI decision only -- see #travel)

	private Vec3 desiredVelocity = Vec3.ZERO;

	private void tickMovementIntent(ServerLevel server) {
		if (activeAbility == Ability.SOVEREIGN_DESCENT && stage == Stage.ACTIVE) {
			return; // the dive itself drives velocity directly in #tickAbility -- don't fight it here
		}
		LivingEntity target = getTarget();
		// v0.17.2: out of sight, it hunts the last place it saw the target, not the target itself (no homing through walls)
		// (a lure is the exception: it was called to its summoner, and comes for them)
		boolean hunting = target != null && luredTicks > 0 && target.getUUID().equals(luredBy);
		Vec3 focus = target == null ? null : unseenTicks == 0 || lastSeenAt == null || hunting ? target.position() : lastSeenAt;
		Vec3 desiredPos;
		if (focus == null) {
			desiredPos = position().add(0, Math.sin(tickCount * 0.02) * 0.02, 0); // idle hover in place
		} else {
			Vec3 toSelfFlat = position().subtract(focus);
			toSelfFlat = new Vec3(toSelfFlat.x, 0, toSelfFlat.z);
			double horizDist = toSelfFlat.length();
			double preferredHoriz = lowAltitudeTicks > 0 ? 9.0 : 14.0;
			Vec3 dirFlat = horizDist > 1.0e-3 ? toSelfFlat.scale(1.0 / horizDist) : new Vec3(1, 0, 0);
			// slow orbit around the target rather than sitting dead still
			double orbit = tickCount * 0.01;
			Vec3 tangent = new Vec3(-dirFlat.z, 0, dirFlat.x).scale(Math.sin(orbit) * 3.0);
			Vec3 spot = focus.add(dirFlat.scale(preferredHoriz)).add(tangent);
			// v0.17.2: hover only as high as the cavern lets it -- it used to aim 12 blocks over the target whatever the
			// ceiling, and pressed its 9-block body into the rock of any cave lower than ~21 blocks
			if (tickCount % 10 == 0 || headroom > 63.0) {
				headroom = measureHeadroom(spot);
			}
			double wantAlt = lowAltitudeTicks > 0 ? 4.0 : BehemothConfig.stats().preferredAerialAltitude;
			double altitude = Mth.clamp(headroom - SIZE - 1.5, lowAltitudeTicks > 0 ? -2.0 : 0.5, wantAlt);
			desiredPos = spot.add(0, altitude, 0);
		}
		Vec3 toDesired = desiredPos.subtract(position());
		double maxSpeed = (target != null ? BehemothConfig.stats().aggressiveFlightSpeed : BehemothConfig.stats().flightSpeed) * speedMultiplier();
		Vec3 accel = toDesired.length() > 1.0e-3 ? toDesired.normalize().scale(Math.min(toDesired.length() * 0.06, maxSpeed)) : Vec3.ZERO;
		Vec3 wanted = desiredVelocity.scale(0.82).add(accel.scale(0.3));
		if (wanted.length() > maxSpeed) {
			wanted = wanted.normalize().scale(maxSpeed);
		}
		wantedSpeed = wanted.length();
		wantedDistance = toDesired.length();
		wanted = steer(wanted);
		desiredVelocity = wanted;
		// face the target while it can see it (it is about to breathe fire at them), else where it flies
		Vec3 face = target != null && unseenTicks == 0 ? target.position().subtract(position()) : desiredVelocity;
		if (face.horizontalDistance() > 1.0e-3) {
			double yaw = Math.toDegrees(Math.atan2(-face.x, face.z));
			float turned = Mth.approachDegrees(getYRot(), (float) yaw, 6.0f);
			setYRot(turned);
			setYBodyRot(turned);
			setYHeadRot(turned);
		}
	}

	/**
	 * v0.17.2: obstacle avoidance. The old probe only ever nudged it UP when its path was blocked -- so under a cavern
	 * ceiling (every Nether cave) it ground its box into the rock and stayed there. Now the box is swept ahead along the
	 * wanted velocity; if that hits terrain it tries the same speed turned 35/70/110 degrees either way, then straight up,
	 * then straight down, and takes the free direction closest to where it wanted to go. Nothing free: it stops (and the
	 * unstick in {@link #tickUnstick} takes over if that lasts).
	 */
	private Vec3 steer(Vec3 wanted) {
		double speed = wanted.length();
		if (speed < 1.0e-4 || pathFree(wanted)) {
			return wanted;
		}
		Vec3 flat = new Vec3(wanted.x, 0, wanted.z);
		double flatSpeed = Math.max(flat.length(), speed * 0.6);
		Vec3 flatDir = flat.lengthSqr() > 1.0e-6 ? flat.normalize() : Vec3.directionFromRotation(0, getYRot());
		List<Vec3> options = new ArrayList<>();
		for (double deg : new double[] { 35, -35, 70, -70, 110, -110 }) {
			Vec3 d = flatDir.yRot((float) Math.toRadians(deg)).scale(flatSpeed);
			options.add(new Vec3(d.x, wanted.y * 0.5, d.z));
		}
		options.add(new Vec3(wanted.x * 0.25, speed, wanted.z * 0.25));
		options.add(new Vec3(wanted.x * 0.25, -speed, wanted.z * 0.25));
		Vec3 best = null;
		double bestDot = -2.0;
		Vec3 wantDir = wanted.normalize();
		for (Vec3 o : options) {
			if (!pathFree(o)) {
				continue;
			}
			double dot = o.normalize().dot(wantDir);
			if (dot > bestDot) {
				bestDot = dot;
				best = o;
			}
		}
		return best != null ? best : Vec3.ZERO;
	}

	/** Its box, swept 4 ticks (at least 1.5 blocks) along {@code v}, touches no terrain. */
	private boolean pathFree(Vec3 v) {
		Vec3 probe = v.normalize().scale(Math.max(1.5, v.length() * 4.0));
		var box = getBoundingBox().deflate(0.05);
		return level().noCollision(this, box.expandTowards(probe)) && (!(level() instanceof ServerLevel s) || belowRoof(s, box.move(probe)));
	}

	/** While its target is under the Nether's bedrock roof, it never drifts up through/above that roof. */
	private boolean belowRoof(ServerLevel server, net.minecraft.world.phys.AABB box) {
		int roof = server.getMinBuildHeight() + server.getLogicalHeight();
		LivingEntity target = getTarget();
		return getY() >= roof || (target != null && target.getY() >= roof) || box.maxY < roof;
	}

	/**
	 * Open air above {@code spot} (blocks, up to 40), sampled at its centre and at 4 points round its 9-block width. A column
	 * that starts inside a block (the spot is beside a pillar or a wall) is measured from the first air above it, and one that
	 * is solid all the way up is left out -- otherwise one pillar beside its target read as "no headroom" and it sank to the floor.
	 */
	private double measureHeadroom(Vec3 spot) {
		int roof = level().getMinBuildHeight() + level().dimensionType().logicalHeight();
		double half = SIZE * 0.45;
		double best = 40.0;
		for (double[] o : new double[][] { { 0, 0 }, { half, 0 }, { -half, 0 }, { 0, half }, { 0, -half } }) {
			net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
			int x = Mth.floor(spot.x + o[0]), z = Mth.floor(spot.z + o[1]);
			int y0 = Mth.floor(spot.y) + 1;
			int dy = 0;
			while (dy < 16 && isSolidOrRoof(p.set(x, y0 + dy, z), spot.y < roof, roof)) {
				dy++;
			}
			if (dy >= 16) {
				continue;
			}
			int start = dy;
			double open = 40.0;
			for (; dy < 40; dy++) {
				if (isSolidOrRoof(p.set(x, y0 + dy, z), spot.y < roof, roof)) {
					open = dy - start;
					break;
				}
			}
			best = Math.min(best, open);
		}
		return best;
	}

	private boolean isSolidOrRoof(net.minecraft.core.BlockPos p, boolean underRoof, int roof) {
		return (underRoof && p.getY() >= roof) || !level().getBlockState(p).getCollisionShape(level(), p).isEmpty();
	}

	/**
	 * v0.17.2 unstick: if its box ends up overlapping terrain (a block placed into it, a spawn by some other mod / command
	 * into rock) for a second, or it has wanted to move but barely moved for 3 s, it shakes itself free -- a puff of smoke and
	 * it is in the nearest open pocket ({@link BehemothSpawner#findOpenNear}). Never during the dive.
	 */
	private void tickUnstick(ServerLevel server, Vec3 before) {
		if (activeAbility == Ability.SOVEREIGN_DESCENT && stage == Stage.ACTIVE) {
			return;
		}
		boolean wedged = !level().noCollision(this, getBoundingBox().deflate(0.3));
		wedgedTicks = wedged ? wedgedTicks + 1 : 0;
		double moved = position().distanceTo(before);
		boolean trying = wantedSpeed > 0.15 && wantedDistance > 6.0;
		stuckTicks = trying && moved < 0.03 ? stuckTicks + 1 : Math.max(0, stuckTicks - 2);
		if (wedgedTicks > 20 || stuckTicks > 60) {
			Vec3 open = com.projecthero.mod.behemoth.BehemothSpawner.findOpenNear(server, position(), 24.0);
			if (open != null && open.distanceToSqr(position()) > 1.0e-4) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + SIZE * 0.5, getZ(), 30, SIZE * 0.4, SIZE * 0.4, SIZE * 0.4, 0.02);
				moveTo(open.x, open.y, open.z, getYRot(), getXRot());
				desiredVelocity = Vec3.ZERO;
				server.sendParticles(ParticleTypes.LARGE_SMOKE, open.x, open.y + SIZE * 0.5, open.z, 30, SIZE * 0.4, SIZE * 0.4, SIZE * 0.4, 0.02);
			}
			wedgedTicks = 0;
			stuckTicks = 0;
		}
	}

	/** Test hook: wedged / stuck counters (v0.17.2). */
	public int wedgedTicksForTest() {
		return wedgedTicks;
	}

	@Override
	public void travel(Vec3 movementInput) {
		if (level().isClientSide()) {
			super.travel(movementInput);
			return;
		}
		Vec3 before = position();
		tickMovementIntent((ServerLevel) level());
		setDeltaMovement(desiredVelocity);
		move(MoverType.SELF, getDeltaMovement());
		tickUnstick((ServerLevel) level(), before);
	}

	/** v0.17.2: it is never pushed around by other mobs (a 9-block flier shoved into a wall by a zombie pack). */
	@Override
	public boolean isPushable() {
		return false;
	}

	/** v0.17.2: no suffocation -- it unsticks itself instead ({@link #tickUnstick}); a world boss dying in a wall is a lost fight. */
	@Override
	public boolean isInWall() {
		return false;
	}

	// ---------------------------------------------------------------- combat state machine (spec 6, 17-19, 26)

	private void tickCombat(ServerLevel server) {
		LivingEntity target = getTarget();
		if (stage != Stage.NONE) {
			tickAbility(server, target);
			return;
		}
		if (target == null || server.getGameTime() < globalLockUntil || com.projecthero.mod.quirk.erasure.ErasureMobs.specialsHeld(this)) { // v0.16.7: Erased
			return;
		}
		if (!canStrike(target)) {
			return; // v0.17.2: never an attack on someone it cannot see (it used to fire through walls at anyone in 90 blocks)
		}
		Ability choice = chooseAbility(server, target);
		if (choice == null) {
			return;
		}
		startAbility(server, choice);
	}

	private boolean ready(ServerLevel server, Ability a) {
		Long t = abilityReadyAt.get(a);
		return t == null || server.getGameTime() >= t;
	}

	/**
	 * Weighted contextual selection (spec 6/26): builds the list of abilities that are off cooldown and
	 * make sense right now, then picks one at random, favouring whichever the situation calls for and
	 * never immediately repeating {@link #lastAbility} unless it is the only option.
	 */
	private Ability chooseAbility(ServerLevel server, LivingEntity target) {
		boolean targetAirborne = !target.onGround() && target.getY() - groundLevelBelow(target) > 4.0;
		boolean targetGrounded = target.onGround();
		boolean underneath = Math.abs(target.getX() - getX()) < 3.0 && Math.abs(target.getZ() - getZ()) < 3.0 && target.getY() < getY();
		int groupSize = countNearby(target, 12.0);
		boolean lowHealth = getHealth() / getMaxHealth() < 0.25;

		List<Ability> weighted = new ArrayList<>();
		add(weighted, server, Ability.FIREBALL, 3);
		add(weighted, server, Ability.BARRAGE, targetGrounded ? 1 : 2);
		add(weighted, server, Ability.MAGMA_RAIN, targetGrounded ? 3 : 1, groupSize >= 2 ? 3 : 0);
		add(weighted, server, Ability.BEAM, targetAirborne ? 3 : 1, lowHealth ? 2 : 0);
		add(weighted, server, Ability.CINDER_TETHER, targetGrounded ? 3 : 1);
		add(weighted, server, Ability.NETHERSTORM, groupSize >= 2 ? 3 : 1);
		add(weighted, server, Ability.HELLWIND, underneath ? 4 : 1);
		add(weighted, server, Ability.SOVEREIGN_DESCENT, targetGrounded ? 3 : 1, underneath ? 2 : 0);
		if (airborneTargetTicks > BehemothConfig.abilities().skyfallAirborneTicksTrigger) {
			add(weighted, server, Ability.SKYFALL, 5);
		}
		if (weighted.isEmpty()) {
			return null;
		}
		if (weighted.size() > 1) {
			weighted.removeIf(a -> a == lastAbility && weighted.stream().anyMatch(o -> o != lastAbility));
		}
		return weighted.get(server.random.nextInt(weighted.size()));
	}

	private void add(List<Ability> list, ServerLevel server, Ability a, int weight) {
		add(list, server, a, weight, 0);
	}

	private void add(List<Ability> list, ServerLevel server, Ability a, int weight, int bonus) {
		if (!ready(server, a)) {
			return;
		}
		for (int i = 0; i < weight + bonus; i++) {
			list.add(a);
		}
	}

	private void startAbility(ServerLevel server, Ability a) {
		activeAbility = a;
		lastAbility = a;
		stage = Stage.WINDUP;
		stageTicks = 0;
		globalLockUntil = server.getGameTime() + BehemothConfig.abilities().globalAbilityLockTicks;
		String anim = switch (a) {
			case FIREBALL -> "charge_fireball";
			case BARRAGE -> "hellfire_barrage";
			case MAGMA_RAIN -> "magma_rain";
			case BEAM -> "charge_beam";
			case CINDER_TETHER -> "cinder_tether";
			case NETHERSTORM -> "netherstorm";
			case HELLWIND -> "hellwind";
			case SOVEREIGN_DESCENT -> "sovereign_descent";
			case SKYFALL -> "hellfire_barrage";
		};
		triggerAnim("action", anim);
		if (a == Ability.MAGMA_RAIN) {
			magmaImpactsLeft = BehemothConfig.abilities().magmaRainCount;
		}
		if (a == Ability.BARRAGE || a == Ability.SKYFALL) {
			barrageShotsLeft = a == Ability.SKYFALL ? BehemothConfig.abilities().skyfallCount : BehemothConfig.abilities().hellfireCount;
		}
	}

	/** Windup -> active -> recovery for {@link #activeAbility}. Every ability's numbers live in {@link BehemothConfig}. */
	private void tickAbility(ServerLevel server, LivingEntity target) {
		var cfg = BehemothConfig.abilities();
		stageTicks++;
		switch (activeAbility) {
			case FIREBALL -> {
				if (stage == Stage.WINDUP && stageTicks >= 14) {
					if (canStrike(target)) {
						BehemothCombat.fireFireball(this, target);
					}
					enterRecovery(server, Ability.FIREBALL, 20, cfg.fireballCooldownTicks);
				}
			}
			case BARRAGE, SKYFALL -> {
				if (stage == Stage.WINDUP && stageTicks >= 10) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
				} else if (stage == Stage.ACTIVE) {
					int total = activeAbility == Ability.SKYFALL ? cfg.skyfallCount : cfg.hellfireCount;
					if (stageTicks % 5 == 0 && barrageShotsLeft > 0) {
						int index = total - barrageShotsLeft;
						if (!canStrike(target)) {
							// v0.17.2: target ducked out of sight -- the shot is held back, not fired into the rock
						} else if (activeAbility == Ability.SKYFALL) {
							BehemothCombat.fireSkyfallVolley(this, target, index);
						} else {
							BehemothCombat.fireBarrageShot(this, target, index, total);
						}
						barrageShotsLeft--;
					}
					if (barrageShotsLeft <= 0) {
						enterRecovery(server, activeAbility, 15,
								activeAbility == Ability.SKYFALL ? cfg.skyfallCooldownTicks : cfg.hellfireCooldownTicks);
					}
				}
			}
			case MAGMA_RAIN -> {
				if (stage == Stage.WINDUP && stageTicks >= 16) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
					if (target != null) {
						for (int i = 0; i < cfg.magmaRainCount; i++) {
							double ang = server.random.nextDouble() * Math.PI * 2;
							double dist = server.random.nextDouble() * cfg.magmaRainRadius;
							Vec3 point = target.position().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
							Vec3 ground = groundedPoint(point);
							scheduleWarned(server, ground);
						}
					}
				}
				if (magmaImpactsLeft <= 0 && stage == Stage.ACTIVE) {
					enterRecovery(server, Ability.MAGMA_RAIN, 10, cfg.magmaRainCooldownTicks);
				}
			}
			case BEAM -> {
				if (stage == Stage.WINDUP) {
					if (stageTicks == 1 && target != null) {
						Vec3 dir = target.position().subtract(position());
						beamStartYaw = Math.atan2(dir.x, dir.z) - Math.toRadians(35);
						beamEndYaw = Math.atan2(dir.x, dir.z) + Math.toRadians(35);
					}
					if (stageTicks >= cfg.beamChargeTicks) {
						stage = Stage.ACTIVE;
						stageTicks = 0;
						triggerAnim("action", "beam_fire");
						server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.6f);
					} else if (stageTicks % 4 == 0) {
						server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), (int) (6 + 10.0 * stageTicks / cfg.beamChargeTicks), 0.6, 0.6, 0.6, 0.05);
					}
				} else if (stage == Stage.ACTIVE) {
					double t = Math.min(1.0, stageTicks / (double) cfg.beamFireTicks);
					double yaw = beamStartYaw + (beamEndYaw - beamStartYaw) * t;
					Vec3 dir = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
					BehemothCombat.beamTick(this, dir);
					if (stageTicks >= cfg.beamFireTicks) {
						enterRecovery(server, Ability.BEAM, 20, cfg.beamCooldownTicks);
					}
				}
			}
			case CINDER_TETHER -> {
				if (stage == Stage.WINDUP && stageTicks >= 20) {
					int hits = BehemothCombat.fireCinderTether(this);
					if (hits > 0) {
						lowAltitudeTicks = Math.max(lowAltitudeTicks, cfg.lowAltitudeTicks);
					}
					enterRecovery(server, Ability.CINDER_TETHER, 10, cfg.cinderTetherCooldownTicks);
				}
			}
			case NETHERSTORM -> {
				if (stage == Stage.WINDUP && stageTicks >= 20) {
					netherstormTicksLeft = cfg.netherstormDurationTicks;
					enterRecovery(server, Ability.NETHERSTORM, 5, cfg.netherstormCooldownTicks);
				}
			}
			case HELLWIND -> {
				if (stage == Stage.WINDUP && stageTicks >= 18) {
					BehemothCombat.hellwind(this);
					enterRecovery(server, Ability.HELLWIND, 15, cfg.hellwindCooldownTicks);
				}
			}
			case SOVEREIGN_DESCENT -> {
				if (stage == Stage.WINDUP && stageTicks >= 16) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
				} else if (stage == Stage.ACTIVE) {
					// a fast, visible dive rather than a teleport -- travel() keeps steering the body,
					// this just overrides the vertical component while the dive is in progress
					setDeltaMovement(getDeltaMovement().x * 0.5, -1.1, getDeltaMovement().z * 0.5);
					move(MoverType.SELF, getDeltaMovement());
					if (onGround() || stageTicks > 40) {
						BehemothCombat.sovereignDescentImpact(this);
						triggerAnim("action", "ground_land");
						lowAltitudeTicks = Math.max(lowAltitudeTicks, cfg.groundAssaultTicks);
						enterRecovery(server, Ability.SOVEREIGN_DESCENT, 20, cfg.sovereignDescentCooldownTicks);
					}
				}
			}
		}
	}

	private void enterRecovery(ServerLevel server, Ability a, int recoverTicks, int cooldownTicks) {
		abilityReadyAt.put(a, server.getGameTime() + (long) (cooldownTicks * cooldownMultiplier()));
		stage = Stage.RECOVER;
		stageTicks = 0;
		schedule(server, recoverTicks, () -> {
			stage = Stage.NONE;
			activeAbility = null;
		});
	}

	/** Snaps a horizontal point down onto the Nether floor beneath it (for Magma Rain's impact markers). */
	private Vec3 groundedPoint(Vec3 point) {
		// v0.17.2: a downward scan from the target's height (the heightmap is the Nether's bedrock roof)
		net.minecraft.core.BlockPos.MutableBlockPos p = net.minecraft.core.BlockPos.containing(point.x, point.y + 2, point.z).mutable();
		for (int i = 0; i < 24 && p.getY() > level().getMinBuildHeight(); i++) {
			if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
				return new Vec3(point.x, p.getY() + 1.0, point.z);
			}
			p.move(0, -1, 0);
		}
		return point;
	}

	/** v0.17.2: the first solid block under {@code e} (scanning down 48) -- the heightmap it used reads the bedrock ROOF in
	 *  the Nether, so "is the target airborne" never came out true there and Skyfall never fired. */
	private double groundLevelBelow(LivingEntity e) {
		net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos(Mth.floor(e.getX()), Mth.floor(e.getY()), Mth.floor(e.getZ()));
		for (int i = 0; i < 48 && p.getY() > level().getMinBuildHeight(); i++) {
			if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
				return p.getY() + 1.0;
			}
			p.move(0, -1, 0);
		}
		return e.getY() - 48.0;
	}

	private void scheduleWarned(ServerLevel server, Vec3 pos) {
		BehemothCombat.magmaWarning(this, pos);
		schedule(server, BehemothConfig.abilities().magmaRainWarningTicks, () -> {
			BehemothCombat.magmaImpact(this, pos);
			magmaImpactsLeft--;
		});
	}

	// ---------------------------------------------------------------- small per-entity task queue (mirrors AllMightAbilities)

	private void schedule(ServerLevel server, int delayTicks, Runnable run) {
		tasks.add(new Task(server.getGameTime() + Math.max(0, delayTicks), run));
	}

	private void runScheduledTasks(ServerLevel server) {
		if (tasks.isEmpty()) {
			return;
		}
		long now = server.getGameTime();
		for (Task t : new ArrayList<>(tasks)) {
			if (t.due() <= now) {
				tasks.remove(t);
				if (isAlive()) {
					t.run().run();
				}
			}
		}
	}

	// ---------------------------------------------------------------- boss bar (spec 4)

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
		bossBar().update(server, blockPosition(), Math.max(80.0, BehemothConfig.stats().detectionRange * 1.4),
				Component.translatable("entity.projecthero.abyssal_behemoth").withStyle(net.minecraft.ChatFormatting.DARK_RED),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- death (spec 25)

	private int deathTicks = -1;

	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		setTarget(null);
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		deathTicks = 60; // 3s cinematic collapse before removal
		triggerAnim("action", "death");
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 4.0f, 0.5f);
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5, getZ(), 60, getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.1);
			bossBar().clear(server);
			com.projecthero.mod.behemoth.BehemothSpawner.onKilled(server);
			dropLoot(server, source);
			dropXp(server, source);
		}
	}

	/**
	 * v0.14.23: this boss's {@link #die} never calls {@code super.die} (it plays its own long death), so vanilla's
	 * XP drop never ran and the {@code xpReward} was dead. Drop it here, under vanilla's own rule: only if a player
	 * hurt it recently, and only with {@code doMobLoot} on.
	 */
	private void dropXp(ServerLevel server, DamageSource source) {
		if (lastHurtByPlayerTime > 0 && server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) {
			net.minecraft.world.entity.ExperienceOrb.award(server, position(), getExperienceReward(server, source.getEntity()));
		}
	}

	private void dropLoot(ServerLevel server, DamageSource source) {
		var lootTable = server.getServer().reloadableRegistries().getLootTable(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						com.projecthero.mod.ProjectHeroMod.id("entities/abyssal_behemoth")));
		LootParams.Builder params = new LootParams.Builder(server)
				.withParameter(LootContextParams.THIS_ENTITY, this)
				.withParameter(LootContextParams.ORIGIN, position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withLuck(0);
		lootTable.getRandomItems(params.create(LootContextParamSets.ENTITY), this.getLootTableSeed(), stack -> spawnAtLocation(stack));
	}

	@Override
	public void tick() {
		super.tick();
		if (deathTicks < 0) {
			return;
		}
		if (level() instanceof ServerLevel server) {
			if (deathTicks % 6 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.4, getZ(),
						20, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.04);
			}
			if (--deathTicks <= 0) {
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<AbyssalBehemothEntity> action = new AnimationController<>(this, "action", 6, state -> PlayState.STOP);
		for (String name : new String[] { "charge_fireball", "fireball_attack", "hellfire_barrage", "charge_beam", "beam_fire",
				"magma_rain", "cinder_tether", "sovereign_descent", "ground_land", "ground_attack", "hellwind", "netherstorm",
				"hit", "phase_transition", "death" }) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.abyssal_behemoth." + name));
		}
		controllers.add(new AnimationController<>(this, "main", 8, this::mainPredicate));
		controllers.add(action);
	}

	private PlayState mainPredicate(AnimationState<AbyssalBehemothEntity> state) {
		if (isDeadOrDying() || deathTicks >= 0) {
			return PlayState.STOP; // the "action" controller's death trigger owns this instead
		}
		if (lowAltitudeTicks > 0) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.ground_idle"));
		}
		if (enraged) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.enraged_idle"));
		}
		if (getTarget() != null) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.fly"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.idle"));
	}

	// ---------------------------------------------------------------- persistence (phase/enrage survive a reload)

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Phase", phase);
		tag.putBoolean("Enraged", enraged);
		tag.putInt("CombatTicks", combatTicks);
		if (luredBy != null && luredTicks > 0) {
			tag.putUUID("LuredBy", luredBy);
			tag.putInt("LuredTicks", luredTicks);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		phase = tag.contains("Phase") ? tag.getInt("Phase") : 1;
		enraged = tag.getBoolean("Enraged");
		combatTicks = tag.getInt("CombatTicks");
		luredBy = tag.hasUUID("LuredBy") ? tag.getUUID("LuredBy") : null;
		luredTicks = tag.getInt("LuredTicks");
	}
}
