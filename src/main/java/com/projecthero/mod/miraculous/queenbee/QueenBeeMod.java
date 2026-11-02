package com.projecthero.mod.miraculous.queenbee;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2: Queen Bee server entry point (called from {@code MiraculousMod.initialize}): the kit, and Venom carried by a
 * plain punch -- with Venom armed, the next creature she hits with her hand is paralysed (her moves deliver it through
 * {@link QueenBeeMoves#hit}).
 */
public final class QueenBeeMod {
	private QueenBeeMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.POLLEN, new QueenBeeKit());
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le && QueenBee.venomArmed(sp)) {
				QueenBeeMoves.deliverVenom(sp, le);
			}
			return InteractionResult.PASS;
		});
	}
}
