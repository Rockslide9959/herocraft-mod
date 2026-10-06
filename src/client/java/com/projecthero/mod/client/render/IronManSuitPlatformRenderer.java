package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.fabricator.PlatformDeployTimeline;
import com.projecthero.mod.ironman.suit.IronManSuitPoses;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * "Hall of Armor" display ("changes 16"): renders the racked suit as the actual worn armour <em>model</em> on an
 * invisible, slowly-rotating armour stand above the platform (vanilla {@code HumanoidArmorLayer}, so the GeckoLib suit
 * models show exactly as on a player). The block entity syncs its contents on every change, so this always reflects
 * what is really stored.
 *
 * <p>v0.15.1: the platform has two <b>robotic arms</b> on its gantry posts (code-built {@link ModelPart}s, texture
 * {@code textures/block/suit_platform_arm.png} painted by {@code scratchpad/gen_v0151_platform_arms.js}). At rest they
 * hang folded down the posts. During a deploy ({@link PlatformDeployTimeline}, read off the synced start tick so every
 * viewer sees the same frame) they unfold, and for each piece the carrying arm swings to the rack, closes its jaws on
 * the piece and carries it -- growing from rack size to full size and turning to the wearer's heading -- round onto the
 * body while the other arm braces it there with a spray of sparks; the jaws let go and the arms pull back for the next
 * piece, then fold away. Each arm is a two-bone IK chain (shoulder hub, upper arm with hydraulic ram and status lamp,
 * elbow, forearm, wrist, two-jaw clamp) solved per frame with a pole vector, so the motion is smooth at any frame rate.
 *
 * <p>v0.14.21 (kept for retrieve): while a retrieve runs the rack turns to face the player and each piece flies home
 * on a little arc from the body to the rack.
 */
public class IronManSuitPlatformRenderer implements BlockEntityRenderer<IronManSuitPlatformBlockEntity> {
	/** Per platform BE: the client-only armour stands (0..3 pieces in flight, 4 the rack) and the last stack seen per slot. */
	private static final Map<IronManSuitPlatformBlockEntity, State> STATES = new WeakHashMap<>();

	private static final class State {
		final ArmorStand[] stands;
		final ItemStack[] seen = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };

