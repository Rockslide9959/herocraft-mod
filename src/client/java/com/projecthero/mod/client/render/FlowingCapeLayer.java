package com.projecthero.mod.client.render;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.client.moonknight.MoonKnightCapeLayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the Superman Suit's flowing cloth cape ({@code SupermanCapeLayer}, v0.14.9) generalised so any suit can
 * wear one -- first used for Thor's crimson cape ({@code ThorCapeLayer}); v0.14.21 the Superman cape itself moved onto
 * it, so both capes share one implementation. A subclass only says <em>who</em> wears it,
 * with which texture, and when they count as flying; the cloth itself is Moon Knight's cape mesh
 * ({@link MoonKnightCapeLayer#drawCape}, texture spread by real width) plus a collar laid over the shoulders.
 *
 * <p><b>How it moves</b> (identical to the Superman cape). On the ground: vanilla's cape swing
 * ({@link MoonKnightCapeLayer#cloakSwing} -- the lagging cloak position, walk bob, crouch). In flight it is driven by
 * the wind of the flight instead: it points along gravity plus the wind (the reverse of the velocity),
 * {@code atan2(forward speed, G + vertical speed)} from straight down, taken back into the leaning body's frame by
 * subtracting the flight lean ({@link FlightPoseHelper#lean}) -- hovering it hangs, cruising it streams out behind,
 * at full speed it lies along the legs, rippling. The flight value is eased per player so a velocity step never snaps
 * it. Crouching also shifts the anchor the way vanilla shifts its own cloak part (the body leans forward), so the
 * cape stays on the back instead of sinking into it.
 *
 * <p>Like every player render layer it is not drawn in first person, so it can never block the view.
 */
public abstract class FlowingCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** Gravity's pull against the wind, in blocks/tick of "wind". */
	private static final double GRAVITY = 0.2;
	/** Never closer to the back / legs than this (deg), so it never cuts into the suit. */
	private static final float MIN_SWING = 6.0F;
	private static final float MAX_SWING = 120.0F;
	/** Vanilla's crouching cloak offset ({@code PlayerModel#setupAnim}: cloak.y = 1.85, cloak.z = 1.4), in blocks. */
	private static final float CROUCH_Y = 1.85F / 16.0F;
	private static final float CROUCH_Z = 1.4F / 16.0F;

	/** Per player: {eased flight weight 0..1, eased flight swing, last frame nanos}. One map per layer instance. */
	private final Map<UUID, double[]> ease = new ConcurrentHashMap<>();

	protected FlowingCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** Whether {@code player} is wearing this cape right now. */
	protected abstract boolean wearsCape(AbstractClientPlayer player);

	protected abstract ResourceLocation texture(AbstractClientPlayer player);

	/** Whether the cape should stream in the wind of a flight rather than swing like a walking cloak. */
	protected abstract boolean isFlying(AbstractClientPlayer player);

	/**
	 * How much of the cape is showing (0..1): it unrolls down from the collar as this rises, so a suit that forms
	 * piece by piece can grow its cape with it. Fully shown by default.
	 */
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		return 1.0F;
	}

	/** How far behind the back the cape hangs, in blocks -- clear of the suit's inflated body. */
	protected float backOffset() {
		return 0.125F + 0.07F;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float pt, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !wearsCape(player)) {
			return;
		}
		float shown = Mth.clamp(unfurl(player, pt), 0.0F, 1.0F);
		if (shown <= 0.01F) {
			return;
		}
		float time = ageInTicks + pt;
		float[] cloak = MoonKnightCapeLayer.cloakSwing(player, pt);
		float swing = cloak[0];
		float side = cloak[1];

		boolean flying = isFlying(player);
		double[] e = ease.computeIfAbsent(player.getUUID(), k -> new double[] { 0, cloak[0], System.nanoTime() });
		double dt = Math.min(0.2, (System.nanoTime() - e[2]) / 1.0e9);
		e[2] = System.nanoTime();

		// the wind of the flight, in the body's frame (forward = along the body yaw)
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
			e[1] += (target - e[1]) * (1.0 - Math.exp(-dt * 9.0));
		} else {
			e[1] = swing;
		}
		e[0] += ((flying ? 1 : 0) - e[0]) * (1.0 - Math.exp(-dt * 6.0));
		swing = (float) Mth.lerp(e[0], swing, e[1]);
		side *= (float) (1.0 - 0.6 * e[0]);

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(player)));
		pose.pushPose();
		if (player.isCrouching() && !flying) {
			pose.translate(0.0F, CROUCH_Y, CROUCH_Z);
		}
		pose.translate(0.0F, -0.035F, backOffset()); // over the shoulders, behind the back
		drawCollar(vc, pose.last(), light);
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
		pose.mulPose(Axis.YP.rotationDegrees(-side / 2.0F));
		if (shown < 1.0F) {
			pose.scale(1.0F, shown, 1.0F);
		}
		MoonKnightCapeLayer.drawCape(vc, pose.last(), light, 0.0, time,
				Math.max(player.getDeltaMovement().horizontalDistance(), speed), true);
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
	public void clear() {
		ease.clear();
	}

	/** Every live cape layer (weak: a resource reload rebuilds the renderers), so their easing can be dropped on disconnect. */
	private static final Set<FlowingCapeLayer> LIVE = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private static boolean disconnectHooked;

	/**
	 * v0.14.21: adds a cape layer made by {@code factory} to both player renderers (wide + slim) and forgets its easing
	 * on disconnect. Call once per cape from client init.
	 */
	public static void register(Function<PlayerRenderer, ? extends FlowingCapeLayer> factory) {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				FlowingCapeLayer layer = factory.apply(playerRenderer);
				LIVE.add(layer);
				helper.register(layer);
			}
		});
		if (!disconnectHooked) {
			disconnectHooked = true;
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
				synchronized (LIVE) {
					LIVE.forEach(FlowingCapeLayer::clear);
				}
			});
		}
	}
}
