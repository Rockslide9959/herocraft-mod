package com.projecthero.mod.blackpanther;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.blackpanther.entity.BlackPantherEntityTypes;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.blackpanther.network.BlackPantherSuitPayload;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.3: one entry point for the whole Black Panther power, called once from {@code ProjectHeroMod.onInitialize}: the herb,
 * soup and thrown things, the damage rules, the payloads and the lifecycle hooks; plus
 * {@code /projecthero power tune black_panther kinetic <0-100>}. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class BlackPantherMod {
	private BlackPantherMod() {
	}

	public static void initialize() {
		BlackPantherItems.initialize();
		BlackPantherEntityTypes.initialize();
		BlackPantherDamage.initialize();
		BlackPantherSounds.initialize(); // v0.17.7: the nanotech suit sounds
		// v0.17.7: the soup gives Bast's Blessing; sleeping with it opens the Ancestral Plane (the trial that grants the power)
		com.projecthero.mod.blackpanther.plane.AncestralPlane.initialize();
		PayloadTypeRegistry.playS2C().register(PantherSensesPayload.TYPE, PantherSensesPayload.CODEC);
		// v0.17.7 (user): H with the necklace on lets the suit out / draws it back in (the Flash Ring's pattern)
		PayloadTypeRegistry.playC2S().register(BlackPantherSuitPayload.TYPE, BlackPantherSuitPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlackPantherSuitPayload.TYPE, (payload, context) -> {
			if (!com.projecthero.mod.hero.PowerSuppression.refuse(context.player())) {
				BlackPanther.toggleSuit(context.player());
			}
		});
		ServerPlayerEvents.JOIN.register(BlackPanther::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> BlackPanther.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && BlackPanther.hasPower(p)) {
				BlackPanther.onDeath(p); // v0.17.7: the running moves end and the Kinetic bar empties
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> BlackPanther.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlackPanther.clearTransient(handler.getPlayer()));
	}

	public static void clearSessionState() {
		BlackPanther.clearSessionState();
		// v0.17.7: the Ancestral Plane's dreams (player UUIDs), pending wake-ups and slot builds (dimension keys)
		com.projecthero.mod.blackpanther.plane.AncestralPlane.clearSessionState();
	}

	/** One plane test command: jump the dream (starting one here if needed) to {@code stage}. */
	private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> planeJump(String name,
			com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage stage, String done) {
		return Commands.literal(name).executes(c -> {
			ServerPlayer player = c.getSource().getPlayerOrException();
			if (BlackPanther.hasPower(player)) {
				c.getSource().sendFailure(Component.literal("You are already the Black Panther (power revoke black_panther first)"));
				return 0;
			}
			boolean ok = com.projecthero.mod.blackpanther.plane.AncestralPlane.jumpTo(player, stage);
			c.getSource().sendSuccess(() -> Component.literal(ok ? done : "Couldn't open the Ancestral Plane here"), false);
			return ok ? 1 : 0;
		});
	}

	/**
	 * {@code /projecthero power tune black_panther kinetic <0-100>} (op only); v0.17.7 Ancestral Plane test tools:
	 * {@code blessing} (Bast's Blessing for 60 s), {@code plane} (fall asleep into it now, from where you stand),
	 * {@code shrine | trial | path | parkour | finale} (jump to that stage), {@code leave} (wake up, no power).
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("black_panther")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("blessing").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					com.projecthero.mod.blackpanther.plane.BastBlessing.give(player);
					c.getSource().sendSuccess(() -> Component.literal("Bast's Blessing for 60 s: sleep in a bed (day or night) to enter the Ancestral Plane"),
							false);
					return 1;
				}))
				.then(Commands.literal("plane").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					if (BlackPanther.hasPower(player)) {
						c.getSource().sendFailure(Component.literal("You are already the Black Panther (power revoke black_panther first)"));
						return 0;
					}
					boolean ok = com.projecthero.mod.blackpanther.plane.AncestralPlane.beginHere(player);
					c.getSource().sendSuccess(() -> Component.literal(ok ? "You fall into the Ancestral Plane" : "Already dreaming"), false);
					return ok ? 1 : 0;
				}))
				.then(planeJump("shrine", com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage.SHRINE, "At the Panther Shrine: right-click the statue"))
				.then(planeJump("trial", com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage.TRIAL, "The Trial of the Shades begins"))
				.then(planeJump("path", com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage.PATH, "The spirit path forms"))
				.then(planeJump("parkour", com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage.PARKOUR, "On the spirit path, checkpoint 1"))
				.then(planeJump("finale", com.projecthero.mod.blackpanther.plane.AncestralPlane.Stage.FINALE, "The end of the path"))
				.then(Commands.literal("leave").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					boolean ok = com.projecthero.mod.blackpanther.plane.AncestralPlane.leave(player);
					c.getSource().sendSuccess(() -> Component.literal(ok ? "You wake up (no power)" : "You aren't in the Ancestral Plane"), false);
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("kinetic")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0f, BlackPantherConfig.KINETIC_MAX))
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!BlackPanther.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not the Black Panther"));
										return 0;
									}
									float v = FloatArgumentType.getFloat(c, "amount");
									BlackPanther.setKinetic(player, v);
									c.getSource().sendSuccess(() -> Component.literal("Kinetic Energy set to " + Math.round(v)), false);
									return 1;
								})));
	}
}
