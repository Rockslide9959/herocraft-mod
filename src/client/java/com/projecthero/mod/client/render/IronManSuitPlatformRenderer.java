package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "Hall of Armor" display ("changes 16"): renders the racked suit as the actual worn armour <em>model</em> on an
 * invisible, slowly-rotating armour stand above the platform (vanilla {@code HumanoidArmorLayer}, so the GeckoLib suit
 * models show exactly as on a player). The block entity syncs its contents on every change, so this always reflects
 * what is really stored.
 *
 * <p>v0.15.4: the platform's robotic arms (v0.15.1 deploy, v0.15.3 retrieve) are gone -- suiting up is done by the Stark
 * Gantry's arms now ({@link StarkGantryRenderer}, which reuses their model and texture), so this only draws the rack.
 */
public class IronManSuitPlatformRenderer implements BlockEntityRenderer<IronManSuitPlatformBlockEntity> {
	private static final Map<IronManSuitPlatformBlockEntity, ArmorStand> STANDS = new WeakHashMap<>();

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final float RACK_SCALE = 0.62f;
	/** Height the racked suit stands at above the block (master: the raised floor plate of the new platform model). */
	public static final double DISPLAY_Y_OFFSET = 0.2;

	public IronManSuitPlatformRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(IronManSuitPlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		Level level = be.getLevel();
		if (level == null) {
			return;
		}
		ArmorStand rack = STANDS.computeIfAbsent(be, k -> makeStand(level));
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = be.getItem(i);
			if (!ItemStack.matches(rack.getItemBySlot(SLOTS[i]), piece)) {
				rack.setItemSlot(SLOTS[i], piece);
			}
			any |= !piece.isEmpty();
		}
		if (!any) {
			return;
		}
		// v0.15.8, user request: the Suitcase tab -- the racked Mark 5 folds itself up (the suit-down backwards, twice as
		// fast), then the case forms at its chest, drops to the pad and hops off to its owner
		float packAge = be.packAge(partialTick);
		if (packAge >= 0f) {
			renderPack(be, rack, packAge, partialTick, pose, buffers, packedLight);
			return;
		}
		// v0.15.9, user request: a docked Mark 5 Suitcase does exactly that backwards -- the case hops in, lands on the pad,
		// rises to chest height and opens, and the suit builds itself up out of it on the rack
		float unpackAge = be.unpackAge(partialTick);
		if (unpackAge >= 0f) {
			float back = IronManSuitPlatformBlockEntity.PACK_TICKS - Math.min(unpackAge, IronManSuitPlatformBlockEntity.UNPACK_TICKS);
			renderPack(be, rack, Math.max(0f, back), partialTick, pose, buffers, packedLight);
			return;
		}
		float time = level.getGameTime() + partialTick;
		float spin = time * 1.4f;
		float bob = (float) Math.sin(time * 0.06f) * 0.03f;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		pose.pushPose();
		pose.translate(0.5, DISPLAY_Y_OFFSET + bob, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
		dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		pose.popPose();
		dispatcher.setRenderShadow(true);
		// v0.15.20, user request: welding arms rise out of the pad's open front while a damaged suit is repaired; the suit
		// keeps turning between them like a turntable
		float arms = be.repairArms(partialTick);
		if (arms > 0.001f) {
			var state = be.getBlockState();
			float facingYaw = state.hasProperty(com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlock.FACING)
					? -state.getValue(com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlock.FACING).toYRot() : 0f;
			pose.pushPose();
			pose.translate(0.5, DISPLAY_Y_OFFSET, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(facingYaw));
			renderWelding(be, level, pose, buffers, packedLight, arms, time, facingYaw);
			pose.popPose();
		}
	}

	// ---------------- v0.15.20: the repair welders ----------------

	/** Arm scale (the gantry arm is ~1.9 blocks long; these are a little over half that). */
	private static final float ARM_SCALE = 0.55f;
	/** How long (ticks) a welded spot keeps glowing behind the torch as it cools. */
	private static final int SEAM_TICKS = 10;
	private static final Map<IronManSuitPlatformBlockEntity, Long> LAST_SPARK = new WeakHashMap<>();

