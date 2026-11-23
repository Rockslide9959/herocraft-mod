package com.projecthero.mod.temple;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.temple.block.TempleBlocks;
import com.projecthero.mod.temple.block.TempleGateBlock;
import com.projecthero.mod.temple.block.TempleSealBlock;
import com.projecthero.mod.temple.block.TempleVaultSealBlock;
import com.projecthero.mod.temple.entity.AnkhatepBoss;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.7: the Ancient Temple's progress events, all static and stateless (the cleared temples live in
 * {@link TempleState}).
 * <ul>
 * <li>{@link #trialsCheck}: a seal broke -- once all three in the hall are broken, the hall's {@code trials} gates open.</li>
 * <li>{@link #sentinelDefeated}: the Tomb Sentinel fell -- the crypt's {@code crypt} gate opens.</li>
 * <li>{@link #bossDefeated}: Ankhatep fell -- cleared for good, the {@code vault} gate opens and every sealed Treasury
 * Vault becomes a Tier 4 Hero Vault; title + chat within 64.</li>
 * <li>{@link #pokeSeal}: a sealed vault used while no Ankhatep is near opens anyway (never stuck).</li>
 * </ul>
 */
public final class TempleClearing {
	public static final int SEAL_SEARCH = 16;
	public static final int GATE_SEARCH = 24;
	public static final int VAULT_SEARCH = 24;
	public static final double GUARDIAN_SEARCH = 96.0;

	private TempleClearing() {
	}

	/** A Seal broke at {@code pos}: open the hall's gate once all three seals around it are broken. Returns whether it opened. */
	public static boolean trialsCheck(ServerLevel level, BlockPos pos) {
		int seals = 0;
		int broken = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-SEAL_SEARCH, -4, -SEAL_SEARCH), pos.offset(SEAL_SEARCH, 4, SEAL_SEARCH))) {
			BlockState s = level.getBlockState(p);
			if (s.is(TempleBlocks.SEAL)) {
				seals++;
				if (s.getValue(TempleSealBlock.BROKEN)) {
					broken++;
				}
			}
		}
		if (seals == 0 || broken < seals) {
			Component msg = Component.translatable("projecthero.temple.seals_left", seals - broken).withStyle(c -> c.withColor(0xE8C060));
			for (ServerPlayer p : playersNear(level, pos, 32)) {
				p.displayClientMessage(msg, true);
			}
			return false;
		}
		int n = TempleGateBlock.open(level, pos, TempleGateBlock.Group.TRIALS, GATE_SEARCH);
		Component msg = Component.translatable("projecthero.temple.trials_done").withStyle(c -> c.withColor(0xE8C060).withBold(true));
		for (ServerPlayer p : playersNear(level, pos, 48)) {
			p.displayClientMessage(msg, false);
		}
		return n > 0;
	}

	/** The Tomb Sentinel fell at (or was bound to) {@code home}: the crypt's gate opens. */
	public static void sentinelDefeated(ServerLevel level, BlockPos home) {
		TempleGateBlock.open(level, home, TempleGateBlock.Group.CRYPT, GATE_SEARCH);
		Component msg = Component.translatable("projecthero.temple.sentinel.down").withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : playersNear(level, home, 48)) {
			p.displayClientMessage(msg, false);
		}
	}

	/** Ankhatep, bound to {@code home}, has died. */
	public static void bossDefeated(ServerLevel level, BlockPos home) {
		boolean first = TempleState.get(level).markCleared(home, level.getGameTime());
		TempleGateBlock.open(level, home, TempleGateBlock.Group.VAULT, GATE_SEARCH);
		int opened = unsealVaults(level, home);
		if (!first) {
			return;
		}
		level.playSound(null, home, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);
		Component title = Component.translatable("projecthero.temple.cleared.title").withStyle(s -> s.withColor(0xE8C060).withBold(true));
		Component sub = Component.translatable("projecthero.temple.cleared.sub").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : playersNear(level, home, 64)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			p.displayClientMessage(Component.translatable("projecthero.temple.cleared.chat").withStyle(s -> s.withColor(0xE8C060)), false);
			if (opened > 0) {
				p.displayClientMessage(Component.translatable("projecthero.temple.cleared.vaults", opened).withStyle(ChatFormatting.AQUA), false);
			}
		}
	}

	/** Every sealed Treasury Vault near {@code home} becomes an open Tier 4 Hero Vault. Returns how many. */
	public static int unsealVaults(ServerLevel level, BlockPos home) {
		int n = 0;
		for (BlockPos m : BlockPos.betweenClosed(home.offset(-VAULT_SEARCH, -8, -VAULT_SEARCH), home.offset(VAULT_SEARCH, 8, VAULT_SEARCH))) {
			BlockState s = level.getBlockState(m);
			if (s.is(TempleBlocks.VAULT_SEAL)) {
				BlockPos at = m.immutable();
				HeroVault.place(level, at, 4, s.getValue(TempleVaultSealBlock.FACING));
				level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
				level.playSound(null, at, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5f, 0.8f);
				n++;
			}
		}
		return n;
	}

	/** Someone used a sealed vault: say who guards it, or open them if Ankhatep is gone or beaten. */
	public static void pokeSeal(ServerLevel level, BlockPos pos, Player player) {
		AnkhatepBoss king = guardianNear(level, pos);
		if (king == null) {
			TempleState state = TempleState.get(level);
			TempleState.Entry e = state.clearedNear(pos, VAULT_SEARCH + 8);
			BlockPos home = e != null ? e.anchor : pos;
			if (e == null) {
				state.markCleared(pos, level.getGameTime()); // the king vanished without dying: give up on him
			}
			TempleGateBlock.open(level, home, TempleGateBlock.Group.VAULT, GATE_SEARCH);
			unsealVaults(level, home);
			return;
		}
		player.displayClientMessage(Component.translatable("block.projecthero.temple_vault_seal.locked").withStyle(s -> s.withColor(0xE8C060)), true);
	}

	public static AnkhatepBoss guardianNear(ServerLevel level, BlockPos pos) {
		List<AnkhatepBoss> list = level.getEntitiesOfClass(AnkhatepBoss.class, new AABB(pos).inflate(GUARDIAN_SEARCH), AnkhatepBoss::isAlive);
		return list.isEmpty() ? null : list.get(0);
	}

	public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double radius) {
		return level.getPlayers(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius);
	}
}
