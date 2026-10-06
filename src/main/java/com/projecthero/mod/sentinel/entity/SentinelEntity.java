package com.projecthero.mod.sentinel.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.sentinel.SentinelConfig;
import com.projecthero.mod.sentinel.SentinelPurgeEvent;
import com.projecthero.mod.sentinel.item.SentinelItems;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * v0.15.1: a <b>Sentinel</b> -- the Sentinel Program's main unit. About three and a half blocks tall (a 2-block GeckoLib
 * model at {@link #SCALE}). Since v0.15.3 the model is the user's Blockbench Sentinel -- a player-skin rig in the classic
 * magenta-and-blue armour with burning red eyes and a glowing chest gem ({@code scratchpad/gen_v0153_sentinel.js}).
 * 90 health, 10 armour.
 * <ul>
 *   <li><b>Arrival</b>: purge Sentinels come down out of the sky on their boot thrusters ({@link #startArrival}) and land
 *       with a jolt.</li>
 *   <li><b>Chest beam</b>: rears back and fires three magenta bolts from its chest emitter (3 x 3 damage).</li>
 *   <li><b>Palm blast</b>: swings its right arm at the target and fires one heavier, leading bolt (7).</li>
 *   <li><b>Grab and slam</b>: reaches for anything within arm's length, lifts it overhead, holds it, then drives it
 *       into the ground (4 + 14). It works in the air too: a flyer it catches is hurled downward instead.</li>
 *   <li><b>Flight</b>: when its prey is high above it or out of reach, it takes off and chases on its thrusters,
 *       still firing, and lands again when the prey does.</li>
 *   <li><b>Carriers</b> (40%) also release a pair of Sentinel Drones from the pack on their back every 20 seconds.</li>
 * </ul>
 * The attack clips play on the "action" controller under a synced busy flag that stands the looping body controller
 * down (the Oathbreaker / Bone Tyrant lesson); the timings below mirror {@code scratchpad/gen_v0153_sentinel.js}.
 */
public class SentinelEntity extends SentinelRobot {
	/** The geo model is authored two blocks tall; the SCALE attribute makes it ~3.5 (and the hit-box follows). */
	public static final float SCALE = 1.75f;
	public static final int DEATH_TICKS = 60;

	// clip timings (ticks): length, key frame
	static final int CHEST_LEN = 24, CHEST_FIRE = 12;
	static final int HAND_LEN = 20, HAND_FIRE = 10;
	static final int GRAB_LEN = 36, GRAB_REACH = 8, GRAB_SLAM = 28;
	static final int DEPLOY_LEN = 30, DEPLOY_RELEASE = 16;

	public enum Attack { NONE, CHEST_BEAM, PALM_BLAST, GRAB, DEPLOY }

	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_CARRIER = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);

	private Attack attack = Attack.NONE;
	private int attackTicks;
	private int globalCooldown = 30;
	private int beamCooldown = 40;
	private int grabCooldown = 60;
	private int deployCooldown = 200;
	private UUID grabbed;
	private boolean arriving;
	private int unreachableTicks;
	private int meleeHold;
	private final List<UUID> drones = new ArrayList<>();

	public SentinelEntity(EntityType<? extends SentinelEntity> type, Level level) {
		super(type, level);
		this.xpReward = 30;
		refreshDimensions(); // the SCALE attribute sizes its hit-box
	}

	public static AttributeSupplier.Builder createAttributes() {
		SentinelConfig.Sentinel cfg = SentinelConfig.sentinel();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.SAFE_FALL_DISTANCE, 256.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BUSY, false);
		builder.define(DATA_FLYING, false);
		builder.define(DATA_CARRIER, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return attack == Attack.NONE && !isFlying() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return attack == Attack.NONE && !isFlying() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return getTarget() == null && !isFlying() && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	/** Rolls whether this Sentinel is a drone carrier (call once, on spawning). */
	public void rollCarrier() {
		entityData.set(DATA_CARRIER, random.nextDouble() < SentinelConfig.sentinel().carrierChance);
	}

	public void setCarrier(boolean carrier) {
		entityData.set(DATA_CARRIER, carrier);
	}

	public boolean isCarrier() {
		return entityData.get(DATA_CARRIER);
	}

	public boolean isFlying() {
		return entityData.get(DATA_FLYING);
	}

	public boolean isBusy() {
		return entityData.get(DATA_BUSY);
	}

	public boolean isArriving() {
		return arriving;
	}

	public Attack currentAttack() {
		return attack;
	}

	/** Comes down out of the sky on its thrusters from wherever it is now. */
	public void startArrival() {
		arriving = true;
		setFlying(true);
	}

	private void setFlying(boolean flying) {
		entityData.set(DATA_FLYING, flying);
		setNoGravity(flying);
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (globalCooldown > 0) globalCooldown--;
		if (beamCooldown > 0) beamCooldown--;
		if (grabCooldown > 0) grabCooldown--;
		if (deployCooldown > 0) deployCooldown--;
		if (meleeHold > 0) meleeHold--;
		LivingEntity target = getTarget();
		if (arriving) {
			tickArrival(server, target);
		} else {
			tickFlight(server, target);
		}
		if (attack != Attack.NONE) {
			tickAttack(server, target);
		} else if (!arriving && target != null && globalCooldown <= 0) {
			chooseAttack(server, target);
		}
		syncBusy();
		if (isFlying() && tickCount % 2 == 0) {
			thrusterFx(server);
		}
		if (!isFlying() && onGround() && tickCount % 14 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.002) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.2f, 0.6f);
		}
		if (tickCount % 40 == 0) {
			drones.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
		}
	}

	/** The thruster descent: fast high up, braking near the ground, a jolt on landing. */
	private void tickArrival(ServerLevel server, LivingEntity target) {
		int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
		double above = getY() - ground;
		if (onGround() || above <= 0.15 || verticalCollision && getDeltaMovement().y > -0.05) {
			arriving = false;
			setFlying(false);
			server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 2, 0.6, 0.1, 0.6, 0);
			server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.1, getZ(), 12, 1.0, 0.1, 1.0, 0.02);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.5f);
			return;
		}
		double vy = -Math.min(0.85, Math.max(0.14, above * 0.09));
		Vec3 drift = Vec3.ZERO;
		if (target != null) {
			Vec3 to = target.position().subtract(position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (flat.lengthSqr() > 36) {
				drift = flat.normalize().scale(0.08);
			}
			getLookControl().setLookAt(target, 30f, 30f);
		}
		setDeltaMovement(drift.x, vy, drift.z);
		fallDistance = 0;
	}

	/** Chase-flight: up after a target high above or out of reach, down again once it lands near. */
	private void tickFlight(ServerLevel server, LivingEntity target) {
		boolean flying = isFlying();
		if (target == null) {
			if (flying) {
				setFlying(false);
			}
			unreachableTicks = 0;
			return;
		}
		double dy = target.getY() - getY();
		boolean targetUp = !target.onGround() && dy > 5.0;
		if (!flying) {
			if (getNavigation().isDone() && distanceToSqr(target) > 16 * 16) {
				unreachableTicks++;
			} else {
				unreachableTicks = Math.max(0, unreachableTicks - 1);
			}
			if ((targetUp || unreachableTicks > 60) && attack == Attack.NONE) {
				setFlying(true);
				unreachableTicks = 0;
				setDeltaMovement(getDeltaMovement().add(0, 0.6, 0));
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.5f);
			}
			return;
		}
		Vec3 flat = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
		if (target.onGround() && flat.length() < 7.0 && dy < 2.0) {
			setFlying(false); // drop back down beside it (no fall damage)
			return;
		}
		getNavigation().stop();
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5 + 1.0, 0);
		Vec3 offset = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(-4.0);
		Vec3 dir = aim.add(offset).subtract(position());
		double len = dir.length();
		double speed = attack == Attack.NONE ? 0.5 : 0.12;
		Vec3 desired = len < 0.5 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.3) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.2));
		getLookControl().setLookAt(target, 30f, 30f);
		float yaw = (float) (Math.atan2(aim.z - getZ(), aim.x - getX()) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		fallDistance = 0;
	}

	private void thrusterFx(ServerLevel server) {
		Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 side = new Vec3(-look.z, 0, look.x).scale(getBbWidth() * 0.32);
		for (Vec3 foot : new Vec3[] { position().add(side), position().subtract(side) }) {
			server.sendParticles(ParticleTypes.FLAME, foot.x, foot.y - 0.1, foot.z, 2, 0.06, 0.05, 0.06, 0.01);
			server.sendParticles(ParticleTypes.SMOKE, foot.x, foot.y - 0.4, foot.z, 1, 0.08, 0.1, 0.08, 0.01);
		}
		if (tickCount % 10 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 0.8f, 0.6f);
		}
	}

	// ---------------------------------------------------------------- attacks

	private void chooseAttack(ServerLevel server, LivingEntity target) {
		SentinelConfig.Sentinel cfg = SentinelConfig.sentinel();
		double d = distanceTo(target);
		boolean sight = hasLineOfSight(target);
		double reach = getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 2.2;
		if (grabCooldown <= 0 && d <= reach && canBeGrabbed(target)) {
			begin(Attack.GRAB, "grab");
			grabCooldown = cfg.grabCooldownTicks + random.nextInt(60);
			return;
		}
		if (isCarrier() && deployCooldown <= 0 && drones.size() < cfg.dronesPerDeploy && random.nextFloat() < 0.5f) {
			begin(Attack.DEPLOY, "deploy");
			deployCooldown = cfg.deployCooldownTicks + random.nextInt(100);
			return;
		}
		if (beamCooldown <= 0 && sight && d <= cfg.beamRange && d > 3.0) {
			boolean chest = random.nextBoolean();
			begin(chest ? Attack.CHEST_BEAM : Attack.PALM_BLAST, chest ? "beam_chest" : "beam_hand");
			beamCooldown = cfg.beamCooldownTicks + random.nextInt(30);
		}
	}

	private void begin(Attack a, String clip) {
		attack = a;
		attackTicks = 0;
		if (!isFlying()) {
			getNavigation().stop();
		}
		triggerAnim("action", clip);
		syncBusy();
	}

	/** Test/debug: start {@code a} now, regardless of range and cooldowns. */
	public void forceAttack(Attack a) {
		switch (a) {
			case CHEST_BEAM -> begin(a, "beam_chest");
			case PALM_BLAST -> begin(a, "beam_hand");
			case GRAB -> begin(a, "grab");
			case DEPLOY -> begin(a, "deploy");
			default -> {
			}
		}
	}

	private static boolean canBeGrabbed(LivingEntity target) {
		return !(target instanceof SentinelRobot) && target.getBbHeight() <= 2.6f && !target.isPassenger();
	}

	private void tickAttack(ServerLevel server, LivingEntity target) {
		attackTicks++;
		if (target != null && attack != Attack.GRAB) {
			getLookControl().setLookAt(target, 40f, 40f);
			if (!isFlying()) {
				lookAt(target, 40f, 40f);
				yBodyRot = getYRot();
			}
		}
		SentinelConfig.Sentinel cfg = SentinelConfig.sentinel();
		switch (attack) {
			case CHEST_BEAM -> {
				if (target != null && (attackTicks == CHEST_FIRE || attackTicks == CHEST_FIRE + 2 || attackTicks == CHEST_FIRE + 4)) {
					Vec3 from = position().add(0, getBbHeight() * 0.68, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(getBbWidth() * 0.5));
					double speed = 1.7;
					SentinelBeamEntity.fire(server, this, SentinelBeamEntity.Kind.BEAM, from,
							SentinelBeamEntity.leadAim(target, from, speed).subtract(from), cfg.chestBeamDamage / 3.0f, speed);
				}
				if (attackTicks >= CHEST_LEN) {
					finish();
				}
			}
			case PALM_BLAST -> {
				if (target != null && attackTicks == HAND_FIRE) {
					Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
					Vec3 side = new Vec3(-look.z, 0, look.x); // points to its right
					// v0.15.3: out of the user's model's real right palm (the old model's "right" arm was on its left)
					Vec3 from = position().add(0, getBbHeight() * 0.7, 0).add(look.scale(getBbWidth() * 0.9)).add(side.scale(getBbWidth() * 0.45));
					double speed = 1.9;
					SentinelBeamEntity.fire(server, this, SentinelBeamEntity.Kind.BEAM, from,
							SentinelBeamEntity.leadAim(target, from, speed).subtract(from), cfg.palmBlastDamage, speed);
				}
				if (attackTicks >= HAND_LEN) {
					finish();
				}
			}
			case GRAB -> tickGrab(server, target, cfg);
			case DEPLOY -> {
				if (attackTicks == DEPLOY_RELEASE) {
					releaseDrones(server, cfg.dronesPerDeploy - drones.size());
				}
				if (attackTicks >= DEPLOY_LEN) {
					finish();
				}
			}
			default -> finish();
		}
	}

	private void tickGrab(ServerLevel server, LivingEntity target, SentinelConfig.Sentinel cfg) {
		if (attackTicks == GRAB_REACH && target != null && grabbed == null) {
			double reach = getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 2.6;
			if (distanceTo(target) <= reach && canBeGrabbed(target)) {
				grabbed = target.getUUID();
				target.hurt(damageSources().mobAttack(this), cfg.grabDamage);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
		}
		LivingEntity held = grabbedEntity(server);
		if (grabbed != null && (held == null || !held.isAlive())) {
			grabbed = null;
		}
		if (held != null && attackTicks > GRAB_REACH && attackTicks < GRAB_SLAM) {
			Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
			Vec3 hold = position().add(0, getBbHeight() * 0.92, 0).add(look.scale(getBbWidth() * 0.55));
			moveHeld(held, hold);
		}
		if (attackTicks == GRAB_SLAM && held != null) {
			Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
			if (isFlying()) {
				// caught in the air: hurled straight down
				held.setDeltaMovement(look.x * 0.4, -1.6, look.z * 0.4);
			} else {
				Vec3 slam = position().add(look.scale(getBbWidth() * 0.5 + 1.2));
				int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(slam.x), (int) Math.floor(slam.z));
				moveHeld(held, new Vec3(slam.x, Math.max(ground, getY() - 1), slam.z));
				held.setDeltaMovement(look.x * 0.3, 0.35, look.z * 0.3);
			}
			held.hurtMarked = true;
			held.hurt(damageSources().mobAttack(this), cfg.slamDamage);
			if (held instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
			server.sendParticles(ParticleTypes.EXPLOSION, held.getX(), held.getY(), held.getZ(), 1, 0, 0, 0, 0);
			server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, held.getX(), held.getY(), held.getZ(), 8, 0.6, 0.1, 0.6, 0.02);
			server.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.6f, 0.5f);
			grabbed = null;
		}
		if (attackTicks >= GRAB_LEN) {
			grabbed = null;
			finish();
		}
	}

	private static void moveHeld(LivingEntity held, Vec3 at) {
		if (held instanceof ServerPlayer sp) {
			sp.teleportTo(at.x, at.y, at.z);
		} else {
			held.setPos(at.x, at.y, at.z);
		}
		held.setDeltaMovement(Vec3.ZERO);
		held.fallDistance = 0;
		held.hurtMarked = true;
	}

	public LivingEntity grabbedEntity(ServerLevel server) {
		return grabbed == null ? null : server.getEntity(grabbed) instanceof LivingEntity l ? l : null;
	}

	public boolean isHolding(UUID id) {
		return id.equals(grabbed);
	}

	/** Releases up to {@code count} Sentinel Drones from its back pack (the purge's cap permitting). */
	public int releaseDrones(ServerLevel server, int count) {
		SentinelPurgeEvent purge = purgeId() == null ? null : SentinelPurgeEvent.find(server, purgeId());
		int made = 0;
		Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
		for (int i = 0; i < count; i++) {
			if (purge != null && !purge.hasRoom(server)) {
				break;
			}
			SentinelDroneEntity drone = SentinelEntityTypes.SENTINEL_DRONE.create(server);
			if (drone == null) {
				break;
			}
			Vec3 side = new Vec3(-look.z, 0, look.x).scale(i % 2 == 0 ? 1.0 : -1.0);
			Vec3 at = position().add(0, getBbHeight() * 0.75, 0).subtract(look.scale(getBbWidth() * 0.7)).add(side);
			drone.moveTo(at.x, at.y, at.z, getYRot(), 0);
			drone.finalizeSpawn(server, server.getCurrentDifficultyAt(drone.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			drone.setDeltaMovement(side.scale(0.3).add(0, 0.5, 0).subtract(look.scale(0.3)));
			if (getTarget() != null) {
				drone.setTarget(getTarget());
			}
			if (purge != null) {
				drone.bindToPurge(purge.id());
				purge.own(drone);
			}
			server.addFreshEntity(drone);
			drones.add(drone.getUUID());
			server.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.05);
			made++;
		}
		if (made > 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1.4f, 0.6f);
		}
		return made;
	}

	private void finish() {
		attack = Attack.NONE;
		attackTicks = 0;
		globalCooldown = 20 + random.nextInt(20);
		syncBusy();
	}

	private void syncBusy() {
		boolean busy = attack != Attack.NONE || meleeHold > 0 || isDeadOrDying();
		if (entityData.get(DATA_BUSY) != busy) {
			entityData.set(DATA_BUSY, busy);
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit) {
			triggerAnim("action", "beam_hand"); // a backhand with the same arm
			meleeHold = 12;
			syncBusy();
			target.setDeltaMovement(target.getDeltaMovement().add(0, 0.25, 0));
		}
		return hit;
	}

	// ---------------------------------------------------------------- sound, death, loot

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BEACON_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	public float getVoicePitch() {
		return 0.6f;
	}

	@Override
	protected float getSoundVolume() {
		return 1.4f;
	}

	@Override
	public void die(DamageSource source) {
		if (dead || isRemoved()) {
			return;
		}
		grabbed = null;
		attack = Attack.NONE;
		super.die(source);
		setFlying(false);
		arriving = false;
		getNavigation().stop();
		triggerAnim("action", "death");
		syncBusy();
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Vec3 mid = position().add(0, getBbHeight() * 0.5, 0);
		if (deathTime % 4 == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 6, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.15);
			server.sendParticles(ParticleTypes.SMOKE, mid.x, mid.y + 0.5, mid.z, 3, 0.3, 0.3, 0.3, 0.02);
		}
		if (deathTime == 30) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.8f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (SentinelItems.SENTINEL_CIRCUITRY != null && random.nextFloat() < 0.45f) {
			spawnAtLocation(new ItemStack(SentinelItems.SENTINEL_CIRCUITRY, 1));
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return purgeId() == null && super.removeWhenFarAway(distanceSq);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Carrier", isCarrier());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_CARRIER, tag.getBoolean("Carrier"));
		// flights and attacks run on timers that aren't saved: never come back frozen in the air or mid-move
		arriving = false;
		setFlying(false);
		attack = Attack.NONE;
	}

	// ---------------------------------------------------------------- GeckoLib

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.sentinel.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.sentinel.walk");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.sentinel.fly");
	public static final String[] ACTION_CLIPS = { "beam_chest", "beam_hand", "grab", "deploy", "death" };

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<SentinelEntity> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
		for (String clip : ACTION_CLIPS) {
			action.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.sentinel." + clip));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<SentinelEntity> state) {
		if (isDeadOrDying() || isBusy()) {
			return PlayState.STOP;
		}
		if (isFlying()) {
			return state.setAndContinue(FLY);
		}
		return state.setAndContinue(state.getLimbSwingAmount() > 0.05f ? WALK : IDLE);
	}
}
