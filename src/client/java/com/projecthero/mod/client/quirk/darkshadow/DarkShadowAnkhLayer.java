package com.projecthero.mod.client.quirk.darkshadow;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp: Black Ankh -- Dark Shadow wrapped round its user as armour, for every viewer while the
 * mode is on:
 * <ul>
 *   <li>the feathered shell: the player model a little inflated, in {@code dark_shadow/ankh.png} (body, arms, legs);</li>
 *   <li>the GeckoLib parts ({@link DarkShadowCreature.AnkhRenderer}), each drawn on its own limb so they follow every pose:
 *       the bird-skull helmet with the beak and the glowing eyes (head), shoulder plumes and clawed gauntlets (arms), the
 *       back ridge (body) and, for Abyssal Black Ankh, the great shadow wings (body) beating -- held wide while gliding.</li>
 * </ul>
 * The parts' coordinates are each limb's own space with Y up: {@code limb.translateAndRotate} then a half turn about Z.
 */
public class DarkShadowAnkhLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/quirk/dark_shadow/ankh.png");
	private static final Map<Integer, DarkShadowCreature.Shape> SHAPES = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> TRAIL = new ConcurrentHashMap<>();
	private static PlayerModel<AbstractClientPlayer> wide;
	private static PlayerModel<AbstractClientPlayer> slim;
	private static DarkShadowCreature.AnkhRenderer parts;

	public DarkShadowAnkhLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	static void reset() {
		SHAPES.clear();
		TRAIL.clear();
	}

	private static PlayerModel<AbstractClientPlayer> model(boolean isSlim) {
		if (isSlim ? slim == null : wide == null) {
			ModelPart root = LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.45f), isSlim), 64, 64).bakeRoot();
			PlayerModel<AbstractClientPlayer> m = new PlayerModel<>(root, isSlim);
			if (isSlim) {
				slim = m;
			} else {
				wide = m;
			}
		}
		return isSlim ? slim : wide;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !DarkShadow.is(player) || !DarkShadow.ankh(player)) {
			return;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return;
		}
		// ---- the shell
		boolean isSlim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> m = model(isSlim);
		getParentModel().copyPropertiesTo(m);
		m.setAllVisible(true);
		m.head.visible = false;
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		VertexConsumer shell = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		m.renderToBuffer(pose, shell, light, overlay, 0xFFFFFFFF);

		// ---- the parts
		if (parts == null) {
			parts = new DarkShadowCreature.AnkhRenderer();
		}
		DarkShadowCreature.Shape sh = SHAPES.computeIfAbsent(player.getId(), k -> new DarkShadowCreature.Shape("dark_shadow_ankh"));
		boolean abyssal = DarkShadow.abyssal(player);
		sh.clip = abyssal ? (player.isFallFlying() ? "glide" : "wings") : "idle";
		sh.clipKey = 0L;
		PlayerModel<AbstractClientPlayer> pm = getParentModel();
		part(pose, buffers, light, partialTick, sh, pm.head, Set.of("helmet"));
		part(pose, buffers, light, partialTick, sh, pm.rightArm, Set.of("plume_r", "gauntlet_r"));
		part(pose, buffers, light, partialTick, sh, pm.leftArm, Set.of("plume_l", "gauntlet_l"));
		boolean blackBody = DarkShadow.blackBody(player);
		if (blackBody) {
			// v0.17.1 Abyssal Black Body: the Ankh upgraded -- great wings, a ridge of shadow and violet fire in the eyes
			pose.pushPose();
			pm.body.translateAndRotate(pose);
			pose.translate(0, 0.25f, 0.1f);
			pose.scale(1.45f, 1.45f, 1.45f);
			pose.translate(0, -0.25f, -0.1f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f));
			DarkShadowCreature.ankhOnly = Set.of("back", "wings");
			parts.render(pose, sh, buffers, null, null, light, partialTick);
			pose.popPose();
			pose.pushPose();
			pm.head.translateAndRotate(pose);
			pose.translate(0, -0.3f, -0.26f);
			float pulse = 0.75f + 0.25f * net.minecraft.util.Mth.sin(ageInTicks * 0.6f);
			var vc = com.projecthero.mod.client.quirk.fx.QuirkFxDraw.glow(buffers, com.projecthero.mod.client.quirk.fx.QuirkFxDraw.GLOW);
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 0.11f, 0, 0);
				com.projecthero.mod.client.quirk.fx.QuirkFxDraw.shell(vc, pose.last(), 0.07f + 0.02f * pulse, 0.2f, player.getId() * 7L + side, ageInTicks * 0.5f,
						0xC8A0FF, 0.9f);
				pose.popPose();
			}
			pose.popPose();
		} else {
			part(pose, buffers, light, partialTick, sh, pm.body, abyssal ? Set.of("back", "wings") : Set.of("back"));
		}
		DarkShadowCreature.ankhOnly = Set.of();

		// ---- a dark feather trail while the wings carry you
		if (abyssal && player.isFallFlying()) {
			long now = player.level().getGameTime();
			Long last = TRAIL.get(player.getId());
			if ((last == null || last != now) && !Minecraft.getInstance().isPaused()) {
				TRAIL.put(player.getId(), now);
				var rnd = player.getRandom();
				Vec3 c = player.position().add(0, 0.6, 0);
				for (int i = 0; i < 3; i++) {
					player.level().addParticle(i == 0 ? DarkShadowCombat.VIOLET : DarkShadowCombat.SHADOW_BIG, c.x + (rnd.nextDouble() - 0.5) * 2.4,
							c.y + (rnd.nextDouble() - 0.5) * 0.6, c.z + (rnd.nextDouble() - 0.5) * 2.4, 0, -0.01, 0);
				}
			}
		}
	}

	private static void part(PoseStack pose, MultiBufferSource buffers, int light, float partial, DarkShadowCreature.Shape sh, ModelPart limb, Set<String> only) {
		pose.pushPose();
		limb.translateAndRotate(pose);
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		DarkShadowCreature.ankhOnly = only;
		parts.render(pose, sh, buffers, null, null, light, partial);
		pose.popPose();
	}
}
