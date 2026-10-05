package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.network.IronManPosePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.26: Iron Man attack animations. The server says which ability a player is using ({@link IronManPosePayload});
 * this keeps the latest one per player and poses the model for it -- after the flight pose, so a move made mid-air
 * overrides the flight arms. Every pose eases in over 2 ticks and out over its last 2.
 * <ul>
 *   <li>Repulsor: the right palm thrown out at the crosshair with a recoil kick; Charged: both palms, a bigger kick.</li>
 *   <li>Charging: right palm aimed, left hand bracing the right wrist.</li>
 *   <li>Shield: both palms forward, spread. Unibeam: arms swept back and down, chest out.</li>
 *   <li>Flamethrower / wrist laser: the right arm aimed and held. Rocket: aim with the right, left arm back.</li>
 *   <li>Strong punch: a wind-up then a full-reach straight right with a body twist.</li>
 *   <li>Flare: both arms thrown up and out. Micro-missiles: arms out from the sides, shoulders open (the pods).</li>
 * </ul>
 */
public final class IronManAbilityPose {
	/** anim, start game time (with the partial added at read), length. */
	record Play(int anim, long start, int ticks) {
	}

	private static final Map<Integer, Play> PLAYS = new HashMap<>();

	private IronManAbilityPose() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(IronManPosePayload.TYPE, (payload, context) -> context.client().execute(() -> {
			Minecraft mc = context.client();
			if (mc.level == null) {
				return;
			}
			long now = mc.level.getGameTime();
			Play prev = PLAYS.get(payload.playerId());
			// a held ability refreshing itself keeps its original start (so the ease-in doesn't restart)
			long start = prev != null && prev.anim() == payload.anim() && now <= prev.start() + prev.ticks() ? prev.start() : now;
			PLAYS.put(payload.playerId(), new Play(payload.anim(), start, (int) (now - start) + payload.ticks()));
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> PLAYS.clear());
	}

	/** The ability {@code player} is animating right now and how far into it (ticks, with partial), or null. */
	public static Play current(Player player) {
		Minecraft mc = Minecraft.getInstance();
		Play p = PLAYS.get(player.getId());
		if (p == null || mc.level == null) {
			return null;
		}
		if (mc.level.getGameTime() > p.start() + p.ticks()) {
			PLAYS.remove(player.getId());
			return null;
		}
		return p;
	}

	public static float age(Play p) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : mc.level.getGameTime() - p.start() + mc.getTimer().getGameTimeDeltaPartialTick(false);
	}

	public static void apply(Player player, HumanoidModel<?> m, float ageInTicks) {
		Play p = current(player);
		if (p == null) {
			return;
		}
		float t = age(p);
		float w = Mth.clamp(Math.min(t / 2f, (p.ticks() - t) / 2f), 0f, 1f);
		if (w <= 0f) {
			return;
		}
		float aimX = -Mth.HALF_PI + m.head.xRot;
		float aimY = m.head.yRot;
		switch (p.anim()) {
			case IronManAbilityFx.REPULSOR -> {
				float kick = t < 4 ? (4 - t) / 4f * 0.35f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.05f, 0f);
			}
			case IronManAbilityFx.CHARGED -> {
				float kick = t < 6 ? (6 - t) / 6f * 0.55f : 0f;
				set(m.rightArm, w, aimX - kick, aimY - 0.18f, 0f);
				set(m.leftArm, w, aimX - kick, aimY + 0.18f, 0f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -kick * 0.3f);
			}
			case IronManAbilityFx.CHARGING -> {
				float tremble = Mth.sin(ageInTicks * 2.1f) * 0.02f;
				set(m.rightArm, w, aimX + tremble, aimY - 0.05f, 0f);
				set(m.leftArm, w, aimX + 0.15f, aimY + 0.55f, 0f);
			}
			case IronManAbilityFx.BARRIER -> {
				set(m.rightArm, w, aimX - 0.1f, aimY - 0.38f, 0f);
				set(m.leftArm, w, aimX - 0.1f, aimY + 0.38f, 0f);
			}
			case IronManAbilityFx.UNIBEAM -> {
				set(m.rightArm, w, 0.55f, 0f, 0.45f);
				set(m.leftArm, w, 0.55f, 0f, -0.45f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.12f);
			}
			case IronManAbilityFx.FLAME, IronManAbilityFx.LASER -> set(m.rightArm, w, aimX, aimY - 0.05f, 0f);
			case IronManAbilityFx.ROCKET -> {
				set(m.rightArm, w, aimX, aimY - 0.05f, 0f);
				set(m.leftArm, w, 0.6f, 0f, -0.2f);
			}
			case IronManAbilityFx.PUNCH -> {
				if (t < 3f) {
					set(m.rightArm, w, 0.9f, 0.3f, 0f);
					m.body.yRot = Mth.lerp(w, m.body.yRot, 0.35f);
				} else {
					float k = Mth.clamp((t - 3f) / 2f, 0f, 1f);
					set(m.rightArm, w, Mth.lerp(k, 0.9f, aimX), Mth.lerp(k, 0.3f, aimY - 0.1f), 0f);
					m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(k, 0.35f, -0.3f));
				}
			}
			case IronManAbilityFx.FLARE -> {
				set(m.rightArm, w, -0.5f, 0f, 1.5f);
				set(m.leftArm, w, -0.5f, 0f, -1.5f);
			}
			case IronManAbilityFx.MISSILES -> {
				set(m.rightArm, w, 0.25f, 0f, 0.55f);
				set(m.leftArm, w, 0.25f, 0f, -0.55f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.05f);
			}
			default -> {
			}
		}
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
