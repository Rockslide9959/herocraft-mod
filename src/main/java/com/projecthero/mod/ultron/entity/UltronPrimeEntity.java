package com.projecthero.mod.ultron.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.ironman.item.IronManArmorItem;
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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: <b>Ultron Prime</b> -- body one. The player model at 1.4x in his Skindex skin; he flies. 900 health for one
 * fighter (+300 for each extra, up to 3,000), 12 armour. His moves (phase 1 above two-thirds health, 2 above a third, 3
 * below; enraged below a third -- shorter gaps):
 * <ul>
 *   <li><b>Repulsor barrage</b>: five quick red beams, spread across the fighters.</li>
 *   <li><b>Encephalo-beam</b>: a red line shows where it starts for a second, then a held face laser sweeps across
 *       (12 damage a second while it's on you) -- get out of its path.</li>
 *   <li><b>Drone swarm</b>: calls 4 + fighters Ultron Drones.</li>
 *   <li><b>Magnetic pull</b> (phase 2+): yanks anyone in iron, chainmail, netherite or Iron Man armour to him, then
 *       punches.</li>
 *   <li><b>Dive bomb</b> (phase 3): up into the sky, then straight down into a 6-block shockwave.</li>
 * </ul>
 * <b>Body jump</b>: at 0 health, if a relay pylon still stands, his head "uploads": a red streak flies to the pylon, the
 * pylon is consumed and he rebuilds there at half health. With none left he falls, and the uprising raises the Sentry.
 */
public class UltronPrimeEntity extends UltronRobot {
	public static final float SCALE = 1.4f;
	public static final byte ACTION_BARRAGE = 2;
	public static final byte ACTION_TELEGRAPH = 3;
	public static final byte ACTION_BEAM = 4;
	public static final byte ACTION_SWARM = 5;
	public static final byte ACTION_PULL = 6;
	public static final byte ACTION_PUNCH = 7;
	public static final byte ACTION_DIVE_UP = 8;
	public static final byte ACTION_DIVE_DOWN = 9;
	public static final byte ACTION_UPLOAD = 10;
	public static final int JUMP_TICKS = 30;

	public enum Move { NONE, BARRAGE, ENCEPHALO, SWARM, PULL, DIVE }

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.ultron_prime"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private Move move = Move.NONE;
	/** v0.15.19: the shared threat table -- he turns on whoever is actually hurting him; a move in flight finishes first. */
	private final BossThreat threat = new BossThreat(this).range(72).filter(UltronCombat::canTarget).holdWhile(() -> move != Move.NONE);

	/** v0.15.19: his threat table (tests / debug). */
	public BossThreat threat() {
		return threat;
	}
	private int moveTick;
	private int cooldown = 40;
	private int lastMove = -1;
	private boolean enraged;
	private boolean introduced;
	private long lastLine = -1000;
	private int bodyIndex = 1;
	// move state
	private final List<UUID> barrageTargets = new ArrayList<>();
	private float sweepYaw;
	private float sweepPitch;
	private Vec3 diveAt;
	// body jump
	private int jumpTicks = -1;
	private UUID jumpPylon;
	private Vec3 jumpFrom;
	private Vec3 jumpTo;
	private final float bobPhase;

	public UltronPrimeEntity(EntityType<? extends UltronPrimeEntity> type, Level level) {
		super(type, level);
		this.xpReward = 300;
		this.bobPhase = random.nextFloat() * 100f;
		refreshDimensions(); // the SCALE attribute sizes his hit-box
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Prime cfg = UltronConfig.prime();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.PRIME;
	}

	@Override
	protected void registerGoals() {
	}

	/** Health for {@code fighters} fighters (full). */
	public void configure(int fighters) {
		double hp = UltronConfig.primeHealthFor(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public int phase() {
		float f = getHealth() / getMaxHealth();
		return f > 0.66f ? 1 : f > 0.33f ? 2 : 3;
	}

	public Move move() {
		return move;
	}

	public int bodyIndex() {
		return bodyIndex;
	}

	public boolean isJumping() {
		return jumpTicks >= 0;
	}

	public boolean enraged() {
		return enraged;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isNoAi()) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (isJumping()) {
			tickJump(server);
			return;
		}
		if (isStunned()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && (!UltronCombat.canTarget(target) || distanceToSqr(target) > 72 * 72)) {
			setTarget(null);
			target = null;
		}
		// v0.15.19: whoever is hurting him most takes his attention (BossThreat); the old "nearest player every 5 s"
		// re-pick only runs while nobody has hurt him lately, so a kiting player can no longer cover a friend's free hits.
		LivingEntity hot = threat.tick();
		if (hot != null) {
			target = hot;
		}
		if ((target == null || tickCount % 100 == 0 && threat.isEmpty()) && tickCount % 10 == 0) {
			Player p = UltronCombat.nearestPlayer(server, position(), 56);
			if (p != null) {
				setTarget(p);
				target = p;
			}
		}
		if (!introduced && target instanceof Player) {
			introduced = true;
			say(server, bodyIndex == 1 ? "intro" : "jump_done", true);
		}
		if (move != Move.NONE) {
			tickMove(server, target);
		} else {
			hover(server, target);
			if (target != null && --cooldown <= 0) {
				pickMove(server, target);
			}
		}
		if (tickCount % 3 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() - 0.1, getZ(), 1, 0.15, 0.02, 0.15, 0.0);
		}
		if (enraged && tickCount % 4 == 0) {
			server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.4, 0.6, 0.4, 0.0);
		}
	}

	/** Holds a point ~9 blocks off the target and a few above the ground, drifting round it. */
	private void hover(ServerLevel server, LivingEntity target) {
		setAction(ACTION_NONE);
		Vec3 want;
		if (target != null) {
			Vec3 away = position().subtract(target.position());
			double ang = Math.atan2(away.z, away.x) + 0.012 * Mth.sin((tickCount + bobPhase) * 0.02f) * 20;
			double r = 9.0;
			int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(target.getX() + Math.cos(ang) * r),
					Mth.floor(target.getZ() + Math.sin(ang) * r));
			double y = Math.max(ground, target.getY()) + 4.5 + Math.sin((tickCount + bobPhase) * 0.06) * 0.8;
			want = new Vec3(target.getX() + Math.cos(ang) * r, y, target.getZ() + Math.sin(ang) * r);
			face(target.getEyePosition());
		} else {
			want = position().add(0, Math.sin((tickCount + bobPhase) * 0.06) * 0.05, 0);
		}
		steer(want, 0.35);
	}

	private void steer(Vec3 want, double speed) {
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		Vec3 desired = len < 0.3 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.25) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.18));
	}

	private void face(Vec3 at) {
		double dx = at.x - getX();
		double dz = at.z - getZ();
		double dy = at.y - getEyeY();
		float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setXRot(pitch);
	}

	private void pickMove(ServerLevel server, LivingEntity target) {
		int phase = phase();
		List<Move> options = new ArrayList<>(List.of(Move.BARRAGE, Move.ENCEPHALO, Move.SWARM));
		if (phase >= 2 && anyMetalNear(server)) {
			options.add(Move.PULL);
		}
		if (phase >= 3) {
			options.add(Move.DIVE);
			options.add(Move.DIVE);
		}
		if (options.size() > 1 && lastMove >= 0) {
			options.remove(Move.values()[lastMove]);
		}
		if (countDronesNear(server) >= 10) {
			options.remove(Move.SWARM);
		}
		Move pick = options.isEmpty() ? Move.BARRAGE : options.get(random.nextInt(options.size()));
		startMove(server, pick, target);
	}

	/** Starts {@code m} at once (also the gametests' and harness's hook). */
	public void startMove(ServerLevel server, Move m, LivingEntity target) {
		move = m;
		moveTick = 0;
		lastMove = m.ordinal();
		switch (m) {
			case BARRAGE -> {
				barrageTargets.clear();
				List<Player> ps = UltronCombat.playersNear(server, position(), 40);
				Collections.shuffle(ps, new java.util.Random(random.nextLong()));
				for (Player p : ps) {
					barrageTargets.add(p.getUUID());
				}
				if (barrageTargets.isEmpty() && target != null) {
					barrageTargets.add(target.getUUID());
				}
				setAction(ACTION_BARRAGE);
			}
			case ENCEPHALO -> {
				Vec3 to = (target != null ? target.getEyePosition() : position().add(getViewVector(1f).scale(10))).subtract(headPos());
				float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
				sweepSide = random.nextBoolean() ? 1 : -1;
				sweepYaw = yaw - sweepSide * 35f;
				sweepPitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
				setAction(ACTION_TELEGRAPH);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 0.6f);
			}
			case SWARM -> {
				setAction(ACTION_SWARM);
				say(server, "swarm", false);
			}
			case PULL -> {
				setAction(ACTION_PULL);
				say(server, "pull", false);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.8f);
			}
			case DIVE -> {
				setAction(ACTION_DIVE_UP);
				say(server, "dive", false);
			}
			default -> setAction(ACTION_NONE);
		}
	}

	private void endMove() {
		move = Move.NONE;
		setAction(ACTION_NONE);
		UltronConfig.Prime cfg = UltronConfig.prime();
		cooldown = (int) ((cfg.attackCooldownTicks + random.nextInt(25)) * (enraged ? 0.6 : 1.0));
	}

	private void tickMove(ServerLevel server, LivingEntity target) {
		moveTick++;
		UltronConfig.Prime cfg = UltronConfig.prime();
		switch (move) {
			case BARRAGE -> {
				setDeltaMovement(getDeltaMovement().scale(0.8));
				if (target != null) {
					face(target.getEyePosition());
				}
				if (moveTick >= 8 && (moveTick - 8) % 3 == 0) {
					int shot = (moveTick - 8) / 3;
					if (shot < cfg.barrageBeams && !barrageTargets.isEmpty()) {
						Entity e = server.getEntity(barrageTargets.get(shot % barrageTargets.size()));
						if (e instanceof LivingEntity le && le.isAlive()) {
							Vec3 from = palm(shot % 2 == 0);
							Vec3 dir = le.position().add(0, le.getBbHeight() * 0.55, 0).subtract(from);
							UltronCombat.hitscan(server, this, from, dir.normalize().add(random.nextGaussian() * 0.015, 0, random.nextGaussian() * 0.015),
									48, cfg.barrageDamage, UltronFx.BOLT, 5, 0.3f);
							server.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0f, 1.8f);
						}
					}
					if (shot >= cfg.barrageBeams) {
						endMove();
					}
				}
			}
			case ENCEPHALO -> tickEncephalo(server, cfg);
			case SWARM -> {
				setDeltaMovement(getDeltaMovement().scale(0.7));
				if (moveTick == 15) {
					callSwarm(server);
				}
				if (moveTick >= 25) {
					endMove();
				}
			}
			case PULL -> tickPull(server, cfg);
			case DIVE -> tickDive(server, target, cfg);
			default -> endMove();
		}
	}

	// ---------------------------------------------------------------- encephalo-beam

	public Vec3 headPos() {
		return position().add(0, getBbHeight() * 0.82, 0);
	}

	private Vec3 sweepDir(float yaw) {
		return Vec3.directionFromRotation(sweepPitch, yaw);
	}

	private void tickEncephalo(ServerLevel server, UltronConfig.Prime cfg) {
		setDeltaMovement(getDeltaMovement().scale(0.6));
		int tele = cfg.encephaloTelegraphTicks;
		int sweep = cfg.encephaloSweepTicks;
		float yaw = moveTick <= tele ? sweepYaw : sweepYaw + sweepSide * 70f * Math.min(1f, (moveTick - tele) / (float) sweep);
		Vec3 dir = sweepDir(yaw);
		Vec3 from = headPos().add(dir.scale(0.3));
		face(from.add(dir.scale(10)));
		Vec3 to = beamEnd(server, from, dir, 36);
		if (moveTick <= tele) {
			if (moveTick % 2 == 1) {
				UltronFx.beam(server, from, to, UltronFx.TELEGRAPH, 3);
			}
			if (moveTick == tele) {
				setAction(ACTION_BEAM);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 2.0f, 0.7f);
			}
			return;
		}
		if (moveTick % 2 == 0) {
			UltronFx.beam(server, from, to, UltronFx.ENCEPHALO, 3);
			server.sendParticles(UltronFx.RED, to.x, to.y, to.z, 3, 0.2, 0.2, 0.2, 0.02);
		}
		if (moveTick % 5 == 0) {
			float dmg = cfg.encephaloDamagePerSecond / 4f;
			for (LivingEntity e : UltronCombat.alongLine(server, from, to, 0.5)) {
				UltronCombat.hit(server, this, e, dmg);
			}
		}
		if (moveTick >= tele + sweep) {
			endMove();
		}
	}

	/** Which way the encephalo-beam sweeps (+1 / -1). */
	private int sweepSide = 1;

	private Vec3 beamEnd(ServerLevel server, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		var hit = server.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
	}

	// ---------------------------------------------------------------- swarm

	private int countDronesNear(ServerLevel server) {
		return server.getEntitiesOfClass(UltronDroneEntity.class, getBoundingBox().inflate(40)).size();
	}

	private void callSwarm(ServerLevel server) {
		int fighters = Math.max(1, UltronCombat.playersNear(server, position(), 48).size());
		int n = UltronConfig.prime().swarmBaseDrones + fighters;
		UltronUprising up = uprising();
		for (int i = 0; i < n; i++) {
			UltronDroneEntity d = UltronEntityTypes.DRONE.create(server);
			if (d == null) {
				continue;
			}
			double a = i * Math.PI * 2 / n;
			Vec3 at = position().add(Math.cos(a) * 3, 1.5, Math.sin(a) * 3);
			d.moveTo(at.x, at.y, at.z, random.nextFloat() * 360f, 0f);
			d.finalizeSpawn(server, server.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
			if (up != null) {
				up.adopt(d);
			}
			server.addFreshEntity(d);
			if (getTarget() != null) {
				d.setTarget(getTarget());
			}
			server.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0f, 0.7f);
	}

	// ---------------------------------------------------------------- magnetic pull

	/** Is {@code p} wearing anything magnetic -- iron, chainmail, netherite, or any Iron Man piece? */
	public static boolean wearsMetal(LivingEntity p) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.getItem() instanceof IronManArmorItem) {
				return true;
			}
			if (s.getItem() instanceof ArmorItem armor) {
				ArmorMaterial m = armor.getMaterial().value();
				if (m == ArmorMaterials.IRON.value() || m == ArmorMaterials.CHAIN.value() || m == ArmorMaterials.NETHERITE.value()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean anyMetalNear(ServerLevel server) {
		for (Player p : UltronCombat.playersNear(server, position(), UltronConfig.prime().pullRange)) {
			if (wearsMetal(p)) {
				return true;
			}
		}
		return false;
	}

	private void tickPull(ServerLevel server, UltronConfig.Prime cfg) {
		setDeltaMovement(getDeltaMovement().scale(0.5));
		Vec3 me = position().add(0, getBbHeight() * 0.5, 0);
		if (moveTick <= 14) {
			for (Player p : UltronCombat.playersNear(server, position(), cfg.pullRange)) {
				if (!wearsMetal(p)) {
					continue;
				}
				Vec3 to = me.subtract(p.position().add(0, 1, 0));
				double len = to.length();
				if (len > 2.5) {
					Vec3 v = to.scale(Math.min(1.1, 0.25 + len * 0.06) / len);
					p.setDeltaMovement(v);
					p.hurtMarked = true;
					p.fallDistance = 0;
				}
				if (moveTick % 2 == 0) {
					for (double t = 0.15; t < 1.0; t += 0.15) {
						Vec3 pt = p.position().add(0, 1, 0).lerp(me, t);
						server.sendParticles(UltronFx.RED_SMALL, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
					}
				}
			}
			return;
		}
		if (moveTick == 15) {
			setAction(ACTION_PUNCH);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.6f);
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5), UltronCombat::canTarget)) {
				UltronCombat.hit(server, this, e, cfg.pullPunchDamage);
				Vec3 push = e.position().subtract(position()).normalize().scale(1.4);
				e.push(push.x, 0.5, push.z);
				e.hurtMarked = true;
				server.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
			}
		}
		if (moveTick >= 24) {
			endMove();
		}
	}

	// ---------------------------------------------------------------- dive bomb

	private void tickDive(ServerLevel server, LivingEntity target, UltronConfig.Prime cfg) {
		if (moveTick <= 18) {
			setDeltaMovement(getDeltaMovement().scale(0.6).add(0, 0.45, 0));
			if (moveTick == 18) {
				diveAt = target != null ? target.position() : position().add(0, -12, 0);
				setAction(ACTION_DIVE_DOWN);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0f, 0.6f);
			}
			return;
		}
		if (diveAt == null) {
			endMove();
			return;
		}
		Vec3 dir = diveAt.subtract(position());
		face(diveAt);
		double len = dir.length();
		setDeltaMovement(len < 0.5 ? Vec3.ZERO : dir.scale(Math.min(1.8, len) / len));
		server.sendParticles(UltronFx.RED, getX(), getY() + 1, getZ(), 2, 0.2, 0.3, 0.2, 0.0);
		if (onGround() || verticalCollision || len < 1.2 || moveTick > 45) {
			slam(server, cfg);
			diveAt = null;
			endMove();
		}
	}

	private void slam(ServerLevel server, UltronConfig.Prime cfg) {
		Vec3 c = position();
		BlockState ground = server.getBlockState(BlockPos.containing(c).below());
		if (!ground.isAir()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, 60, cfg.diveRadius * 0.4, 0.1,
					cfg.diveRadius * 0.4, 0.25);
		}
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		for (double r = 1.5; r <= cfg.diveRadius; r += 1.5) {
			UltronFx.ring(server, UltronFx.RED, c.add(0, 0.3, 0), r, (int) (r * 8));
		}
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.diveRadius, 3, cfg.diveRadius),
				UltronCombat::canTarget)) {
			double d = e.distanceTo(this);
			if (d <= cfg.diveRadius) {
				UltronCombat.hit(server, this, e, (float) (cfg.diveDamage * (1.0 - 0.5 * d / cfg.diveRadius)));
				Vec3 push = e.position().subtract(c).normalize().scale(0.9);
				e.push(push.x, 0.8, push.z);
				e.hurtMarked = true;
			}
		}
		setDeltaMovement(0, 0.5, 0);
	}

	// ---------------------------------------------------------------- palms

	public Vec3 palm(boolean right) {
		float yawRad = yBodyRot * ((float) Math.PI / 180f);
		Vec3 side = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).scale(right ? 0.45 * SCALE : -0.45 * SCALE);
		return position().add(0, getBbHeight() * 0.62, 0).add(side).add(getViewVector(1f).scale(0.6));
	}

	// ---------------------------------------------------------------- damage, body jump, death

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isJumping()) {
			return false;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide()) {
			threat.record(source, amount);
		}
		if (hurt && level() instanceof ServerLevel server && !enraged && getHealth() / getMaxHealth() <= 0.33f && getHealth() > 1f) {
			enraged = true;
			bossBar.setColor(BossEvent.BossBarColor.PURPLE);
			lastLine = UltronDialogue.say(server, this, "enrage", true, lastLine);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (!level().isClientSide() && !isJumping() && level() instanceof ServerLevel server && tryBodyJump(server)) {
			return; // not dead: his mind is moving to the next body
		}
		if (level() instanceof ServerLevel server) {
			lastLine = UltronDialogue.say(server, this, "fall", true, lastLine);
		}
		super.die(source);
		setNoGravity(false);
		bossBar.removeAllPlayers();
	}

	/** The nearest standing relay pylon of his uprising (or, unbound, any within 64 blocks). */
	public UltronPylonEntity nearestPylon(ServerLevel server) {
		UltronUprising up = uprising();
		List<UltronPylonEntity> pylons = up != null ? up.livePylons(server)
				: server.getEntitiesOfClass(UltronPylonEntity.class, getBoundingBox().inflate(64), LivingEntity::isAlive);
		UltronPylonEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (UltronPylonEntity p : pylons) {
			if (!p.isAlive() || p.isDeadOrDying()) {
				continue;
			}
			double d = p.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** At 0 health: if a pylon stands, start the upload to it and stay alive. */
	boolean tryBodyJump(ServerLevel server) {
		UltronPylonEntity pylon = nearestPylon(server);
		if (pylon == null) {
			return false;
		}
		setHealth(1.0f);
		jumpTicks = 0;
		jumpPylon = pylon.getUUID();
		jumpFrom = headPos();
		jumpTo = pylon.eye();
		move = Move.NONE;
		setAction(ACTION_UPLOAD);
		setInvisible(true);
		setDeltaMovement(Vec3.ZERO);
		UltronFx.wreck(server, position().add(0, getBbHeight() * 0.5, 0), 2.0);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 2.0f, 0.6f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 1.4f);
		lastLine = UltronDialogue.say(server, this, "jump", true, lastLine);
		bossBar.setName(Component.translatable("event.projecthero.ultron.bar.uploading"));
		return true;
	}

	private void tickJump(ServerLevel server) {
		jumpTicks++;
		setDeltaMovement(Vec3.ZERO);
		Entity e = server.getEntity(jumpPylon);
		if (e instanceof UltronPylonEntity pylon && pylon.isAlive()) {
			jumpTo = pylon.eye();
		}
		float t = Math.min(1f, jumpTicks / (float) JUMP_TICKS);
		Vec3 head = jumpFrom.lerp(jumpTo, t).add(0, Math.sin(t * Math.PI) * 4.0, 0);
		UltronFx.forced(server, UltronFx.RED_BIG, head.x, head.y, head.z, 4, 0.12, 0.12, 0.12, 0.0);
		UltronFx.forced(server, ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 2, 0.1, 0.1, 0.1, 0.05);
		if (jumpTicks % 2 == 1) {
			UltronFx.beam(server, jumpFrom, head, UltronFx.STREAK, 4);
			UltronFx.beam(server, head, jumpTo, UltronFx.TETHER, 3);
		}
		if (jumpTicks >= JUMP_TICKS) {
			finishJump(server);
		}
	}

	/** Completes the body jump now: the pylon is consumed and the new body stands there at half health. */
	public void finishJump(ServerLevel server) {
		Entity e = jumpPylon == null ? null : server.getEntity(jumpPylon);
		Vec3 at = jumpTo != null ? jumpTo : position();
		if (e instanceof UltronPylonEntity pylon) {
			at = pylon.position().add(0, pylon.getBbHeight() + 0.2, 0);
			UltronFx.wreck(server, pylon.eye(), 2.0);
			pylon.discard(); // consumed: no EMP
		}
		moveTo(at.x, at.y, at.z, getYRot(), 0f);
		setInvisible(false);
		setHealth((float) (getMaxHealth() * UltronConfig.prime().bodyJumpHealth));
		jumpTicks = -1;
		jumpPylon = null;
		bodyIndex++;
		introduced = false;
		enraged = false;
		bossBar.setColor(BossEvent.BossBarColor.RED);
		bossBar.setName(Component.translatable("event.projecthero.ultron.bar.prime_body", bodyIndex));
		setAction(ACTION_NONE);
		cooldown = 30;
		UltronFx.forced(server, UltronFx.STEEL, at.x, at.y + 1, at.z, 40, 0.8, 1.2, 0.8, 0.05);
		UltronFx.forced(server, UltronFx.RED, at.x, at.y + 1, at.z, 30, 0.6, 1.0, 0.6, 0.05);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 2.0f, 0.6f);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.2f);
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server && !isRemoved()) {
			if (deathTime % 4 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 3, 0.4, 0.6, 0.4, 0.02);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 4, 0.5, 0.6, 0.5, 0.1);
			}
			if (deathTime >= 40) {
				UltronFx.wreck(server, position().add(0, 1.5, 0), 2.5);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	private void say(ServerLevel server, String key, boolean force) {
		lastLine = UltronDialogue.say(server, this, key, force, lastLine);
	}

	// ---------------------------------------------------------------- flight, bar, save

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			if (isDeadOrDying()) {
				super.travel(input);
				return;
			}
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

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
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Body", bodyIndex);
		tag.putBoolean("Enraged", enraged);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		bodyIndex = Math.max(1, tag.getInt("Body"));
		enraged = tag.getBoolean("Enraged");
		if (bodyIndex > 1) {
			bossBar.setName(Component.translatable("event.projecthero.ultron.bar.prime_body", bodyIndex));
		}
		if (enraged) {
			bossBar.setColor(BossEvent.BossBarColor.PURPLE);
		}
		setNoGravity(true);
	}
}
