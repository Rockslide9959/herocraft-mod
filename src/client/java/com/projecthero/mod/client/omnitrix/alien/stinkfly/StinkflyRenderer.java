package com.projecthero.mod.client.omnitrix.alien.stinkfly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixLayer;
import com.projecthero.mod.client.omnitrix.OmnitrixWatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.17.1: draws Stinkfly in place of a player who is him (the Omnitrix's custom-body hook, {@code OmnitrixPlayerRendererMixin}
 * -> {@code StinkflyClient.renderBody}): the GeckoLib insect, its clips from {@link StinkflyAnimatable}. The whole body is drawn
 * with the translucent entity type (no back-face culling), so the wings' see-through texels show the world behind them from
 * either side; the glowmask ({@link AutoGlowingGeoLayer}) keeps the four bulb eyes bright and gives the wings a faint pale glow;
 * the Omnitrix face sits on the plate on his chest ({@link Badge}: the framework's badge art, red while the watch is). Built from
 * the player renderers' context on every resource reload. Drawn 1:1; the kit's 0.9 size stat scales him (the entity's scale).
 */
public class StinkflyRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, StinkflyAnimatable> {
	private static StinkflyRenderer instance;

	public StinkflyRenderer(EntityRendererProvider.Context context) {
		super(context, new StinkflyModel<>(), StinkflyAnimatable.INSTANCE);
		this.shadowRadius = 0.4f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new Badge<>(this, this::getCurrentEntity));
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new StinkflyRenderer(context);
	}

	public static StinkflyRenderer get() {
		return instance;
	}

	/** The see-through wings: the translucent entity type (it discards fully clear texels and culls no faces). */
	@Override
	public RenderType getRenderType(StinkflyAnimatable animatable, ResourceLocation texture, MultiBufferSource buffers, float partial) {
		return RenderType.entityTranslucent(texture);
	}

	/** Name tags the way a player gets them (the replaced renderer only shows custom names by default). */
	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null && !player.isInvisibleTo(mc.player)
				&& !player.isVehicle();
	}

	/**
	 * The Omnitrix on the chest plate: the {@code badge} bone (a child of the chest, untipped), the plate's front face at json
	 * z -2.85, y 18.52..21.32 (gen_stinkfly.js prints it), so the symbol rides the chest whatever the body does.
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
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(red ? OmnitrixLayer.BADGE_RED : OmnitrixLayer.BADGE));
			float s = 1f / 16f;
			float z = -2.88f * s, y = 19.92f * s, h = 1.25f * s;
			PoseStack.Pose m = pose.last();
			int full = LightTexture.FULL_BRIGHT;
			// facing -Z (out of his chest); GeckoLib mirrors json X, the plate is centred so it does not matter
			AlienParts.quad(vc, m, 0xFFFFFFFF, full, h, y + h, z, -h, y + h, z, -h, y - h, z, h, y - h, z, 0, 0, -1, 0f, 0f, 1f, 1f);
			// hand the main pass its buffer back (GeoRenderLayer#renderForBone's contract)
			buffers.getBuffer(renderType);
		}
	}
}
