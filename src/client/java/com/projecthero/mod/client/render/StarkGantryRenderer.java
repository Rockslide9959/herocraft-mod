package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitPoses;
import com.projecthero.mod.client.ironman.IronManGantryBuild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.4: draws a running Stark Gantry from its centre tile ({@link StarkGantryFloorBlockEntity}), every frame a pure
 * function of the synced start tick ({@link GantryTimeline}), so every viewer sees the same thing:
 * <ul>
 *   <li>the <b>hatch panels</b> of the three open tiles (wearer's left, right, front) sliding apart under the
 *       neighbouring tiles, and the <b>lift pad</b> on its ram raising the wearer half a block -- textured from the floor's
 *       own top texture so a shut hatch looks exactly like the floor;</li>
 *   <li>two <b>arm masts</b> telescoping up out of the side hatches, each with a robotic arm (the two-bone IK arm of the
 *       old Suit Platform: shoulder hub, upper arm with hydraulic ram and status lamp, forearm, two-jaw clamp) that
 *       unfolds from a tucked fold;</li>
 *   <li>the <b>front elevator</b> bringing each piece up out of the floor, the arm taking it, carrying it onto the body
 *       (the other arm braces it, sparks as the clamps lock) and the elevator sinking back -- or all of it backwards
 *       while a suit comes off, each piece lowered into the floor.</li>
 * </ul>
 * The arms and elevator are drawn in a frame that faces the way the wearer does (local -Z = forward, +X = their right).
 */
public class StarkGantryRenderer implements BlockEntityRenderer<StarkGantryFloorBlockEntity> {
	private static final Map<StarkGantryFloorBlockEntity, ItemStack[]> SEEN = new WeakHashMap<>();
	private static ArmorStand stand;

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ResourceLocation TOP = ResourceLocation.fromNamespaceAndPath("projecthero", "block/stark_gantry_floor_top");
	private static final ResourceLocation SIDE = ResourceLocation.fromNamespaceAndPath("projecthero", "block/stark_gantry_floor_side");
	private static final ResourceLocation PIT = ResourceLocation.fromNamespaceAndPath("projecthero", "block/stark_gantry_floor_pit");

	/** Upper-arm and forearm lengths (blocks); wrist -> the middle of the jaws. */
	private static final double L1 = 15.0 / 16.0;
	private static final double L2 = 15.0 / 16.0;
	private static final double JAW = 6.0 / 16.0;
	/** Mast foot (the pit floor) and shoulder height sunk / up, local frame (y 0 = the floor's top). */
	private static final double MAST_FOOT = -15.0 / 16.0;
	private static final double SHOULDER_DOWN = -1.05;
	private static final double SHOULDER_UP = 1.2;
	/** Elevator pad top sunk / up. */
	private static final double ELEVATOR_DOWN = -0.86;
	private static final double ELEVATOR_UP = 0.0;
	/** Ticks a fitted piece keeps being drawn in the clamp after it went on, while the worn piece's sync lands. */
	private static final float LINGER = 3f;

	private static ModelPart hub, link, piston, lamp, palm, finger;

	public static final ResourceLocation ARM_TEXTURE =
			ResourceLocation.fromNamespaceAndPath("projecthero", "textures/block/suit_platform_arm.png");

	private static void bakeArmParts() {
		if (hub != null) {
			return;
		}
		// texOffs / sizes must match scratchpad/gen_v0151_platform_arms.js (the arm texture is shared with v0.15.1)
		hub = part(CubeListBuilder.create().texOffs(16, 0).addBox(-2, -2, -2, 4, 4, 4));
		link = part(CubeListBuilder.create().texOffs(0, 10).addBox(-1.5f, -1.5f, 0, 3, 3, 15));
		piston = part(CubeListBuilder.create().texOffs(36, 10).addBox(-0.5f, -0.5f, 0, 1, 1, 10));
		lamp = part(CubeListBuilder.create().texOffs(36, 22).addBox(-0.5f, -0.5f, 0, 1, 1, 6));
		palm = part(CubeListBuilder.create().texOffs(32, 0).addBox(-2.5f, -2.5f, 0, 5, 5, 2));
		finger = part(CubeListBuilder.create().texOffs(46, 0).addBox(-0.5f, -1, 0, 1, 2, 5));
	}

	private static ModelPart part(CubeListBuilder cubes) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("p", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("p");
	}

	/** One arm's pose: wrist position, clamp direction, jaw opening 0..1, elbow pole. */
	private record ArmPose(Vec3 wrist, Vec3 dir, float open, Vec3 pole) {
		ArmPose lerp(ArmPose o, float f) {
			return new ArmPose(lerpV(wrist, o.wrist, f), nlerp(dir, o.dir, f), Mth.lerp(f, open, o.open), nlerp(pole, o.pole, f));
		}
	}

	public StarkGantryRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public boolean shouldRenderOffScreen(StarkGantryFloorBlockEntity be) {
		return true; // the arms reach well outside the block's own box
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	@Override
	public void render(StarkGantryFloorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		Level level = be.getLevel();
		if (level == null || !be.running()) {
			SEEN.remove(be);
			return;
		}
		ItemStack[] seen = SEEN.computeIfAbsent(be, k -> new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY });
		for (int i = 0; i < 4; i++) {
			if (!be.buffered(i).isEmpty()) {
				seen[i] = be.buffered(i).copy();
			}
		}
		boolean equip = be.mode() == StarkGantryFloorBlockEntity.MODE_EQUIP;
		GantryTimeline.Plan plan = be.plan();
		float time = level.getGameTime() + partialTick;
		float f = be.frameAt(partialTick);
		BlockPos pos = be.getBlockPos();
		int light = LevelRenderer.getLightColor(level, pos.above());
		Direction facing = be.facing();

		// ---- hatch panels + the lift pad (world-aligned block frame) ----
		VertexConsumer blocks = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
		TextureAtlasSprite top = atlas.apply(TOP);
		TextureAtlasSprite side = atlas.apply(SIDE);
		TextureAtlasSprite pit = atlas.apply(PIT);
		float hatch = GantryTimeline.hatch(f, plan);
		for (BlockPos h : be.hatches()) {
			int dx = h.getX() - pos.getX();
			int dz = h.getZ() - pos.getZ();
			pose.pushPose();
			pose.translate(dx, 0, dz);
			drawPanels(pose, blocks, top, side, hatch, dx != 0, LevelRenderer.getLightColor(level, h.above()));
			pose.popPose();
		}
		drawLift(pose, blocks, top, side, pit, GantryTimeline.lift(f, plan), light);

		// ---- the arms, the elevator and the part in flight (the wearer's frame) ----
		LivingEntity player = level.getEntity(be.playerEntity()) instanceof LivingEntity le ? le : null;
		float theta = 180f - facing.toYRot();
		Vec3 origin = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		pose.pushPose();
		pose.translate(0.5, 1.0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(theta));

		double sh = Mth.lerp(GantryTimeline.rise(f, plan), SHOULDER_DOWN, SHOULDER_UP);
		Vec3 sR = new Vec3(1, sh, 0);
		Vec3 sL = new Vec3(-1, sh, 0);
		float unfold = GantryTimeline.unfold(f, plan);
		ArmPose readyR = folded(true, sR).lerp(ready(true, sR), unfold);
		ArmPose readyL = folded(false, sL).lerp(ready(false, sL), unfold);
		ArmPose right = readyR;
		ArmPose left = readyL;

		float lift = GantryTimeline.lift(f, plan);
		Vec3 body = player != null ? toLocal(player.getPosition(partialTick).subtract(origin), theta) : new Vec3(0, lift, 0);
		// v0.15.8, user request: the parts go on to a fixed beat, so both arms can be busy at once (a gauntlet / boot pair
		// rides up the elevator side by side) -- each arm plays its own carry, and the pad is as high as any part needs
		boolean inPlan = plan.at(f) >= 0;
		int iR = inPlan ? plan.carrying(f, true) : -1;
		int iL = inPlan ? plan.carrying(f, false) : -1;
		double elevatorTop = ELEVATOR_DOWN;
		for (int i : new int[] { iR, iL }) {
			if (i >= 0) {
				elevatorTop = Math.max(elevatorTop, Mth.lerp(GantryTimeline.elevator(plan.frac(f, i)), ELEVATOR_DOWN, ELEVATOR_UP));
			}
		}
		java.util.List<Carry> carries = new java.util.ArrayList<>();
		Vec3 sparkAt = null;
		for (int armNo = 0; armNo < 2; armNo++) {
			boolean rightArm = armNo == 0;
			int i = rightArm ? iR : iL;
			if (i < 0) {
				// the second of a pair rides up beside the first and waits on the pad until its own arm reaches for it
				int other = rightArm ? iL : iR;
				int p = other < 0 ? -1 : plan.partner(other);
				if (p >= 0 && f < plan.begin(p) && plan.ridesWithPartner(p)) { // v0.15.9: 1 s apart, they ride alone
					carries.add(waiting(plan, p, elevatorTop));
				}
				continue;
			}
			// v0.15.8: an arm that set off straight from its last part starts its reach from where that part left it
			ArmPose start = rightArm ? readyR : readyL;
			int from = plan.cutFrom(i);
			if (from >= 0) {
				start = carry(plan, from, plan.frac(plan.begin(i), from), elevatorTop, body, rightArm ? sR : sL, rightArm ? sL : sR,
						start, start, rightArm ? readyL : readyR, time).carry();
			}
			Carry c = carry(plan, i, plan.frac(f, i), elevatorTop, body, rightArm ? sR : sL, rightArm ? sL : sR,
					start, rightArm ? readyR : readyL, rightArm ? readyL : readyR, time);
			carries.add(c);
			if (rightArm) {
				right = c.carry();
			} else {
				left = c.carry();
			}
			if (c.sparkAt() != null) {
				sparkAt = c.sparkAt();
			}
		}
		// a part one arm can't fit alone (chest, legs, helmet, faceplate) has the other arm bracing it, when that arm is free
		for (Carry c : carries) {
			// ...but not when that arm is due at its own next part before this one is done (it would have to jump)
			if (c.assist() != null && plan.carrying(plan.end(c.index()) - 1, !plan.rightArm(c.index())) < 0) {
				if (plan.rightArm(c.index())) {
					if (iL < 0) {
						left = c.assist();
					}
				} else if (iR < 0) {
					right = c.assist();
				}
			}
		}

		bakeArmParts();
		VertexConsumer arms = buffers.getBuffer(RenderType.entityCutoutNoCull(ARM_TEXTURE));
		drawMast(pose, arms, light, new Vec3(1, MAST_FOOT, 0), sR);
		drawMast(pose, arms, light, new Vec3(-1, MAST_FOOT, 0), sL);
		Vec3 gripR = drawArm(pose, arms, light, sR, right);
		Vec3 gripL = drawArm(pose, arms, light, sL, left);
		// re-fetched: asking the buffer source for the arms' render type ended the block-atlas batch ("Not building!")
		drawElevator(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), top, side, elevatorTop, LevelRenderer.getLightColor(level, pos.relative(facing).above()));

		// ---- the parts: on the elevator, in a clamp, or just fitted ----
		for (Carry c : carries) {
			int carryStage = c.stage();
			int idx = GantryTimeline.pieceOf(carryStage);
			float fitAt = plan.fitTick(c.index());
			boolean first = carryStage == GantryTimeline.firstStage(idx);
			// putting on: until it is fitted (a first part lingers a few ticks while the worn sync lands); taking off: once
			// it is off the body
			boolean show = equip ? f < fitAt + (first ? LINGER : 0f) : f < fitAt;
			if (!show) {
				continue;
			}
			ItemStack piece;
			ItemStack worn = player != null ? player.getItemBySlot(SLOTS[idx]) : ItemStack.EMPTY;
			if (!be.buffered(idx).isEmpty()) {
				piece = be.buffered(idx);
			} else if (worn.getItem() instanceof IronManArmorItem) {
				piece = worn; // a later part of a piece already on the body (or one the sync has not moved yet)
			} else {
				piece = seen[idx];
			}
			if (piece.isEmpty()) {
				continue;
			}
			Vec3 jaws = plan.rightArm(c.index()) ? gripR : gripL;
			Vec3 a = c.onElevator() || c.poseF() >= 1f ? c.carryAt()
					: lerpV(jaws, c.carryAt(), GantryTimeline.smooth((c.poseF() - 0.5f) / 0.5f));
			Vec3 part = c.part();
			ArmorStand s = stand(level);
			for (int j = 0; j < 4; j++) {
				setIfChanged(s, SLOTS[j], j == idx ? piece : ItemStack.EMPTY);
			}
			standPose(s, GantryTimeline.pose(fitAt, plan), c.poseF());
			var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			dispatcher.setRenderShadow(false);
			pose.pushPose();
			pose.translate(a.x - part.x, a.y - part.y, a.z - part.z);
			pose.mulPose(Axis.YP.rotationDegrees(180f)); // a stand faces +Z; the wearer faces local -Z
			IronManGantryBuild.solo = carryStage;
			try {
				dispatcher.render(s, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, c.onElevator() ? LevelRenderer.getLightColor(level,
						pos.relative(facing).above()) : light);
			} finally {
				IronManGantryBuild.solo = -1;
			}
			pose.popPose();
			dispatcher.setRenderShadow(true);
			if (!c.onElevator() && c.poseF() < 1f && level.random.nextFloat() < 0.15f) {
				Vec3 w = origin.add(toWorldRel(jaws, theta));
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 0, 0, 0);
			}
		}
		if (sparkAt != null && level.random.nextFloat() < 0.2f) {
			Vec3 w = origin.add(toWorldRel(sparkAt, theta));
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, (level.random.nextFloat() - 0.5f) * 0.12f,
					level.random.nextFloat() * 0.08f, (level.random.nextFloat() - 0.5f) * 0.12f);
		}
		pose.popPose();
	}

	// ---------------- v0.15.8: one arm's carry ----------------

	/** One carried (or waiting) part this frame: its arm's pose, the other arm's brace (null = none), where the part is. */
	private record Carry(int stage, int index, ArmPose carry, ArmPose assist, Vec3 carryAt, Vec3 part, float poseF,
			boolean onElevator, Vec3 sparkAt) {
	}

	/** Grip point of carried entry {@code i}'s part, and how high above the elevator pad top it sits. */
	private static Vec3 partOf(int stage) {
		double[] pp = GantryTimeline.partPoint(stage, 0.5f, 0);
		return new Vec3(pp[0], pp[1], pp[2]);
	}

	private static double riseOf(int stage, Vec3 part) {
		return 0.05 + part.y - GantryTimeline.partBottom(stage); // elevator top -> the part's grip point
	}

	/** The second part of a pair, riding up on the pad beside the first before its own arm reaches for it. */
	private static Carry waiting(GantryTimeline.Plan plan, int i, double elevatorTop) {
		int stage = plan.stage(i);
		Vec3 part = partOf(stage);
		Vec3 at = new Vec3(plan.padX(i), elevatorTop + riseOf(stage, part), -1);
		return new Carry(stage, i, null, null, at, part, 0f, true, null);
	}

	/**
	 * Carried entry {@code i} at fraction {@code u}: the elevator brings the part up, the arm reaches over it, the jaws
	 * close, it is carried round onto the body on a raised arc, the clamps lock (sparks), the jaws open and the arm pulls
	 * back. A gauntlet or boot is one arm's work alone; anything else has the other arm brace the body.
	 */
	private static Carry carry(GantryTimeline.Plan plan, int i, float u, double elevatorTop, Vec3 body, Vec3 sC, Vec3 sA,
			ArmPose start, ArmPose readyC, ArmPose readyA, float time) {
		int stage = plan.stage(i);
		// one arm alone works a gauntlet -- v0.15.7, user request: and a boot (the other stays at its ready hover)
		boolean limb = stage == GantryTimeline.R_GAUNTLET || stage == GantryTimeline.L_GAUNTLET
				|| stage == GantryTimeline.R_BOOT || stage == GantryTimeline.L_BOOT;
		Vec3 part = partOf(stage);
		double rise = riseOf(stage, part);
		double padX = plan.padX(i);
		Vec3 eA = new Vec3(padX, elevatorTop + rise, -1);
		Vec3 eUp = new Vec3(padX, ELEVATOR_UP + rise, -1);
		Vec3 bodyA = body.add(part);
		ArmPose carry;
		ArmPose assist = readyA;
		Vec3 carryAt;
		float poseF = 0f;
		boolean onElevator = false;
		Vec3 sparkAt = null;
		if (u < GantryTimeline.REACH) {
			// the elevator brings the part up; the arm reaches over it, jaws wide
			carry = start.lerp(grip(sC, eUp.add(0, 0.25, 0), 1f), GantryTimeline.smooth(u / GantryTimeline.REACH));
			carryAt = eA;
			onElevator = true;
		} else if (u < GantryTimeline.GRIP) {
			float r = GantryTimeline.smooth((u - GantryTimeline.REACH) / (GantryTimeline.ELEVATOR_UP - GantryTimeline.REACH));
			float jaws = 1f - GantryTimeline.smooth((u - GantryTimeline.GRIP_CLOSE) / (GantryTimeline.GRIP - GantryTimeline.GRIP_CLOSE));
			ArmPose over = grip(sC, eUp.add(0, 0.25, 0), 1f);
			ArmPose at = grip(sC, eA, jaws);
			carry = over.lerp(at, r);
			carry = new ArmPose(carry.wrist(), carry.dir(), jaws, carry.pole());
			carryAt = eA;
			onElevator = true;
		} else if (u < GantryTimeline.FIT) {
			// lifted off the elevator and carried round onto the body on a raised arc
			float g = GantryTimeline.smooth((u - GantryTimeline.GRIP) / (GantryTimeline.FIT - GantryTimeline.GRIP));
			double arc = Math.sin(Math.PI * g);
			Vec3 sideOut = horizontal(sC.subtract(lerpV(eUp, bodyA, g)));
			Vec3 a = lerpV(eUp, bodyA, g).add(0, 0.30 * arc, 0).add(sideOut.scale(0.15 * arc));
			carry = grip(sC, a, 0f);
			carryAt = a;
			poseF = g;
			if (!limb) {
				assist = readyA.lerp(brace(sA, bodyA), GantryTimeline.smooth((g - 0.35f) / 0.65f));
			}
		} else {
			// on: the clamps lock (sparks), then the jaws open and the arms pull back
			ArmPose atBody = grip(sC, bodyA, 0f);
			ArmPose braced = limb ? readyA : brace(sA, bodyA);
			if (u < GantryTimeline.LET_GO) {
				float k = (u - GantryTimeline.FIT) / (GantryTimeline.LET_GO - GantryTimeline.FIT);
				double push = 0.03 * Math.sin(Math.PI * k);
				carry = new ArmPose(atBody.wrist().add(atBody.dir().scale(push)), atBody.dir(), 0f, atBody.pole());
				if (!limb) {
					double jit = 0.012 * Math.sin(time * 2.7);
					assist = new ArmPose(braced.wrist().add(jit, -jit, jit), braced.dir(), 0.25f, braced.pole());
					sparkAt = braced.wrist().add(braced.dir().scale(JAW + 0.05));
				} else {
					sparkAt = bodyA;
				}
			} else {
				float g = GantryTimeline.smooth((u - GantryTimeline.LET_GO) / (1f - GantryTimeline.LET_GO));
				carry = new ArmPose(atBody.wrist(), atBody.dir(), Math.min(1f, g * 2.5f), atBody.pole()).lerp(readyC, g);
				assist = braced.lerp(readyA, g);
			}
			carryAt = bodyA;
			poseF = 1f;
		}
		return new Carry(stage, i, carry, limb ? null : assist, carryAt, part, poseF, onElevator, sparkAt);
	}

	// ---------------- the floor parts (block-atlas quads) ----------------

	/**
	 * The two panels of an open hatch, sliding apart along the axis that leads away from the lift (so they disappear
	 * under the shut tiles either side, never into the open centre). Textured from the floor top at their shut place.
	 */
	private static void drawPanels(PoseStack pose, VertexConsumer vc, TextureAtlasSprite top, TextureAtlasSprite side,
			float open, boolean slideAlongZ, int light) {
		float y0 = 14.5f / 16f;
		float y1 = 15.5f / 16f;
		float s = open * 7f / 16f;
		for (int half = 0; half < 2; half++) {
			float a0 = half == 0 ? 1f / 16f : 8f / 16f;
			float a1 = half == 0 ? 8f / 16f : 15f / 16f;
			float shift = half == 0 ? -s : s;
			float b0 = 1f / 16f;
			float b1 = 15f / 16f;
			if (slideAlongZ) {
				box(pose, vc, b0, y0, a0 + shift, b1, y1, a1 + shift, top, b0, a0, b1, a1, side, light);
			} else {
				box(pose, vc, a0 + shift, y0, b0, a1 + shift, y1, b1, top, a0, b0, a1, b1, side, light);
			}
		}
	}

	/** The lift pad on its ram: flush with the floor at rest, {@code lift} blocks up at the top of its travel. */
	private static void drawLift(PoseStack pose, VertexConsumer vc, TextureAtlasSprite top, TextureAtlasSprite side,
			TextureAtlasSprite pit, float lift, int light) {
		float p1 = 15.5f / 16f + lift;
		float p0 = p1 - 2f / 16f;
		box(pose, vc, 1f / 16f, p0, 1f / 16f, 15f / 16f, p1, 15f / 16f, top, 1f / 16f, 1f / 16f, 15f / 16f, 15f / 16f, side, light);
		if (p0 > 1f / 16f + 0.01f) {
			// the ram, and two guide rods
			box(pose, vc, 6f / 16f, 1f / 16f, 6f / 16f, 10f / 16f, p0, 10f / 16f, side, 0.3f, 0f, 0.55f, 0.2f, side, light);
			box(pose, vc, 2.5f / 16f, 1f / 16f, 7.5f / 16f, 3.5f / 16f, p0, 8.5f / 16f, pit, 0.2f, 0.6f, 0.25f, 0.65f, pit, light);
			box(pose, vc, 12.5f / 16f, 1f / 16f, 7.5f / 16f, 13.5f / 16f, p0, 8.5f / 16f, pit, 0.2f, 0.6f, 0.25f, 0.65f, pit, light);
		}
	}

	/** The front elevator: a small pad on a ram (local frame, wearer's front tile). */
	private static void drawElevator(PoseStack pose, VertexConsumer vc, TextureAtlasSprite top, TextureAtlasSprite side,
			double padTop, int light) {
		float t1 = (float) padTop - 0.5f / 16f;
		float t0 = t1 - 1.5f / 16f;
		float r = 5.5f / 16f;
		box(pose, vc, -r, t0, -1 - r, r, t1, -1 + r, top, 0.16f, 0.16f, 0.84f, 0.84f, side, light);
		float foot = (float) MAST_FOOT;
		if (t0 > foot + 0.01f) {
			box(pose, vc, -1.5f / 16f, foot, -1 - 1.5f / 16f, 1.5f / 16f, t0, -1 + 1.5f / 16f, side, 0.3f, 0f, 0.5f, 0.2f, side, light);
		}
	}

	/** An axis-aligned box: the top face shows {@code topS} over (u0,v0)-(u1,v1); every other face a strip of {@code sideS}. */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			TextureAtlasSprite topS, float u0, float v0, float u1, float v1, TextureAtlasSprite sideS, int light) {
		PoseStack.Pose p = pose.last();
		float tu0 = topS.getU(u0), tu1 = topS.getU(u1), tv0 = topS.getV(v0), tv1 = topS.getV(v1);
		float su0 = sideS.getU(0f), su1 = sideS.getU(1f), sv0 = sideS.getV(0f), sv1 = sideS.getV(0.25f);
		// up
		quad(vc, p, light, 0, 1, 0, x0, y1, z0, tu0, tv0, x0, y1, z1, tu0, tv1, x1, y1, z1, tu1, tv1, x1, y1, z0, tu1, tv0);
		// down
		quad(vc, p, light, 0, -1, 0, x0, y0, z0, su0, sv0, x1, y0, z0, su1, sv0, x1, y0, z1, su1, sv1, x0, y0, z1, su0, sv1);
		// north / south
		quad(vc, p, light, 0, 0, -1, x0, y1, z0, su0, sv0, x1, y1, z0, su1, sv0, x1, y0, z0, su1, sv1, x0, y0, z0, su0, sv1);
		quad(vc, p, light, 0, 0, 1, x1, y1, z1, su0, sv0, x0, y1, z1, su1, sv0, x0, y0, z1, su1, sv1, x1, y0, z1, su0, sv1);
		// west / east
		quad(vc, p, light, -1, 0, 0, x0, y1, z1, su0, sv0, x0, y1, z0, su1, sv0, x0, y0, z0, su1, sv1, x0, y0, z1, su0, sv1);
		quad(vc, p, light, 1, 0, 0, x1, y1, z0, su0, sv0, x1, y1, z1, su1, sv0, x1, y0, z1, su1, sv1, x1, y0, z0, su0, sv1);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
			float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
		vertex(vc, p, light, nx, ny, nz, ax, ay, az, au, av);
		vertex(vc, p, light, nx, ny, nz, bx, by, bz, bu, bv);
		vertex(vc, p, light, nx, ny, nz, cx, cy, cz, cu, cv);
		vertex(vc, p, light, nx, ny, nz, dx, dy, dz, du, dv);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, int light, float nx, float ny, float nz,
			float x, float y, float z, float u, float v) {
		vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
	}

	// ---------------- arm poses (local frame) ----------------

	private static Vec3 inward(boolean rightArm) {
		return new Vec3(rightArm ? -1 : 1, 0, 0);
	}

	/** Tucked: upper arm straight up, forearm folded back down beside it, clamp turned in toward the wearer. */
	private static ArmPose folded(boolean rightArm, Vec3 s) {
		Vec3 in = inward(rightArm);
		return new ArmPose(s.add(in.scale(0.06)).add(0, 0.02, 0), in, 0.2f, new Vec3(0, 1, 0));
	}

	/** Ready: hovering in front of and inside the mast, jaws half open, pointing forward and down. */
	private static ArmPose ready(boolean rightArm, Vec3 s) {
		Vec3 in = inward(rightArm);
		return new ArmPose(s.add(in.scale(0.30)).add(0, -0.10, -0.40), in.scale(0.4).add(0, -0.35, -1).normalize(), 0.5f,
				in.scale(-0.5).add(0, 1, 0.2));
	}

	/** Jaws round a piece whose anchor is {@code a}: pointing at it from the shoulder. */
	private static ArmPose grip(Vec3 shoulder, Vec3 a, float open) {
		Vec3 dir = a.subtract(shoulder);
		dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : dir.normalize();
		Vec3 out = horizontal(shoulder.subtract(a));
		return new ArmPose(a.subtract(dir.scale(JAW + 0.08)), dir, open, out.scale(0.5).add(0, 1, 0));
	}

	/** The bracing arm: jaws pressed to the wearer's side at the piece's height. */
	private static ArmPose brace(Vec3 shoulder, Vec3 bodyA) {
		Vec3 sideOut = horizontal(shoulder.subtract(bodyA));
		Vec3 target = bodyA.add(sideOut.scale(0.28));
		Vec3 dir = target.subtract(shoulder);
		dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : dir.normalize();
		return new ArmPose(target.subtract(dir.scale(JAW)), dir, 0.35f, sideOut.scale(0.5).add(0, 1, 0));
	}

	// ---------------- drawing the arms ----------------

	/** The telescoping mast from the pit floor up to the shoulder (nothing while it is still sunk). */
	private static void drawMast(PoseStack pose, VertexConsumer vc, int light, Vec3 foot, Vec3 shoulder) {
		if (shoulder.y < foot.y + 0.05) {
			return;
		}
		int overlay = OverlayTexture.NO_OVERLAY;
		// outer sleeve (lower half) and the inner ram (whole height)
		Vec3 mid = lerpV(foot, shoulder, 0.5);
		pose.pushPose();
		align(pose, foot, mid);
		pose.scale(2.2f, 2.2f, (float) (foot.distanceTo(mid) / L1));
		link.render(pose, vc, light, overlay);
		pose.popPose();
		pose.pushPose();
		align(pose, foot, shoulder);
		pose.scale(1.4f, 1.4f, (float) (foot.distanceTo(shoulder) / L1));
		link.render(pose, vc, light, overlay);
		pose.popPose();
		pose.pushPose();
		align(pose, mid, shoulder);
		pose.translate(1.5 / 16.0, 0, 0);
		pose.scale(1f, 1f, (float) (mid.distanceTo(shoulder) * 16.0 / 6.0 * 0.8)); // the lamp box is 6 px long
		lamp.render(pose, vc, LightTexture.FULL_BRIGHT, overlay);
		pose.popPose();
		drawHub(pose, vc, light, mid, shoulder, 1.3f);
	}

	/** Two-bone IK + draw. Returns the point between the jaws (local frame), where a carried piece sits. */
	private static Vec3 drawArm(PoseStack pose, VertexConsumer vc, int light, Vec3 shoulder, ArmPose arm) {
		Vec3 d = arm.wrist().subtract(shoulder);
		double dist = d.length();
		Vec3 u = dist < 1e-4 ? new Vec3(0, -1, 0) : d.scale(1.0 / dist);
		double reach = Mth.clamp(dist, 0.03, L1 + L2 - 1e-3);
		Vec3 wrist = shoulder.add(u.scale(reach)); // stops short (arm straight) if the target is out of reach
		double a = (L1 * L1 - L2 * L2 + reach * reach) / (2 * reach);
		double h = Math.sqrt(Math.max(0, L1 * L1 - a * a));
		Vec3 pole = arm.pole();
		Vec3 v = pole.subtract(u.scale(pole.dot(u)));
		if (v.lengthSqr() < 1e-6) {
			v = new Vec3(0, 0, -1).subtract(u.scale(-u.z));
		}
		Vec3 elbow = shoulder.add(u.scale(a)).add(v.normalize().scale(h));
		int overlay = OverlayTexture.NO_OVERLAY;

		drawHub(pose, vc, light, shoulder, elbow, 1.1f);
		pose.pushPose();
		align(pose, shoulder, elbow);
		pose.scale(1f, 1f, (float) (shoulder.distanceTo(elbow) / L1));
		link.render(pose, vc, light, overlay);
		pose.pushPose();
		pose.translate(0, 2.1 / 16.0, 2.5 / 16.0);
		piston.render(pose, vc, light, overlay);
		pose.popPose();
		pose.pushPose();
		pose.translate(1.6 / 16.0, 0, 4.5 / 16.0);
		lamp.render(pose, vc, LightTexture.FULL_BRIGHT, overlay);
		pose.popPose();
		pose.popPose();
		drawHub(pose, vc, light, elbow, wrist, 1.0f);
		pose.pushPose();
		align(pose, elbow, wrist);
		pose.scale(0.85f, 0.85f, (float) (elbow.distanceTo(wrist) / L2));
		link.render(pose, vc, light, overlay);
		pose.popPose();
		Vec3 dir = arm.dir().lengthSqr() < 1e-6 ? u : arm.dir().normalize();
		drawHub(pose, vc, light, wrist, wrist.add(dir), 0.75f);
		pose.pushPose();
		align(pose, wrist, wrist.add(dir));
		pose.translate(0, 0, 1.5 / 16.0);
		palm.render(pose, vc, light, overlay);
		float spread = (0.9f + Mth.clamp(arm.open(), 0f, 1f) * 1.6f) / 16f;
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			pose.pushPose();
			pose.translate(sgn * spread, 0, 2.0 / 16.0);
			pose.mulPose(Axis.YP.rotation(sgn * 0.25f * arm.open()));
			finger.render(pose, vc, light, overlay);
			pose.popPose();
		}
		pose.popPose();
		return wrist.add(dir.scale(JAW));
	}

	private static void drawHub(PoseStack pose, VertexConsumer vc, int light, Vec3 at, Vec3 toward, float size) {
		pose.pushPose();
		align(pose, at, toward);
		pose.scale(size, size, size);
		hub.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** Move to {@code from} and turn local +Z to point at {@code to} (yaw about Y, then pitch). */
	private static void align(PoseStack pose, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		pose.translate(from.x, from.y, from.z);
		float yaw = (float) Math.atan2(d.x, d.z);
		float pitch = (float) -Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
		pose.mulPose(Axis.YP.rotation(yaw));
		pose.mulPose(Axis.XP.rotation(pitch));
	}

	// ---------------- the piece's armour stand ----------------

	private static ArmorStand stand(Level level) {
		if (stand == null || stand.level() != level) {
			stand = new ArmorStand(level, 0, 0, 0);
			stand.setInvisible(true);
			stand.setNoBasePlate(true);
			stand.setShowArms(true);
			stand.setNoGravity(true);
			stand.setYRot(0f);
			stand.yBodyRot = 0f;
			stand.yHeadRot = 0f;
		}
		return stand;
	}

	private static final Rotations DEF_HEAD = new Rotations(0f, 0f, 0f);
	private static final Rotations DEF_LEFT_ARM = new Rotations(-10f, 0f, -10f);
	private static final Rotations DEF_RIGHT_ARM = new Rotations(-15f, 0f, 10f);
	private static final Rotations DEF_LEFT_LEG = new Rotations(-1f, 0f, -1f);
	private static final Rotations DEF_RIGHT_LEG = new Rotations(1f, 0f, 1f);

	/** A stand's default stance at {@code f} = 0, the wearer's gantry pose at 1 -- so a chestplate fits the arms it meets. */
	private static void standPose(ArmorStand s, float[] key, float f) {
		float g = GantryTimeline.smooth(f);
		float k = Mth.RAD_TO_DEG;
		s.setHeadPose(lerpR(DEF_HEAD, key[1 + IronManSuitPoses.HX] * k, 0f, 0f, g));
		s.setRightArmPose(lerpR(DEF_RIGHT_ARM, key[1 + IronManSuitPoses.RAX] * k, key[1 + IronManSuitPoses.RAY] * k,
				key[1 + IronManSuitPoses.RAZ] * k, g));
		s.setLeftArmPose(lerpR(DEF_LEFT_ARM, key[1 + IronManSuitPoses.LAX] * k, key[1 + IronManSuitPoses.LAY] * k,
				key[1 + IronManSuitPoses.LAZ] * k, g));
		s.setRightLegPose(lerpR(DEF_RIGHT_LEG, key[1 + IronManSuitPoses.RLX] * k, 0f, key[1 + IronManSuitPoses.RLZ] * k, g));
		s.setLeftLegPose(lerpR(DEF_LEFT_LEG, key[1 + IronManSuitPoses.LLX] * k, 0f, key[1 + IronManSuitPoses.LLZ] * k, g));
	}

	private static Rotations lerpR(Rotations a, float x, float y, float z, float g) {
		return new Rotations(Mth.lerp(g, a.getX(), x), Mth.lerp(g, a.getY(), y), Mth.lerp(g, a.getZ(), z));
	}

	private static void setIfChanged(ArmorStand s, EquipmentSlot slot, ItemStack want) {
		if (!ItemStack.matches(s.getItemBySlot(slot), want)) {
			s.setItemSlot(slot, want);
		}
	}

	// ---------------- maths ----------------

	/** World offset (relative to the lift's top centre) -> the wearer's frame. */
	private static Vec3 toLocal(Vec3 rel, float theta) {
		return rotY(rel, -theta);
	}

	private static Vec3 toWorldRel(Vec3 local, float theta) {
		return rotY(local, theta);
	}

	/** The rotation {@code Axis.YP.rotationDegrees(deg)} applies. */
	private static Vec3 rotY(Vec3 v, float deg) {
		double r = Math.toRadians(deg);
		double c = Math.cos(r);
		double s = Math.sin(r);
		return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
	}

	private static Vec3 lerpV(Vec3 a, Vec3 b, double f) {
		return a.add(b.subtract(a).scale(f));
	}

	private static Vec3 nlerp(Vec3 a, Vec3 b, double f) {
		Vec3 v = lerpV(a, b, f);
		return v.lengthSqr() < 1e-6 ? b : v.normalize();
	}

}
