package com.projecthero.mod.nova.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.item.NovaItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.13: the dying Nova Corps Centurion who lies in a Crashed Nova Corps Pod. Not hostile, never moves (he sits
 * slumped against his pod and only turns his head to whoever comes near), cannot be hurt (only {@code /kill} removes him)
 * and never despawns. He mutters a short line to anyone within {@link NovaConfig#CENTURION_TALK_RANGE} blocks, at most
 * once every {@link NovaConfig#CENTURION_TALK_GAP} ticks per player. Right-clicking him hands over the Nova Corps Helmet;
 * then he fades away in golden light over {@link NovaConfig#CENTURION_FADE_TICKS} ticks. Someone who already carries the
 * Nova Force gets a line instead, and he stays for the next one.
 */
public class NovaCenturionEntity extends PathfinderMob {
	/** Ticks of fading left (0 = not fading). Synced: the renderer fades him out. */
	private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(NovaCenturionEntity.class, EntityDataSerializers.INT);

	private static final String[] IDLE_LINES = { "idle1", "idle2", "idle3", "idle4" };

	private final Map<UUID, Long> lastTalk = new HashMap<>();
	private int idleLine;

	public NovaCenturionEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(FADE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0f, 1.0f));
	}

	/** 1 -> 0 as he fades away; 1 when not fading. Client-safe. */
	public float presence(float partialTick) {
		int fade = this.entityData.get(FADE);
		if (fade <= 0) {
			return 1.0f;
		}
		return Math.max(0f, (fade - partialTick) / NovaConfig.CENTURION_FADE_TICKS);
	}

	public boolean fading() {
		return this.entityData.get(FADE) > 0;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(net.minecraft.world.entity.Entity entity) {
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		return false;
	}

	@Override
	public boolean isInvulnerable() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		int fade = this.entityData.get(FADE);
		if (fade > 0) {
			fade--;
			this.entityData.set(FADE, fade);
			Vec3 c = this.position().add(0, 0.6, 0);
			level.sendParticles(Nova.GOLD, c.x, c.y, c.z, 4, 0.35, 0.45, 0.35, 0.0);
			if (fade % 3 == 0) {
				level.sendParticles(Nova.CYAN, c.x, c.y + 0.4, c.z, 1, 0.2, 0.2, 0.2, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.3, 0.4, 0.3, 0.03);
			}
			if (fade == 0) {
				level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(Nova.GOLD_BIG, c.x, c.y, c.z, 40, 0.4, 0.6, 0.4, 0.0);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0f, 1.6f);
				this.discard();
			}
			return;
		}
		if (this.tickCount % 20 == 0) {
			talkToNearby(level);
		}
		if (this.tickCount % 40 == 0) {
			// a wisp of smoke and a flicker of the Nova Force leaving him
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.9, this.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			level.sendParticles(Nova.GOLD, this.getX(), this.getY() + 0.9, this.getZ(), 1, 0.25, 0.3, 0.25, 0.0);
		}
	}

	private void talkToNearby(ServerLevel level) {
		long now = level.getGameTime();
		double r = NovaConfig.CENTURION_TALK_RANGE;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(this) > r * r) {
				continue;
			}
			Long last = lastTalk.get(p.getUUID());
			if (last != null && now - last < NovaConfig.CENTURION_TALK_GAP && now >= last) {
				continue;
			}
			lastTalk.put(p.getUUID(), now);
			String line = IDLE_LINES[idleLine++ % IDLE_LINES.length];
			say(p, "entity.projecthero.nova_centurion.say." + line);
		}
	}

	private void say(Player p, String key) {
		p.sendSystemMessage(Component.translatable("entity.projecthero.nova_centurion.speaker").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (fading()) {
			return InteractionResult.CONSUME;
		}
		if (Nova.hasPower(player)) {
			say(player, "entity.projecthero.nova_centurion.say.already");
			return InteractionResult.CONSUME;
		}
		handOver(player);
		return InteractionResult.CONSUME;
	}

	/** Gives {@code player} the Nova Corps Helmet and starts the fade. */
	public void handOver(Player player) {
		ItemStack helmet = new ItemStack(NovaItems.NOVA_CORPS_HELMET);
		if (!player.getInventory().add(helmet)) {
			player.drop(helmet, false);
		}
		say(player, "entity.projecthero.nova_centurion.say.handoff");
		player.displayClientMessage(Component.translatable("message.projecthero.nova_helmet.received").withStyle(ChatFormatting.GOLD), true);
		this.entityData.set(FADE, NovaConfig.CENTURION_FADE_TICKS);
		if (this.level() instanceof ServerLevel level) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5f, 0.6f);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0f, 1.2f);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("NovaFade", this.entityData.get(FADE));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(FADE, tag.getInt("NovaFade"));
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return null;
	}
}
