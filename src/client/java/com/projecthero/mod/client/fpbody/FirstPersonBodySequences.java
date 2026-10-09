package com.projecthero.mod.client.fpbody;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.flash.FlashSuitReveal;
import com.projecthero.mod.client.ironman.GantryClient;
import com.projecthero.mod.client.ironman.IronManSentryClient;
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
		FirstPersonBody.register(FirstPersonBodySequences::ironManSuiting);
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
			return age > -40L && age < GreenLanternConfig.SUIT_UP_TICKS;
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
		FirstPersonBody.register(p -> {
			com.projecthero.mod.symbiote.SymbioteState s = p.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
			if (s == null || !SymbioteReveal.isRevealing(p) || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - s.transformStartTick;
			return age > -20L && age < Math.max(1, s.transformDurationTicks);
		});

		// Thor: the armour summoned with lightning (H) and dissolving away
		FirstPersonBody.register(p -> {
			ThorFx fx = ThorVisuals.fx(p);
			if (fx == null || fx.suitDir() == ThorFx.SUIT_NONE || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - fx.suitStart();
			int len = fx.suitDir() == ThorFx.SUIT_UP ? ThorArmor.SUIT_UP_TICKS : ThorArmor.SUIT_DOWN_TICKS;
			return age > -20L && age < len;
		});

		// The Flash: the suit springing out of / back into the ring
		FirstPersonBody.register(p -> FlashSuitReveal.age(p, 0f) >= 0f);

		// Max Steel: the armour-up / power-down (N / H)
		FirstPersonBody.register(p -> {
			com.projecthero.mod.maxsteel.data.MaxSteelState s = p.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
			if (s == null || s.transformDir == com.projecthero.mod.maxsteel.data.MaxSteelState.DIR_IDLE || p.level() == null) {
				return false;
			}
			long age = p.level().getGameTime() - s.transformStartTick;
			return age > -20L && age < Math.max(1, s.transformDurationTicks);
		});

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

		// v0.15.18 (not a suit-up): the Punisher's Breach Kick -- the real body is drawn so the kicking leg (armour and
		// all) comes up into the view (PunisherKickCameraMixin dips the view to meet it); the gun rig makes way for the real arms
		FirstPersonBody.register(p -> com.projecthero.mod.client.punisher.GunAnim.meleeKind(p) == com.projecthero.mod.punisher.ability.PunisherMelee.ANIM_KICK
				&& com.projecthero.mod.client.punisher.GunAnim.melee(p, 0f) >= 0f);
	}

	/**
	 * Iron Man: a piece still locking on, a piece still coming off while it is in its slot, or a suit-up / suit-down body
	 * pose -- but only while it still has work to do. Off the instant the sequence is over: a suit-down once nothing is
	 * worn (v0.15.15 playtest: the Mark 5 suitcase fold kept the view on long after the case had closed, because the
	 * pieces' "stay gone" clocks and the pose tail outlive the fold), a suit-up once every piece is on and built.
	 */
	private static boolean ironManSuiting(net.minecraft.client.player.AbstractClientPlayer p) {
		IronManSuitFx fx = IronManSuitFx.of(p);
		if (fx == null || p.level() == null) {
			return false;
		}
		long now = p.level().getGameTime();
		boolean anyWorn = false;
		boolean allOnAndBuilt = true;
		for (EquipmentSlot slot : ARMOR) {
			int bit = IronManSuitFx.bit(slot);
			boolean worn = !p.getItemBySlot(slot).isEmpty();
			anyWorn |= worn;
			if (fx.building(bit, now)) {
				return true; // locking on right now
			}
			if (worn && !fx.assembling(bit) && fx.start(bit) > 0L) {
				long age = now - fx.start(bit);
				if (age >= 0L && age < fx.releaseTicks(bit)) {
					return true; // coming off right now
				}
			}
			if (!worn) {
				allOnAndBuilt = false;
			}
		}
		if (fx.poseAge(now, 0f) < 0f) {
			return false;
		}
		int kind = fx.poseKind();
		boolean down = kind == IronManSuitFx.POSE_SUIT_DOWN || kind == IronManSuitFx.POSE_CASE_DOWN
				|| kind == IronManSuitFx.POSE_PLATFORM_OFF || kind == IronManSuitFx.POSE_MK5_DOWN
				|| kind == IronManSuitFx.POSE_MK1_OFF || kind == IronManSuitFx.POSE_SLEEK_OFF;
		return down ? anyWorn : !allOnAndBuilt;
	}
}
