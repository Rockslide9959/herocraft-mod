package com.projecthero.mod.client.omnitrix.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttAnimatable;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttFirstPerson;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttModel;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttRenderer;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Wildmutt's client side -- the first alien with a fully custom body ({@link #customBody}): a GeckoLib quadruped
 * ({@link WildmuttRenderer}, clips picked by {@link WildmuttAnimatable}) in place of the player model, his own clawed arms in
 * first person ({@link WildmuttFirstPerson}, both always in view), the scent trail to a tracked creature and the Scent row on
 * the HUD. The outlines of what he senses are {@code WildmuttSense} (two glow mixins).
 */
public final class WildmuttClient implements AlienClientKit {
	private static final DustParticleOptions SCENT = new DustParticleOptions(new Vector3f(1.0f, 0.42f, 0.25f), 0.7f);

	// first-person clips for the moves: {tick, pitch, yaw, roll, x, y, z}; two-handed (both paws)
	private static final FirstPersonClip FP_POUNCE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, 18, 0, 0, 0, -0.15f, 0.15f), FirstPersonClip.f(6, -70, 8, 0, 0.06f, 0.42f, -0.2f),
			FirstPersonClip.f(9, 25, 14, 0, 0.1f, -0.1f, -0.3f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MAUL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(2, -80, -10, 20, 0, 0.5f, 0.1f), FirstPersonClip.f(5, 20, 25, -10, 0.2f, -0.1f, -0.25f),
			FirstPersonClip.f(8, -80, -10, 20, 0, 0.5f, 0.1f), FirstPersonClip.f(11, 20, 25, -10, 0.2f, -0.1f, -0.25f),
			FirstPersonClip.f(14, -95, 0, 0, 0.05f, 0.6f, 0.1f), FirstPersonClip.f(17, 30, 10, 0, 0.1f, -0.25f, -0.2f),
			FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(7, -100, 5, 0, 0.05f, 0.65f, 0.2f), FirstPersonClip.f(9, 35, 10, 0, 0.05f, -0.35f, -0.15f),
			FirstPersonClip.f(14, 30, 10, 0, 0.05f, -0.3f, -0.12f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(5, 10, -25, 0, -0.15f, -0.12f, 0.1f), FirstPersonClip.f(24, 12, -28, 0, -0.18f, -0.14f, 0.1f),
			FirstPersonClip.f(29, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FRENZY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(4, -60, 0, 0, 0, 0.35f, 0), FirstPersonClip.f(7, 25, 10, 0, 0.05f, -0.2f, -0.1f),
			FirstPersonClip.f(11, -60, 0, 0, 0, 0.35f, 0), FirstPersonClip.f(14, 25, 10, 0, 0.05f, -0.2f, -0.1f),
			FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_QUILLS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, 14, -10, 6, -0.05f, -0.18f, 0.1f), FirstPersonClip.f(8, 14, -10, -6, -0.05f, -0.18f, 0.1f),
			FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, -40, 0, 0, 0, 0.25f, -0.1f), FirstPersonClip.f(14, -30, 4, 0, 0, 0.2f, -0.1f),
			FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return WildmuttModel.TEXTURE;
	}

	// ---------------------------------------------------------------- the custom body

	@Override
	public boolean customBody() {
		return true;
	}

	@Override
	public void buildBody(EntityRendererProvider.Context context) {
		WildmuttRenderer.rebuild(context);
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		WildmuttRenderer r = WildmuttRenderer.get();
		if (r != null) {
			r.render(p, yaw, partial, pose, buffers, light);
		}
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		WildmuttFirstPerson.render(pose, buffers, light, p, right);
		return true;
	}

	@Override
	public boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WildmuttKit.ANIM_POUNCE -> FP_POUNCE;
			case WildmuttKit.ANIM_MAUL -> FP_MAUL;
			case WildmuttKit.ANIM_SLAM -> FP_SLAM;
			case WildmuttKit.ANIM_ROAR -> FP_ROAR;
			case WildmuttKit.ANIM_FRENZY -> FP_FRENZY;
			case WildmuttKit.ANIM_QUILLS -> FP_QUILLS;
			case WildmuttKit.ANIM_LEAP -> FP_LEAP;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- ambience, the scent trail

	@Override
	public void tick(AbstractClientPlayer p) {
		Minecraft mc = Minecraft.getInstance();
		if (p != mc.player || !(Aliens.kit(WildmuttKit.ID) instanceof WildmuttKit k)) {
			return;
		}
		// the trail to the tracked creature: a drift of scent along the ground toward it (his eyes only)
		Entity t = k.tracked(p);
		if (t != null && p.tickCount % 3 == 0) {
			Vec3 from = p.position().add(0, 0.4, 0);
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 d = to.subtract(from);
			double len = d.length();
			int n = (int) Math.min(24, len);
			double phase = (p.tickCount % 12) / 12.0;
			for (int i = 1; i <= n; i++) {
				double f = (i - 1 + phase) / Math.max(1, len);
				Vec3 q = from.add(d.scale(f));
				double wob = Mth.sin((float) (i * 1.7 + p.tickCount * 0.2)) * 0.12;
				p.level().addParticle(SCENT, q.x + wob, q.y, q.z - wob, 0, 0.01, 0);
			}
		}
	}

	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		if (!(Aliens.kit(WildmuttKit.ID) instanceof WildmuttKit k)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		long now = p.level().getGameTime();
		Entity t = k.tracked(p);
		double range = k.senseRange(p);
		float frac;
		String value;
		int col;
		if (t != null) {
			frac = Mth.clamp((k.timer(p, "track_end") - now) / (float) WildmuttKit.TRACK_TICKS, 0f, 1f);
			value = String.format(java.util.Locale.ROOT, "%dm", Math.round(Math.sqrt(t.distanceToSqr(p))));
			col = 0xFFFF5A3A;
		} else if (range <= 0) {
			frac = 0f;
			value = Component.translatable("hud.projecthero.omnitrix.wildmutt.deaf").getString();
			col = 0xFF8A8A8A;
		} else {
			frac = (float) (range / WildmuttKit.SNIFF_RANGE);
			value = String.format(java.util.Locale.ROOT, "%dm", Math.round(range));
			col = 0xFFF0A040;
		}
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.wildmutt.scent"), frac, col, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return 10;
	}
}
