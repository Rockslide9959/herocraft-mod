package com.projecthero.mod.carnage.entity;

import org.joml.Vector3f;

import com.projecthero.mod.carnage.CarnageEntityTypes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the crimson meteor Carnage arrives in. It streaks down at a slant trailing fire and red smoke (seen from
 * far away -- the particles are forced), hits the ground with a boom that knocks people back (it breaks no blocks), and
 * Carnage climbs out of the impact. Never saved: a meteor caught mid-fall by a world save is simply gone.
 */
public class CrimsonMeteorEntity extends Entity {
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.08f), 2.5f);

	public CrimsonMeteorEntity(EntityType<? extends CrimsonMeteorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	/** Launches a meteor that lands at {@code impact} about three seconds from now. */
	public static CrimsonMeteorEntity launch(ServerLevel level, Vec3 impact) {
		CrimsonMeteorEntity m = CarnageEntityTypes.CRIMSON_METEOR.create(level);
		if (m == null) {
			return null;
		}
		double a = level.random.nextDouble() * Math.PI * 2;
		Vec3 start = impact.add(Math.cos(a) * 40, 70, Math.sin(a) * 40);
		m.moveTo(start.x, start.y, start.z, 0f, 0f);
		m.setDeltaMovement(impact.subtract(start).scale(1.0 / 60.0));
		level.addFreshEntity(m);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0f, 0.4f);
		return m;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level() instanceof ServerLevel server) {
			BlockHitResult hit = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
			if (hit.getType() != HitResult.Type.MISS) {
				impact(server, hit.getLocation());
				return;
			}
			for (ServerLevelViewer viewer : ServerLevelViewer.of(server, from)) {
				viewer.send(server, ParticleTypes.FLAME, from, 8, 0.4);
				viewer.send(server, RED, from, 6, 0.6);
				viewer.send(server, ParticleTypes.LARGE_SMOKE, from, 3, 0.5);
			}
			if (tickCount % 10 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5f, 0.4f);
			}
			if (tickCount > 200) {
				discard();
				return;
			}
		}
		setPos(to.x, to.y, to.z);
	}

	private void impact(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(RED, at.x, at.y + 0.5, at.z, 120, 2.5, 1.0, 2.5, 0.0);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 30, 1.5, 0.5, 1.5, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5))) {
			Vec3 out = e.position().subtract(at);
			Vec3 h = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
			e.hurt(level.damageSources().explosion(this, null), 6.0f);
			e.push(h.x * 1.1, 0.6, h.z * 1.1);
			e.hurtMarked = true;
		}
		CarnageEntity carnage = CarnageEntityTypes.CARNAGE.create(level);
		if (carnage != null) {
			carnage.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
			carnage.finalizeSpawn(level, level.getCurrentDifficultyAt(carnage.blockPosition()), MobSpawnType.EVENT, null);
			level.addFreshEntity(carnage);
			carnage.emerge();
		}
		discard();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
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

	/** Forced (long-range) particles for every player within 192 blocks, so the trail is visible across the sky. */
	private record ServerLevelViewer(net.minecraft.server.level.ServerPlayer player) {
		static java.util.List<ServerLevelViewer> of(ServerLevel level, Vec3 near) {
			java.util.List<ServerLevelViewer> out = new java.util.ArrayList<>();
			for (var p : level.players()) {
				if (p.position().distanceToSqr(near) < 192 * 192) {
					out.add(new ServerLevelViewer(p));
				}
			}
			return out;
		}

		void send(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 at, int count, double spread) {
			level.sendParticles(player, particle, true, at.x, at.y, at.z, count, spread, spread, spread, 0.01);
		}
	}
}
