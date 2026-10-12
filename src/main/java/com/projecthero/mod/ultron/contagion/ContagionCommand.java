package com.projecthero.mod.ultron.contagion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.1: {@code /projecthero contagion ...} (op only), merged into the {@code /projecthero} root:
 * <ul>
 *   <li>{@code start} -- begin now (patient zero next tick); {@code stop} -- end everything, revert every recorded block,
 *       remove every citadel block and robot; {@code status};</li>
 *   <li>{@code saturation <0-100>}; {@code trace} -- hand JARVIS one Trace (three start the Sokovia Protocol);</li>
 *   <li>{@code nexus} -- plant a grown Nexus 12 blocks in front of you; {@code finale} -- mark the Sokovia site where you
 *       stand (the citadel goes up above you); {@code resurge} -- Ultron comes back as the next version.</li>
 * </ul>
 */
public final class ContagionCommand {
	private ContagionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("projecthero").requires(s -> s.hasPermission(2))
				.then(Commands.literal("contagion")
						.then(Commands.literal("start").executes(c -> {
							Contagion.startNow(overworld(c));
							return ok(c, "The Ultron Contagion begins.");
						}))
						.then(Commands.literal("stop").executes(c -> {
							Contagion.stop(overworld(c));
							return ok(c, "The Ultron Contagion was stopped; every recorded block is being put back.");
						}))
						.then(Commands.literal("status").executes(ContagionCommand::status))
						.then(Commands.literal("saturation").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
							ContagionState st = ContagionState.get(overworld(c));
							st.saturation = IntegerArgumentType.getInteger(c, "percent");
							st.setDirty();
							return ok(c, "Saturation set to " + (int) st.saturation + "%.");
						})))
						.then(Commands.literal("trace").executes(c -> {
							ContagionState st = ContagionState.get(overworld(c));
							st.traces = Math.min(3, st.traces + 1);
							st.setDirty();
							return ok(c, "Traces: " + st.traces + " / 3.");
						}))
						.then(Commands.literal("nexus").executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ServerLevel level = overworld(c);
							ContagionState st = ContagionState.get(level);
							if (st.stage != ContagionState.Stage.ACTIVE && st.stage != ContagionState.Stage.FINALE) {
								st.stage = ContagionState.Stage.ACTIVE;
								st.setDirty();
							}
							BlockPos at = Contagion.groundAt(level, BlockPos.containing(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(12))));
							if (at == null) {
								c.getSource().sendFailure(Component.literal("No dry ground there."));
								return 0;
							}
							UltronNexusEntity n = Contagion.spawnNexus(level, st, at);
							if (n != null) {
								n.finishGrowing();
							}
							return ok(c, "Ultron Nexus planted at " + at.toShortString() + ".");
						}))
						.then(Commands.literal("finale").executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ServerLevel level = overworld(c);
							ContagionState st = ContagionState.get(level);
							if (st.finale != null) {
								c.getSource().sendFailure(Component.literal("The Sokovia Protocol is already running."));
								return 0;
							}
							st.traces = 3;
							SokoviaFinale.begin(level, st, true);
							st.finale.site = new BlockPos(p.getBlockX(), 0, p.getBlockZ());
							return ok(c, "Sokovia Protocol marked where you stand.");
						}))
						.then(Commands.literal("resurge").executes(c -> {
							ServerLevel level = overworld(c);
							ContagionState st = ContagionState.get(level);
							Contagion.resurge(level, st);
							return ok(c, "Ultron resurfaces as version " + st.version + ".");
						}))));
	}

	private static ServerLevel overworld(CommandContext<CommandSourceStack> c) {
		return c.getSource().getServer().overworld();
	}

	private static int ok(CommandContext<CommandSourceStack> c, String msg) {
		c.getSource().sendSuccess(() -> Component.literal(msg), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		ContagionState st = ContagionState.get(level);
		long now = level.getGameTime();
		StringBuilder b = new StringBuilder();
		b.append("Contagion: ").append(st.stage).append(", Ultron v").append(st.version).append(st.omegaBeaten ? " (Omega beaten)" : "")
				.append("\nSaturation ").append(String.format(java.util.Locale.ROOT, "%.1f", st.saturation)).append("%, Traces ")
				.append(st.traces).append("/3, Nexus ").append(st.nexus.size()).append(", recorded blocks ").append(st.totalRecorded())
				.append(", fuelled Firewall Beacons ").append(st.fuelledBeacons(now)).append(", compromised machines ").append(st.compromised.size());
		if (st.stage == ContagionState.Stage.SCHEDULED) {
			b.append("\nStarts in ").append(Math.max(0, (st.startAt - now) / 20)).append(" s");
		}
		if (st.finale != null) {
			b.append("\nSokovia Protocol: ").append(st.finale.phase).append(" at ").append(st.finale.site.getX()).append(", ").append(st.finale.site.getZ())
					.append(", Core ").append((int) (st.finale.charge * 100)).append("%, citadel blocks ").append(st.finale.placed.size());
		}
		String msg = b.toString();
		c.getSource().sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}
}
