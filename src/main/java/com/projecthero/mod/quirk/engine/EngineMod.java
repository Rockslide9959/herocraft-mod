package com.projecthero.mod.quirk.engine;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: the Engine Quirk's server init -- registers {@link EngineKit}, its meter and sounds, and v0.17.1 <b>Rescue Carry</b>:
 * Sneak + use (empty hand) on an ally lifts them onto his back; Sneak + use again on anything sets them down.
 */
public final class EngineMod {
	private EngineMod() {
	}

	public static void initialize() {
		EngineSounds.initialize();
		QuirkKits.register(Quirk.ENGINE, new EngineKit());
		EngineMeter.register(); // v0.17.1 framework: Engine Heat bands + pacing
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !carryGesture(sp)) {
				return InteractionResult.PASS;
			}
			if (EngineMoves.carried(sp) != null) {
				EngineMoves.setDown(sp);
				return InteractionResult.SUCCESS;
			}
			return entity instanceof LivingEntity le && EngineMoves.carry(sp, le) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && carryGesture(sp)
					&& EngineMoves.carried(sp) != null) {
				EngineMoves.setDown(sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Sneaking, empty-handed, holding Engine (not Erased). */
	private static boolean carryGesture(ServerPlayer sp) {
		return sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && Engine.is(sp) && !sp.isSpectator();
	}
}
