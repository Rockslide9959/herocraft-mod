package com.projecthero.mod.galactus.entity;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.galactus.CosmicBolts;
import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>the Silver Surfer</b>, Herald of Galactus -- player-sized, always on his board (the model's {@code board} bone).
 *
 * <h2>Act 1: the duel (hostile)</h2>
 * An aerial fight: he circles his target at {@link GalactusConfig.Herald#preferredRange} and
 * {@link GalactusConfig.Herald#hoverHeight}, choosing one move at a time (targets from the shared {@link BossThreat} table):
 * <ul>
 *   <li><b>Power Cosmic blast</b> -- a dodgeable bolt ({@link CosmicBolts}).</li>
 *   <li><b>Light-speed dash</b> -- a 1 s line of silver telegraphs the path, then he crosses it in a flash, hitting anything
 *       on it.</li>
 *   <li><b>Reflective shield</b> -- 3 s: projectiles bounce back at their shooter, melee does 30%.</li>
 *   <li><b>Cosmic lift</b> -- the target is pulled high into the air (Levitation), then dropped.</li>
 * </ul>
 * He can't be killed in the duel: at {@link GalactusConfig.Herald#turnAtFraction} of his health he stops and <b>turns</b>.
 *
 * <h2>Acts 2-3: the ally</h2>
 * Protected (nothing hurts him but {@code /kill}), he flies with the fighters, blasts the nearest Punisher, bubbles a
 * fighter in trouble now and then, and guides the Ultimate Nullifier step.
 */
public class SilverSurferEntity extends GalactusMob {
	public static final int HOSTILE = 0, TURNING = 1, ALLY = 2;
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SilverSurferEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(SilverSurferEntity.class, EntityDataSerializers.BOOLEAN);

	private final BossThreat threat = new BossThreat(this).range(96.0).holdWhile(this::busy);
	private final CosmicBolts bolts = new CosmicBolts();
	private Clip move;
	private int moveTick;
	private LivingEntity moveTarget;
	private Vec3 dashFrom = Vec3.ZERO;
	private Vec3 dashTo = Vec3.ZERO;
	private final java.util.Set<java.util.UUID> dashHit = new java.util.HashSet<>();
	private int cooldown = 40;
	/** v0.16.4: ticks left of the low, winded pause after a dash (in melee reach). */
	private int windedTicks;
	private int shieldTicks;
	private int turnTicks;
	private float orbit;
	private int allyBlastCooldown = 20;
	private int allyShieldCooldown = 200;
	private boolean bypassClamp;

	public SilverSurferEntity(EntityType<? extends SilverSurferEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		// v0.16.4 (user): "he can launch himself into the floor and then he gets stuck" -- he phases through blocks: no block
		// collision and no suffocation (isInWall is false without physics). He flies on his own steering anyway.
		this.noPhysics = true;
		this.orbit = random.nextFloat() * Mth.TWO_PI;
	}

	public static AttributeSupplier.Builder createAttributes() {
		GalactusConfig.Herald c = GalactusConfig.herald();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, c.health)
				.add(Attributes.ARMOR, c.armor)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, c.flySpeed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, HOSTILE);
		builder.define(DATA_SHIELD, false);
		builder.define(DATA_POSE, 0xFF);
	}

	/** A player model (no GeckoLib asset): see {@code GalactusRenderers.SurferModel}. */
	@Override
	public String model() {
		return null;
	}

	// ---------------------------------------------------------------- the code-driven pose (synced: index | seq << 8)

	private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(SilverSurferEntity.class, EntityDataSerializers.INT);
	private int poseSeq;
	private int clientPoseStart;

	@Override
	public void setAnim(Clip clip) {
		poseSeq = (poseSeq + 1) & 0xFFFFFF;
		int idx = clip == null ? -1 : GalactusAnims.SURFER_POSES.indexOf(clip); // List.of rejects indexOf(null)
		entityData.set(DATA_POSE, (idx < 0 ? 0xFF : idx) | (poseSeq << 8));
		animTicksLeft = clip == null || clip.loops() ? 0 : clip.length();
	}

	@Override
	public Clip currentClip() {
		int idx = entityData.get(DATA_POSE) & 0xFF;
		return idx < GalactusAnims.SURFER_POSES.size() ? GalactusAnims.SURFER_POSES.get(idx) : null;
	}

	/** The pose playing (null: idle / fly, chosen by speed), for the renderer. */
	public Clip pose() {
		return currentClip();
	}

	/** Ticks (client) since the current pose began. */
	public float poseAge(float partialTick) {
		return tickCount - clientPoseStart + partialTick;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_POSE.equals(key)) {
			clientPoseStart = tickCount;
		}
	}

	@Override
	protected Clip idleClip() {
		return isAlly() ? GalactusAnims.SURFER_ALLY_IDLE : GalactusAnims.SURFER_IDLE;
	}

	@Override
	protected Clip moveClip() {
		return GalactusAnims.SURFER_FLY;
	}

	public void configure(double health) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	public int surferState() {
		return entityData.get(DATA_STATE);
	}

	public boolean isAlly() {
		return surferState() == ALLY;
	}

	public boolean isTurning() {
		return surferState() == TURNING;
	}

	public boolean shieldUp() {
		return entityData.get(DATA_SHIELD);
	}

	public BossThreat threat() {
		return threat;
	}

	public boolean busy() {
		return move != null;
	}

	public double turnHealth() {
		return getMaxHealth() * GalactusConfig.herald().turnAtFraction;
	}

	// ---------------------------------------------------------------- health: never dies in the duel, protected after

	@Override
	public void setHealth(float health) {
		if (surferState() == HOSTILE && !bypassClamp && health < getHealth()) {
			health = Math.max(health, (float) turnHealth());
		}
		super.setHealth(health);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			bypassClamp = true;
			try {
				return super.hurt(source, amount);
			} finally {
				bypassClamp = false;
			}
		}
		if (surferState() != HOSTILE) {
			return false; // turning / turned: protected
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		if (shieldUp()) {
			GalactusConfig.Herald c = GalactusConfig.herald();
			if (source.getDirectEntity() != null && source.getDirectEntity() != attacker) {
				// a projectile: it rebounds onto its shooter
				if (attacker instanceof LivingEntity a && level() instanceof ServerLevel server && a.distanceToSqr(this) < 48 * 48) {
					GalactusFx.line(server, GalactusFx.SILVER, position().add(0, 1.2, 0), a.getEyePosition(), 0.8);
					a.hurt(server.damageSources().indirectMagic(this, this), (float) (amount * c.shieldReflectFraction));
				}
				if (level() instanceof ServerLevel server) {
					server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 1.4f);
				}
				return false;
			}
			amount *= (float) (1.0 - c.shieldMeleeReduction);
		}
		float before = getHealth();
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			threat.record(source, Math.max(0f, before - getHealth()));
		}
		return hurt;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		if (surferState() != HOSTILE && (other instanceof Player || other instanceof SilverSurferEntity)) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	// ---------------------------------------------------------------- the turn

	/** He stops, the turn plays (the event speaks his lines), then he is an ally. */
	public void beginTurn(ServerLevel level) {
		if (surferState() != HOSTILE) {
			return;
		}
		entityData.set(DATA_STATE, TURNING);
		entityData.set(DATA_SHIELD, false);
		move = null;
		shieldTicks = 0;
		turnTicks = 0;
		bolts.clear();
		setTarget(null);
		threat.clear();
		setAnim(GalactusAnims.SURFER_TURN_ALLY);
		GalactusFx.burst(level, position().add(0, 1, 0), 1.5);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 0.6f);
		GalactusEvent e = event();
		if (e != null) {
			e.onSurferTurning(level, this);
		}
	}

	/** Gametest / command hook: straight to an ally (no turn clip). */
	public void becomeAlly(ServerLevel level) {
		entityData.set(DATA_STATE, ALLY);
		entityData.set(DATA_SHIELD, false);
		move = null;
		setTarget(null);
		bypassClamp = true;
		setHealth(getMaxHealth());
		bypassClamp = false;
		setAnim(null);
		GalactusEvent e = event();
		if (e != null) {
			e.onSurferTurned(level, this);
		}
	}

	// ---------------------------------------------------------------- AI

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		this.noPhysics = true; // v0.16.4: never stuck in terrain (kept on every tick)
		ServerLevel level = (ServerLevel) level();
		bolts.tick(level, this);
		if (shieldTicks > 0 && --shieldTicks == 0) {
			entityData.set(DATA_SHIELD, false);
		}
		if (tickCount % 3 == 0) {
			level.sendParticles(GalactusFx.SILVER, getX(), getY() + 0.1, getZ(), 2, 0.4, 0.05, 0.4, 0.01);
		}
		switch (surferState()) {
			case HOSTILE -> tickHostile(level);
			case TURNING -> {
				setDeltaMovement(getDeltaMovement().scale(0.6));
				if (++turnTicks >= GalactusConfig.herald().turnTicks) {
					becomeAlly(level);
				}
			}
			default -> tickAlly(level);
		}
	}

	/** The first thing his hostile AI does every tick: at 10% he turns. True if he did (public for the gametests). */
	public boolean checkTurn(ServerLevel level) {
		if (surferState() == HOSTILE && getHealth() <= turnHealth() + 0.01f) {
			beginTurn(level);
			return true;
		}
		return false;
	}

	private void tickHostile(ServerLevel level) {
		if (checkTurn(level)) {
			return;
		}
		threat.tick();
		LivingEntity target = getTarget();
		GalactusEvent e = event();
		if (target == null || !GalactusCombat.canTarget(target) || tickCount % 20 == 0 && threat.isEmpty()) {
			Player p = GalactusCombat.nearestFighter(level, e != null ? Vec3.atCenterOf(e.center()) : position(), 96);
			if (p != null && (e == null || e.isFighter(p.getUUID()))) {
				target = p;
			} else if (p != null && e == null) {
				target = p;
			}
			setTarget(target);
		}
		if (move != null) {
			tickMove(level);
			return;
		}
		if (windedTicks > 0) {
			windedTicks--;
			double ground = GalactusCombat.groundY(level, getX(), getY(), getZ());
			double dy = Mth.clamp(ground + 0.2 - getY(), -0.35, 0.1);
			setDeltaMovement(0, dy, 0);
			if (target != null) {
				faceTowards(target.position(), 8f);
			}
			if (tickCount % 4 == 0) {
				level.sendParticles(GalactusFx.SILVER, getX(), getY() + 1.2, getZ(), 4, 0.3, 0.5, 0.3, 0.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.4, 0.3, 0.05);
			}
			return;
		}
		if (target == null) {
			hover(e != null ? Vec3.atCenterOf(e.center()).add(0, 12, 0) : position(), 0.3);
			return;
		}
		GalactusConfig.Herald c = GalactusConfig.herald();
		orbit += 0.02f;
		Vec3 want = target.position().add(Math.cos(orbit) * c.preferredRange, c.hoverHeight, Math.sin(orbit) * c.preferredRange);
		hover(want, c.flySpeed);
		faceTowards(target.position(), 20f);
		if (--cooldown > 0) {
			return;
		}
		float r = random.nextFloat();
		boolean hurtBad = getHealth() < getMaxHealth() * 0.5f;
		if (r < (hurtBad ? 0.18f : 0.1f) && !shieldUp()) {
			startMove(GalactusAnims.SURFER_SHIELD, target);
		} else if (r < 0.32f) {
			startMove(GalactusAnims.SURFER_DASH, target);
		} else if (r < 0.46f && target.onGround()) {
			startMove(GalactusAnims.SURFER_LIFT, target);
		} else {
			startMove(GalactusAnims.SURFER_BLAST, target);
		}
	}

	private void hover(Vec3 want, double speed) {
		Vec3 d = want.subtract(position());
		double len = d.length();
		Vec3 vel = len < 0.3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
		setDeltaMovement(getDeltaMovement().scale(0.5).add(vel.scale(0.5)));
	}

	private void startMove(Clip clip, LivingEntity target) {
		move = clip;
		moveTick = 0;
		moveTarget = target;
		setAnim(clip);
		if (clip == GalactusAnims.SURFER_DASH) {
			dashFrom = position().add(0, 0.6, 0);
			Vec3 through = target.position().add(0, 0.8, 0);
			Vec3 dir = through.subtract(dashFrom);
			if (dir.lengthSqr() < 1.0e-3) {
				dir = new Vec3(1, 0, 0);
			}
			dashTo = dashFrom.add(dir.normalize().scale(GalactusConfig.herald().dashLength));
			// v0.16.4 (user): a dash through a target on the ground used to end underground -- finish it in the air above the terrain
			int top = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					Mth.floor(dashTo.x), Mth.floor(dashTo.z));
			if (dashTo.y < top + 1.5) {
				dashTo = new Vec3(dashTo.x, top + 1.5, dashTo.z);
			}
			dashHit.clear();
		}
	}

	private void tickMove(ServerLevel level) {
		moveTick++;
		GalactusConfig.Herald c = GalactusConfig.herald();
		LivingEntity t = moveTarget;
		if (move == GalactusAnims.SURFER_BLAST) {
			setDeltaMovement(getDeltaMovement().scale(0.7));
			if (t != null) {
				faceTowards(t.position(), 30f);
			}
			if (moveTick < move.hit() && moveTick % 2 == 0) {
				level.sendParticles(GalactusFx.COSMIC_SMALL, getX(), getY() + 1.3, getZ(), 3, 0.2, 0.2, 0.2, 0.02);
			}
			if (moveTick == move.hit() && t != null) {
				Vec3 from = position().add(0, 1.4, 0);
				bolts.fire(from, t.getBoundingBox().getCenter(), c.blastSpeed, c.blastRange, c.blastDamage, GalactusCombat::canTarget, GalactusFx.COSMIC);
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4f, 1.6f);
			}
		} else if (move == GalactusAnims.SURFER_DASH) {
			int hit = move.hit();
			if (moveTick < hit) {
				setDeltaMovement(Vec3.ZERO);
				if (moveTick % 3 == 0) {
					GalactusFx.line(level, GalactusFx.SILVER, dashFrom, dashTo, 1.0);
				}
			} else if (moveTick < hit + c.dashTicks) {
				double f = (moveTick - hit + 1) / (double) c.dashTicks;
				Vec3 at = dashFrom.lerp(dashTo, Math.min(1.0, f));
				Vec3 prev = position().add(0, 0.6, 0);
				setPos(at.x, at.y - 0.6, at.z);
				setDeltaMovement(Vec3.ZERO);
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.05);
				for (LivingEntity e : GalactusCombat.alongLine(level, prev, at, 1.5)) {
					if (dashHit.add(e.getUUID()) && GalactusCombat.hit(level, this, e, c.dashDamage)) {
						Vec3 push = dashTo.subtract(dashFrom).normalize().scale(1.2);
						e.push(push.x, 0.5, push.z);
						e.hurtMarked = true;
					}
				}
				if (moveTick == hit) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 2.0f, 1.4f);
				}
			}
		} else if (move == GalactusAnims.SURFER_SHIELD) {
			setDeltaMovement(getDeltaMovement().scale(0.8));
			if (moveTick == move.hit()) {
				shieldTicks = c.shieldTicks;
				entityData.set(DATA_SHIELD, true);
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 1.6f);
			}
			if (shieldUp() && moveTick % 3 == 0) {
				GalactusFx.ring(level, GalactusFx.SILVER, position().add(0, 1.1, 0), 1.4, 14);
			}
		} else if (move == GalactusAnims.SURFER_LIFT) {
			setDeltaMovement(getDeltaMovement().scale(0.7));
			if (t != null && moveTick < move.hit() && moveTick % 2 == 0) {
				GalactusFx.ring(level, GalactusFx.COSMIC, t.position().add(0, 0.15, 0), 1.6, 12);
			}
			if (moveTick == move.hit() && t != null && GalactusCombat.canTarget(t) && t.distanceToSqr(this) < 40 * 40) {
				t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, c.liftTicks, c.liftAmplifier, false, true), this);
				level.sendParticles(GalactusFx.COSMIC, t.getX(), t.getY() + 1, t.getZ(), 30, 0.4, 1.0, 0.4, 0.1);
				level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SHULKER_BULLET_HIT, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
		}
		if (moveTick >= move.length()) {
			if (move == GalactusAnims.SURFER_DASH) {
				// v0.16.4 (playthrough audit): he flew out of melee reach for the whole duel -- after a dash he is winded, and
				// sinks to the ground where it ended for a few seconds: the window for swords
				windedTicks = c.windedTicks;
			}
			move = null;
			cooldown = c.moveCooldownTicks + random.nextInt(20);
		}
	}

	private void tickAlly(ServerLevel level) {
		GalactusEvent e = event();
		GalactusConfig.Ally c = GalactusConfig.ally();
		// guide the Nullifier step: hover over the marked spot
		Vec3 spot = e != null ? e.nullifierSpot() : null;
		if (spot != null) {
			hover(spot.add(0, 3.5, 0), 0.5);
			if (tickCount % 4 == 0) {
				GalactusFx.forced(level, ParticleTypes.END_ROD, spot.x, spot.y + 1, spot.z, 4, 0.2, 3.0, 0.2, 0.02);
				GalactusFx.ring(level, GalactusFx.SILVER, spot.add(0, 0.2, 0), 2.5, 16);
			}
			return;
		}
		Player anchor = null;
		if (e != null) {
			double best = Double.MAX_VALUE;
			for (Player p : e.fighterPlayers(level)) {
				double d = p.distanceToSqr(this);
				if (d < best) {
					best = d;
					anchor = p;
				}
			}
		}
		if (anchor == null) {
			anchor = GalactusCombat.nearestFighter(level, position(), 64);
		}
		orbit += 0.03f;
		if (anchor != null) {
			hover(anchor.position().add(Math.cos(orbit) * c.orbitRadius, 4.0, Math.sin(orbit) * c.orbitRadius), 0.5);
		} else {
			setDeltaMovement(getDeltaMovement().scale(0.8));
		}
		if (--allyBlastCooldown <= 0) {
			allyBlastCooldown = c.blastIntervalTicks;
			PunisherRobotEntity nearest = null;
			double best = 40 * 40;
			for (PunisherRobotEntity p : level.getEntitiesOfClass(PunisherRobotEntity.class, getBoundingBox().inflate(40), x -> x.isAlive()
					&& !x.isDeadOrDying())) {
				double d = p.distanceToSqr(this);
				if (d < best) {
					best = d;
					nearest = p;
				}
			}
			if (nearest != null) {
				faceTowards(nearest.position(), 180f);
				setAnim(GalactusAnims.SURFER_BLAST);
				bolts.fire(position().add(0, 1.4, 0), nearest.getBoundingBox().getCenter(), 1.6, 48, c.blastDamage,
						x -> x instanceof PunisherRobotEntity, GalactusFx.SILVER);
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.2f, 1.8f);
			}
		}
		if (--allyShieldCooldown <= 0 && e != null) {
			for (Player p : e.fighterPlayers(level)) {
				if (p.getHealth() < p.getMaxHealth() * c.shieldBelowHealth && p.distanceToSqr(this) < 48 * 48) {
					allyShieldCooldown = c.shieldCooldownTicks;
					p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, c.shieldTicks, 1, false, true), this);
					p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, c.shieldTicks, 1, false, true), this);
					setAnim(GalactusAnims.SURFER_SHIELD);
					GalactusFx.line(level, GalactusFx.SILVER, position().add(0, 1.2, 0), p.position().add(0, 1, 0), 0.6);
					GalactusFx.ring(level, GalactusFx.SILVER, p.position().add(0, 1, 0), 1.2, 16);
					level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0f, 1.8f);
					if (e != null) {
						e.surferLine(level, "shield");
					}
					break;
				}
			}
			if (allyShieldCooldown <= 0) {
				allyShieldCooldown = 40;
			}
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		// a Surfer reloaded mid-turn finishes turning
		tag.putInt("SurferState", surferState() == TURNING ? ALLY : surferState());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_STATE, Mth.clamp(tag.getInt("SurferState"), HOSTILE, ALLY));
	}
}
