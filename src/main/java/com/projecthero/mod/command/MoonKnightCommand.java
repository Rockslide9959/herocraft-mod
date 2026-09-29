package com.projecthero.mod.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * v0.13.19: Moon Knight's op-only debug / test commands, exactly as the user asked for them:
 * <pre>
 *   /moonknight grant [players]            the pact with Khonshu (Vengeance 50, or 100 under a full moon)
 *   /moonknight revoke [players]
 *   /moonknight vengeance &lt;0-100&gt; [players]
 *   /moonknight locate_temple              the nearest Temple of Khonshu (Phase 7)
 *   /moonknight moon &lt;phase 0-7&gt; [day|night]  jump the world clock to that moon phase (0 = full, 4 = new)
 *   /moonknight suit &lt;on|off&gt;              Phase 1 only: flip "transformed" without the suit, to test the HUD
 *   /moonknight status [player]
 * </pre>
 * The permanent admin path is still {@code /projecthero power grant moon_knight}.
 */
public final class MoonKnightCommand {
	private static final ResourceKey<Structure> TEMPLE = ResourceKey.create(Registries.STRUCTURE,
			com.projecthero.mod.ProjectHeroMod.id("temple_of_khonshu"));

	private MoonKnightCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("moonknight")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("grant")
						.executes(c -> grant(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> grant(c, EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("revoke")
						.executes(c -> revoke(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> revoke(c, EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("vengeance")
						.then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f, MoonKnightConfig.VENGEANCE_MAX))
								.executes(c -> vengeance(c, List.of(c.getSource().getPlayerOrException())))
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(c -> vengeance(c, EntityArgument.getPlayers(c, "targets"))))))
				.then(Commands.literal("locate_temple").executes(MoonKnightCommand::locateTemple))
				.then(Commands.literal("moon")
						.then(Commands.argument("phase", IntegerArgumentType.integer(0, 7))
								.executes(c -> moon(c, true))
								.then(Commands.literal("night").executes(c -> moon(c, true)))
								.then(Commands.literal("day").executes(c -> moon(c, false)))))
				.then(Commands.literal("suit")
						.then(Commands.literal("on").executes(c -> suit(c, true)))
						.then(Commands.literal("off").executes(c -> suit(c, false))))
				.then(Commands.literal("status")
						.executes(c -> status(c, c.getSource().getPlayerOrException()))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(c -> status(c, EntityArgument.getPlayer(c, "target")))));
	}

	private static int grant(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		int n = 0;
		for (ServerPlayer p : targets) {
			String name = p.getGameProfile().getName();
			if (MoonKnight.grant(p)) {
				n++;
				c.getSource().sendSuccess(() -> Component.literal("Sealed " + name + "'s pact with Khonshu"), true);
			} else {
				c.getSource().sendFailure(Component.literal(name + " already serves Khonshu"));
			}
		}
		return n;
	}

	private static int revoke(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		int n = 0;
		for (ServerPlayer p : targets) {
			if (MoonKnight.hasPower(p)) {
				MoonKnight.revoke(p);
				n++;
				String name = p.getGameProfile().getName();
				c.getSource().sendSuccess(() -> Component.literal("Released " + name + " from Khonshu's service"), true);
			}
		}
		return n;
	}

	private static int vengeance(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
		float amount = FloatArgumentType.getFloat(c, "amount");
		int n = 0;
		for (ServerPlayer p : targets) {
			if (!MoonKnight.hasPower(p)) {
				c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " has no pact with Khonshu"));
				continue;
			}
			MoonKnight.setVengeance(p, amount);
			n++;
		}
		int done = n;
		c.getSource().sendSuccess(() -> Component.literal("Vengeance set to " + Math.round(amount) + " for " + done + " player(s)"), true);
		return n;
	}

	private static int locateTemple(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		var holder = registry.getHolder(TEMPLE);
		if (holder.isEmpty()) {
			c.getSource().sendFailure(Component.literal("The Temple of Khonshu has not been built yet (Moon Knight Phase 7)"));
			return 0;
		}
		BlockPos from = BlockPos.containing(c.getSource().getPosition());
		HolderSet<Structure> set = HolderSet.direct((Holder<Structure>) holder.get());
		var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, from, 200, false);
		if (found == null) {
			c.getSource().sendFailure(Component.literal("No Temple of Khonshu within range (they only rise in deserts)"));
			return 0;
		}
		BlockPos pos = found.getFirst();
		int dist = (int) Math.sqrt(from.distSqr(new BlockPos(pos.getX(), from.getY(), pos.getZ())));
		c.getSource().sendSuccess(() -> Component.literal("Temple of Khonshu at " + pos.getX() + ", ~, " + pos.getZ()
				+ " (" + dist + " blocks away)"), false);
		return 1;
	}

	/** Jump every world's clock to moon phase {@code phase} (0 full .. 4 new), at midnight or at noon. */
	private static int moon(CommandContext<CommandSourceStack> c, boolean night) {
		int phase = IntegerArgumentType.getInteger(c, "phase");
		ServerLevel overworld = c.getSource().getServer().overworld();
		long cycleStart = Math.floorDiv(overworld.getDayTime(), MoonKnightConfig.MOON_CYCLE_TICKS) * MoonKnightConfig.MOON_CYCLE_TICKS;
		long time = cycleStart + phase * MoonKnightConfig.DAY_TICKS + (night ? 18000L : 6000L);
		for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
			level.setDayTime(time);
		}
		float power = MoonKnightLunar.power(overworld, BlockPos.containing(c.getSource().getPosition()));
		c.getSource().sendSuccess(() -> Component.literal("Moon phase " + phase + (night ? " (night)" : " (day)")
				+ " -- lunar power here x" + String.format(java.util.Locale.ROOT, "%.2f", power)), true);
		return 1;
	}

	private static int suit(CommandContext<CommandSourceStack> c, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!MoonKnight.setTransformedForTesting(p, on)) {
			c.getSource().sendFailure(Component.literal("No pact with Khonshu -- /moonknight grant first"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Moon Knight transformed flag " + (on ? "ON" : "OFF")
				+ " (Phase 1 test hook -- the real H transformation arrives in Phase 2)"), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		MoonKnightState s = MoonKnight.state(p);
		if (!s.hasPact) {
			c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " has no pact with Khonshu"));
			return 0;
		}
		long now = p.level().getGameTime();
		String text = String.format(java.util.Locale.ROOT,
				"%s: transformed=%s alter=%s vengeance=%.1f resurrection=%s lunar=x%.2f (moon phase %d, %s, sky %s) fractured=%s idle=%ds",
				p.getGameProfile().getName(), s.transformed, MoonKnightAlter.byOrdinal(s.alter).id(), s.vengeance,
				s.resurrectionCharged ? "charged" : "used", MoonKnightLunar.power(p), p.level().getMoonPhase(),
				MoonKnightLunar.isMoonNight(p.level()) ? "night" : "day",
				MoonKnightLunar.hasSky(p.level(), BlockPos.containing(p.getEyePosition())) ? "open" : "blocked",
				s.fractureUntil > now, (now - s.lastHostileKill) / 20);
		c.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
