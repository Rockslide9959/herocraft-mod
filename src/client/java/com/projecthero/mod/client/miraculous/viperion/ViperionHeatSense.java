package com.projecthero.mod.client.miraculous.viperion;

import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.miraculous.viperion.Viperion;
import com.projecthero.mod.miraculous.viperion.ViperionConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Viperion's Heat Sense (C, a toggle) -- snake infrared. On HIS client only, every living thing within
 * {@link ViperionConfig#HEAT_RANGE} glows through walls in warm colours (the glow mixins ask {@link #outlined} and
 * {@link #color}; only client-side entities are answered, see {@link IronManHighlight#mayDecide}): hostile mobs red,
 * players yellow, everything else orange.
 */
public final class ViperionHeatSense {
	public static final int HOSTILE = 0xFF3A1E;
	public static final int PLAYER = 0xFFD23A;
	public static final int OTHER = 0xFF8A1E;

	private ViperionHeatSense() {
	}

	public static boolean outlined(Entity entity) {
		if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !IronManHighlight.mayDecide(entity)) {
			return false;
		}
		LocalPlayer me = Minecraft.getInstance().player;
		if (me == null || entity == me || !living.isAlive() || !Viperion.heatSense(me)) {
			return false;
		}
		double r = ViperionConfig.HEAT_RANGE;
		return entity.distanceToSqr(me) <= r * r;
	}

	public static int color(Entity entity) {
		if (entity instanceof Enemy) {
			return HOSTILE;
		}
		return entity instanceof Player ? PLAYER : OTHER;
	}
}
