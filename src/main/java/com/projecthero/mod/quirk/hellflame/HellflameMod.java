package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: the Hellflame Quirk's server init (called from {@code QuirkMod.initialize}): the config, the particles, the kit
 * and its hooks. v0.17.1: his own sounds; hot hands (a punch sets the target alight while cloaked or from Blaze up); the
 * Forge Hand (Sneak + use with an empty main hand cooks / smelts what is in the off hand -- both sides take the use, so the
 * off-hand food is not eaten or the ore block placed instead).
 */
public final class HellflameMod {
	private HellflameMod() {
	}

	public static void initialize() {
		HellflameConfig.load(); // config/projecthero_quirk_hellflame.json (Body Heat)
		HellflameParticles.register();
		HellflameSounds.initialize();
		QuirkKits.register(Quirk.HELLFLAME, new HellflameKit());
		HellflameMeter.register(); // v0.17.1: Body Heat bands, the Overheat push, pacing, the combo route
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le && Hellflame.is(sp)) {
				HellflameMoves.hotHands(sp, le);
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!HellflameMoves.forgeWants(player, hand)) {
				return InteractionResultHolder.pass(player.getItemInHand(hand));
			}
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				HellflameMoves.forge(sp);
			}
			return InteractionResultHolder.success(player.getItemInHand(hand));
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!HellflameMoves.forgeWants(player, hand)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				HellflameMoves.forge(sp);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
