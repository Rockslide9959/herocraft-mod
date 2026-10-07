package com.projecthero.mod.ultron.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronDialogue;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.UltronUprising;

import net.minecraft.core.BlockPos;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: the <b>Ultron Sentry</b> -- body two, the giant. The player model at 3.4x (about 6 blocks tall) in his
 * Skindex skin; slow and heavy-handed. 1,500 health for one fighter (+400 for each extra, up to 4,000), 14 armour.
 * <ul>
 *   <li><b>Arm slam</b> (close): both fists up, then down -- a shockwave in front of him.</li>
 *   <li><b>Chest cannon</b>: a 2-second charge (his chest glows, the light gathers) then a wide red beam down a locked
 *       line. Move off the line while it charges.</li>
 *   <li><b>Missile rain</b>: red missiles fall out of the sky round the fighters.</li>
 *   <li><b>Shield phase</b> at half health: he goes invulnerable and three Ultron Drones carry the shield (red tethers).
 *       Kill all three to break it -- he staggers, taking half again as much damage for a few seconds.</li>
 * </ul>
 * Dying, he falls, his head cracks open, and the uprising ends: "Ultron has been purged from the network."
 */
public class UltronSentryEntity extends UltronRobot {
	public static final float SCALE = 3.4f;
	public static final byte ACTION_SLAM_UP = 2;
	public static final byte ACTION_SLAM = 3;
	public static final byte ACTION_CHARGE = 4;
	public static final byte ACTION_CANNON = 5;
	public static final byte ACTION_RAIN = 6;
	public static final byte ACTION_STAGGER = 7;
	public static final int DEATH_TICKS = 70;
	private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(UltronSentryEntity.class, EntityDataSerializers.BOOLEAN);

	public enum Move { NONE, SLAM, CANNON, RAIN }

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.ultron_sentry"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private Move move = Move.NONE;
	private int moveTick;
	private int cooldown = 60;
	private int lastMove = -1;
	private boolean shieldUsed;
	private boolean enraged;
	private boolean introduced;
	private int stagger;
	private long lastLine = -1000;
	private Vec3 cannonDir;
	private final List<UUID> shieldDrones = new ArrayList<>();

