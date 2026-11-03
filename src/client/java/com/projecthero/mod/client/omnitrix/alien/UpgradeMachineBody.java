package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit.MergeKind;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.2 (user: "When the player takes over a machine they should become the machine, they should be able to move around and
 * walk around, usually when upgrade merges with tech in the show the tech gets a visual upgrade and the look of it changes ...
 * give all the things that upgrade can upgrade upgraded models with the black and white colour scheme with the green wiring
 * pattern on the black parts please").
 *
 * <p>While Upgrade carries a machine ({@link MergeKind#stored()}) {@code AlienClients.shownKit} hands out this kit instead of
 * {@link UpgradeClient}: the player body is replaced by the machine's UPGRADED form, built from boxes on
 * {@code upgrade_machine.png} (white plating, black panels with his lime circuit traces, green-lit lenses; its glowing copy on
 * top, full-bright) -- a dispenser becomes a turret on four legs, a furnace a heavy plated walker, a piston a pile driver, a
 * note block a speaker bot, a jukebox a walking boombox, an observer a one-eyed watcher, a hopper a hovering vacuum, a minecart
 * a hover-cart and a boat a hover-boat. Legs stride with his walk, the turret / eye follow his look, R (ANIM_MACHINE) kicks it.
 */
public final class UpgradeMachineBody extends UpgradeClient {
	public static final ResourceLocation MACHINE = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_machine.png");
	public static final ResourceLocation MACHINE_GLOW = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_machine_glow.png");

	// the atlas' 16x16 tiles (gen_upgrade_machines_v0172.js)
	static final int WHITE = 0, CIRCUIT = 1, LENS = 2, JOINT = 3, CIRCUIT2 = 4, SCREEN = 5, GRILLE = 6, BAND = 7, FLAT_WHITE = 8, FLAT_BLACK = 12;
	private static final boolean[] LIT = { false, true, true, false, true, true, true, true, false, false, false, false, false, false, false, false };

	/** The machine {@code p} is right now, or null if he isn't carrying one. */
	@Nullable
	public static MergeKind kindOf(Player p) {
		OmnitrixState st = Omnitrix.state(p);
		long k = st.readyAt(UpgradeKit.ID + "." + UpgradeKit.T_MERGE_KIND);
		if (k <= 0 || k > MergeKind.values().length) {
			return null;
		}
		MergeKind kind = MergeKind.values()[(int) k - 1];
		return kind.stored() ? kind : null;
	}

	@Override
	public boolean customBody() {
		return true;
	}

	@Override
	public boolean showBadge(AbstractClientPlayer p) {
		return false;
	}

	/** First person: no hands (he is a machine). */
	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return true;
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		MergeKind kind = kindOf(p);
		if (kind == null) {
			return;
		}
		OmnitrixState st = Omnitrix.state(p);
		long variant = st.readyAt(UpgradeKit.ID + "." + UpgradeKit.T_MERGE_VARIANT);
		float time = p.level().getGameTime() + partial;
		float since = time - st.readyAt(UpgradeKit.ID + "." + UpgradeKit.T_MERGE_AT);
		// it assembles out of him: scaled up over 7 ticks with a little overshoot
		float k = Mth.clamp(since / 7f, 0f, 1f);
		float grow = since > 40 ? 1f : k < 1f ? k * k * (3f - 2f * k) * 1.08f : 1f + 0.08f * Math.max(0f, 1f - (since - 7f) / 4f);
		Pose ps = new Pose(p, partial, time, st, kind, variant);
		pose.pushPose();
		float body = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		pose.mulPose(Axis.YP.rotationDegrees(180f - body));
		pose.scale(grow, grow, grow);
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		Draw base = new Draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MACHINE)), light, false);
		model(base, ps);
		Draw glow = new Draw(pose, buffers.getBuffer(RenderType.eyes(MACHINE_GLOW)), LightTexture.FULL_BRIGHT, true);
		model(glow, ps);
		pose.popPose();
	}

	/** Everything a model needs about the frame. */
	record Pose(AbstractClientPlayer p, float partial, float time, OmnitrixState st, MergeKind kind, long variant) {
		float walk() {
			return p.walkAnimation.position(partial) * 0.6662f;
		}

		float stride() {
			return Math.min(1f, p.walkAnimation.speed(partial));
		}

		/** Leg swing (radians) for a leg in phase {@code phase} (0 or PI). */
		float leg(float phase) {
			return Mth.cos(walk() + phase) * 0.9f * stride();
		}

		float headYaw() {
			return Mth.wrapDegrees(Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot) - Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot)) * Mth.DEG_TO_RAD;
		}

		float pitch() {
			return Mth.lerp(partial, p.xRotO, p.getXRot()) * Mth.DEG_TO_RAD;
		}

		/** 0..1..0 over R's kick (ANIM_MACHINE), 0 when not firing. */
		float kick() {
			if (st.animId != UpgradeKit.ANIM_MACHINE) {
				return 0f;
			}
			float t = time - st.animStart;
			return t < 0 || t > 12 ? 0f : t < 3 ? t / 3f : 1f - (t - 3f) / 9f;
		}

		@Nullable
		BlockState block() {
			return kind.block() && variant > 0 ? Block.stateById((int) variant - 1) : null;
		}

		@Nullable
		EntityType<?> vehicle() {
			return !kind.block() && variant > 0 ? BuiltInRegistries.ENTITY_TYPE.byId((int) variant - 1) : null;
		}

		float bob() {
			return Mth.sin(time * 0.15f) * 0.8f;
		}
	}

	/** One pass (the base, or the glow copy that only draws the tiles with circuits / lenses). */
	static final class Draw {
		final PoseStack pose;
		final VertexConsumer vc;
		final int light;
		final boolean glow;

		Draw(PoseStack pose, VertexConsumer vc, int light, boolean glow) {
			this.pose = pose;
			this.vc = vc;
			this.light = light;
			this.glow = glow;
		}

		void box(float x0, float y0, float z0, float x1, float y1, float z1, int tile) {
			if (glow && !LIT[tile]) {
				return;
			}
			float u0 = (tile % 4) * 0.25f + 0.5f / 64f, v0 = (tile / 4) * 0.25f + 0.5f / 64f;
			AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, u0, v0, u0 + 0.25f - 1f / 64f, v0 + 0.25f - 1f / 64f, 0xFFFFFFFF, light);
		}

		/** {@code draw} rotated about the pivot (pixels) by x then y radians. */
		void at(float px, float py, float pz, float xRot, float yRot, Runnable draw) {
			pose.pushPose();
			pose.translate(px / 16f, py / 16f, pz / 16f);
			if (yRot != 0f) {
				pose.mulPose(Axis.YP.rotation(yRot));
			}
			if (xRot != 0f) {
				pose.mulPose(Axis.XP.rotation(xRot));
			}
			pose.translate(-px / 16f, -py / 16f, -pz / 16f);
			draw.run();
			pose.popPose();
		}

		void lift(float dy, Runnable draw) {
			pose.pushPose();
			pose.translate(0f, dy / 16f, 0f);
			draw.run();
			pose.popPose();
		}
	}

	static void model(Draw d, Pose ps) {
		switch (ps.kind()) {
			case DISPENSER -> turret(d, ps);
			case FURNACE -> furnace(d, ps);
			case PISTON -> piston(d, ps);
			case NOTE_BLOCK -> speaker(d, ps);
			case JUKEBOX -> boombox(d, ps);
			case OBSERVER -> watcher(d, ps);
			case HOPPER -> vacuum(d, ps);
			case MINECART -> cart(d, ps);
			case BOAT -> boat(d, ps);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- legs

	/** A two-part leg hanging from a hip at (x, hip, z), swung by {@code swing}; {@code w} its half width. */
	static void leg(Draw d, float x, float hip, float z, float swing, float w, int thigh, int shin) {
		float knee = hip + (24f - hip) * 0.5f;
		d.at(x, hip, z, swing, 0f, () -> {
			d.box(x - w * 0.8f, hip, z - w * 0.8f, x + w * 0.8f, knee, z + w * 0.8f, thigh);
			d.box(x - w, knee, z - w, x + w, 23f, z + w, shin);
			d.box(x - w - 0.5f, 23f, z - w - 1.2f, x + w + 0.5f, 24f, z + w + 0.4f, JOINT);
			d.box(x - w - 0.1f, knee - 0.6f, z - w - 0.1f, x + w + 0.1f, knee + 0.6f, z + w + 0.1f, BAND);
		});
	}

	// ---------------------------------------------------------------- the machines

	/** Dispenser: a turret on four legs, twin barrels following the look (a dropper: one wide launcher). */
	static void turret(Draw d, Pose ps) {
		float a = ps.leg(0f), b = ps.leg(Mth.PI);
		leg(d, -5f, 14f, -4f, a, 1.3f, JOINT, WHITE);
		leg(d, 5f, 14f, 4f, a, 1.3f, JOINT, WHITE);
		leg(d, 5f, 14f, -4f, b, 1.3f, JOINT, WHITE);
		leg(d, -5f, 14f, 4f, b, 1.3f, JOINT, WHITE);
		float bob = Mth.sin(ps.walk() * 2f) * 0.4f * ps.stride();
		d.lift(bob, () -> {
			d.box(-6f, 5f, -6f, 6f, 14f, 6f, WHITE);
			d.box(-6.4f, 6.5f, -4f, -6f, 12.5f, 4f, CIRCUIT);
			d.box(6f, 6.5f, -4f, 6.4f, 12.5f, 4f, CIRCUIT);
			d.box(-4.5f, 6.5f, 6f, 4.5f, 12.5f, 6.4f, CIRCUIT2);
			d.box(-6.2f, 13f, -6.2f, 6.2f, 14.4f, 6.2f, BAND);
			boolean dropper = ps.block() != null && ps.block().is(Blocks.DROPPER);
			float recoil = ps.kick() * 2f;
			d.at(0f, 6f, 0f, ps.pitch(), ps.headYaw(), () -> {
				d.box(-5f, 1f, -5f, 5f, 5f, 5f, WHITE);
				d.box(-4f, 0.6f, -4f, 4f, 1f, 4f, CIRCUIT2);
				d.box(-1.6f, 1.6f, -5.4f, 1.6f, 3.6f, -5f, LENS);
				if (dropper) {
					d.box(-3f, 1.5f, -12f + recoil, 3f, 5f, -5f, CIRCUIT);
					d.box(-3.4f, 1.1f, -13f + recoil, 3.4f, 5.4f, -12f + recoil, GRILLE);
				} else {
					d.box(-4.2f, 2f, -14f + recoil, -1.2f, 5f, -5f, CIRCUIT);
					d.box(1.2f, 2f, -14f + recoil, 4.2f, 5f, -5f, CIRCUIT);
					d.box(-4f, 2.2f, -14.4f + recoil, -1.4f, 4.8f, -14f + recoil, LENS);
					d.box(1.4f, 2.2f, -14.4f + recoil, 4f, 4.8f, -14f + recoil, LENS);
					d.box(-4.6f, 1.6f, -8f, 4.6f, 5.4f, -6f, JOINT);
				}
			});
		});
	}

	/** Furnace: a heavy plated walker -- a glowing grille for a mouth, a visor, stacks on its back (blast furnace: armour; smoker: a chimney). */
	static void furnace(Draw d, Pose ps) {
		float a = ps.leg(0f) * 0.7f, b = ps.leg(Mth.PI) * 0.7f;
		leg(d, -4.5f, 13f, 0f, a, 2.4f, WHITE, CIRCUIT);
		leg(d, 4.5f, 13f, 0f, b, 2.4f, WHITE, CIRCUIT);
		BlockState s = ps.block();
		boolean blast = s != null && s.is(Blocks.BLAST_FURNACE), smoker = s != null && s.is(Blocks.SMOKER);
		float bob = Math.abs(Mth.sin(ps.walk())) * 0.8f * ps.stride();
		float kick = ps.kick();
		d.lift(bob, () -> {
			d.box(-8f, -2f, -7f, 8f, 13f, 7f, WHITE);
			d.box(-7f, 0f, -7.4f, 7f, 11.5f, -7f, CIRCUIT);
			d.box(-4f, 5f - kick, -7.9f, 4f, 10.5f + kick, -7.4f, GRILLE);
			d.box(-5f, 0.6f, -7.9f, 5f, 3.2f, -7.4f, SCREEN);
			d.box(-8.6f, 1f, -5f, -8f, 11f, 5f, BAND);
			d.box(8f, 1f, -5f, 8.6f, 11f, 5f, BAND);
			d.box(-7f, 0f, 7f, 7f, 11f, 7.4f, CIRCUIT2);
			d.box(-6f, -6f, 3f, -3f, -2f, 6f, JOINT);
			d.box(3f, -6f, 3f, 6f, -2f, 6f, JOINT);
			d.box(-5.6f, -6.6f, 3.4f, -3.4f, -6f, 5.6f, LENS);
			d.box(3.4f, -6.6f, 3.4f, 5.6f, -6f, 5.6f, LENS);
			if (smoker) {
				d.box(-1.6f, -10f, 1.5f, 1.6f, -2f, 4.5f, CIRCUIT);
				d.box(-2f, -10.6f, 1.1f, 2f, -10f, 4.9f, JOINT);
			}
			if (blast) {
				d.box(-8.6f, 11f, -8f, 8.6f, 13.6f, -6.6f, WHITE);
				d.box(-8.6f, -2.6f, -8f, 8.6f, -1f, -6.6f, WHITE);
				d.box(-3f, -2.4f, -8.3f, 3f, -1.2f, -8f, LENS);
			}
			// arms: stubby cannons, swinging against the stride
			float arm = -ps.leg(0f) * 0.5f;
			d.at(-9f, 2f, 0f, arm - kick * 1.2f, 0f, () -> {
				d.box(-11f, 0f, -2.5f, -8f, 4f, 2.5f, JOINT);
				d.box(-11.5f, 4f, -2.2f, -8f, 11f, 2.2f, WHITE);
				d.box(-11.2f, 11f, -1.8f, -8.3f, 12.2f, 1.8f, LENS);
			});
			d.at(9f, 2f, 0f, -arm - kick * 1.2f, 0f, () -> {
				d.box(8f, 0f, -2.5f, 11f, 4f, 2.5f, JOINT);
				d.box(8f, 4f, -2.2f, 11.5f, 11f, 2.2f, WHITE);
				d.box(8.3f, 11f, -1.8f, 11.2f, 12.2f, 1.8f, LENS);
			});
		});
	}

	/** Piston: a pile driver -- the piston head thrown out ahead on R (sticky: a green head). */
	static void piston(Draw d, Pose ps) {
		leg(d, -3.5f, 14f, 0f, ps.leg(0f), 1.6f, JOINT, WHITE);
		leg(d, 3.5f, 14f, 0f, ps.leg(Mth.PI), 1.6f, JOINT, WHITE);
		boolean sticky = ps.block() != null && ps.block().is(Blocks.STICKY_PISTON);
		float ext = ps.kick() * 9f;
		d.box(-6f, 2f, -4f, 6f, 14f, 7f, WHITE);
		d.box(-6.4f, 4f, -2f, -6f, 12f, 5f, CIRCUIT);
		d.box(6f, 4f, -2f, 6.4f, 12f, 5f, CIRCUIT);
		d.box(-4.5f, 4f, 7f, 4.5f, 12f, 7.4f, CIRCUIT2);
		d.box(-3f, -2f, -1f, 3f, 2f, 4f, JOINT);
		d.box(-2.5f, -1.2f, -1.4f, 2.5f, 1f, -1f, SCREEN);
		// the rod and the head
		d.box(-1.5f, 6f, -5f - ext, 1.5f, 10f, -4f, CIRCUIT);
		d.box(-6.5f, 1.5f, -7f - ext, 6.5f, 14.5f, -5f - ext, WHITE);
		d.box(-5.5f, 2.5f, -7.4f - ext, 5.5f, 13.5f, -7f - ext, sticky ? LENS : CIRCUIT2);
	}

	/** Note block: a speaker bot -- two cones and a woofer that thump with every note (and the blast). */
	static void speaker(Draw d, Pose ps) {
		leg(d, -3.5f, 14f, 0f, ps.leg(0f), 1.5f, JOINT, WHITE);
		leg(d, 3.5f, 14f, 0f, ps.leg(Mth.PI), 1.5f, JOINT, WHITE);
		float thump = 0.4f * Math.max(0f, Mth.sin(ps.time() * 0.5f)) + ps.kick() * 1.5f;
		d.box(-6f, 1f, -6f, 6f, 14f, 6f, WHITE);
		d.box(-6f, 1f, -6.4f, 6f, 14f, -6f, CIRCUIT);
		d.box(-5f - thump * 0.3f, 2.5f, -7f - thump, -0.6f, 7f, -6.4f, LENS);
		d.box(0.6f, 2.5f, -7f - thump, 5f + thump * 0.3f, 7f, -6.4f, LENS);
		d.box(-3.2f - thump * 0.5f, 7.8f - thump * 0.5f, -7.2f - thump, 3.2f + thump * 0.5f, 13.4f + thump * 0.5f, -6.4f, LENS);
		d.box(-6.4f, 3f, -4f, -6f, 12f, 4f, CIRCUIT2);
		d.box(6f, 3f, -4f, 6.4f, 12f, 4f, CIRCUIT2);
		d.box(-0.5f, -4f, -0.5f, 0.5f, 1f, 0.5f, JOINT);
		d.box(-1f, -5.2f, -1f, 1f, -3.8f, 1f, LENS);
	}

	/** Jukebox: a walking boombox -- handle, two big speakers, a disc slot on top, beat-pulsing. */
	static void boombox(Draw d, Pose ps) {
		leg(d, -5f, 15f, 0f, ps.leg(0f), 1.4f, JOINT, WHITE);
		leg(d, 5f, 15f, 0f, ps.leg(Mth.PI), 1.4f, JOINT, WHITE);
		float beat = 0.5f * Math.max(0f, Mth.sin(ps.time() * 0.42f)) + ps.kick() * 1.8f;
		d.box(-9f, 4f, -5f, 9f, 15f, 5f, WHITE);
		d.box(-9f, 4f, -5.4f, 9f, 15f, -5f, CIRCUIT);
		d.box(-8f - beat * 0.3f, 5.5f - beat * 0.3f, -6f - beat, -2.4f + beat * 0.3f, 13.5f + beat * 0.3f, -5.4f, LENS);
		d.box(2.4f - beat * 0.3f, 5.5f - beat * 0.3f, -6f - beat, 8f + beat * 0.3f, 13.5f + beat * 0.3f, -5.4f, LENS);
		d.box(-1.8f, 6f, -5.7f, 1.8f, 12.5f, -5.4f, SCREEN);
		d.box(-5f, 3.6f, -3f, 5f, 4f, 3f, CIRCUIT2);
		d.box(-7f, 0f, -1f, -6f, 4f, 1f, JOINT);
		d.box(6f, 0f, -1f, 7f, 4f, 1f, JOINT);
		d.box(-7f, -0.6f, -1f, 7f, 0.6f, 1f, WHITE);
		d.box(-9f, 4f, 5f, 9f, 15f, 5.4f, BAND);
	}

	/** Observer: a one-eyed watcher on tall legs; the head (and its big green eye) follows his look. */
	static void watcher(Draw d, Pose ps) {
		leg(d, -3f, 12f, 0f, ps.leg(0f), 1.3f, JOINT, WHITE);
		leg(d, 3f, 12f, 0f, ps.leg(Mth.PI), 1.3f, JOINT, WHITE);
		d.box(-3.5f, 9f, -2.5f, 3.5f, 13f, 2.5f, JOINT);
		float blink = (ps.time() % 80f) < 3f ? 0.3f : 1f;
		float scan = ps.kick();
		d.at(0f, 6f, 0f, ps.pitch(), ps.headYaw(), () -> {
			d.box(-6f, 0f, -6f, 6f, 10f, 6f, WHITE);
			d.box(-6f, 0f, -6.4f, 6f, 10f, -6f, CIRCUIT);
			float r = 3.6f + scan * 0.8f;
			d.box(-r, 5f - r * blink, -7.2f, r, 5f + r * blink, -6.4f, LENS);
			d.box(-6.4f, 1f, -4f, -6f, 9f, 4f, CIRCUIT2);
			d.box(6f, 1f, -4f, 6.4f, 9f, 4f, CIRCUIT2);
			d.box(-4f, 1f, 6f, 4f, 9f, 6.4f, BAND);
			d.box(-1f, 4f, 6.4f, 1f, 6f, 6.8f, LENS);
			d.box(-6.2f, -0.4f, -6.2f, 6.2f, 0.6f, 6.2f, BAND);
		});
	}

	/** Hopper: a hovering vacuum -- the funnel upside-right, its intake glowing, a green nozzle-jet underneath. */
	static void vacuum(Draw d, Pose ps) {
		float suck = ps.kick();
		d.lift(ps.bob() - 2f, () -> {
			d.box(-7f, 2f, -7f, 7f, 7f, 7f, WHITE);
			d.box(-6f, 1.6f, -6f, 6f, 2f, 6f, CIRCUIT2);
			d.box(-7.4f, 3f, -5f, -7f, 6f, 5f, CIRCUIT);
			d.box(7f, 3f, -5f, 7.4f, 6f, 5f, CIRCUIT);
			d.box(-5f, 3f, -7.4f, 5f, 6f, -7f, CIRCUIT);
			d.box(-5f, 3f, 7f, 5f, 6f, 7.4f, CIRCUIT);
			d.box(-5f, 7f, -5f, 5f, 12f, 5f, CIRCUIT);
			d.box(-5.4f, 8.5f, -5.4f, 5.4f, 9.5f, 5.4f, BAND);
			d.box(-2.5f, 12f, -2.5f, 2.5f, 16f, 2.5f, JOINT);
			float n = 1.5f + suck;
			d.box(-n, 16f, -n, n, 17.5f, n, LENS);
			d.box(-3f - suck, 0.6f - suck, -3f - suck, 3f + suck, 1.6f, 3f + suck, LENS);
		});
	}

	/** Minecart: a hover-cart -- the tub plated and wired, four green hover pads, a headlight; its cargo rides in it. */
	static void cart(Draw d, Pose ps) {
		EntityType<?> t = ps.vehicle();
		d.lift(ps.bob() - 2f, () -> {
			d.box(-7f, 14f, -10f, 7f, 16f, 10f, WHITE);
			d.box(-7f, 7f, -10f, -6f, 14f, 10f, CIRCUIT);
			d.box(6f, 7f, -10f, 7f, 14f, 10f, CIRCUIT);
			d.box(-6f, 7f, -10f, 6f, 14f, -9f, BAND);
			d.box(-6f, 7f, 9f, 6f, 14f, 10f, BAND);
			d.box(-7.4f, 6.4f, -10.4f, 7.4f, 7f, 10.4f, WHITE);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					d.box(sx * 6f - 1.8f, 16f, sz * 7f - 1.8f, sx * 6f + 1.8f, 17f, sz * 7f + 1.8f, LENS);
				}
			}
			d.box(-2f, 9f, -10.6f, 2f, 12f, -10f, LENS);
			if (t == EntityType.CHEST_MINECART) {
				d.box(-5f, 4f, -5f, 5f, 14f, 5f, WHITE);
				d.box(-5.2f, 6f, -5.2f, 5.2f, 7.5f, 5.2f, BAND);
				d.box(-1f, 6.5f, -5.6f, 1f, 9f, -5.2f, LENS);
			} else if (t == EntityType.FURNACE_MINECART) {
				d.box(-5f, 4f, -5f, 5f, 14f, 5f, WHITE);
				d.box(-3.5f, 7f, -5.5f, 3.5f, 12f, -5f, GRILLE);
			} else if (t == EntityType.HOPPER_MINECART) {
				d.box(-5.5f, 4f, -5.5f, 5.5f, 8f, 5.5f, WHITE);
				d.box(-4f, 8f, -4f, 4f, 13f, 4f, CIRCUIT);
			} else if (t == EntityType.TNT_MINECART) {
				d.box(-5f, 4f, -5f, 5f, 14f, 5f, BAND);
			} else if (t != null && t != EntityType.MINECART) {
				d.box(-5f, 4f, -5f, 5f, 14f, 5f, CIRCUIT2);
			}
		});
	}

	/** Boat: a hover-boat -- a pointed plated hull, wired sides, twin jets at the stern, hover pads beneath. */
	static void boat(Draw d, Pose ps) {
		EntityType<?> t = ps.vehicle();
		float jet = 0.5f + 0.5f * Mth.sin(ps.time() * 0.9f) + ps.kick();
		d.lift(ps.bob() - 2f, () -> {
			d.box(-6f, 14f, -12f, 6f, 16f, 12f, WHITE);
			d.box(-7f, 9f, -12f, -6f, 15f, 11f, CIRCUIT);
			d.box(6f, 9f, -12f, 7f, 15f, 11f, CIRCUIT);
			d.box(-4.5f, 9f, -16f, 4.5f, 15.5f, -12f, WHITE);
			d.box(-2f, 10f, -18.5f, 2f, 15.5f, -16f, WHITE);
			d.box(-4.9f, 11f, -16.4f, 4.9f, 12.5f, -12f, BAND);
			d.box(-6f, 9f, 11f, 6f, 15f, 13f, BAND);
			d.box(-5f, 10.5f, 13f, -2f, 13.5f, 14.5f + jet, LENS);
			d.box(2f, 10.5f, 13f, 5f, 13.5f, 14.5f + jet, LENS);
			d.box(-1.5f, 9.5f, -18.9f, 1.5f, 11.5f, -18.5f, LENS);
			for (int sz = -1; sz <= 1; sz++) {
				d.box(-4f, 16f, sz * 7f - 1.5f, 4f, 17f, sz * 7f + 1.5f, LENS);
			}
			if (t == EntityType.CHEST_BOAT) {
				d.box(-4.5f, 5f, 2f, 4.5f, 14f, 10f, WHITE);
				d.box(-4.7f, 7f, 1.8f, 4.7f, 8.5f, 10.2f, BAND);
			}
		});
	}
}
