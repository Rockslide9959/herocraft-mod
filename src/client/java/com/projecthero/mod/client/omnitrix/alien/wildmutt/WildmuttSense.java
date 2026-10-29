package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * v0.17.1: Wildmutt's Scent Sense, on HIS client only (the v0.15.4 privacy rule: only client-side entities are answered, see
 * {@link IronManHighlight#mayDecide}). He has no eyes: every creature within {@link WildmuttKit#senseRange} is outlined in a
 * warm amber -- invisible ones too (vanilla draws an invisible creature's outline when it glows) -- dropped items as well
 * while he sniffs, and the creature he tracks in red at any distance.
 * <p>v0.17.1 (user: "make his smelling power better"): he tells scents apart -- monsters smell red-orange, animals green, players
 * pale gold, anything badly hurt of blood (deep red), the rest the old amber.
 */
public final class WildmuttSense {
	public static final int COLOUR = 0xFFB060;
	public static final int ITEM_COLOUR = 0xFFE8A0;
	public static final int TRACK_COLOUR = 0xFF2A1A;
	public static final int MONSTER_COLOUR = 0xFF6A30;
	public static final int ANIMAL_COLOUR = 0x8EE05A;
	public static final int PLAYER_COLOUR = 0xFFE9A8;
	public static final int BLOOD_COLOUR = 0xB0102A;
	/** Below this share of its health a creature smells of blood. */
	public static final float BLOOD_BELOW = 0.35f;

	private WildmuttSense() {
	}

	/** The wearer's Wildmutt kit, or null when this client is not Wildmutt. */
	private static WildmuttKit me(LocalPlayer me) {
		return me != null && AlienKit.activeKit(me) instanceof WildmuttKit k ? k : null;
	}

	public static boolean outlined(Entity entity) {
		return colour(entity) != 0;
	}

	/** The outline colour for {@code entity}, or 0 for none. */
	public static int colour(Entity entity) {
		if (entity == null || !IronManHighlight.mayDecide(entity)) {
			return 0;
		}
		LocalPlayer me = Minecraft.getInstance().player;
		WildmuttKit k = me(me);
		if (k == null || entity == me) {
			return 0;
		}
		Entity tracked = k.tracked(me);
		if (tracked != null && tracked == entity) {
			return TRACK_COLOUR;
		}
		double r = k.senseRange(me);
		if (r <= 0 || entity.distanceToSqr(me) > r * r) {
			return 0;
		}
		if (entity instanceof ItemEntity) {
			return k.flag(me, WildmuttKit.F_SNIFF) ? ITEM_COLOUR : 0;
		}
		if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !living.isAlive()) {
			return 0;
		}
		if (living.getHealth() < living.getMaxHealth() * BLOOD_BELOW) {
			return BLOOD_COLOUR;
		}
		if (entity instanceof net.minecraft.world.entity.monster.Enemy) {
			return MONSTER_COLOUR;
		}
		if (entity instanceof net.minecraft.world.entity.player.Player) {
			return PLAYER_COLOUR;
		}
		if (entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.animal.WaterAnimal) {
			return ANIMAL_COLOUR;
		}
		return COLOUR;
	}
}
