package com.projecthero.mod.skyfortress.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.skyfortress.SkyFortressClearing;
import com.projecthero.mod.skyfortress.SkyFortressEntityTypes;
import com.projecthero.mod.ultron.UltronEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 * v0.17.7: <b>Admiral Kestrel</b>, master of the Sky Fortress -- an original villain, an ace pilot sealed in a heavy
 * gunmetal-and-navy flight suit with a crested visor helmet, a twin-nozzle jet pack, folding wing-blades and missile
 * pods on both forearms (custom GeckoLib body, about 2.6 blocks tall). {@link #MAX_HEALTH} health, 14 armour.
 *
 * <p><b>Phase 1</b> (on the flight deck):
 * <ul>
 * <li><b>Jet Dash</b> (6-20 blocks): a 0.6 s crouch while an orange line marks the path, then a dash at
 * {@link #DASH_SPEED} b/t for 0.6 s -- 12 and thrown.</li>
 * <li><b>Missile Barrage</b> (8-36, in sight): arms up, lock-on rings under up to three targets for 0.8 s, then
 * {@link #missileCount()} homing micro-missiles, one every 3 ticks (6 each in a 2.5 burst).</li>
 * <li><b>Strafing Run</b> (every ~25 s): she lifts off, a red lane is painted down the whole deck through her target for
 * 1.5 s, then she rakes it end to end at {@link #STRAFE_SPEED} b/t -- 7 in a 2.2 circle every other tick under her.</li>
 * <li><b>Fighter Drones</b> (with fewer than {@link #maxDrones} near): radio call, two fighter drones (+1 per extra
 * player) sweep in.</li>
 * <li>Thruster punch in melee (10).</li>
 * </ul>
 * <p><b>Phase 2</b> at half health: a 2 s transformation (wings unfold, she can't be hurt), then she <b>takes to the
 * air</b> -- circling the deck {@link #ORBIT_RADIUS} out and {@link #ORBIT_HEIGHT} up, facing her target. Missiles are
 * nine, strafing runs come faster, and the Jet Dash becomes an <b>Afterburner Dive</b>: a ring marks the target's spot
 * for a second, she plunges onto it (14 in 3.5 + thrown) and stays down {@link #GROUNDED_TICKS} ticks catching her
 * breath -- taking 30% more damage -- before she climbs again.
 *
 * <p>Shared {@link BossThreat} threat table (switches to whoever hurts her most), boss bar within {@link #BAR_RANGE},
 * bound to the flight deck ({@link #home}); she never takes fall damage and is put back on the deck if knocked off.
 * Dying opens the Fortress Vault ({@link SkyFortressClearing}).
 */
public class AdmiralKestrelBoss extends Monster implements GeoEntity {
	public static final double MAX_HEALTH = 1100.0;
	public static final double BAR_RANGE = 56.0;
	public static final double TETHER = 44.0;
	public static final double ORBIT_RADIUS = 11.0;
	public static final double ORBIT_HEIGHT = 7.0;
	public static final double DASH_SPEED = 1.3;
	public static final double STRAFE_SPEED = 1.5;
	public static final double DIVE_SPEED = 1.6;
	public static final double STRAFE_HALF = 18.0;
	public static final int GROUNDED_TICKS = 50;
	public static final String DRONE_TAG = "projecthero.kestrel_drone";

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_DASH = 1;
	public static final byte MOVE_MISSILES = 2;
	public static final byte MOVE_STRAFE = 3;
	public static final byte MOVE_DRONES = 4;
	public static final byte MOVE_DIVE = 5;
	public static final byte MOVE_GROUNDED = 6;
	public static final byte MOVE_TRANSFORM = 7;

	public static final int DASH_WINDUP = 12;
	public static final int DASH_RUN = 12;
	public static final int MISSILE_WINDUP = 16;
	public static final int STRAFE_RISE = 16;
	public static final int STRAFE_MARK = 30;
	public static final int DRONE_WINDUP = 20;
	public static final int DIVE_MARK = 20;
	public static final int TRANSFORM_TICKS = 40;

	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(AdmiralKestrelBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(AdmiralKestrelBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(AdmiralKestrelBoss.class, EntityDataSerializers.BOOLEAN);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.12f, 0.08f), 1.5f);
	private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1f, 0.55f, 0.1f), 1.4f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final BossThreat threat = new BossThreat(this).range(48).holdWhile(() -> this.move() != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.admiral_kestrel").withStyle(ChatFormatting.AQUA),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private boolean reported;

	int moveTicks;
	int dashCd = 50;
	int missileCd = 70;
	int strafeCd = 260;
	int droneCd = 200;
	int diveCd = 80;
	int gap;
	double orbitAngle;
	private Vec3 dashDir = Vec3.ZERO;
	private Vec3 laneStart;
	private Vec3 laneEnd;
	private Vec3 diveSpot;
	private final List<LivingEntity> missileTargets = new ArrayList<>();
	private int missilesFired;
	private final Set<LivingEntity> struck = new HashSet<>();
	private int movesUsed;

	public AdmiralKestrelBoss(EntityType<? extends AdmiralKestrelBoss> type, Level level) {
		super(type, level);
		this.xpReward = 400;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.4)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, MOVE_NONE);
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_FLYING, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MovesGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false) {
			@Override
			public boolean canUse() {
				return !flying() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !flying() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	// ---------------------------------------------------------------- state

	public byte move() {
		return entityData.get(DATA_MOVE);
	}

	private void setMove(byte m) {
		entityData.set(DATA_MOVE, m);
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	public boolean flying() {
		return entityData.get(DATA_FLYING);
	}

	public void setFlying(boolean f) {
		entityData.set(DATA_FLYING, f);
		setNoGravity(f);
	}

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home != null) {
			restrictTo(home, 18);
		}
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bar() {
		return bar;
	}

	public int movesUsed() {
		return movesUsed;
	}

	public int missileCount() {
		return phase() >= 2 ? 9 : 6;
	}

	public int maxDrones() {
		int players = level() instanceof ServerLevel s ? s.getPlayers(p -> p.distanceToSqr(this) < 48 * 48 && !p.isSpectator()).size() : 1;
		return Math.min(5, 3 + Math.max(0, players - 1));
	}

	public Vec3 laneStart() {
		return laneStart;
	}

	public Vec3 laneEnd() {
		return laneEnd;
	}

	public Vec3 diveSpot() {
		return diveSpot;
	}

	private double deckY() {
		return home != null ? home.getY() : getY();
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (move() == MOVE_TRANSFORM && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		if (move() == MOVE_GROUNDED) {
			amount *= 1.3f;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server && isAlive()) {
			threat.record(source, amount);
			if (phase() == 1 && getHealth() <= getMaxHealth() * 0.5f) {
				startTransform(server);
			}
		}
		return hurt;
	}

	/** Half health: the phase change (2 s, untouchable), then the air phase. */
	public void startTransform(ServerLevel level) {
		entityData.set(DATA_PHASE, (byte) 2);
		begin(MOVE_TRANSFORM);
		missileTargets.clear();
		bar.setColor(BossEvent.BossBarColor.RED);
		bar.setName(Component.translatable("projecthero.sky_fortress.kestrel.air_bar").withStyle(ChatFormatting.RED));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0f, 0.5f);
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE)) {
			p.displayClientMessage(Component.translatable("projecthero.sky_fortress.kestrel.phase2").withStyle(ChatFormatting.RED), false);
		}
		strafeCd = Math.min(strafeCd, 120);
		droneCd = Math.min(droneCd, 80);
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		threat.tick();
		if (dashCd > 0) {
			dashCd--;
		}
		if (missileCd > 0) {
			missileCd--;
		}
		if (strafeCd > 0) {
			strafeCd--;
		}
		if (droneCd > 0) {
			droneCd--;
		}
		if (diveCd > 0) {
			diveCd--;
		}
		if (gap > 0) {
			gap--;
		}
		if (move() == MOVE_NONE && phase() >= 2) {
			orbit(server);
		}
		if (flying() && tickCount % 2 == 0) {
			Vec3 back = position().add(0, 1.3, 0).subtract(getViewVector(1f).multiply(1, 0, 1).scale(0.5));
			server.sendParticles(ParticleTypes.FLAME, back.x, back.y - 0.3, back.z, 2, 0.12, 0.05, 0.12, 0.01);
			server.sendParticles(ParticleTypes.SMOKE, back.x, back.y - 0.5, back.z, 1, 0.1, 0.05, 0.1, 0.01);
		}
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
		}
		if (home != null && tickCount % 20 == 0) {
			leash();
		}
	}

	/** Phase 2 idle flight: circle the deck, face the target. */
	void orbit(ServerLevel level) {
		if (home == null) {
			return;
		}
		if (!flying()) {
			setFlying(true);
		}
		orbitAngle += 0.022;
		Vec3 want = Vec3.atBottomCenterOf(home).add(Math.cos(orbitAngle) * ORBIT_RADIUS, ORBIT_HEIGHT + Math.sin(tickCount * 0.05) * 1.2,
				Math.sin(orbitAngle) * ORBIT_RADIUS);
		steer(want, 0.45);
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
			faceToward(t.position());
		}
	}

	/** Moves toward {@code want} at up to {@code speed} b/t (flight). */
	void steer(Vec3 want, double speed) {
		Vec3 to = want.subtract(position());
		double d = to.length();
		Vec3 v = d < 1.0e-3 ? Vec3.ZERO : to.scale(Math.min(speed, d * 0.25) / d);
		setDeltaMovement(v);
		getNavigation().stop();
	}

	void faceToward(Vec3 p) {
		double dx = p.x - getX();
		double dz = p.z - getZ();
		if (dx * dx + dz * dz > 1.0e-4) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			setYRot(yaw);
			setYBodyRot(yaw);
			setYHeadRot(yaw);
		}
	}

	private void refreshViewers(ServerLevel level) {
		viewers.removeIf(p -> {
			boolean keep = p.isAlive() && !p.isRemoved() && p.level() == level && p.distanceToSqr(this) <= (BAR_RANGE + 8) * (BAR_RANGE + 8);
			if (!keep) {
				bar.removePlayer(p);
			}
			return !keep;
		});
		for (ServerPlayer p : level.players()) {
			if (!viewers.contains(p) && !p.isSpectator() && p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE) {
				viewers.add(p);
				bar.addPlayer(p);
			}
		}
	}

	private void leash() {
		Vec3 h = Vec3.atBottomCenterOf(home);
		double d = position().distanceTo(h);
		if (d > TETHER || getY() < home.getY() - 10) {
			moveTo(h.x, h.y + (phase() >= 2 ? ORBIT_HEIGHT : 0), h.z, getYRot(), 0f);
			setDeltaMovement(Vec3.ZERO);
			getNavigation().stop();
			if (move() == MOVE_DASH || move() == MOVE_STRAFE || move() == MOVE_DIVE) {
				endMove();
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			bar.removeAllPlayers();
			viewers.clear();
			if (!reported) {
				reported = true;
				for (Mob d : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(64), m -> m.getTags().contains(DRONE_TAG))) {
					d.kill();
				}
				SkyFortressClearing.bossDefeated(server, home != null ? home : blockPosition());
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		viewers.remove(player);
		bar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		bar.removeAllPlayers();
		viewers.clear();
		super.remove(reason);
	}

	// ---------------------------------------------------------------- moves

	private static boolean victim(LivingEntity e, LivingEntity self) {
		return e != self && e.isAlive() && !(e instanceof AdmiralKestrelBoss) && !e.getTags().contains(DRONE_TAG)
				&& !(e instanceof com.projecthero.mod.syndicate.entity.SyndicateCriminal) && !(e instanceof SkyPrisoner)
				&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private void begin(byte which) {
		setMove(which);
		moveTicks = 0;
		movesUsed++;
		getNavigation().stop();
	}

	/** Starts the Jet Dash now (test hook too). */
	public void startDash() {
		begin(MOVE_DASH);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		to = new Vec3(to.x, 0, to.z);
		dashDir = to.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : to.normalize();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	/** Starts a Missile Barrage now: locks onto up to three players in range (the target first). */
	public void startMissiles() {
		begin(MOVE_MISSILES);
		missilesFired = 0;
		missileTargets.clear();
		LivingEntity t = getTarget();
		if (t != null) {
			missileTargets.add(t);
		}
		if (level() instanceof ServerLevel s) {
			for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(this) < 36 * 36 && victim(p, this))) {
				if (missileTargets.size() >= 3) {
					break;
				}
				if (!missileTargets.contains(p)) {
					missileTargets.add(p);
				}
			}
		}
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1.5f, 2.0f);
	}

	/** Starts a Strafing Run now: the lane through the target (or her front) along the line from her to it. */
	public void startStrafe() {
		begin(MOVE_STRAFE);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 center = t != null ? t.position() : position().add(getViewVector(1f).scale(8));
		Vec3 dir = center.subtract(position()).multiply(1, 0, 1);
		dir = dir.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : dir.normalize();
		double y = deckY() + 5.5;
		laneStart = new Vec3(center.x - dir.x * STRAFE_HALF, y, center.z - dir.z * STRAFE_HALF);
		laneEnd = new Vec3(center.x + dir.x * STRAFE_HALF, y, center.z + dir.z * STRAFE_HALF);
		setFlying(true);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0f, 0.6f);
	}

	/** Calls fighter drones now. */
	public void startDrones() {
		begin(MOVE_DRONES);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
	}

	/** Starts the Afterburner Dive now (phase 2): marks the target's spot. */
	public void startDive() {
		begin(MOVE_DIVE);
		struck.clear();
		LivingEntity t = getTarget();
		diveSpot = t != null ? new Vec3(t.getX(), deckY(), t.getZ()) : new Vec3(getX(), deckY(), getZ());
		setFlying(true);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0f, 0.6f);
	}

	void endMove() {
		setMove(MOVE_NONE);
		moveTicks = 0;
		gap = phase() >= 2 ? 14 + random.nextInt(14) : 20 + random.nextInt(20);
		if (phase() < 2 && flying()) {
			setFlying(false);
		}
	}

	void tickMove(ServerLevel level) {
		moveTicks++;
		LivingEntity t = getTarget();
		switch (move()) {
			case MOVE_DASH -> tickDash(level);
			case MOVE_MISSILES -> {
				if (t != null) {
					getLookControl().setLookAt(t, 60f, 60f);
					faceToward(t.position());
				}
				if (flying()) {
					setDeltaMovement(getDeltaMovement().scale(0.5));
				}
				if (moveTicks < MISSILE_WINDUP) {
					if (moveTicks % 3 == 0) {
						for (LivingEntity m : missileTargets) {
							ring(level, m.position().add(0, 0.1, 0), 1.2, RED, 12);
						}
					}
					return;
				}
				if ((moveTicks - MISSILE_WINDUP) % 3 == 0 && missilesFired < missileCount()) {
					fireMissile(level, missileTargets.isEmpty() ? t : missileTargets.get(missilesFired % missileTargets.size()), missilesFired);
					missilesFired++;
				}
				if (missilesFired >= missileCount()) {
					missileCd = (phase() >= 2 ? 90 : 120) + random.nextInt(40);
					endMove();
				}
			}
			case MOVE_STRAFE -> tickStrafe(level);
			case MOVE_DRONES -> {
				if (flying()) {
					setDeltaMovement(getDeltaMovement().scale(0.5));
				}
				if (moveTicks % 5 == 0) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getEyeY() + 0.3, getZ(), 4, 0.3, 0.2, 0.3, 0.05);
				}
				if (moveTicks >= DRONE_WINDUP) {
					callDrones(level);
					droneCd = 600 + random.nextInt(200);
					endMove();
				}
			}
			case MOVE_DIVE -> tickDive(level);
			case MOVE_GROUNDED -> {
				setDeltaMovement(0, getDeltaMovement().y, 0);
				if (moveTicks % 6 == 0) {
					level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 3, 0.3, 0.2, 0.3, 0.02);
				}
				if (moveTicks >= GROUNDED_TICKS) {
					setFlying(true);
					endMove();
				}
			}
			case MOVE_TRANSFORM -> {
				setFlying(true);
				setDeltaMovement(0, 0.12, 0);
				if (moveTicks % 4 == 0) {
					level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.8, getZ(), 10, 0.5, 0.3, 0.5, 0.06);
					level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.6, 0.1, 0.6, 0.06);
				}
				if (moveTicks >= TRANSFORM_TICKS) {
					endMove();
				}
			}
			default -> endMove();
		}
	}

	private void tickDash(ServerLevel level) {
		if (moveTicks <= DASH_WINDUP) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			faceToward(position().add(dashDir));
			if (moveTicks % 3 == 0) {
				for (int i = 1; i <= 16; i++) {
					Vec3 p = position().add(dashDir.scale(i));
					level.sendParticles(ORANGE, p.x, p.y + 0.15, p.z, 1, 0.1, 0, 0.1, 0);
				}
			}
			return;
		}
		faceToward(position().add(dashDir));
		setDeltaMovement(dashDir.x * DASH_SPEED, getDeltaMovement().y, dashDir.z * DASH_SPEED);
		level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2, getZ(), 3, 0.2, 0.2, 0.2, 0.02);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.9), e -> victim(e, this))) {
			if (struck.add(e)) {
				e.hurt(damageSources().mobAttack(this), 12.0f);
				e.knockback(1.6, -dashDir.x, -dashDir.z);
				e.push(0, 0.3, 0);
			}
		}
		if (moveTicks >= DASH_WINDUP + DASH_RUN || horizontalCollision) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			dashCd = 70 + random.nextInt(40);
			endMove();
		}
	}

	private void tickStrafe(ServerLevel level) {
		if (laneStart == null || laneEnd == null) {
			endMove();
			return;
		}
		Vec3 dir = laneEnd.subtract(laneStart).normalize();
		if (moveTicks <= STRAFE_RISE + STRAFE_MARK) {
			// lift off and fly to the lane start while the lane is painted
			steer(laneStart, moveTicks <= STRAFE_RISE ? 0.5 : 0.9);
			faceToward(position().add(dir));
			if (moveTicks % 4 == 0) {
				double len = laneStart.distanceTo(laneEnd);
				for (double s = 0; s <= len; s += 1.5) {
					Vec3 p = laneStart.add(dir.scale(s));
					for (int side = -1; side <= 1; side += 2) {
						Vec3 q = p.add(-dir.z * side * 2.0, 0, dir.x * side * 2.0);
						level.sendParticles(RED, q.x, deckY() + 0.15, q.z, 1, 0.05, 0, 0.05, 0);
					}
				}
			}
			return;
		}
		Vec3 v = dir.scale(STRAFE_SPEED);
		setDeltaMovement(v);
		faceToward(position().add(dir));
		if (moveTicks % 2 == 0) {
			Vec3 hit = new Vec3(getX(), deckY(), getZ());
			level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y + 0.3, hit.z, 1, 0.4, 0, 0.4, 0);
			level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y + 0.5, hit.z, 8, 0.6, 0.2, 0.6, 0.2);
			level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 1.8f);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(2.2, 3, 2.2), e -> victim(e, this))) {
				e.hurt(damageSources().mobAttack(this), 7.0f);
			}
		}
		boolean past = position().subtract(laneEnd).dot(dir) >= 0;
		if (past || moveTicks > STRAFE_RISE + STRAFE_MARK + 60) {
			strafeCd = (phase() >= 2 ? 300 : 460) + random.nextInt(100);
			setDeltaMovement(getDeltaMovement().scale(0.3));
			endMove();
		}
	}

	private void tickDive(ServerLevel level) {
		if (diveSpot == null) {
			endMove();
			return;
		}
		if (moveTicks <= DIVE_MARK) {
			setDeltaMovement(getDeltaMovement().scale(0.6));
			faceToward(diveSpot);
			if (moveTicks % 3 == 0) {
				ring(level, diveSpot.add(0, 0.15, 0), 3.5, RED, 24);
			}
			return;
		}
		Vec3 to = diveSpot.subtract(position());
		double d = to.length();
		if (d > 1.0 && !onGround()) {
			setDeltaMovement(to.scale(Math.min(DIVE_SPEED, d) / d));
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.4, getZ(), 4, 0.2, 0.2, 0.2, 0.03);
			if (moveTicks < DIVE_MARK + 40) {
				return;
			}
		}
		// impact
		setFlying(false);
		setDeltaMovement(Vec3.ZERO);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 30, 2.0, 0.2, 2.0, 0.1);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.8f);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1.5, 3.5), e -> victim(e, this))) {
			if (e.distanceToSqr(this) <= 3.5 * 3.5 + 2) {
				e.hurt(damageSources().mobAttack(this), 14.0f);
				Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				e.knockback(1.4, -away.x, -away.z);
			}
		}
		diveCd = 120 + random.nextInt(40);
		begin(MOVE_GROUNDED);
	}

	private void ring(ServerLevel level, Vec3 at, double r, DustParticleOptions dust, int n) {
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(dust, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/** Fires one missile at {@code target} from alternating forearm pods. */
	public KestrelMissileEntity fireMissile(ServerLevel level, LivingEntity target, int index) {
		KestrelMissileEntity m = SkyFortressEntityTypes.KESTREL_MISSILE.create(level);
		if (m == null) {
			return null;
		}
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 from = position().add(0, 1.9, 0).add(right.scale(index % 2 == 0 ? 0.7 : -0.7)).add(fwd.scale(0.4));
		Vec3 up = new Vec3(0, 0.55, 0).add(right.scale(index % 2 == 0 ? 0.3 : -0.3)).add(fwd.scale(0.3));
		m.setOwner(this);
		m.setTarget(target);
		m.damage = 6.0f;
		m.moveTo(from.x, from.y, from.z, getYRot(), -45f);
		m.setDeltaMovement(up.normalize().scale(KestrelMissileEntity.SPEED));
		level.addFreshEntity(m);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.HOSTILE, 1.2f, 0.9f + random.nextFloat() * 0.3f);
		return m;
	}

	/** Fighter drones sweep in round her. Returns how many came. */
	public int callDrones(ServerLevel level) {
		int near = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48), m -> m.isAlive() && m.getTags().contains(DRONE_TAG)).size();
		int players = Math.max(1, level.getPlayers(p -> p.distanceToSqr(this) < 48 * 48 && !p.isSpectator()).size());
		int want = Math.min(maxDrones() - near, 2 + Math.max(0, players - 1));
		int came = 0;
		Vec3 c = home != null ? Vec3.atBottomCenterOf(home) : position();
		for (int i = 0; i < want; i++) {
			Mob d = UltronEntityTypes.DRONE.create(level);
			if (d == null) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2;
			d.moveTo(c.x + Math.cos(a) * 14, c.y + 6 + random.nextInt(3), c.z + Math.sin(a) * 14, random.nextFloat() * 360f, 0f);
			d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			d.addTag(DRONE_TAG);
			d.setCustomName(Component.translatable("entity.projecthero.kestrel_drone"));
			d.setPersistenceRequired();
			if (getTarget() != null) {
				d.setTarget(getTarget());
			}
			level.addFreshEntity(d);
			level.sendParticles(ParticleTypes.CLOUD, d.getX(), d.getY(), d.getZ(), 10, 0.4, 0.3, 0.4, 0.05);
			came++;
		}
		if (came > 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.8f, 1.3f);
		}
		return came;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("SkyHome", NbtUtils.writeBlockPos(home));
		}
		tag.putByte("Phase", (byte) phase());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "SkyHome").orElse(null));
		int ph = tag.getByte("Phase");
		if (ph >= 2) {
			entityData.set(DATA_PHASE, (byte) 2);
			setFlying(true);
			bar.setColor(BossEvent.BossBarColor.RED);
			bar.setName(Component.translatable("projecthero.sky_fortress.kestrel.air_bar").withStyle(ChatFormatting.RED));
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 4, this::predicate));
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop("animation.admiral_kestrel." + name);
	}

	private static RawAnimation once(String name) {
		return RawAnimation.begin().thenPlayAndHold("animation.admiral_kestrel." + name);
	}

	private PlayState predicate(AnimationState<AdmiralKestrelBoss> state) {
		if (isDeadOrDying()) {
			return state.setAndContinue(once("death"));
		}
		String wings = phase() >= 2 ? "_open" : "";
		switch (move()) {
			case MOVE_DASH -> {
				return state.setAndContinue(once("dash"));
			}
			case MOVE_MISSILES -> {
				return state.setAndContinue(once("missiles"));
			}
			case MOVE_STRAFE -> {
				return state.setAndContinue(loop("strafe"));
			}
			case MOVE_DRONES -> {
				return state.setAndContinue(once("radio"));
			}
			case MOVE_DIVE -> {
				return state.setAndContinue(once("dive"));
			}
			case MOVE_GROUNDED -> {
				return state.setAndContinue(loop("recover"));
			}
			case MOVE_TRANSFORM -> {
				return state.setAndContinue(once("transform"));
			}
			default -> {
			}
		}
		if (flying()) {
			return state.setAndContinue(loop("hover" + wings));
		}
		if (state.isMoving() || state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(loop("walk" + wings));
		}
		return state.setAndContinue(loop("idle" + wings));
	}

	/** Every move as one goal above the melee, so they never overlap. */
	static final class MovesGoal extends Goal {
		private final AdmiralKestrelBoss mob;

		MovesGoal(AdmiralKestrelBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move() != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.gap > 0) {
				return false;
			}
			if (mob.phase() < 2 && !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.strafeCd <= 0) {
				mob.startStrafe();
				return true;
			}
			if (mob.droneCd <= 0 && mob.level() instanceof ServerLevel s
					&& s.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(48), m -> m.isAlive() && m.getTags().contains(DRONE_TAG)).size() < mob.maxDrones()) {
				mob.startDrones();
				return true;
			}
			if (mob.phase() >= 2) {
				if (mob.diveCd <= 0 && mob.random.nextInt(2) == 0) {
					mob.startDive();
					return true;
				}
			} else if (mob.dashCd <= 0 && d >= 6 && d <= 20 && sees && mob.random.nextInt(2) == 0) {
				mob.startDash();
				return true;
			}
			if (mob.missileCd <= 0 && d >= (mob.phase() >= 2 ? 4 : 8) && d <= 36 && sees) {
				mob.startMissiles();
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.move() != MOVE_NONE && mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level) {
				mob.tickMove(level);
			}
		}

		@Override
		public void stop() {
			if (mob.move() != MOVE_NONE && mob.move() != MOVE_TRANSFORM) {
				mob.endMove();
			}
		}
	}
}
