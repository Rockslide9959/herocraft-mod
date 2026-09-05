package com.herocraft.mod.ironman;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.attachment.ModAttachments;
// IronManArmor, IronManEnergy, TonyStark are in this same package.

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * "changes 19": the Mark 5's gauntlet blades (its slot-3 / X toggle, replacing Flare). Pressing X
 * extends energy blades from both gauntlets; pressing X again retracts them.
 *
 * <ul>
 *   <li>+4 melee (`ATTACK_DAMAGE`) via a fixed-id transient modifier while extended;</li>
 *   <li>you cannot place blocks while the blades are out ({@code HeroCraftMod}'s
 *       {@code UseBlockCallback} vetoes a {@code BlockItem} use);</li>
 *   <li>bright blade FX from both gauntlets each render tick.</li>
 * </ul>
 *
 * {@code IRON_MAN_BLADES} is a synced-to-everyone, non-persistent boolean attachment.
 */
public final class IronManBlade {
	public static final float MELEE_BONUS = 4.0f;
	private static final ResourceLocation ATK_ID = HeroCraftMod.id("iron_man_blade_strength");

	private IronManBlade() {
	}

	public static boolean active(Player player) {
		return player.getAttachedOrElse(ModAttachments.IRON_MAN_BLADES, false);
	}

	/** Slot 3 (X) on the Mark 5: toggle the blades. */
	public static void toggle(ServerPlayer player) {
		boolean out = !active(player);
		player.setAttached(ModAttachments.IRON_MAN_BLADES, out);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					out ? SoundEvents.NETHERITE_BLOCK_HIT : SoundEvents.NETHERITE_BLOCK_PLACE,
					SoundSource.PLAYERS, 0.9f, out ? 1.7f : 1.1f);
		}
		player.displayClientMessage(Component.translatable(out
				? "message.herocraft.ironman.blades_out" : "message.herocraft.ironman.blades_in"), true);
	}

	public static void retract(ServerPlayer player) {
		if (active(player)) {
			player.setAttached(ModAttachments.IRON_MAN_BLADES, false);
		}
	}

	/**
	 * Per-tick from {@link IronManSuitTicker}: reconcile the +melee modifier, retract the blades if the
	 * Mark 5 is no longer the worn/powered suit, and draw the blade FX.
	 */
	public static void tick(ServerPlayer player) {
		boolean want = active(player) && "mark_v".equals(IronManArmor.wornSuitId(player))
				&& IronManArmor.canOperate(player)
				&& IronManEnergy.energy(player, "mark_v") > 0f
				&& !TonyStark.overloaded(player);
		AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			AttributeModifier cur = atk.getModifier(ATK_ID);
			if (want && cur == null) {
				atk.addOrUpdateTransientModifier(
						new AttributeModifier(ATK_ID, MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE));
			} else if (!want && cur != null) {
				atk.removeModifier(ATK_ID);
			}
		}
		if (!want) {
			retract(player);
			return;
		}
		if (player.tickCount % 2 != 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		}
		right = right.normalize();
		Vec3 eye = player.getEyePosition();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 base = eye.add(right.scale(0.42 * side)).add(0, -0.45, 0).add(look.scale(0.2));
			for (double d = 0.0; d <= 1.3; d += 0.32) {
				Vec3 p = base.add(look.scale(d));
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
			}
			Vec3 tip = base.add(look.scale(1.35));
			level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.01, 0.01, 0.01, 0.0);
		}
	}
}
