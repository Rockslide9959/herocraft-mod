package com.projecthero.mod.frostgiant;

import com.projecthero.mod.frostgiant.item.FrostGiantItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

/**
 * v0.16.1: the Frost Giant Assault's optional natural trigger ({@link FrostGiantConfig.Trigger#naturalTriggerEnabled}, <b>off
 * by default</b>). Once a minute, every player who is Thor-worthy or holds Mjolnir / Stormbreaker, standing in a snowy
 * biome, at night, during a thunderstorm, rolls {@link FrostGiantConfig.Trigger#chancePerMinute}. On a hit Heimdall speaks
 * from the Bifrost -- "The Jotnar march on Midgard." -- and a Jotunheim Rune Stone appears in their inventory: placing it
 * is up to them. Stateless (the roll is the only state), so nothing to reset.
 */
public final class FrostGiantSpawner {
	private FrostGiantSpawner() {
	}

	public static void tick(MinecraftServer server) {
		FrostGiantConfig.Trigger cfg = FrostGiantConfig.trigger();
		if (!cfg.naturalTriggerEnabled || server.getTickCount() % 1200 != 600) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.serverLevel().getDifficulty() == Difficulty.PEACEFUL || !eligible(p)) {
				continue;
			}
			if (p.getRandom().nextDouble() < Math.max(0.0, cfg.chancePerMinute)) {
				deliver(p);
			}
		}
	}

	/** Worthy or carrying a Thor weapon, in a snowy biome, at night, in a thunderstorm. */
	public static boolean eligible(ServerPlayer p) {
		if (!Worthiness.isWorthy(p) && !ThorPowers.isHoldingThorWeapon(p)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		return level.isThundering() && level.isNight() && isSnowy(level, p);
	}

	private static boolean isSnowy(ServerLevel level, ServerPlayer p) {
		Biome biome = level.getBiome(p.blockPosition()).value();
		return biome.getPrecipitationAt(p.blockPosition()) == Biome.Precipitation.SNOW;
	}

	/** Heimdall's warning and the Rune Stone in the inventory (or at the player's feet). */
	public static void deliver(ServerPlayer p) {
		FrostGiantDialogue.heimdall(p, "march");
		p.sendSystemMessage(Component.translatable("message.projecthero.frost_giants.natural").withStyle(ChatFormatting.AQUA));
		ItemStack stone = new ItemStack(FrostGiantItems.RUNE_STONE);
		if (!p.getInventory().add(stone)) {
			p.drop(stone, false);
		}
	}
}
