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
 * v0.17.3 Raven, Shift+G: her <b>soul-self</b> -- one big raven of black and violet energy with white eyes (not the Dark Shadow quirk's
 * clawed shadow). It flies out along her look for {@link RavenConfig#SOUL_RANGE} blocks through walls and creatures, and engulfs
 * every creature it passes once: {@link RavenConfig#SOUL_DAMAGE} damage, Blindness and Slowness. Then it dissolves back to her. The
 * client draws it ({@code SoulSelfRenderer}: the raven model, wings beating, the eyes full-bright).
 */
public class SoulSelfEntity extends Entity {
	/** Synced: ticks it has been dissolving (0 = flying). */
	private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(SoulSelfEntity.class, EntityDataSerializers.INT);

	private UUID ownerId;
	private Vec3 dir = new Vec3(0, 0, 1);
	private double travelled;
	private final Set<Integer> engulfed = new HashSet<>();
	/** For the tests: creatures engulfed so far. */
	public int hits;

	public SoulSelfEntity(EntityType<? extends SoulSelfEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static SoulSelfEntity release(ServerPlayer owner) {
		SoulSelfEntity soul = new SoulSelfEntity(RavenEntities.SOUL_SELF, owner.level());
		soul.ownerId = owner.getUUID();
		soul.dir = owner.getViewVector(1f).normalize();
		Vec3 at = owner.getEyePosition().add(soul.dir.scale(0.8)).add(0, -0.75, 0);
		soul.moveTo(at.x, at.y, at.z, owner.getYRot(), owner.getXRot());
		soul.setDeltaMovement(soul.dir.scale(RavenConfig.SOUL_SPEED));
		owner.level().addFreshEntity(soul);
		return soul;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FADE, 0);
	}

	public int fade() {
		return entityData.get(FADE);
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
			for (int i = 0; i < 2; i++) {
				level().addParticle(ParticleTypes.SQUID_INK, c.x + (random.nextDouble() - 0.5) * 2.4, c.y + (random.nextDouble() - 0.5) * 0.8,
						c.z + (random.nextDouble() - 0.5) * 2.4, 0, 0, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || owner.level() != level || tickCount > 80) {
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
		Vec3 step = dir.scale(RavenConfig.SOUL_SPEED);
		Vec3 next = position().add(step);
		setPos(next.x, next.y, next.z);
		setDeltaMovement(step);
		travelled += RavenConfig.SOUL_SPEED;
		Vec3 c = position().add(0, 0.75, 0);
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RavenConfig.SOUL_RADIUS - 1.0),
				e -> e.isAlive() && e != owner && !engulfed.contains(e.getId()) && HeroTargets.canHarm(owner, e))) {
			engulfed.add(t.getId());
			if (Raven.hit(owner, t, RavenConfig.SOUL_DAMAGE)) {
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
			level.sendParticles(Raven.dust(Raven.VIOLET, 1.3f), c.x, c.y, c.z, 3, 0.9, 0.3, 0.9, 0.0);
		}
		if (travelled >= RavenConfig.SOUL_RANGE) {
			entityData.set(FADE, 1);
			setDeltaMovement(Vec3.ZERO);
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
