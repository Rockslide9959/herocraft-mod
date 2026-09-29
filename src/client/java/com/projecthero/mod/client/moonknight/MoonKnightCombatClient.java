package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.item.MoonKnightItems;
import com.projecthero.mod.network.MoonKnightActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

/**
 * Client side of Moon Knight's combat keys (Phases 3-4), wired from {@code ProjectHeroModClient} with one line:
 * <ul>
 *   <li>the grappling line render for every player ({@link MoonKnightLineRenderer});</li>
 *   <li>the Cape Glide movement: while the local player's synced {@code FLAG_GLIDING} is on, their own client steers
 *       its velocity through {@link MoonKnightCape#glideVelocity} each tick (the server only decides -- from the
 *       player's Sneak, since v0.13.21; see {@code MoonKnightCape} for why the client moves the player);</li>
 *   <li>v0.13.21 the Cape Block: holding right click with an empty main hand (or the Truncheon) sends
 *       {@code CAPE_BLOCK_START} / {@code STOP} on the edges;</li>
 *   <li>the Truncheon's {@code projecthero:staff} model predicate (1 while the holder's {@code FLAG_STAFF} is on), which
 *       swaps in the long staff model for every viewer.</li>
 * </ul>
 */
public final class MoonKnightCombatClient {
	/** True while this client has told the server the Cape Block is up. */
	private static boolean capeBlockSent;

	private MoonKnightCombatClient() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(MoonKnightLineRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(MoonKnightCombatClient::tickGlide);
		ClientTickEvents.END_CLIENT_TICK.register(MoonKnightCombatClient::tickCapeBlock);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			capeBlockSent = false;
			MoonKnightCapeLayer.clear();
			MoonKnightPose.clear();
		});
		FabricModelPredicateProviderRegistry.register(MoonKnightItems.TRUNCHEON, ProjectHeroMod.id("staff"),
				(stack, level, entity, seed) -> entity instanceof Player p
						&& MoonKnightAnim.flag(p, MoonKnightAction.FLAG_STAFF) ? 1.0f : 0.0f);
	}

	/**
	 * After vanilla's own movement step: set the velocity the next move will use. Only for the local player, only
	 * while the server says the glide is on, and never on the ground / in water / with an elytra or creative flight.
	 */
	private static void tickGlide(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !MoonKnight.isTransformed(p)
				|| !MoonKnightAnim.flag(p, MoonKnightAction.FLAG_GLIDING)) {
			return;
		}
		if (p.onGround() || p.isInWater() || p.isInLava() || p.isFallFlying() || p.getAbilities().flying
				|| p.isPassenger() || p.onClimbable()) {
			return;
		}
		p.setDeltaMovement(MoonKnightCape.glideVelocity(p.getDeltaMovement(), p.getYRot(), p.getXRot(),
				MoonKnightLunar.power(p)));
		p.resetFallDistance();
	}

	/**
	 * v0.13.21: HOLD right click = Cape Block. Only with the main hand empty or holding the Truncheon, and never while
	 * something is being used (a shield, food, a bow in the off hand...). With an item in the off hand it also waits
	 * until the crosshair is on nothing, so right-clicking a block still places that torch; with both hands free,
	 * vanilla does nothing with the click anyway (a door or a chest still opens on the first click as always).
	 */
	private static void tickCapeBlock(Minecraft mc) {
		LocalPlayer p = mc.player;
		boolean want = p != null && mc.screen == null && mc.options.keyUse.isDown() && MoonKnightCape.canBlock(p)
				&& (p.getOffhandItem().isEmpty() || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS);
		if (want == capeBlockSent) {
			return;
		}
		capeBlockSent = want;
		if (mc.getConnection() == null) {
			return;
		}
		ClientPlayNetworking.send(new MoonKnightActionPayload(want
				? MoonKnightActionPayload.Action.CAPE_BLOCK_START : MoonKnightActionPayload.Action.CAPE_BLOCK_STOP, 0));
	}
}
