package com.projecthero.mod.ultron.entity;

import java.util.UUID;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.UltronJam;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Ultron Hijacker</b> -- the saboteur. Lean, dark gunmetal with red seams (50 health, 2 armour) and fast.
 * It runs <b>cloaked</b> -- only a shimmer -- until it is within 6 blocks of its prey, then <b>latches on</b> for up to
 * 4 s: one random ability key of theirs (any of the six universal slots: hero abilities, Iron Man slots, mutation slots)
 * goes dead for 6 s ({@link UltronJam}), and a worn Iron Man suit loses 15% of its energy. Hitting it breaks the latch.
 * The Mind Stone and the Iron Man highlight show it while it is cloaked (client-side decisions only, never a glowing flag).
 */
public class UltronHijackerEntity extends UltronRobot {
	public static final byte ACTION_LATCH = 2;
	private static final EntityDataAccessor<Boolean> DATA_CLOAKED = SynchedEntityData.defineId(UltronHijackerEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_LATCHED = SynchedEntityData.defineId(UltronHijackerEntity.class, EntityDataSerializers.INT);

	private UUID latchedTo;
	private int latchTicks;
	private int cooldown = 20;
	private int reveal;

	public UltronHijackerEntity(EntityType<? extends UltronHijackerEntity> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Hijacker cfg = UltronConfig.hijacker();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CLOAKED, true);
		builder.define(DATA_LATCHED, -1);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.HIJACKER;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, UltronRobot.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	/** Cloaked (synced): the renderer draws only a shimmer, unless the viewer's Mind Stone / Iron Man highlight reveals it. */
	public boolean cloaked() {
		return entityData.get(DATA_CLOAKED);
	}

	public boolean isLatched() {
		return latchedTo != null || entityData.get(DATA_LATCHED) >= 0;
	}

	/** The entity id it is latched onto (synced; -1 for none). */
	public int latchedId() {
		return entityData.get(DATA_LATCHED);
	}

	public UUID latchedTo() {
		return latchedTo;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		UltronConfig.Hijacker cfg = UltronConfig.hijacker();
		if (cooldown > 0) {
			cooldown--;
		}
		if (reveal > 0) {
			reveal--;
		}
		if (isStunned()) {
			if (latchedTo != null) {
				release(server, false);
			}
			setCloaked(false);
			return;
		}
		if (latchedTo != null) {
			tickLatch(server, cfg);
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !UltronCombat.canTarget(target)) {
			if (target != null) {
				setTarget(null);
			}
			if (tickCount % 20 == 0) {
				Player p = UltronCombat.nearestPlayer(server, position(), 48);
				if (p != null) {
					setTarget(p);
				}
			}
			setCloaked(reveal == 0);
			return;
		}
		double d = distanceTo(target);
		setCloaked(reveal == 0 && d > cfg.decloakRange);
		if (tickCount % 5 == 0) {
			getNavigation().moveTo(target, 1.15);
		}
		getLookControl().setLookAt(target, 40f, 40f);
		if (d <= 1.9 && cooldown <= 0 && target instanceof ServerPlayer sp) {
			latch(server, sp);
		}
		if (cloaked() && tickCount % 6 == 0) {
			// the shimmer: a faint heat-haze of sparks, nothing more
			server.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.25, 0.5, 0.25, 0.0);
		}
	}

	private void setCloaked(boolean c) {
		if (entityData.get(DATA_CLOAKED) != c) {
			entityData.set(DATA_CLOAKED, c);
			if (!c && level() instanceof ServerLevel server) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8f, 1.4f);
			}
		}
	}

	/** Latches onto {@code player} now (also the gametests' hook): one random ability key jammed, a suit drained. */
	public void latch(ServerLevel server, ServerPlayer player) {
		UltronConfig.Hijacker cfg = UltronConfig.hijacker();
		latchedTo = player.getUUID();
		latchTicks = 0;
		entityData.set(DATA_LATCHED, player.getId());
		setCloaked(false);
		setAction(ACTION_LATCH);
		setNoGravity(true);
		getNavigation().stop();
		UltronJam.jam(player, UltronJam.bit(UltronJam.randomSlot(random)), cfg.jamTicks, false);
		if (TonyStark.hasPower(player)) {
			String suit = IronManArmor.wornSuitId(player);
			if (suit != null) {
				IronManEnergy.addEnergy(player, suit, (float) (-IronManEnergy.capacity(suit) * cfg.ironManEnergyDrain));
			}
		}
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.4f, 0.6f);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0f, 1.8f);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 14, 0.3, 0.4, 0.3, 0.2);
	}

	private void tickLatch(ServerLevel server, UltronConfig.Hijacker cfg) {
		Entity e = server.getEntity(latchedTo);
		latchTicks++;
		if (!(e instanceof ServerPlayer p) || !p.isAlive() || !UltronCombat.canTarget(p) || latchTicks >= cfg.latchTicks) {
			release(server, false);
			return;
		}
		// clinging to their back, over the right shoulder
		float yawRad = p.yBodyRot * ((float) Math.PI / 180f);
		Vec3 back = new Vec3(Math.sin(yawRad), 0, -Math.cos(yawRad)).scale(0.45);
		Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).scale(0.3);
		Vec3 at = p.position().add(back).add(right).add(0, 0.25, 0);
		setPos(at.x, at.y, at.z);
		setDeltaMovement(Vec3.ZERO);
		setYRot(p.yBodyRot);
		yBodyRot = p.yBodyRot;
		yHeadRot = p.yBodyRot;
		fallDistance = 0;
		if (latchTicks % 4 == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.3, p.getZ(), 3, 0.25, 0.3, 0.25, 0.1);
			server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + 1.2, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** Lets go (hit, stunned, or the latch ran out): it drops off and backs away, then hunts again. */
	public void release(ServerLevel server, boolean knocked) {
		Entity e = latchedTo == null ? null : server.getEntity(latchedTo);
		latchedTo = null;
		latchTicks = 0;
		entityData.set(DATA_LATCHED, -1);
		setNoGravity(false);
		setAction(ACTION_NONE);
		cooldown = UltronConfig.hijacker().latchCooldownTicks;
		reveal = 40;
		if (e != null) {
			Vec3 away = position().subtract(e.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
			setDeltaMovement(flat.scale(knocked ? 0.8 : 0.4).add(0, 0.3, 0));
			hurtMarked = true;
		}
		if (knocked) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2f, 0.8f);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			reveal = 40;
			setCloaked(false);
			if (latchedTo != null) {
				release(server, true); // hitting it breaks the latch
			}
		}
		return hurt;
	}

	@Override
	public boolean isPushable() {
		return latchedTo == null && entityData.get(DATA_LATCHED) < 0 && super.isPushable();
	}

	@Override
	protected void doPush(Entity entity) {
		if (!isLatched()) {
			super.doPush(entity);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server && latchedTo != null) {
			release(server, true);
		}
		super.die(source);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("LatchCooldown", cooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		cooldown = tag.getInt("LatchCooldown");
	}
}
