package com.projecthero.mod.client.speed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user): Ground-Stick for speedsters (Shift+N toggles it). At speed, cresting a hill or dropping down a slope
 * used to launch the runner into the air, coasting and losing speed; with Ground-Stick on, a runner who leaves the ground
 * with solid ground within {@link #MAX_DROP} blocks below is pulled straight down onto it -- following the terrain down
 * hills and mountainsides with all their forward speed kept. A drop bigger than that (a cliff, a ravine, a cave mouth) is
 * left alone: they fly off and fall as normal. Going uphill is unchanged (the step-up carries them). Jumping turns it off
 * until they land, and it never applies while flying, swimming, on water, riding, gliding or phasing, or when slow.
 *
 * <p>Client-side: the local player's own movement is simulated here (the server takes the landed position and the
 * on-ground flag from the movement packets), so the pull is smooth and costs no network traffic.
 */
public final class SpeedsterGroundStick {
	/** Ground further below than this is not stuck to (the user: don't drop them into caves). */
	public static final double MAX_DROP = 5.0;
	/** Below this horizontal speed (blocks a tick, 10 b/s) nothing happens. */
	private static final double MIN_SPEED = 0.5;
	/** Ticks after leaving the ground in which a pull may still start (a crest launch, not a fall already under way). */
	private static final int GRACE_TICKS = 3;

	/** Who counts as a speedster -- the plain Speedster (the Super Speed mutation); other speedster powers add theirs. */
	private static final List<Predicate<Player>> SPEEDSTERS = new ArrayList<>();

	private static boolean enabled;
	private static int airTicks;
	private static boolean jumped;

	static {
		SPEEDSTERS.add(SpeedsterGroundStick::plainSpeedster);
		SPEEDSTERS.add(com.projecthero.mod.speedforce.SpeedForceSpeedster::hasPower);
	}

	private SpeedsterGroundStick() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(SpeedsterGroundStick::tick);
		// the server only learns a plain Speedster's setting from us (it forgets on logout): tell it again on joining
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (enabled) {
				sender.sendPacket(new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(true, -1));
			}
		});
	}

	/** Lets another speedster power count (the Speed Force Speedster). */
	public static void addSpeedster(Predicate<Player> test) {
		SPEEDSTERS.add(test);
	}

	public static boolean isSpeedster(Player p) {
		if (p == null) {
			return false;
		}
		for (Predicate<Player> t : SPEEDSTERS) {
			if (t.test(p)) {
				return true;
			}
		}
		return false;
	}

	private static boolean plainSpeedster(Player p) {
		ExperimentalState s = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return s != null && SuperSpeedHandlers.KEY.equals(s.activePower);
	}

	/** Ground-Stick is on for the local player: the Speed Force Speedster's is saved in its state (the Shift+N menu). */
	public static boolean enabled() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && com.projecthero.mod.speedforce.SpeedForceSpeedster.hasPower(mc.player)) {
			com.projecthero.mod.speedforce.data.SpeedForceState s = com.projecthero.mod.speedforce.SpeedForceSpeedster.peek(mc.player);
			return s != null && s.groundStick;
		}
		return enabled;
	}

	/** The menu's toggle (and the plain Speedster's Shift+N): set it and tell the server (its fall-damage guard). */
	public static void set(LocalPlayer p, boolean on, int colour) {
		enabled = on;
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(on, colour));
	}

	/** Shift+N. */
	public static void toggle(LocalPlayer p) {
		set(p, !enabled, -1);
		p.displayClientMessage(Component.translatable(enabled ? "message.projecthero.speed.ground_stick_on" : "message.projecthero.speed.ground_stick_off")
				.withStyle(enabled ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.isPaused()) {
			return;
		}
		if (p.onGround()) {
			airTicks = 0;
			jumped = false;
			return;
		}
		airTicks++;
		if (mc.options.keyJump.isDown()) {
			jumped = true; // a jump on purpose: no pulling until they land
		}
		if (!enabled() || jumped || airTicks > GRACE_TICKS || !isSpeedster(p)) {
			return;
		}
		if (p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.isInWater() || p.isInLava() || p.isSwimming()
				|| p.onClimbable() || p.noPhysics || SuperSpeedHandlers.phasing(p) || p.horizontalCollision) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.horizontalDistance() < MIN_SPEED || v.y > 0.25) {
			return; // too slow to matter, or going up on purpose (wall-run, a launch)
		}
		Vec3 feet = p.position();
		BlockHitResult hit = mc.level.clip(new ClipContext(feet.add(0, 0.05, 0), feet.add(0, -MAX_DROP, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, p));
		if (hit.getType() == HitResult.Type.MISS) {
			return; // a real drop: let them fall
		}
		double drop = feet.y - hit.getLocation().y;
		if (drop < 0.01) {
			return;
		}
		if (drop <= 2.5) {
			// glued straight back onto the slope, forward speed untouched
			p.setPos(feet.x, hit.getLocation().y, feet.z);
			p.setDeltaMovement(v.x, 0.0, v.z);
			p.setOnGround(true);
			p.resetFallDistance();
			airTicks = 0;
		} else {
			// a steeper slope: come down onto it over the next tick or two, still at full forward speed
			p.setDeltaMovement(v.x, -drop * 0.75, v.z);
			p.resetFallDistance();
			airTicks = Math.max(0, airTicks - 1);
		}
	}
}
