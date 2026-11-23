package com.projecthero.mod.hideout.boss;

import java.util.List;

import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the <b>Hive Mother</b> -- the Symbiote Hive mini-boss, a towering black Symbiote host (x1.8, 400 health) drawn
 * on Carnage's player model in a black, white-spidered recolour of his skin (no new body). Claws in melee, and:
 * <ul>
 * <li><b>Tendril Lash</b> (up to 3 targets within 12 blocks, in sight): black tendrils from her chest -- 7 damage and a
 * drag towards her.</li>
 * <li><b>Spike Volley</b> (5-22 blocks): a fan of six black spikes (5 damage + a short Wither).</li>
 * <li><b>Brood</b>: two Symbiote-infested spiders crawl out of her (never more than four of her brood at once).</li>
 * </ul>
 * Like every Symbiote she hates fire (x1.5). Shared threat table, own boss bar ({@link HideoutBossLink}).
 */
public class HiveMotherBoss extends Monster implements HideoutBoss {
	public static final float SCALE = 1.8f;
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(HiveMotherBoss.class, EntityDataSerializers.BYTE);
	public static final byte ACTION_NONE = 0;
	public static final byte ACTION_LASH = 1;
	public static final byte ACTION_VOLLEY = 2;
	public static final byte ACTION_BROOD = 3;

	private final HideoutBossLink link = new HideoutBossLink(this, HideoutFaction.SYMBIOTE, BossEvent.BossBarColor.WHITE, this::busy);
	/** v0.17.7: the second phase (see HideoutPhase). */
	final HideoutPhase phase = new HideoutPhase(this, HideoutFaction.SYMBIOTE, HideoutPhase.Kind.BIOMASS_ERUPTION, 0.5f, 150);
	private int moveTicks;
	private int lashCooldown = 60;
	private int volleyCooldown = 100;
	private int broodCooldown = 160;
	/** Client: the tick the current action started (for the pose). */
	public float actionStartTick;

	public HiveMotherBoss(EntityType<? extends HiveMotherBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		setPersistenceRequired();
		refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.2)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	/** v0.17.7: the second phase (test hook). */
	public HideoutPhase phase() {
		return phase;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	private void setAction(byte a) {
		entityData.set(DATA_ACTION, a);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ACTION.equals(accessor)) {
			actionStartTick = tickCount;
		}
	}

	boolean busy() {
		return action() != ACTION_NONE;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true) {
			@Override
			public boolean canUse() {
				return !busy() && super.canUse();
			}
		});
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	private static boolean brood(Entity e) {
		return e instanceof Mob m && (SymbioteHost.is(m) || m instanceof HiveMotherBoss);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return brood(other) || super.isAlliedTo(other);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			amount *= 1.5f;
		}
		if (source.getEntity() != null && brood(source.getEntity())) {
			return false;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide()) {
			link.onHurt(source, amount);
			phase.hurtCheck((ServerLevel) level());
		}
		return hurt;
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
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		link.tick(server);
		phase.tick(server, busy());
		if (tickCount % 8 == 0) {
			server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.5, 0.8, 0.5, 0.005);
		}
		if (lashCooldown > 0) {
			lashCooldown--;
		}
		if (volleyCooldown > 0) {
			volleyCooldown--;
		}
		if (broodCooldown > 0) {
			broodCooldown--;
		}
		if (busy()) {
			tickMove(server);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}
		double d = distanceTo(t);
		if (broodCooldown <= 0 && broodAlive(server).size() < 4) {
			start(ACTION_BROOD);
		} else if (lashCooldown <= 0 && d < 12 && hasLineOfSight(t)) {
			start(ACTION_LASH);
		} else if (volleyCooldown <= 0 && d > 5 && d < 22 && hasLineOfSight(t)) {
			start(ACTION_VOLLEY);
		}
	}

	private List<Mob> broodAlive(ServerLevel level) {
		return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24), m -> m != this && m.isAlive() && SymbioteHost.is(m));
	}

	/** Starts a move now (test hook too). */
	public void start(byte action) {
		setAction(action);
		moveTicks = 0;
		getNavigation().stop();
		level().playSound(null, getX(), getY(), getZ(), action == ACTION_BROOD ? SoundEvents.WARDEN_ROAR : SoundEvents.WARDEN_ANGRY,
				SoundSource.HOSTILE, 1.4f, action == ACTION_BROOD ? 1.4f : 1.2f);
	}

	private Vec3 chest() {
		return position().add(0, getBbHeight() * 0.62, 0);
	}

	private void tickMove(ServerLevel level) {
		moveTicks++;
		getNavigation().stop();
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
		}
		switch (action()) {
			case ACTION_LASH -> {
				if (moveTicks == 10) {
					int n = 0;
					for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12),
							p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && hasLineOfSight(p))) {
						if (n++ >= 3) {
							break;
						}
						SymbioteTendrilEntity.fromBodyBlack(this, p.position().add(0, p.getBbHeight() * 0.5, 0), p, 18, 6, 0.3f);
					}
					if (n == 0 && t != null && hasLineOfSight(t)) {
						SymbioteTendrilEntity.fromBodyBlack(this, t.position().add(0, t.getBbHeight() * 0.5, 0), t, 18, 6, 0.3f);
					}
				}
				if (moveTicks == 16) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12),
							e -> e != this && e.isAlive() && !brood(e) && hasLineOfSight(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
						if (e.hurt(damageSources().mobAttack(this), 7.0f)) {
							Vec3 pull = position().subtract(e.position()).normalize().scale(1.1);
							e.push(pull.x, 0.35, pull.z);
							e.hurtMarked = true;
						}
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.4f, 0.5f);
				}
				if (moveTicks >= 24) {
					end();
					lashCooldown = 120 + random.nextInt(60);
				}
			}
			case ACTION_VOLLEY -> {
				if (moveTicks == 12 && t != null) {
					Vec3 from = chest();
					Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
					for (int i = 0; i < 6; i++) {
						double a = Math.toRadians(-25 + i * 10);
						Vec3 d = new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), aim.y + 0.04, aim.x * Math.sin(a) + aim.z * Math.cos(a));
						SymbioteSpikeEntity.shootBlack(this, from.add(d.scale(0.8)), d, 5.0f, 2.2f);
					}
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
				}
				if (moveTicks >= 20) {
					end();
					volleyCooldown = 90 + random.nextInt(40);
				}
			}
			case ACTION_BROOD -> {
				if (moveTicks % 4 == 0) {
					level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0, getZ(), 12, 0.8, 0.6, 0.8, 0.05);
				}
				if (moveTicks == 20) {
					for (int i = 0; i < 2; i++) {
						BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 2, 5, random);
						if (spot != null) {
							var m = HideoutGuards.spawn(level, HideoutGuards.Role.HOST_SPIDER, spot, random.nextFloat() * 360f, 12);
							if (m != null && t != null) {
								m.setTarget(t);
							}
						}
					}
				}
				if (moveTicks >= 30) {
					end();
					broodCooldown = 400 + random.nextInt(120);
				}
			}
			default -> end();
		}
	}

	private void end() {
		setAction(ACTION_NONE);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			link.onDeath(server);
			server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 120, 1.0, 1.4, 1.0, 0.2);
		}
	}

	@Override
	public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
		super.stopSeenByPlayer(player);
		link.stopSeen(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		link.detach();
		super.remove(reason);
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
		return 1.3f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
		phase.save(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
		phase.load(tag);
	}
}
