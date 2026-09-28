package com.projecthero.mod.client.hulk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * v0.13.12 (Hulk Phase 3): draws the Hulk model in place of a player who is the Hulk. Called from
 * {@code PlayerRendererHulkMixin}, which cancels the vanilla player render -- so his armour, cape and held items are
 * hidden while he is out. GeckoLib applies the player's own scale attribute (1.8x) itself. Created from the
 * {@code PlayerRenderer}'s context on every resource reload.
 */
public class HulkRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, HulkAnimatable> {
	private static HulkRenderer instance;

	public HulkRenderer(EntityRendererProvider.Context context) {
		super(context, new HulkModel(), HulkAnimatable.INSTANCE);
		this.shadowRadius = 0.5f;
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new HulkRenderer(context);
	}

	public static HulkRenderer get() {
		return instance;
	}

	/** Name tags the way a player gets them (the replaced renderer only shows custom names by default). */
	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null
				&& !player.isInvisibleTo(mc.player) && !player.isVehicle();
	}
}
