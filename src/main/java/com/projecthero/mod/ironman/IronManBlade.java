package com.projecthero.mod.ironman;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
// IronManArmor, IronManEnergy, TonyStark are in this same package.

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

/**
 * "changes 19": the Mark 5's gauntlet blades (its slot-3 / X toggle, replacing Flare). Pressing X
 * extends energy blades from both gauntlets; pressing X again retracts them.
 *
 * <ul>
 *   <li>+4 melee (`ATTACK_DAMAGE`) via a fixed-id transient modifier while extended;</li>
 *   <li>you cannot place blocks while the blades are out ({@code ProjectHeroMod}'s
 *       {@code UseBlockCallback} vetoes a {@code BlockItem} use);</li>
 *   <li>v0.14.21 round two: real silver / red blades slide out of both gauntlets (client geometry, eased over
 *       {@link IronManBladeLook#EXTEND_TICKS} ticks) instead of the old particle FX.</li>
 * </ul>
 *
 * {@code IRON_MAN_BLADES} is a synced-to-everyone, non-persistent boolean attachment.
 */
public final class IronManBlade {
	public static final float MELEE_BONUS = 4.0f;
	private static final ResourceLocation ATK_ID = ProjectHeroMod.id("iron_man_blade_strength");

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
			if (out) { // v0.14.31: a metallic snikt + ring out, a servo-clank back in
				IronManSounds.move(player, IronManSounds.BLADE_EXTEND, 1.0f, 1.0f);
				IronManSounds.play(player, IronManSounds.BLADE_RING, 0.8f, 1.0f);
			} else {
				IronManSounds.move(player, IronManSounds.BLADE_RETRACT, 0.9f, 1.0f);
			}
		}
		player.displayClientMessage(Component.translatable(out
				? "message.projecthero.ironman.blades_out" : "message.projecthero.ironman.blades_in"), true);
	}

	public static void retract(ServerPlayer player) {
		if (active(player)) {
			player.setAttached(ModAttachments.IRON_MAN_BLADES, false);
		}
	}

	/**
	 * Per-tick from {@link IronManSuitTicker}: reconcile the +melee modifier, retract the blades if the
	 * Mark 5 is no longer the worn/powered suit, and keep the blades out.
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
		// v0.14.21 round two: the blades are real geometry now (mark_v right_blade / left_blade bones + the first-person
		// gauntlet, client IronManBladeClient); the old floating particle line along the look vector is gone.
	}
}
