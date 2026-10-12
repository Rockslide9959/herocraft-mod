package com.projecthero.mod.frostgiant.entity;

import java.util.UUID;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAssault;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: the <b>Casket of Ancient Winters</b> in phase 3 -- floating and pulsing above King Laufey, with a hit-box and
 * a health bar of its own ({@link FrostGiantConfig#casketHealthFor}). <b>It can only be damaged while Laufey is staggered</b>
 * (or kneeling at 1 health, refusing to fall). When it breaks, Ymir is torn back through the rift, Laufey is drained and
 * the blizzard ends ({@link FrostGiantAssault#onCasketBroken}).
 */
public class IceCasketEntity extends Monster implements GeoEntity {
	private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(IceCasketEntity.class, EntityDataSerializers.BOOLEAN);
	public static final double FLOAT_ABOVE = 3.0;

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.ice_casket"),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_6);
	private UUID laufeyId;
	private UUID assaultId;
	private int lastNag;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	public IceCasketEntity(EntityType<? extends IceCasketEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		setPersistenceRequired();
		this.xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, FrostGiantConfig.ymir().casketHealth)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.ARMOR, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OPEN, false);
	}

	@Override
	protected void registerGoals() {
	}

	public void configure(UUID laufey, UUID assault, double health) {
		this.laufeyId = laufey;
		this.assaultId = assault;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	/** Breakable right now? (Laufey staggered or kneeling at 1 health.) Synced for the pulse on the client. */
	public boolean isVulnerable() {
		return entityData.get(DATA_OPEN);
	}

	public KingLaufeyEntity laufey(ServerLevel server) {
		return laufeyId != null && server.getEntity(laufeyId) instanceof KingLaufeyEntity l ? l : null;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(Vec3.ZERO);
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		KingLaufeyEntity laufey = laufey(server);
		if (laufey == null || !laufey.isAlive()) {
			if (tickCount > 40) {
				discard();
			}
			return;
		}
		Vec3 at = laufey.position().add(0, laufey.getBbHeight() + FLOAT_ABOVE + Mth.sin(tickCount * 0.08f) * 0.5, 0);
		setPos(at.x, at.y, at.z);
		boolean open = laufey.exposesCasket();
		if (open != isVulnerable()) {
			entityData.set(DATA_OPEN, open);
			bossBar.setColor(open ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.BLUE);
		}
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % (open ? 3 : 8) == 0) {
			server.sendParticles(open ? FrostFx.WHITE : FrostFx.FROST, getX(), getY() + getBbHeight() * 0.5, getZ(), open ? 4 : 2,
					getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.01);
		}
		if (tickCount % 40 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5f, 0.5f);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isInvulnerableTo(source)) {
			return false;
		}
		if (!isVulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getEntity() instanceof Player p && tickCount - lastNag > 30) {
				lastNag = tickCount;
				p.displayClientMessage(Component.translatable("message.projecthero.frost_giants.casket_shielded").withStyle(ChatFormatting.AQUA), true);
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.6f);
			}
			return false;
		}
		invulnerableTime = 0;
		return super.hurt(source, amount);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker != null && FrostCombat.isFrost(attacker)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bossBar.removeAllPlayers();
		if (level() instanceof ServerLevel server) {
			FrostFx.shatter(server, position().add(0, 1, 0), 3.0);
			FrostFx.forced(server, ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0f, 1.6f);
			KingLaufeyEntity laufey = laufey(server);
			if (laufey != null) {
				laufey.onCasketBroken(server);
			}
			FrostGiantAssault a = assaultId == null ? null : FrostGiantAssault.find(server, assaultId);
			if (a != null) {
				a.onCasketBroken(server);
			}
		}
	}

	@Override
	protected void tickDeath() {
		if (++deathTime >= 2 && !level().isClientSide()) {
			remove(Entity.RemovalReason.KILLED);
		}
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

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GLASS_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 0,
				state -> state.setAndContinue(FrostGiantEntity.raw(com.projecthero.mod.frostgiant.FrostGiantAnims.CASKET_IDLE))));
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (laufeyId != null) {
			tag.putUUID("Laufey", laufeyId);
		}
		if (assaultId != null) {
			tag.putUUID("FrostAssault", assaultId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		laufeyId = tag.hasUUID("Laufey") ? tag.getUUID("Laufey") : null;
		assaultId = tag.hasUUID("FrostAssault") ? tag.getUUID("FrostAssault") : null;
	}
}
