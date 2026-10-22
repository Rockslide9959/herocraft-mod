package com.projecthero.mod.quirk.hellflame;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: the Hellflame Quirk's server init (called from {@code QuirkMod.initialize}): the kit, and the Flame Cloak's
 * burning melee (a punch while cloaked sets the target alight; the swing itself is untouched).
 */
public final class HellflameMod {
	private HellflameMod() {
	}

	public static void initialize() {
		HellflameConfig.load(); // v0.16.7 revamp: config/projecthero_quirk_hellflame.json (Body Heat)
		HellflameParticles.register();
		QuirkKits.register(Quirk.HELLFLAME, new HellflameKit());
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le && Hellflame.cloaked(sp)) {
				HellflameMoves.cloakMelee(sp, le);
			}
			return InteractionResult.PASS;
		});
	}
}
