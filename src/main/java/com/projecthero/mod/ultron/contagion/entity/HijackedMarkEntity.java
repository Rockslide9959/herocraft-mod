package com.projecthero.mod.ultron.contagion.entity;

import java.util.List;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSkin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Hijacked Mark</b> -- one of the Iron Legion in phase 3 of the Omega fight. An empty Iron Man suit Ultron
 * has taken over: drawn with the real Mark's armour models ({@link #suitId}, Mark 2 / III / 4 / 5 / 6, or a fighter's own
 * Mark) and a red visor glow; no body inside. It flies, snaps 5-damage repulsor bolts and every so often charges a
 * unibeam (a 2 s red telegraph line, then 14 damage down it). 150 health. A Purge Charge frees it: it powers down and
 * drops 1 Ultron Scrap -- never the armour itself (it carries no items at all; the client only draws them).
 */
public class HijackedMarkEntity extends UltronRobot {
	public static final List<String> LEGION_SUITS = List.of("mark_2", "mark_iii", "mark_4", "mark_v", "mark_6");
	public static final byte ACTION_REPULSOR = 2;
	public static final byte ACTION_UNIBEAM = 3;

	private static final EntityDataAccessor<String> DATA_SUIT = SynchedEntityData.defineId(HijackedMarkEntity.class, EntityDataSerializers.STRING);

	private int boltCooldown = 40;
	private int beamCooldown = 160;
	private int beamTick = -1;
	private Vec3 beamAim;
	private int orbitDir;
	private final float bobPhase;
	private float damageScale = 1f;

	public HijackedMarkEntity(EntityType<? extends HijackedMarkEntity> type, Level level) {
		super(type, level);
		this.xpReward = 15;
		this.bobPhase = random.nextFloat() * 100f;
		this.orbitDir = random.nextBoolean() ? 1 : -1;
		setNoGravity(true);
		setPersistenceRequired();
		addTag(Contagion.UNIT_TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, ContagionConfig.omega().legionHealth)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SUIT, "mark_iii");
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.DRONE;
	}

	@Override
	protected void registerGoals() {
	}

	public String suitId() {
		return entityData.get(DATA_SUIT);
	}

	public void configure(String suitId, double health, float damageScale) {
		entityData.set(DATA_SUIT, suitId);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
		this.damageScale = damageScale;
	}

	public boolean chargingUnibeam() {
		return action() == ACTION_UNIBEAM;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isNoAi()) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		if (isStunned()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !UltronCombat.canTarget(target) || distanceToSqr(target) > 64 * 64) {
			Player p = UltronCombat.nearestPlayer(server, position(), 56);
			setTarget(p);
			target = p;
		}
		if (target == null) {
			setDeltaMovement(getDeltaMovement().scale(0.8));
			return;
		}
		face(target.getEyePosition());
		if (beamTick >= 0) {
			tickUnibeam(server, target);
			return;
		}
		Vec3 away = position().subtract(target.position());
		double ang = Math.atan2(away.z, away.x) + orbitDir * 0.25;
		double up = 3.5 + Math.sin((tickCount + bobPhase) * 0.07) * 1.2;
		Vec3 want = target.position().add(Math.cos(ang) * 8, up, Math.sin(ang) * 8);
		steer(want, 0.45);
		if (horizontalCollision || tickCount % 90 == 0) {
			orbitDir = -orbitDir;
		}
		if (--beamCooldown <= 0 && hasLineOfSight(target)) {
			beamCooldown = 180 + random.nextInt(80);
			beamTick = 0;
			beamAim = target.position().add(0, target.getBbHeight() * 0.5, 0);
			setAction(ACTION_UNIBEAM);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.2f);
			return;
		}
		if (--boltCooldown <= 0 && hasLineOfSight(target)) {
			boltCooldown = 45 + random.nextInt(25);
			setAction(ACTION_REPULSOR);
			Vec3 from = palm();
			Vec3 dir = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from);
			UltronCombat.hitscan(server, this, from, dir, 40, ContagionConfig.omega().repulsorDamage * damageScale, UltronFx.BOLT, 5, 0.3f);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.8f, 1.9f);
		} else if (boltCooldown < 35 && action() == ACTION_REPULSOR) {
			setAction(ACTION_NONE);
		}
		if (tickCount % 3 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() - 0.1, getZ(), 1, 0.1, 0.02, 0.1, 0.0);
		}
	}

	private void tickUnibeam(ServerLevel server, LivingEntity target) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		beamTick++;
		setDeltaMovement(getDeltaMovement().scale(0.6));
		Vec3 chest = position().add(0, getBbHeight() * 0.62, 0);
		if (beamTick < cfg.unibeamTelegraphTicks) {
			// the aim drifts slowly after the target for the first second, then locks
			if (beamTick < cfg.unibeamTelegraphTicks / 2) {
				beamAim = beamAim.lerp(target.position().add(0, target.getBbHeight() * 0.5, 0), 0.08);
			}
			if (beamTick % 2 == 0) {
				UltronFx.beam(server, chest, end(server, chest, beamAim), UltronFx.LASER_SIGHT, 3);
			}
			return;
		}
		Vec3 to = end(server, chest, beamAim);
		UltronFx.beam(server, chest, to, UltronFx.CANNON, 12);
		for (LivingEntity e : UltronCombat.alongLine(server, chest, to, 1.0)) {
			UltronCombat.hit(server, this, e, cfg.unibeamDamage * damageScale);
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.6f);
		beamTick = -1;
		setAction(ACTION_NONE);
	}

	private Vec3 end(ServerLevel server, Vec3 from, Vec3 at) {
		Vec3 dir = at.subtract(from).normalize();
		Vec3 to = from.add(dir.scale(40));
		var hit = server.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
	}

	private Vec3 palm() {
		Vec3 look = getViewVector(1.0f);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return position().add(0, getBbHeight() * 0.7, 0).add(look.scale(0.6)).add(right.scale(0.35));
	}

	private void face(Vec3 at) {
		double dx = at.x - getX(), dz = at.z - getZ(), dy = at.y - getEyeY();
		float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setXRot((float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)));
	}

	private void steer(Vec3 want, double speed) {
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		Vec3 desired = len < 0.3 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.3) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.2));
	}

	/** A Purge Charge frees it: it powers down, drops a single Ultron Scrap and is gone. */
	public void powerDown() {
		if (!(level() instanceof ServerLevel server) || isRemoved()) {
			return;
		}
		spawnAtLocation(new ItemStack(ContagionItems.ULTRON_SCRAP));
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 30, 0.4, 0.6, 0.4, 0.2);
		server.sendParticles(UltronFx.CYAN, getX(), getY() + 1, getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0f, 0.8f);
		discard();
	}

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			if (isDeadOrDying()) {
				super.travel(input);
				return;
			}
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
		spawnAtLocation(new ItemStack(ContagionItems.ULTRON_SCRAP));
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("Suit", suitId());
		tag.putFloat("DamageScale", damageScale);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Suit")) {
			entityData.set(DATA_SUIT, tag.getString("Suit"));
		}
		damageScale = tag.contains("DamageScale") ? tag.getFloat("DamageScale") : 1f;
		setNoGravity(true);
	}
}
