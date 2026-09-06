package com.projecthero.mod.hero.power;

import java.util.function.Consumer;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;

/** Small factories so per-power handler classes stay terse. */
public final class Handlers {
	private Handlers() {
	}

	/** INSTANT ability: handler should call {@code ctx.triggerCooldown()} on a successful activation. */
	public static AbilityHandler instant(Consumer<AbilityContext> onActivate) {
		return new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				onActivate.accept(ctx);
			}
		};
	}

	/** INSTANT ability that also needs per-tick upkeep (grab, marker). */
	public static AbilityHandler instantTicking(Consumer<AbilityContext> onActivate, Consumer<AbilityContext> onTick) {
		return new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				onActivate.accept(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				onTick.accept(ctx);
			}
		};
	}

	/** CHARGE ability: press starts charging (onStart), release fires (onRelease), onTick builds charge. */
	public static AbilityHandler charge(Consumer<AbilityContext> onStart, Consumer<AbilityContext> onTick,
			Consumer<AbilityContext> onRelease) {
		return new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				onStart.accept(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				onTick.accept(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				onRelease.accept(ctx);
			}
		};
	}

	/** HOLD / channel ability. */
	public static AbilityHandler hold(Consumer<AbilityContext> onStart, Consumer<AbilityContext> onEnd) {
		return new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				onStart.accept(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				onEnd.accept(ctx);
			}
		};
	}

	/** TOGGLE ability. {@code onTick} runs every server tick while active and must be idempotent. */
	public static AbilityHandler toggle(Consumer<AbilityContext> onOn, Consumer<AbilityContext> onOff,
			Consumer<AbilityContext> onTick) {
		return new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				onOn.accept(ctx);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				onOff.accept(ctx);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				onTick.accept(ctx);
			}
		};
	}

	public static AbilityHandler cycle(Consumer<AbilityContext> onCycle) {
		return new AbilityHandler() {
			@Override
			public void onCycle(AbilityContext ctx) {
				onCycle.accept(ctx);
			}
		};
	}

	private static final Consumer<AbilityContext> NOOP = c -> {
	};

	public static Consumer<AbilityContext> noop() {
		return NOOP;
	}
}
