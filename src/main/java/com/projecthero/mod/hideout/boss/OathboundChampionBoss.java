package com.projecthero.mod.hideout.boss;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.hideout.mob.HideoutSoldier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the <b>Oathbound Champion</b> -- the Oathbreaker Keep mini-boss: the Oathbreaker's sworn champion, a towering
 * knight in soul-scorched plate with a greatsword (player model, x1.5, 520 health). Sword in melee, and:
 * <ul>
 * <li><b>Oath Combo</b> (within 4.5): three cuts in a 110-degree arc (8, 8, then 12 and a throw).</li>
 * <li><b>Shield Charge</b> (5-16): braces 0.6 s, then charges ~10 blocks straight ahead; whoever he runs through takes 10
 * and is thrown. Stops on walls. Dodge sideways.</li>
 * <li><b>Oath Slam</b>: raises the blade for 1 s (a soul-fire ring marks 5 blocks round him), then slams: 14, a launch
 * and soul fire.</li>
 * <li><b>Rally</b>: raises his blade; his knights within 16 get Strength and Speed for 10 s and heal 6; with fewer
 * than two near, a squire answers the call.</li>
 * </ul>
 * Phase 2 at half health: "Oath Broken" ({@link HideoutPhase.Kind#JUDGEMENT_BLADES}).
 */
public class OathboundChampionBoss extends MiniBossBase {
	public static final float SCALE = 1.5f;
	public static final byte ACTION_COMBO = 1;
	public static final byte ACTION_CHARGE = 2;
	public static final byte ACTION_SLAM = 3;
	public static final byte ACTION_RALLY = 4;
	public static final double SLAM_RADIUS = 5.0;

	private int comboCooldown = 30;
	private int chargeCooldown = 80;
	private int slamCooldown = 120;
	private int rallyCooldown = 200;
	private Vec3 chargeDir;
	private final Set<Integer> chargeHit = new HashSet<>();

	public OathboundChampionBoss(EntityType<? extends OathboundChampionBoss> type, Level level) {
		super(type, level, HideoutFaction.OATH, BossEvent.BossBarColor.BLUE, HideoutPhase.Kind.JUDGEMENT_BLADES, 150);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 520.0)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.2)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		return d;
	}

	@Override
	protected void tickCooldowns() {
		comboCooldown = Math.max(0, comboCooldown - 1);
		chargeCooldown = Math.max(0, chargeCooldown - 1);
		slamCooldown = Math.max(0, slamCooldown - 1);
		rallyCooldown = Math.max(0, rallyCooldown - 1);
	}

	@Override
	protected byte chooseMove(ServerLevel level, LivingEntity t, double d) {
		if (rallyCooldown <= 0) {
			return ACTION_RALLY;
		}
		if (slamCooldown <= 0 && d < SLAM_RADIUS + 1) {
			return ACTION_SLAM;
		}
		if (comboCooldown <= 0 && d < 4.5) {
			return ACTION_COMBO;
		}
		if (chargeCooldown <= 0 && d > 5 && d < 16 && hasLineOfSight(t)) {
			return ACTION_CHARGE;
		}
		return ACTION_NONE;
	}

	@Override
	protected boolean lockLook() {
		return action() != ACTION_CHARGE || moveTicks < 12;
	}

	@Override
	protected void onStart(byte action) {
		SoundEvent s = switch (action) {
			case ACTION_CHARGE -> SoundEvents.RAVAGER_ROAR;
			case ACTION_SLAM -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
			case ACTION_RALLY -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value();
			default -> SoundEvents.ARMOR_EQUIP_NETHERITE.value();
		};
		level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, 1.5f, action == ACTION_CHARGE ? 1.3f : 0.8f);
		chargeHit.clear();
	}

	@Override
	protected void tickMove(ServerLevel level, byte action, int ticks) {
		switch (action) {
			case ACTION_COMBO -> {
				if (ticks == 6 || ticks == 14 || ticks == 24) {
					boolean last = ticks == 24;
					swing(level, last ? 12f : 8f, last);
				}
				if (ticks >= 32) {
					end();
					comboCooldown = 100 + random.nextInt(40);
				}
			}
			case ACTION_CHARGE -> {
				if (ticks < 12) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.2, getZ(), 2, 0.4, 0.05, 0.4, 0.02);
					if (ticks == 11) {
						chargeDir = flatLook();
					}
				} else if (chargeDir != null) {
					Vec3 before = position();
					setDeltaMovement(chargeDir.x * 0.85, getDeltaMovement().y, chargeDir.z * 0.85);
					hasImpulse = true;
					level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 2, 0.3, 0.05, 0.3, 0.01);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), this::victim)) {
						if (chargeHit.add(e.getId()) && e.hurt(damageSources().mobAttack(this), 10f)) {
							e.push(chargeDir.x * 1.3, 0.5, chargeDir.z * 1.3);
							e.hurtMarked = true;
						}
					}
					boolean blocked = ticks > 14 && horizontalCollision;
					if (blocked || ticks >= 12 + 13) {
						if (blocked) {
							level.playSound(null, before.x, before.y, before.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.6f);
						}
						setDeltaMovement(Vec3.ZERO);
						end();
						chargeCooldown = 160 + random.nextInt(60);
					}
				} else {
					end();
				}
			}
			case ACTION_SLAM -> {
				if (ticks < 20 && ticks % 3 == 0) {
					int n = 28;
					for (int i = 0; i < n; i++) {
						double a = i * Mth.TWO_PI / n;
						level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * SLAM_RADIUS, getY() + 0.1, getZ() + Math.sin(a) * SLAM_RADIUS, 1, 0, 0.01, 0, 0);
					}
				}
				if (ticks == 20) {
					level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.5, 0.1, 0.5, 0);
					for (int i = 0; i < 40; i++) {
						double a = i * Mth.TWO_PI / 40;
						for (double r = 1; r <= SLAM_RADIUS; r += 1.3) {
							level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 1, 0, 0.12, 0, 0.02);
						}
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.6f, 0.7f);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SLAM_RADIUS, 2, SLAM_RADIUS), this::victim)) {
						double dx = e.getX() - getX();
						double dz = e.getZ() - getZ();
						if (dx * dx + dz * dz <= SLAM_RADIUS * SLAM_RADIUS && e.getY() < getY() + 2.5 && e.hurt(damageSources().mobAttack(this), 14f)) {
							e.push(0, 0.85, 0);
							e.hurtMarked = true;
							e.igniteForSeconds(3f);
						}
					}
				}
				if (ticks >= 34) {
					end();
					slamCooldown = 200 + random.nextInt(60);
				}
			}
			case ACTION_RALLY -> {
				if (ticks == 14) {
					int knights = 0;
					for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(16), m -> m != this && m.isAlive() && friend(m))) {
						m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
						m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
						m.heal(6f);
						level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
						if (m instanceof HideoutSoldier) {
							knights++;
						}
					}
					if (knights < 2) {
						BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 2, 6, random);
						if (spot != null) {
							Mob m = HideoutGuards.reinforcementOf(level, HideoutFaction.OATH, HideoutGuards.Role.SQUIRE, spot, random);
							if (m != null && getTarget() != null) {
								m.setTarget(getTarget());
							}
						}
					}
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() + 0.5, getZ(), 30, 0.3, 0.8, 0.3, 0.03);
				}
				if (ticks >= 26) {
					end();
					rallyCooldown = 500 + random.nextInt(100);
				}
			}
			default -> end();
		}
	}

	private void swing(ServerLevel level, float damage, boolean last) {
		Vec3 f = flatLook();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 2, getY() + 1.4, getZ() + f.z * 2, 2, 0.5, 0.1, 0.5, 0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, last ? 0.5f : 0.8f);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5), this::victim)) {
			if (inCone(e, 4.5, 110) && e.hurt(damageSources().mobAttack(this), damage)) {
				if (last) {
					Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
					e.push(push.x, 0.45, push.z);
					e.hurtMarked = true;
				}
			}
		}
	}

	@Override
	protected void ambient(ServerLevel level) {
		if (tickCount % 8 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.55, getZ(), 1, 0.4, 0.5, 0.4, 0.01);
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.igniteForSeconds(2f);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 80, 0.8, 1.4, 0.8, 0.08);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6f;
	}
}
