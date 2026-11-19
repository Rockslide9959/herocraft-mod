package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.moonknight.MoonKnightCapeLayer;
import com.projecthero.mod.spin.GyroSuitItem;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.17.4 (user: "make a cape model and remove the cape from the back of the skin"): Gyro's green cape as real cloth instead of paint
 * on the suit's back. Drawn while his coat is worn, hung where vanilla hangs a cape (the cloak's spot over a chestplate) with exactly
 * vanilla's cape swing ({@link MoonKnightCapeLayer#cloakSwing}: lags behind when he moves, bobs with his walk, lifts when he crouches);
 * the wearer's own Minecraft cape is hidden meanwhile ({@code GyroCoatCapeMixin}).
 *
 * <p>v0.17.5 (user: "his cape almost splits off into 2 capes ... make it look like that"): one 10 x 9 panel across the shoulders that
 * splits below the waist into two tails, a 2 px gap between them. Each tail is a chain of three boxes (4 x 4, 3 x 3, 2 x 2: it narrows
 * from the gap side, so the split opens up like an inverted V) hinged at the top of each box, flaring out to its side and trailing back
 * a little more, the further down and the faster he moves; the tips sway gently. 18 px long in all (mid-calf), like the old 16 px panel
 * plus a little.
 *
 * <p>Also v0.17.5 (user: "make the rim of gyros hat come out slightly further"): while his hat is worn, a flat 11 x 1 x 11 brim on the
 * head at the hat's painted brim line (tipped up 5 degrees at the front like the paint), 1 px further out on every side than the hat
 * layer's painted brim.
 *
 * <p>Texture {@code textures/entity/spin/gyro_cape.png} is an atlas for both (cape boxes in the top 20 rows, the brim below; layout in
 * {@code scratchpad/spin/fix_gyro_suit_skin.js}, which makes it, like the suit skins); without it nothing is drawn.
 */
