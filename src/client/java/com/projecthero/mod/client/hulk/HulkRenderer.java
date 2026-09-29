package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * v0.13.12 (Hulk Phase 3): draws the Hulk model in place of a player who is the Hulk. Called from
 * {@code PlayerRendererHulkMixin}, which cancels the vanilla player render -- so his armour, cape and held items are
 * hidden while he is out. GeckoLib applies the player's own scale attribute (1.8x) itself. Created from the
 * {@code PlayerRenderer}'s context on every resource reload.
 *
 * <p>v0.13.15: {@link #renderFaded} draws him see-through while he phases onto Banner (or off him) -- the translucent
 * entity render type and the alpha in the render colour -- and solid once the change is over.
 */
public class HulkRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, HulkAnimatable> {
	private static HulkRenderer instance;

	/** The alpha of the draw in progress (1 = solid). Rendering is single-threaded. */
	private float fade = 1.0f;

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

	/** Draws the Hulk at {@code alpha} (0..1); below 1 he is mid-change and Banner is drawn under him too. */
	public void renderFaded(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, float alpha) {
		this.fade = Math.max(0.0f, Math.min(1.0f, alpha));
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			this.fade = 1.0f;
		}
	}

	@Override
	public RenderType getRenderType(HulkAnimatable animatable, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
		return this.fade < 0.999f ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, buffer, partialTick);
	}

	@Override
	public Color getRenderColor(HulkAnimatable animatable, float partialTick, int packedLight) {
		Color base = super.getRenderColor(animatable, partialTick, packedLight);
		if (this.fade >= 0.999f) {
			return base;
		}
		return Color.ofARGB(Math.round(base.getAlpha() * this.fade), base.getRed(), base.getGreen(), base.getBlue());
	}

	/**
	 * Name tags the way a player gets them (the replaced renderer only shows custom names by default). Mid-change Banner's
	 * own renderer is still drawing his tag, so the Hulk only takes it over once he is solid.
	 */
	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return this.fade >= 0.999f && Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null
				&& !player.isInvisibleTo(mc.player) && !player.isVehicle();
	}
}
