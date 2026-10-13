package com.projecthero.mod.galactus.entity;

import java.util.UUID;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: one of Galactus' four <b>weak points</b> -- the chest emblem, the helmet crest, the left and right gauntlets --
 * a separate damageable part entity that he moves onto his body every tick. Its health is the event's (saved there,
 * {@link #KIND_EMBLEM}..{@link #KIND_RIGHT}); every hit also comes off Galactus himself
 * ({@link com.projecthero.mod.galactus.GalactusConfig.WeakPoints#passThrough}) and goes on his threat table. Sealed
 * (blue sparks, no damage) while any Power Pylon stands. Never saved: Galactus re-creates the unbroken ones on load.
 */
public class GalactusWeakPointEntity extends GalactusMob {
	public static final int KIND_EMBLEM = 0, KIND_CREST = 1, KIND_LEFT = 2, KIND_RIGHT = 3;
	public static final int KINDS = 4;
	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(GalactusWeakPointEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SEALED = SynchedEntityData.defineId(GalactusWeakPointEntity.class,
			EntityDataSerializers.BOOLEAN);

	private UUID ownerId;

	public GalactusWeakPointEntity(EntityType<? extends GalactusWeakPointEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1000.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, 0);
		builder.define(DATA_SEALED, true);
	}

	@Override
	public String model() {
		return null;
	}

	public int kind() {
		return entityData.get(DATA_KIND);
	}

	public boolean sealed() {
		return entityData.get(DATA_SEALED);
	}

	public void setSealed(boolean sealed) {
		if (sealed != sealed()) {
			entityData.set(DATA_SEALED, sealed);
		}
	}

	public void configure(UUID owner, int kind, double maxHealth, float health) {
		this.ownerId = owner;
		entityData.set(DATA_KIND, kind);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		setHealth(Math.max(1f, health));
	}

	public GalactusEntity owner() {
		return ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof GalactusEntity g ? g : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (tickCount > 5 && owner() == null) {
				discard();
				return;
			}
			if (tickCount % 4 == 0) {
				var dust = sealed() ? GalactusFx.BLUE : GalactusFx.EMBER;
				GalactusFx.forced(server, dust, getX(), getY() + getBbHeight() * 0.5, getZ(), 3, getBbWidth() * 0.35, getBbHeight() * 0.35,
						getBbWidth() * 0.35, 0.0);
			}
		}
	}

	@Override
	public void aiStep() {
		setDeltaMovement(Vec3.ZERO);
		// no AI, no physics: Galactus places it
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!(level() instanceof ServerLevel server) || isRemoved() || isDeadOrDying()) {
			return false;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (source.getEntity() instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		GalactusEntity g = owner();
		if (g == null) {
			return false;
		}
		if (sealed()) {
			server.sendParticles(GalactusFx.BLUE, getX(), getY() + getBbHeight() * 0.5, getZ(), 10, 0.6, 0.6, 0.6, 0.05);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 0.5f);
			if (source.getEntity() instanceof Player p) {
				p.displayClientMessage(Component.translatable("message.projecthero.galactus.weak_point_sealed").withStyle(ChatFormatting.AQUA), true);
			}
			return false;
		}
		float before = getHealth();
		invulnerableTime = 0;
		boolean hurt = super.hurt(source, amount);
		float dealt = Math.max(0f, before - Math.max(0f, getHealth()));
		if (hurt && dealt > 0f) {
			g.onWeakPointHit(server, this, source, dealt);
			server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.5, 0.5, 0.5, 0.2);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			GalactusEntity g = owner();
			if (g != null) {
				g.onWeakPointBroken(server, this);
			}
			discard();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}
}
