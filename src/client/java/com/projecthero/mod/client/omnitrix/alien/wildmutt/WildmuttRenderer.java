package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixWatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.17.1: draws Wildmutt in place of a player who is him (the Omnitrix's custom-body hook, {@code OmnitrixPlayerRendererMixin}
 * -> {@code WildmuttClient.renderBody}): the GeckoLib quadruped, its clips from {@link WildmuttAnimatable}, and the Omnitrix
 * face on the strap round his left upper arm ({@link Badge}: the framework's badge art, red while the watch is). Built from the
 * player renderers' context on every resource reload.
 */
public class WildmuttRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, WildmuttAnimatable> {
	private static WildmuttRenderer instance;
	/** The model (26 px to the hump) drawn 1.1x: 1.43 blocks at the shoulder, 1.76 to the top of the hump, 2.3 long. */
	public static final float MODEL_SCALE = 1.1f;

	public WildmuttRenderer(EntityRendererProvider.Context context) {
		super(context, new WildmuttModel<>(), WildmuttAnimatable.INSTANCE);
		this.shadowRadius = 0.8f;
		withScale(MODEL_SCALE);
		addRenderLayer(new Badge(this, this::getCurrentEntity));
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new WildmuttRenderer(context);
	}

	public static WildmuttRenderer get() {
		return instance;
	}

	/** Name tags the way a player gets them (the replaced renderer only shows custom names by default). */
	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null && !player.isInvisibleTo(mc.player)
				&& !player.isVehicle();
	}

	/** The Omnitrix on the strap's plate (json x 13.7, y 14.2..19.4, z -7.6..-2.4 on the {@code badge} bone). */
	static final class Badge<T extends software.bernie.geckolib.animatable.GeoAnimatable> extends GeoRenderLayer<T> {
		private final java.util.function.Supplier<AbstractClientPlayer> wearer;

		Badge(software.bernie.geckolib.renderer.GeoRenderer<T> owner, java.util.function.Supplier<AbstractClientPlayer> wearer) {
			super(owner);
			this.wearer = wearer;
		}

		@Override
		public void renderForBone(PoseStack pose, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
				VertexConsumer buffer, float partial, int light, int overlay) {
			AbstractClientPlayer p = wearer.get();
			if (!"badge".equals(bone.getName()) || p == null || bone.isHidden()) {
				return;
			}
			boolean red = OmnitrixWatch.red(p, partial);
			// the framework's badge: the show's white symbol (OmnitrixLayer.BADGE, the whole texture), the red watch face while it beeps
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(red ? OmnitrixWatch.TEX_RED
					: com.projecthero.mod.client.omnitrix.OmnitrixLayer.BADGE));
			float u0 = red ? 0.5f : 0f, v0 = red ? 0.5f : 0f;
			// GeckoLib mirrors json X: the plate's outer face is at render x = -13.72 px, facing -X
			float s = 1f / 16f;
			float x = -13.72f * s, y = 16.83f * s, z = -5f * s, h = 2.35f * s;
			PoseStack.Pose m = pose.last();
			int full = LightTexture.FULL_BRIGHT;
			AlienParts.quad(vc, m, 0xFFFFFFFF, full, x, y + h, z - h, x, y + h, z + h, x, y - h, z + h, x, y - h, z - h, -1, 0, 0, u0, v0, 1f, 1f);
			AlienParts.quad(vc, m, 0xFFFFFFFF, full, x, y + h, z + h, x, y + h, z - h, x, y - h, z - h, x, y - h, z + h, -1, 0, 0, u0, v0, 1f, 1f);
			// hand the main pass its buffer back (GeoRenderLayer#renderForBone's contract)
			buffers.getBuffer(renderType);
		}
	}
}
