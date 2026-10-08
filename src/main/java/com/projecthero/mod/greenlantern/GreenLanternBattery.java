package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Charging the ring at the Personal Power Battery. v0.15.15 rework (user: "player has to hold the power battery in their
 * offhand and then shift+right click with the battery in their offhand, the player will then hold out the power battery
 * and put their ring against the battery, the player will then recite the oath and the ring will be fully charged, make
 * the power battery glow while this is happening, show this in first person perspective aswell"):
 * <ul>
 *   <li>Start: Sneak + use with the Power Battery in the OFF hand ({@code PowerBatteryItem}) -- the old right-click on a
 *   placed battery block is gone (the block now just says how it's done).</li>
 *   <li>The Lantern is rooted in place (movement and jumping zeroed by attribute modifiers, any drift pulled back) for
 *   the four lines of the Oath, one every {@link GreenLanternConfig#OATH_LINE_TICKS} on the action bar -- the same lines
 *   as the "Green Lantern's Light!" recital ({@link GreenLanternOath}). Looking around is fine.</li>
 *   <li>Taking damage, using any ability or letting go of the battery cancels it -- no partial charge.</li>
 *   <li>Completing it fills the ring to {@link GreenLanternConfig#MAX_RING_CHARGE} with a flash of light.</li>
 *   <li>Everyone sees it: {@link GreenLanternFx#CH_CHARGE} (+ its start tick) drives the pose (battery held out, ring
 *   fist pressed to it), the battery's glow and the first-person arms on the client.</li>
 * </ul>
 */
public final class GreenLanternBattery {
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.0f);
	private static final ParticleOptions PALE_DUST = new DustParticleOptions(new Vector3f(0.66f, 1.0f, 0.75f), 0.7f);
	/** The classic Green Lantern Oath, one line at a time (shared with {@link GreenLanternOath}). */
	static final String[] OATH_LINES = {
			"message.projecthero.green_lantern.oath.line1",
			"message.projecthero.green_lantern.oath.line2",
			"message.projecthero.green_lantern.oath.line3",
			"message.projecthero.green_lantern.oath.line4",
	};
	private static final ResourceLocation ROOT_MOVE = com.projecthero.mod.ProjectHeroMod.id("green_lantern_charge_root");
	private static final ResourceLocation ROOT_JUMP = com.projecthero.mod.ProjectHeroMod.id("green_lantern_charge_root_jump");

	/** Total length of the ritual: the four lines. */
	public static final int CHARGE_TICKS = GreenLanternConfig.OATH_LINE_TICKS * 4;

	private static final class Charge {
		final Vec3 origin;
		final long startTick;
		int lastLineShown = -1;

		Charge(Vec3 origin, long startTick) {
			this.origin = origin;
			this.startTick = startTick;
		}
	}

	private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();

	private GreenLanternBattery() {
	}

	public static void clearSessionState() {
		CHARGES.clear();
	}

	/** Charging at the battery right now (name kept from the v0.11.4 block-based Oath). */
	public static boolean isRecitingOath(ServerPlayer player) {
		return CHARGES.containsKey(player.getUUID());
	}

	/** Whether {@code player} holds a Power Battery in the off hand. */
	public static boolean holdsBattery(Player player) {
		return player.getOffhandItem().is(GreenLanternBlocks.POWER_BATTERY_ITEM);
	}

	/**
	 * Sneak + use with the battery in the off hand. Returns true if the ritual started (or was already running) -- the
	 * item's use is then consumed instead of placing the block.
	 */
	public static boolean beginCharge(ServerPlayer player) {
		if (!GreenLantern.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.not_a_lantern"), true);
			return false;
		}
		if (isRecitingOath(player)) {
			return true;
		}
		if (!holdsBattery(player)) {
			return false;
		}
		if (GreenLanternEnergy.get(player) >= GreenLanternConfig.MAX_RING_CHARGE) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_full"), true);
			return true;
		}
		long now = player.level().getGameTime();
		CHARGES.put(player.getUUID(), new Charge(player.position(), now));
		GreenLanternVisuals.charge(player, now);
		// rooted: no walking, no jumping (removed again on every way out)
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, ROOT_MOVE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, ROOT_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
		player.hurtMarked = true;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f);
		return true;
	}

	/** Ends a running charge without any charge gained. {@code message}: say so on the action bar. */
	public static void cancel(ServerPlayer player, boolean message) {
		if (CHARGES.remove(player.getUUID()) == null) {
			return;
		}
		end(player);
		if (message) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.cancelled")
					.withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.2f);
		}
	}

	private static void end(ServerPlayer player) {
		GreenLanternVisuals.charge(player, 0L);
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ROOT_MOVE);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, ROOT_JUMP);
	}

	/** Lifecycle cleanup (death, logout, dimension change, power loss). */
	public static void cancelOath(ServerPlayer player) {
		cancel(player, false);
	}

	/** Called by every Green Lantern ability the instant it activates -- cancels a charge in progress. */
	public static void onAbilityUsed(ServerPlayer player) {
		cancel(player, true);
	}

	/** Taking any damage cancels the charge (user decision for v0.15.15). */
	public static void onDamaged(ServerPlayer player) {
		cancel(player, true);
	}

	/** Where the battery is held out (the off hand, in front of the chest) -- particles and the flash. */
	public static Vec3 batteryPoint(Player player) {
		double yaw = Math.toRadians(player.yBodyRot);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return player.position().add(fwd.scale(0.62)).add(right.scale(-0.05)).add(0, 0.95, 0);
	}

	/** Per-player server tick. */
	public static void tick(ServerPlayer player) {
		Charge c = CHARGES.get(player.getUUID());
		if (c == null) {
			return;
		}
		if (!holdsBattery(player) || !player.isAlive() || !GreenLantern.hasPower(player)) {
			cancel(player, true);
			return;
		}
		// rooted: pull any drift back to where the ritual began (turning is fine)
		if (player.position().distanceToSqr(c.origin) > 0.04 * 0.04) {
			player.teleportTo(c.origin.x, Math.min(player.getY(), c.origin.y + 0.05), c.origin.z);
		}
		player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);

		ServerLevel level = player.serverLevel();
		long elapsed = player.level().getGameTime() - c.startTick;
		Vec3 bat = batteryPoint(player);
		// the battery's light: motes of green rising out of it and spiralling in to the ring fist pressed against it
		if (elapsed % 2 == 0) {
			double a = elapsed * 0.45;
			double rr = 0.28;
			level.sendParticles(GREEN_DUST, bat.x + Math.cos(a) * rr, bat.y + 0.1 + (elapsed % 10) * 0.03, bat.z + Math.sin(a) * rr,
					1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(PALE_DUST, bat.x - Math.cos(a) * rr, bat.y + 0.1, bat.z - Math.sin(a) * rr, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (elapsed % 5 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, bat.x, bat.y + 0.15, bat.z, 1, 0.12, 0.12, 0.12, 0.01);
		}

		int line = (int) (elapsed / GreenLanternConfig.OATH_LINE_TICKS);
		if (elapsed >= CHARGE_TICKS) {
			CHARGES.remove(player.getUUID());
			end(player);
			GreenLanternEnergy.addCharge(player, GreenLanternConfig.MAX_RING_CHARGE);
			GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_CHARGED);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.complete")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.3f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.6f);
			// the flash
			level.sendParticles(ParticleTypes.FLASH, bat.x, bat.y, bat.z, 1, 0, 0, 0, 0);
			level.sendParticles(GREEN_DUST, bat.x, bat.y, bat.z, 40, 0.5, 0.5, 0.5, 0.15);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.12);
			return;
		}
		if (line != c.lastLineShown) {
			c.lastLineShown = line;
			player.displayClientMessage(Component.translatable(OATH_LINES[Math.min(line, OATH_LINES.length - 1)])
					.withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.4f + 0.15f * line);
		}
	}
}
