package com.projecthero.mod.client.fpbody;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.flash.FlashSuitReveal;
import com.projecthero.mod.client.ironman.GantryClient;
import com.projecthero.mod.client.ironman.IronManSentryClient;
import com.projecthero.mod.client.maxsteel.MaxSteelReveal;
import com.projecthero.mod.client.moonknight.MoonKnightReveal;
import com.projecthero.mod.client.symbiote.SymbioteReveal;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.data.NovaState;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.15.15: every timed suit-up / suit-down in the mod, registered with {@link FirstPersonBody} -- while one runs on the
 * local player in first person, their whole body is drawn so they can look down and watch it.
 *
 * <p>Not registered (no timed suit-up / suit-down of their own): the Superman Suit and Super Soldier gear (ordinary
 * armour, put on instantly), Spider-Man's costume (craftable armour; its black suit is the Symbiote, covered), the
 * Hulk / Gladiator gear (a body change with its own model and camera, the gear goes on instantly), the Titan Shifter
 * (becomes a separate Titan entity), Wolverine (claws only), and Max Steel's combat-mode swaps (a fighting-form change,
 * not a suit-up -- the armour-up / power-down itself is covered).
 */
public final class FirstPersonBodySequences {
	private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private FirstPersonBodySequences() {
	}

	public static void initialize() {
		// Iron Man: every piece clock (C hand build / manual suit-up, Marks 2-7 retract removal, Mark 1 removal, the Mark 5
		// suitcase, the Mark 7 bracelet wrap and pod, Suit Platform, couriers locking on) and every suit-up body pose
		FirstPersonBody.register(p -> {
			IronManSuitFx fx = IronManSuitFx.of(p);
			if (fx == null || p.level() == null) {
				return false;
			}
			long now = p.level().getGameTime();
			if (fx.poseAge(now, 0f) >= 0f) {
				return true;
			}
			for (EquipmentSlot slot : ARMOR) {
				if (fx.pieceAge(slot, now, 0f) >= 0f) {
					return true;
				}
			}
			return false;
		});
		// Stark Gantry suit-up / suit-down / swap (the wearer is held by a running sequence)
		FirstPersonBody.register(p -> p == Minecraft.getInstance().player && GantryClient.locked());
		// Mark 8 Sentry Mode: stepping out of / walking into / being closed into the standing suit
		FirstPersonBody.register(p -> IronManSentryClient.stepping(p) != null);

		// Green Lantern: suit up / suit down, and a suit-style re-form (the suit-up sweep again)
		FirstPersonBody.register(p -> {
			GreenLanternState s = p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
			if (s == null || !s.hasPower || s.suitAnimDir == GreenLanternState.SUIT_IDLE || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - s.suitAnimStartTick;
			return age > -40L && age < GreenLanternConfig.SUIT_UP_TICKS + 2L;
		});

		// Nova: helmet suit-up / dematerialise
		FirstPersonBody.register(p -> {
			NovaState s = p.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
			if (s == null || !s.hasPower || s.suitChangeAt <= 0L || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - s.suitChangeAt;
			return age > -40L && age < (s.suited ? NovaConfig.SUIT_UP_TICKS : NovaConfig.SUIT_DOWN_TICKS);
		});

		// Symbiote (Normal host, Black Suit Spider-Man, Agent Venom): the suit spreading on / melting off
		FirstPersonBody.register(SymbioteReveal::isRevealing);

		// Thor: the armour summoned with lightning (H) and dissolving away
		FirstPersonBody.register(p -> {
			ThorFx fx = ThorVisuals.fx(p);
			if (fx == null || fx.suitDir() == ThorFx.SUIT_NONE || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - fx.suitStart();
			int len = fx.suitDir() == ThorFx.SUIT_UP ? ThorArmor.SUIT_UP_TICKS : ThorArmor.SUIT_DOWN_TICKS;
			return age > -20L && age < len + 2L;
		});

		// The Flash: the suit springing out of / back into the ring
		FirstPersonBody.register(p -> FlashSuitReveal.age(p, 0f) >= 0f);

		// Max Steel: the armour-up / power-down (N / H)
		FirstPersonBody.register(MaxSteelReveal::isRevealing);

		// Moon Knight: the transformation, the untransformation and an alter's suit swap
		FirstPersonBody.register(p -> {
			MoonKnightAction a = MoonKnightAnim.action(p);
			return a != null && (a.has(MoonKnightAction.FLAG_TRANSFORMING) || a.has(MoonKnightAction.FLAG_UNTRANSFORMING))
					|| MoonKnightReveal.swapFrom(p) != null;
		});

		// All Might: the form change (the body grows / shrinks and the costume goes on / comes off)
		FirstPersonBody.register(p -> {
			AllMightState s = p.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
			if (s == null || p.level() == null) {
				return false;
			}
			long now = p.level().getGameTime();
			return now < s.transformUntil && s.transformUntil - now <= 40L
					&& (s.animId == AllMightState.ANIM_TRANSFORM_UP || s.animId == AllMightState.ANIM_TRANSFORM_DOWN);
		});
	}
}
