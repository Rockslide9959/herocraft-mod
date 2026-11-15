package com.projecthero.mod.hideout;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * v0.17.5: a hideout alarm went off (its tripwire snapped): a siren / horn sounds and a squad of the faction's guards
 * arrives near it -- through a Boom Tube for Apokolips, out of burst egg pods for the Symbiote Hive. Squad size is
 * {@link #BASE_SQUAD} plus one per extra player within 24 blocks (cap 6).
 */
public final class HideoutAlarms {
	public static final int BASE_SQUAD = 3;

	private HideoutAlarms() {
	}

	public static void trigger(ServerLevel level, BlockPos alarm, HideoutFaction faction) {
		var players = HideoutClearing.playersNear(level, alarm, 24);
		int squad = Math.min(6, BASE_SQUAD + Math.max(0, players.size() - 1));
		switch (faction) {
			case KINGPIN -> level.playSound(null, alarm, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5f, 1.4f);
			case ULTRON -> level.playSound(null, alarm, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.5f, 0.5f);
			case APOKOLIPS -> level.playSound(null, alarm, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.6f);
			case SYMBIOTE -> level.playSound(null, alarm, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 2.0f, 0.5f);
			case FROST -> level.playSound(null, alarm, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), SoundSource.HOSTILE, 3.0f, 0.7f);
		}
		Component msg = Component.translatable("projecthero.hideout.alarm." + faction.getSerializedName()).withStyle(s -> s.withColor(faction.colour()));
		for (ServerPlayer p : players) {
			p.displayClientMessage(msg, true);
		}
		for (int i = 0; i < squad; i++) {
			BlockPos spot = HideoutGuards.findSpot(level, alarm, 2, 6, level.random);
			if (spot == null) {
				continue;
			}
			Mob m = HideoutGuards.reinforcement(level, faction, spot, level.random);
			if (m != null && !players.isEmpty()) {
				m.setTarget(players.get(level.random.nextInt(players.size())));
			}
		}
	}
}
