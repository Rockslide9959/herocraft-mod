package com.projecthero.mod.client.worthy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.worthiness.Worthiness;
import com.projecthero.mod.worthy.WorthyActionPayload;
import com.projecthero.mod.worthy.WorthyFlight;
import com.projecthero.mod.worthy.WorthyKit;
import com.projecthero.mod.worthy.WorthyState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier, client: the sneak + right-click on a player with a Thor weapon is consumed here (so it never toggles
 * Mjolnir's binding -- the server decides whether it is a lend or a refusal), the double-tap flight request, and the thrown lent
 * weapon's flight drawn from {@link WorthyState} (out along the throw, hanging, then home to the live hand, spinning, a jagged
 * lightning trail behind it).
 */
public final class WorthyClient {
	private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;

	private WorthyClient() {
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide || hand != InteractionHand.MAIN_HAND || !(entity instanceof Player) || !player.isShiftKeyDown()
					|| player != Minecraft.getInstance().player || !Worthiness.isWorthy(player)
					|| (ThorWeapon.of(player.getMainHandItem()) == null && ThorWeapon.of(player.getOffhandItem()) == null)) {
				return InteractionResult.PASS;
			}
			return InteractionResult.SUCCESS; // the server lends or refuses (WorthyLending)
		});
		WorldRenderEvents.AFTER_ENTITIES.register(WorthyClient::render);
	}

	/** The double-tap: in the air with a lent weapon in hand (take off), or already flying (land). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return WorthyFlight.isFlying(player) || (!player.onGround() && WorthyKit.kit(player) != WorthyKit.Kit.NONE);
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new WorthyActionPayload(WorthyActionPayload.TOGGLE_FLIGHT, 0));
	}

	/** The Thor weapon screen's Call back button. */
	public static void callBack(ThorWeapon weapon) {
		ClientPlayNetworking.send(new WorthyActionPayload(WorthyActionPayload.CALL_BACK, weapon.ordinal()));
	}

	// ================================================================== the thrown weapon

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		for (Player p : mc.level.players()) {
			WorthyState s = p.getAttachedOrElse(com.projecthero.mod.worthy.WorthyAttachments.STATE, null);
			if (s == null || s.throwWeapon < 0 || p.distanceToSqr(cam) > MAX_DISTANCE_SQ) {
				continue;
			}
			double t = now - s.throwStart;
			int total = s.throwOut + s.throwHold + s.throwBack;
			if (t < 0 || t > total) {
				continue;
			}
			ThorWeapon weapon = ThorWeapon.values()[Math.max(0, Math.min(1, s.throwWeapon))];
			ItemStack stack = ThorThrown.of(p).in(s.throwOff ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (stack.isEmpty()) {
				stack = new ItemStack(weapon.item());
			}
			Vec3 from = new Vec3(s.fromX, s.fromY, s.fromZ);
			Vec3 to = new Vec3(s.toX, s.toY, s.toZ);
			Vec3 pos;
			Vec3 prev;
			if (t <= s.throwOut) {
				double k = t / s.throwOut;
				pos = from.lerp(to, k);
				prev = from.lerp(to, Math.max(0, k - 0.25));
			} else if (t <= s.throwOut + s.throwHold) {
				pos = to;
				prev = to.add(0, 0.6, 0);
			} else {
				double k = Mth.clamp((t - s.throwOut - s.throwHold) / s.throwBack, 0.0, 1.0);
				Vec3 hand = hand(p, s.throwOff, partial);
				Vec3 ctrl = to.lerp(hand, 0.5).add(0, 1.0, 0);
				double e = 1 - (1 - k) * (1 - k);
				pos = bezier(to, ctrl, hand, e);
				prev = bezier(to, ctrl, hand, Math.max(0, e - 0.2));
			}
			boolean axe = weapon == ThorWeapon.STORMBREAKER;
			// the lightning trail
			VertexConsumer vc = ThorDraw.buffer(buffers);
			PoseStack.Pose pose = ps.last();
			double seed = p.getId() * 3.7;
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(pos.subtract(cam), prev.subtract(cam), 6, 0.18, seed + Math.floor(now * 0.6) * 7),
					axe ? 0.55f : 0.45f, 0.75f);
			// the weapon, spinning about its flight
			Vec3 dir = (t <= s.throwOut ? to.subtract(from) : pos.subtract(prev));
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			float spin = (float) (t * (axe ? 45 : -55));
			ps.pushPose();
			ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
			ps.mulPose(Axis.YP.rotationDegrees(yaw));
			ps.mulPose(Axis.XP.rotationDegrees(spin));
			ps.scale(1.1f, 1.1f, 1.1f);
			mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps,
					buffers, mc.level, 0);
			ps.popPose();
		}
	}

	private static Vec3 hand(Player p, boolean off, float partial) {
		Vec3 feet = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()));
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		boolean rightSide = (p.getMainArm() == HumanoidArm.RIGHT) != off;
		return feet.add(0, p.getBbHeight() * 0.62, 0).add(right.scale(rightSide ? 0.4 : -0.4)).add(fwd.scale(0.35));
	}

	private static Vec3 bezier(Vec3 a, Vec3 c, Vec3 b, double t) {
		double u = 1 - t;
		return a.scale(u * u).add(c.scale(2 * u * t)).add(b.scale(t * t));
	}
}
