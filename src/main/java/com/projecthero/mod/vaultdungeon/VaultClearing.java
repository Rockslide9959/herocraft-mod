package com.projecthero.mod.vaultdungeon;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.block.VaultSealBlock;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5: clearing The Vault, and Warden Kross's Lockdown.
 * <ul>
 * <li>Kross dies -> {@link #bossDefeated}: the dungeon is marked cleared for good ({@link VaultDungeonState}), every
 * sealed Dungeon Vault near his arena opens into a Tier 4 Hero Vault (per-player loot), title + chat to everyone
 * within 64.</li>
 * <li>Someone uses a seal while Kross is gone without dying (Peaceful, a command) -> {@link #pokeSeal} opens them
 * anyway, so the dungeon can never get stuck.</li>
 * <li>{@link #seal} / {@link #lift}: the Lockdown -- shutters over every doorway marked by a lockdown track near the
 * arena, and their removal.</li>
 * </ul>
 * Static methods only, no state.
 */
public final class VaultClearing {
	/** How far from the arena anchor sealed vaults are looked for (horizontal). */
	public static final int VAULT_SEARCH = 24;
	/** How far from the anchor lockdown tracks are looked for (horizontal). */
	public static final int TRACK_SEARCH = 20;
	/** How far from a seal Kross is looked for before it gives up on him. */
	public static final double GUARDIAN_SEARCH = 96.0;

	private VaultClearing() {
	}

	/** Warden Kross, whose arena centre is {@code anchor}, has died. */
	public static void bossDefeated(ServerLevel level, BlockPos anchor) {
		boolean first = VaultDungeonState.get(level).markCleared(anchor, level.getGameTime());
		int opened = unsealVaults(level, anchor);
		if (!first) {
			return;
		}
		level.playSound(null, anchor, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);
		Component title = Component.translatable("projecthero.vault.cleared.title").withStyle(s -> s.withColor(0x5FE8E8).withBold(true));
		Component sub = Component.translatable("projecthero.vault.cleared.sub").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : playersNear(level, anchor, 64)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			p.displayClientMessage(Component.translatable("projecthero.vault.cleared.chat").withStyle(s -> s.withColor(0x5FE8E8)), false);
			if (opened > 0) {
				p.displayClientMessage(Component.translatable("projecthero.vault.cleared.vaults", opened).withStyle(ChatFormatting.AQUA), false);
			}
		}
	}

	/** Every sealed vault within reach of {@code anchor} becomes an open Tier 4 Hero Vault. Returns how many. */
	public static int unsealVaults(ServerLevel level, BlockPos anchor) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -VAULT_SEARCH; dx <= VAULT_SEARCH; dx++) {
			for (int dy = -8; dy <= 8; dy++) {
				for (int dz = -VAULT_SEARCH; dz <= VAULT_SEARCH; dz++) {
					m.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
					BlockState s = level.getBlockState(m);
					if (s.is(VaultBlocks.SEAL)) {
						BlockPos at = m.immutable();
						HeroVault.place(level, at, 4, s.getValue(VaultSealBlock.FACING));
						level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
						level.playSound(null, at, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5f, 0.8f);
						n++;
					} else if (s.is(com.projecthero.mod.sentry.block.SentryBlocks.SERUM_VAT)) {
						com.projecthero.mod.sentry.block.SerumVatBlock.open(level, m.immutable()); // v0.17.5: the Sentry Serum's vat opens too
					}
				}
			}
		}
		return n;
	}

	/** Someone used the seal at {@code pos}: say who guards it, or open the vaults if Kross is gone or beaten. */
	public static void pokeSeal(ServerLevel level, BlockPos pos, Player player) {
		WardenKrossBoss kross = guardianNear(level, pos);
		if (kross == null) {
			BlockPos anchor = pos;
			VaultDungeonState state = VaultDungeonState.get(level);
			VaultDungeonState.Entry e = state.clearedNear(pos, VAULT_SEARCH + 8);
			if (e != null) {
				anchor = e.anchor;
			} else {
				state.markCleared(pos, level.getGameTime()); // the guardian vanished without dying: give up on him
			}
			unsealVaults(level, anchor);
			return;
		}
		player.displayClientMessage(Component.translatable("block.projecthero.vault_seal.locked").withStyle(s -> s.withColor(0x5FE8E8)), true);
	}

	/** A living Warden Kross within {@link #GUARDIAN_SEARCH} of {@code pos}, or null. */
	public static WardenKrossBoss guardianNear(ServerLevel level, BlockPos pos) {
		List<WardenKrossBoss> list = level.getEntitiesOfClass(WardenKrossBoss.class, new AABB(pos).inflate(GUARDIAN_SEARCH), WardenKrossBoss::isAlive);
		return list.isEmpty() ? null : list.get(0);
	}

	/** The Lockdown: blast shutters over every doorway marked by a lockdown track near {@code centre}. Returns how many doorways. */
	public static int seal(ServerLevel level, BlockPos centre) {
		int doors = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -TRACK_SEARCH; dx <= TRACK_SEARCH; dx++) {
			for (int dz = -TRACK_SEARCH; dz <= TRACK_SEARCH; dz++) {
				for (int dy = -4; dy <= 1; dy++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.getBlockState(m).is(VaultBlocks.TRACK)) {
						continue;
					}
					boolean any = false;
					for (int h = 1; h <= 3; h++) {
						BlockPos p = m.above(h);
						BlockState s = level.getBlockState(p);
						if (s.isAir() || s.canBeReplaced()) {
							level.setBlock(p, VaultBlocks.SHUTTER.defaultBlockState(), 3);
							any = true;
						}
					}
					if (any) {
						doors++;
						level.sendParticles(ParticleTypes.LAVA, m.getX() + 0.5, m.getY() + 1.2, m.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
					}
				}
			}
		}
		return doors;
	}

	/** Lifts every Lockdown shutter near {@code centre}. Returns how many blocks were removed. */
	public static int lift(ServerLevel level, BlockPos centre) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int r = TRACK_SEARCH + 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -4; dy <= 5; dy++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (level.getBlockState(m).is(VaultBlocks.SHUTTER)) {
						level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
						n++;
					}
				}
			}
		}
		if (n > 0) {
			level.playSound(null, centre, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.5f, 0.6f);
		}
		return n;
	}

	public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double radius) {
		return level.getPlayers(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius);
	}
}
