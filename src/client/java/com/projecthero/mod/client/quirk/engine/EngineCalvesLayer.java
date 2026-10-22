package com.projecthero.mod.client.quirk.engine;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.quirk.engine.Engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the calf engines ({@link EngineCalves}) on every player holding the Engine Quirk, for every viewer --
 * registered after the Quirk suit layer, so they sit over the skin, the trousers, a Quirk suit and armour. In Recipro Turbo
 * it also wraps the body in a <b>blue speed aura</b>: a swirling energy shell (the charged-creeper swirl, tinted blue,
 * pulsing) a little out from the body.
 */
public class EngineCalvesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation SWIRL = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
	private static PlayerModel<AbstractClientPlayer> auraWide;
	private static PlayerModel<AbstractClientPlayer> auraSlim;

	public EngineCalvesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Engine.hasEngines(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Matrix4f toWorld = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z);
		EngineCalves.render(pose, buffers, light, player, model, toWorld, partialTick);
		if (Engine.turbo(player)) {
			aura(pose, buffers, light, player, model, ageInTicks);
		}
	}

	/** Recipro Turbo's blue speed aura. */
	static void aura(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidModel<?> parent, float age) {
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> m = aura(slim);
		copyPose(parent, m);
		float scroll = age * 0.035f;
		float pulse = 0.75f + 0.25f * (float) Math.sin(age * 0.45f);
		int r = (int) (40 * pulse), g = (int) (115 * pulse), b = (int) (235 * pulse);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(SWIRL, scroll % 1f, (scroll * 0.6f) % 1f)), light, OverlayTexture.NO_OVERLAY,
				0xFF000000 | r << 16 | g << 8 | b);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void copyPose(HumanoidModel<?> parent, PlayerModel<AbstractClientPlayer> m) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.head.visible = parent.head.visible;
		m.body.visible = parent.body.visible;
		m.rightArm.visible = parent.rightArm.visible;
		m.leftArm.visible = parent.leftArm.visible;
		m.rightLeg.visible = parent.rightLeg.visible;
		m.leftLeg.visible = parent.leftLeg.visible;
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
	}

	private static PlayerModel<AbstractClientPlayer> aura(boolean slim) {
		if (slim) {
			if (auraSlim == null) {
				auraSlim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.9f), true), 64, 64).bakeRoot(), true);
			}
			return auraSlim;
		}
		if (auraWide == null) {
			auraWide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.9f), false), 64, 64).bakeRoot(), false);
		}
		return auraWide;
	}
}
