package com.projecthero.mod.horde.entity;

import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.12: the Skeleton Horde's boss -- a towering skeleton king (a vanilla skeleton at three times the size, through
 * the {@code SCALE} attribute, so it needs no model of its own) with a bow and four moves on top of its normal shots:
 * <ul>
 *   <li><b>Volley</b>: a fan of seven arrows at its target;</li>
 *   <li><b>Arrow Rain</b>: a storm of arrows falls round its target for three seconds (a ring of crit sparks warns first);</li>
 *   <li><b>Bone Quake</b>: when someone gets close it stamps -- 14 damage and a throw to everything within 5 blocks;</li>
 *   <li><b>Raise the Dead</b>: at two-thirds and one-third health it calls up six skeletons and strays.</li>
 * </ul>
 * Never despawns, can't be knocked back and doesn't burn in daylight. Health is set by the horde ({@link #configure}).
 */
public class BoneTyrant extends Skeleton {
	public static final float SCALE = 3.0f;
	private int volleyCooldown = 60;
	private int rainCooldown = 200;
	private int quakeCooldown = 40;
	private int rainTicks;
	private Vec3 rainAt = Vec3.ZERO;
	private int summonsLeft = 2;

	public BoneTyrant(EntityType<? extends Skeleton> type, Level level) {
		super(type, level);
		this.xpReward = 200;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Sets its health for {@code players} fighting it (vanilla caps max health at 1024). */
	public void configure(int players) {
		double hp = Math.min(1024.0, 400.0 + 150.0 * Math.max(0, players - 1));
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET)); // the crown
		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setDropChance(EquipmentSlot.HEAD, 0f);
		return out;
	}

	@Override
	protected boolean isSunBurnTick() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && summonsLeft > 0 && getHealth() <= getMaxHealth() * (summonsLeft / 3.0f) && level() instanceof ServerLevel server) {
			summonsLeft--;
			raiseTheDead(server);
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		tickRain(level);
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = distanceTo(target);
		if (--quakeCooldown <= 0 && d < 6.0) {
			quake(level);
			quakeCooldown = 80;
			return;
		}
		if (--volleyCooldown <= 0 && d < 40.0 && getSensing().hasLineOfSight(target)) {
			volley(level, target);
			volleyCooldown = 70 + getRandom().nextInt(30);
			return;
		}
		if (--rainCooldown <= 0 && d < 40.0) {
			rainAt = target.position();
			rainTicks = 80; // 1 s warning, then 3 s of arrows
			rainCooldown = 260 + getRandom().nextInt(80);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_HORSE_DEATH, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}

	private void volley(ServerLevel level, LivingEntity target) {
		Vec3 from = getEyePosition();
		Vec3 aim = target.getEyePosition().subtract(from);
		double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		float baseYaw = (float) Math.atan2(aim.z, aim.x);
		for (int i = -3; i <= 3; i++) {
			float yaw = baseYaw + i * 0.13f;
			Arrow arrow = arrow(level, from);
			arrow.shoot(Math.cos(yaw) * horizontal, aim.y + horizontal * 0.18, Math.sin(yaw) * horizontal, 2.2f, 1.0f);
			level.addFreshEntity(arrow);
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 2.0f, 0.6f);
	}

	private void tickRain(ServerLevel level) {
		if (rainTicks <= 0) {
			return;
		}
		rainTicks--;
		if (rainTicks > 60) {
			// the warning: a ring of crit sparks round the spot
			for (int i = 0; i < 12; i++) {
				double a = i * Math.PI / 6 + rainTicks * 0.2;
				level.sendParticles(ParticleTypes.CRIT, rainAt.x + Math.cos(a) * 4.5, rainAt.y + 0.2, rainAt.z + Math.sin(a) * 4.5, 1, 0, 0, 0, 0);
			}
			return;
		}
		for (int i = 0; i < 3; i++) {
			double x = rainAt.x + (getRandom().nextDouble() * 2 - 1) * 5.0;
			double z = rainAt.z + (getRandom().nextDouble() * 2 - 1) * 5.0;
			Arrow arrow = arrow(level, new Vec3(x, rainAt.y + 18, z));
			arrow.setDeltaMovement(0, -2.4, 0);
			level.addFreshEntity(arrow);
		}
	}

	private Arrow arrow(ServerLevel level, Vec3 at) {
		Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW), null);
		arrow.setPos(at.x, at.y, at.z);
		arrow.setBaseDamage(4.0);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		return arrow;
	}

	private void quake(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
				getX(), getY() + 0.2, getZ(), 120, 3.0, 0.3, 3.0, 0.15);
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(5.0, 3.0, 5.0),
				e -> e != this && e.isAlive() && !(e instanceof Monster) && enemy(e));
		for (LivingEntity e : hit) {
			if (e.distanceTo(this) > 5.5f) {
				continue;
			}
			e.hurt(damageSources().mobAttack(this), 14.0f);
			Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
			e.push(away.x * 1.4, 0.7, away.z * 1.4);
			e.hurtMarked = true;
		}
	}

	private void raiseTheDead(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			EntityType<? extends Mob> type = i % 2 == 0 ? EntityType.SKELETON : EntityType.STRAY;
			Mob m = type.create(level);
			if (m == null) {
				continue;
			}
			m.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getRandom().nextFloat() * 360f, 0);
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			if (getTarget() != null) {
				m.setTarget(getTarget());
			}
			level.addFreshEntity(m);
			level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		}
	}

	/** True for players a quake or rain should treat as enemies (everyone but creative / spectators). */
	static boolean enemy(LivingEntity e) {
		return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
	}

}
