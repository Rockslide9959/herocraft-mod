package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1 Quirk framework: Explosion's bands and pacing ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.6). The HUD meter is
 * <b>Sweat</b> -- fuel, more is better: Dry 0-29 (x0.6 sputter), Primed 30-79 (x1.0), Soaked 80-100 (x1.4). Its build is
 * NOT paced (sweating is what powers him). The drawback is <b>Arm Strain</b>, so the pacing runs on that channel: every
 * blast's strain x{@link #BUILD}, its danger at 70 (the kit's smoking-arms warning), "recovered" at 30.
 *
 * <p>An attack every 2 s (v0.17.1 kit build: the eleven costed moves in rotation, the new Pierce / Crush Grip / Spin Dodge /
 * Uppercut included) reaches 70 strain after about 49 s (it was 12 s before the framework); the 7/s ease-off after 2 s
 * without a blast is unchanged (70 to 30 in about 7.7 s).
 */
public final class ExplosionMeter {
	public static final float BUILD = 0.28f;
	public static final float RECOVER = 1.0f;
	/** The doc's starter route (2.7): Stun Grenade, then AP Shot within 1.2 s = a crit (x1.5). */
	public static QuirkCombos.Route STUN_SHOT;
	/** v0.17.1: the Explosive Uppercut, then AP Shot within 1.5 s = the air follow-up (x1.3). */
	public static QuirkCombos.Route SKY_SHOT;

	/** Arm Strain as the pacing channel. */
	static final QuirkMeterSpec.Channel STRAIN = new QuirkMeterSpec.Channel() {
		@Override
		public float read(ServerPlayer p) {
			return ExplosionMoves.strain(p);
		}

		@Override
		public void add(ServerPlayer p, float rawDelta) {
			if (rawDelta >= 0f) {
				ExplosionMoves.strain(p, rawDelta);
			} else {
				ExplosionMoves.setStrain(p, ExplosionMoves.strain(p) + com.projecthero.mod.quirk.meter.QuirkMeters.pace(p, rawDelta));
			}
		}

		@Override
		public void set(ServerPlayer p, float value) {
			ExplosionMoves.setStrain(p, value);
		}
	};

	private ExplosionMeter() {
	}

	static void register() {
		STUN_SHOT = QuirkCombos.route(Quirk.EXPLOSION, "stun_shot", ExplosionConfig.STUN_GRENADE, ExplosionConfig.AP_SHOT)
				.window(24).bonus(1.5f).register();
		SKY_SHOT = QuirkCombos.route(Quirk.EXPLOSION, "sky_shot", ExplosionConfig.UPPERCUT, ExplosionConfig.AP_SHOT).window(30).bonus(1.3f).register();
		QuirkMeterSpec.builder(Quirk.EXPLOSION)
				.bands(30f, 80f)
				.names("dry", "primed", "soaked", "beyond")
				.damage(0.6f, 1.0f, 1.4f, 1.4f)
				.pacing(BUILD, RECOVER)
				.channel(STRAIN, QuirkMeterSpec.Direction.BUILDS)
				.danger(ExplosionConfig.STRAIN_WARN)
				.safe(30f)
				.heavyUse(h -> h
						.move(ExplosionConfig.AP_STRAIN, ExplosionConfig.AP_COOLDOWN)
						.move(ExplosionConfig.PUNCH_STRAIN, ExplosionConfig.PUNCH_COOLDOWN)
						.move(ExplosionConfig.DASH_STRAIN, ExplosionConfig.DASH_COOLDOWN)
						.move(ExplosionConfig.CLUSTER_STRAIN, ExplosionConfig.CLUSTER_COOLDOWN)
						.move(ExplosionConfig.AUTO_STRAIN * ExplosionConfig.AUTO_SHOTS, ExplosionConfig.AUTO_COOLDOWN)
						.move(ExplosionConfig.BLAST_RUSH_STRAIN, ExplosionConfig.BLAST_RUSH_COOLDOWN)
						.move(ExplosionConfig.STUN_STRAIN, ExplosionConfig.STUN_COOLDOWN)
						// v0.17.1 kit build: the new moves join the rotation
						.move(ExplosionConfig.PIERCE_STRAIN, ExplosionConfig.PIERCE_COOLDOWN)
						.move(ExplosionConfig.GRIP_STRAIN, ExplosionConfig.GRIP_COOLDOWN)
						.move(ExplosionConfig.DODGE_STRAIN, ExplosionConfig.DODGE_COOLDOWN)
						.move(ExplosionConfig.UPPERCUT_STRAIN, ExplosionConfig.UPPERCUT_COOLDOWN)
						.recovery(ExplosionConfig.STRAIN_DECAY * 20f, ExplosionConfig.STRAIN_DECAY_DELAY))
				.register();
	}
}
