package com.projecthero.mod.skyfortress.entity;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * v0.17.7: a <b>prisoner</b> in the Sky Fortress cell block -- a downed pilot or a kidnapped engineer (player model,
 * detailed skin; harmless). Open the cell and right-click them to free them: they thank you, press a reward into your
 * hands ({@code chests/sky_prisoner_reward}) and bail out of the fortress with a parachute a moment later. Leave them
 * and nothing happens. Persistent; never despawns.
 */
public class SkyPrisoner extends PathfinderMob {
	public static final ResourceKey<LootTable> REWARD = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/sky_prisoner_reward"));
	public static final int BAIL_TICKS = 40;
	private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(SkyPrisoner.class, EntityDataSerializers.BYTE);
	private static final ResourceLocation[] TEX = { ProjectHeroMod.id("textures/entity/sky_fortress/prisoner_pilot.png"),
			ProjectHeroMod.id("textures/entity/sky_fortress/prisoner_engineer.png") };

	private boolean freed;
	private int bail = -1;

	public SkyPrisoner(EntityType<? extends SkyPrisoner> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.22);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, (byte) 0);
	}

	public int variant() {
		return entityData.get(DATA_VARIANT) & 1;
	}

	public void setVariant(int v) {
		entityData.set(DATA_VARIANT, (byte) (v & 1));
	}

	public ResourceLocation texture() {
		return TEX[variant()];
	}

	public boolean isFreed() {
		return freed;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (freed) {
			return InteractionResult.PASS;
		}
		if (level() instanceof ServerLevel server) {
			free(server, player);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	/** Frees this prisoner: thanks, the reward to {@code player}, and a parachute bail-out shortly after. Returns the reward. */
	public List<ItemStack> free(ServerLevel level, Player player) {
		if (freed) {
			return List.of();
		}
		freed = true;
		bail = BAIL_TICKS;
		getNavigation().stop();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(REWARD);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position())
				.withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
		List<ItemStack> loot = table.getRandomItems(params);
		for (ItemStack s : loot) {
			ItemStack copy = s.copy();
			if (!player.getInventory().add(copy)) {
				player.drop(copy, false);
			}
		}
		player.displayClientMessage(Component.translatable("entity.projecthero.sky_prisoner.thanks." + variant()).withStyle(ChatFormatting.GREEN), false);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 12, 0.4, 0.5, 0.4, 0.0);
		return loot;
	}

	@Override
	public void tick() {
		super.tick();
		if (bail > 0 && level() instanceof ServerLevel server && --bail == 0) {
			server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 24, 0.4, 0.6, 0.4, 0.06);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.NEUTRAL, 1.0f, 1.0f);
			discard();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("Variant", (byte) variant());
		tag.putBoolean("Freed", freed);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setVariant(tag.getByte("Variant"));
		freed = tag.getBoolean("Freed");
		if (freed) {
			bail = 2;
		}
	}
}
