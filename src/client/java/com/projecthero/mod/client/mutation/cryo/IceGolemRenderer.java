package com.projecthero.mod.client.mutation.cryo;

import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.IceGolemEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.6 Cryokinesis hold-C: the Ice Golem -- the iron golem's body (its model and walk / punch animation) recast in
 * ice. Its skin is made at runtime from the iron golem's own texture: every texel takes the packed-ice pattern, shaded by
 * the golem's light and dark (so the face, the plates and the vines still read) and it is drawn translucent. On top,
 * a {@link Crystals} layer grows blue-ice crystals from its shoulders, back and brow and puts a cold glow in its eyes.
 * It rises out of the ground over its first 0.75 s.
 */
public class IceGolemRenderer extends MobRenderer<IceGolemEntity, IronGolemModel<IceGolemEntity>> {
	private static final ResourceLocation GOLEM_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
	private static final ResourceLocation PACKED_SRC = ResourceLocation.withDefaultNamespace("textures/block/packed_ice.png");
	private static final ResourceLocation SKIN = com.projecthero.mod.ProjectHeroMod.id("dynamic/ice_golem");
	private static boolean built;

	public IceGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new IronGolemModel<>(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.6f);
		addLayer(new Crystals(this));
	}

	/** The icy skin, built once from the vanilla textures (falls back to the plain packed ice if they can't be read). */
	private static ResourceLocation skin() {
		if (built) {
			return SKIN;
		}
		built = true;
		Minecraft mc = Minecraft.getInstance();
		try (InputStream g = mc.getResourceManager().open(GOLEM_SKIN); InputStream i = mc.getResourceManager().open(PACKED_SRC);
				NativeImage golem = NativeImage.read(g); NativeImage iceImg = NativeImage.read(i)) {
			NativeImage out = new NativeImage(golem.getWidth(), golem.getHeight(), true);
			for (int y = 0; y < golem.getHeight(); y++) {
				for (int x = 0; x < golem.getWidth(); x++) {
					int src = golem.getPixelRGBA(x, y); // ABGR
					int a = (src >>> 24) & 0xFF;
					if (a == 0) {
						out.setPixelRGBA(x, y, 0);
						continue;
					}
					int r = src & 0xFF;
					int gg = (src >> 8) & 0xFF;
					int b = (src >> 16) & 0xFF;
					float lum = (0.3f * r + 0.59f * gg + 0.11f * b) / 255.0f;
					int ip = iceImg.getPixelRGBA(x % iceImg.getWidth(), y % iceImg.getHeight());
					float shade = 0.45f + 0.75f * lum;
					// the vines (green on the iron golem) become streaks of deep blue ice
					boolean vine = gg > r + 18 && gg > b + 10;
					int ir = Mth.clamp((int) ((ip & 0xFF) * shade * (vine ? 0.55f : 1.0f)), 0, 255);
					int ig = Mth.clamp((int) (((ip >> 8) & 0xFF) * shade * (vine ? 0.8f : 1.0f)), 0, 255);
					int ib = Mth.clamp((int) (((ip >> 16) & 0xFF) * shade * 1.05f + 12), 0, 255);
					int oa = lum < 0.18f ? 245 : 215;
					out.setPixelRGBA(x, y, (oa << 24) | (ib << 16) | (ig << 8) | ir);
				}
			}
			mc.getTextureManager().register(SKIN, new DynamicTexture(out));
			return SKIN;
		} catch (Exception ex) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("Ice Golem skin could not be built: {}", ex.toString());
			return null;
		}
	}

	@Override
	public ResourceLocation getTextureLocation(IceGolemEntity entity) {
		ResourceLocation s = skin();
		return s != null ? s : PACKED_SRC;
	}

	@Override
	protected RenderType getRenderType(IceGolemEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return RenderType.entityTranslucent(getTextureLocation(entity));
	}

	@Override
	public void render(IceGolemEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float rise = Mth.clamp((e.tickCount + partial) / 15.0f, 0.0f, 1.0f);
		pose.pushPose();
		pose.translate(0, -(1.0f - rise) * (1.0f - rise) * 2.4f, 0);
		super.render(e, yaw, partial, pose, buffers, light);
		pose.popPose();
	}

	@Override
	protected void setupRotations(IceGolemEntity e, PoseStack pose, float bob, float yaw, float partial, float scale) {
		super.setupRotations(e, pose, bob, yaw, partial, scale);
		// the iron golem's lumbering sway
		if (e.walkAnimation.speed() >= 0.01) {
			float pos = e.walkAnimation.position(partial) + 6.0f;
			float sway = (Math.abs(pos % 13.0f - 6.5f) - 3.25f) / 3.25f;
			pose.mulPose(Axis.ZP.rotationDegrees(6.5f * sway));
		}
	}

	/** Blue-ice crystals on the shoulders, back and brow, and glowing eyes. */
	private static final class Crystals extends RenderLayer<IceGolemEntity, IronGolemModel<IceGolemEntity>> {
		Crystals(RenderLayerParent<IceGolemEntity, IronGolemModel<IceGolemEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, IceGolemEntity e, float limb, float limbAmount,
				float partial, float age, float headYaw, float headPitch) {
			IronGolemModel<IceGolemEntity> m = getParentModel();
			ModelPart root = m.root();
			float px = 1.0f / 16.0f;
			int lit = LightTexture.pack(Math.max(LightTexture.block(light), 7), LightTexture.sky(light));
			// the back: a ridge of crystals leaning back and up
			pose.pushPose();
			root.getChild("body").translateAndRotate(pose);
			for (int i = 0; i < 4; i++) {
				pose.pushPose();
				pose.translate((i - 1.5f) * 4.0f * px, (-1.0f + (i % 2) * 2.0f) * px, 4.5f * px);
				pose.mulPose(Axis.XP.rotation(-2.3f));
				pose.mulPose(Axis.ZP.rotation((i - 1.5f) * 0.25f));
				IceDraw.crystal(pose, i % 2 == 0 ? buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 3.2f * px, (9.0f - Math.abs(i - 1.5f) * 2.0f) * px, 1.0f * px,
						0xF0FFFFFF, lit);
				pose.popPose();
			}
			pose.popPose();
			// the shoulders: three crystals bursting up off each arm
			for (String arm : new String[] { "right_arm", "left_arm" }) {
				float cx = arm.equals("right_arm") ? -11.0f : 11.0f;
				float lean = arm.equals("right_arm") ? -0.35f : 0.35f;
				pose.pushPose();
				root.getChild(arm).translateAndRotate(pose);
				for (int i = 0; i < 3; i++) {
					pose.pushPose();
					pose.translate((cx + (i - 1) * 1.2f) * px, -2.4f * px, (i - 1) * 2.0f * px);
					pose.mulPose(Axis.XP.rotation(Mth.PI + (i - 1) * 0.3f));
					pose.mulPose(Axis.ZP.rotation(lean));
					IceDraw.crystal(pose, i == 1 ? buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 2.6f * px, (i == 1 ? 8.0f : 5.5f) * px, 0.8f * px, 0xF0FFFFFF, lit);
					pose.popPose();
				}
				// icicle knuckles on the fist
				for (int i = -1; i <= 1; i += 2) {
					pose.pushPose();
					pose.translate((cx + i * 1.2f) * px, 27.0f * px, -3.0f * px);
					pose.mulPose(Axis.XP.rotation(-1.6f));
					IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 1.6f * px, 3.0f * px, 0, 0xE8F4FBFF, lit);
					pose.popPose();
				}
				pose.popPose();
			}
			// the brow crown and the eyes
			pose.pushPose();
			root.getChild("head").translateAndRotate(pose);
			for (int i = -1; i <= 1; i++) {
				pose.pushPose();
				pose.translate(i * 2.5f * px, -11.8f * px, -3.0f * px);
				pose.mulPose(Axis.XP.rotation(Mth.PI + 0.25f));
				pose.mulPose(Axis.ZP.rotation(i * 0.4f));
				IceDraw.crystal(pose, i == 0 ? buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 2.0f * px, (i == 0 ? 6.0f : 4.0f) * px, 0, 0xF0FFFFFF, lit);
				pose.popPose();
			}
			float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
			int eye = ((int) (255 * pulse) << 24) | 0x9EE6FF;
			IceDraw.box(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), -3.2f * px, -8.2f * px, -5.62f * px, -1.2f * px, -7.0f * px, -5.52f * px, 1.0f, eye, LightTexture.FULL_BRIGHT);
			IceDraw.box(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 1.2f * px, -8.2f * px, -5.62f * px, 3.2f * px, -7.0f * px, -5.52f * px, 1.0f, eye, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
	}
}
