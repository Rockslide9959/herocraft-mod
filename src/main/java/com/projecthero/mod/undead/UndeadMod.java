package com.projecthero.mod.undead;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadEntityTypes;
import com.projecthero.mod.undead.item.UndeadItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: one entry point for the whole Undead power, called once from {@code ProjectHeroMod.onInitialize}: the card, the
 * flying pieces and clones, the damage rules, the missing-hand rules and the lifecycle hooks; plus
 * {@code /projecthero power tune undead (break <part|all> | restore)}. The per-player tick runs
 * from {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through
 * {@link #clearSessionState}.
 */
public final class UndeadMod {
	private static final String[] PART_NAMES = { "head", "body", "right_arm", "left_arm", "right_leg", "left_leg" };

	private UndeadMod() {
	}

	public static void initialize() {
		UndeadSoundEvents.initialize(); // v0.17.3 Undead sounds: projecthero:undead.*
		UndeadItems.initialize();
		UndeadEntityTypes.initialize();
		UndeadDamage.initialize();
		UndeadBoneSword.initialize(); // v0.17.3 Shift+V: the sword's melee sweep
		// a missing hand can't hit or use anything (main arm), or hold anything (off arm)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		// v0.17.3 (tester: "A way people can naturally pick up the head"): an empty hand picks up an Undead who is only a head
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> UndeadCarry.tryPickUp(player, hand, entity));
		UseItemCallback.EVENT.register((player, world, hand) -> handGone(player, hand)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		ServerPlayerEvents.JOIN.register(Undead::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Undead.clearTransient(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Undead.hasPower(p)) {
				UndeadAbilities.clear(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> UndeadAbilities.clear(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UndeadAbilities.clear(handler.getPlayer());
			Undead.forget(handler.getPlayer().getUUID());
		});
	}

	/**
	 * The hand used is one Undead doesn't have right now: its arm or the hand itself gone or still regrowing, or eaten past the
	 * wrist ({@link Undead#handMissing}; the main hand follows the player's main arm). v0.17.3 (user: "players shouldn't be able to
	 * hold items while not having an arm for the respective arm"): the item stays in its slot, but it can't be used, swung, placed or
	 * attacked with (the callbacks above), anything already in use stops ({@link Undead#tick}), and it isn't drawn in that hand
	 * (UndeadHeldItemMixin, UndeadFirstPersonItemMixin).
	 */
	public static boolean handGone(Player player, InteractionHand hand) {
		if (!Undead.hasPower(player) || player.isSpectator()) {
			return false;
		}
		return hand == InteractionHand.MAIN_HAND ? Undead.mainHandMissing(player) : Undead.offHandMissing(player);
	}

	public static void clearSessionState() {
		Undead.clearSessionState();
	}

	/** {@code /projecthero power tune undead ...} (op only). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("undead")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("break")
						.then(Commands.argument("part", StringArgumentType.word())
								.suggests((c, b) -> {
									for (String n : PART_NAMES) {
										b.suggest(n);
									}
									b.suggest("all");
									return b.buildFuture();
								})
								.executes(c -> {
									ServerPlayer player = c.getSource().getPlayerOrException();
									if (!Undead.hasPower(player)) {
										c.getSource().sendFailure(Component.literal("You are not Undead"));
										return 0;
									}
									String name = StringArgumentType.getString(c, "part");
									if ("all".equals(name)) {
										Undead.shatter(player);
										return 1;
									}
									int part = java.util.Arrays.asList(PART_NAMES).indexOf(name);
									if (part < 0) {
										c.getSource().sendFailure(Component.literal("Unknown part: " + name));
										return 0;
									}
									Undead.breakPart(player, part, UndeadState.KIND_BURST);
									return 1;
								})))
				.then(Commands.literal("restore").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					Undead.restore(player);
					c.getSource().sendSuccess(() -> Component.literal("Every part regrown"), false);
					return 1;
				}));
		// (v0.17.3: "regen on|off" went with Toggle Regen: regrowth is always automatic)
	}
}
