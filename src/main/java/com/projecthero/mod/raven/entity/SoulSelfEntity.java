package com.projecthero.mod.raven.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven, Shift+G: her <b>soul-self</b> -- one big raven of black and violet energy with white eyes. Revamp: she <b>steers</b>
 * it with her look for {@link RavenConfig#SOUL_STEER_TICKS} (through walls and creatures, never more than
 * {@link RavenConfig#SOUL_LEASH} from her), and it engulfs every creature it touches once: {@link RavenConfig#SOUL_DAMAGE},
 * Blindness and Slowness. Then it flies home to her and dissolves -- unless she presses Shift+G again and steps through into its
 * place ({@code RavenMoves.soulTeleport}). In Demonic Ascension it is half again as big and strong. The client draws it
 * ({@code SoulSelfRenderer}).
 */
public class SoulSelfEntity extends Entity {
	/** Synced: ticks it has been dissolving (0 = flying). */
	private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(SoulSelfEntity.class, EntityDataSerializers.INT);
	/** Synced: the owner's entity id (the client's "is it out" check). */
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SoulSelfEntity.class, EntityDataSerializers.INT);
	/** Synced: the size (1, or 1.5 in Demonic Ascension). */
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SoulSelfEntity.class, EntityDataSerializers.FLOAT);
	/** Synced: flying home. */
	private static final EntityDataAccessor<Boolean> HOMING = SynchedEntityData.defineId(SoulSelfEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID ownerId;
	private final Set<Integer> engulfed = new HashSet<>();
	/** For the tests: creatures engulfed so far. */
	public int hits;

	public SoulSelfEntity(EntityType<? extends SoulSelfEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static SoulSelfEntity release(ServerPlayer owner, boolean ascended) {
		SoulSelfEntity soul = new SoulSelfEntity(RavenEntities.SOUL_SELF, owner.level());
		soul.ownerId = owner.getUUID();
		soul.entityData.set(OWNER, owner.getId());
		soul.entityData.set(SIZE, ascended ? RavenConfig.SOUL_ASCENDED_MULT : 1f);
		Vec3 dir = owner.getViewVector(1f).normalize();
		Vec3 at = owner.getEyePosition().add(dir.scale(0.8)).add(0, -0.75, 0);
		soul.moveTo(at.x, at.y, at.z, owner.getYRot(), owner.getXRot());
		soul.setDeltaMovement(dir.scale(RavenConfig.SOUL_SPEED));
		owner.level().addFreshEntity(soul);
		return soul;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FADE, 0);
		builder.define(OWNER, -1);
		builder.define(SIZE, 1f);
		builder.define(HOMING, false);
	}

	public int fade() {
		return entityData.get(FADE);
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public boolean homing() {
		return entityData.get(HOMING);
	}

	/** She stepped through: it dissolves where it is. */
	public void dissolve() {
		if (fade() == 0) {
			entityData.set(FADE, 1);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			Vec3 c = position().add(0, 0.75, 0);
			float s = size();
			for (int i = 0; i < 2; i++) {
				level().addParticle(ParticleTypes.SQUID_INK, c.x + (random.nextDouble() - 0.5) * 2.4 * s, c.y + (random.nextDouble() - 0.5) * 0.8 * s,
						c.z + (random.nextDouble() - 0.5) * 2.4 * s, 0, 0, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		int maxLife = RavenConfig.SOUL_STEER_TICKS + 80;
		if (owner == null || owner.level() != level || !owner.isAlive() || tickCount > maxLife) {
			discard();
			return;
		}
		int fade = fade();
		if (fade > 0) {
			entityData.set(FADE, fade + 1);
			if (fade > 8) {
				discard();
			}
			return;
		}
		Vec3 step;
		if (tickCount <= RavenConfig.SOUL_STEER_TICKS) {
			// steered: along her look, kept on its leash
			Vec3 dir = owner.getViewVector(1f).normalize();
			step = dir.scale(RavenConfig.SOUL_SPEED * (size() > 1f ? 1.15 : 1.0));
			Vec3 next = position().add(step);
			if (next.distanceTo(owner.position()) > RavenConfig.SOUL_LEASH) {
				Vec3 out = next.subtract(owner.position()).normalize();
				step = step.subtract(out.scale(step.dot(out))); // slide round the edge of the leash
			}
			setYRot(owner.getYRot());
			setXRot(owner.getXRot());
		} else {
			// home to her
			if (!homing()) {
				entityData.set(HOMING, true);
			}
			Vec3 home = owner.position().add(0, 0.6, 0);
			Vec3 to = home.subtract(position());
			if (to.length() < 1.6) {
				dissolve();
				return;
			}
			step = to.normalize().scale(Math.min(to.length(), RavenConfig.SOUL_RETURN_SPEED));
			float yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90f;
			setYRot(yaw);
			setXRot((float) (-Mth.atan2(step.y, step.horizontalDistance()) * Mth.RAD_TO_DEG));
		}
		Vec3 next = position().add(step);
		setPos(next.x, next.y, next.z);
		setDeltaMovement(step);
		Vec3 c = position().add(0, 0.75, 0);
		float s = size();
		double reach = RavenConfig.SOUL_RADIUS * s - 1.0;
		float damage = RavenConfig.SOUL_DAMAGE * (s > 1f ? RavenConfig.SOUL_ASCENDED_MULT / com.projecthero.mod.raven.RavenConfig.ASCEND_POWER : 1f);
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
				e -> e.isAlive() && e != owner && !engulfed.contains(e.getId()) && HeroTargets.canHarm(owner, e))) {
			engulfed.add(t.getId());
			if (Raven.hit(owner, t, damage)) {
				hits++;
				t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RavenConfig.SOUL_EFFECT_TICKS, 0), owner);
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RavenConfig.SOUL_EFFECT_TICKS, 2), owner);
				Vec3 tc = t.position().add(0, t.getBbHeight() * 0.5, 0);
				level.sendParticles(Raven.dust(Raven.VOID, 2.0f), tc.x, tc.y, tc.z, 30, t.getBbWidth() * 0.6, t.getBbHeight() * 0.5,
						t.getBbWidth() * 0.6, 0.0);
				level.sendParticles(ParticleTypes.SCULK_SOUL, tc.x, tc.y, tc.z, 4, 0.3, 0.4, 0.3, 0.02);
				level.playSound(null, tc.x, tc.y, tc.z, RavenSounds.SOUL_HIT, SoundSource.PLAYERS, 0.6f, 0.7f);
			}
		}
		if (tickCount % 2 == 0) {
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.3f), c.x, c.y, c.z, 3, 0.9 * s, 0.3 * s, 0.9 * s, 0.0);
		}
		if (tickCount % 16 == 0) {
			level.playSound(null, c.x, c.y, c.z, RavenSounds.FLIGHT, SoundSource.PLAYERS, 0.3f, 0.5f);
		}
	}

	/** Wing beat phase for the renderer (radians). */
	public float wingPhase(float partial) {
		return (tickCount + partial) * 0.55f;
	}

	public float bodyYaw() {
		return getYRot();
	}

	public float bodyPitch() {
		return Mth.clamp(getXRot(), -60f, 60f);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	/** For the tests. */
	public Entity ownerEntity() {
		return ownerId == null || !(level() instanceof ServerLevel sl) ? null : sl.getServer().getPlayerList().getPlayer(ownerId);
	}
}
