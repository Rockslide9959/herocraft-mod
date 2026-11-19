package com.projecthero.mod.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 (user: "i want the symbiote to be able to fly for a short amount of time by creating giant symbiote wings"): Symbiote Wings,
 * Normal host only, suit on. <b>Double-tap Space in the air</b> (not on the ground, not in water or lava, not riding or gliding on an
 * elytra) grows giant bat-like wings out of the back: tendril bones first, then the web-veined membrane fills in ({@link #GROW_TICKS}).
 * The host then flies for up to {@link #FLIGHT_TICKS} (6 s) in the look direction with wing beats -- a strong flap on take-off, Space
 * to rise, Shift to dive -- and when the time runs out the wings stay spread in a glide until the host lands (no fall damage: a bonded
 * host never takes any). On landing (or water, a mount, the suit coming off) they dissolve back into the back over
 * {@link #DISSOLVE_TICKS}.
 *
 * <p>Costs {@link #BIOMASS_COST} Biomass and its own cooldown entry ({@link #CDI_WINGS}, {@link #COOLDOWN_TICKS} from take-off); a new
 * pair can't grow until the last one has dissolved. No new key: the gesture is the mod's usual airborne double-tap (sent as
 * {@code SymbioteWingsPayload}), so H / N are untouched.
 *
 * <p>State lives in the synced {@link SymbioteVitals} ({@link SymbioteVitals#wingsStart} / {@link SymbioteVitals#wingsLanded}): every
 * viewer draws the wings from it, and the local client flies from it (the client moves itself, the server accepts it -- as every flight
 * in the mod). No static collections.
 */
public final class SymbioteWings {
	public static final int GROW_TICKS = 8;
	public static final int FLIGHT_TICKS = 120;
	public static final int DISSOLVE_TICKS = 12;
	public static final float BIOMASS_COST = 10.0f;
	public static final int COOLDOWN_TICKS = 400;
	/** {@link SymbioteState#abilityCooldowns} entry. */
	public static final int CDI_WINGS = 15;

	public enum Phase { NONE, FLYING, GLIDING, DISSOLVING }

	private SymbioteWings() {
	}

	/** Which part of the flight a host's wings are in (either side: the vitals are synced). */
	public static Phase phase(SymbioteVitals v, long now) {
		if (v == null || v.wingsStart <= 0L) {
			return Phase.NONE;
		}
		if (v.wingsLanded > 0L) {
			return now - v.wingsLanded < DISSOLVE_TICKS ? Phase.DISSOLVING : Phase.NONE;
		}
		return now - v.wingsStart < FLIGHT_TICKS ? Phase.FLYING : Phase.GLIDING;
	}

	public static Phase phase(Player player) {
		return phase(player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null), player.level().getGameTime());
	}

	/** Wings out (flying, gliding or dissolving)? */
	public static boolean up(Player player) {
		return phase(player) != Phase.NONE;
	}

	/** In the air on the wings (flying or gliding) -- lifts the server's "flying" kick and the fall. */
	public static boolean airborne(Player player) {
		Phase p = phase(player);
		return p == Phase.FLYING || p == Phase.GLIDING;
	}

	/** Flight ticks left (0 when not flying). */
	public static int flightTicksLeft(Player player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (phase(v, player.level().getGameTime()) != Phase.FLYING) {
			return 0;
		}
		return (int) Math.max(0L, v.wingsStart + FLIGHT_TICKS - player.level().getGameTime());
	}

	/** Could the double-tap grow wings right now (client pre-check, the server re-checks everything)? */
	public static boolean canTryFrom(Player player) {
		return Symbiote.isNormalHost(player) && Symbiote.isActive(player) && !player.onGround() && !player.isInWater()
				&& !player.isInLava() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying;
	}

	/** The double-tap (server side). @return true if the wings grew. */
	public static boolean tryStart(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (!SymbioteAbilityManager.hasContext(player) || !Symbiote.isActive(player)) {
			return false;
		}
		if (phase(SymbioteVitalsManager.vitals(player), now) != Phase.NONE) {
			return false;
		}
		if (!canTryFrom(player)) {
			return false;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return false;
		}
		long readyAt = Symbiote.state(player).abilityCooldowns.get(CDI_WINGS);
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return false;
		}
		SymbioteVitals v = SymbioteVitalsManager.vitals(player);
		if (v.hp < BIOMASS_COST) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.wings.no_biomass"), true);
			return false;
		}
		SymbioteVitals c = v.copy();
		c.hp -= BIOMASS_COST;
		c.wingsStart = now;
		c.wingsLanded = 0L;
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
		SymbioteState s = Symbiote.state(player).copy();
		s.abilityCooldowns.set(CDI_WINGS, now + COOLDOWN_TICKS);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, s);
		player.resetFallDistance();
		SymbioteVfx.bodyBurst(player, 0.9f);
		SymbioteSounds.organic(player, 1.1f, 0.45f);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 1.2f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.wings.on")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
		return true;
	}

	/** Every server tick: the landing, the dissolve, and no fall while the wings carry the host. */
	public static void tick(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null || v.wingsStart <= 0L) {
			return;
		}
		long now = player.level().getGameTime();
		if (v.wingsLanded > 0L) {
			if (now - v.wingsLanded >= DISSOLVE_TICKS) {
				SymbioteVitals c = v.copy();
				c.wingsStart = 0L;
				c.wingsLanded = 0L;
				player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
			}
			return;
		}
		player.resetFallDistance();
		boolean lost = !Symbiote.hasSymbiote(player) || !Symbiote.isActive(player) || !SymbioteAbilityManager.hasContext(player);
		boolean down = (player.onGround() && now - v.wingsStart > 3) || player.isInWater() || player.isInLava() || player.isPassenger()
				|| player.getAbilities().flying || player.isSpectator();
		if (lost || down || now < v.wingsStart) {
			land(player, now);
			return;
		}
		if (now - v.wingsStart == FLIGHT_TICKS) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.wings.glide")
					.withStyle(ChatFormatting.DARK_PURPLE), true);
		}
		// wing beats every 10 ticks while flying (the client drives the motion, this is only the sound)
		if (now - v.wingsStart < FLIGHT_TICKS && (now - v.wingsStart) % 10 == 2) {
			AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.7f, 0.55f + player.getRandom().nextFloat() * 0.1f);
		}
	}

	/** Touch-down: the wings start dissolving back into the back. */
	public static void land(ServerPlayer player, long now) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null || v.wingsStart <= 0L || v.wingsLanded > 0L) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.wingsLanded = Math.max(1L, now);
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
		player.resetFallDistance();
		SymbioteSounds.organic(player, 0.8f, 0.6f);
	}

	/** Nothing static to clear (the state is in the vitals); kept so the reset hook documents it. */
	static void clearSessionState() {
	}
}
