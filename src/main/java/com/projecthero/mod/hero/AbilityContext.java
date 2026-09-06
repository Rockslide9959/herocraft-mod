package com.projecthero.mod.hero;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Everything an {@link AbilityHandler} needs for one activation, plus thin helpers over
 * {@link ExperimentalPowers} and {@link HeroConfig} so handlers stay short.
 */
public final class AbilityContext {
	private final ServerPlayer player;
	private final Power power;
	private final Ability ability;
	private final boolean pressed;

	public AbilityContext(ServerPlayer player, Power power, Ability ability, boolean pressed) {
		this.player = player;
		this.power = power;
		this.ability = ability;
		this.pressed = pressed;
	}

	public ServerPlayer player() {
		return player;
	}

	public ServerLevel level() {
		return (ServerLevel) player.level();
	}

	public Power power() {
		return power;
	}

	public Ability ability() {
		return ability;
	}

	/** true = key-down edge, false = key-up edge. */
	public boolean pressed() {
		return pressed;
	}

	public HeroConfig config() {
		return HeroConfig.get();
	}

	// ---- cooldown ----

	public boolean cooldownReady() {
		return ExperimentalPowers.cooldownReady(player, power, ability);
	}

	public int cooldownRemaining() {
		return ExperimentalPowers.cooldownRemainingTicks(player, power, ability);
	}

	/** Start this ability's own (config-scaled) base cooldown. Call only on a successful activation. */
	public void triggerCooldown() {
		ExperimentalPowers.triggerCooldown(player, power, ability, HeroConfig.get().scaledCooldown(ability.cooldownTicks()));
	}

	public void triggerCooldown(int baseTicks) {
		ExperimentalPowers.triggerCooldown(player, power, ability, HeroConfig.get().scaledCooldown(baseTicks));
	}

	// ---- toggle / cycle ----

	public boolean isToggled() {
		return ExperimentalPowers.isToggled(player, power, ability);
	}

	public void setToggled(boolean on) {
		ExperimentalPowers.setToggled(player, power, ability, on);
	}

	public int cycleMode() {
		return ExperimentalPowers.cycleMode(player, power, ability);
	}

	public void advanceCycle(int modeCount) {
		ExperimentalPowers.setCycleMode(player, power, ability, (cycleMode() + 1) % Math.max(1, modeCount));
	}

	// ---- resources ----

	public float resource(String name) {
		return ExperimentalPowers.getResource(player, power, name);
	}

	public void setResource(String name, float value, float max) {
		ExperimentalPowers.setResource(player, power, name, value, max);
	}

	public void addResource(String name, float delta, float max) {
		ExperimentalPowers.addResource(player, power, name, delta, max);
	}

	public boolean spendResource(String name, float amount) {
		return ExperimentalPowers.spendResource(player, power, name, amount);
	}

	// ---- feedback ----

	public void actionBar(String translationKey, Object... args) {
		player.displayClientMessage(Component.translatable(translationKey, args), true);
	}

	public void playSound(SoundEvent sound, float volume, float pitch) {
		level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}
}
