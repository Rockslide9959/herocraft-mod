package com.projecthero.mod.speedforce.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.event.boss.BossTargets;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.speedforce.BlackFlash;
import com.projecthero.mod.speedforce.BlackFlashConfig;
import com.projecthero.mod.speedforce.SpeedForceMod;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "i want the black flash to punch you out of the speed force and fight you in the real world, he should be
 * powerful and everything and a real boss fight with all of your abilities"): <b>the Black Flash</b>, the Speed Force's
 * reaper. He comes for a runner who stays too long in the Speed Force ({@code SpeedForceRealm}), punches them out, and
 * follows them into the real world, where the fight happens.
 *
 * <p>The user's skin on the player rig (wide arms). 600 health ({@link BlackFlashConfig}), armour 8, very fast, immune to
 * knockback, falls, suffocation and lightning, and he regenerates 2 health a second once nothing has hit him for 5 s. A
 * {@link ServerBossEvent} bar ("BLACK FLASH"), the shared {@link BossThreat} table (he turns on whoever is hurting him), and
 * his hits on a player are capped at 80% of their max health, as the other bosses' are.
 *
 * <p>His moves are the Speed Force Speedster's own, aimed at his target, each with its own cooldown and a wind-up
 * (black-and-red sparks gathering, a sound) to read and dodge: Blitz, Rapid Assault, Mach Punch, Lightning Throw (black
 * bolts that chain), Speed Vortex, Tornado Arms, Speed Sweep, Phase (through walls, when he can't see you), Time Remnant (a
 * copy that fights beside him for 10 s) and -- under half health -- his own Flash Time: the world crawls for everyone near
 * him, then an Infinite Mass Punch, after which he stands spent for 2 s.
 *
 * <p>Rules (see {@link BlackFlash}): one per player, gone if that player dies or logs out or after 5 minutes with nothing to
 * fight; never saved (the entity type is {@code noSave}). Beaten, he dissolves into black lightning, drops a Speed Force
 * Shard and XP, and the runner leaves the Speed Force undrained.
 */
