package com.projecthero.mod.frostgiant.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
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
 * v0.16.1: the <b>Frost Giant Warlord</b> -- wave 5's mini-boss, 11 blocks tall, horned helm and an ice greatsword, with
 * a boss bar of its own. 1200 health + 300 per extra fighter, 14 armour. Uses the shared {@link BossThreat} table.
 * <ul>
 *   <li><b>Cleave</b>: a wide arc, telegraphed for a second -- 18 damage.</li>
 *   <li><b>Sword Plant</b>: the sword goes into the ground and a ring of ice spikes runs outward -- jump it or be out of
 *       range (14 damage to anyone on the ground as it passes).</li>
 *   <li><b>Rally</b>: a war cry -- every giant within 24 blocks hits 20% harder for 8 s (frost runes swirl round them).</li>
 *   <li>At 30% it drops to one knee for 3 s and takes half again as much damage.</li>
 * </ul>
 */
public class FrostGiantWarlordEntity extends FrostGiantEntity {
	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.frost_giant_warlord"),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final BossThreat threat = new BossThreat(this).range(72).filter(FrostCombat::canTarget).holdWhile(this::busy);
	private boolean knelt;
	private int kneelTicks;
	private int rallyCooldown = 100;
	private boolean criedOut;
	private final Set<UUID> ringHit = new HashSet<>();

