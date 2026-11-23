package com.projecthero.mod.alarm;

import com.projecthero.mod.alarm.block.AlarmPanelBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: sabotaging an Alarm Panel. Holding right-click on a block repeats the use every 4 ticks; each use adds the
 * ticks since the last one (a gap over {@link #GAP} starts again) until {@link #HOLD_TICKS} (3 s) -- then the panel
 * sparks and goes dead for good. Only while the zone is Calm: any suspicion (a guard's meter filling) or an Alert stops
 * it. Progress is per player and transient (in the zone).
 */
public final class AlarmSabotage {
	public static final int HOLD_TICKS = 60;
	public static final int GAP = 10;

	private AlarmSabotage() {
	}

	/** One use of the panel by {@code player}. Returns the progress in ticks after this use (-1 refused, HOLD_TICKS done). */
	public static int use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		if (state.getValue(AlarmPanelBlock.SABOTAGED)) {
			player.displayClientMessage(Component.translatable("block.projecthero.alarm_panel.dead").withStyle(ChatFormatting.GRAY), true);
			return -1;
		}
		AlarmZone z = AlarmZones.find(level, pos);
		if (z == null) {
			sabotage(level, pos, state, player);
			return HOLD_TICKS;
		}
		if (z.state != AlarmState.CALM) {
			z.sabotage.remove(player.getUUID());
			player.displayClientMessage(Component.translatable(z.state == AlarmState.ALERT ? "block.projecthero.alarm_panel.alert"
					: "block.projecthero.alarm_panel.suspicious").withStyle(ChatFormatting.RED), true);
			return -1;
		}
		long now = level.getGameTime();
		long[] s = z.sabotage.get(player.getUUID());
		if (s == null || s[0] != pos.asLong() || now - s[1] > GAP || now < s[1]) {
			s = new long[] { pos.asLong(), now, 0L };
			z.sabotage.put(player.getUUID(), s);
		} else {
			s[2] += now - s[1];
			s[1] = now;
		}
		int progress = (int) s[2];
		if (progress >= HOLD_TICKS) {
			z.sabotage.remove(player.getUUID());
			sabotage(level, pos, state, player);
			return HOLD_TICKS;
		}
		int bars = 10;
		int filled = progress * bars / HOLD_TICKS;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < bars; i++) {
			b.append(i < filled ? '|' : '.');
		}
		player.displayClientMessage(Component.translatable("block.projecthero.alarm_panel.sabotaging", b.toString()).withStyle(ChatFormatting.YELLOW), true);
		level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.6f + progress / 100f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.05);
		return progress;
	}

	/** Kills the panel now. */
	public static void sabotage(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		level.setBlock(pos, state.setValue(AlarmPanelBlock.SABOTAGED, true).setValue(AlarmPanelBlock.ALERT, false), 3);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 1.4f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.25);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
		if (player != null) {
			player.displayClientMessage(Component.translatable("block.projecthero.alarm_panel.sabotaged").withStyle(ChatFormatting.GREEN), true);
		}
	}
}
