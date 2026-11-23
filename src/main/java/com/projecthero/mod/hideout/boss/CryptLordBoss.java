package com.projecthero.mod.hideout.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the <b>Crypt Lord</b> -- the Gravebound Crypt mini-boss: a dead king in a tarnished crown and rotted royal
 * robes, on the Zombie Raid's player rig (x1.7, 460 health). Claws in melee, and:
 * <ul>
 * <li><b>Soul Reap</b> (within 5): 0.6 s wind-up, a 200-degree scythe sweep of soul fire round his front -- 12 + Wither
 * and a shove.</li>
 * <li><b>Grave Grasp</b> (within 16): soul rings under up to three players, 1 s later dead hands burst up -- 8 and rooted
 * (Slowness X) for 1.5 s. Step out of the ring.</li>
 * <li><b>Curse Volley</b> (5-24, in sight): three slow soul bolts in a fan -- 6 + Wither each.</li>
 * <li><b>Raise the Dead</b>: kneels; two Risen claw up out of the floor (never more than four of his dead at once).</li>
 * </ul>
 * Phase 2 at half health: "Requiem" ({@link HideoutPhase.Kind#REQUIEM_RINGS}).
 */
public class CryptLordBoss extends MiniBossBase {
	public static final float SCALE = 1.7f;
	public static final byte ACTION_REAP = 1;
	public static final byte ACTION_GRASP = 2;
	public static final byte ACTION_VOLLEY = 3;
	public static final byte ACTION_RAISE = 4;

	private int reapCooldown = 40;
	private int graspCooldown = 80;
	private int volleyCooldown = 60;
	private int raiseCooldown = 160;
	private final List<Vec3> graspMarks = new ArrayList<>();
	private final List<Bolt> bolts = new ArrayList<>();

	/** A soul bolt in flight (transient). */
	private static final class Bolt {
		Vec3 pos;
		final Vec3 vel;
		int life = 40;

		Bolt(Vec3 pos, Vec3 vel) {
			this.pos = pos;
			this.vel = vel;
		}
	}

	public CryptLordBoss(EntityType<? extends CryptLordBoss> type, Level level) {
		super(type, level, HideoutFaction.GRAVE, BossEvent.BossBarColor.PURPLE, HideoutPhase.Kind.REQUIEM_RINGS, 150);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 460.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 11.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.2)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void tickCooldowns() {
		if (reapCooldown > 0) {
			reapCooldown--;
		}
		if (graspCooldown > 0) {
			graspCooldown--;
		}
		if (volleyCooldown > 0) {
			volleyCooldown--;
		}
		if (raiseCooldown > 0) {
			raiseCooldown--;
		}
	}

	@Override
	protected byte chooseMove(ServerLevel level, LivingEntity t, double d) {
		if (raiseCooldown <= 0 && deadAlive(level) < 4) {
			return ACTION_RAISE;
		}
		if (reapCooldown <= 0 && d < 5) {
			return ACTION_REAP;
		}
		if (graspCooldown <= 0 && d < 16) {
			return ACTION_GRASP;
		}
		if (volleyCooldown <= 0 && d > 5 && d < 24 && hasLineOfSight(t)) {
			return ACTION_VOLLEY;
		}
		return ACTION_NONE;
	}

	private int deadAlive(ServerLevel level) {
		return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24),
				m -> m != this && m.isAlive() && m.getTags().contains(DEAD_TAG)).size();
	}

	/** Tag on the Risen he raises (for the cap of four). */
	public static final String DEAD_TAG = "projecthero.crypt_lord_dead";

	@Override
	protected void onStart(byte action) {
		SoundEvent s = switch (action) {
			case ACTION_REAP -> SoundEvents.WITHER_SKELETON_AMBIENT;
			case ACTION_GRASP -> SoundEvents.SCULK_SHRIEKER_SHRIEK;
			case ACTION_RAISE -> SoundEvents.EVOKER_PREPARE_SUMMON;
			default -> SoundEvents.EVOKER_CAST_SPELL;
		};
		level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, 1.6f, 0.6f);
		if (action == ACTION_GRASP) {
			graspMarks.clear();
			int n = 0;
			for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(16), this::victim)) {
				graspMarks.add(p.position());
				if (++n >= 3) {
					break;
				}
			}
			if (graspMarks.isEmpty() && getTarget() != null) {
				graspMarks.add(getTarget().position());
			}
		}
	}

	@Override
	protected void tickMove(ServerLevel level, byte action, int ticks) {
		switch (action) {
			case ACTION_REAP -> {
				if (ticks < 12 && ticks % 3 == 0) {
					Vec3 f = flatLook();
					level.sendParticles(ParticleTypes.SOUL, getX() - f.z * 1.2, getY() + 2.6, getZ() + f.x * 1.2, 2, 0.1, 0.1, 0.1, 0.01);
				}
				if (ticks == 12) {
					Vec3 f = flatLook();
					for (int i = -10; i <= 10; i++) {
						double a = Math.atan2(f.z, f.x) + Math.toRadians(i * 10);
						for (double r = 1.5; r <= 4.5; r += 1.5) {
							level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * r, getY() + 1.0, getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
						}
					}
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 2, getY() + 1.2, getZ() + f.z * 2, 3, 1.0, 0.2, 1.0, 0);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6f, 0.5f);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), this::victim)) {
						if (inCone(e, 4.5, 200) && e.hurt(damageSources().mobAttack(this), 12f)) {
							e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
							Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.9);
							e.push(push.x, 0.3, push.z);
							e.hurtMarked = true;
						}
					}
				}
				if (ticks >= 22) {
					end();
					reapCooldown = 120 + random.nextInt(40);
				}
			}
			case ACTION_GRASP -> {
				if (ticks % 4 == 0 && ticks < 20) {
					for (Vec3 m : graspMarks) {
						for (int i = 0; i < 12; i++) {
							double a = i * Mth.TWO_PI / 12;
							level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.x + Math.cos(a) * 1.3, m.y + 0.1, m.z + Math.sin(a) * 1.3, 1, 0, 0.01, 0, 0);
						}
					}
				}
				if (ticks == 20) {
					for (Vec3 m : graspMarks) {
						level.sendParticles(ParticleTypes.SOUL, m.x, m.y + 0.6, m.z, 16, 0.5, 0.6, 0.5, 0.05);
						level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
								net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState()), m.x, m.y + 0.2, m.z, 30, 0.6, 0.2, 0.6, 0.15);
						level.playSound(null, m.x, m.y, m.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8f, 0.5f);
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(m.x - 1.3, m.y - 0.5, m.z - 1.3, m.x + 1.3, m.y + 2.5, m.z + 1.3), this::victim)) {
							if (e.hurt(damageSources().mobAttack(this), 8f)) {
								e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 9));
							}
						}
					}
				}
				if (ticks >= 28) {
					end();
					graspCooldown = 180 + random.nextInt(60);
				}
			}
			case ACTION_VOLLEY -> {
				LivingEntity t = getTarget();
				if (ticks == 10 && t != null) {
					Vec3 from = position().add(0, getBbHeight() * 0.7, 0);
					Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
					for (int i = -1; i <= 1; i++) {
						double a = Math.toRadians(i * 12);
						Vec3 d = new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), aim.y, aim.x * Math.sin(a) + aim.z * Math.cos(a));
						bolts.add(new Bolt(from.add(d.scale(1.0)), d.scale(0.75)));
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.4f, 0.5f);
				}
				if (ticks >= 18) {
					end();
					volleyCooldown = 100 + random.nextInt(40);
				}
			}
			case ACTION_RAISE -> {
				if (ticks % 4 == 0) {
					level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 10, 1.5, 0.1, 1.5, 0.03);
				}
				if (ticks == 24) {
					for (int i = 0; i < 2; i++) {
						BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 2, 6, random);
						if (spot != null) {
							Mob m = HideoutGuards.reinforcementOf(level, HideoutFaction.GRAVE, HideoutGuards.Role.RISEN, spot, random);
							if (m != null) {
								m.addTag(DEAD_TAG);
								if (getTarget() != null) {
									m.setTarget(getTarget());
								}
							}
						}
					}
				}
				if (ticks >= 34) {
					end();
					raiseCooldown = 400 + random.nextInt(120);
				}
			}
			default -> end();
		}
	}

	@Override
	protected void ambient(ServerLevel level) {
		if (tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.95, getZ(), 1, 0.25, 0.05, 0.25, 0.005);
		}
		if (tickCount % 10 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.6, 0.05, 0.6, 0.005);
		}
		tickBolts(level);
	}

	private void tickBolts(ServerLevel level) {
		Iterator<Bolt> it = bolts.iterator();
		while (it.hasNext()) {
			Bolt b = it.next();
			Vec3 next = b.pos.add(b.vel);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.pos.x, b.pos.y, b.pos.z, 2, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(ParticleTypes.SOUL, b.pos.x, b.pos.y, b.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
			boolean done = --b.life <= 0;
			if (!done && level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
				done = true;
			}
			if (!done) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(0.7), this::victim)) {
					if (e.hurt(damageSources().indirectMagic(this, this), 6f)) {
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					}
					done = true;
					break;
				}
			}
			if (done) {
				level.sendParticles(ParticleTypes.SOUL, next.x, next.y, next.z, 8, 0.2, 0.2, 0.2, 0.03);
				it.remove();
			} else {
				b.pos = next;
			}
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
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 80, 1.0, 1.4, 1.0, 0.1);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6f;
	}
}
