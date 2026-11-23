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

	/** Within this many blocks of a tripped Gravebound grave, players catch the Crypt Curse. */
	public static final double CURSE_RADIUS = 6.0;

	/**
	 * v0.17.7: a Gravebound Curse trap (a cursed headstone on a tripwire) went off: everyone within
	 * {@link #CURSE_RADIUS} catches the short <b>Crypt Curse</b> -- Wither II (6 s), Darkness (8 s), Slowness II (6 s) and
	 * Hunger (10 s). Deliberately not the Gravebound Curse itself (that schedules a 12-wave Zombie Raid): the curse traps
	 * are a crawl hazard, not a world event. Returns how many players were cursed (test hook too).
	 */
	public static int curse(ServerLevel level, BlockPos at) {
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 40, 2.0, 1.0, 2.0, 0.05);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 1.5, 0.5, 1.5, 0.02);
		int n = 0;
		for (ServerPlayer p : HideoutClearing.playersNear(level, at, (int) CURSE_RADIUS)) {
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 120, 1));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 160, 0));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 200, 1));
			p.displayClientMessage(Component.translatable("projecthero.hideout.crypt_curse").withStyle(s -> s.withColor(HideoutFaction.GRAVE.colour())), false);
			n++;
		}
		return n;
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
			case GRAVE -> {
				level.playSound(null, alarm, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
				curse(level, alarm);
			}
			case CARNAGE -> level.playSound(null, alarm, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 2.0f, 0.4f);
			case OATH -> level.playSound(null, alarm, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(3).value(), SoundSource.HOSTILE, 3.0f, 0.8f);
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
