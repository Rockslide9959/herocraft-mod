package com.projecthero.mod.skyfortress;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.block.SkyVaultSealBlock;
import com.projecthero.mod.skyfortress.entity.AdmiralKestrelBoss;

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
 * v0.17.7: clearing the Sky Fortress. Admiral Kestrel dies -> {@link #bossDefeated}: every sealed Fortress Vault within
 * {@link #VAULT_SEARCH} of her flight deck opens into a Tier 4 Hero Vault (per-player loot; the Ghost bonus applies via
 * {@code HeroVault.open}), title + chat to everyone within 80. A seal used while no Kestrel lives within
 * {@link #GUARDIAN_SEARCH} opens anyway ({@link #pokeSeal}), so the fortress never gets stuck. Static methods, no state:
 * an opened vault is the record.
 */
public final class SkyFortressClearing {
	public static final int VAULT_SEARCH = 44;
	public static final double GUARDIAN_SEARCH = 96.0;

	private SkyFortressClearing() {
	}

	public static void bossDefeated(ServerLevel level, BlockPos home) {
		int opened = unsealVaults(level, home, VAULT_SEARCH);
		level.playSound(null, home, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1.0f);
		Component title = Component.translatable("projecthero.sky_fortress.cleared.title").withStyle(s -> s.withColor(0x6FA8DC).withBold(true));
		Component sub = Component.translatable("projecthero.sky_fortress.cleared.sub").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) < 80 * 80)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			p.displayClientMessage(Component.translatable("projecthero.sky_fortress.cleared.chat").withStyle(s -> s.withColor(0x6FA8DC)), false);
			if (opened > 0) {
				p.displayClientMessage(Component.translatable("projecthero.vault.cleared.vaults", opened).withStyle(ChatFormatting.AQUA), false);
			}
		}
	}

	/** Every seal within {@code r} (horizontal, 16 up / down) of {@code centre} becomes an open Tier 4 Hero Vault. */
	public static int unsealVaults(ServerLevel level, BlockPos centre, int r) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -16; dy <= 16; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					if (s.is(SkyFortressBlocks.SEAL)) {
						BlockPos at = m.immutable();
						HeroVault.place(level, at, 4, s.getValue(SkyVaultSealBlock.FACING));
						level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
						level.playSound(null, at, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5f, 0.8f);
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Someone used a seal: say who guards it, or open it when the Admiral is gone. */
	public static void pokeSeal(ServerLevel level, BlockPos pos, Player player) {
		if (guardianNear(level, pos) == null) {
			unsealVaults(level, pos, 6);
			return;
		}
		player.displayClientMessage(Component.translatable("block.projecthero.sky_vault_seal.locked").withStyle(s -> s.withColor(0x6FA8DC)), true);
	}

	public static AdmiralKestrelBoss guardianNear(ServerLevel level, BlockPos pos) {
		List<AdmiralKestrelBoss> list = level.getEntitiesOfClass(AdmiralKestrelBoss.class, new AABB(pos).inflate(GUARDIAN_SEARCH), AdmiralKestrelBoss::isAlive);
		return list.isEmpty() ? null : list.get(0);
	}
}
