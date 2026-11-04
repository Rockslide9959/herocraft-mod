package com.projecthero.mod.omnitrix;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.2 (user: "add the power tune command for the omnitrix power so that we can edit master control and recalibration and timers
 * and stuff please") -- admin / test tools (op only) on your own watch, under {@code /projecthero power tune omnitrix}:
 * <ul>
 *   <li>{@code status} -- everything below, read back;</li>
 *   <li>{@code master on|off} -- Master Control at once (no hold, no dial sequence);</li>
 *   <li>{@code recalibrated on|off} -- the Recalibrated Omnitrix or the prototype (not while transformed);</li>
 *   <li>{@code recal_mode <seconds>} -- recalibration mode with this long left (started if it isn't running; 0 ends it now);</li>
 *   <li>{@code transforms <count>} -- prototype transformations made (the Recalibration Chip needs 25);</li>
 *   <li>{@code time <seconds>} -- the alien time left (transformed), or the stored battery (a human on the Recalibrated Omnitrix);</li>
 *   <li>{@code recharge <seconds>} -- the recharge (0 = ready now);</li>
 *   <li>{@code failsafe <seconds>} -- the failsafe offline this long (0 = armed now);</li>
 *   <li>{@code glitch on|off} -- the prototype's chance to mistransform.</li>
 * </ul>
 */
public final class OmnitrixCommand {
	private OmnitrixCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("omnitrix")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("status").executes(OmnitrixCommand::status))
				.then(onOff("master", (p, on) -> {
					Omnitrix.setMasterControl(p, on);
					return "Master Control " + (on ? "on" : "off");
				}))
				.then(onOff("recalibrated", (p, on) -> {
					if (on ? !Omnitrix.recalibrate(p) : !Omnitrix.unrecalibrate(p)) {
						return null;
					}
					return on ? "Now the Recalibrated Omnitrix" : "Now the prototype Omnitrix";
				}))
				// v0.17.2: the aliens found only by a mistransform (Cannonbolt) -- on the dial now, or hidden again
				.then(onOff("found", (p, on) -> {
					for (String id : new String[] { com.projecthero.mod.omnitrix.alien.Aliens.CANNONBOLT.id() }) {
						com.projecthero.mod.omnitrix.alien.Aliens.discover(p, id, on);
					}
					return on ? "Cannonbolt is on your dial" : "Cannonbolt is hidden again (mistransform #" + OmnitrixConfig.UNLOCK_EVERY_MISTRANSFORMS + " finds him)";
				}))
				// v0.17.2: how many mistransforms so far (the 11th finds Cannonbolt)
				.then(Commands.literal("mistransforms")
						.then(Commands.argument("count", IntegerArgumentType.integer(0, 10_000)).executes(c -> {
							ServerPlayer p = wearer(c);
							if (p == null) {
								return 0;
							}
							int count = IntegerArgumentType.getInteger(c, "count");
							Omnitrix.setMistransforms(p, count);
							c.getSource().sendSuccess(() -> Component.literal("Mistransforms so far: " + count), false);
							return 1;
						})))
				.then(onOff("glitch", (p, on) -> {
					Omnitrix.setGlitches(p, on);
					return "Mistransforms " + (on ? "on" : "off");
				}))
				.then(seconds("recal_mode", 0, OmnitrixConfig.RECAL_MODE_TICKS / 20, (p, ticks) -> {
					long now = p.level().getGameTime();
					OmnitrixState n = Omnitrix.state(p).copy();
					if (Omnitrix.recalibrated(p) || n.transformed() || n.timers.containsKey(OmnitrixRecal.INSERT)
							|| n.timers.containsKey(OmnitrixRecal.FINISH)) {
						return null;
					}
					if (!n.timers.containsKey(OmnitrixRecal.END)) {
						n.timers.put(OmnitrixRecal.BEATS, 0L);
						n.selecting = false;
					}
					n.timers.put(OmnitrixRecal.END, now + ticks);
					Omnitrix.save(p, n);
					return ticks == 0 ? "Recalibration mode ends now" : "Recalibration mode: " + clock(ticks) + " left";
				}))
				.then(Commands.literal("transforms")
						.then(Commands.argument("count", IntegerArgumentType.integer(0, 10_000)).executes(c -> {
							ServerPlayer p = wearer(c);
							if (p == null) {
								return 0;
							}
							int count = IntegerArgumentType.getInteger(c, "count");
							Omnitrix.setPrototypeTransforms(p, count);
							c.getSource().sendSuccess(() -> Component.literal("Prototype transformations: " + count), false);
							return 1;
						})))
				.then(seconds("time", 1, 60 * 60, (p, ticks) -> Omnitrix.setTimeLeft(p, ticks)
						? (Omnitrix.transformed(p) ? "Alien time left: " : "Battery: ") + clock(ticks) : null))
				.then(seconds("recharge", 0, 60 * 60, (p, ticks) -> {
					Omnitrix.setRecharge(p, ticks);
					return ticks == 0 ? "Recharged" : "Recharge: " + clock(ticks);
				}))
				.then(seconds("failsafe", 0, 60 * 60, (p, ticks) -> {
					Omnitrix.setFailsafeIn(p, ticks);
					return ticks == 0 ? "Failsafe armed" : "Failsafe offline for " + clock(ticks);
				}));
	}

	private interface Toggle {
		/** The success line, or null when it could not be done. */
		String apply(ServerPlayer p, boolean on);
	}

	private interface Timed {
		String apply(ServerPlayer p, int ticks);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name, Toggle fn) {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);
		for (boolean on : new boolean[] { true, false }) {
			node.then(Commands.literal(on ? "on" : "off").executes(c -> done(c, p -> fn.apply(p, on))));
		}
		return node;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> seconds(String name, int min, int max, Timed fn) {
		return Commands.literal(name).then(Commands.argument("seconds", IntegerArgumentType.integer(min, max))
				.executes(c -> done(c, p -> fn.apply(p, IntegerArgumentType.getInteger(c, "seconds") * 20))));
	}

	private static int done(CommandContext<CommandSourceStack> c, java.util.function.Function<ServerPlayer, String> fn)
			throws CommandSyntaxException {
		ServerPlayer p = wearer(c);
		if (p == null) {
			return 0;
		}
		String line = fn.apply(p);
		if (line == null) {
			c.getSource().sendFailure(Component.literal("Can't do that with the watch as it is now (/projecthero power tune omnitrix status)"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static ServerPlayer wearer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!Omnitrix.hasPower(p)) {
			c.getSource().sendFailure(Component.literal("You are not wearing the Omnitrix"));
			return null;
		}
		return p;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = wearer(c);
		if (p == null) {
			return 0;
		}
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		boolean recal = Omnitrix.recalibrated(p);
		StringBuilder b = new StringBuilder("Omnitrix: ").append(recal ? "Recalibrated" : "prototype");
		b.append("\n Master Control: ").append(Omnitrix.masterControl(p) ? "on" : "off");
		b.append("\n Alien: ").append(s.transformed() ? s.active + " (" + clock(Omnitrix.timeLeft(p)) + " left)" : "none, selected " + s.selected);
		if (recal) {
			b.append("\n Battery: ").append(clock(Omnitrix.batteryTicks(p)));
		} else {
			b.append("\n Transformations: ").append(Omnitrix.prototypeTransforms(p)).append("/").append(OmnitrixConfig.RECAL_MIN_TRANSFORMS);
			b.append("\n Mistransforms: ").append(Omnitrix.glitches(p) ? "on" : "off").append(", ").append(Omnitrix.mistransforms(p)).append(" so far");
			com.projecthero.mod.omnitrix.alien.Aliens.Alien next = com.projecthero.mod.omnitrix.alien.Aliens.nextHidden(p);
			if (next != null) {
				int every = OmnitrixConfig.UNLOCK_EVERY_MISTRANSFORMS;
				b.append(" (").append(next.id()).append(" on #").append((Omnitrix.mistransforms(p) / every + 1) * every).append(")");
			}
		}
		if (OmnitrixRecal.busy(s)) {
			b.append("\n Recalibration mode: ").append(clock(OmnitrixRecal.modeLeft(s, now))).append(" left");
		}
		b.append("\n Recharge: ").append(Omnitrix.rechargeLeft(p) > 0 ? clock(Omnitrix.rechargeLeft(p)) : "ready");
		b.append("\n Failsafe: ").append(Omnitrix.failsafeLeft(p) > 0 ? "offline " + clock(Omnitrix.failsafeLeft(p)) : "armed");
		String text = b.toString();
		c.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
