package com.projecthero.mod.hideout.boss;

import org.joml.Vector3f;

import com.projecthero.mod.carnage.entity.CarnageAttackEntity;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonSpawnEntity;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the <b>Brood Spawn of Carnage</b> -- the Carnage Nest mini-boss: the first thing that crawled out of the
 * crimson meteor, a hulking red symbiote on Carnage's own player model in a darker, egg-studded recolour of his skin
 * (x1.6, 440 health). Claws (+Wither) in melee, and Carnage's moves out of {@link CarnageAttackEntity}:
 * <ul>
 * <li><b>Spike Line</b> (4-18): red spikes burst up one after another in a line to the target (9 each).</li>
 * <li><b>Goo Volley</b> (5-20): five goo globs lobbed round the target (4 + slow each).</li>
 * <li><b>Snare</b> (within 12, in sight): roots the target for 2 s.</li>
 * <li><b>Pounce</b> (6-14): leaps onto the target; a shockwave ring where it lands (6 + throw).</li>
 * <li><b>Brood</b>: three of Carnage's knee-high brood pour out of it (never more than six at once).</li>
 * </ul>
 * Fire hurts it half again as much. Phase 2 at half health: "Bloodbath" ({@link HideoutPhase.Kind#CRIMSON_SPIKES}).
 */
public class BroodSpawnBoss extends MiniBossBase {
	public static final float SCALE = 1.6f;
	public static final byte ACTION_SPIKES = 1;
	public static final byte ACTION_VOLLEY = 2;
	public static final byte ACTION_SNARE = 3;
	public static final byte ACTION_POUNCE = 4;
	public static final byte ACTION_BROOD = 5;
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.06f), 1.2f);

	private int spikeCooldown = 50;
	private int volleyCooldown = 80;
	private int snareCooldown = 120;
	private int pounceCooldown = 100;
	private int broodCooldown = 140;
	private Vec3 spikeFrom;
	private Vec3 spikeDir;

	public BroodSpawnBoss(EntityType<? extends BroodSpawnBoss> type, Level level) {
		super(type, level, HideoutFaction.CARNAGE, BossEvent.BossBarColor.RED, HideoutPhase.Kind.CRIMSON_SPIKES, 150);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 440.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.2)
				.add(Attributes.SAFE_FALL_DISTANCE, 16.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected boolean friend(Entity e) {
		return super.friend(e) || e instanceof CarnageEntity || e instanceof CrimsonSpawnEntity;
	}

	@Override
	protected float incoming(DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FIRE) ? amount * 1.5f : amount;
	}

	@Override
	protected void tickCooldowns() {
		spikeCooldown = Math.max(0, spikeCooldown - 1);
		volleyCooldown = Math.max(0, volleyCooldown - 1);
		snareCooldown = Math.max(0, snareCooldown - 1);
		pounceCooldown = Math.max(0, pounceCooldown - 1);
		broodCooldown = Math.max(0, broodCooldown - 1);
	}

	@Override
	protected byte chooseMove(ServerLevel level, LivingEntity t, double d) {
		if (broodCooldown <= 0 && broodAlive(level) < 6) {
			return ACTION_BROOD;
		}
		if (pounceCooldown <= 0 && d > 6 && d < 14 && onGround()) {
			return ACTION_POUNCE;
		}
		if (snareCooldown <= 0 && d < 12 && hasLineOfSight(t)) {
			return ACTION_SNARE;
		}
		if (spikeCooldown <= 0 && d > 4 && d < 18) {
			return ACTION_SPIKES;
		}
		if (volleyCooldown <= 0 && d > 5 && d < 20 && hasLineOfSight(t)) {
			return ACTION_VOLLEY;
		}
		return ACTION_NONE;
	}

	private int broodAlive(ServerLevel level) {
		return level.getEntitiesOfClass(CrimsonSpawnEntity.class, getBoundingBox().inflate(24), Entity::isAlive).size();
	}

	@Override
	protected boolean lockLook() {
		return action() != ACTION_POUNCE;
	}

	@Override
	protected void onStart(byte action) {
		SoundEvent s = switch (action) {
			case ACTION_BROOD -> SoundEvents.WARDEN_ROAR;
			case ACTION_POUNCE -> SoundEvents.RAVAGER_ROAR;
			default -> SoundEvents.WARDEN_ANGRY;
		};
		level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, 1.4f, 1.3f);
		LivingEntity t = getTarget();
		if (action == ACTION_SPIKES && t != null) {
			spikeFrom = position();
			Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
			spikeDir = d.lengthSqr() < 1.0e-3 ? flatLook() : d.normalize();
		}
	}

	@Override
	protected void tickMove(ServerLevel level, byte action, int ticks) {
		LivingEntity t = getTarget();
		switch (action) {
			case ACTION_SPIKES -> {
				if (ticks == 8 && spikeFrom != null) {
					for (int i = 1; i <= 10; i++) {
						Vec3 p = spikeFrom.add(spikeDir.scale(1.0 + i * 1.6));
						CarnageAttackEntity.spike(level, this, p.x, p.z, spikeFrom.y, 4 + i * 2);
					}
				}
				if (ticks >= 22) {
					end();
					spikeCooldown = 130 + random.nextInt(40);
				}
			}
			case ACTION_VOLLEY -> {
				if (ticks == 10 && t != null) {
					Vec3 from = position().add(0, getBbHeight() * 0.8, 0);
					CarnageAttackEntity.glob(level, this, from, t.position());
					for (int i = 0; i < 4; i++) {
						double a = i * Math.PI / 2 + random.nextDouble() * 0.5;
						CarnageAttackEntity.glob(level, this, from, t.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5));
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.4f, 0.5f);
				}
				if (ticks >= 18) {
					end();
					volleyCooldown = 110 + random.nextInt(40);
				}
			}
			case ACTION_SNARE -> {
				if (ticks == 8 && t != null && hasLineOfSight(t) && distanceTo(t) < 14) {
					CarnageAttackEntity.snare(level, this, t);
				}
				if (ticks >= 14) {
					end();
					snareCooldown = 240 + random.nextInt(60);
				}
			}
			case ACTION_POUNCE -> {
				if (ticks == 8 && t != null) {
					Vec3 d = t.position().subtract(position());
					double flat = Math.sqrt(d.x * d.x + d.z * d.z);
					setDeltaMovement(d.x / flat * Math.min(1.6, flat * 0.11), 0.75, d.z / flat * Math.min(1.6, flat * 0.11));
					hasImpulse = true;
					getLookControl().setLookAt(t, 30f, 30f);
				}
				if (ticks > 12 && (onGround() || ticks > 40)) {
					CarnageAttackEntity.shockwave(level, this, position());
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.6f);
					level.sendParticles(BLOOD, getX(), getY() + 0.3, getZ(), 40, 1.5, 0.2, 1.5, 0.05);
					end();
					pounceCooldown = 180 + random.nextInt(60);
				}
			}
			case ACTION_BROOD -> {
				if (ticks % 4 == 0) {
					level.sendParticles(BLOOD, getX(), getY() + 1.2, getZ(), 12, 0.8, 0.6, 0.8, 0.05);
				}
				if (ticks == 20) {
					for (int i = 0; i < 3; i++) {
						BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 2, 5, random);
						if (spot != null) {
							Mob m = HideoutGuards.reinforcementOf(level, HideoutFaction.CARNAGE, HideoutGuards.Role.BROODLING, spot, random);
							if (m != null && t != null) {
								m.setTarget(t);
							}
						}
					}
				}
				if (ticks >= 30) {
					end();
					broodCooldown = 360 + random.nextInt(120);
				}
			}
			default -> end();
		}
	}

	@Override
	protected void ambient(ServerLevel level) {
		if (tickCount % 6 == 0) {
			level.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.5, 0.8, 0.5, 0.0);
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
		}
		return hit;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(BLOOD, getX(), getY() + 1.5, getZ(), 120, 1.0, 1.4, 1.0, 0.1);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.1f;
	}
}
