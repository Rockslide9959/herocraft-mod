package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.item.MoonKnightItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Client side of Moon Knight's R / X / G / Z (Phases 3-4), wired from {@code ProjectHeroModClient} with one line:
 * <ul>
 *   <li>the grappling line render for every player ({@link MoonKnightLineRenderer});</li>
 *   <li>the Cape Glide movement: while the local player's synced {@code FLAG_GLIDING} is on, their own client steers
 *       its velocity through {@link MoonKnightCape#glideVelocity} each tick (the server only decides; see
 *       {@code MoonKnightCape} for why the client moves the player);</li>
 *   <li>the Truncheon's {@code projecthero:staff} model predicate (1 while the holder's {@code FLAG_STAFF} is on), which
 *       swaps in the long staff model for every viewer.</li>
 * </ul>
 */
public final class MoonKnightCombatClient {
	private MoonKnightCombatClient() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(MoonKnightLineRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(MoonKnightCombatClient::tickGlide);
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
}