public class GyroCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/spin/gyro_cape.png");
	/** The texture is there (null: not looked up since the last resource reload). */
	private static Boolean present;
	/** The cape (its tails as children) and the brim, baked once. */
	private static ModelPart cape, brim;
	private static ModelPart leftTail, leftMid, leftTip, rightTail, rightMid, rightTip;

	public GyroCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	static void initialize() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("gyro_cape");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				present = null;
			}
		});
	}

	/** The cape and brim boxes (depth 1 px; uv layout matches the atlas the skin script writes). */
	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition c = root.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10, 9, 1),
				PartPose.ZERO);
		// left tail (-x): hinged under the panel at its own middle; narrows from the gap (+x) side
		PartDefinition l1 = c.addOrReplaceChild("left_tail", CubeListBuilder.create().texOffs(0, 10).addBox(-2.0F, 0.0F, -1.0F, 4, 4, 1),
				PartPose.offset(-3.0F, 9.0F, 0.0F));
		PartDefinition l2 = l1.addOrReplaceChild("left_mid", CubeListBuilder.create().texOffs(12, 10).addBox(-2.0F, 0.0F, -1.0F, 3, 3, 1),
				PartPose.offset(0.0F, 4.0F, 0.0F));
		l2.addOrReplaceChild("left_tip", CubeListBuilder.create().texOffs(22, 10).addBox(-2.0F, 0.0F, -1.0F, 2, 2, 1),
				PartPose.offset(0.0F, 3.0F, 0.0F));
		// right tail (+x), the mirror image
		PartDefinition r1 = c.addOrReplaceChild("right_tail", CubeListBuilder.create().texOffs(0, 15).addBox(-2.0F, 0.0F, -1.0F, 4, 4, 1),
				PartPose.offset(3.0F, 9.0F, 0.0F));
		PartDefinition r2 = r1.addOrReplaceChild("right_mid", CubeListBuilder.create().texOffs(12, 15).addBox(-1.0F, 0.0F, -1.0F, 3, 3, 1),
				PartPose.offset(0.0F, 4.0F, 0.0F));
		r2.addOrReplaceChild("right_tip", CubeListBuilder.create().texOffs(22, 15).addBox(0.0F, 0.0F, -1.0F, 2, 2, 1),
				PartPose.offset(0.0F, 3.0F, 0.0F));
		// the hat brim, in head space: centred on the brim line (y -4), front tipped up
		root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 20).addBox(-5.5F, -0.5F, -5.5F, 11, 1, 11),
				PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -5.0F * Mth.DEG_TO_RAD, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static void bake() {
		if (cape != null) {
			return;
		}
		ModelPart root = createLayer().bakeRoot();
		cape = root.getChild("cape");
		brim = root.getChild("brim");
		leftTail = cape.getChild("left_tail");
		leftMid = leftTail.getChild("left_mid");
		leftTip = leftMid.getChild("left_tip");
		rightTail = cape.getChild("right_tail");
		rightMid = rightTail.getChild("right_mid");
		rightTip = rightMid.getChild("right_tip");
	}

	private static boolean present() {
		if (present == null) {
			present = Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isPresent();
		}
		return present;
	}

	/** Gyro's cape is drawn on this player (the coat is on and the cape texture exists). */
	public static boolean shown(AbstractClientPlayer player) {
		return GyroSuitItem.wornIn(player, EquipmentSlot.CHEST) && present();
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !present()) {
			return;
		}
		bake();
		VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		if (GyroSuitItem.wornIn(player, EquipmentSlot.HEAD) && model.head.visible) {
			pose.pushPose();
			model.head.translateAndRotate(pose);
			brim.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		if (!GyroSuitItem.wornIn(player, EquipmentSlot.CHEST) || !model.body.visible) {
			return;
		}
		pose.pushPose();
		float[] cloak = MoonKnightCapeLayer.cloakSwing(player, partialTick);
		float swing = cloak[0];
		float side = cloak[1];
		// v0.17.5: over a backpack it hangs from the bag's top outer edge, in the body's frame, never swinging back into it
		float bagLean = com.projecthero.mod.client.backpack.BackpackLayer.hangCapeFromBag(player, model, pose);
		boolean onBag = !Float.isNaN(bagLean);
		if (onBag) {
			swing = Math.max(swing - bagLean, com.projecthero.mod.client.backpack.BackpackLayer.CAPE_MIN_SWING);
			side *= 0.6F;
		} else {
			pose.translate(0.0F, 0.0F, 0.125F + 0.01F); // vanilla's offset, a hair further out over the suit's base layer
		}
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - side / 2.0F));
		// where vanilla's cloak part sits over a chestplate (PlayerModel.setupAnim; the coat is one)
		boolean crouch = player.isCrouching();
		cape.y = onBag ? 0.0F : crouch ? 0.8F : -0.85F;
		cape.z = onBag ? 0.0F : crouch ? 0.3F : -1.1F;
		poseTails(cloak[0], ageInTicks);
		cape.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/**
	 * The tails' hinges for this frame. In the cape's space (turned 180 degrees) -z is away from his back, so a negative xRot trails a
	 * box back; +zRot swings a box's bottom toward -x, the left tail's side. {@code swing} is vanilla's cape tilt (6 at rest).
	 */
	private static void poseTails(float swing, float age) {
		float move = Mth.clamp((swing - 6.0F) / 60.0F, 0.0F, 1.0F); // 0 standing .. 1 running
		float sway = Mth.sin(age * 0.09F) * 1.5F;
		float flare = 7.0F + 6.0F * move;
		float trail = 1.5F + 12.0F * move;
		float flutter = Mth.sin(age * 0.13F) * (1.0F + 3.0F * move);
		leftTail.zRot = (flare + sway) * Mth.DEG_TO_RAD;
		rightTail.zRot = (-flare + sway) * Mth.DEG_TO_RAD;
		leftTail.xRot = -(trail + flutter) * Mth.DEG_TO_RAD;
		rightTail.xRot = -(trail - flutter) * Mth.DEG_TO_RAD;
		float bend = (2.0F + 8.0F * move) * Mth.DEG_TO_RAD;
		leftMid.xRot = rightMid.xRot = -bend;
		leftTip.xRot = rightTip.xRot = -bend * 1.2F;
		leftMid.zRot = 4.0F * Mth.DEG_TO_RAD;
		rightMid.zRot = -4.0F * Mth.DEG_TO_RAD;
		leftTip.zRot = 5.0F * Mth.DEG_TO_RAD;
		rightTip.zRot = -5.0F * Mth.DEG_TO_RAD;
	}
}
