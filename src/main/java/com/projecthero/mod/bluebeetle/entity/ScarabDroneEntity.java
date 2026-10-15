package com.projecthero.mod.bluebeetle.entity;

import java.util.UUID;

import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.BlueBeetleCombat;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5: one of Blue Beetle's Scarab Swarm drones -- a little energy scarab that flies at its owner's target (or the
 * nearest threat) and darts in to bite for {@link BlueBeetleConfig#SWARM_DAMAGE} every half-second, for 10 s. No physics,
 * no saving, can't be hit; it flies straight back into the armour when its time is up or its owner suits down.
 */
public class ScarabDroneEntity extends Entity {
	private UUID owner;
	private int targetId = -1;
	private int life;
	private int biteCooldown;
	private float orbit;

	public ScarabDroneEntity(EntityType<? extends ScarabDroneEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	public void setup(ServerPlayer owner, LivingEntity target, int life) {
		this.owner = owner.getUUID();
		this.targetId = target == null ? -1 : target.getId();
		this.life = life;
		this.orbit = owner.getRandom().nextFloat() * 6.28f;
	}

	/** Sends every drone of this owner home (suit-down, a new swarm, death). */
	public static void recallAll(ServerPlayer owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		for (ScarabDroneEntity d : level.getEntitiesOfClass(ScarabDroneEntity.class, owner.getBoundingBox().inflate(96),
				d -> owner.getUUID().equals(d.owner))) {
			d.discard();
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return; // the client follows the synced position
		}
		ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (p == null || !BlueBeetle.suited(p) || --life <= 0) {
			discard();
			return;
		}
		LivingEntity target = level.getEntity(targetId) instanceof LivingEntity le && le.isAlive() && BlueBeetleCombat.isTarget(p, le) ? le : null;
		if (target == null && tickCount % 10 == 0) {
			target = pick(level, p);
			targetId = target == null ? -1 : target.getId();
		}
		Vec3 goal;
		orbit += 0.35f;
		if (target != null) {
			double r = target.getBbWidth() * 0.5 + 0.7;
			goal = target.position().add(Math.cos(orbit) * r, target.getBbHeight() * 0.6 + Math.sin(orbit * 1.7) * 0.3, Math.sin(orbit) * r);
			if (biteCooldown > 0) {
				biteCooldown--;
			} else if (distanceToSqr(target.position().add(0, target.getBbHeight() * 0.5, 0)) < 2.6) {
				BlueBeetleCombat.strike(p, target, position(), BlueBeetleConfig.SWARM_DAMAGE, 0.1, 0.0);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.6f, 1.6f);
				biteCooldown = 10;
				goal = target.position().add(0, target.getBbHeight() * 0.5, 0);
			}
		} else {
			// no prey: circle above the owner
			goal = p.position().add(Math.cos(orbit) * 1.2, 2.3, Math.sin(orbit) * 1.2);
		}
		Vec3 to = goal.subtract(position());
		double d = to.length();
		Vec3 v = d < 1.0e-3 ? Vec3.ZERO : to.scale(Math.min(0.7, d) / d);
		setDeltaMovement(v);
		setPos(position().add(v));
		if (v.lengthSqr() > 1.0e-4) {
			setYRot((float) (Math.toDegrees(Math.atan2(-v.x, v.z))));
		}
		if (tickCount % 2 == 0) {
			level.sendParticles(BlueBeetle.BLUE, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private LivingEntity pick(ServerLevel level, ServerPlayer p) {
		LivingEntity best = null;
		double bd = 16 * 16;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p.position(), p.position()).inflate(16),
				e -> e.isAlive() && BlueBeetleCombat.isHostile(p, e))) {
			double dd = e.distanceToSqr(p);
			if (dd < bd) {
				bd = dd;
				best = e;
			}
		}
		return best;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	/** Unused by the server; lets {@link Entity#push} etc. stay inert. */
	@Override
	public void push(Entity entity) {
	}
}
