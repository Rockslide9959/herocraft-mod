package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>client</b> half of the portable forms ({@link PortableForm}). Everything
 * here reads the synced pose clock only ({@link IronManSuitFx#STYLE_PORTABLE}, the form from the pose kind), so every
 * viewer sees the same frame:
 * <ul>
 *   <li>{@link #texture} -- the worn pieces built texel by texel ({@code IronManGantryBuild} portable scheme);</li>
 *   <li>{@link #incomplete} / {@link #bare} -- lights dark and the skin overlay showing until a part is on;</li>
 *   <li>{@link #pose} -- the form's body-pose keys;</li>
 *   <li>the prop -- a {@link PortableProp} registered per {@link PortableForm#propId}, drawn in two passes: on the body
 *       (a player layer: in the hand / on the back) and in the world (after entities: on the floor / a block, anchored
 *       where the sequence started -- {@link Anchor}, captured client-side the first frame a viewer sees the clock).</li>
 * </ul>
 */
public final class IronManPortableClient {
	/** Where a running sequence's floor prop sits: the wearer's feet and body yaw when it started. */
	public record Anchor(long poseStart, Vec3 pos, float yaw) {
		/** World position {@code right} / {@code up} / {@code fwd} blocks from the anchor, in its yaw frame. */
		public Vec3 at(double right, double up, double fwd) {
			double r = Math.toRadians(yaw);
			double fx = -Math.sin(r), fz = Math.cos(r);
			return pos.add(-fz * right + fx * fwd, up, fx * right + fz * fwd);
		}
	}

	/**
	 * A packed form's prop renderer. Both passes get the frame on the deploy clock (a fold runs it backwards) and draw
	 * nothing outside their own part of it.
	 */
	public interface PortableProp {
		/** On the body (player render layer, model space): in the hand / on the back. */
		default void renderBody(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
				PlayerModel<AbstractClientPlayer> model, PortableForm form, float frame) {
		}

		/**
		 * In the world (after entities): {@code pose} is already at the anchor, turned to its yaw -- local +z is the
		 * wearer's forward, -x their right, +y up, 1 unit = 1 block.
		 */
		default void renderWorld(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
				PortableForm form, float frame, Anchor anchor) {
		}
	}

	private static final Map<String, PortableProp> PROPS = new HashMap<>();
	private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();

	private IronManPortableClient() {
	}

	/** Register the renderer of a {@link PortableForm#propId}. */
	public static void registerProp(String propId, PortableProp prop) {
		PROPS.put(propId, prop);
	}

	public static void initialize() {
		registerProp(PortableForms.MK6_BRIEFCASE.propId(), new Mk6BriefcaseProp());
		// v0.16.7 (user) Era II stage 2: the Mk VII / Mk VIII backpacks (one shared prop, per-suit style)
		registerProp(PortableForms.MK7_BACKPACK.propId(), new BackpackProp(BackpackProp.MK7));
		registerProp(PortableForms.MK8_THRUSTER_PACK.propId(), new BackpackProp(BackpackProp.MK8));
		registerProp(PortableForms.MK9_HEAT_CASE.propId(), new Mk9HeatCaseProp()); // v0.16.7 (user) Era II: Mk IX heat case
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new BodyLayer(playerRenderer));
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(IronManPortableClient::renderWorld);
	}

	/** Leaving a world: drop the anchors. */
	public static void clear() {
		ANCHORS.clear();
	}

	// ---------------- reading the clock ----------------

	/** The form {@code player} is deploying / folding right now, or null. */
	public static PortableForm form(Player player) {
		if (player.level() == null) {
			return null;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		if (!fx.portable() || fx.poseAge(player.level().getGameTime(), 0f) < 0f) {
			return null;
		}
		return PortableForms.byPoseKind(fx.poseKind());
	}

	/** The frame (deploy clock) {@code player} is at, or NaN outside a portable sequence. */
	public static float frame(Player player, float partialTick) {
		PortableForm form = form(player);
		if (form == null) {
			return Float.NaN;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		float age = fx.poseAge(player.level().getGameTime(), partialTick);
		if (age < 0f) {
			return Float.NaN;
		}
		return Mth.clamp(form.frame(PortableForms.deployPose(fx.poseKind()), age), 0f, form.beats().total());
	}

	public static boolean active(Player player) {
		return form(player) != null;
	}

	/** Does the prop own the right hand right now (so the ordinary held item is not drawn)? */
	public static boolean ownsRightHand(Player player) {
		PortableForm form = form(player);
		return form != null && form.rest() == PortableForm.Place.HAND;
	}

	/**
	 * v0.16.7 (user) Era II fold polish, FIRST person: should the vanilla held item be hidden from the main hand? A
	 * hand-carried form's case stays in your hand until it is let go at {@code dropFrom} (the floor prop takes over from
	 * there); a fold hides the hand throughout (the slot does not hold the case until the fold's last tick, when it is
	 * raised into the hand like any newly held item).
	 */
	public static boolean firstPersonHidesHeld(Player player, float partialTick) {
		// v0.16.7 (user) fix: the world pass now draws your own case at the first-person hand from frame 0
		// ({@link #firstPersonHand}), so the vanilla held item is hidden for the whole sequence (no double case)
		return ownsRightHand(player);
	}

	/** First person: the held case sits this far ahead / to the right / below the eye (blocks, vanilla held-item-ish). */
	static final float FP_HAND_FWD = 0.62f, FP_HAND_RIGHT = 0.36f, FP_HAND_DOWN = 0.40f;

	/**
	 * v0.16.7 (user) fix, FIRST person: where your right hand holds a case in your own view, as a matrix in the anchor
	 * frame (+z the anchor's forward, -x its right, +y up; no scale) -- in front of the camera, low and to the right, turned
	 * with your view's yaw. The case props start their hand-to-floor move from here when drawn for your own eyes (the body
	 * model's hand hangs at the hip, out of view), so the case is seen in your hand and then set down without a gap.
	 */
	public static org.joml.Matrix4f firstPersonHand(Anchor a) {
		net.minecraft.client.Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
		org.joml.Vector3f look = c.getLookVector(), up = c.getUpVector(), left = c.getLeftVector();
		Vec3 eye = c.getPosition();
		double wx = eye.x + look.x() * FP_HAND_FWD - left.x() * FP_HAND_RIGHT - up.x() * FP_HAND_DOWN;
		double wy = eye.y + look.y() * FP_HAND_FWD - left.y() * FP_HAND_RIGHT - up.y() * FP_HAND_DOWN;
		double wz = eye.z + look.z() * FP_HAND_FWD - left.z() * FP_HAND_RIGHT - up.z() * FP_HAND_DOWN;
		float yaw = (float) Math.toRadians(a.yaw());
		org.joml.Vector3f local = new org.joml.Vector3f((float) (wx - a.pos().x), (float) (wy - a.pos().y), (float) (wz - a.pos().z))
				.rotateY(yaw);
		float relYaw = (float) Math.toRadians(-(c.getYRot() - a.yaw()));
		return new org.joml.Matrix4f().translation(local).rotateY(relYaw);
	}

	/** v0.16.7 (user) fold polish: is {@code player} the one whose eyes this frame is drawn from (first person)? */
	public static boolean viewedFromInside(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
	}

	/**
	 * v0.16.7 (user) fold polish: the scale of a flying plate at anchor-local ({@code x}, {@code y}, {@code z}) -- 1, or, seen
	 * from inside the wearer's own eyes, shrinking to nothing as it nears them. The plates fly to the build height, and at
	 * the helmet that is the camera: they used to fill the screen as big dark blocks in first person.
	 */
	public static float nearEyeScale(boolean inside, float x, float y, float z) {
		if (!inside) {
			return 1f;
		}
		float dy = y - 1.62f;
		float d = (float) Math.sqrt(x * x + dy * dy + z * z);
		return Mth.clamp((d - 0.35f) / 0.4f, 0f, 1f);
	}

	/** The texture of {@code player}'s piece in {@code slot} during a portable sequence, or null outside one. */
	public static ResourceLocation texture(Player player, String setId, EquipmentSlot slot, ResourceLocation base,
			float partialTick) {
		float f = frame(player, partialTick);
		return Float.isNaN(f) ? null : IronManGantryBuild.portableTexture(form(player), setId, slot, base, f);
	}

	/** Is part of the piece still missing (its lights stay dark until it is whole)? */
	public static boolean incomplete(Player player, EquipmentSlot slot, String setId, float partialTick) {
		float f = frame(player, partialTick);
		return !Float.isNaN(f) && IronManGantryBuild.portableIncomplete(form(player), setId, slot, f);
	}

	/** Should skin-overlay region {@code region} ({@link IronManSkinOverlay}) show although armour is in its slot? */
	public static boolean bare(Player player, int region) {
		float f = frame(player, 0f);
		if (Float.isNaN(f)) {
			return false;
		}
		PortableForm form = form(player);
		int first = switch (region) {
			case IronManSkinOverlay.HAT -> form.firstAppear(GantryTimeline.HELMET, GantryTimeline.FACEPLATE);
			case IronManSkinOverlay.JACKET -> form.firstAppear(GantryTimeline.TORSO_TOP, GantryTimeline.TORSO_BOTTOM,
					GantryTimeline.JACKET);
			case IronManSkinOverlay.SLEEVES -> form.firstAppear(GantryTimeline.R_GAUNTLET, GantryTimeline.L_GAUNTLET,
					GantryTimeline.R_ARM, GantryTimeline.L_ARM, GantryTimeline.R_SLEEVE, GantryTimeline.L_SLEEVE);
			default -> form.firstAppear(GantryTimeline.R_BOOT, GantryTimeline.L_BOOT, GantryTimeline.THIGH_TOP,
					GantryTimeline.THIGH_BOTTOM, GantryTimeline.PANTS);
		};
		return f < first - 0.5f;
	}

	/** The body pose {weight, key...} ({@code IronManSuitPoses} layout), or null outside a portable sequence. */
	public static float[] pose(Player player, float partialTick) {
		float f = frame(player, partialTick);
		return Float.isNaN(f) ? null : form(player).pose(f);
	}

	/** The anchor of {@code player}'s running sequence (captured the first time this client sees its clock). */
	public static Anchor anchor(Player player, float partialTick) {
		IronManSuitFx fx = IronManSuitFx.of(player);
		Anchor a = ANCHORS.get(player.getId());
		if (a == null || a.poseStart() != fx.poseStart()) {
			a = new Anchor(fx.poseStart(), player.getPosition(partialTick), Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
			ANCHORS.put(player.getId(), a);
		}
		return a;
	}

	// ---------------- the two prop passes ----------------

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		renderOwnStraps(context, mc, partial, cam);
		for (AbstractClientPlayer player : mc.level.players()) {
			PortableForm form = form(player);
			if (form == null || player.isInvisible()) {
				continue;
			}
			PortableProp prop = PROPS.get(form.propId());
			if (prop == null) {
				continue;
			}
			Anchor anchor = anchor(player, partial);
			float f = frame(player, partial);
			PoseStack pose = context.matrixStack();
			pose.pushPose();
			pose.translate(anchor.pos().x - cam.x, anchor.pos().y - cam.y, anchor.pos().z - cam.z);
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-anchor.yaw()));
			int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,
					net.minecraft.core.BlockPos.containing(anchor.at(0, 0.3, 0.8)));
			try {
				prop.renderWorld(pose, context.consumers(), light, player, form, f, anchor);
			} finally {
				pose.popPose();
			}
		}
	}

	/**
	 * v0.16.7 (user) fix: in plain first person (no full-body view running) a carried Mk VII / Mk VIII pack shows as its
	 * shoulder straps down your chest when you look down ({@link BackpackProp#renderOwnStraps}), drawn in the same body
	 * frame the player renderer would use (feet, body yaw, the crouch lean).
	 */
	private static void renderOwnStraps(WorldRenderContext context, Minecraft mc, float partial, Vec3 cam) {
		AbstractClientPlayer p = mc.player;
		if (p == null || !viewedFromInside(p) || p.isInvisible() || p.isSpectator() || p.isSleeping() || p.isSwimming()
				|| p.isFallFlying() || active(p) || com.projecthero.mod.client.fpbody.FirstPersonBody.active()) {
			return;
		}
		String carried = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_BACK_PACK, "");
		if (carried.isEmpty()) {
			return;
		}
		var chest = p.getItemBySlot(EquipmentSlot.CHEST);
		if (chest.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
			return; // never over an Iron Man suit (as on the back)
		}
		PortableForm packed = PortableForms.byId(carried);
		if (packed == null || !(PROPS.get(packed.propId()) instanceof BackpackProp bp)) {
			return;
		}
		Vec3 at = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		// LivingEntityRenderer's model frame
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - bodyYaw));
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		if (p.isCrouching()) {
			pose.translate(0f, 3.2f / 16f, 0f); // the crouched body: lower and leaning forward (PlayerModel)
			pose.mulPose(com.mojang.math.Axis.XP.rotation(0.5f));
		}
		if (!chest.isEmpty()) {
			pose.translate(0f, 0f, -1.1f / 16f); // over a worn chestplate
		}
		int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, p.blockPosition().above());
		bp.renderOwnStraps(pose, context.consumers(), light);
		pose.popPose();
	}

	/** The body pass: a player render layer. */
	static final class BodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
		BodyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (player.isInvisible()) {
				return;
			}
			PortableForm form = form(player);
			if (form == null) {
				renderBack(pose, buffers, light, player);
				return;
			}
			PortableProp prop = PROPS.get(form.propId());
			if (prop != null) {
				prop.renderBody(pose, buffers, light, player, getParentModel(), form, frame(player, partialTick));
			}
		}

		/**
		 * v0.16.7 (user) Era II stage 2, outside a sequence: a carried backpack is worn on the back (the synced
		 * {@code IRON_MAN_BACK_PACK}; pushed out over a worn chestplate, never drawn over an Iron Man suit), and a Mk VIII
		 * deployed out of its thruster pack keeps its O2 tanks ({@code IRON_MAN_PACK_TANKS}).
		 */
		private void renderBack(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
			PlayerModel<AbstractClientPlayer> model = getParentModel();
			if (player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_PACK_TANKS, false)
					&& BackpackProp.wearsMk8Chest(player)) {
				BackpackProp.renderWornTanks(pose, buffers, light, model);
				return;
			}
			String carried = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_BACK_PACK, "");
			if (carried.isEmpty()) {
				return;
			}
			var chest = player.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return;
			}
			PortableForm packed = PortableForms.byId(carried);
			if (packed != null && PROPS.get(packed.propId()) instanceof BackpackProp bp) {
				bp.renderPacked(pose, buffers, light, model, chest.isEmpty() ? 0f : 1.1f);
			}
		}
	}
}
