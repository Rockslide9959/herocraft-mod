package com.projecthero.mod.client.miraculous.catnoir;

import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * v0.16.6: Cat Noir's Night Vision (C) -- cat eyes. The server gives the Night Vision effect; here, on HIS client only,
 * every creature within {@link CatNoirConfig#NIGHT_VISION_RANGE} is outlined Plagg green (the glow mixins ask
 * {@link #outlined}; only client-side entities are answered, see {@link IronManHighlight#mayDecide}).
 */
public final class CatNoirNightVision {
	public static final int COLOR = CatNoirConfig.NIGHT_VISION_COLOR;

	private CatNoirNightVision() {
	}

	public static boolean outlined(Entity entity) {
		if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !IronManHighlight.mayDecide(entity)) {
			return false;
		}
		LocalPlayer me = Minecraft.getInstance().player;
		if (me == null || entity == me || !living.isAlive() || !CatNoir.nightVision(me)) {
			return false;
		}
		double r = CatNoirConfig.NIGHT_VISION_RANGE;
		return entity.distanceToSqr(me) <= r * r;
	}
}
