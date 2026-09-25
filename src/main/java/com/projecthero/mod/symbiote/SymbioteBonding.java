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
		// v0.12.25: touching the Symbiote starts the bonding minigame; winning it calls bondNow.
		SymbioteBondGame.begin(player, symbiote);
	}

	/** The bond itself, after the minigame is won (also what the gametests call directly). */
	public static void bondNow(ServerPlayer player, SymbioteEntity symbiote) {
		if (!symbiote.claimBond(player, 60)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.entity_busy"), true);
			return;
		}
		completeBond(player, symbiote.getX(), symbiote.getY() + 0.5, symbiote.getZ());
		symbiote.discard();
	}

	/**
	 * Bond from a filled {@link com.projecthero.mod.symbiote.item.SymbioteVialItem Symbiote Vial}: the same
	 * flow as touching a free Symbiote, minus the entity. Returns false if the player is already bonded. v0.12.25: starts the bonding minigame; the vial is only used up when it is won.
	 */
	public static boolean attemptFromVial(ServerPlayer player) {
		if (Symbiote.hasSymbiote(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.already_bonded")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		SymbioteBondGame.begin(player, null);
		return true;
	}

	/** The vial bond itself, after the minigame is won. */
	public static void bondFromVialNow(ServerPlayer player) {
		completeBond(player, player.getX(), player.getY() + 1.0, player.getZ());
	}

	private static void completeBond(ServerPlayer player, double fxX, double fxY, double fxZ) {
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

		Symbiote.grant(player, false);

		// A wild bond does not just click into place -- the organism has to spread through the host
		// first. For the settling phase the host has the Symbiote's protection but no abilities, and
		// their body fights it (see SymbioteVitalsManager.tickBonding). The Symbiote talks them
		// through it (SymbioteDialogue).
		SymbioteVitalsManager.beginBonding(player);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bonding_begins")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SQUID_INK, fxX, fxY, fxZ, 100, 0.4, 0.6, 0.4, 0.12);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
				60, 0.4, 0.9, 0.4, 0.05);
		SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 1.2f, 0.35f);
	}
}
