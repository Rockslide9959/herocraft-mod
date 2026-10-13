package com.projecthero.mod.galactus.entity;

import java.util.UUID;

import com.projecthero.mod.galactus.CosmicBolts;
import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Punisher</b> -- one of Galactus' ~8-block war robots. Guards an Element Converter (the converter is shielded
 * while its guards live, and they never stray more than {@link GalactusConfig.Punisher#leash} blocks from it unless a
 * fighter is right there) or, summoned in Act 3, hunts freely. A heavy punch up close, a blaster-gauntlet bolt at range
 * (dodgeable, {@link CosmicBolts}) and a telegraphed ground slam (a 1.2 s ring of violet on the ground, then a shockwave).
 */
public class PunisherRobotEntity extends GalactusMob {
	private UUID guardOf;
	private Vec3 post;
	private final CosmicBolts bolts = new CosmicBolts();
	private Clip move;
	private int moveTick;
	private LivingEntity moveTarget;
	private int blastCooldown = 40;
	private int slamCooldown = 80;
	private int punchCooldown = 20;
	private int deathTicks = -1;

	public PunisherRobotEntity(EntityType<? extends PunisherRobotEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		GalactusConfig.Punisher c = GalactusConfig.punisher();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, c.health)
				.add(Attributes.ARMOR, c.armor)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.MOVEMENT_SPEED, c.speed)
				.add(Attributes.ATTACK_DAMAGE, c.punchDamage)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	public String model() {
		return GalactusAnims.PUNISHER;
	}

	@Override
	protected Clip idleClip() {
		return GalactusAnims.PUNISHER_IDLE;
	}

	@Override
	protected Clip moveClip() {
		return GalactusAnims.PUNISHER_WALK;
	}

	public void configure(double health) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	public void guard(ElementConverterEntity converter) {
		this.guardOf = converter.getUUID();
		this.post = converter.position();
	}

	public UUID guardOf() {
		return guardOf;
	}

	public boolean busy() {
		return move != null;
	}

	// ---------------------------------------------------------------- AI

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) level();
		bolts.tick(level, this);
		if (isDeadOrDying()) {
			return;
		}
		if (blastCooldown > 0) {
			blastCooldown--;
		}
		if (slamCooldown > 0) {
			slamCooldown--;
		}
		if (punchCooldown > 0) {
			punchCooldown--;
		}
		if (move != null) {
			tickMove(level);
			return;
		}
		LivingEntity target = chooseTarget(level);
		setTarget(target);
		GalactusConfig.Punisher cfg = GalactusConfig.punisher();
		if (target == null) {
			if (post != null && distanceToSqr(post) > 9) {
				if (tickCount % 20 == 0) {
					getNavigation().moveTo(post.x, post.y, post.z, 1.0);
				}
			} else {
				getNavigation().stop();
			}
			return;
		}
		double d = Math.sqrt(distanceToSqr(target));
		getLookControl().setLookAt(target, 30f, 30f);
		if (d <= cfg.slamRadius - 1.0 && slamCooldown <= 0) {
			startMove(GalactusAnims.PUNISHER_SLAM, target);
			return;
		}
		if (d <= 4.0 + getBbWidth() * 0.5 && punchCooldown <= 0) {
			punchCooldown = 24;
			swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			if (GalactusCombat.hit(level, this, target, cfg.punchDamage)) {
				Vec3 push = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
				target.push(push.x, 0.45, push.z);
				target.hurtMarked = true;
			}
			level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5f, 0.5f);
			return;
		}
		if (d >= 6.0 && d <= cfg.blastRange && blastCooldown <= 0 && GalactusCombat.clearLine(level, this, gauntlet(), target.getEyePosition())) {
			startMove(GalactusAnims.PUNISHER_BLAST, target);
			return;
		}
		if (tickCount % 10 == 0 || getNavigation().isDone()) {
			if (!getNavigation().moveTo(target, 1.0)) {
				getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
			}
		}
	}

	private Vec3 gauntlet() {
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		return position().add(-Mth.sin(yaw) * 1.5 - Mth.cos(yaw) * 1.6, getBbHeight() * 0.62, Mth.cos(yaw) * 1.5 - Mth.sin(yaw) * 1.6);
	}

	private LivingEntity chooseTarget(ServerLevel level) {
		LivingEntity t = getTarget();
		GalactusConfig.Punisher cfg = GalactusConfig.punisher();
		Vec3 anchor = post != null ? post : position();
		double leash = post != null ? cfg.leash : 64.0;
		if (t != null && (!GalactusCombat.canTarget(t) || t.distanceToSqr(anchor) > (leash + 6) * (leash + 6))) {
			t = null;
		}
		if (t == null || tickCount % 20 == 0) {
			GalactusEvent e = event();
			Player p = GalactusCombat.nearestFighter(level, anchor, leash + 6);
			if (p != null && (e == null || e.isFighter(p.getUUID()))) {
				t = p;
			}
		}
		return t;
	}

	private void startMove(Clip clip, LivingEntity target) {
		move = clip;
		moveTick = 0;
		moveTarget = target;
		setAnim(clip);
		getNavigation().stop();
	}

	private void tickMove(ServerLevel level) {
		moveTick++;
		GalactusConfig.Punisher cfg = GalactusConfig.punisher();
		if (moveTarget != null && moveTarget.isAlive()) {
			faceTowards(moveTarget.position(), 12f);
		}
		if (move == GalactusAnims.PUNISHER_SLAM) {
			if (moveTick < move.hit() && moveTick % 3 == 0) {
				GalactusFx.ring(level, GalactusFx.COSMIC, position().add(0, 0.2, 0), cfg.slamRadius, 24);
			}
			if (moveTick == move.hit()) {
				GalactusFx.shake(level, position(), 0.6f, 12, 32);
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, cfg.slamRadius * 0.4, 0.2, cfg.slamRadius * 0.4, 0);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
				for (LivingEntity e : GalactusCombat.targetsNear(level, position(), cfg.slamRadius)) {
					if (e.getY() - getY() < 2.0 && GalactusCombat.hit(level, this, e, cfg.slamDamage)) {
						Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.0);
						e.push(push.x, 0.7, push.z);
						e.hurtMarked = true;
					}
				}
			}
			if (moveTick >= move.length()) {
				move = null;
				slamCooldown = cfg.slamCooldownTicks;
			}
		} else if (move == GalactusAnims.PUNISHER_BLAST) {
			if (moveTick < move.hit() && moveTick % 2 == 0) {
				Vec3 g = gauntlet();
				level.sendParticles(GalactusFx.COSMIC_SMALL, g.x, g.y, g.z, 3, 0.15, 0.15, 0.15, 0.01);
			}
			if (moveTick == move.hit() && moveTarget != null) {
				Vec3 g = gauntlet();
				bolts.fire(g, moveTarget.getBoundingBox().getCenter(), 1.1, cfg.blastRange + 6, cfg.blastDamage, GalactusCombat::canTarget,
						GalactusFx.COSMIC);
				level.playSound(null, g.x, g.y, g.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.5f);
			}
			if (moveTick >= move.length()) {
				move = null;
				blastCooldown = cfg.blastCooldownTicks;
			}
		} else {
			move = null;
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		setAnim(GalactusAnims.PUNISHER_DEATH);
		if (level() instanceof ServerLevel server) {
			GalactusFx.burst(server, position().add(0, 4, 0), 1.5);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 2.0f, 0.5f);
			GalactusEvent e = event();
			if (e != null) {
				e.onPunisherDied(server, this);
			}
		}
	}

	@Override
	protected void tickDeath() {
		// the 2 s death clip, then gone
		if (++deathTime >= GalactusAnims.PUNISHER_DEATH.length() && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, (byte) 60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (guardOf != null) {
			tag.putUUID("GuardOf", guardOf);
		}
		if (post != null) {
			tag.putDouble("PostX", post.x);
			tag.putDouble("PostY", post.y);
			tag.putDouble("PostZ", post.z);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		guardOf = tag.hasUUID("GuardOf") ? tag.getUUID("GuardOf") : null;
		post = tag.contains("PostX") ? new Vec3(tag.getDouble("PostX"), tag.getDouble("PostY"), tag.getDouble("PostZ")) : null;
	}
}
