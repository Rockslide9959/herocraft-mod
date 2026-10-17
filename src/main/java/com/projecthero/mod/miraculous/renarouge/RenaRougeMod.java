package com.projecthero.mod.miraculous.renarouge;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.renarouge.entity.RenaRougeEntityTypes;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * v0.16.6: Rena Rouge's server entry point (called from {@code MiraculousMod.initialize}): the kit, her entities, and
 * two damage hooks -- Fox Feint's veto (an attack on her lands on an illusion instead) and Fox Camouflage ending the
 * moment she hurts anything (a melee swing or any of her damage).
 */
public final class RenaRougeMod {
	private RenaRougeMod() {
	}

	public static void initialize() {
		MiraculousKits.register(Kwami.TRIXX, new RenaRougeKit());
		RenaRougeEntityTypes.initialize();

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Miraculous.holds(p, Kwami.TRIXX) && RenaRougeMoves.feintHit(p, source)) {
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer p && p != entity && Miraculous.holds(p, Kwami.TRIXX)) {
				RenaRouge.breakCamouflage(p);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (!level.isClientSide && player instanceof ServerPlayer p && Miraculous.holds(p, Kwami.TRIXX)) {
				RenaRouge.breakCamouflage(p);
			}
			return InteractionResult.PASS;
		});
	}
}
