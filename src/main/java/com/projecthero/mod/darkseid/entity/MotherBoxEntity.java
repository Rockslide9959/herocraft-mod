package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidFx;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * One of the four Mother Boxes powering Darkseid's shield. A floating, humming Apokoliptian computer.
 *
 * <p><b>Disrupting it:</b> right-click it to start channelling. The channel fills over
 * {@link DarkseidConfig.MotherBoxes#motherBoxChannelTime} seconds while the channeller stays within
 * {@link DarkseidConfig.MotherBoxes#channelRange} blocks -- taking hits does not interrupt it, walking away does
 * (Darkseid's Grip, which prefers channellers, is the other way to lose it). Progress survives a short
 * interruption and then slowly bleeds away, so a team can hand a box over. Anyone else may right-click to take
 * over. When the channel completes the box goes dark and the raid is told
 * ({@link DarkseidRaid#onMotherBoxDisabled}).
 *
 * <p>Progress is shown three ways: the floating name above the box ("DISRUPTING 60%"), the channeller's action
 * bar, and a cyan tether of particles between them. The overload timer itself belongs to the raid (it is an
 * encounter rule, not a property of the box); the box just shows it ({@link #setOverloadWarning}).
 */
public class MotherBoxEntity extends Entity {
	public static final int STATE_ACTIVE = 0;
	public static final int STATE_DISABLED = 1;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(MotherBoxEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_PROGRESS = SynchedEntityData.defineId(MotherBoxEntity.class, EntityDataSerializers.FLOAT);
	/** 0..1 -- how close the raid's neglect timer is to overloading this box (the renderer pulses red with it). */
	private static final EntityDataAccessor<Float> DATA_WARNING = SynchedEntityData.defineId(MotherBoxEntity.class, EntityDataSerializers.FLOAT);
	/**
	 * v0.13.19: 0..1 -- a disabled box the raid is about to switch back on during the fight (the few seconds of warning
	 * before {@link #reactivate}); the renderer lifts, spins up and brightens it, the box sparks and its name warns.
	 */
	private static final EntityDataAccessor<Float> DATA_WAKING = SynchedEntityData.defineId(MotherBoxEntity.class, EntityDataSerializers.FLOAT);

	private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 1.0f), 1.0f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 1.2f);

	private UUID raidId;
	private int index;
	private UUID channelerId;
	/** Ticks since the channeller last counted (drives the "hold then bleed away" of a broken channel). */
	private int idleTicks;
	private int nameCooldown;

	public MotherBoxEntity(EntityType<? extends MotherBoxEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static MotherBoxEntity spawn(ServerLevel level, Vec3 pos, UUID raidId, int index) {
		MotherBoxEntity box = new MotherBoxEntity(DarkseidEntityTypes.MOTHER_BOX, level);
		box.setPos(pos.x, pos.y, pos.z);
		box.raidId = raidId;
		box.index = index;
		box.refreshName();
		level.addFreshEntity(box);
		level.playSound(null, pos.x, pos.y, pos.z, DarkseidSounds.MOTHER_BOX_ACTIVATE, SoundSource.HOSTILE, 2.5f, 1.0f);
		level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
		return box;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STATE, (byte) STATE_ACTIVE);
		builder.define(DATA_PROGRESS, 0.0f);
		builder.define(DATA_WARNING, 0.0f);
		builder.define(DATA_WAKING, 0.0f);
	}

	// ---------------------------------------------------------------- state

	public boolean isActive() {
		return entityData.get(DATA_STATE) == STATE_ACTIVE;
	}

	public float progress() {
		return entityData.get(DATA_PROGRESS);
	}

	public float overloadWarning() {
		return entityData.get(DATA_WARNING);
	}

	public void setOverloadWarning(float f) {
		float v = Math.max(0.0f, Math.min(1.0f, f));
		if (Math.abs(entityData.get(DATA_WARNING) - v) > 0.02f || (v == 0.0f && entityData.get(DATA_WARNING) != 0.0f)) {
			entityData.set(DATA_WARNING, v);
		}
	}

	/** 0..1 while a disabled box is powering back up (see {@link #setWaking}); 0 otherwise. */
	public float waking() {
		return entityData.get(DATA_WAKING);
	}

	/** The raid's reactivation warning: {@code f} rises 0 -> 1 over the warning, then {@link #reactivate} clears it. */
	public void setWaking(float f) {
		float v = Math.max(0.0f, Math.min(1.0f, f));
		if (entityData.get(DATA_WAKING) != v) {
			entityData.set(DATA_WAKING, v);
			refreshName();
		}
	}

	public int index() {
		return index;
	}

	public UUID raidId() {
		return raidId;
	}

	/** True while a player is actively channelling this box right now. */
	public boolean isBeingChanneled() {
		return channelerId != null && idleTicks == 0;
	}

	public ServerPlayer channeler(ServerLevel level) {
		return channelerId == null ? null : level.getServer().getPlayerList().getPlayer(channelerId);
	}

	/** The box powers back up (soft enrage, or v0.13.19's periodic reactivation during the fight). */
	public void reactivate(ServerLevel level) {
		entityData.set(DATA_STATE, (byte) STATE_ACTIVE);
		entityData.set(DATA_PROGRESS, 0.0f);
		entityData.set(DATA_WAKING, 0.0f);
		entityData.set(DATA_WARNING, 0.0f);
		channelerId = null;
		idleTicks = 0;
		refreshName();
		level.playSound(null, getX(), getY(), getZ(), DarkseidSounds.MOTHER_BOX_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.8f);
		level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
		DarkseidFx.ring(level, CYAN, position().add(0, 0.5, 0), 2.0, 20);
	}

	/** Disable it now, as if a channel had just completed (debug command / tests). */
	public void forceDisable(ServerLevel level) {
		if (isActive()) {
			disable(level, null);
		}
	}

	private void disable(ServerLevel level, ServerPlayer by) {
		entityData.set(DATA_STATE, (byte) STATE_DISABLED);
		entityData.set(DATA_PROGRESS, 1.0f);
		entityData.set(DATA_WARNING, 0.0f);
		channelerId = null;
		refreshName();
		level.playSound(null, getX(), getY(), getZ(), DarkseidSounds.MOTHER_BOX_DISABLE, SoundSource.HOSTILE, 3.0f, 1.0f);
		level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.4f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(CYAN, getX(), getY() + 0.5, getZ(), 60, 1.5, 1.5, 1.5, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 30, 1.0, 1.0, 1.0, 0.4);
		DarkseidRaid raid = raid(level);
		if (raid != null) {
			raid.onMotherBoxDisabled(level, this, by);
		}
	}

	private DarkseidRaid raid(ServerLevel level) {
		return raidId == null ? null : DarkseidRaid.find(level, raidId);
	}

	// ---------------------------------------------------------------- interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!isActive() || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (!(level() instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (player.distanceTo(this) > DarkseidConfig.motherBoxes().channelRange + 1.0) {
			return InteractionResult.PASS;
		}
		if (sp.getUUID().equals(channelerId)) {
			return InteractionResult.SUCCESS;
		}
		channelerId = sp.getUUID();
		idleTicks = 0;
		server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 1.4f);
		sp.displayClientMessage(Component.translatable("message.projecthero.mother_box.channel_start")
				.withStyle(ChatFormatting.AQUA), true);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean skipAttackInteraction(Entity attacker) {
		if (attacker instanceof ServerPlayer sp && isActive()) {
			sp.displayClientMessage(Component.translatable("message.projecthero.mother_box.hint")
					.withStyle(ChatFormatting.AQUA), true);
		}
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount % 40 == 0 && raid(server) == null) {
			discard(); // orphan guard: a box never outlives its raid
			return;
		}
		if (!isActive()) {
			float waking = waking();
			if (waking > 0.0f) {
				// powering back up: sparks and a rising cyan/red pillar, faster as it nears
				if (tickCount % Math.max(1, 4 - (int) (waking * 3)) == 0) {
					server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 3, 0.35, 0.35, 0.35, 0.15);
					server.sendParticles(tickCount % 2 == 0 ? CYAN : RED, getX(), getY() + 0.5 + random.nextDouble() * 3.0 * waking,
							getZ(), 2, 0.15, 0.1, 0.15, 0.0);
				}
			} else if (tickCount % 10 == 0) {
				server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6, getZ(), 1, 0.15, 0.05, 0.15, 0.01);
			}
			return;
		}
		if (tickCount % 60 == 0) {
			server.playSound(null, getX(), getY(), getZ(), DarkseidSounds.MOTHER_BOX_HUM, SoundSource.HOSTILE, 1.2f, 1.0f);
		}
		if (tickCount % 5 == 0) {
			server.sendParticles(overloadWarning() > 0.66f ? RED : CYAN, getX(), getY() + 0.5, getZ(), 2, 0.4, 0.4, 0.4, 0.0);
		}
		tickChannel(server);
		if (--nameCooldown <= 0) {
			nameCooldown = 5;
			refreshName();
		}
	}

	private void tickChannel(ServerLevel server) {
		DarkseidConfig.MotherBoxes cfg = DarkseidConfig.motherBoxes();
		float progress = progress();
		ServerPlayer channeler = channeler(server);
		boolean counting = channeler != null && channeler.isAlive() && !channeler.isSpectator()
				&& channeler.level() == level() && channeler.distanceTo(this) <= cfg.channelRange;
		if (counting) {
			idleTicks = 0;
			progress += 1.0f / Math.max(20.0f, cfg.motherBoxChannelTime * 20.0f);
			if (tickCount % 2 == 0) {
				DarkseidFx.line(server, CYAN, position().add(0, 0.5, 0), channeler.position().add(0, 1.0, 0), 0.7, 10);
			}
			if (tickCount % 5 == 0) {
				channeler.displayClientMessage(Component.translatable("message.projecthero.mother_box.channel",
						bar(progress), Math.round(progress * 100)).withStyle(ChatFormatting.AQUA), true);
			}
			if (progress >= 1.0f) {
				disable(server, channeler);
				return;
			}
		} else {
			if (channelerId != null && channeler != null && idleTicks == 0) {
				channeler.displayClientMessage(Component.translatable("message.projecthero.mother_box.channel_lost")
						.withStyle(ChatFormatting.RED), true);
			}
			if (channeler == null || !channeler.isAlive()) {
				channelerId = null;
			}
			idleTicks++;
			if (idleTicks > 60) {
				progress = Math.max(0.0f, progress - 0.004f);
			}
		}
		entityData.set(DATA_PROGRESS, progress);
	}

	private static String bar(float progress) {
		int filled = Math.round(progress * 10);
		return "▮".repeat(filled) + "▯".repeat(10 - filled);
	}

	private void refreshName() {
		Component name;
		if (!isActive() && waking() > 0.0f) {
			name = Component.translatable("entity.projecthero.mother_box.waking").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
		} else if (!isActive()) {
			name = Component.translatable("entity.projecthero.mother_box.disabled").withStyle(ChatFormatting.DARK_GRAY);
		} else if (progress() > 0.0f) {
			name = Component.translatable("entity.projecthero.mother_box.disrupting", Math.round(progress() * 100))
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
		} else if (overloadWarning() > 0.66f) {
			name = Component.translatable("entity.projecthero.mother_box.overloading").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		} else {
			name = Component.translatable("entity.projecthero.mother_box.active").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
		}
		if (!name.equals(getCustomName())) {
			setCustomName(name);
		}
		setCustomNameVisible(true);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		raidId = tag.hasUUID("Raid") ? tag.getUUID("Raid") : null;
		index = tag.getInt("Index");
		entityData.set(DATA_STATE, tag.getByte("State"));
		entityData.set(DATA_PROGRESS, tag.getFloat("Progress"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (raidId != null) {
			tag.putUUID("Raid", raidId);
		}
		tag.putInt("Index", index);
		tag.putByte("State", entityData.get(DATA_STATE));
		tag.putFloat("Progress", progress());
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}
}
