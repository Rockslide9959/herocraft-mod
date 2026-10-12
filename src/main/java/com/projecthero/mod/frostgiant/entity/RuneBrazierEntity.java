package com.projecthero.mod.frostgiant.entity;

import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.16.1: a <b>Rune Brazier</b> -- one of the four fires that keep the fighters alive in the blizzard. A 1 x 2 stone
 * fire-bowl carved with runes; no brain, never moves.
 * <ul>
 *   <li>Lit, it warms everyone within {@link FrostGiantConfig.Braziers#warmRadius} (the assault thaws them and gives
 *       Regeneration I).</li>
 *   <li>Only Jotunheim can hurt it: the giants stamp it out (fighters' blows pass through). At 0 health it does not
 *       break -- it goes <b>out</b> (cold smoke) and the giants leave it alone.</li>
 *   <li>Any fighter can <b>relight</b> it: hold right-click on it for 3 s, or use flint and steel / a fire charge, or a
 *       fire power (Pyrokinesis), for an instant relight. It comes back at half health.</li>
 * </ul>
 */
public class RuneBrazierEntity extends Mob {
	private static final EntityDataAccessor<Boolean> DATA_LIT = SynchedEntityData.defineId(RuneBrazierEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_RELIGHT = SynchedEntityData.defineId(RuneBrazierEntity.class, EntityDataSerializers.FLOAT);

	private UUID assaultId;
	/** Per player: relight progress (ticks) and the last game tick they held use on it. Transient. */
	private final Map<UUID, long[]> relighting = new HashMap<>();

	public RuneBrazierEntity(EntityType<? extends RuneBrazierEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, FrostGiantConfig.braziers().health)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LIT, true);
		builder.define(DATA_RELIGHT, 0f);
	}

	@Override
	protected void registerGoals() {
	}

	public void bindToAssault(UUID id) {
		this.assaultId = id;
	}

	/** Sets its health for {@code fighters} fighters (full, lit). */
	public void configure(double health) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
		entityData.set(DATA_LIT, true);
	}

	public boolean isLit() {
		return entityData.get(DATA_LIT);
	}

	/** 0..1 while someone is holding use on it (for the HUD). */
	public float relightProgress() {
		return entityData.get(DATA_RELIGHT);
	}

	/** Is {@code e} inside this brazier's warmth? */
	public boolean warms(Entity e) {
		if (!isLit()) {
			return false;
		}
		double r = FrostGiantConfig.braziers().warmRadius;
		double dx = e.getX() - getX();
		double dz = e.getZ() - getZ();
		double dy = e.getY() - getY();
		return dx * dx + dz * dz <= r * r && Math.abs(dy) <= 5.0;
	}

	/** The fire dies: cold smoke, no more warmth, and the giants lose interest. */
	public void goOut(ServerLevel server) {
		if (!isLit()) {
			return;
		}
		entityData.set(DATA_LIT, false);
		setHealth(1.0f);
		server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5f, 0.6f);
		server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 20, 0.3, 0.4, 0.3, 0.03);
		FrostGiantAssault a = assault(server);
		if (a != null) {
			a.onBrazierOut(server, this);
		}
	}

	/** Back on, at {@link FrostGiantConfig.Braziers#relightHealth} of its health. */
	public void relight(ServerLevel server, Player by) {
		if (isLit()) {
			return;
		}
		entityData.set(DATA_LIT, true);
		entityData.set(DATA_RELIGHT, 0f);
		relighting.clear();
		setHealth((float) (getMaxHealth() * FrostGiantConfig.braziers().relightHealth));
		server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.8f);
		server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.6, getZ(), 30, 0.3, 0.3, 0.3, 0.05);
		FrostGiantAssault a = assault(server);
		if (a != null) {
			a.onBrazierRelit(server, this, by);
		}
	}

	/** Loses {@code fraction} of its max health (the Casket opening); goes out at 0. */
	public void drain(ServerLevel server, double fraction) {
		if (!isLit()) {
			return;
		}
		float left = getHealth() - (float) (getMaxHealth() * fraction);
		if (left <= 1.0f) {
			goOut(server);
		} else {
			setHealth(left);
		}
	}

	private FrostGiantAssault assault(ServerLevel server) {
		return assaultId == null ? null : FrostGiantAssault.find(server, assaultId);
	}

	// ---------------------------------------------------------------- relighting

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (isLit()) {
			return InteractionResult.PASS;
		}
		if (!(level() instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.FLINT_AND_STEEL)) {
			held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			relight(server, player);
			return InteractionResult.CONSUME;
		}
		if (held.is(Items.FIRE_CHARGE)) {
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			relight(server, player);
			return InteractionResult.CONSUME;
		}
		if (FrostCombat.hasActivePower(player, FrostCombat.PYROKINESIS)) {
			relight(server, player);
			return InteractionResult.CONSUME;
		}
		long now = server.getGameTime();
		long[] st = relighting.computeIfAbsent(player.getUUID(), u -> new long[] { 0, now });
		long gap = now - st[1];
		if (gap > 10) {
			st[0] = 0; // let go: start over
		} else {
			st[0] += Math.max(1, Math.min(6, gap));
		}
		st[1] = now;
		int need = Math.max(1, FrostGiantConfig.braziers().relightTicks);
		float p = Math.min(1f, st[0] / (float) need);
		entityData.set(DATA_RELIGHT, p);
		player.displayClientMessage(Component.translatable("message.projecthero.frost_giants.relighting", Math.round(p * 100))
				.withStyle(ChatFormatting.GOLD), true);
		if (now % 4 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.5, getZ(), 3, 0.2, 0.1, 0.2, 0.01);
		}
		if (st[0] >= need) {
			relight(server, player);
		}
		return InteractionResult.CONSUME;
	}

	/** Gametest hook: relight progress for {@code player} as if they had held use for {@code ticks}. */
	public void addRelightProgress(ServerLevel server, Player player, int ticks) {
		long now = server.getGameTime();
		long[] st = relighting.computeIfAbsent(player.getUUID(), u -> new long[] { 0, now });
		st[0] += ticks;
		st[1] = now;
		if (st[0] >= FrostGiantConfig.braziers().relightTicks) {
			relight(server, player);
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount % 40 == 0 && assaultId != null && FrostGiantAssault.find(server, assaultId) == null) {
			discard();
			return;
		}
		if (isLit()) {
			if (tickCount % 4 == 0) {
				server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.55, getZ(), 2, 0.18, 0.1, 0.18, 0.01);
			}
			if (tickCount % 10 == 0) {
				server.sendParticles(FrostFx.EMBER, getX(), getY() + 1.8, getZ(), 2, 0.25, 0.3, 0.25, 0.02);
				server.sendParticles(FrostFx.RUNE, getX(), getY() + 0.6, getZ(), 1, 0.35, 0.3, 0.35, 0.0);
			}
			if (tickCount % 60 == 0) {
				server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 0.8f);
			}
		} else {
			if (tickCount % 6 == 0) {
				server.sendParticles(ParticleTypes.WHITE_SMOKE, getX(), getY() + 1.5, getZ(), 2, 0.15, 0.2, 0.15, 0.01);
			}
			if (tickCount % 20 == 0 && relightProgress() > 0f) {
				long now = server.getGameTime();
				boolean anyone = relighting.values().stream().anyMatch(st -> now - st[1] <= 10);
				if (!anyone) {
					relighting.clear();
					entityData.set(DATA_RELIGHT, 0f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- damage: only Jotunheim can hurt it

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || !isLit() || isInvulnerableTo(source)) {
			return false;
		}
		if (getHealth() - amount <= 1.0f && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (level() instanceof ServerLevel server) {
				goOut(server);
			}
			return true;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.4, getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		Entity attacker = source.getEntity();
		boolean frost = FrostCombat.isFrost(attacker)
				|| source.getDirectEntity() instanceof Projectile proj && FrostCombat.isFrost(proj.getOwner())
				|| source.is(DamageTypeTags.IS_FREEZING);
		return !frost;
	}

	@Override
	public void die(DamageSource source) {
		// a brazier never dies in the fight -- only /kill and the cleanup remove it
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			super.die(source);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.STONE_BREAK;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Lit", isLit());
		if (assaultId != null) {
			tag.putUUID("FrostAssault", assaultId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_LIT, !tag.contains("Lit") || tag.getBoolean("Lit"));
		assaultId = tag.hasUUID("FrostAssault") ? tag.getUUID("FrostAssault") : null;
	}

	/** The warmth radius, for the HUD and tests. */
	public static double warmRadius() {
		return FrostGiantConfig.braziers().warmRadius;
	}

	/** Gametest hook: is {@code e} (a living thing) warmed by this brazier? */
	public boolean warmsLiving(LivingEntity e) {
		return warms(e);
	}
}
