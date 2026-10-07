package com.projecthero.mod.ultron;

import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.item.UltronItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.12: the Ultron Uprising's optional natural trigger ({@link UltronConfig.Trigger#naturalTriggerEnabled}, <b>off
 * by default</b>). Once a minute, every Tony Stark who has built {@link UltronConfig.Trigger#minimumMarks}+ Iron Man
 * marks -- Marks 2 and up are Fabricator-built, so this also means they own a Stark Fabricator -- rolls
 * {@code dailyChance / 20} (an in-game day is 20 minutes). On a hit JARVIS warns of "an unauthorised process in the
 * Fabricator network" and an Ultron Beacon turns up in their inventory: placing it is up to them. Stateless (the roll is
 * the only state), so nothing to reset.
 */
public final class UltronSpawner {
	private UltronSpawner() {
	}

	public static void tick(MinecraftServer server) {
		UltronConfig.Trigger cfg = UltronConfig.trigger();
		if (!cfg.naturalTriggerEnabled || server.getTickCount() % 1200 != 0) {
			return;
		}
		double chance = Math.max(0.0, cfg.dailyChance) / 20.0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.serverLevel().getDifficulty() == Difficulty.PEACEFUL || !eligible(p)) {
				continue;
			}
			if (p.getRandom().nextDouble() < chance) {
				deliver(p);
			}
		}
	}

	/** A Tony Stark with enough marks built. */
	public static boolean eligible(ServerPlayer p) {
		return TonyStark.hasPower(p) && TonyStark.builtSuitIds(p).size() >= UltronConfig.trigger().minimumMarks;
	}

	/** JARVIS's warning and the beacon in the inventory (or at the player's feet). */
	public static void deliver(ServerPlayer p) {
		JarvisDialogue.speak(p, "ultron_process");
		p.sendSystemMessage(Component.translatable("message.projecthero.ultron.natural").withStyle(ChatFormatting.RED));
		ItemStack beacon = new ItemStack(UltronItems.ULTRON_BEACON);
		if (!p.getInventory().add(beacon)) {
			p.drop(beacon, false);
		}
	}
}
