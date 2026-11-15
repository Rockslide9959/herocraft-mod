package com.projecthero.mod.hideout.boss;

import java.util.List;

import com.projecthero.mod.darkseid.entity.BoomTubeEntity;
import com.projecthero.mod.darkseid.entity.ParademonBoltEntity;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the <b>Parademon Commander</b> -- the Apokolips Outpost mini-boss. A crimson-armoured Parademon half again
 * the size of the rest (x1.5, 380 health, 12 armour) on the same winged GeckoLib body (an existing model, not a new
 * one). Keeps the Parademon's claws and flight, and adds:
 * <ul>
 * <li><b>Bolt Fan</b> (4-24 blocks): five energy bolts in a 40-degree fan.</li>
 * <li><b>Dive Strike</b>: it beats up into the air above its target, hangs there for half a second while a ring of
 * flame marks the landing spot, then plunges -- 12 damage, fire and a throw within 4.5 blocks. Move out of the ring.</li>
 * <li><b>Boom Tube Reinforcements</b>: two Parademons through a Boom Tube (never more than four of its own at once).</li>
 * </ul>
 * Shared threat table, own boss bar (see {@link HideoutBossLink}).
 */
public class ParademonCommanderBoss extends ParademonEntity implements HideoutBoss {
	public static final float SCALE = 1.5f;
	static final byte MOVE_NONE = 0;
	static final byte MOVE_FAN = 1;
	static final byte MOVE_DIVE = 2;
	static final byte MOVE_TUBE = 3;
	static final double DIVE_RADIUS = 4.5;

	private final HideoutBossLink link = new HideoutBossLink(this, HideoutFaction.APOKOLIPS, BossEvent.BossBarColor.RED, this::busy);
	private byte move = MOVE_NONE;
	private int moveTicks;
	private int fanCooldown = 50;
	private int diveCooldown = 140;
	private int tubeCooldown = 200;
	private Vec3 diveTarget = Vec3.ZERO;