public class BlackFlashEntity extends Monster {
	public static final byte ACTION_NONE = 0, ACTION_WINDUP = 1, ACTION_FLURRY = 2, ACTION_THROW = 3, ACTION_ARMS = 4,
			ACTION_VORTEX = 5, ACTION_PHASE = 6, ACTION_MASS = 7, ACTION_SPENT = 8, ACTION_CHARGE = 9, ACTION_CROUCH = 10,
			ACTION_PUNCH = 11, ACTION_FLASH = 12;

	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.06f, 0.05f), 1.3f);
	public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.07f), 1.6f);

	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(BlackFlashEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_REMNANT = SynchedEntityData.defineId(BlackFlashEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_CINEMATIC = SynchedEntityData.defineId(BlackFlashEntity.class, EntityDataSerializers.BOOLEAN);

	/** The moves (his brain is one state machine, as Carnage's). */
	public enum Move {
		NONE, BLITZ, RAPID, MACH, LIGHTNING, VORTEX, ARMS, SWEEP, PHASE, REMNANT, FLASH_TIME, MASS, SPENT
	}

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.black_flash.bar")
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final BossThreat threat = new BossThreat(this).range(64.0).holdWhile(() -> this.move != Move.NONE);

	/** The runner he came for. */
	private UUID hunted;
	private int noTargetTicks;
	/** v0.17.1: ticks his runner has been in another dimension; the way his run is heading (the chunk ahead is loaded). */
	private int otherLevelTicks;
	private Vec3 runDir;
	private int lastHurtTick = -10_000;
	private boolean defeated;
	private boolean phase2Announced;
	/** A remnant: how long it has left (ticks). */
	private int remnantLife;
	private UUID remnantOwner;
	private int remnantId = -1;

	// the brain
	private Move move = Move.NONE;
	private int moveTick;
	private LivingEntity moveTarget;
	private Vec3 aim;
	private Vec3 anchor;
	private final List<LivingEntity> sweepList = new ArrayList<>();
	private int meleeCd = 20;
	private int globalCd = 30;
	private int zipCd = 40;
	private int blitzCd = 60, rapidCd = 40, machCd = 140, lightningCd = 100, vortexCd = 260, armsCd = 200, sweepCd = 240,
			phaseCd = 0, remnantCd = 300, flashCd = 0;

	public BlackFlashEntity(EntityType<? extends BlackFlashEntity> type, Level level) {
		super(type, level);
		this.xpReward = BlackFlashConfig.get().boss.xpReward;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 600.0)
				.add(Attributes.MOVEMENT_SPEED, 0.42)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 1024.0);
	}

	/** The boss: his health, armour and speed from the config, bound to the runner he hunts. */
	public void configure(UUID huntedPlayer) {
		BlackFlashConfig.Boss b = BlackFlashConfig.get().boss;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(b.health);
		getAttribute(Attributes.ARMOR).setBaseValue(b.armor);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(b.movementSpeed);
		setHealth((float) b.health);
		this.hunted = huntedPlayer;
	}

	/**
	 * v0.17.1 (user, the Black Flash Lure): summoned on purpose -- +50% health, richer loot, and a win costs his runner only
	 * the ordinary drain (no 20-minute loss of the Speed Force).
	 */
	private boolean lured;

	public void makeLured() {
		this.lured = true;
		double hp = getAttribute(Attributes.MAX_HEALTH).getBaseValue() * com.projecthero.mod.speedforce.SpeedForceConfig.LURE_HEALTH_MULTIPLIER;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public boolean isLured() {
		return lured;
	}

	/** A Time Remnant of {@code owner}: weak, short-lived, no bar, no drops. */
	public void configureRemnant(BlackFlashEntity owner) {
		BlackFlashConfig.Moves m = BlackFlashConfig.get().moves;
		entityData.set(DATA_REMNANT, true);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(m.remnantHealth);
		setHealth((float) m.remnantHealth);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BlackFlashConfig.get().boss.movementSpeed);
		this.remnantLife = m.remnantTicks;
		this.remnantOwner = owner.getUUID();
		this.hunted = owner.hunted;
		this.lured = owner.lured;
		this.xpReward = 0;
		setTarget(owner.getTarget());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
		builder.define(DATA_REMNANT, false);
		builder.define(DATA_CINEMATIC, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	// ---------------------------------------------------------------- accessors

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public void setAction(byte a) {
		if (entityData.get(DATA_ACTION) != a) {
			entityData.set(DATA_ACTION, a);
		}
	}

	public boolean isRemnant() {
		return entityData.get(DATA_REMNANT);
	}

	/** The in-realm charge (he only runs and punches; the realm drives him): untouchable, no bar, no AI. */
	public boolean isCinematic() {
		return entityData.get(DATA_CINEMATIC);
	}

	public void setCinematic(boolean c) {
		entityData.set(DATA_CINEMATIC, c);
		setNoAi(c);
		noPhysics = c;
		setNoGravity(c);
	}

	public UUID hunted() {
		return hunted;
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bossEvent() {
		return bossBar;
	}

	public Move currentMove() {
		return move;
	}

	public boolean defeated() {
		return defeated;
	}

	public boolean phase2() {
		return getHealth() <= getMaxHealth() * 0.5f;
	}

	/** Test / harness hook: start move {@code name} (a {@link Move} name) on his current target now. */
	public boolean debugStartMove(String name) {
		LivingEntity t = getTarget();
		if (t == null) {
			return false;
		}
		start(Move.valueOf(name), t);
		return true;
	}

	/** Ticks in a row he has had nothing to fight. */
	public int noTargetTicks() {
		return noTargetTicks;
	}

	/** Test hook: as if he had had nothing to fight for {@code ticks}. */
	public void ageNoTargetForTest(int ticks) {
		noTargetTicks += ticks;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass) {
			if (isCinematic() || move == Move.PHASE) {
				return false;
			}
			if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.LIGHTNING_BOLT)
					|| source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.CRAMMING)) {
				return false;
			}
		}
		if (move == Move.SPENT) {
			amount *= 1.25f; // spent after the Infinite Mass Punch: the opening
		}
		float before = getHealth();
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide()) {
			lastHurtTick = tickCount;
			threat.record(source, Math.max(0f, before - getHealth()));
		}
		return hurt;
	}

	@Override
	public void knockback(double strength, double x, double z) {
		// immune
	}

	@Override
	public void push(double x, double y, double z) {
		// never shoved about
	}

	@Override
	public void thunderHit(ServerLevel level, LightningBolt bolt) {
		// lightning is his
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.MOVEMENT_SLOWDOWN) && !effect.is(MobEffects.LEVITATION) && super.canBeAffected(effect);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void checkDespawn() {
		if (level().getDifficulty() == Difficulty.PEACEFUL) {
			discard();
		}
		// otherwise only his own rules (BlackFlash#tickRules)
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	/** A blow from him: at most 80% of a player's max health (before armour), knocked away and lifted. */
	public boolean strike(LivingEntity e, double damage, double knock, double lift) {
		if (e == null || !BossTargets.isVictim(this, e)) {
			return false;
		}
		float amount = (float) (damage * (isRemnant() ? BlackFlashConfig.get().moves.remnantDamageScale : 1.0));
		if (e instanceof Player p) {
			amount = (float) Math.min(amount, p.getMaxHealth() * BlackFlashConfig.get().boss.playerDamageCap);
		}
		boolean hit = e.hurt(damageSources().mobAttack(this), amount);
		if (hit && (knock > 0 || lift > 0)) {
			Vec3 away = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
			away = away.lengthSqr() < 1.0e-4 ? getLookAngle() : away.normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(away.x * knock, lift, away.z * knock));
			e.hurtMarked = true;
		}
		if (hit && level() instanceof ServerLevel sl) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.2);
			sl.sendParticles(RED, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
		}
		return hit;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl && !isCinematic() && !isDeadOrDying() && tickCount % 2 == 0) {
			// the black-and-red crackle off him, all the time
			sl.sendParticles(BLACK, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.6, 0.3, 0.0);
			if (tickCount % 4 == 0) {
				sl.sendParticles(RED, getX(), getY() + 1.0, getZ(), 1, 0.35, 0.6, 0.35, 0.0);
			}
		}
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (isRemnant()) {
			if (--remnantLife <= 0 || !(server.getEntity(remnantOwner) instanceof BlackFlashEntity owner) || !owner.isAlive()) {
				shatter(server);
				return;
			}
			if (getTarget() == null || !getTarget().isAlive()) {
				setTarget(owner.getTarget());
			}
			brainRemnant(server);
			return;
		}
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (!BlackFlash.tickRules(this, server)) {
			return; // gone
		}
		LivingEntity switched = threat.tick();
		if (switched != null && move != Move.NONE) {
			// a new target from the table: the move in flight finishes on whom it was aimed at
		}
		// v0.17.1 (user: "he should be able to track the player down even if you manage to escape him ... make it so that he
		// loads in his own chunks"): wherever he runs, the world around him (and ahead of him) stays loaded and ticking
		keepLoaded(server);
		ServerPlayer h = hunted == null ? null : server.getServer().getPlayerList().getPlayer(hunted);
		if (h != null && h.level() != server) {
			// gone to another dimension: after a moment he follows through the Speed Force -- not into the Speed Force
			// itself (he waits at the way out)
			if (h.level().dimension() != com.projecthero.mod.speedforce.SpeedForceRealm.DIMENSION && ++otherLevelTicks >= FOLLOW_DIMENSION_TICKS
					&& h.level() instanceof ServerLevel there) {
				otherLevelTicks = 0;
				Vec3 behind = h.position().subtract(h.getLookAngle().multiply(1, 0, 1).normalize().scale(24.0));
				int y = there.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(behind.x), (int) Math.floor(behind.z));
				BlackFlash.spawnFor(h, there, new Vec3(behind.x, Math.max(y, there.getMinBuildHeight() + 1), behind.z));
			}
			noTargetTicks = 0; // he is waiting for them, not idle
			return;
		}
		otherLevelTicks = 0;
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			// v0.17.1 (user): no range limit -- he always knows where his runner is
			if (h != null && h.isAlive() && BossTargets.isVictim(this, h)) {
				setTarget(h);
				target = h;
			}
		}
		noTargetTicks = target == null ? noTargetTicks + 1 : 0;
		// slow regeneration when nothing has hurt him for a while
		BlackFlashConfig.Boss b = BlackFlashConfig.get().boss;
		if (tickCount - lastHurtTick > b.regenDelayTicks && tickCount % 10 == 0 && getHealth() < getMaxHealth()) {
			heal((float) (b.regenPerSecond / 2.0));
		}
		if (phase2() && !phase2Announced) {
			phase2Announced = true;
			say(server, "phase2");
			flashCd = 20;
		}
		brain(server, target);
	}

	// ---------------------------------------------------------------- the brain

	private void cooldowns() {
		meleeCd--;
		globalCd--;
		zipCd--;
		blitzCd--;
		rapidCd--;
		machCd--;
		lightningCd--;
		vortexCd--;
		armsCd--;
		sweepCd--;
		phaseCd--;
		remnantCd--;
		flashCd--;
	}

	private void brain(ServerLevel level, LivingEntity target) {
		cooldowns();
		if (move != Move.NONE) {
			moveTick++;
			tickMove(level);
			return;
		}
		setAction(ACTION_NONE);
		if (target == null) {
			getNavigation().stop();
			return;
		}
		getLookControl().setLookAt(target, 60f, 60f);
		double dist = distanceTo(target);
		boolean sees = hasLineOfSight(target);
		if (!sees && phaseCd <= 0 && dist > 3.0) {
			start(Move.PHASE, target);
			return;
		}
		if (globalCd <= 0) {
			if (phase2() && flashCd <= 0) {
				start(Move.FLASH_TIME, target);
				return;
			}
			if (remnantCd <= 0 && dist < 20 && random.nextInt(4) == 0) {
				start(Move.REMNANT, target);
				return;
			}
			int victims = BossTargets.victims(level, this, getBoundingBox().inflate(32)).size();
			if (sweepCd <= 0 && (victims >= 2 || random.nextInt(3) == 0) && dist < 24) {
				start(Move.SWEEP, target);
				return;
			}
			if (dist > 10) {
				if (lightningCd <= 0 && sees && random.nextBoolean()) {
					start(Move.LIGHTNING, target);
					return;
				}
				if (blitzCd <= 0 && dist < 28) {
					start(Move.BLITZ, target);
					return;
				}
			} else if (dist > 4) {
				if (machCd <= 0) {
					start(Move.MACH, target);
					return;
				}
				if (vortexCd <= 0) {
					start(Move.VORTEX, target);
					return;
				}
				if (lightningCd <= 0 && sees) {
					start(Move.LIGHTNING, target);
					return;
				}
				if (blitzCd <= 0) {
					start(Move.BLITZ, target);
					return;
				}
			} else {
				if (armsCd <= 0 && random.nextInt(3) == 0) {
					start(Move.ARMS, target);
					return;
				}
				if (rapidCd <= 0) {
					start(Move.RAPID, target);
					return;
				}
				if (machCd <= 0) {
					start(Move.MACH, target);
					return;
				}
			}
		}
		// in between: run at them (and zip the long way), punch when close
		if (dist <= 2.4 && meleeCd <= 0) {
			swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			strike(target, BlackFlashConfig.get().moves.punch, 0.4, 0.1);
			sound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
			meleeCd = 14;
			return;
		}
		if (dist > 14 && dist < 48 && zipCd <= 0) {
			zipTo(level, target, 3.0);
			zipCd = 40;
			return;
		}
		// v0.17.1 (user: "make it so that he is as fast as you so even if you try running away he should be able to keep up
		// with you and chase you down"): far off, or after a runner, he RUNS -- faster than they are going
		double targetSpeed = Math.sqrt(Mth.square(target.getX() - target.xo) + Mth.square(target.getZ() - target.zo));
		if (dist > 6 || targetSpeed > 0.5) {
			if (speedsterRun(level, target, dist, targetSpeed)) {
				return;
			}
		}
		getNavigation().moveTo(target, 1.7);
	}

	/** Ticks with his runner in another dimension before he follows them there. */
	private static final int FOLLOW_DIMENSION_TICKS = 10 * 20;
	/** His run: never slower than this (blocks a tick, 30 b/s), and this much faster than whoever he chases. */
	private static final double RUN_MIN_PER_TICK = 1.5;
	private static final double RUN_CATCH_UP = 1.2;
	/** Far behind (out of sight): he closes in harder still. */
	private static final double RUN_FAR = 96.0;
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> CHASE_TICKET =
			net.minecraft.server.level.TicketType.create("projecthero_black_flash", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 40);

	/** One step of his run at {@code t} (gametests). */
	public boolean runStepForTest(LivingEntity t) {
		double speed = Math.sqrt(Mth.square(t.getX() - t.xo) + Mth.square(t.getZ() - t.zo));
		return level() instanceof ServerLevel sl && speedsterRun(sl, t, distanceTo(t), speed);
	}

	/** Keeps his own chunk entity-ticking (and loads the one ahead of his run), wherever he is. */
	private void keepLoaded(ServerLevel level) {
		if (tickCount % 10 != 0) {
			return;
		}
		net.minecraft.world.level.ChunkPos here = chunkPosition();
		level.getChunkSource().addRegionTicket(CHASE_TICKET, here, 2, here);
		if (runDir != null) {
			Vec3 ahead = position().add(runDir.scale(32.0));
			net.minecraft.world.level.ChunkPos next = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(ahead));
			if (!next.equals(here)) {
				level.getChunkSource().addRegionTicket(CHASE_TICKET, next, 2, next);
			}
		}
	}

	/**
	 * One step of his run straight at {@code target}: faster than they are moving, over the ground (onto the top of
	 * whatever is in the way), to 2 blocks short of them. Only into chunks that are loaded -- his ticket loads the next one
	 * while he waits. False if he can't run this tick.
	 */
	private boolean speedsterRun(ServerLevel level, LivingEntity target, double dist, double targetSpeed) {
		Vec3 to = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
		double flat = to.length();
		if (flat < 1.0e-3) {
			return false;
		}
		Vec3 dir = to.scale(1.0 / flat);
		runDir = dir;
		double step = Math.max(RUN_MIN_PER_TICK, targetSpeed * RUN_CATCH_UP + 0.4);
		if (dist > RUN_FAR) {
			step = Math.max(step, targetSpeed * 1.5 + 4.0);
		}
		step = Math.min(step, flat - 2.0);
		if (step <= 0.2) {
			return false;
		}
		Vec3 from = position();
		double nx = getX() + dir.x * step;
		double nz = getZ() + dir.z * step;
		net.minecraft.core.BlockPos col = net.minecraft.core.BlockPos.containing(nx, getY(), nz);
		if (!level.hasChunkAt(col)) {
			getNavigation().stop();
			return true; // waiting for the ground ahead to load
		}
		// the ground at the new spot: close to his height if there is a floor there (caves, under trees), else the surface
		double ny = Double.NaN;
		for (int up = 3; up >= -6; up--) {
			Vec3 at = new Vec3(nx, Math.floor(getY()) + up, nz);
			net.minecraft.world.phys.AABB box = getDimensions(getPose()).makeBoundingBox(at);
			if (level.noCollision(this, box) && !level.noCollision(this, box.move(0, -0.5, 0))) {
				ny = at.y;
				break;
			}
		}
		if (Double.isNaN(ny)) {
			ny = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
			// a target up a cliff, on a roof or flying: run to beneath them, they come down eventually
		}
		getNavigation().stop();
		setPos(nx, ny, nz); // setPos (not moveTo) keeps xo, so his legs run
		setDeltaMovement(Vec3.ZERO);
		resetFallDistance();
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
		if (step > 2.0 && tickCount % 2 == 0) {
			BlackFlash.fx(this, SpeedForceFxPayload.BF_STREAK, from, position());
		}
		level.sendParticles(RED, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.5, 0.3, 0.0);
		return true;
	}

	private void start(Move m, LivingEntity target) {
		move = m;
		moveTick = 0;
		moveTarget = target;
		aim = null;
		anchor = position();
		sweepList.clear();
		getNavigation().stop();
		// the wind-up's telegraph sound
		switch (m) {
			case BLITZ -> {
				setAction(ACTION_CROUCH);
				sound(SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
			}
			case RAPID -> {
				setAction(ACTION_WINDUP);
				sound(SoundEvents.ELYTRA_FLYING, 0.4f, 1.8f);
			}
			case MACH -> {
				setAction(ACTION_WINDUP);
				sound(SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f);
			}
			case LIGHTNING -> {
				setAction(ACTION_THROW);
				sound(SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 0.5f);
			}
			case VORTEX -> {
				setAction(ACTION_VORTEX);
				sound(SoundEvents.BREEZE_SHOOT, 1.0f, 0.4f);
			}
			case ARMS -> {
				setAction(ACTION_ARMS);
				sound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0f, 0.5f);
			}
			case SWEEP -> {
				setAction(ACTION_CROUCH);
				sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.6f);
			}
			case PHASE -> {
				setAction(ACTION_PHASE);
				sound(SoundEvents.BEACON_ACTIVATE, 0.8f, 2.0f);
			}
			case REMNANT -> {
				setAction(ACTION_CROUCH);
				sound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.6f);
			}
			case FLASH_TIME -> {
				setAction(ACTION_FLASH);
				sound(SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.5f);
				sound(SoundEvents.WARDEN_EMERGE, 0.8f, 1.6f);
			}
			default -> {
			}
		}
	}

	private void end(int globalBreather) {
		move = Move.NONE;
		moveTick = 0;
		moveTarget = null;
		noPhysics = false;
		setNoGravity(false);
		setAction(ACTION_NONE);
		globalCd = globalBreather;
	}

	private LivingEntity mt() {
		return moveTarget != null && moveTarget.isAlive() && BossTargets.isVictim(this, moveTarget) ? moveTarget : null;
	}

	private void tickMove(ServerLevel level) {
		LivingEntity t = mt();
		BlackFlashConfig.Moves cfg = BlackFlashConfig.get().moves;
		if (t != null && move != Move.VORTEX && move != Move.PHASE) {
			getLookControl().setLookAt(t, 90f, 90f);
			faceToward(t.position());
		}
		switch (move) {
			case BLITZ -> {
				// 10 ticks crouched, red sparks at his feet; then the zip and the hit
				windupFx(level, false);
				if (moveTick == 10) {
					if (t != null) {
						zipTo(level, t, 1.4);
						Vec3 c = center(t);
						swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						strike(t, cfg.blitz, 0.9, 0.25);
						for (LivingEntity e : BossTargets.victims(level, this, new AABB(c, c).inflate(3.0))) {
							if (e != t) {
								strike(e, cfg.blitzShock, 0.8, 0.2);
							}
						}
						BlackFlash.fx(this, SpeedForceFxPayload.BF_RING, c, new Vec3(4.0, 0, 0));
						sound(SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
					}
					blitzCd = 100;
					end(25);
				}
			}
			case RAPID -> {
				windupFx(level, true);
				if (moveTick == 8 && t != null && distanceTo(t) > 2.6) {
					zipTo(level, t, 1.5);
				}
				if (moveTick > 8) {
					setAction(ACTION_FLURRY);
					if (moveTick % 2 == 0) {
						for (LivingEntity e : inFront(level, 3.5)) {
							strike(e, cfg.rapidPerHit, 0.15, 0.0);
						}
						swing(moveTick % 4 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
						sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
					}
				}
				if (moveTick >= 20) {
					rapidCd = 80;
					end(20);
				}
			}
			case MACH -> {
				// 16 ticks: the fist cocked, red lightning gathering in it, rising
				windupFx(level, true);
				if (moveTick % 4 == 0) {
					sound(SoundEvents.ELYTRA_FLYING, 0.3f, 0.8f + moveTick * 0.06f);
				}
				if (moveTick == 16) {
					if (t != null) {
						if (distanceTo(t) > 2.2) {
							zipTo(level, t, 1.4);
						}
						setAction(ACTION_PUNCH);
						Vec3 c = center(t);
						swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						strike(t, cfg.mach, 2.6, 0.6);
						for (LivingEntity e : BossTargets.victims(level, this, new AABB(c, c).inflate(4.0))) {
							if (e != t) {
								strike(e, cfg.machShock, 1.2, 0.3);
							}
						}
						level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
						BlackFlash.fx(this, SpeedForceFxPayload.BF_RING, c, new Vec3(6.0, 0, 0));
						sound(SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
					}
					machCd = 180;
				}
				if (moveTick >= 22) {
					end(25);
				}
			}
			case LIGHTNING -> {
				// 18 ticks, the arm raised and crackling; the aim locks at 12 (step aside after that and it misses)
				Vec3 hand = handPos();
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0.05);
				level.sendParticles(RED, hand.x, hand.y, hand.z, 2, 0.12, 0.12, 0.12, 0.0);
				if (moveTick == 12) {
					aim = t != null ? center(t) : position().add(getLookAngle().scale(20));
					sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.5f);
				}
				if (moveTick == 18) {
					throwBolt(level, hand, aim == null ? position().add(getLookAngle().scale(20)) : aim, cfg.lightning);
					lightningCd = 120;
					end(20);
				}
			}
			case VORTEX -> {
				// 12 ticks spinning up, then 60 of circling the spot the target stood on
				if (moveTick <= 12) {
					windupFx(level, false);
					if (moveTick == 12) {
						anchor = t != null ? t.position() : position();
						noPhysics = true;
						setNoGravity(true);
					}
					return;
				}
				int k = moveTick - 12;
				double a = k * 0.55;
				double r = 3.5;
				Vec3 at = anchor.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
				Vec3 from = position();
				moveTo(at.x, at.y, at.z, (float) Math.toDegrees(a) + 180f, 0f);
				if (k % 3 == 0) {
					BlackFlash.fx(this, SpeedForceFxPayload.BF_BOLT, from.add(0, 1, 0), at.add(0, 1, 0));
				}
				for (int arm = 0; arm < 3; arm++) {
					double aa = a + arm * Math.PI * 2 / 3;
					level.sendParticles(ParticleTypes.CLOUD, anchor.x + Math.cos(aa) * 2.0, anchor.y + (k % 8) * 0.5, anchor.z + Math.sin(aa) * 2.0, 1, 0.1, 0.1, 0.1, 0.02);
				}
				for (LivingEntity e : BossTargets.victims(level, this, new AABB(anchor, anchor).inflate(6.0))) {
					Vec3 in = new Vec3(anchor.x - e.getX(), 0, anchor.z - e.getZ());
					double d = Math.max(0.1, in.length());
					in = in.scale(1.0 / d);
					Vec3 around = new Vec3(-in.z, 0, in.x);
					e.setDeltaMovement(in.scale(d > 2.0 ? 0.22 : -0.05).add(around.scale(0.35)).add(0, e.getY() < anchor.y + 3 ? 0.12 : -0.02, 0));
					e.hurtMarked = true;
					if (k % 10 == 0) {
						strike(e, cfg.vortexTick, 0, 0);
					}
				}
				if (k % 12 == 0) {
					sound(SoundEvents.ELYTRA_FLYING, 0.4f, 1.2f);
				}
				if (k >= 60) {
					for (LivingEntity e : BossTargets.victims(level, this, new AABB(anchor, anchor).inflate(6.0))) {
						strike(e, cfg.vortexFinal, 1.5, 0.8);
					}
					level.sendParticles(ParticleTypes.EXPLOSION, anchor.x, anchor.y + 1, anchor.z, 3, 1.0, 0.5, 1.0, 0);
					sound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.0f);
					vortexCd = 300;
					end(30);
				}
			}
			case ARMS -> {
				// 10 ticks winding up, then 40 of wind straight at the target: it shoves them away
				if (moveTick <= 10) {
					windupFx(level, false);
					return;
				}
				Vec3 look = t != null ? center(t).subtract(getEyePosition()).normalize() : getLookAngle();
				Vec3 apex = getEyePosition().subtract(0, 0.4, 0);
				for (LivingEntity e : BossTargets.victims(level, this, getBoundingBox().inflate(11.0))) {
					Vec3 to = center(e).subtract(apex);
					double d = to.length();
					if (d > 10.0 || d < 0.1 || to.scale(1.0 / d).dot(look) < 0.84) {
						continue;
					}
					e.setDeltaMovement(look.x * 0.7, Math.max(e.getDeltaMovement().y, 0.15), look.z * 0.7);
					e.hurtMarked = true;
					if (moveTick % 10 == 0) {
						strike(e, cfg.armsTick, 0, 0);
					}
				}
				for (int i = 0; i < 4; i++) {
					double s = 1.0 + (i + (moveTick % 3) / 3.0) * 2.2;
					Vec3 at = apex.add(look.scale(s));
					level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, look.x * 0.6, look.y * 0.6, look.z * 0.6, 1.0);
				}
				if (moveTick % 3 == 0) {
					Vec3 g = apex.add(look.scale(3.0 + (moveTick % 9) * 0.6));
					level.sendParticles(ParticleTypes.GUST, g.x, g.y, g.z, 1, 0.3, 0.3, 0.3, 0.0);
				}
				if (moveTick >= 50) {
					armsCd = 240;
					end(25);
				}
			}
			case SWEEP -> {
				// 12 ticks of the wind-up (his eyes flare), then a blink to every victim near, one every 4 ticks, then back
				if (moveTick <= 12) {
					windupFx(level, false);
					if (moveTick == 12) {
						for (LivingEntity e : BossTargets.victims(level, this, getBoundingBox().inflate(32))) {
							if (sweepList.size() < 5) {
								sweepList.add(e);
							}
						}
						if (sweepList.size() == 1) {
							sweepList.add(sweepList.get(0));
							sweepList.add(sweepList.get(0));
						}
					}
					return;
				}
				int k = moveTick - 12;
				if (k % 4 == 0) {
					int i = k / 4 - 1;
					if (i >= 0 && i < sweepList.size()) {
						LivingEntity e = sweepList.get(i);
						if (e.isAlive()) {
							double ang = i * 2.1;
							Vec3 spot = e.position().add(Math.cos(ang) * 1.4, 0, Math.sin(ang) * 1.4);
							blink(level, spot);
							faceToward(e.position());
							swing(net.minecraft.world.InteractionHand.MAIN_HAND);
							strike(e, cfg.sweepPerHit, 0.5, 0.15);
							sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
						}
					} else if (i >= sweepList.size()) {
						blink(level, anchor);
						sweepCd = 280;
						end(25);
					}
				}
			}
			case PHASE -> {
				// vibrating through walls straight at the target
				noPhysics = true;
				setNoGravity(true);
				if (t == null) {
					phaseCd = 60;
					end(10);
					return;
				}
				Vec3 to = t.position().subtract(position());
				double d = to.length();
				Vec3 step = d < 0.9 ? to : to.scale(0.9 / d);
				setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
				setDeltaMovement(Vec3.ZERO);
				if (moveTick % 2 == 0) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 4, 0.3, 0.6, 0.3, 0.05);
					level.sendParticles(BLACK, getX(), getY() + 1, getZ(), 3, 0.3, 0.6, 0.3, 0.0);
				}
				boolean free = level.noCollision(this, getBoundingBox());
				if ((free && (d < 3.0 || hasLineOfSight(t))) || moveTick > 60) {
					if (!free) {
						blink(level, t.position().add(1.0, 0, 0)); // never left inside a wall
					}
					phaseCd = 120;
					end(10);
				}
			}
			case REMNANT -> {
				windupFx(level, false);
				if (moveTick == 14) {
					splitRemnant(level);
					remnantCd = 500;
					end(20);
				}
			}
			case FLASH_TIME -> {
				// 10 ticks, a hand raised; then the world crawls for everyone near him while he blurs about
				if (moveTick <= 10) {
					windupFx(level, true);
					if (moveTick == 10) {
						flashTimeStart(level, cfg.flashTimeTicks);
					}
					return;
				}
				int k = moveTick - 10;
				if (t != null && k % 12 == 0) {
					double ang = random.nextDouble() * Math.PI * 2;
					blink(level, t.position().add(Math.cos(ang) * 4.0, 0, Math.sin(ang) * 4.0));
				}
				if (k >= cfg.flashTimeTicks) {
					move = Move.MASS;
					moveTick = 0;
					setAction(ACTION_MASS);
					sound(SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.7f);
					sound(SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.4f);
				}
			}
			case MASS -> {
				// 16 ticks: the fist cocked back, a storm of red lightning gathering -- the dodge window
				windupFx(level, true);
				Vec3 hand = handPos();
				if (moveTick % 2 == 0) {
					BlackFlash.fx(this, SpeedForceFxPayload.BF_BOLT, hand, hand.add(random.nextGaussian() * 1.5, random.nextGaussian(), random.nextGaussian() * 1.5));
				}
				if (moveTick == 16) {
					if (t != null) {
						zipTo(level, t, 1.3);
						setAction(ACTION_PUNCH);
						Vec3 c = center(t);
						swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						strike(t, cfg.massPunch, 4.0, 0.9);
						for (LivingEntity e : BossTargets.victims(level, this, new AABB(c, c).inflate(5.0))) {
							if (e != t) {
								strike(e, cfg.massShock, 1.6, 0.4);
							}
						}
						level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
						level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0);
						BlackFlash.fx(this, SpeedForceFxPayload.BF_RING, c, new Vec3(9.0, 0, 0));
						sound(SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.9f);
						sound(SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
					}
					move = Move.SPENT;
					moveTick = 0;
					setAction(ACTION_SPENT);
					say(level, "spent");
				}
			}
			case SPENT -> {
				// 2 s spent after the punch: he stands, panting sparks (and takes +25%)
				getNavigation().stop();
				if (moveTick % 4 == 0) {
					level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				}
				if (moveTick >= 40) {
					flashCd = 600;
					end(20);
				}
			}
			default -> end(10);
		}
	}

	/** The remnant's brain: run at the target, punch, and a Blitz now and then. */
	private void brainRemnant(ServerLevel level) {
		cooldowns();
		LivingEntity t = getTarget();
		if (move != Move.NONE) {
			moveTick++;
			tickMove(level);
			return;
		}
		if (t == null) {
			return;
		}
		double dist = distanceTo(t);
		if (blitzCd <= 0 && dist > 5) {
			start(Move.BLITZ, t);
			return;
		}
		if (dist <= 2.4 && meleeCd <= 0) {
			swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			strike(t, BlackFlashConfig.get().moves.punch, 0.3, 0.1);
			meleeCd = 16;
			return;
		}
		getNavigation().moveTo(t, 1.6);
	}

	// ---------------------------------------------------------------- move helpers

	private static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private Vec3 handPos() {
		Vec3 f = getLookAngle();
		Vec3 right = new Vec3(-f.z, 0, f.x).normalize();
		return position().add(0, 2.1, 0).add(right.scale(0.4));
	}

	private void faceToward(Vec3 at) {
		double dx = at.x - getX();
		double dz = at.z - getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
	}

	/** The wind-up's telegraph: black and red sparks gathering on him (in his fist when {@code fist}). */
	private void windupFx(ServerLevel level, boolean fist) {
		Vec3 at = fist ? handPos().subtract(0, 0.6, 0) : position().add(0, 0.2, 0);
		level.sendParticles(RED, at.x, at.y, at.z, 3, fist ? 0.15 : 0.5, fist ? 0.15 : 0.1, fist ? 0.15 : 0.5, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.05);
		if (moveTick % 3 == 0) {
			level.sendParticles(BLACK, getX(), getY() + 1.0, getZ(), 4, 0.4, 0.7, 0.4, 0.0);
		}
	}

	/** Everything in a short cone in front of him. */
	private List<LivingEntity> inFront(ServerLevel level, double range) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 look = getLookAngle();
		Vec3 eye = getEyePosition();
		for (LivingEntity e : BossTargets.victims(level, this, getBoundingBox().inflate(range))) {
			Vec3 to = center(e).subtract(eye);
			double d = to.length();
			if (d <= range && (d < 1.2 || to.scale(1.0 / d).dot(look) > 0.4)) {
				out.add(e);
			}
		}
		return out;
	}

	/** A zip: to {@code gap} blocks from the target, on the near side, with a streak of him behind. */
	public void zipTo(ServerLevel level, LivingEntity t, double gap) {
		Vec3 dir = new Vec3(getX() - t.getX(), 0, getZ() - t.getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.normalize();
		Vec3 spot = t.position().add(dir.scale(gap + t.getBbWidth() * 0.5));
		blink(level, spot);
		faceToward(t.position());
	}

	/** A blink to {@code spot} (or as near it as he fits), drawn as a streak of black lightning. */
	public void blink(ServerLevel level, Vec3 spot) {
		Vec3 from = position();
		Vec3 to = spot;
		for (double up = 0; up <= 2.0; up += 1.0) {
			Vec3 tryAt = spot.add(0, up, 0);
			if (level.noCollision(this, getDimensions(getPose()).makeBoundingBox(tryAt))) {
				to = tryAt;
				break;
			}
		}
		moveTo(to.x, to.y, to.z, getYRot(), getXRot());
		setDeltaMovement(Vec3.ZERO);
		BlackFlash.fx(this, SpeedForceFxPayload.BF_STREAK, from, to);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 1, to.z, 6, 0.3, 0.5, 0.3, 0.1);
	}

	private void throwBolt(ServerLevel level, Vec3 hand, Vec3 aimAt, double damage) {
		Vec3 dir = aimAt.subtract(hand);
		dir = dir.lengthSqr() < 1.0e-4 ? getLookAngle() : dir.normalize();
		Vec3 end = hand.add(dir.scale(32));
		var clip = level.clip(new net.minecraft.world.level.ClipContext(hand, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		end = clip.getLocation();
		LivingEntity first = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : BossTargets.victims(level, this, new AABB(hand, end).inflate(1.5))) {
			var hit = e.getBoundingBox().inflate(0.6).clip(hand, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(hand);
				if (d < best) {
					best = d;
					first = e;
				}
			}
		}
		Vec3 stop = first != null ? center(first) : end;
		BlackFlash.fx(this, SpeedForceFxPayload.BF_BOLT, hand, stop);
		sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 0.6f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, stop.x, stop.y, stop.z, 20, 0.4, 0.4, 0.4, 0.2);
		if (first == null) {
			return;
		}
		strike(first, damage, 0.5, 0.1);
		List<LivingEntity> hit = new ArrayList<>();
		hit.add(first);
		Vec3 from = stop;
		for (int i = 0; i < 2; i++) {
			LivingEntity next = null;
			double nd = 36.0;
			for (LivingEntity e : BossTargets.victims(level, this, new AABB(from, from).inflate(6.0))) {
				double d = center(e).distanceToSqr(from);
				if (!hit.contains(e) && d < nd) {
					nd = d;
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			Vec3 to = center(next);
			BlackFlash.fx(this, SpeedForceFxPayload.BF_BOLT, from, to);
			strike(next, damage * 0.5, 0.2, 0.0);
			hit.add(next);
			from = to;
		}
	}

	private void splitRemnant(ServerLevel level) {
		if (level.getEntity(remnantId) instanceof BlackFlashEntity old && old.isAlive()) {
			return;
		}
		BlackFlashEntity r = SpeedForceEntityTypes.BLACK_FLASH.create(level);
		if (r == null) {
			return;
		}
		Vec3 side = new Vec3(-getLookAngle().z, 0, getLookAngle().x).normalize().scale(1.5);
		Vec3 at = position().add(side);
		if (!level.noCollision(r, r.getDimensions(r.getPose()).makeBoundingBox(at))) {
			at = position();
		}
		r.moveTo(at.x, at.y, at.z, getYRot(), 0f);
		r.configureRemnant(this);
		level.addFreshEntity(r);
		remnantId = r.getId();
		BlackFlash.fx(this, SpeedForceFxPayload.BF_STREAK, position(), at);
		level.sendParticles(BLACK, at.x, at.y + 1, at.z, 20, 0.3, 0.7, 0.3, 0.0);
		sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 0.6f);
	}

	/** Phase 2: his Flash Time -- everyone near crawls (Slowness IV) while he blurs about, then the punch. */
	private void flashTimeStart(ServerLevel level, int ticks) {
		Component title = Component.translatable("message.projecthero.black_flash.flash_time").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		for (LivingEntity e : BossTargets.victims(level, this, getBoundingBox().inflate(32))) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, true, true));
			e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2, false, true, true));
			if (e instanceof ServerPlayer p) {
				p.connection.send(new ClientboundSetTitlesAnimationPacket(3, 30, 10));
				p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.projecthero.black_flash.flash_time_sub")
						.withStyle(ChatFormatting.RED)));
				p.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}
		level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 2, 0.2, 0.2, 0.2, 0);
		BlackFlash.fx(this, SpeedForceFxPayload.BF_RING, position().add(0, 1, 0), new Vec3(14.0, 0, 0));
		sound(SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.5f);
	}

	private void sound(SoundEvent s, float volume, float pitch) {
		level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, volume, pitch);
	}

	/** A line to everyone within 48 blocks. */
	public void say(ServerLevel level, String key) {
		Component line = Component.translatable("boss.projecthero.black_flash.chat",
				Component.translatable("entity.projecthero.black_flash").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.black_flash.say." + key).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 48 * 48) {
				p.sendSystemMessage(line);
			}
		}
	}

	/** A remnant's end: it breaks up into black sparks. */
	private void shatter(ServerLevel level) {
		level.sendParticles(BLACK, getX(), getY() + 1, getZ(), 24, 0.3, 0.7, 0.3, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 12, 0.3, 0.7, 0.3, 0.1);
		discard();
	}

	// ---------------------------------------------------------------- bar, death, removal

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!isRemnant() && !isCinematic()) {
			bossBar.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		if (!isRemnant() && level() instanceof ServerLevel server && !defeated) {
			defeated = true;
			setAction(ACTION_NONE);
			noPhysics = false;
			say(server, "death");
			BlackFlash.onDefeated(this, server);
			BlackFlash.fx(this, SpeedForceFxPayload.BF_DISSOLVE, position().add(0, 1, 0), position().add(0, 6, 0));
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.6f, 0.6f);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.7f, 1.6f);
		}
		super.die(source);
		bossBar.removeAllPlayers();
	}

	/** The death scene: 2.5 s of dissolving into black lightning (the renderer fades him; the server sends the sparks). */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (level() instanceof ServerLevel sl) {
			double up = deathTime * 0.04;
			sl.sendParticles(BLACK, getX(), getY() + 0.3 + up * 10, getZ(), 6, 0.35, 0.5, 0.35, 0.0);
			sl.sendParticles(RED, getX(), getY() + 1.0, getZ(), 3, 0.4, 0.8, 0.4, 0.0);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 4, 0.4, 0.9, 0.4, 0.15);
			if (deathTime % 10 == 0) {
				BlackFlash.fx(this, SpeedForceFxPayload.BF_DISSOLVE, position().add(0, 1, 0), position().add(0, 6 + deathTime * 0.1, 0));
			}
			if (deathTime >= 50 && !isRemoved()) {
				sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
				remove(RemovalReason.KILLED);
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (!isRemnant() && SpeedForceMod.SPEED_FORCE_SHARD != null) {
			spawnAtLocation(new ItemStack(SpeedForceMod.SPEED_FORCE_SHARD));
			if (lured) {
				// v0.17.1 (user): a lured fight pays better -- more shards and a netherite ingot
				spawnAtLocation(new ItemStack(SpeedForceMod.SPEED_FORCE_SHARD, Math.max(0, com.projecthero.mod.speedforce.SpeedForceConfig.LURE_SHARDS - 1)));
				spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT));
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		boolean server = !level().isClientSide();
		super.remove(reason);
		if (server && !isRemnant() && !isCinematic()) {
			BlackFlash.onGone(this, defeated);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.4f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	}
}
