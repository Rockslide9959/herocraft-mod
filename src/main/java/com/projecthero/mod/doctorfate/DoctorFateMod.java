package com.projecthero.mod.doctorfate;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.doctorfate.data.FateAttachments;
import com.projecthero.mod.doctorfate.network.FateActionPayload;
import com.projecthero.mod.doctorfate.network.FateBargainPayload;
import com.projecthero.mod.doctorfate.network.FateFxPayload;
import com.projecthero.mod.doctorfate.network.FatePossessPayload;
import com.projecthero.mod.doctorfate.network.FateServePayload;
import com.projecthero.mod.doctorfate.worldgen.HelmetPedestalBlock;
import com.projecthero.mod.doctorfate.worldgen.TombOfNabuStructure;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.3 Doctor Fate: one entry point for the whole power, called once from {@code ProjectHeroMod.onInitialize}: the attachment,
 * items, block, Tomb Guardian, the Tomb of Nabu structure type, the damage rules, the payloads, the chat tag and the lifecycle
 * hooks; plus {@code /projecthero power tune doctor_fate (dominion <0-100> | cooldowns | possess | bargain <1-3>)}. The per-player
 * tick runs from {@code AbilityRouter.serverTick}; the storms, wards and chains tick once a server tick here. Static state is
 * dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class DoctorFateMod {
	private DoctorFateMod() {
	}

	public static void initialize() {
		FateAttachments.initialize();
		DoctorFateItems.initialize();
		TombOfNabuStructure.initialize();
		DoctorFateDamage.initialize();
		PayloadTypeRegistry.playC2S().register(FateActionPayload.TYPE, FateActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FateBargainPayload.TYPE, FateBargainPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FateServePayload.TYPE, FateServePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FateFxPayload.TYPE, FateFxPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FatePossessPayload.TYPE, FatePossessPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.projecthero.mod.doctorfate.network.FateTrialPayload.TYPE,
				com.projecthero.mod.doctorfate.network.FateTrialPayload.CODEC); // v0.17.4 round 2: Nabu's library
		PayloadTypeRegistry.playS2C().register(com.projecthero.mod.doctorfate.network.FateSpellFxPayload.TYPE,
				com.projecthero.mod.doctorfate.network.FateSpellFxPayload.CODEC); // v0.17.3 effects pass
		ServerPlayNetworking.registerGlobalReceiver(FateActionPayload.TYPE, (payload, context) -> onAction(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DoctorFateSpells.tickWorld(server);
			DoctorFateSpells.tickBound(server);
		});
		ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE,
				(sender, message) -> sender == null ? message : DoctorFateDominion.chatTag(sender, message));
		ServerPlayerEvents.JOIN.register(DoctorFate::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DoctorFate.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && DoctorFate.hasPower(p)) {
				DoctorFate.clearTransient(p);
			}
		});
		// v0.17.4 round 2: a Possession survives a world change and a logout (no escaping Nabu); death ends it
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> DoctorFate.clearTransient(player, false));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DoctorFate.clearTransient(handler.getPlayer(), false);
			DoctorFate.forget(handler.getPlayer().getUUID());
		});
	}

	public static void clearSessionState() {
		DoctorFate.clearSessionState();
	}

	/** A {@link FateActionPayload} from the client (public for the gametests, which drive H through it). */
	public static void onAction(ServerPlayer p, FateActionPayload payload) {
		switch (payload.action()) {
			case TOGGLE_FLIGHT -> {
				if (DoctorFate.hasPower(p)) {
					DoctorFateFlight.toggle(p);
				}
			}
			case STRUGGLE -> {
				if (DoctorFate.hasPower(p)) {
					DoctorFatePossession.struggle(p, payload.arg() != 0); // v0.17.4 round 2: the hold state (1 down, 0 up)
				}
			}
			case BARGAIN_ACCEPT -> DoctorFateBargains.answer(p, payload.arg(), true);
			case BARGAIN_REFUSE -> DoctorFateBargains.answer(p, payload.arg(), false);
			case SERVE_ACCEPT -> serve(p, payload.pos(), true);
			case SERVE_REFUSE -> serve(p, payload.pos(), false);
			case TOGGLE_HELMET -> {
				// v0.17.3 (user: "like the Nova ability"): H puts the helmet on / takes it off
				if (DoctorFate.hasPower(p) && !com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) {
					DoctorFate.pressH(p);
				}
			}
			case MIND_CHOICE -> DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.byId(payload.arg()));
			case MIND_CLOSE -> DoctorFateMindscape.close(p);
			// v0.17.4 round 2: the trial in Nabu's library
			case TRIAL_READ -> DoctorFateNabuTrial.read(p, payload.arg());
			case TRIAL_CONFRONT -> DoctorFateNabuTrial.confront(p);
			case TRIAL_ANSWER -> DoctorFateNabuTrial.answer(p, payload.arg());
			case TRIAL_ENTERED -> DoctorFateNabuTrial.entered(p);
		}
	}

	/**
	 * "Will you serve Order?" answered at the Tomb pedestal at {@code pos}. Accepting grants the power (the helmet is part of it, not
	 * an item: H wears it) and empties the pedestal; refusing leaves the helmet there for another player.
	 */
	public static boolean serve(ServerPlayer p, BlockPos pos, boolean accept) {
		if (DoctorFate.hasPower(p)) {
			return false;
		}
		BlockState st = p.level().getBlockState(pos);
		if (!(st.getBlock() instanceof HelmetPedestalBlock) || !st.getValue(HelmetPedestalBlock.HAS_HELMET)
				|| p.distanceToSqr(pos.getCenter()) > 64.0) {
			return false;
		}
		if (!accept) {
			p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.refuse_pedestal").withStyle(ChatFormatting.GOLD), false);
			return false;
		}
		if (!DoctorFate.grant(p)) {
			return false;
		}
		p.level().setBlock(pos, st.setValue(HelmetPedestalBlock.HAS_HELMET, false), 3);
		return true;
	}

	/** {@code /projecthero power tune doctor_fate ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("doctor_fate")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("dominion")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, DoctorFateConfig.DOMINION_MAX))
								.executes(c -> {
									ServerPlayer player = fate(c.getSource());
									if (player == null) {
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									DoctorFate.setDominion(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Dominion set to " + Math.round(v)), false);
									return 1;
								})))
				.then(Commands.literal("cooldowns").executes(c -> {
					ServerPlayer player = fate(c.getSource());
					if (player == null) {
						return 0;
					}
					DoctorFate.clearCooldowns(player);
					c.getSource().sendSuccess(() -> Component.literal("Doctor Fate cooldowns and lockout cleared"), false);
					return 1;
				}))
				.then(Commands.literal("possess").executes(c -> {
					ServerPlayer player = fate(c.getSource());
					if (player == null) {
						return 0;
					}
					DoctorFate.setHelmet(player, true);
					DoctorFatePossession.start(player);
					return 1;
				}))
				.then(Commands.literal("bargain")
						.then(Commands.argument("kind", IntegerArgumentType.integer(1, 3)).executes(c -> {
							ServerPlayer player = fate(c.getSource());
							if (player == null) {
								return 0;
							}
							return DoctorFateBargains.offer(player, IntegerArgumentType.getInteger(c, "kind")) ? 1 : 0;
						})));
	}

	private static ServerPlayer fate(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!DoctorFate.hasPower(player)) {
			source.sendFailure(Component.literal("You don't serve Nabu"));
			return null;
		}
		return player;
	}

}
