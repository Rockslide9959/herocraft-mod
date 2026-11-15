package com.projecthero.mod.gear;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 Tier IV set bonus: all four Celestial pieces worn = +{@link #BONUS_HEALTH} max health (2 hearts) and immunity
 * to Blindness and Darkness ({@code mixin/CelestialSightMixin} refuses the effects; {@link #update} also clears any that
 * were already on when the set was completed). Checked every {@link #INTERVAL} ticks per player; nothing is stored
 * outside the player's own attribute map.
 */
public final class CelestialSet {
	public static final double BONUS_HEALTH = 4.0;
	public static final int INTERVAL = 10;
	public static final ResourceLocation HEALTH_ID = ProjectHeroMod.id("celestial_set_health");

	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private CelestialSet() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				update(p);
			}
		});
	}

	public static boolean isWorn(LivingEntity entity) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack s = entity.getItemBySlot(slot);
			if (!(s.getItem() instanceof GearArmorItem g) || g.gearTier() < 4) {
				return false;
			}
		}
		return true;
	}

	/** Adds or removes the bonus to match what is worn. Returns whether the bonus is now active. */
	public static boolean update(LivingEntity entity) {
		boolean full = isWorn(entity);
		AttributeInstance hp = entity.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			boolean has = hp.hasModifier(HEALTH_ID);
			if (full && !has) {
				hp.addTransientModifier(new AttributeModifier(HEALTH_ID, BONUS_HEALTH, AttributeModifier.Operation.ADD_VALUE));
			} else if (!full && has) {
				hp.removeModifier(HEALTH_ID);
				if (entity.getHealth() > entity.getMaxHealth()) {
					entity.setHealth(entity.getMaxHealth());
				}
			}
		}
		if (full) {
			if (entity.hasEffect(MobEffects.BLINDNESS)) {
				entity.removeEffect(MobEffects.BLINDNESS);
			}
			if (entity.hasEffect(MobEffects.DARKNESS)) {
				entity.removeEffect(MobEffects.DARKNESS);
			}
		}
		return full;
	}
}
