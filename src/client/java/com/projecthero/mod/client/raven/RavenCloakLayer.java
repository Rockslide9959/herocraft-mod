package com.projecthero.mod.client.raven;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenItems;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Raven: the cape of Raven's Cloak (the hood is drawn by {@link RavenAuraLayer}), on the shared {@link FlowingCapeLayer}
 * cloth -- it drifts behind her calm levitation and streams when she flies fast. The colour follows a Raven wearer's Composure
 * ({@link RavenItems#cloakColour}): blue, white when very calm, dark red at Rage; anyone else's is blue. Through Trigon's Wrath she
 * wears a black one whether or not she has the costume on.
 */
public class RavenCloakLayer extends FlowingCapeLayer {
	private static final ResourceLocation BLUE = ProjectHeroMod.id("textures/entity/raven/cloak_blue.png");
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/raven/cloak_white.png");
	private static final ResourceLocation RED = ProjectHeroMod.id("textures/entity/raven/cloak_red.png");
	private static final ResourceLocation BLACK = ProjectHeroMod.id("textures/entity/raven/cloak_black.png");

	public RavenCloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return (RavenItems.wearsCloak(player) || Raven.inWrath(player)) && !Raven.inShadow(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		if (Raven.inWrath(player)) {
			return BLACK;
		}
		return switch (RavenItems.cloakColour(player)) {
			case RavenItems.CLOAK_WHITE -> WHITE;
			case RavenItems.CLOAK_RED -> RED;
			default -> BLUE;
		};
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return Raven.isFlying(player) || player.getAbilities().flying || player.isFallFlying();
	}

	/** The hood's colour (matching the cape texture's main tone). */
	public static int colour(AbstractClientPlayer player) {
		return switch (RavenItems.cloakColour(player)) {
			case RavenItems.CLOAK_WHITE -> 0xFFE6E4EE;
			case RavenItems.CLOAK_RED -> 0xFF5E1622;
			default -> 0xFF2C3E94;
		};
	}
}
