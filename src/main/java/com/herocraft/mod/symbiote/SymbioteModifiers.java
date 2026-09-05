package com.herocraft.mod.symbiote;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.hero.power.PowerToggles;
import com.herocraft.mod.spider.SpiderMan;
import com.herocraft.mod.spider.SpiderWebReserve;
import com.herocraft.mod.spider.data.SpiderManState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Every gameplay stat the Symbiote changes while it is active, in one place so a future expansion adds
 * a line here rather than hunting through the tick loop.
 *
 * <h2>What the Symbiote currently changes</h2>
 * <ul>
 *   <li><b>Armour protection</b> -- <em>not</em> handled here. It comes entirely from the equipped
 *       armour pieces' material (Black Suit: {@link com.herocraft.mod.item.ModArmorMaterials#SYMBIOTE},
 *       a notch above diamond. Normal host: {@link com.herocraft.mod.item.ModArmorMaterials#SYMBIOTE_HOST},
 *       the iron/diamond midpoint). Because it is equipment, it appears the instant the suit is worn
 *       and is gone the instant it is stripped -- nothing to reconcile, nothing that can stack across
 *       repeated toggles.</li>
 *   <li><b>Web capacity</b> -- doubled (Black Suit host only). Also not a stored value:
 *       {@link SpiderWebReserve#maxFor} is a pure function of {@link Symbiote#hasSymbiote} (v0.9.13: a
 *       permanent bond perk, not a suit-on perk -- it applies whether or not the black suit is
 *       currently worn), so bonding raises the ceiling with the current amount untouched (50/100
 *       becomes 50/200) and only fully unbonding ({@link Symbiote#remove}) lowers it again. The one
 *       thing this class does is {@link #onUnbond}: clamp a now-over-cap reserve back down when that
 *       happens.</li>
 *   <li><b>Black Suit physical stat bonuses</b> (v0.9.14) -- melee +30%, movement speed +15%, jump
 *       +20%, knockback resistance +20% (spec). Layered ON TOP of Spider-Man's own base passives
 *       ({@code SpiderPassives}) as separate fixed-id transient modifiers, never edited into the base
 *       numbers -- exactly the "modify/multiply the existing attributes" instruction, and the same
 *       stacking-modifier technique Super Speed's speed_mode/overdrive already use. Web regen, web
 *       swing acceleration, and Enhanced Web Grab pull strength/range are small numeric multipliers
 *       applied directly at their point of use ({@code SpiderWebReserve#tick},
 *       {@code SpiderSwing#applyRope}, {@code SpiderAbilities#webYank}) rather than here, since they
 *       aren't expressible as a plain attribute. Normal hosts never reach this branch -- see
 *       {@link SymbiotePassives} for their (structurally different, weaker) buffs.</li>
 * </ul>
 */
public final class SymbioteModifiers {
	private static final ResourceLocation BLACK_SUIT_ATTACK = HeroCraftMod.id("symbiote_black_suit_attack");
	private static final ResourceLocation BLACK_SUIT_SPEED = HeroCraftMod.id("symbiote_black_suit_speed");
	private static final ResourceLocation BLACK_SUIT_JUMP = HeroCraftMod.id("symbiote_black_suit_jump");
	private static final ResourceLocation BLACK_SUIT_KNOCKBACK = HeroCraftMod.id("symbiote_black_suit_knockback");

	private SymbioteModifiers() {
	}

	/** Called right after the Symbiote engages (either variant). */
	public static void onActivate(ServerPlayer player) {
		// Web capacity was already raised the moment the player bonded (SpiderWebReserve.maxFor keys off
		// the bond, not the suit). Nothing to do but leave the current reserve where it is -- no free
		// refill.
		reconcileBlackSuit(player);
	}

	/**
	 * Reconcile the Black Suit's extra physical modifiers -- present only while active AND the host is
	 * currently Spider-Man. Safe to call every tick from {@link Symbiote#tick}.
	 */
	public static void reconcileBlackSuit(ServerPlayer player) {
		boolean blackSuit = Symbiote.isActive(player) && SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
		if (blackSuit) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLACK_SUIT_ATTACK, 0.30,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, BLACK_SUIT_SPEED, 0.15,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, BLACK_SUIT_JUMP, 0.20,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, BLACK_SUIT_KNOCKBACK, 0.20,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, BLACK_SUIT_ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, BLACK_SUIT_SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, BLACK_SUIT_JUMP);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, BLACK_SUIT_KNOCKBACK);
		}
	}

	/** Called right after {@link Symbiote#remove}: bring an over-cap web reserve back to the normal max. */
	public static void onUnbond(ServerPlayer player) {
		SpiderManState s = SpiderMan.state(player);
		if (s.webReserve > SpiderWebReserve.MAX) {
			SpiderMan.setWebReserve(player, SpiderWebReserve.MAX);
		}
		reconcileBlackSuit(player);
	}
}
