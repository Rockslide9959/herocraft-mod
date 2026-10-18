package com.projecthero.mod.hero.power.p09;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cryokinesis hold-R: the <b>Ice Javelin</b>, drawn by {@code IceJavelinRenderer} -- a 2.6-block spear of clear
 * ice with a glowing core, a packed-ice haft and a crown of fins. It first forms in the raised right hand for
 * {@link #CHARGE_TICKS} (growing from a sliver to full size while the caster draws back), then flies straight along the
 * caster's look, <b>piercing</b> up to {@link CryokinesisHandlers#JAVELIN_PIERCE} creatures (18 damage and 3 Frost each)
 * before it shatters on the next thing or a block. Never saved.
 */
public class IceJavelinEntity extends ThrowableProjectile {
	public static final int CHARGE_TICKS = 10;
	private static final int FLIGHT_TICKS = 40;
	private static final float SPEED = 2.4f;
	/** Ticks of charge left (0 = flying). */
	private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(IceJavelinEntity.class, EntityDataSerializers.INT);
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

	private final Set<Integer> pierced = new HashSet<>();
	private final Set<Integer> passed = new HashSet<>();
	private int flying;

	public IceJavelinEntity(EntityType<? extends IceJavelinEntity> type, Level level) {
		super(type, level);
	}

	public IceJavelinEntity(Level level, LivingEntity shooter) {
		super(CryoEntities.ICE_JAVELIN, shooter, level);
		this.entityData.set(CHARGE, CHARGE_TICKS);
		Vec3 at = handPoint(shooter, 1.0f);
		setPos(at.x, at.y, at.z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(CHARGE, 0);
	}

	public int charge() {
		return entityData.get(CHARGE);
	}

	/** 0..1 of the way formed (1 once thrown). */
	public float formed(float partial) {
		int c = charge();
		return c <= 0 ? 1.0f : Math.min(1.0f, (CHARGE_TICKS - c + partial) / CHARGE_TICKS);
	}

	public int piercedCount() {
		return pierced.size();
	}

	/** Where the spear is held while it forms: over the right shoulder, drawn back, along the look. */
	public static Vec3 handPoint(Entity shooter, float partial) {
		Vec3 eye = shooter.getEyePosition(partial);
		Vec3 look = shooter.getViewVector(partial);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(right.scale(0.5)).add(0, 0.3, 0).add(look.scale(-0.1));
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		int c = charge();
		if (c > 0) {
			// forming in the hand: follow the caster, no flight, no hits
			baseTick();
			Entity owner = getOwner();
			if (!level().isClientSide) {
				if (!(owner instanceof LivingEntity le) || !le.isAlive() || owner.level() != level()) {
					discard();
					return;
				}
				Vec3 at = handPoint(owner, 1.0f);
				setPos(at.x, at.y, at.z);
				setDeltaMovement(Vec3.ZERO);
				if (level() instanceof ServerLevel sl && c % 2 == 0) {
					sl.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.01);
				}
				entityData.set(CHARGE, c - 1);
				if (c - 1 <= 0) {
					launch(le);
				}
			}
			return;
		}
		super.tick();
		if (level() instanceof ServerLevel server && !isRemoved()) {
			sweep(server);
		}
		if (level() instanceof ServerLevel server && !isRemoved()) {
			flying++;
			server.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 2, 0.08, 0.08, 0.08, 0.0);
			if (flying % 2 == 0) {
				server.sendParticles(ICE_DUST, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (flying > FLIGHT_TICKS) {
				burst(server, position());
			}
		}
	}

	/**
	 * At 2.4 blocks a tick the vanilla hit test (only the nearest creature on the tick's path) would skip a second
	 * creature standing close behind the first: everything else this tick's path ran through is pierced too, nearest
	 * first, up to the pierce limit.
	 */
	private void sweep(ServerLevel server) {
		Vec3 from = new Vec3(xo, yo, zo);
		Vec3 to = position();
		if (from.distanceToSqr(to) < 1.0e-6) {
			return;
		}
		java.util.List<Entity> found = new java.util.ArrayList<>();
		for (Entity e : server.getEntities(this, getBoundingBox().expandTowards(from.subtract(to)).inflate(1.0), this::canHitEntity)) {
			if (e.getBoundingBox().inflate(0.3).clip(from, to).isPresent() || e.getBoundingBox().inflate(0.3).contains(to)) {
				found.add(e);
			}
		}
		found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
		for (Entity e : found) {
			if (isRemoved() || pierced.size() >= CryokinesisHandlers.JAVELIN_PIERCE) {
				break;
			}
			onHit(new EntityHitResult(e, e.getBoundingBox().getCenter()));
		}
	}

	private void launch(LivingEntity owner) {
		Vec3 look = owner.getLookAngle();
		Vec3 start = owner.getEyePosition().add(look.scale(0.8));
		Vec3 right = new Vec3(-look.z, 0, look.x);
		if (right.lengthSqr() > 1.0e-6) {
			start = start.add(right.normalize().scale(0.25));
		}
		setPos(start.x, start.y - 0.15, start.z);
		shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0f, SPEED, 0.0f);
		hurtMarked = true;
		if (level() instanceof ServerLevel sl) {
			sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
			sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return charge() <= 0 && !pierced.contains(e.getId()) && !passed.contains(e.getId()) && !(e instanceof IceBoltEntity) && !(e instanceof IceJavelinEntity)
				&& !(e instanceof FrostShellEntity) && !(e instanceof IceSpikeEntity) && !(e instanceof CryoFieldEntity)
				&& super.canHitEntity(e);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel server) || result.getEntity() == getOwner()) {
			return;
		}
		if (getOwner() instanceof ServerPlayer p && AbilityHelpers.asLiving(result.getEntity()) instanceof LivingEntity e
				&& com.projecthero.mod.combat.HeroTargets.canHarm(p, e)) {
			pierced.add(result.getEntity().getId());
			boolean landed = AbilityHelpers.hurtBurst(p, e, CryokinesisHandlers.JAVELIN_DAMAGE);
			if (landed || e instanceof Player) {
				FrostStacks.add(p, e, CryokinesisHandlers.JAVELIN_FROST);
			}
			Vec3 c = result.getLocation();
			server.sendParticles(ICE_DUST, c.x, c.y, c.z, 18, 0.25, 0.25, 0.25, 0.12);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
		} else {
			passed.add(result.getEntity().getId()); // a friend: flies straight through, no hit, no count
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (charge() > 0) {
			return;
		}
		if (result.getType() == HitResult.Type.ENTITY) {
			Entity hit = ((EntityHitResult) result).getEntity();
			if (hit == getOwner() || pierced.contains(hit.getId()) || passed.contains(hit.getId())) {
				return;
			}
			super.onHit(result);
			if (pierced.size() >= CryokinesisHandlers.JAVELIN_PIERCE && level() instanceof ServerLevel server) {
				burst(server, result.getLocation());
			}
			return;
		}
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			burst(server, result.getLocation());
		}
	}

	/** Shatters into a spray of ice (and a few loose chunks). */
	private void burst(ServerLevel server, Vec3 c) {
		server.sendParticles(ICE_DUST, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.15);
		server.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.05);
		for (int i = 0; i < 5; i++) {
			double a = server.random.nextDouble() * Math.PI * 2;
			Vec3 v = new Vec3(Math.cos(a) * 0.3, 0.2 + server.random.nextDouble() * 0.3, Math.sin(a) * 0.3);
			server.addFreshEntity(IceBoltEntity.debris(server, c, v));
		}
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.1f, 0.7f);
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	}
}