		State(ArmorStand[] stands) {
			this.stands = stands;
		}
	}

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final float RACK_SCALE = 0.62f;
	/** Height the racked suit stands at above the block (master: the raised floor plate of the new platform model). */
	public static final double DISPLAY_Y_OFFSET = 0.2;

	// ---------------- the arms (block-local frame: the model's own north-facing space, 0..1 per block) ----------------

	public static final ResourceLocation ARM_TEXTURE =
			ResourceLocation.fromNamespaceAndPath("projecthero", "textures/block/suit_platform_arm.png");
	/** Upper-arm and forearm lengths (blocks). */
	private static final double L1 = 15.0 / 16.0;
	private static final double L2 = 15.0 / 16.0;
	/** Wrist joint -> the middle of the jaws (blocks). */
	private static final double JAW = 6.0 / 16.0;
	/** Shoulder joints (right arm on the east post = the wearer's right; left on the west post) and their post brackets. */
	private static final Vec3 SHOULDER_R = new Vec3(0.80, 1.10, 0.70);
	private static final Vec3 SHOULDER_L = new Vec3(0.20, 1.10, 0.70);
	private static final Vec3 MOUNT_R = new Vec3(0.875, 1.10, 0.75);
	private static final Vec3 MOUNT_L = new Vec3(0.125, 1.10, 0.75);
	/** Elbow pole vectors: folded (elbow hangs down the post) and working (elbow forward over the rack, a little up). */
	private static final Vec3 POLE_REST = new Vec3(0, -1, -0.1);
	private static final Vec3 POLE_WORK = new Vec3(0, 0.5, -1);
	/** Ticks a fitted piece keeps being drawn in the clamp after it went on, while the worn piece's sync lands. */
	private static final float LINGER = 5f;

	private static ModelPart mount, hub, link, piston, lamp, palm, finger;

	private static void bakeArmParts() {
		if (mount != null) {
			return;
		}
		// texOffs / sizes must match scratchpad/gen_v0151_platform_arms.js
		mount = part(CubeListBuilder.create().texOffs(0, 0).addBox(-2, -4, -1, 4, 8, 2));
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

	/** One arm's pose this frame: wrist position, clamp direction, jaw opening 0..1, elbow pole. */
	private record ArmPose(Vec3 wrist, Vec3 dir, float open, Vec3 pole) {
		ArmPose lerp(ArmPose o, float f) {
			return new ArmPose(lerpV(wrist, o.wrist, f), nlerp(dir, o.dir, f), Mth.lerp(f, open, o.open), nlerp(pole, o.pole, f));
		}
	}

	public IronManSuitPlatformRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public boolean shouldRenderOffScreen(IronManSuitPlatformBlockEntity be) {
		return true; // the arms (and a piece in flight) reach well outside the block's own box
	}

	@Override
	public void render(IronManSuitPlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		Level level = be.getLevel();
		if (level == null) {
			return;
		}
		State st = STATES.computeIfAbsent(be, k -> new State(makeStands(be)));
		if (st.stands == null) {
			return;
		}
		ArmorStand[] stands = st.stands;
		for (int i = 0; i < 4; i++) {
			ItemStack now = be.getItem(i);
			if (!now.isEmpty() && !ItemStack.matches(now, st.seen[i])) {
				st.seen[i] = now.copy();
			}
		}
		long gameTime = level.getGameTime();
		float time = gameTime + partialTick;
		float spin = time * 1.4f;
		float bob = (float) Math.sin(time * 0.06f) * 0.03f;

		// ---- the running sequence (if any) ----
		int mode = be.seqMode();
		int[] seq = be.seqSlots();
		LivingEntity player = mode != IronManSuitPlatformBlockEntity.SEQ_NONE
				&& level.getEntity(be.seqPlayerEntity()) instanceof LivingEntity le ? le : null;
		boolean deploy = player != null && mode == IronManSuitPlatformBlockEntity.SEQ_DEPLOY;
		float t = time - be.seqStart();
		int n = seq.length;
		boolean[] inFlight = new boolean[4];
		float[] flight = new float[4];
		boolean[] gone = new boolean[4];
		if (player != null) {
			// the rack turns to face the player while it works
			Vec3 d = player.position().subtract(Vec3.atBottomCenterOf(be.getBlockPos()));
			// pose yaw -Y draws the stand as an entity with yaw Y; the yaw looking along d is atan2(-dx, dz)
			spin = (float) (Mth.atan2(d.x, d.z) * (180.0 / Math.PI));
			for (int i = 0; i < n; i++) {
				int idx = seq[i];
				if (idx < 0 || idx > 3) {
					continue;
				}
				if (deploy) {
					float lift = IronManSuitPlatformBlockEntity.deployLiftTick(i, n);
					float equip = IronManSuitPlatformBlockEntity.deployEquipTick(i, n);
					if (t >= equip) {
						gone[idx] = true; // on the player now (the block update may lag a tick behind)
						if (t < equip + LINGER) {
							inFlight[idx] = true;
							flight[idx] = 1f;
						}
					} else if (t >= lift) {
						inFlight[idx] = true;
						flight[idx] = (t - lift) / (equip - lift);
					}
				} else {
					float move = IronManSuitPlatformBlockEntity.retrieveMoveTick(i);
					float land = move + IronManSuitPlatformBlockEntity.SEQ_FLIGHT;
					if (t >= move && t < land) {
						inFlight[idx] = true;
						flight[idx] = 1f - (t - move) / (land - move); // 1 = at the player, 0 = on the rack
					}
				}
			}
		}

		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);

		// ---- the rack ----
		ArmorStand rack = stands[4];
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = inFlight[i] || gone[i] ? ItemStack.EMPTY : be.getItem(i);
			setIfChanged(rack, SLOTS[i], piece);
			any |= !piece.isEmpty();
		}
		if (any) {
			pose.pushPose();
			pose.translate(0.5, DISPLAY_Y_OFFSET + bob, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
			dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
			pose.popPose();
		}

		// ---- retrieve: pieces flying home (v0.14.21) ----
		if (player != null && !deploy) {
			Vec3 toPlayer = player.getPosition(partialTick).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
			float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
			for (int i = 0; i < 4; i++) {
				if (!inFlight[i] || be.getItem(i).isEmpty()) {
					continue;
				}
				ArmorStand s = stands[i];
				loadPiece(s, i, be.getItem(i));
				standPose(s, null, 0f);
				float f = smooth(flight[i]);
				double x = Mth.lerp(f, 0.5, toPlayer.x);
				double y = Mth.lerp(f, DISPLAY_Y_OFFSET + bob, toPlayer.y) + Math.sin(Math.PI * f) * 0.6;
				double z = Mth.lerp(f, 0.5, toPlayer.z);
				float yaw = Mth.rotLerp(f, spin, -bodyYaw);
				float scale = Mth.lerp(f, RACK_SCALE, 1.0f);
				pose.pushPose();
				pose.translate(x, y, z);
				pose.mulPose(Axis.YP.rotationDegrees(yaw));
				pose.scale(scale, scale, scale);
				dispatcher.render(s, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
				pose.popPose();
				if (level.random.nextFloat() < 0.35f) {
					Vec3 w = Vec3.atLowerCornerOf(be.getBlockPos()).add(x, y + 0.9 * scale, z);
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 0, 0, 0);
				}
			}
		}

		// ---- v0.15.1: the robotic arms (and, on deploy, the piece riding in a clamp) in the block's own frame ----
		float theta = 180f - be.deployFacing().toYRot(); // the blockstate's y-rotation, as a PoseStack yaw
		Vec3 corner = Vec3.atLowerCornerOf(be.getBlockPos());
		pose.pushPose();
		pose.translate(0.5, 0.0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(theta));
		pose.translate(-0.5, 0.0, -0.5);

		ArmPose right = rest(true);
		ArmPose left = rest(false);
		int carryIdx = -1;
		Vec3 carryAnchor = null;
		float carryScale = 1f;
		float carryYaw = 0f;
		float carryPoseF = 0f;
		Vec3 sparkAt = null;
		if (deploy && n > 0) {
			Vec3 body = toLocal(player.getPosition(partialTick).subtract(corner), theta);
			float bodyYaw = -Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) - theta;
			float rackYaw = spin - theta;
			ArmPose readyR = ready(true);
			ArmPose readyL = ready(false);
			if (t < PlatformDeployTimeline.LEAD) {
				float f = smooth(t / PlatformDeployTimeline.LEAD);
				right = right.lerp(readyR, f);
				left = left.lerp(readyL, f);
			} else if (t >= PlatformDeployTimeline.foldTick()) {
				float f = smooth((t - PlatformDeployTimeline.foldTick()) / PlatformDeployTimeline.OUTRO);
				right = readyR.lerp(right, f);
				left = readyL.lerp(left, f);
			} else {
				right = readyR;
				left = readyL;
				int i = PlatformDeployTimeline.pieceAt(t, n);
				int idx = seq[i];
				if (idx >= 0 && idx <= 3) {
					boolean rightCarries = PlatformDeployTimeline.rightArmCarries(i);
					Vec3 sCarry = rightCarries ? SHOULDER_R : SHOULDER_L;
					Vec3 sAssist = rightCarries ? SHOULDER_L : SHOULDER_R;
					ArmPose readyC = rightCarries ? readyR : readyL;
					ArmPose readyA = rightCarries ? readyL : readyR;
					double h = IronManSuitUpManager.slotHeight(SLOTS[idx]);
					Vec3 rackA = new Vec3(0.5, DISPLAY_Y_OFFSET + bob + h * RACK_SCALE, 0.5);
					Vec3 bodyA = body.add(0, h, 0);
					float u = PlatformDeployTimeline.pieceFrac(t, i, n);
					ArmPose carry;
					ArmPose assist = readyA;
					if (u < PlatformDeployTimeline.GRIP) {
						// swing to the rack, jaws open wide, then close on the piece
						float r = smooth(u / PlatformDeployTimeline.GRIP_CLOSE);
						ArmPose atRack = grip(sCarry, rackA, RACK_SCALE, 0f);
						float jaws = u < PlatformDeployTimeline.GRIP_CLOSE ? Mth.lerp(r, 0.5f, 1f)
								: 1f - smooth((u - PlatformDeployTimeline.GRIP_CLOSE) / (PlatformDeployTimeline.GRIP - PlatformDeployTimeline.GRIP_CLOSE));
						carry = readyC.lerp(atRack, r);
						carry = new ArmPose(carry.wrist(), carry.dir(), jaws, carry.pole());
					} else if (u < PlatformDeployTimeline.FIT) {
						// carry it off the rack and round onto the body on a lifted arc
						float f = smooth((u - PlatformDeployTimeline.GRIP) / (PlatformDeployTimeline.FIT - PlatformDeployTimeline.GRIP));
						double arc = Math.sin(Math.PI * f);
						Vec3 side = horizontal(sCarry.subtract(lerpV(rackA, bodyA, f)));
						Vec3 a = lerpV(rackA, bodyA, f).add(0, 0.32 * arc, 0).add(side.scale(0.18 * arc));
						carryScale = Mth.lerp(f, RACK_SCALE, 1f);
						carry = grip(sCarry, a, carryScale, 0f);
						carryIdx = idx;
						carryAnchor = a;
						carryYaw = Mth.rotLerp(f, rackYaw, bodyYaw);
						carryPoseF = f;
						// the bracing arm comes in to meet it
						float g = smooth((f - 0.35f) / 0.65f);
						assist = readyA.lerp(brace(sAssist, bodyA), g);
					} else {
						// on: clamps lock (both arms hold, sparks), then the jaws let go and the arms pull back
						ArmPose atBody = grip(sCarry, bodyA, 1f, 0f);
						ArmPose braced = brace(sAssist, bodyA);
						if (u < PlatformDeployTimeline.LET_GO) {
							float k = (u - PlatformDeployTimeline.FIT) / (PlatformDeployTimeline.LET_GO - PlatformDeployTimeline.FIT);
							double push = 0.03 * Math.sin(Math.PI * k);
							carry = new ArmPose(atBody.wrist().add(atBody.dir().scale(push)), atBody.dir(), 0f, atBody.pole());
							double jit = 0.012 * Math.sin(time * 2.7);
							assist = new ArmPose(braced.wrist().add(jit, -jit, jit), braced.dir(), 0.25f, braced.pole());
							sparkAt = braced.wrist().add(braced.dir().scale(JAW + 0.05));
						} else {
							float g = smooth((u - PlatformDeployTimeline.LET_GO) / (1f - PlatformDeployTimeline.LET_GO));
							carry = new ArmPose(atBody.wrist(), atBody.dir(), Math.min(1f, g * 2.5f), atBody.pole()).lerp(readyC, g);
							assist = braced.lerp(readyA, g);
						}
						if (t < IronManSuitPlatformBlockEntity.deployEquipTick(i, n) + LINGER) {
							carryIdx = idx;
							carryAnchor = bodyA;
							carryScale = 1f;
							carryYaw = bodyYaw;
							carryPoseF = 1f;
						}
					}
					if (rightCarries) {
						right = carry;
						left = assist;
					} else {
						left = carry;
						right = assist;
					}
				}
			}
		}

		bakeArmParts();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ARM_TEXTURE));
		Vec3 gripR = drawArm(pose, vc, packedLight, MOUNT_R, SHOULDER_R, right);
		Vec3 gripL = drawArm(pose, vc, packedLight, MOUNT_L, SHOULDER_L, left);

		// the piece riding in the clamp: it is drawn where the jaws actually are (if the wearer stands out of reach the
		// jaws stop short and the piece covers the last stretch on its own), at its growing size and turning heading
		if (deploy && carryIdx >= 0 && carryAnchor != null && !st.seen[carryIdx].isEmpty()) {
			int i = indexOf(seq, carryIdx);
			boolean rightCarries = PlatformDeployTimeline.rightArmCarries(Math.max(0, i));
			Vec3 jaws = rightCarries ? gripR : gripL;
			Vec3 a = carryPoseF >= 1f ? carryAnchor : lerpV(jaws, carryAnchor, smooth((carryPoseF - 0.55f) / 0.45f));
			double h = IronManSuitUpManager.slotHeight(SLOTS[carryIdx]);
			ArmorStand s = stands[carryIdx];
			loadPiece(s, carryIdx, st.seen[carryIdx]);
			float[] key = PlatformDeployTimeline.pose(IronManSuitPlatformBlockEntity.deployEquipTick(Math.max(0, i), n), n);
			standPose(s, key, carryPoseF);
			pose.pushPose();
			pose.translate(a.x, a.y - h * carryScale, a.z);
			pose.mulPose(Axis.YP.rotationDegrees(carryYaw));
			pose.scale(carryScale, carryScale, carryScale);
			dispatcher.render(s, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
			pose.popPose();
			if (carryPoseF < 1f && level.random.nextFloat() < 0.18f) {
				Vec3 w = corner.add(toWorldRel(jaws, theta));
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 0, 0, 0);
			}
		}
		if (sparkAt != null && level.random.nextFloat() < 0.6f) {
			Vec3 w = corner.add(toWorldRel(sparkAt, theta));
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, (level.random.nextFloat() - 0.5f) * 0.12f,
					level.random.nextFloat() * 0.08f, (level.random.nextFloat() - 0.5f) * 0.12f);
		}
		pose.popPose();
		dispatcher.setRenderShadow(true);
	}

	// ---------------- arm poses ----------------

	private static Vec3 inward(boolean rightArm) {
		return new Vec3(rightArm ? -1 : 1, 0, 0);
	}

	/** Folded: hanging down the post, jaws pointing at the floor. */
	private static ArmPose rest(boolean rightArm) {
		Vec3 s = rightArm ? SHOULDER_R : SHOULDER_L;
		return new ArmPose(s.add(0, 0.02, -0.16), new Vec3(0, -1, 0), 0.2f, POLE_REST);
	}

	/** Ready: hovering in front of the shoulder, jaws half open, pointing forward and down. */
	private static ArmPose ready(boolean rightArm) {
		Vec3 s = rightArm ? SHOULDER_R : SHOULDER_L;
		Vec3 in = inward(rightArm);
		return new ArmPose(s.add(in.scale(0.12)).add(0, -0.22, -0.42), in.scale(0.3).add(0, -0.5, -1).normalize(), 0.5f, POLE_WORK);
	}

	/** Jaws round a piece whose anchor (centre at its slot height) is {@code a}: pointing at it from the shoulder. */
	private static ArmPose grip(Vec3 shoulder, Vec3 a, float scale, float open) {
		Vec3 dir = a.subtract(shoulder);
		dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : dir.normalize();
		return new ArmPose(a.subtract(dir.scale(JAW + 0.10 * scale)), dir, open, POLE_WORK);
	}

	/** The bracing arm: jaws pressed to the wearer's side at the piece's height. */
	private static ArmPose brace(Vec3 shoulder, Vec3 bodyA) {
		Vec3 side = horizontal(shoulder.subtract(bodyA));
		Vec3 target = bodyA.add(side.scale(0.30));
		Vec3 dir = target.subtract(shoulder);
		dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : dir.normalize();
		return new ArmPose(target.subtract(dir.scale(JAW)), dir, 0.35f, POLE_WORK);
	}

	// ---------------- drawing ----------------

	/** Two-bone IK + draw. Returns the point between the jaws (block-local), where a carried piece sits. */
	private static Vec3 drawArm(PoseStack pose, VertexConsumer vc, int light, Vec3 mountAt, Vec3 shoulder, ArmPose arm) {
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

		// bracket on the post
		pose.pushPose();
		pose.translate(mountAt.x, mountAt.y, mountAt.z);
		mount.render(pose, vc, light, overlay);
		pose.popPose();
		drawLink(pose, vc, light, mountAt, shoulder, 0.8f);
		// shoulder hub, upper arm (with its hydraulic ram and status lamp), elbow, forearm, wrist
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
		// the clamp: palm + two jaws that open sideways
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

	private static void drawLink(PoseStack pose, VertexConsumer vc, int light, Vec3 from, Vec3 to, float thick) {
		pose.pushPose();
		align(pose, from, to);
		pose.scale(thick, thick, (float) (from.distanceTo(to) / L1));
		link.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
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

	// ---------------- the stands ----------------

	private static void loadPiece(ArmorStand s, int idx, ItemStack stack) {
		for (int j = 0; j < 4; j++) {
			setIfChanged(s, SLOTS[j], j == idx ? stack : ItemStack.EMPTY);
		}
	}

	private static final Rotations DEF_HEAD = new Rotations(0f, 0f, 0f);
	private static final Rotations DEF_LEFT_ARM = new Rotations(-10f, 0f, -10f);
	private static final Rotations DEF_RIGHT_ARM = new Rotations(-15f, 0f, 10f);
	private static final Rotations DEF_LEFT_LEG = new Rotations(-1f, 0f, -1f);
	private static final Rotations DEF_RIGHT_LEG = new Rotations(1f, 0f, 1f);

	/**
	 * Pose a flying piece's stand: the rack's default stance at {@code f} = 0, the wearer's deploy pose (arms out, see
	 * {@link PlatformDeployTimeline#pose}) at 1 -- so a chestplate's arms are already out when it meets the body.
	 */
	private static void standPose(ArmorStand s, float[] key, float f) {
		if (key == null) {
			s.setHeadPose(DEF_HEAD);
			s.setLeftArmPose(DEF_LEFT_ARM);
			s.setRightArmPose(DEF_RIGHT_ARM);
			s.setLeftLegPose(DEF_LEFT_LEG);
			s.setRightLegPose(DEF_RIGHT_LEG);
			return;
		}
		float g = smooth(f);
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

	private static void setIfChanged(ArmorStand stand, EquipmentSlot slot, ItemStack want) {
		if (!ItemStack.matches(stand.getItemBySlot(slot), want)) {
			stand.setItemSlot(slot, want);
		}
	}

	// ---------------- maths ----------------

	/** Block-relative world offset -> the block's own north-facing frame (undo the facing yaw about the block centre). */
	private static Vec3 toLocal(Vec3 rel, float theta) {
		return rotY(rel.subtract(0.5, 0, 0.5), -theta).add(0.5, 0, 0.5);
	}

	private static Vec3 toWorldRel(Vec3 local, float theta) {
		return rotY(local.subtract(0.5, 0, 0.5), theta).add(0.5, 0, 0.5);
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

	private static int indexOf(int[] seq, int idx) {
		for (int i = 0; i < seq.length; i++) {
			if (seq[i] == idx) {
				return i;
			}
		}
		return -1;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	private static ArmorStand[] makeStands(IronManSuitPlatformBlockEntity be) {
		if (be.getLevel() == null) {
			return null;
		}
		ArmorStand[] out = new ArmorStand[5];
		for (int i = 0; i < out.length; i++) {
			ArmorStand stand = new ArmorStand(be.getLevel(), 0, 0, 0);
			stand.setInvisible(true);      // only the armour shows
			stand.setNoBasePlate(true);
			stand.setShowArms(true);
			stand.setNoGravity(true);
			stand.setYRot(0f);
			stand.yBodyRot = 0f;
			stand.yHeadRot = 0f;
			out[i] = stand;
		}
		return out;
	}
}
