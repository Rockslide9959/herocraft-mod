package com.projecthero.mod.symbiote;

import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The bond flow when a player interacts with a free {@link SymbioteEntity} (from a meteor, a lab, or a
 * dead Symbiote Host).
 *
 * <p>Since the v0.9.14 standalone-power rework, <b>any</b> player can bond -- there is no host-type
 * gate any more. What happens on a successful bond depends on what the player already has:
 * <ul>
 *   <li><b>Spider-Man</b> -- the one compatible power ({@link SymbioteCompatibility}). Kept in full;
 *       the player becomes a Black Suit Spider-Man once they suit up.</li>
 *   <li><b>Any other power</b> (Thor, Iron Man, Max Steel, Punisher, or any experimental mutation) --
 *       incompatible. Warned, then purged ({@link SymbioteCompatibility#purge}) as part of the bond.</li>
 *   <li><b>No power at all</b> -- bonds cleanly, becomes a Normal Symbiote Host.</li>
 * </ul>
 *
 * <p>Multiplayer-safe: {@link SymbioteEntity#claimBond} is a single-claim soft lock, and the entity is
 * discarded the instant a bond succeeds.
 */
public final class SymbioteBonding {
	private SymbioteBonding() {
	}

	public static void attempt(ServerPlayer player, SymbioteEntity symbiote) {
		if (Symbiote.hasSymbiote(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.already_bonded")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!symbiote.claimBond(player, 60)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.entity_busy"), true);
			return;
		}

		boolean spiderMan = SymbioteCompatibility.isSpiderMan(player);
		boolean incompatible = !spiderMan && SymbioteCompatibility.hasIncompatiblePower(player);

		if (spiderMan) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.accepts_powers")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), false);
		} else if (incompatible) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.rejects_powers")
					.withStyle(ChatFormatting.DARK_GRAY), false);
			SymbioteCompatibility.purge(player);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.purged")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), false);
		}

		Symbiote.grant(player);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SQUID_INK, symbiote.getX(), symbiote.getY() + 0.5, symbiote.getZ(),
				80, 0.4, 0.6, 0.4, 0.1);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
				60, 0.4, 0.9, 0.4, 0.06);
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 0.6f);

		symbiote.discard();
	}
}
