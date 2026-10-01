package com.projecthero.mod.horde.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.12: a Spider Horde spider -- a vanilla spider, faster (0.42 movement, was 0.3) and tougher, that spits webs
 * ({@link WebShotEntity}) at its target from 4 to 18 blocks every few seconds. Never persistent; only a horde makes it.
 */
public class HordeSpider extends Spider {
	private static final int WEB_COOLDOWN_MIN = 50;
	private static final int WEB_COOLDOWN_SPREAD = 50;
	private int webCooldown = 40;

	public HordeSpider(EntityType<? extends Spider> type, Level level) {
		super(type, level);
		this.xpReward = 7;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Spider.createAttributes()
				.add(Attributes.MAX_HEALTH, 22.0)
				.add(Attributes.MOVEMENT_SPEED, 0.42)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (webCooldown > 0) {
			webCooldown--;
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = distanceTo(target);
		if (d < 4.0 || d > 18.0 || !getSensing().hasLineOfSight(target)) {
			return;
		}
		spit(this, target, false);
		webCooldown = WEB_COOLDOWN_MIN + getRandom().nextInt(WEB_COOLDOWN_SPREAD);
	}

	/** Spits one web from {@code shooter} at {@code target}, leading it a little and arcing for distance. */
	static void spit(Mob shooter, LivingEntity target, boolean queen) {
		spit(shooter, target, queen ? WebShotEntity.Mode.QUEEN : WebShotEntity.Mode.WEB, queen ? 2.0f : 4.0f);
	}

	/**
	 * v0.14.16: spits one shot of any {@link WebShotEntity.Mode} with {@code inaccuracy} spread (the Queen's volleys fan
	 * out by passing a wide spread; a Venom Spitter's globs fly flatter). Returns the shot, or null client-side.
	 */
	public static WebShotEntity spit(Mob shooter, LivingEntity target, WebShotEntity.Mode mode, float inaccuracy) {
		if (!(shooter.level() instanceof ServerLevel level)) {
			return null;
		}
		boolean big = mode == WebShotEntity.Mode.QUEEN || mode == WebShotEntity.Mode.COCOON;
		WebShotEntity shot = new WebShotEntity(level, shooter, mode);
		Vec3 from = shooter.getEyePosition();
		shot.setPos(from.x, from.y - 0.1, from.z);
		Vec3 aim = target.getEyePosition().add(target.getDeltaMovement().scale(6)).subtract(from);
		double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		float speed = mode == WebShotEntity.Mode.VENOM ? 1.7f : big ? 1.8f : 1.5f;
		shot.shoot(aim.x, aim.y + horizontal * (mode == WebShotEntity.Mode.VENOM ? 0.09 : 0.12), aim.z, speed, inaccuracy);
		level.addFreshEntity(shot);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE,
				0.9f, big ? 0.6f : mode == WebShotEntity.Mode.VENOM ? 1.0f : 1.4f);
		return shot;
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
}
