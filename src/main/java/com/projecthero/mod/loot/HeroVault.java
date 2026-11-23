package com.projecthero.mod.loot;

import java.util.List;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the public Hero Vault API.
 *
 * <ul>
 * <li>{@link #place(LevelAccessor, BlockPos, int, Direction)} -- structure builders drop a vault of tier 1-4 facing a
 * direction (works from a {@code StructurePiece.postProcess} {@code WorldGenLevel} too).</li>
 * <li>{@link #open(ServerLevel, BlockPos, ServerPlayer)} -- what a right-click does: rolls the player's own loot from
 * {@link #lootTable(int)} once, pops it out of the door, remembers them.</li>
 * </ul>
 */
public final class HeroVault {
	public static final int MIN_TIER = 1;
	public static final int MAX_TIER = 4;

	/** What {@link #open} did. */
	public enum OpenResult { OPENED, ALREADY_LOOTED, NOT_A_VAULT }

	/** The outcome plus the stacks that were popped out (empty unless {@link OpenResult#OPENED}). */
	public record Opened(OpenResult result, List<ItemStack> loot) {
	}

	private HeroVault() {
	}

	/** {@code projecthero:chests/vault_tier<N>}. */
	public static ResourceKey<LootTable> lootTable(int tier) {
		return ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/vault_tier" + Mth.clamp(tier, MIN_TIER, MAX_TIER)));
	}

	/** The block state for a vault of this tier and facing (the door faces {@code facing}). */
	public static BlockState state(int tier, Direction facing) {
		Direction f = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
		return LootItems.HERO_VAULT.defaultBlockState()
				.setValue(HeroVaultBlock.TIER, Mth.clamp(tier, MIN_TIER, MAX_TIER))
				.setValue(HeroVaultBlock.FACING, f);
	}

	/**
	 * Places a fresh (nobody-looted) vault. {@code facing} is the side the door looks out of -- towards the room the
	 * player walks in from. Returns true if the block was set.
	 */
	public static boolean place(LevelAccessor level, BlockPos pos, int tier, Direction facing) {
		return level.setBlock(pos, state(tier, facing), 3);
	}

	/** The vault's tier at {@code pos}, or 0 when it isn't a vault. */
	public static int tierAt(LevelAccessor level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.is(LootItems.HERO_VAULT) ? s.getValue(HeroVaultBlock.TIER) : 0;
	}

	/** Trim colour per tier (iron, gold, purple, aqua) -- the textures, the particles and the tier name share it. */
	public static int trimColour(int tier) {
		return switch (tier) {
			case 2 -> 0xF2C14E;
			case 3 -> 0xB35CFF;
			case 4 -> 0x3FE6E0;
			default -> 0xC9D1D9;
		};
	}

	/** {@code block.projecthero.hero_vault.tier<N>} -- "Hero Cache", "Site Vault", "Hideout Vault", "Dungeon Vault". */
	public static Component tierName(int tier) {
		return Component.translatable("block.projecthero.hero_vault.tier" + Mth.clamp(tier, MIN_TIER, MAX_TIER));
	}

	/** Right-click: roll this player's own loot once. Spectators are ignored. */
	public static Opened open(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(LootItems.HERO_VAULT) || !(level.getBlockEntity(pos) instanceof HeroVaultBlockEntity vault) || player.isSpectator()) {
			return new Opened(OpenResult.NOT_A_VAULT, List.of());
		}
		int tier = state.getValue(HeroVaultBlock.TIER);
		if (vault.hasLooted(player.getUUID())) {
			player.displayClientMessage(Component.translatable("block.projecthero.hero_vault.looted").withStyle(ChatFormatting.GRAY), true);
			level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 0.8f, 1.0f);
			return new Opened(OpenResult.ALREADY_LOOTED, List.of());
		}
		Vec3 centre = Vec3.atCenterOf(pos);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable(tier));
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, centre)
				.withParameter(LootContextParams.THIS_ENTITY, player)
				.withLuck(player.getLuck())
				.create(LootContextParamSets.CHEST);
		List<ItemStack> loot = table.getRandomItems(params, level.getRandom().nextLong());
		if (com.projecthero.mod.alarm.AlarmGhost.claim(level, pos, player)) {
			// v0.17.7 Ghost bonus: the place's alarm never went off -- one extra roll
			loot = new java.util.ArrayList<>(loot);
			loot.addAll(table.getRandomItems(params, level.getRandom().nextLong()));
		}
		vault.markLooted(player.getUUID());
		eject(level, pos, state.getValue(HeroVaultBlock.FACING), tier, loot);
		player.displayClientMessage(Component.translatable("block.projecthero.hero_vault.opened", tierName(tier))
				.withStyle(s -> s.withColor(trimColour(tier))), true);
		return new Opened(OpenResult.OPENED, loot);
	}

	private static void eject(ServerLevel level, BlockPos pos, Direction facing, int tier, List<ItemStack> loot) {
		Vec3 door = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.7, 0.1, facing.getStepZ() * 0.7);
		level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 1.0f);
		level.playSound(null, pos, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1.0f, 0.9f + tier * 0.05f);
		if (tier >= 3) {
			level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
		int i = 0;
		for (ItemStack stack : loot) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemEntity item = new ItemEntity(level, door.x, door.y, door.z, stack.copy());
			double spread = (i++ % 5 - 2) * 0.04;
			item.setDeltaMovement(facing.getStepX() * 0.18 + (facing.getStepZ() != 0 ? spread : 0),
					0.22 + level.getRandom().nextDouble() * 0.08,
					facing.getStepZ() * 0.18 + (facing.getStepX() != 0 ? spread : 0));
			item.setPickUpDelay(10);
			level.addFreshEntity(item);
		}
		int rgb = trimColour(tier);
		Vector3f c = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		level.sendParticles(new DustParticleOptions(c, 1.2f), door.x, door.y, door.z, 24, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, door.x, door.y + 0.2, door.z, 6 + tier * 3, 0.25, 0.25, 0.25, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, door.x, door.y, door.z, 8, 0.2, 0.2, 0.2, 0.01);
	}
}
