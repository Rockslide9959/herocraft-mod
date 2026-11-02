package com.projecthero.mod.client.miraculous.queenbee;

import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.miraculous.queenbee.QueenBee;
import com.projecthero.mod.miraculous.queenbee.QueenBeeConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * v0.17.2: Queen Bee's Hive Sense (V, a toggle). On HER client only, every hostile mob within
 * {@link QueenBeeConfig#HIVE_RANGE} glows gold through walls (asked by the glow mixins that also serve Viperion's Heat
 * Sense; only client-side entities are answered, see {@link IronManHighlight#mayDecide}).
 */
public final class QueenBeeHiveSense {
	public static final int COLOR = 0xFFC81E;

	private QueenBeeHiveSense() {
	}

	public static boolean outlined(Entity entity) {
		if (!(entity instanceof LivingEntity living) || !(entity instanceof Enemy) || !IronManHighlight.mayDecide(entity)) {
			return false;
		}
		LocalPlayer me = Minecraft.getInstance().player;
		if (me == null || entity == me || !living.isAlive() || !QueenBee.hiveSense(me)) {
			return false;
		}
		double r = QueenBeeConfig.HIVE_RANGE;
		return entity.distanceToSqr(me) <= r * r;
	}
}