	public FrostGiantWarlordEntity(EntityType<? extends FrostGiantWarlordEntity> type, Level level) {
		super(type, level);
		this.xpReward = 150;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Warlord cfg = FrostGiantConfig.warlord();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.ATTACK_DAMAGE, cfg.cleaveDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 72.0)
				.add(Attributes.STEP_HEIGHT, 2.5);
	}

	public void configure(int fighters) {
		double hp = FrostGiantConfig.warlordHealthFor(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public BossThreat threat() {
		return threat;
	}

	@Override
	public String model() {
		return FrostGiantAnims.WARLORD;
	}

	@Override
	protected boolean huntsBraziers() {
		return true;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.WARLORD_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.WARLORD_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.WARLORD_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.WARLORD_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return null; // too big to flinch from a single blow
	}

	public boolean isKneeling() {
		return kneelTicks > 0;
	}

	@Override
	protected LivingEntity chooseTarget(ServerLevel level) {
		LivingEntity hot = threat.tick();
		if (hot != null) {
			return hot;
		}
		if (!threat.isEmpty() && getTarget() != null && FrostCombat.canTarget(getTarget())) {
			return getTarget();
		}
		return super.chooseTarget(level);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide()) {
			bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		}
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Warlord cfg = FrostGiantConfig.warlord();
		if (rallyCooldown > 0) {
			rallyCooldown--;
		}
		if (kneelTicks > 0) {
			getNavigation().stop();
			if (--kneelTicks == 0) {
				clearAnim();
				bossBar.setColor(BossEvent.BossBarColor.WHITE);
				cooldown = 20;
			} else if (kneelTicks % 6 == 0) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2, getZ(), 6, 1.0, 1.0, 1.0, 0.02);
			}
			return;
		}
		if (move != null) {
			tickMove(level, target, cfg);
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		if (!criedOut && target instanceof Player) {
			criedOut = true;
			startMove(FrostGiantAnims.WARLORD_WAR_CRY, target);
			return;
		}
		if (cooldown <= 0) {
			int giantsNear = level.getEntitiesOfClass(FrostGiantEntity.class, getBoundingBox().inflate(cfg.rallyRadius),
					g -> g != this && g.isAlive()).size();
			if (rallyCooldown <= 0 && giantsNear >= 2) {
				startMove(FrostGiantAnims.WARLORD_RALLY, target);
				rallyCooldown = 400;
				return;
			}
			double gap = gapTo(target);
			int close = FrostCombat.playersNear(level, position(), cfg.plantRadius - 2).size();
			if (gap <= cfg.cleaveReach - 1.0) {
				startMove(close >= 2 && random.nextBoolean() ? FrostGiantAnims.WARLORD_SWORD_PLANT : FrostGiantAnims.WARLORD_CLEAVE, target);
				return;
			}
			if (close >= 1 && gap <= cfg.plantRadius - 2 && random.nextInt(3) == 0) {
				startMove(FrostGiantAnims.WARLORD_SWORD_PLANT, target);
				return;
			}
		}
		approach(target, 1.0);
	}

	private void tickMove(ServerLevel level, LivingEntity target, FrostGiantConfig.Warlord cfg) {
		if (target != null && move != FrostGiantAnims.WARLORD_SWORD_PLANT) {
			face(target.position());
		}
		int hit = move.hit();
		if (move == FrostGiantAnims.WARLORD_WAR_CRY && moveTick == hit) {
			warCrySound(level);
		} else if (move == FrostGiantAnims.WARLORD_CLEAVE) {
			if (moveTick < hit && moveTick >= hit - 20 && moveTick % 3 == 0) {
				arc(level, cfg.cleaveReach, FrostFx.FROST);
			}
			if (moveTick == hit) {
				arc(level, cfg.cleaveReach, FrostFx.WHITE);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5f, 0.4f);
				Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
				for (LivingEntity e : FrostCombat.targetsNear(level, position(), cfg.cleaveReach + getBbWidth() * 0.5)) {
					Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
					if (to.lengthSqr() > 1.0e-3 && to.normalize().dot(fwd) < Math.cos(Math.toRadians(55))) {
						continue;
					}
					if (FrostCombat.hit(level, this, e, cfg.cleaveDamage, 0) && !(e instanceof RuneBrazierEntity)) {
						Vec3 push = to.lengthSqr() > 1.0e-3 ? to.normalize() : fwd;
						e.push(push.x * 1.2, 0.4, push.z * 1.2);
						e.hurtMarked = true;
					}
				}
			}
		} else if (move == FrostGiantAnims.WARLORD_SWORD_PLANT) {
			if (moveTick == hit) {
				ringHit.clear();
				FrostFx.shake(level, position(), 0.7f, 14, 24);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			if (moveTick >= hit) {
				double r = 2.0 + (moveTick - hit) * 1.1;
				if (r <= cfg.plantRadius) {
					Vec3 c = position().add(0, 0.2, 0);
					int points = Math.max(12, (int) (r * 5));
					// v0.16.7 (perf): one batched payload per viewer, not a forced packet per point
					com.projecthero.mod.network.ParticleBatch batch = new com.projecthero.mod.network.ParticleBatch();
					for (int i = 0; i < points; i++) {
						double a = i * Math.PI * 2 / points;
						batch.addForced(FrostFx.ICE, FrostFx.FORCED_RANGE, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 2, 0.1, 0.4, 0.1, 0.2);
					}
					batch.send(level);
					for (LivingEntity e : FrostCombat.targetsNear(level, position(), r + 0.6)) {
						double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
						if (d < r - 1.4 || !e.onGround() && !(e instanceof RuneBrazierEntity) || !ringHit.add(e.getUUID())) {
							continue; // jumped it, or already hit
						}
						if (FrostCombat.hit(level, this, e, cfg.plantDamage, 40) && !(e instanceof RuneBrazierEntity)) {
							e.push(0, 0.7, 0);
							e.hurtMarked = true;
						}
					}
				}
			}
		} else if (move == FrostGiantAnims.WARLORD_RALLY && moveTick == hit) {
			warCrySound(level);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
			for (FrostGiantEntity g : level.getEntitiesOfClass(FrostGiantEntity.class, getBoundingBox().inflate(cfg.rallyRadius))) {
				if (g.isAlive()) {
					g.rally(cfg.rallyTicks);
				}
			}
			FrostFx.ring(level, FrostFx.RUNE, position().add(0, 1, 0), 6, 36, true);
		}
		if (moveTick >= move.length()) {
			endMove(move == FrostGiantAnims.WARLORD_WAR_CRY ? 10 : cfg.attackCooldownTicks);
		}
	}

	private void arc(ServerLevel level, double reach, net.minecraft.core.particles.ParticleOptions p) {
		Vec3 c = position().add(0, getBbHeight() * 0.25, 0);
		for (int i = -6; i <= 6; i++) {
			Vec3 d = Vec3.directionFromRotation(0, getYRot() + i * 9f);
			for (double r = reach * 0.5; r <= reach; r += reach * 0.25) {
				FrostFx.forced(level, p, c.x + d.x * r, c.y, c.z + d.z * r, 1, 0, 0, 0, 0);
			}
		}
	}

	@Override
	protected float damageTakenMultiplier(DamageSource source) {
		return isKneeling() ? (float) FrostGiantConfig.warlord().kneelDamageMultiplier : 1.0f;
	}

	@Override
	protected void onHurt(ServerLevel server, DamageSource source, float amount) {
		threat.record(source, amount);
		FrostGiantConfig.Warlord cfg = FrostGiantConfig.warlord();
		if (!knelt && getHealth() / getMaxHealth() <= cfg.kneelAt && getHealth() > 1f) {
			knelt = true;
			kneelTicks = cfg.kneelTicks;
			move = null;
			setAnim(FrostGiantAnims.WARLORD_KNEEL);
			bossBar.setColor(BossEvent.BossBarColor.YELLOW);
			FrostFx.shake(server, position(), 0.6f, 12, 20);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.4f);
		}
	}

	@Override
	protected void onLanded(ServerLevel server) {
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0f, 0.7f);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bossBar.removeAllPlayers();
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
}
