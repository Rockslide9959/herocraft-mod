package com.projecthero.mod.miraculous;

import com.projecthero.mod.miraculous.network.MiraculousActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6: one entry point for the Miraculous powers, called once from {@code ProjectHeroMod.onInitialize}: the core
 * (the Kwami entity, the items, the spoken phrases, the shared passives, payloads, lifecycle) and the three kits. The per-player tick runs from {@code AbilityRouter.serverTick}; static
 * state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class MiraculousMod {
	private MiraculousMod() {
	}

	public static void initialize() {
		com.projecthero.mod.miraculous.entity.MiraculousEntityTypes.initialize();
		com.projecthero.mod.miraculous.item.MiraculousItems.initialize();
		MiraculousDamage.initialize();
		// v0.16.7 (user): secret identity + Kwami chatter
		MiraculousShowExtras.initialize();
		PayloadTypeRegistry.playC2S().register(MiraculousActionPayload.TYPE, MiraculousActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MiraculousActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			// v0.16.7: Erased -- no transforming / Kwami calls (detransforming still works)
			if (!Miraculous.transformed(p) && com.projecthero.mod.hero.PowerSuppression.refuse(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_FORM -> {
					if (Miraculous.hasPower(p)) {
						Miraculous.toggleForm(p);
					}
				}
				case TOGGLE_KWAMI_HIDDEN -> {
					if (Miraculous.hasPower(p)) {
						Miraculous.toggleKwamiHidden(p);
					}
				}
				case TAKE_OFF -> {
					if (Miraculous.hasPower(p)) {
						Miraculous.takeOff(p);
					}
				}
			}
		});

		com.projecthero.mod.miraculous.ladybug.LadybugMod.initialize();
		com.projecthero.mod.miraculous.catnoir.CatNoirMod.initialize();
		com.projecthero.mod.miraculous.renarouge.RenaRougeMod.initialize();
		com.projecthero.mod.miraculous.hawkmoth.HawkMothMod.initialize();
		com.projecthero.mod.miraculous.carapace.CarapaceMod.initialize();
		com.projecthero.mod.miraculous.pegasus.PegasusMod.initialize();
		com.projecthero.mod.miraculous.viperion.ViperionMod.initialize(); // v0.16.7

		// v0.16.6 (user): the transformation is spoken in chat ("Tikki, spots on!"). v0.16.7 (user): it goes out as the
		// HERO saying it ("<Ladybug> Spots on!"), never under the player's own name -- chat is global
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!Miraculous.hasPower(sender)) {
				return true;
			}
			String text = message.signedContent();
			if (!MiraculousShowExtras.speakPhrase(sender, text)) {
				return true;
			}
			sender.server.execute(() -> Miraculous.onChat(sender, text));
			return false;
		});
		// eating the Kwami's favourite food while it is hungry feeds the Kwami instead
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!Miraculous.wantsFood(player, stack)) {
				return InteractionResultHolder.pass(ItemStack.EMPTY);
			}
			if (player instanceof ServerPlayer sp) {
				Miraculous.feed(sp, stack);
			}
			return InteractionResultHolder.success(stack);
		});

		ServerPlayerEvents.JOIN.register(Miraculous::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Miraculous.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Miraculous.hasPower(p)) {
				Miraculous.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Miraculous.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Miraculous.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		Miraculous.clearSessionState();
		MiraculousShowExtras.clearSessionState();
	}
}