	/**
	 * Where arm {@code side} (-1 left / +1 right) holds its torch tip at {@code t}, in block units (origin = the pad's
	 * centre at the suit's feet, +Z = the platform's open front, away from the back panel): sweeping up and down the
	 * turning suit, out of step with the other arm, with a small side-to-side weave like a weld bead.
	 */
	private static net.minecraft.world.phys.Vec3 torchTip(int side, float t) {
		float phase = side > 0 ? 0f : 2.6f;
		double y = 0.30 + 0.38 * (0.5 + 0.5 * Math.sin(t * 0.045 + phase)) + 0.30 * (side > 0 ? 1 : 0.4);
		double x = side * (0.07 + 0.04 * Math.sin(t * 0.21 + phase));
		double z = 0.21 + 0.015 * Math.sin(t * 0.9 + phase);
		return new net.minecraft.world.phys.Vec3(x, y, z);
	}

	private void renderWelding(IronManSuitPlatformBlockEntity be, Level level, PoseStack pose, MultiBufferSource buffers,
			int light, float arms, float time, float frameYaw) {
		float s = ARM_SCALE * (0.2f + 0.8f * arms); // they unfold out of the pad hatch as they rise
		net.minecraft.world.phys.Vec3[] tips = new net.minecraft.world.phys.Vec3[2];
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? -1 : 1;
			// the shoulder rides up out of the pad's front corner; the torch swings from tucked to the suit
			net.minecraft.world.phys.Vec3 shoulder = new net.minecraft.world.phys.Vec3(side * 0.36, -0.12 + 0.18 * arms, 0.34);
			net.minecraft.world.phys.Vec3 tucked = shoulder.add(side * -0.05, 0.12, -0.05);
			net.minecraft.world.phys.Vec3 tip = tucked.lerp(torchTip(side, time), arms);
			tips[i] = tip;
			pose.pushPose();
			pose.scale(s, s, s);
			StarkGantryRenderer.drawWeldingArm(pose, buffers, light, shoulder.scale(1.0 / s), tip.scale(1.0 / s),
					new net.minecraft.world.phys.Vec3(side * 0.8, 0.6, 0.6));
			pose.popPose();
		}
		if (arms < 0.95f) {
			return; // still rising / sinking: no torch yet
		}
		// the torch flame + the seam cooling behind it (white-hot -> orange -> gone), additive so it glows
		com.mojang.blaze3d.vertex.VertexConsumer glow = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? -1 : 1;
			for (int k = SEAM_TICKS; k >= 0; k--) {
				float heat = 1f - k / (float) SEAM_TICKS;
				net.minecraft.world.phys.Vec3 at = torchTip(side, time - k).add(0, 0, -0.02);
				float r = 1f;
				float g = 0.35f + 0.6f * heat * heat;
				float b = 0.1f + 0.85f * heat * heat * heat;
				float size = k == 0 ? 0.035f + 0.01f * (float) Math.sin(time * 3.1 + i) : 0.012f + 0.012f * heat;
				glowQuad(glow, p, at, size, r, g, b, k == 0 ? 0.95f : 0.55f * heat);
			}
		}
		// sparks: a few per tick per torch, flung out of the weld in world space
		long now = level.getGameTime();
		Long last = LAST_SPARK.get(be);
		if (last == null || last != now) {
			LAST_SPARK.put(be, now);
			var rand = level.random;
			double yaw = Math.toRadians(frameYaw);
			for (net.minecraft.world.phys.Vec3 tip : tips) {
				// platform frame -> world: rotate by the facing about Y, then offset to the pad
				double wx = tip.x * Math.cos(yaw) + tip.z * Math.sin(yaw);
				double wz = -tip.x * Math.sin(yaw) + tip.z * Math.cos(yaw);
				double x = be.getBlockPos().getX() + 0.5 + wx;
				double y = be.getBlockPos().getY() + DISPLAY_Y_OFFSET + tip.y;
				double z = be.getBlockPos().getZ() + 0.5 + wz;
				int n = 1 + rand.nextInt(3);
				for (int j = 0; j < n; j++) {
					level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z,
							(rand.nextDouble() - 0.5) * 0.25, rand.nextDouble() * 0.12, (rand.nextDouble() - 0.5) * 0.25);
				}
				if (rand.nextInt(3) == 0) {
					level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x, y, z,
							(rand.nextDouble() - 0.5) * 0.06, 0.02, (rand.nextDouble() - 0.5) * 0.06);
				}
				if (rand.nextInt(6) == 0) {
					level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y + 0.05, z, 0, 0.02, 0);
				}
			}
		}
	}

	/** A small glowing cube of light (all six faces, so it reads from any angle as the rack turns). */
	private static void glowQuad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p, net.minecraft.world.phys.Vec3 c,
			float h, float r, float g, float b, float a) {
		float x0 = (float) c.x - h, x1 = (float) c.x + h;
		float y0 = (float) c.y - h, y1 = (float) c.y + h;
		float z0 = (float) c.z - h, z1 = (float) c.z + h;
		float[][] faces = {
				{ x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 },
				{ x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0 },
				{ x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 },
				{ x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1 },
				{ x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0 },
				{ x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 } };
		for (float[] f : faces) {
			for (int v = 0; v < 12; v += 3) {
				vc.addVertex(p, f[v], f[v + 1], f[v + 2]).setColor(r, g, b, a);
			}
		}
	}

	/**
	 * v0.15.8: the fold -- the stand at its Mark 5 frame, then the suitcase forming, dropping and hopping away. (v0.15.9:
	 * also the unfold, played with {@code age} running backwards.)
	 */
	private void renderPack(IronManSuitPlatformBlockEntity be, ArmorStand rack, float age, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light) {
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		float frame = com.projecthero.mod.ironman.suit.IronManMk5Suitcase.UP_TICKS - 6 - 2f * age; // 134 -> 26
		if (age < IronManSuitPlatformBlockEntity.PACK_FOLD_TICKS) {
			dispatcher.setRenderShadow(false);
			pose.pushPose();
			pose.translate(0.5, DISPLAY_Y_OFFSET, 0.5);
			pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
			com.projecthero.mod.client.ironman.IronManGantryBuild.standMk5Frame = frame;
			try {
				dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, light);
			} finally {
				com.projecthero.mod.client.ironman.IronManGantryBuild.standMk5Frame = Float.NaN;
			}
			pose.popPose();
			dispatcher.setRenderShadow(true);
		}
		// the case: grows out of the chest as the chest top folds away, closes, drops to the pad, then hops off
		float caseFrom = IronManSuitPlatformBlockEntity.PACK_FOLD_TICKS - 4;
		float grow = smooth((age - caseFrom) / 8f);
		if (grow <= 0.01f) {
			return;
		}
		float drop = smooth((age - (caseFrom + 8)) / 5f);
		float hop = smooth((age - (caseFrom + 14)) / 6f);
		float chestY = (float) (DISPLAY_Y_OFFSET + 20.0 / 16.0 * RACK_SCALE);
		float y = chestY + (0.25f - chestY) * drop + 0.6f * (float) Math.sin(Math.PI * hop);
		float scale = 0.55f * grow * (1f - hop);
		if (scale <= 0.01f) {
			return;
		}
		if (caseStack == null) {
			caseStack = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE);
		}
		pose.pushPose();
		pose.translate(0.5, y, 0.5);
		pose.scale(scale, scale, scale);
		com.projecthero.mod.client.ironman.MarkVSuitcaseRenderer.openness = 1f - smooth((age - (caseFrom + 4)) / 4f);
		try {
			Minecraft.getInstance().getItemRenderer().renderStatic(caseStack, net.minecraft.world.item.ItemDisplayContext.NONE,
					light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
		} finally {
			com.projecthero.mod.client.ironman.MarkVSuitcaseRenderer.openness = 0f;
		}
		pose.popPose();
	}

	private static ItemStack caseStack;

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	private static ArmorStand makeStand(Level level) {
		ArmorStand stand = new ArmorStand(level, 0, 0, 0);
		stand.setInvisible(true); // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}
}
