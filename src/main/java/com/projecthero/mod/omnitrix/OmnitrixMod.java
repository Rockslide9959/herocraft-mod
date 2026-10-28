package com.projecthero.mod.omnitrix;

import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.1: one entry point for the whole Omnitrix, called once from {@code ProjectHeroMod.onInitialize}: the attachments, the
 * damage types and rules, the items, the Containment Pod, the meteor and pod entities, the alien projectile, the payload, the
 * vanilla attack / use hooks that reach the alien kits, the lifecycle hooks and the alien kits themselves. The per-player tick
 * runs from {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through
 * {@link #clearSessionState}.
 */
public final class OmnitrixMod {
	private OmnitrixMod() {
	}

	public static void initialize() {
		OmnitrixAttachments.init();
		OmnitrixSounds.initialize();
		OmnitrixDamage.initialize();
		OmnitrixEntities.initialize();
		com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks.initialize();
		com.projecthero.mod.omnitrix.item.OmnitrixItems.initialize();

		// the alien kits -- ONE LINE PER KIT (the other eight add theirs here)
		com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit.register();
		com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit.register();
		com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit.register();
		com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.register();
		com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit.register();
		com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit.register();
		com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.register();
		com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit.register();
		com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit.register();
		com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.register();

		PayloadTypeRegistry.playC2S().register(OmnitrixActionPayload.TYPE, OmnitrixActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OmnitrixActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Omnitrix.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case SELECT -> {
					if (!Omnitrix.state(p).transformed()) {
						Omnitrix.select(p, payload.alien());
					}
				}
				case CYCLE -> Omnitrix.cycle(p, "-1".equals(payload.alien()) ? -1 : 1);
				case SLAP -> Omnitrix.slap(p);
				case USE_AIR -> {
					AlienKit kit = Aliens.kit(Omnitrix.state(p).active);
					if (kit != null && !com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
						kit.onUse(p, net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(OmnitrixMeteors::tick);
		ServerTickEvents.END_SERVER_TICK.register(OmnitrixSfx::tick); // v0.17.1: the sound recipes' delayed layers
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Omnitrix.hasPower(p)) {
				Omnitrix.onDeath(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Omnitrix.onDimensionChange(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				Omnitrix.onLogout(handler.getPlayer());
			}
		});

		// the vanilla attack / use keys, for a transformed wearer
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer p && !level.isClientSide()) {
				AlienKit kit = kitOf(p);
				if (kit != null && kit.onAttack(p, entity)) {
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof ServerPlayer p && !level.isClientSide()) {
				AlienKit kit = kitOf(p);
				if (kit != null) {
					InteractionResult r = kit.onUse(p, hand);
					if (r != InteractionResult.PASS) {
						return r.consumesAction() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
					}
				}
			}
			return InteractionResultHolder.pass(stack);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer p && !level.isClientSide()) {
				AlienKit kit = kitOf(p);
				if (kit != null) {
					return kit.onUseBlock(p, hand, hit);
				}
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer p && !level.isClientSide()) {
				AlienKit kit = kitOf(p);
				if (kit != null) {
					return kit.onUseEntity(p, hand, entity);
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static AlienKit kitOf(ServerPlayer p) {
		if (!Omnitrix.hasPower(p) || com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			return null;
		}
		return Aliens.kit(Omnitrix.state(p).active);
	}

	public static void clearSessionState() {
		Omnitrix.clearSessionState();
		OmnitrixMeteors.clearSessionState();
		OmnitrixSfx.clearSessionState();
		com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit.clearSessionState();
		com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.clearSessionState();
	}
}
