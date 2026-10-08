package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.15.11: the props of a hand-built suit-up ({@link IronManManualSuitUp}) -- render only, nothing is ever given to the
 * player. The <b>hammer</b> (and, in the workshop build, the <b>wrench</b>) in the right hand, and the piece about to be
 * fitted in the left hand while it is carried from the floor to the body (a display copy of that piece; the real one is
 * still in the pack until it lands in its slot). The ordinary held-item draw is skipped for the same window
 * ({@code ItemInHandLayerSuitcaseMixin}).
 */
public class IronManManualSuitUpLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation HAMMER = ProjectHeroMod.id("suitup/hammer");
	public static final ResourceLocation WRENCH = ProjectHeroMod.id("suitup/wrench");
	/** How far the tool's handle is tilted from straight out of the fist toward the forearm (degrees). */
	private static ItemStack dummy;

	public IronManManualSuitUpLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	public static void initialize() {
		ModelLoadingPlugin.register(plugin -> plugin.addModels(HAMMER, WRENCH));
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(IronManManualSuitUpLayer::renderDroppedPlates); // v0.15.15
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new IronManManualSuitUpLayer(playerRenderer));
			}
		});
	}

	/** Is a hand build posing {@code player} (their own held items are not drawn meanwhile)? */
	public static boolean active(Player player) {
		return IronManManualSuitUpPose.sample(player, 0f) != null;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.level() == null || player.isInvisible()) {
			return;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		int kind = IronManManualSuitUp.kindOfPose(fx.poseKind());
		int off = com.projecthero.mod.ironman.suit.IronManSuitRemoval.kindOfPose(fx.poseKind());
		if (off == com.projecthero.mod.ironman.suit.IronManSuitRemoval.KIND_MK1) {
			renderMk1Removal(pose, buffers, light, player, fx, partialTick); // v0.15.15
			return;
		}
		if (kind < 0) {
			return;
		}
		float age = fx.poseAge(player.level().getGameTime(), partialTick);
		if (age < 0f) {
			return;
		}
		IronManManualSuitUp.Schedule sch = IronManManualSuitUp.schedule(kind, IronManManualSuitUp.planOf(fx.poseVariant()));
		int props = sch.props(age);
		// the tools fade in / out with the pose itself (scaled), so they never pop at the very start or end
		float[] s = sch.pose(age);
		float grow = s == null ? 0f : Math.min(1f, s[0] * 1.5f);
		if (grow <= 0.01f) {
			return;
		}
		// the wrist: blended toward the resting angle with the pose weight, like the limbs
		float wrist = (float) Math.toDegrees(IronManManualSuitUp.REST_TILT
				+ (s[1 + IronManManualSuitUp.TILT] - IronManManualSuitUp.REST_TILT) * s[0]);
		if ((props & IronManManualSuitUp.PROP_HAMMER) != 0) {
			renderTool(pose, buffers, light, HAMMER, grow, wrist, 0.7f);
		} else if ((props & IronManManualSuitUp.PROP_WRENCH) != 0) {
			renderTool(pose, buffers, light, WRENCH, grow, wrist, 0.75f);
		}
		if ((props & IronManManualSuitUp.PROP_PIECE) != 0) {
			int bit = sch.pieceInHand(age);
			String suitId = IronManManualSuitUp.suitOf(fx.poseVariant());
			if (bit >= 0 && suitId != null) {
				EquipmentSlot slot = switch (bit) {
					case 0 -> EquipmentSlot.HEAD;
					case 1 -> EquipmentSlot.CHEST;
					case 2 -> EquipmentSlot.LEGS;
					default -> EquipmentSlot.FEET;
				};
				IronManArmorItem item = IronManItems.armor(suitId, IronManSuitUpManager.typeOf(slot));
				if (item != null) {
					// carried in the left hand while it is picked up; once the right arm comes up too (the chestplate /
					// helmet lifted over the head) it is held between both hands
					float rax = s[1 + IronManManualSuitUp.RAX];
					float both = Math.max(0f, Math.min(1f, (-rax - 1.0f) / 0.8f));
					renderPiece(pose, buffers, light, player, new ItemStack(item), grow, both);
				}
			}
		}
	}

	private static EquipmentSlot slotOf(int bit) {
		return switch (bit) {
			case 0 -> EquipmentSlot.HEAD;
			case 1 -> EquipmentSlot.CHEST;
			case 2 -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
	}

	/**
	 * v0.15.15: the Mark 1 pulled off with C ({@link com.projecthero.mod.ironman.suit.IronManSuitRemoval#KIND_MK1}) -- the
	 * hammer in the right hand throughout, the plate just pulled off held in the hands (a display copy: the real stack is
	 * already in the pack), then let go of: it tumbles to the floor beside the wearer and shrinks away.
	 */
	private void renderMk1Removal(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			IronManSuitFx fx, float partialTick) {
		float age = fx.poseAge(player.level().getGameTime(), partialTick);
		if (age < 0f) {
			return;
		}
		var sch = com.projecthero.mod.ironman.suit.IronManSuitRemoval.schedule(
				com.projecthero.mod.ironman.suit.IronManSuitRemoval.KIND_MK1, IronManManualSuitUp.planOf(fx.poseVariant()));
		float[] s = sch.pose(age);
		float grow = s == null ? 0f : Math.min(1f, s[0] * 1.5f);
		String suitId = IronManManualSuitUp.suitOf(fx.poseVariant());
		if (grow > 0.01f) {
			float wrist = (float) Math.toDegrees(IronManManualSuitUp.REST_TILT
					+ (s[1 + IronManManualSuitUp.TILT] - IronManManualSuitUp.REST_TILT) * s[0]);
			if ((sch.props(age) & IronManManualSuitUp.PROP_HAMMER) != 0) {
				renderTool(pose, buffers, light, HAMMER, grow, wrist, 0.7f);
			}
			int bit = sch.pieceInHand(age);
			if (bit >= 0 && suitId != null) {
				IronManArmorItem item = IronManItems.armor(suitId, IronManSuitUpManager.typeOf(slotOf(bit)));
				if (item != null) {
					float rax = s[1 + IronManManualSuitUp.RAX];
					float both = Math.max(0f, Math.min(1f, (-rax - 1.0f) / 0.8f));
					renderPiece(pose, buffers, light, player, new ItemStack(item), 1f, both);
				}
			}
		}
		// v0.15.15: the plates let go of fall in WORLD space (renderDroppedPlates), to the wearer's left
	}

	/**
	 * v0.15.15: the Mark 1's dropped plates, drawn in world space (WorldRenderEvents.AFTER_ENTITIES) so they fall to the
	 * wearer's LEFT -- worked out from the body yaw (facing f = (-sin, cos), left = (cos, sin)) -- whatever the camera
	 * angle: let go of at hand height half a block out to the left, they tumble to the floor and shrink away.
	 */
	static void renderDroppedPlates(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		net.minecraft.world.phys.Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (Player player : mc.level.players()) {
			IronManSuitFx fx = IronManSuitFx.of(player);
			if (com.projecthero.mod.ironman.suit.IronManSuitRemoval.kindOfPose(fx.poseKind())
					!= com.projecthero.mod.ironman.suit.IronManSuitRemoval.KIND_MK1 || player.isInvisible()) {
				continue;
			}
			float age = fx.poseAge(mc.level.getGameTime(), partial);
			String suitId = IronManManualSuitUp.suitOf(fx.poseVariant());
			if (age < 0f || suitId == null) {
				continue;
			}
			var sch = com.projecthero.mod.ironman.suit.IronManSuitRemoval.schedule(
					com.projecthero.mod.ironman.suit.IronManSuitRemoval.KIND_MK1, IronManManualSuitUp.planOf(fx.poseVariant()));
			double yaw = Math.toRadians(net.minecraft.util.Mth.lerp(partial, player.yBodyRotO, player.yBodyRot));
			double fwdX = -Math.sin(yaw);
			double fwdZ = Math.cos(yaw);
			double leftX = Math.cos(yaw);
			double leftZ = Math.sin(yaw);
			net.minecraft.world.phys.Vec3 feet = player.getPosition(partial);
			float scale = player.getScale();
			for (int bit = 0; bit < 4; bit++) {
				int drop = sch.dropAt(bit);
				if (drop < 0) {
					continue;
				}
				float t = age - drop;
				int fall = com.projecthero.mod.ironman.suit.IronManSuitRemoval.FALL_TICKS;
				int vanish = com.projecthero.mod.ironman.suit.IronManSuitRemoval.VANISH_TICKS;
				if (t < 0f || t > fall + vanish) {
					continue;
				}
				IronManArmorItem item = IronManItems.armor(suitId, IronManSuitUpManager.typeOf(slotOf(bit)));
				if (item == null) {
					continue;
				}
				float ft = Math.min(t, fall);
				float u = ft / fall;
				double y0 = bit >= 2 ? 0.75 : 1.05; // the greaves / boots are let go of lower, bent over
				double y = 0.18 + (y0 - 0.18) * (1.0 - u * u); // accelerating down onto the floor
				double side = 0.62 + 0.03 * ft; // drifting a little further out as it falls
				double ahead = 0.15;
				float shrink = t <= fall ? 1f : Math.max(0f, 1f - (t - fall) / vanish);
				if (shrink <= 0.01f) {
					continue;
				}
				double wx = feet.x + (leftX * side + fwdX * ahead) * scale;
				double wy = feet.y + y * scale;
				double wz = feet.z + (leftZ * side + fwdZ * ahead) * scale;
				int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,
						net.minecraft.core.BlockPos.containing(wx, wy + 0.2, wz));
				pose.pushPose();
				pose.translate(wx - cam.x, wy - cam.y, wz - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(yaw) + bit * 40f + ft * 7f));
				pose.mulPose(Axis.XP.rotationDegrees(ft * 11f));
				float sc = 0.55f * shrink * scale;
				pose.scale(sc, sc, sc);
				mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED, light,
						OverlayTexture.NO_OVERLAY, pose, context.consumers(), mc.level, player.getId() + bit);
				pose.popPose();
			}
		}
	}

	private void renderTool(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation id, float grow,
			float tilt, float size) {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getModelManager().getModel(id);
		if (model == null || model == mc.getModelManager().getMissingModel()) {
			return;
		}
		if (dummy == null) {
			dummy = new ItemStack(Items.STICK);
		}
		pose.pushPose();
		getParentModel().translateToHand(HumanoidArm.RIGHT, pose);
		// arm space: +y runs down the arm to the fist, -z is the front of the arm; the fist is ~9 px down
		pose.translate(-1f / 16f, 9.5f / 16f, -0.5f / 16f);
		// the handle sticks out of the front of the fist, turned toward the forearm (+y) by the key's wrist angle
		pose.mulPose(Axis.XP.rotationDegrees(-90f + tilt));
		float sc = size * grow;
		pose.scale(sc, sc, sc);
		// the model's grip point (8, 2.5, 8) to the fist
		pose.translate(0f, 0.5f - 2.5f / 16f, 0f);
		mc.getItemRenderer().render(dummy, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY,
				model);
		pose.popPose();
	}

	/** Where the fist of {@code arm} is right now, in the layer's (model) space. */
	private org.joml.Vector3f fist(HumanoidArm arm) {
		PoseStack ps = new PoseStack();
		getParentModel().translateToHand(arm, ps);
		org.joml.Vector4f v = new org.joml.Vector4f((arm == HumanoidArm.LEFT ? 1f : -1f) / 16f, 10f / 16f, 0f, 1f);
		ps.last().pose().transform(v);
		return new org.joml.Vector3f(v.x, v.y, v.z);
	}

	/**
	 * The piece being fitted, as an upright plate facing forward: hanging from the left fist (picked up off the floor),
	 * or held up between both fists ({@code both} = 1) when it is lifted over the head and pulled down onto the body.
	 */
	private void renderPiece(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ItemStack stack, float grow, float both) {
		org.joml.Vector3f left = fist(HumanoidArm.LEFT);
		org.joml.Vector3f mid = new org.joml.Vector3f(left).add(fist(HumanoidArm.RIGHT)).mul(0.5f);
		org.joml.Vector3f at = new org.joml.Vector3f(left).lerp(mid, both);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		// model space is y-down: flip it upright; in one hand it is gripped by its lower edge (so it never sinks into the
		// floor beside a seated wearer), between both hands lifted overhead it rides a little above them, clear of the head
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pose.translate(0f, ((1f - both) * 2.5f + both * 4.5f) / 16f, -1.5f / 16f);
		float sc = (0.55f + 0.2f * both) * grow;
		pose.scale(sc, sc, sc);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
		pose.popPose();
	}
}
