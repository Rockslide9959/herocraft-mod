package com.projecthero.mod.client.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.client.moonknight.MoonKnightCapeLayer;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.SupermanSuit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.9: the Superman Suit's red cloth cape, drawn for anyone wearing the Superman chestplate (so every player sees
 * it). It is Moon Knight's cape mesh ({@link MoonKnightCapeLayer#drawCape}: a centre panel and two curled side panels,
 * 1.25 blocks long) without the hood, Cape Block or glide webbing, with the texture spread by real width so the shield
 * on its back is not stretched, and a short collar laid over the shoulders to join it to the suit.
 *
 * <p><b>How it moves.</b> On the ground: exactly vanilla's cape swing ({@link MoonKnightCapeLayer#cloakSwing} -- the
 * lagging cloak position, walk bob, crouch). In Kryptonian flight it is driven by the wind of his own flight instead:
 * the cape points along gravity plus the wind (the reverse of his velocity), {@code atan2(forward speed, G + vertical
 * speed)} from straight down, then that is taken back into the leaning body's frame by subtracting the flight lean
 * ({@link FlightPoseHelper#lean}). So hovering it hangs, cruising (0.9 b/t, 25 deg lean) it streams out behind at
 * about 50 deg off the back, and at super-speed (2 b/t, body flat) it lies near-horizontal along his legs, rippling;
 * climbing straight up it trails below him, diving it streams up behind. The flight value is eased per player so a
 * per-tick velocity step never snaps it.
 */
public class SupermanCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/superman_cape.png");
	/** Gravity's pull against the wind, in blocks/tick of "wind": 0.9 b/t of flight streams the cape ~77 deg. */
	private static final double GRAVITY = 0.2;
	/** Never closer to the back / legs than this (deg), so it never cuts into the suit. */
	private static final float MIN_SWING = 6.0F;
	private static final float MAX_SWING = 120.0F;

	/** Per player: {eased flight weight 0..1, eased flight swing, last frame nanos}. */
	private static final Map<UUID, double[]> EASE = new ConcurrentHashMap<>();

	public SupermanCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float pt, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !SupermanSuit.wearsCape(player)) {
			return;
		}
		float time = ageInTicks + pt;
		float[] cloak = MoonKnightCapeLayer.cloakSwing(player, pt);
		float swing = cloak[0];
		float side = cloak[1];

		boolean flying = Kryptonian.isFlying(player);
		double[] ease = EASE.computeIfAbsent(player.getUUID(), k -> new double[]{0, cloak[0], System.nanoTime()});
		double dt = Math.min(0.2, (System.nanoTime() - ease[2]) / 1.0e9);
		ease[2] = System.nanoTime();

		// the wind of his own flight, in the body's frame (forward = along his body yaw)
		double vx = player.getX() - player.xo;
		double vy = player.getY() - player.yo;
		double vz = player.getZ() - player.zo;
		float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double forward = -vx * Mth.sin(yaw) + vz * Mth.cos(yaw);
		double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (flying) {
			float world = (float) Math.toDegrees(Math.atan2(forward, GRAVITY + vy));
			float lean = FlightPoseHelper.lean(player, pt);
			float stream = (float) Mth.clamp(speed, 0.0, 1.0);
			// a fast cape snaps and ripples
			float flap = (Mth.sin(time * 1.7F) * 2.5F + Mth.sin(time * 2.9F + 1.3F) * 1.5F) * stream;
			float target = Mth.clamp(world - lean + flap, MIN_SWING, MAX_SWING);
			ease[1] += (target - ease[1]) * (1.0 - Math.exp(-dt * 9.0));
		} else {
			ease[1] = swing;
		}
		ease[0] += ((flying ? 1 : 0) - ease[0]) * (1.0 - Math.exp(-dt * 6.0));
		swing = (float) Mth.lerp(ease[0], swing, ease[1]);
		side *= (float) (1.0 - 0.6 * ease[0]);

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		pose.pushPose();
		pose.translate(0.0F, -0.035F, 0.125F + 0.07F); // over the shoulders, behind the back, clear of the inflated suit
		drawCollar(vc, pose.last(), light);
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
		pose.mulPose(Axis.YP.rotationDegrees(-side / 2.0F));
		MoonKnightCapeLayer.drawCape(vc, pose.last(), light, 0.0, time, Math.max(player.getDeltaMovement().horizontalDistance(), speed),
				true);
		pose.popPose();
	}

	/** The collar: the cape's top edge carried forward over the tops of the shoulders, so it hangs from them. */
	private static void drawCollar(VertexConsumer vc, PoseStack.Pose pose, int light) {
		Vec3[] back = MoonKnightCapeLayer.section(0.0, 0.0, 0.0);
		int n = back.length;
		for (int i = 0; i < n - 1; i++) {
			Vec3 b0 = back[i];
			Vec3 b1 = back[i + 1];
			Vec3 f0 = new Vec3(b0.x * 0.6, -0.002, -0.19);
			Vec3 f1 = new Vec3(b1.x * 0.6, -0.002, -0.19);
			float u0 = (float) i / (n - 1) * 0.5F;
			float u1 = (float) (i + 1) / (n - 1) * 0.5F;
			MoonKnightCapeLayer.quad(vc, pose, light, f0, f1, b1, b0, u0, 0.0F, u1, 0.03F);
		}
	}

	/** Forget every player's easing (world change / disconnect). */
	public static void clear() {
		EASE.clear();
	}
}
