package com.projecthero.mod.client.shazam;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Shazam: everything the Wizard wears beyond the skin, as extra model parts that follow the player model's parts
 * ({@link WizardShazamModel}): wild grey hair round the head and down the back, a long beard that hangs straight down whatever his
 * head does, the red headband, a rust mantle down the back, a drape over the left shoulder, the robe's long skirt over the legs, bare
 * toes peeking out under the hem, and his <b>staff</b> -- carved dark wood topped with a white-blue crystal (the film's) standing on
 * the ground in his right hand. The chest bolt ({@code wizard_shazam_glow.png}) and the crystal with its soft halo are drawn again
 * full-bright, so they glow at night. Textures from {@code scratchpad/shazam/gen_wizard_textures.js} (UVs match).
 */
public class WizardShazamLayer extends RenderLayer<WizardShazamEntity, PlayerModel<WizardShazamEntity>> {
	private static final ResourceLocation PARTS = ProjectHeroMod.id("textures/entity/shazam/wizard_shazam_parts.png");
	private static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/shazam/wizard_shazam_glow.png");
	/** Model y of the ground under the feet. */
	private static final float GROUND = 24f;

	private final ModelPart head;
	private final ModelPart beardHang;
	private final ModelPart body;
	private final ModelPart leftArm;
	private final ModelPart skirt;
	private final ModelPart rightToe;
	private final ModelPart leftToe;
	private final ModelPart staff;
	private final ModelPart crystal;
	private final ModelPart halo;

	public WizardShazamLayer(RenderLayerParent<WizardShazamEntity, PlayerModel<WizardShazamEntity>> parent) {
		super(parent);
		ModelPart root = createLayer().bakeRoot();
		head = root.getChild("head");
		beardHang = head.getChild("beard_hang");
		body = root.getChild("body");
		leftArm = root.getChild("left_arm");
		skirt = root.getChild("skirt");
		rightToe = root.getChild("right_toe");
		leftToe = root.getChild("left_toe");
		staff = root.getChild("staff");
		crystal = staff.getChild("crystal");
		halo = staff.getChild("halo");
	}

	static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// --- on the head: beard, mane under the headband, a bulge of curls at the back, the headband
		PartDefinition h = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4f, -2f, -5f, 8, 4, 2) // the beard over the jaw, the moustache on top
				.texOffs(24, 0).addBox(-5f, -6f, -2f, 10, 7, 7) // the mane, bushing out under the band round the sides and back
				.texOffs(60, 20).addBox(-4f, -5f, 5f, 8, 7, 1) // more curls at the back
				.texOffs(60, 30).addBox(-4f, -9.5f, -3.5f, 8, 1, 8) // wild curls breaking the top of the head
				.texOffs(60, 8).addBox(-4.5f, -7f, -4.5f, 9, 1, 9, new CubeDeformation(0.2f)), // the red headband, tight round the brow
				PartPose.ZERO);
		h.addOrReplaceChild("beard_hang", CubeListBuilder.create()
				.texOffs(0, 8).addBox(-3f, 0f, -1f, 6, 2, 2)
				.texOffs(0, 14).addBox(-2f, 2f, -1f, 4, 1, 2), // short enough to leave the chest bolt in sight
				PartPose.offset(0f, 2f, -4f));
		// --- on the body: long hair down the back, the mantle under it, locks over the shoulders
		root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 24).addBox(-4.5f, -1f, 3f, 9, 8, 2) // long hair down the back, tapering
				.texOffs(24, 30).addBox(-3.5f, 7f, 3.2f, 7, 7, 2)
				.texOffs(24, 24).addBox(-2.5f, 14f, 3.5f, 5, 3, 1)
				.texOffs(0, 44).addBox(-5f, 0f, 2f, 10, 21, 1, new CubeDeformation(0.1f))
				.texOffs(36, 44).addBox(2.4f, -0.5f, -3.6f, 2, 9, 1)
				.texOffs(44, 44).addBox(-4.4f, -0.5f, -3.6f, 2, 9, 1),
				PartPose.ZERO);
		// --- the drape over the left shoulder, a loose sleeve to the elbow
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(52, 44).addBox(-1.5f, -2.5f, -2.5f, 5, 9, 5, new CubeDeformation(0.15f)), PartPose.ZERO);
		// --- the robe's skirt, hung from the hips (placed each frame)
		root.addOrReplaceChild("skirt", CubeListBuilder.create()
				.texOffs(0, 70).addBox(-4.5f, -0.5f, -2.5f, 9, 6, 5)
				.texOffs(30, 70).addBox(-5f, 5.5f, -3f, 10, 5, 6),
				PartPose.ZERO);
		root.addOrReplaceChild("right_toe", CubeListBuilder.create().texOffs(64, 70).addBox(-1.5f, 11f, -3f, 3, 1, 1), PartPose.ZERO);
		root.addOrReplaceChild("left_toe", CubeListBuilder.create().texOffs(64, 70).addBox(-1.5f, 11f, -3f, 3, 1, 1), PartPose.ZERO);
		// --- the staff, its origin on the ground under the right hand
		PartDefinition s = root.addOrReplaceChild("staff", CubeListBuilder.create()
				.texOffs(110, 0).addBox(-1f, -34f, -1f, 2, 34, 2) // the carved shaft
				.texOffs(106, 70).addBox(-1f, -1.5f, -1f, 2, 2, 2, new CubeDeformation(0.2f)) // the iron foot
				.texOffs(100, 24).addBox(-1f, -12f, -1f, 2, 4, 2, new CubeDeformation(0.3f)) // the leather grip under the hand
				.texOffs(100, 40).addBox(-1.5f, -6f, -1.5f, 3, 1, 3) // knots
				.texOffs(100, 40).addBox(-1.5f, -24f, -1.5f, 3, 1, 3)
				.texOffs(100, 32).addBox(-1.5f, -35.5f, -1.5f, 3, 2, 3), // the carved knob
				PartPose.ZERO);
		for (int i = 0; i < 4; i++) { // four prongs cradling the crystal
			float sx = (i & 1) == 0 ? -1f : 1f, sz = (i & 2) == 0 ? -1f : 1f;
			s.addOrReplaceChild("prong_" + i, CubeListBuilder.create().texOffs(100, 46).addBox(-0.5f, -6f, -0.5f, 1, 6, 1),
					PartPose.offsetAndRotation(sx, -35f, sz, -sz * 0.22f, 0f, sx * 0.22f));
		}
		s.addOrReplaceChild("crystal", CubeListBuilder.create()
				.texOffs(80, 70).addBox(-1.5f, -4f, -1.5f, 3, 5, 3)
				.texOffs(96, 70).addBox(-1f, -6f, -1f, 2, 2, 2),
				PartPose.offset(0f, -37f, 0f));
		s.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(80, 80).addBox(-2.5f, -3.5f, -2.5f, 5, 7, 5),
				PartPose.offset(0f, -39.5f, 0f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, WizardShazamEntity wizard, float limbSwing, float limbSwingAmount,
			float partialTick, float age, float netHeadYaw, float headPitch) {
		if (wizard.isInvisible()) {
			return;
		}
		PlayerModel<WizardShazamEntity> m = getParentModel();
		int overlay = LivingEntityRenderer.getOverlayCoords(wizard, 0f);
		head.copyFrom(m.head);
		body.copyFrom(m.body);
		leftArm.copyFrom(m.leftArm);
		rightToe.copyFrom(m.rightLeg);
		leftToe.copyFrom(m.leftLeg);
		// the beard hangs straight down: undo most of the head's nod and tilt
		beardHang.xRot = -m.head.xRot * 0.85f;
		beardHang.zRot = -m.head.zRot;
		// the skirt hangs from the hips: the bottom of the hunched torso, following the legs a little
		Vector3f hip = point(m.body, 0f, 12f, 0f);
		skirt.setPos(hip.x, hip.y - 0.3f, hip.z);
		skirt.xRot = (m.rightLeg.xRot + m.leftLeg.xRot) * 0.25f + 0.04f;
		skirt.yRot = 0f;
		skirt.zRot = 0f;
		// the staff stands on the ground straight up through the right fist, swaying a hair with his breath
		Vector3f hand = point(m.rightArm, -1f, 9.5f, 0f);
		staff.setPos(hand.x, GROUND, hand.z);
		staff.xRot = Mth.sin(age * 0.07f) * 0.01f;
		staff.zRot = 0.02f;
		crystal.yRot = age * 0.05f;
		halo.visible = false;

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
		head.render(pose, vc, light, overlay);
		body.render(pose, vc, light, overlay);
		leftArm.render(pose, vc, light, overlay);
		skirt.render(pose, vc, light, overlay);
		rightToe.render(pose, vc, light, overlay);
		leftToe.render(pose, vc, light, overlay);
		staff.render(pose, vc, light, overlay);

		// the glow: the chest bolt and the crystal, full-bright; the crystal's halo pulses and flickers
		float pulse = 0.82f + 0.12f * Mth.sin(age * 0.21f) + 0.06f * Mth.sin(age * 0.73f + 1.3f);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), light, OverlayTexture.NO_OVERLAY, grey(0.75f + 0.25f * pulse));
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(PARTS));
		pose.pushPose();
		staff.translateAndRotate(pose);
		crystal.render(pose, glow, light, OverlayTexture.NO_OVERLAY, grey(pulse));
		halo.visible = true;
		float k = 0.9f + 0.25f * pulse;
		halo.xScale = k;
		halo.yScale = k;
		halo.zScale = k;
		halo.yRot = -age * 0.03f;
		halo.render(pose, glow, light, OverlayTexture.NO_OVERLAY, grey(pulse));
		pose.popPose();
	}

	/** A point in a part's own space, in model space (the part's pivot and rotation applied). */
	private static Vector3f point(ModelPart part, float x, float y, float z) {
		Matrix4f mat = new Matrix4f().translation(part.x, part.y, part.z).rotateZYX(part.zRot, part.yRot, part.xRot);
		return mat.transformPosition(new Vector3f(x, y, z));
	}

	private static int grey(float f) {
		int c = Mth.clamp((int) (f * 255), 0, 255);
		return 0xFF000000 | c << 16 | c << 8 | c;
	}
}