	public ParademonCommanderBoss(EntityType<? extends ParademonCommanderBoss> type, Level level) {
		super(type, level);
		setup(Variant.ELITE, null);
		// setup() put the Elite's numbers in; the Commander's own come back on top
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(380.0);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12.0);
		getAttribute(Attributes.ARMOR).setBaseValue(12.0);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31);
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
		setHealth(380.0f);
		this.xpReward = 120;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 380.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.31)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	boolean busy() {
		return move != MOVE_NONE;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(SCALE);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide()) {
			link.onHurt(source, amount);
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		link.tick(server);
		if (fanCooldown > 0) {
			fanCooldown--;
		}
		if (diveCooldown > 0) {
			diveCooldown--;
		}
		if (tubeCooldown > 0) {
			tubeCooldown--;
		}
		if (move != MOVE_NONE) {
			tickMove(server);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}
		double d = distanceTo(t);
		if (tubeCooldown <= 0 && minions(server).size() < 3) {
			startTube();
		} else if (diveCooldown <= 0 && d < 20 && onGround() && random.nextInt(2) == 0) {
			startDive();
		} else if (fanCooldown <= 0 && d > 4 && d < 24 && hasLineOfSight(t)) {
			startFan();
		}
	}

	private List<ParademonEntity> minions(ServerLevel level) {
		return level.getEntitiesOfClass(ParademonEntity.class, getBoundingBox().inflate(32), e -> e != this && e.isAlive());
	}

	/** Starts the Bolt Fan now (test hook too). */
	public void startFan() {
		move = MOVE_FAN;
		moveTicks = 0;
		getNavigation().stop();
	}

	/** Starts the Dive Strike now (test hook too). */
	public void startDive() {
		move = MOVE_DIVE;
		moveTicks = 0;
		getNavigation().stop();
		LivingEntity t = getTarget();
		diveTarget = t != null ? t.position() : position();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.6f, 1.2f);
	}

	/** Starts the Boom Tube Reinforcements now (test hook too). */
	public void startTube() {
		move = MOVE_TUBE;
		moveTicks = 0;
		getNavigation().stop();
	}

	private Vec3 chest() {
		return position().add(0, getBbHeight() * 0.7, 0);
	}

	private void tickMove(ServerLevel level) {
		moveTicks++;
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
		}
		switch (move) {
			case MOVE_FAN -> {
				getNavigation().stop();
				if (moveTicks == 10 && t != null) {
					triggerAnim("action", "shoot");
					Vec3 from = chest();
					Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
					for (int i = -2; i <= 2; i++) {
						double a = Math.toRadians(i * 10);
						Vec3 d = new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), aim.y, aim.x * Math.sin(a) + aim.z * Math.cos(a));
						ParademonBoltEntity.fire(level, this, from.add(d.scale(0.8)), d, 6.0f, 1.2);
					}
				}
				if (moveTicks >= 18) {
					end();
					fanCooldown = 70 + random.nextInt(30);
				}
			}
			case MOVE_DIVE -> tickDive(level, t);
			case MOVE_TUBE -> {
				getNavigation().stop();
				if (moveTicks == 6) {
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 1.8f);
				}
				if (moveTicks == 16) {
					for (int i = 0; i < 2; i++) {
						BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 3, 7, random);
						if (spot == null) {
							continue;
						}
						BoomTubeEntity.open(level, Vec3.atBottomCenterOf(spot).add(0, 1.2, 0), BoomTubeEntity.Kind.MELEE, 1.8f, 30, null);
						var m = HideoutGuards.spawn(level, i == 0 ? HideoutGuards.Role.PARADEMON : HideoutGuards.Role.PARADEMON_RANGED, spot,
								random.nextFloat() * 360f, 12);
						if (m != null && t != null) {
							m.setTarget(t);
						}
					}
				}
				if (moveTicks >= 24) {
					end();
					tubeCooldown = 400 + random.nextInt(120);
				}
			}
			default -> end();
		}
	}

	private void tickDive(ServerLevel level, LivingEntity t) {
		getNavigation().stop();
		if (moveTicks <= 16) { // climb
			setNoGravity(true);
			if (t != null) {
				diveTarget = t.position();
			}
			Vec3 above = diveTarget.add(0, 7, 0);
			Vec3 to = above.subtract(position());
			setDeltaMovement(to.scale(0.12).add(0, 0.05, 0));
			if (moveTicks % 4 == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.0f, 1.4f);
			}
		} else if (moveTicks <= 28) { // hang and mark
			setDeltaMovement(getDeltaMovement().scale(0.5));
			if (moveTicks % 3 == 0) {
				for (int i = 0; i < 24; i++) {
					double a = i * Math.PI * 2 / 24;
					level.sendParticles(ParticleTypes.FLAME, diveTarget.x + Math.cos(a) * DIVE_RADIUS, diveTarget.y + 0.15,
							diveTarget.z + Math.sin(a) * DIVE_RADIUS, 1, 0, 0.02, 0, 0.0);
				}
			}
		} else { // plunge
			Vec3 to = diveTarget.subtract(position());
			Vec3 v = to.lengthSqr() > 1 ? to.normalize().scale(1.6) : to;
			setDeltaMovement(v.x, Math.min(-1.2, v.y), v.z);
			if (onGround() || moveTicks > 50 || position().distanceToSqr(diveTarget) < 1.5) {
				setNoGravity(false);
				impact(level);
				end();
				diveCooldown = 200 + random.nextInt(80);
			}
		}
	}

	private void impact(ServerLevel level) {
		Vec3 c = position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.8f);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 1.0, 0.3, 1.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 60, DIVE_RADIUS * 0.5, 0.2, DIVE_RADIUS * 0.5, 0.08);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DIVE_RADIUS, 2, DIVE_RADIUS),
				e -> e != this && e.isAlive() && !(e instanceof ParademonEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			if (e.distanceToSqr(c) > DIVE_RADIUS * DIVE_RADIUS) {
				continue;
			}
			e.hurt(damageSources().mobAttack(this), 12.0f);
			e.igniteForSeconds(3.0f);
			Vec3 out = e.position().subtract(c);
			Vec3 h = new Vec3(out.x, 0, out.z).normalize();
			e.push(h.x * 0.9, 0.7, h.z * 0.9);
			e.hurtMarked = true;
		}
	}

	private void end() {
		if (move == MOVE_DIVE) {
			setNoGravity(false);
		}
		move = MOVE_NONE;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		setNoGravity(false);
		if (level() instanceof ServerLevel server) {
			link.onDeath(server);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		link.detach();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
	}
}
