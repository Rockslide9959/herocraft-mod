package com.projecthero.mod.horde.entity;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.12: the Spider Horde's boss -- a vast spider queen (a vanilla spider at 3.5x size through the {@code SCALE}
 * attribute) with:
 * <ul>
 *   <li><b>Web Barrage</b>: a fan of five big webs -- each one drops a 3x3 patch of cobweb where it lands;</li>
 *   <li><b>Pounce</b>: from 6 to 24 blocks she leaps at her target and lands with a slam (12 damage within 4);</li>
 *   <li><b>Brood</b>: every so often four cave spiders burst from her;</li>
 *   <li><b>Venom</b>: her bite poisons (Poison II) and weakens.</li>
 * </ul>
 * Faster than she looks, never despawns, can't be knocked back. Health is set by the horde ({@link #configure}).
 */
public class BroodQueen extends Spider {
	public static final float SCALE = 3.5f;
	private int barrageCooldown = 60;
	private int pounceCooldown = 120;
	private int broodCooldown = 240;
	private boolean pouncing;

	public BroodQueen(EntityType<? extends Spider> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Spider.createAttributes()
				.add(Attributes.MAX_HEALTH, 450.0)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Sets her health for {@code players} fighting her (vanilla caps max health at 1024). */
	public void configure(int players) {
		double hp = Math.min(1024.0, 450.0 + 175.0 * Math.max(0, players - 1));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	/** Vanilla spiders lose interest in daylight; a horde's never does. */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, true));
		this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this,
				net.minecraft.world.entity.player.Player.class, false));
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
		}
		return hit;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (pouncing && (onGround() || isInWater())) {
			pouncing = false;
			land(level);
		}
		if (--broodCooldown <= 0) {
			brood(level);
			broodCooldown = 300 + getRandom().nextInt(120);
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || pouncing) {
			return;
		}
		double d = distanceTo(target);
		if (--pounceCooldown <= 0 && d > 6.0 && d < 24.0 && onGround()) {
			pounce(level, target);
			pounceCooldown = 140 + getRandom().nextInt(60);
			return;
		}
		if (--barrageCooldown <= 0 && d < 30.0 && getSensing().hasLineOfSight(target)) {
			for (int i = 0; i < 5; i++) {
				HordeSpider.spit(this, target, true);
			}
			barrageCooldown = 80 + getRandom().nextInt(40);
		}
	}

	private void pounce(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().subtract(position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		// a lob that lands on the target: ~0.08 gravity, speed ~1.3 blocks a tick along the ground
		double ticks = Math.max(8.0, horizontal / 1.3);
		setDeltaMovement(to.x / ticks, Math.min(1.6, 0.04 * ticks + to.y / ticks), to.z / ticks);
		hasImpulse = true;
		pouncing = true;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.4f);
	}

	private void land(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.7f);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 1.5, 0.2, 1.5, 0);
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(4.0, 2.5, 4.0),
				e -> e != this && e.isAlive() && !(e instanceof Monster) && BoneTyrant.enemy(e));
		for (LivingEntity e : hit) {
			e.hurt(damageSources().mobAttack(this), 12.0f);
			Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
			e.push(away.x * 1.1, 0.5, away.z * 1.1);
			e.hurtMarked = true;
		}
	}

	private void brood(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 2.0f, 0.5f);
		for (int i = 0; i < 4; i++) {
			Mob m = EntityType.CAVE_SPIDER.create(level);
			if (m == null) {
				continue;
			}
			m.moveTo(getX() + getRandom().nextGaussian(), getY() + 0.5, getZ() + getRandom().nextGaussian(), getRandom().nextFloat() * 360f, 0);
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			if (getTarget() != null) {
				m.setTarget(getTarget());
			}
			level.addFreshEntity(m);
		}
		level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1, getZ(), 30, 1.0, 0.5, 1.0, 0.1);
	}
}
