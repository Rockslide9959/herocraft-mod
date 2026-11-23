package com.projecthero.mod.alarm;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * v0.17.7: the <b>Ghost bonus</b>. Opening a Hero Vault inside an alarm zone that has <i>never</i> gone Alert gives that
 * player one extra roll on the vault's table (once per player per place) and the {@code projecthero:ghost} advancement.
 * Hooked from {@code HeroVault.open}; any vault placed in a zone gets it for free.
 */
public final class AlarmGhost {
	public static final String ADVANCEMENT = "ghost";

	private AlarmGhost() {
	}

	/** True (and remembered) when {@code player} earns the Ghost bonus for the vault at {@code pos}. */
	public static boolean claim(ServerLevel level, BlockPos pos, ServerPlayer player) {
		AlarmZone z = AlarmZones.find(level, pos);
		if (z == null || z.everAlert || z.state == AlarmState.ALERT) {
			return false;
		}
		// v0.17.7: an unguarded zone (a place generated before it had an alarm: no panels at all) gives no Ghost bonus
		AlarmZones.scan(level, z);
		if (z.panels.isEmpty() || !z.ghosts.add(player.getUUID())) {
			return false;
		}
		AlarmZones.get(level).changed();
		award(player);
		player.displayClientMessage(Component.translatable("projecthero.alarm.ghost").withStyle(s -> s.withColor(0xB8F2FF).withBold(true)), false);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
		return true;
	}

	public static void award(ServerPlayer player) {
		if (player.getServer() == null) {
			return;
		}
		AdvancementHolder holder = player.getServer().getAdvancements().get(ProjectHeroMod.id(ADVANCEMENT));
		if (holder != null) {
			player.getAdvancements().award(holder, "code_trigger");
		}
	}
}