	public UltronSentryEntity(EntityType<? extends UltronSentryEntity> type, Level level) {
		super(type, level);
		this.xpReward = 600;
		refreshDimensions(); // the SCALE attribute sizes his hit-box
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Sentry cfg = UltronConfig.sentry();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 2.0)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 32.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHIELD, false);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.SENTRY;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0f));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof ServerPlayer sp) {
			UltronCombat.drainSuit(sp, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
		}
		return hit;
	}

	/** Health for {@code fighters} fighters (full). */
	public void configure(int fighters) {
		double hp = UltronConfig.sentryHealthFor(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public boolean shielded() {
		return entityData.get(DATA_SHIELD);
	}

	public Move move() {
		return move;
	}

	public List<UUID> shieldDrones() {
		return shieldDrones;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (shielded()) {
			tickShield(server);
		}
		if (stagger > 0) {
			stagger--;
			getNavigation().stop();
			setAction(stagger > 0 ? ACTION_STAGGER : ACTION_NONE);
			return;
		}
		if (isStunned()) {
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && !UltronCombat.canTarget(target)) {
			setTarget(null);
			target = null;
		}
		if (target == null && tickCount % 20 == 0) {
			Player p = UltronCombat.nearestPlayer(server, position(), 56);
			if (p != null) {
				setTarget(p);
				target = p;
			}
		}
		if (!introduced && target instanceof Player) {
			introduced = true;
			lastLine = UltronDialogue.say(server, this, "sentry_intro", true, lastLine);
		}
		if (move != Move.NONE) {
			tickMove(server, target);
			return;
		}
		if (target != null && --cooldown <= 0) {
			double d = distanceTo(target);
			List<Move> options = new ArrayList<>();
			if (d < 6.5) {
				options.add(Move.SLAM);
				options.add(Move.SLAM);
			}
			if (d > 4 && hasLineOfSight(target)) {
				options.add(Move.CANNON);
			}
			options.add(Move.RAIN);
			if (options.size() > 1 && lastMove >= 0) {
				options.remove(Move.values()[lastMove]);
			}
			startMove(server, options.get(random.nextInt(options.size())), target);
		}
	}

	/** Starts {@code m} at once (also the gametests' and harness's hook). */
	public void startMove(ServerLevel server, Move m, LivingEntity target) {
		move = m;
		moveTick = 0;
		lastMove = m.ordinal();
		getNavigation().stop();
		switch (m) {
			case SLAM -> setAction(ACTION_SLAM_UP);
			case CANNON -> {
				setAction(ACTION_CHARGE);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.5f);
				lastLine = UltronDialogue.say(server, this, "cannon", false, lastLine);
			}
			case RAIN -> {
				setAction(ACTION_RAIN);
				lastLine = UltronDialogue.say(server, this, "rain", false, lastLine);
			}
			default -> setAction(ACTION_NONE);
		}
	}

	private void endMove() {
		move = Move.NONE;
		setAction(ACTION_NONE);
		cooldown = (int) ((UltronConfig.sentry().attackCooldownTicks + random.nextInt(30)) * (enraged ? 0.65 : 1.0));
	}

	private void tickMove(ServerLevel server, LivingEntity target) {
		moveTick++;
		getNavigation().stop();
		UltronConfig.Sentry cfg = UltronConfig.sentry();
		switch (move) {
			case SLAM -> {
				if (target != null) {
					getLookControl().setLookAt(target, 20f, 20f);
				}
				if (moveTick == 22) {
					setAction(ACTION_SLAM);
					slam(server, cfg);
				}
				if (moveTick >= 34) {
					endMove();
				}
			}
			case CANNON -> tickCannon(server, target, cfg);
			case RAIN -> {
				if (moveTick % 4 == 0 && moveTick / 4 <= cfg.missileCount) {
					rainMissile(server, cfg);
				}
				if (moveTick > cfg.missileCount * 4 + 10) {
					endMove();
				}
			}
			default -> endMove();
		}
	}

	private void slam(ServerLevel server, UltronConfig.Sentry cfg) {
		Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 c = position().add(fwd.scale(2.2));
		BlockState ground = server.getBlockState(BlockPos.containing(c).below());
		if (!ground.isAir()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, 80, cfg.slamRadius * 0.4, 0.1,
					cfg.slamRadius * 0.4, 0.3);
		}
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.4, c.z, 1, 0, 0, 0, 0);
		UltronFx.ring(server, UltronFx.RED, c.add(0, 0.3, 0), cfg.slamRadius, 36);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.5f);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.slamRadius + 2, 2, cfg.slamRadius + 2),
				UltronCombat::canTarget)) {
			if (e.distanceToSqr(c.x, e.getY(), c.z) <= cfg.slamRadius * cfg.slamRadius) {
				UltronCombat.hit(server, this, e, cfg.slamDamage);
				Vec3 push = e.position().subtract(c).normalize().scale(1.0);
				e.push(push.x, 0.9, push.z);
				e.hurtMarked = true;
			}
		}
	}

	/** Where the chest cannon fires from. */
	public Vec3 chest() {
		return position().add(0, getBbHeight() * 0.66, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(0.5 * SCALE * 0.35));
	}

	private void tickCannon(ServerLevel server, LivingEntity target, UltronConfig.Sentry cfg) {
		Vec3 from = chest();
		if (moveTick <= cfg.cannonChargeTicks) {
			if (target != null) {
				getLookControl().setLookAt(target, 10f, 10f);
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
				cannonDir = aim.subtract(from).normalize();
			}
			// light gathering into the chest
			for (int i = 0; i < 3; i++) {
				Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(2.5);
				Vec3 p = from.add(off);
				Vec3 v = off.scale(-0.12);
				server.sendParticles(UltronFx.RED, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
			}
			if (moveTick > cfg.cannonChargeTicks - 12 && cannonDir != null && moveTick % 2 == 0) {
				UltronFx.beam(server, from, beamEnd(server, from, cannonDir, cfg.cannonRange), UltronFx.TELEGRAPH, 3);
			}
			if (moveTick % 8 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.5f,
						0.5f + moveTick / (float) cfg.cannonChargeTicks);
			}
			if (moveTick == cfg.cannonChargeTicks) {
				setAction(ACTION_CANNON);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.7f);
			}
			return;
		}
		if (cannonDir == null) {
			cannonDir = getViewVector(1f);
		}
		Vec3 to = beamEnd(server, from, cannonDir, cfg.cannonRange);
		if (moveTick % 2 == 0) {
			UltronFx.beam(server, from, to, UltronFx.CANNON, 3);
			server.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 1, 0.3, 0.3, 0.3, 0);
		}
		if (moveTick % 5 == 0) {
			for (LivingEntity e : UltronCombat.alongLine(server, from, to, 1.2)) {
				UltronCombat.hit(server, this, e, cfg.cannonDamage);
				e.push(cannonDir.x * 0.5, 0.15, cannonDir.z * 0.5);
				e.hurtMarked = true;
			}
		}
		if (moveTick >= cfg.cannonChargeTicks + cfg.cannonFireTicks) {
			endMove();
		}
	}

	private Vec3 beamEnd(ServerLevel server, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		var hit = server.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
	}

	private void rainMissile(ServerLevel server, UltronConfig.Sentry cfg) {
		List<Player> ps = UltronCombat.playersNear(server, position(), 48);
		Vec3 aim;
		if (!ps.isEmpty()) {
			Player p = ps.get(random.nextInt(ps.size()));
			aim = p.position().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
		} else if (getTarget() != null) {
			aim = getTarget().position().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
		} else {
			aim = position().add(random.nextGaussian() * 8, 0, random.nextGaussian() * 8);
		}
		Vec3 from = aim.add(random.nextGaussian() * 2, 22, random.nextGaussian() * 2);
		Vec3 dir = aim.subtract(from).normalize();
		IronManMissileEntity m = new IronManMissileEntity(server, this, dir.scale(1.1)).withDamage(cfg.missileDamage, cfg.missileSplash)
				.withBlastRadius(1.8f).withRedTint();
		m.setPos(from.x, from.y, from.z);
		server.addFreshEntity(m);
		Vec3 launch = position().add(0, getBbHeight() * 0.9, 0);
		server.sendParticles(UltronFx.RED, launch.x, launch.y, launch.z, 4, 0.6, 0.3, 0.6, 0.05);
		server.playSound(null, launch.x, launch.y, launch.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.5f, 0.6f);
	}

	// ---------------------------------------------------------------- shield phase

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (shielded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
				Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
				server.sendParticles(UltronFx.RED, c.x, c.y, c.z, 12, getBbWidth() * 0.8, getBbHeight() * 0.4, getBbWidth() * 0.8, 0.02);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
			return false;
		}
		if (stagger > 0) {
			amount *= 1.5f;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server && !isDeadOrDying()) {
			float f = getHealth() / getMaxHealth();
			if (!shieldUsed && f <= 0.5f) {
				raiseShield(server);
			}
			if (!enraged && f <= 0.25f) {
				enraged = true;
				bossBar.setColor(BossEvent.BossBarColor.PURPLE);
				lastLine = UltronDialogue.say(server, this, "sentry_enrage", true, lastLine);
			}
		}
		return hurt;
	}

	/** At half health: invulnerable, and the shield drones come out (also a test / harness hook). */
	public void raiseShield(ServerLevel server) {
		shieldUsed = true;
		entityData.set(DATA_SHIELD, true);
		move = Move.NONE;
		setAction(ACTION_NONE);
		shieldDrones.clear();
		UltronUprising up = uprising();
		int n = Math.max(1, UltronConfig.sentry().shieldDrones);
		for (int i = 0; i < n; i++) {
			UltronDroneEntity d = UltronEntityTypes.DRONE.create(server);
			if (d == null) {
				continue;
			}
			double a = i * Math.PI * 2 / n;
			Vec3 at = position().add(Math.cos(a) * 4, getBbHeight() * 0.6, Math.sin(a) * 4);
			d.moveTo(at.x, at.y, at.z, 0f, 0f);
			d.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
			d.carryShieldFor(this);
			if (up != null) {
				up.adopt(d);
			}
			server.addFreshEntity(d);
			shieldDrones.add(d.getUUID());
			server.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		bossBar.setName(Component.translatable("event.projecthero.ultron.bar.shield"));
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		lastLine = UltronDialogue.say(server, this, "shield", true, lastLine);
		for (ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 64 * 64) {
				JarvisDialogue.speak(p, "ultron_shield");
			}
		}
	}

	private void tickShield(ServerLevel server) {
		int alive = 0;
		for (UUID id : shieldDrones) {
			Entity e = server.getEntity(id);
			if (e instanceof UltronDroneEntity d && d.isAlive() && !d.isDeadOrDying()) {
				alive++;
			}
		}
		if (tickCount % 5 == 0) {
			Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
			for (int i = 0; i < 6; i++) {
				Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian() * 1.4, random.nextGaussian()).normalize().scale(getBbHeight() * 0.55);
				server.sendParticles(UltronFx.RED_SMALL, c.x + off.x, c.y + off.y, c.z + off.z, 1, 0, 0, 0, 0);
			}
		}
		if (alive == 0) {
			breakShield(server);
		}
	}

	/** All shield drones are down: the shield breaks and he staggers. */
	public void breakShield(ServerLevel server) {
		entityData.set(DATA_SHIELD, false);
		shieldDrones.clear();
		stagger = 80;
		setAction(ACTION_STAGGER);
		bossBar.setName(Component.translatable("entity.projecthero.ultron_sentry"));
		Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
		UltronFx.forced(server, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		UltronFx.forced(server, UltronFx.RED_BIG, c.x, c.y, c.z, 60, 2.0, 3.0, 2.0, 0.2);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
		lastLine = UltronDialogue.say(server, this, "shield_break", true, lastLine);
	}

	// ---------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			lastLine = UltronDialogue.say(server, this, "death", true, lastLine);
		}
		super.die(source);
		entityData.set(DATA_SHIELD, false);
		bossBar.removeAllPlayers();
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server) || isRemoved()) {
			return;
		}
		Vec3 head = position().add(0, getBbHeight() * 0.5, 0);
		if (deathTime % 5 == 0) {
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 6, 1.5, 0.6, 1.5, 0.02);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 8, 1.5, 0.6, 1.5, 0.2);
		}
		if (deathTime == 30 || deathTime == 45) {
			// his head cracks
			UltronFx.forced(server, UltronFx.RED_BIG, head.x, head.y, head.z, 30, 0.8, 0.8, 0.8, 0.1);
			UltronFx.forced(server, ParticleTypes.EXPLOSION, head.x, head.y, head.z, 3, 0.6, 0.6, 0.6, 0);
			server.playSound(null, head.x, head.y, head.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
			server.playSound(null, head.x, head.y, head.z, SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
		if (deathTime >= DEATH_TICKS) {
			UltronFx.forced(server, ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 3, 1.5, 0.5, 1.5, 0);
			UltronFx.wreck(server, position().add(0, 1, 0), 4.0);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	// ---------------------------------------------------------------- bar, save

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("ShieldUsed", shieldUsed);
		tag.putBoolean("Shielded", shielded());
		tag.putBoolean("Enraged", enraged);
		ListTag list = new ListTag();
		for (UUID u : shieldDrones) {
			list.add(NbtUtils.createUUID(u));
		}
		tag.put("ShieldDrones", list);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		shieldUsed = tag.getBoolean("ShieldUsed");
		entityData.set(DATA_SHIELD, tag.getBoolean("Shielded"));
		enraged = tag.getBoolean("Enraged");
		shieldDrones.clear();
		ListTag list = tag.getList("ShieldDrones", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			shieldDrones.add(NbtUtils.loadUUID(list.get(i)));
		}
		if (shielded()) {
			bossBar.setName(Component.translatable("event.projecthero.ultron.bar.shield"));
		}
	}
}
