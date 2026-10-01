package com.projecthero.mod.grave;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * v0.14.13: the Gravebound Curse shown as a status effect (icon + countdown in the inventory and the top-right effect
 * row) instead of the old HUD timer block. It is purely a display: the curse itself still lives in the persistent
 * {@link GraveboundState} attachment, which is what survives milk, death and relogging. {@link #sync} keeps the effect
 * matching that state -- put back if milk (or anything else) strips it, its duration kept to the real time left, and
 * removed once the curse is gone.
 */
public final class GraveboundEffect extends MobEffect {
	public static final Holder<MobEffect> HOLDER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ProjectHeroMod.id("gravebound"), new GraveboundEffect());

	/** The shown duration may drift this far (ticks) from the real time left before it is re-sent. */
	private static final int DRIFT = 40;

	private GraveboundEffect() {
		super(MobEffectCategory.HARMFUL, 0x7A3FA8);
	}

	/** Class-load hook (registers the effect). */
	public static void initialize() {
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return false;
	}

	/** Makes the shown effect match the curse: {@code ticksLeft <= 0} removes it. */
	public static void sync(ServerPlayer player, int ticksLeft) {
		MobEffectInstance cur = player.getEffect(HOLDER);
		if (ticksLeft <= 0) {
			if (cur != null) {
				player.removeEffect(HOLDER);
			}
			return;
		}
		if (cur != null && Math.abs(cur.getDuration() - ticksLeft) <= DRIFT) {
			return;
		}
		if (cur != null) {
			player.removeEffect(HOLDER);
		}
		player.addEffect(new MobEffectInstance(HOLDER, ticksLeft, 0, false, false, true));
	}
}
