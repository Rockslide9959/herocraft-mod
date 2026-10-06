package com.projecthero.mod.sentinel.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.sentinel.SentinelConfig;
import com.projecthero.mod.sentinel.SentinelPurgeEvent;
import com.projecthero.mod.sentinel.SentinelTargets;
import com.projecthero.mod.sentinel.item.SentinelItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * v0.15.1: <b>Master Mold</b> -- the Sentinel factory, and the Sentinel Purge's final boss. Eleven blocks of purple and
 * magenta armour (a 2-block GeckoLib model at {@link #SCALE}): slow, heavily armoured (14 armour, 8 toughness, immovable)
 * with 1,400 health for one fighter plus 400 per extra fighter (capped at 4,000 -- the Darkseid / horde boss bracket).
 * <ul>
 *   <li><b>Stomp</b>: a foot raised high and slammed down -- a shockwave round its feet (18, knocks up).</li>
 *   <li><b>Sweep</b>: a flat backhand across everything in front of it (20, big knockback).</li>
 *   <li><b>Purge Beam</b>: a 1.5 s charge (its eyes gather light), then a 2 s beam from its face that slowly tracks
 *       its prey up to 56 blocks (6 every quarter second; blocks stop it -- cover works).</li>
 *   <li><b>Fabricate</b>: its chest hatch swings open and a freshly built Sentinel launches out (two in phase 3), up to
 *       a cap per phase.</li>
 *   <li><b>Phases</b> at two thirds and one third: it roars (untouchable for two seconds), looses a swarm of drones and
 *       gets faster and quicker on the trigger.</li>
 * </ul>
 * Death keeps vanilla's bookkeeping ({@code super.die}) so the purge sees it die at once, then a 5 s chain of explosions
 * (particles only -- no block damage) before it's gone.
 */
public class MasterMoldEntity extends SentinelRobot {
	/** The geo model is authored two blocks tall; the SCALE attribute makes it ~11 (and the hit-box follows). */
	public static final float SCALE = 5.5f;
	public static final int DEATH_TICKS = 100;

	// clip timings (ticks): mirror scratchpad/gen_sentinel_v0151.js
	static final int STOMP_LEN = 34, STOMP_HIT = 20;
	static final int SWEEP_LEN = 30, SWEEP_HIT = 16;
	static final int BEAM_LEN = 80, BEAM_FIRE = 30, BEAM_END = 70;
	static final int DEPLOY_LEN = 44, DEPLOY_LAUNCH = 24;
	static final int ROAR_LEN = 40, ROAR_PEAK = 16;

	public enum Attack { NONE, STOMP, SWEEP, BEAM, DEPLOY, ROAR }

	/** 0 = off, 1 = charging, 2 = firing. */
	private static final EntityDataAccessor<Byte> DATA_BEAM = SynchedEntityData.defineId(MasterMoldEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Vector3f> DATA_BEAM_TARGET = SynchedEntityData.defineId(MasterMoldEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(MasterMoldEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(MasterMoldEntity.class, EntityDataSerializers.BYTE);
	private static final DustParticleOptions MAGENTA = new DustParticleOptions(new Vector3f(1.0f, 0.25f, 0.85f), 2.5f);

	private Attack attack = Attack.NONE;
	private int attackTicks;
	private int attackCooldown = 40;
	private int deployCooldown = 160;
	private Vec3 beamPoint;
	private boolean arriving;
	private boolean introduced;
	private final List<UUID> fabricated = new ArrayList<>();
	private EventBossBar bossBar;

	public MasterMoldEntity(EntityType<? extends MasterMoldEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		setPersistenceRequired();
		refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		SentinelConfig.MasterMold cfg = SentinelConfig.masterMold();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, cfg.sweepDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.STEP_HEIGHT, 3.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 512.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Health for {@code fighters} fighters (from the config), full. */
	public void configure(int fighters) {
		double hp = SentinelConfig.masterMoldHealthFor(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BEAM, (byte) 0);
		builder.define(DATA_BEAM_TARGET, new Vector3f());
		builder.define(DATA_BUSY, false);
		builder.define(DATA_PHASE, (byte) 1);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected double huntRange() {
		return 72.0;
	}

	@Override
	protected Component speakerName() {
		return Component.translatable("entity.projecthero.master_mold");
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	public byte beamState() {
		return entityData.get(DATA_BEAM);
	}

	public Vec3 beamTarget() {
		Vector3f v = entityData.get(DATA_BEAM_TARGET);
		return new Vec3(v.x, v.y, v.z);
	}

	public boolean isBusy() {
		return entityData.get(DATA_BUSY);
	}

	public Attack currentAttack() {
		return attack;
	}

	/** Where the Purge Beam comes from: its face. */
	public Vec3 beamOrigin() {
		return position().add(0, getBbHeight() * 0.82, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(getBbWidth() * 0.3));
	}

	/** Descends out of the sky from where it is now (the purge drops it in). */
	public void startArrival() {
		arriving = true;
		setNoGravity(true);
	}

	public boolean isArriving() {
		return arriving;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (attackCooldown > 0) attackCooldown--;
		if (deployCooldown > 0) deployCooldown--;
		LivingEntity target = getTarget();
		if (arriving) {
			tickArrival(server);
		} else {
			if (!introduced && target instanceof Player) {
				introduced = true;
				say(server, "intro");
			}
			checkPhase(server);
			if (attack != Attack.NONE) {
				tickAttack(server, target);
			} else if (target != null) {
				approach(target);
				if (attackCooldown <= 0) {
					chooseAttack(server, target);
				}
			}
		}
		syncBusy();
		if (tickCount % 4 == 0) {
			updateBossBar(server);
		}
		if (tickCount % 40 == 0) {
			fabricated.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
		}
		if (!arriving && onGround() && tickCount % 24 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 3.0f, 0.3f);
			server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.1, getZ(), 2, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.005);
		}
		if (phase() >= 3 && tickCount % 5 == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, getBbWidth() * 0.4, getBbHeight() * 0.2, getBbWidth() * 0.4, 0.1);
		}
	}

	private void tickArrival(ServerLevel server) {
		int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
		double above = getY() - ground;
		if (onGround() || above <= 0.2) {
			arriving = false;
			setNoGravity(false);
			land(server);
			return;
		}
		setDeltaMovement(0, -Math.min(0.9, Math.max(0.18, above * 0.06)), 0);
		fallDistance = 0;
		if (tickCount % 2 == 0) {
			for (int i = 0; i < 4; i++) {
				double a = i * Math.PI / 2 + tickCount * 0.1;
				double x = getX() + Math.cos(a) * getBbWidth() * 0.35, z = getZ() + Math.sin(a) * getBbWidth() * 0.35;
				server.sendParticles(ParticleTypes.FLAME, x, getY(), z, 3, 0.2, 0.1, 0.2, 0.02);
				server.sendParticles(ParticleTypes.LARGE_SMOKE, x, getY() - 1.0, z, 2, 0.3, 0.3, 0.3, 0.02);
			}
		}
		if (tickCount % 10 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 3.0f, 0.3f);
		}
	}

	/** Touchdown: a shockwave round the landing site. */
	private void land(ServerLevel server) {
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 2, 1.5, 0.2, 1.5, 0);
		server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), getX(), getY() + 0.2, getZ(),
				120, getBbWidth(), 0.2, getBbWidth(), 0.3);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.4f);
		shockwave(server, getBbWidth() * 0.5 + 6.0, SentinelConfig.masterMold().stompDamage * 0.5f, 0.6);
	}

	/** Walks straight at a target that's out of melee reach (it is far too big for the path-finder's corridors). */
	private void approach(LivingEntity target) {
		double flat = Math.sqrt(Math.pow(target.getX() - getX(), 2) + Math.pow(target.getZ() - getZ(), 2));
		if (flat > getBbWidth() * 0.5 + 5.0) {
			getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
		} else {
			getNavigation().stop();
		}
		getLookControl().setLookAt(target, 20f, 20f);
	}

	private void checkPhase(ServerLevel server) {
		float frac = getHealth() / getMaxHealth();
		int want = frac <= 1f / 3f ? 3 : frac <= 2f / 3f ? 2 : 1;
		if (want > phase() && attack != Attack.BEAM) {
			if (attack != Attack.NONE) {
				endBeam();
			}
			entityData.set(DATA_PHASE, (byte) want);
			begin(Attack.ROAR, "roar");
			say(server, "phase" + want);
		}
	}

	private int cooldownFor(int base) {
		return (int) Math.round(base * (phase() >= 3 ? 0.65 : phase() == 2 ? 0.82 : 1.0));
	}

	private int fabricationCap() {
		int[] caps = SentinelConfig.masterMold().sentinelCap;
		return caps.length == 0 ? 0 : caps[Math.min(caps.length, phase()) - 1];
	}

	public int liveFabricated(ServerLevel server) {
		fabricated.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
		return fabricated.size();
	}

	// ---------------------------------------------------------------- attacks

	private void chooseAttack(ServerLevel server, LivingEntity target) {
		SentinelConfig.MasterMold cfg = SentinelConfig.masterMold();
		double flat = Math.sqrt(Math.pow(target.getX() - getX(), 2) + Math.pow(target.getZ() - getZ(), 2)) - getBbWidth() * 0.5;
		boolean sight = hasLineOfSight(target);
		if (deployCooldown <= 0 && liveFabricated(server) < fabricationCap() && random.nextFloat() < 0.6f) {
			begin(Attack.DEPLOY, "deploy");
			deployCooldown = cooldownFor(cfg.deployCooldownTicks);
			return;
		}
		Attack pick;
		if (flat <= cfg.stompRadius - 1.0) {
			pick = random.nextBoolean() ? Attack.STOMP : Attack.SWEEP;
		} else if (flat <= cfg.sweepReach) {
			pick = random.nextFloat() < 0.6f ? Attack.SWEEP : (sight ? Attack.BEAM : Attack.STOMP);
		} else if (sight && flat <= cfg.beamRange) {
			pick = Attack.BEAM;
		} else {
			return; // keep walking
		}
		forceAttack(pick);
	}

	/** Test/debug: start {@code a} now, regardless of range and cooldowns. */
	public void forceAttack(Attack a) {
		switch (a) {
			case STOMP -> begin(a, "stomp");
			case SWEEP -> begin(a, "sweep");
			case BEAM -> begin(a, "beam");
			case DEPLOY -> begin(a, "deploy");
			case ROAR -> begin(a, "roar");
			default -> {
			}
		}
	}

	private void begin(Attack a, String clip) {
		attack = a;
		attackTicks = 0;
		getNavigation().stop();
		getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
		triggerAnim("action", clip);
		syncBusy();
	}

	private void tickAttack(ServerLevel server, LivingEntity target) {
		attackTicks++;
		SentinelConfig.MasterMold cfg = SentinelConfig.masterMold();
		if (target != null && attack != Attack.BEAM) {
			getLookControl().setLookAt(target, 15f, 15f);
			if (attackTicks < 10) {
				lookAt(target, 12f, 12f);
				yBodyRot = getYRot();
			}
		}
		switch (attack) {
			case STOMP -> {
				if (attackTicks == STOMP_HIT) {
					Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
					Vec3 side = new Vec3(-look.z, 0, look.x);
					Vec3 foot = position().add(side.scale(getBbWidth() * 0.22)).add(look.scale(getBbWidth() * 0.15));
					shockwaveAt(server, foot, cfg.stompRadius, cfg.stompDamage, 0.9);
					server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, foot.x, foot.y + 0.5, foot.z, 1, 0, 0, 0, 0);
					ring(server, foot, cfg.stompRadius);
					server.playSound(null, foot.x, foot.y, foot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.5f, 0.45f);
				}
				if (attackTicks >= STOMP_LEN) finish(cfg);
			}
			case SWEEP -> {
				if (attackTicks == SWEEP_HIT) {
					sweep(server, cfg);
				}
				if (attackTicks >= SWEEP_LEN) finish(cfg);
			}
			case BEAM -> tickBeam(server, target, cfg);
			case DEPLOY -> {
				if (attackTicks == DEPLOY_LAUNCH) {
					fabricate(server, phase() >= 3 ? 2 : 1);
				}
				if (attackTicks >= DEPLOY_LEN) finish(cfg);
			}
			case ROAR -> {
				if (attackTicks == ROAR_PEAK) {
					server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4.0f, 0.5f);
					server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4.0f, 0.4f);
					releaseDrones(server, 4);
				}
				if (attackTicks >= ROAR_LEN) finish(cfg);
			}
			default -> finish(cfg);
		}
	}

	private void tickBeam(ServerLevel server, LivingEntity target, SentinelConfig.MasterMold cfg) {
		Vec3 eye = beamOrigin();
		if (beamPoint == null) {
			beamPoint = target != null ? target.getEyePosition() : eye.add(Vec3.directionFromRotation(0, yBodyRot).scale(20));
		}
		if (target != null) {
			// the beam point creeps after the target: dodge sideways and it can't keep up
			double turn = phase() >= 3 ? 0.55 : 0.35;
			Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Vec3 delta = want.subtract(beamPoint);
			double len = delta.length();
			if (len > 1.0e-4) {
				beamPoint = beamPoint.add(delta.scale(Math.min(len, turn + len * 0.04) / len));
			}
			float yaw = (float) (Math.atan2(beamPoint.z - getZ(), beamPoint.x - getX()) * (180.0 / Math.PI)) - 90.0f;
			setYRot(Mth.approachDegrees(getYRot(), yaw, 4f));
			yBodyRot = getYRot();
			getLookControl().setLookAt(beamPoint.x, beamPoint.y, beamPoint.z, 10f, 10f);
		}
		Vec3 end = beamEnd(server, eye);
		entityData.set(DATA_BEAM_TARGET, new Vector3f((float) end.x, (float) end.y, (float) end.z));
		if (attackTicks < BEAM_FIRE) {
			entityData.set(DATA_BEAM, (byte) 1);
			if (attackTicks % 3 == 0) {
				server.sendParticles(MAGENTA, eye.x, eye.y, eye.z, 6, 1.2, 1.2, 1.2, 0.0);
				server.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 3, 0.8, 0.8, 0.8, 0.02);
			}
			if (attackTicks == 2) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0f, 0.5f);
			}
		} else if (attackTicks < BEAM_END) {
			entityData.set(DATA_BEAM, (byte) 2);
			if (attackTicks == BEAM_FIRE) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 0.5f);
			}
			if ((attackTicks - BEAM_FIRE) % 5 == 0) {
				beamDamage(server, eye, end, cfg.beamDamage);
				server.sendParticles(MAGENTA, end.x, end.y, end.z, 8, 0.4, 0.4, 0.4, 0.05);
				server.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 1, 0.2, 0.2, 0.2, 0.0);
			}
			if (attackTicks % 8 == 0) {
				server.playSound(null, end.x, end.y, end.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.2f, 0.6f);
			}
		} else {
			endBeam();
		}
		if (attackTicks >= BEAM_LEN) {
			finish(cfg);
		}
	}

	/** The beam runs from {@code eye} through {@link #beamPoint} until it meets a block (or its range runs out). */
	private Vec3 beamEnd(ServerLevel server, Vec3 eye) {
		Vec3 dir = beamPoint.subtract(eye);
		if (dir.lengthSqr() < 1.0e-6) {
			dir = Vec3.directionFromRotation(0, yBodyRot);
		}
		Vec3 tip = eye.add(dir.normalize().scale(SentinelConfig.masterMold().beamRange));
		var hit = server.clip(new ClipContext(eye, tip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? tip : hit.getLocation();
	}

	/** Everything the beam passes through (within 1.2 blocks of its line) takes {@code damage}. */
	private void beamDamage(ServerLevel server, Vec3 from, Vec3 to, float damage) {
		AABB box = new AABB(from, to).inflate(1.5);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, SentinelTargets::canTarget)) {
			if (e.getBoundingBox().inflate(1.2).clip(from, to).isPresent()) {
				e.hurt(damageSources().indirectMagic(this, this), damage);
			}
		}
	}

	private void endBeam() {
		entityData.set(DATA_BEAM, (byte) 0);
		beamPoint = null;
	}

	private void sweep(ServerLevel server, SentinelConfig.MasterMold cfg) {
		Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
		double reach = getBbWidth() * 0.5 + cfg.sweepReach;
		AABB box = getBoundingBox().inflate(reach, 2.0, reach);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, SentinelTargets::canTarget)) {
			Vec3 to = e.position().subtract(position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double d = flat.length();
			if (d > reach || d < 1.0e-3 || flat.normalize().dot(look) < Math.cos(Math.toRadians(75)) || e.getY() > getY() + getBbHeight() * 0.8) {
				continue;
			}
			e.hurt(damageSources().mobAttack(this), cfg.sweepDamage);
			Vec3 push = flat.normalize().scale(2.2);
			e.setDeltaMovement(push.x, 0.55, push.z);
			e.hurtMarked = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
		}
		Vec3 arc = position().add(0, getBbHeight() * 0.4, 0).add(look.scale(reach * 0.6));
		server.sendParticles(ParticleTypes.SWEEP_ATTACK, arc.x, arc.y, arc.z, 6, reach * 0.3, 0.5, reach * 0.3, 0);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.4f);
	}

	private void shockwave(ServerLevel server, double radius, float damage, double lift) {
		shockwaveAt(server, position(), radius, damage, lift);
	}

	private void shockwaveAt(ServerLevel server, Vec3 at, double radius, float damage, double lift) {
		AABB box = new AABB(at.x - radius, at.y - 1, at.z - radius, at.x + radius, at.y + 4, at.z + radius);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, SentinelTargets::canTarget)) {
			Vec3 flat = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
			double d = flat.length();
			if (d > radius) {
				continue;
			}
			float scaled = (float) (damage * (1.0 - 0.5 * d / radius));
			e.hurt(damageSources().mobAttack(this), scaled);
			Vec3 out = d < 1.0e-3 ? Vec3.ZERO : flat.normalize().scale(0.9);
			e.setDeltaMovement(out.x, lift, out.z);
			e.hurtMarked = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
		}
	}

	private static void ring(ServerLevel server, Vec3 at, double radius) {
		BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2 / 48;
			for (double r = 2.0; r <= radius; r += radius / 3) {
				server.sendParticles(dirt, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.1);
			}
		}
	}

	/** Builds and launches {@code count} Sentinels from its chest hatch (never past the phase cap). */
	public int fabricate(ServerLevel server, int count) {
		SentinelPurgeEvent purge = purgeId() == null ? null : SentinelPurgeEvent.find(server, purgeId());
		int room = fabricationCap() - liveFabricated(server);
		Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
		int made = 0;
		for (int i = 0; i < Math.min(count, room); i++) {
			if (purge != null && !purge.hasRoom(server)) {
				break;
			}
			SentinelEntity s = SentinelEntityTypes.SENTINEL.create(server);
			if (s == null) {
				break;
			}
			Vec3 side = new Vec3(-look.z, 0, look.x).scale(i == 0 ? 0.0 : 2.5);
			Vec3 at = position().add(0, getBbHeight() * 0.42, 0).add(look.scale(getBbWidth() * 0.55 + 1.0)).add(side);
			s.moveTo(at.x, at.y, at.z, yBodyRot, 0);
			s.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
			s.rollCarrier();
			s.startArrival();
			s.setDeltaMovement(look.scale(0.6));
			if (getTarget() != null) {
				s.setTarget(getTarget());
			}
			if (purge != null) {
				s.bindToPurge(purge.id());
				purge.own(s);
			}
			server.addFreshEntity(s);
			fabricated.add(s.getUUID());
			server.sendParticles(MAGENTA, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.1);
			server.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.6, 0.6, 0.6, 0.08);
			made++;
		}
		if (made > 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 3.0f, 0.4f);
			say(server, "fabricate");
		}
		return made;
	}

	private void releaseDrones(ServerLevel server, int count) {
		SentinelPurgeEvent purge = purgeId() == null ? null : SentinelPurgeEvent.find(server, purgeId());
		for (int i = 0; i < count; i++) {
			if (purge != null && !purge.hasRoom(server)) {
				break;
			}
			SentinelDroneEntity drone = SentinelEntityTypes.SENTINEL_DRONE.create(server);
			if (drone == null) {
				break;
			}
			double a = i * Math.PI * 2 / count + random.nextDouble() * 0.4;
			Vec3 at = position().add(Math.cos(a) * getBbWidth() * 0.6, getBbHeight() * 0.8, Math.sin(a) * getBbWidth() * 0.6);
			drone.moveTo(at.x, at.y, at.z, random.nextFloat() * 360f, 0);
			drone.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
			drone.setDeltaMovement(Math.cos(a) * 0.5, 0.4, Math.sin(a) * 0.5);
			if (getTarget() != null) {
				drone.setTarget(getTarget());
			}
			if (purge != null) {
				drone.bindToPurge(purge.id());
				purge.own(drone);
			}
			server.addFreshEntity(drone);
		}
	}

	private void finish(SentinelConfig.MasterMold cfg) {
		if (attack == Attack.BEAM) {
			endBeam();
		}
		attack = Attack.NONE;
		attackTicks = 0;
		attackCooldown = cooldownFor(cfg.attackCooldownTicks) + random.nextInt(20);
		syncBusy();
	}

	private void syncBusy() {
		boolean busy = attack != Attack.NONE || isDeadOrDying();
		if (entityData.get(DATA_BUSY) != busy) {
			entityData.set(DATA_BUSY, busy);
		}
	}

	private void say(ServerLevel server, String line) {
		Component msg = Component.translatable("sentinel.projecthero.chat", speakerName(),
				Component.translatable("sentinel.projecthero.master_mold.say." + line)).withStyle(ChatFormatting.LIGHT_PURPLE);
		for (ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 96 * 96) {
				p.sendSystemMessage(msg);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if ((attack == Attack.ROAR || arriving) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return false; // it never uses vanilla melee: every hit is one of its attacks
	}

	// ---------------------------------------------------------------- boss bar (only outside a purge: the purge has its own)

	private void updateBossBar(ServerLevel server) {
		if (purgeId() != null || isRemoved() || isDeadOrDying()) {
			return;
		}
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_6, true);
		}
		bossBar.update(server, blockPosition(), 72.0, Component.translatable("boss.projecthero.master_mold.bar", phase()),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- sound, death, save

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BEACON_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	public float getVoicePitch() {
		return 0.4f;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0f;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		if (dead || isRemoved()) {
			return;
		}
		endBeam();
		attack = Attack.NONE;
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
			say(server, "death");
		}
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (deathTime % 8 == 0 && deathTime < DEATH_TICKS - 10) {
			Vec3 p = position().add((random.nextDouble() - 0.5) * getBbWidth(), random.nextDouble() * getBbHeight() * 0.8,
					(random.nextDouble() - 0.5) * getBbWidth());
			server.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 12, 0.8, 0.8, 0.8, 0.2);
			server.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f + random.nextFloat() * 0.3f);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
			Vec3 mid = position().add(0, getBbHeight() * 0.4, 0);
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 4, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
			server.sendParticles(MAGENTA, mid.x, mid.y, mid.z, 80, getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.2);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0f, 0.4f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		// a purge pays its fighters directly; a Master Mold from a spawn egg drops its salvage on the spot
		if (purgeId() == null && SentinelItems.SENTINEL_CIRCUITRY != null) {
			spawnAtLocation(new ItemStack(SentinelItems.SENTINEL_CIRCUITRY, 4 + random.nextInt(5)));
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server && bossBar != null) {
			bossBar.clear(server);
		}
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("MoldPhase", (byte) phase());
		tag.putBoolean("MoldIntroduced", introduced);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_PHASE, (byte) Math.max(1, Math.min(3, tag.getByte("MoldPhase"))));
		introduced = tag.getBoolean("MoldIntroduced");
		arriving = false;
		setNoGravity(false);
		attack = Attack.NONE;
		endBeam();
	}

	// ---------------------------------------------------------------- GeckoLib

	public static final String[] ACTION_CLIPS = { "stomp", "sweep", "beam", "deploy", "roar", "death" };
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.master_mold.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.master_mold.walk");

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<MasterMoldEntity> action = new AnimationController<>(this, "action", 4, state -> PlayState.STOP);
		for (String clip : ACTION_CLIPS) {
			action.triggerableAnim(clip, RawAnimation.begin().thenPlay("animation.master_mold." + clip));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 6, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<MasterMoldEntity> state) {
		if (isDeadOrDying() || isBusy()) {
			return PlayState.STOP;
		}
		return state.setAndContinue(state.getLimbSwingAmount() > 0.03f ? WALK : IDLE);
	}
}
