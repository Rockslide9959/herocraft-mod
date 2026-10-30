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
	/** v0.17.1 rework 3: drawn 1:1 -- ~1.52 blocks at the hump, 1.79 to the quill tips, 2.55 long. */
	public static final float MODEL_SCALE = 1.0f;

	public WildmuttRenderer(EntityRendererProvider.Context context) {
		super(context, new WildmuttModel<>(), WildmuttAnimatable.INSTANCE);
		this.shadowRadius = 0.6f;
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

	/**
	 * The Omnitrix on the cuff's plate. v0.17.1 rework 2 / 3: the cuff round the left upper front leg near the shoulder, the
	 * {@code badge} bone tipped with the upper arm -- the plate's outer face at json x 10.8, y 14.85..18.75, z -7.45..-3.55 (rework 3) in the
	 * bone's own (untipped) frame, so the symbol rides the cuff whatever the leg does.
	 */
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
			// the framework's badge: the show's symbol (OmnitrixLayer.BADGE, the whole texture), BADGE_RED while the watch beeps
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(red ? com.projecthero.mod.client.omnitrix.OmnitrixLayer.BADGE_RED
					: com.projecthero.mod.client.omnitrix.OmnitrixLayer.BADGE));
			// GeckoLib mirrors json X: the plate's outer face is at render x = -10.8 px, facing -X (v0.17.1 rework 3)
			float s = 1f / 16f;
			// v0.17.1: centred on the shoulder pad's plate (outer face json x 11.1, y 17.5..21.4)
			float x = -11.13f * s, y = 19.45f * s, z = -5.5f * s, h = 1.7f * s;
			PoseStack.Pose m = pose.last();
			int full = LightTexture.FULL_BRIGHT;
			AlienParts.quad(vc, m, 0xFFFFFFFF, full, x, y + h, z - h, x, y + h, z + h, x, y - h, z + h, x, y - h, z - h, -1, 0, 0, 0f, 0f, 1f, 1f);
			// hand the main pass its buffer back (GeoRenderLayer#renderForBone's contract)
			buffers.getBuffer(renderType);
		}
	}
}
