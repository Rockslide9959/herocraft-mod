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
		// v0.16.7: the Mirage wheel (Shift+Z opens it on the client; the pick comes back here)
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
				com.projecthero.mod.miraculous.renarouge.network.RenaMirageMenuPayload.TYPE, com.projecthero.mod.miraculous.renarouge.network.RenaMirageMenuPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
				com.projecthero.mod.miraculous.renarouge.network.RenaMirageChoicePayload.TYPE, com.projecthero.mod.miraculous.renarouge.network.RenaMirageChoicePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.projecthero.mod.miraculous.renarouge.network.RenaMirageChoicePayload.TYPE,
				(payload, context) -> {
					if (Miraculous.transformedAs(context.player(), Kwami.TRIXX) && !com.projecthero.mod.hero.PowerSuppression.refuse(context.player())) {
						RenaRougeMoves.chooseMirage(context.player(), payload.index());
					}
				});
		// v0.16.7: Eclipse -- a mob's shot loosed inside the night flies off course
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> RenaMirage.onEntityLoad(entity));

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
				RenaMirage.onSwing(p); // v0.16.7: the Mirage copies swing with her
			}
			return InteractionResult.PASS;
		});
	}
}
