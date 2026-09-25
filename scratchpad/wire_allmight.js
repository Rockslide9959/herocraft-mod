// Wires the All Might power (v0.12.33) into the existing systems. One-off; idempotence is not attempted.
const fs = require('fs');
function edit(f, pairs) {
	let s = fs.readFileSync(f, 'utf8');
	const crlf = s.includes('\r\n');
	if (crlf) s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!s.includes(a)) { console.error('MISSING in', f, ':', a.slice(0, 100)); process.exit(1); }
		s = s.replace(a, () => b);
	}
	if (crlf) s = s.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, s);
}
const M = 'src/main/java/com/projecthero/mod/';
const C = 'src/client/java/com/projecthero/mod/client/';

edit(M + 'attachment/ModAttachments.java', [
[`	/**
	 * The Spider-Man Symbiote upgrade: {@code hasSymbiote}`, `	/**
	 * The whole All Might / One For All power for one player (v0.12.33): the power, the chosen form, OFA Power, Full Cowl
	 * and transformation clocks, ability cooldowns and the current pose animation. Persistent + {@code copyOnDeath()}; synced
	 * to every client (other players render the form and the poses from it); the server stays authoritative.
	 */
	public static final AttachmentType<com.projecthero.mod.allmight.data.AllMightState> ALL_MIGHT_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("all_might_state"),
					builder -> builder.persistent(com.projecthero.mod.allmight.data.AllMightState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.allmight.data.AllMightState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.allmight.data.AllMightState.CODEC),
									AttachmentSyncPredicate.all()));

	/**
	 * The Spider-Man Symbiote upgrade: {@code hasSymbiote}`],
]);

edit(M + 'hero/HeroTiers.java', [
[`				|| TitanShifter.isShifter(player);
	}`, `				|| TitanShifter.isShifter(player)
				|| com.projecthero.mod.allmight.AllMight.hasPower(player);
	}`],
[`"green_lantern", "wolverine", "titan_shifter");`, `"green_lantern", "wolverine", "titan_shifter", "all_might");`],
[`			case "titan_shifter" -> TitanShifter.isShifter(player);
			default -> false;`, `			case "titan_shifter" -> TitanShifter.isShifter(player);
			case "all_might" -> com.projecthero.mod.allmight.AllMight.hasPower(player);
			default -> false;`],
[`			case "titan_shifter" -> {
				if (TitanShifter.isShifter(player)) {
					TitanShifter.revoke(player);
				}
			}`, `			case "titan_shifter" -> {
				if (TitanShifter.isShifter(player)) {
					TitanShifter.revoke(player);
				}
			}
			case "all_might" -> {
				if (com.projecthero.mod.allmight.AllMight.hasPower(player)) {
					com.projecthero.mod.allmight.AllMight.revoke(player);
				}
			}`],
[`		if (!excludeHeroKeys.contains("titan_shifter") && TitanShifter.isShifter(player)) {
			return true;
		}`, `		if (!excludeHeroKeys.contains("titan_shifter") && TitanShifter.isShifter(player)) {
			return true;
		}
		if (!excludeHeroKeys.contains("all_might") && com.projecthero.mod.allmight.AllMight.hasPower(player)) {
			return true;
		}`],
]);

edit(M + 'hero/AbilityRouter.java', [
[`		// A Normal Symbiote host (bonded, suit active`, `		// All Might (v0.12.33) takes the slots on the same terms: has the power and has not selected a mutation.
		if (com.projecthero.mod.allmight.AllMightAbilityManager.hasContext(player)) {
			com.projecthero.mod.allmight.AllMightAbilityManager.handle(player, slot, pressed);
			return;
		}

		// A Normal Symbiote host (bonded, suit active`],
[`		com.projecthero.mod.titanshifter.TitanShifterAbilityManager.serverTick(player);
`, `		com.projecthero.mod.titanshifter.TitanShifterAbilityManager.serverTick(player);
		com.projecthero.mod.allmight.AllMightAbilityManager.serverTick(player);
`],
]);

edit(M + 'command/HeroCommand.java', [
[`"wolverine", "titan_shifter", "symbiote");`, `"wolverine", "titan_shifter", "all_might", "symbiote");`],
[`			case "titan_shifter" -> {
				return com.projecthero.mod.command.TitanShifterCommand.grant(c, target);
			}`, `			case "titan_shifter" -> {
				return com.projecthero.mod.command.TitanShifterCommand.grant(c, target);
			}
			case "all_might" -> {
				return com.projecthero.mod.command.AllMightCommand.grant(c, target);
			}`],
[`			case "titan_shifter" -> com.projecthero.mod.titanshifter.TitanShifter.revoke(target);`, `			case "titan_shifter" -> com.projecthero.mod.titanshifter.TitanShifter.revoke(target);
			case "all_might" -> com.projecthero.mod.allmight.AllMight.revoke(target);`],
]);
// both "Unknown Hero-Tier power" messages
{
	const f = M + 'command/HeroCommand.java';
	let s = fs.readFileSync(f, 'utf8');
	s = s.split('wolverine, titan_shifter)').join('wolverine, titan_shifter, all_might)');
	fs.writeFileSync(f, s);
}

edit(M + 'command/ProjectHeroCommand.java', [
[`		root.then(TitanShifterCommand.build());`, `		root.then(TitanShifterCommand.build());
		root.then(AllMightCommand.build());`],
]);

edit(M + 'network/ModNetworking.java', [
[`		PayloadTypeRegistry.playC2S().register(TitanShiftPayload.TYPE, TitanShiftPayload.CODEC);`, `		PayloadTypeRegistry.playC2S().register(TitanShiftPayload.TYPE, TitanShiftPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AllMightActionPayload.TYPE, AllMightActionPayload.CODEC);`],
[`		// Wolverine: the H-key claw toggle.`, `		// All Might: H (transform / change back) and N (All Might Leap). Server re-validates power, cooldown, OFA and state.
		ServerPlayNetworking.registerGlobalReceiver(AllMightActionPayload.TYPE, (payload, context) -> {
			net.minecraft.server.level.ServerPlayer p = context.player();
			if (!com.projecthero.mod.allmight.AllMight.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_FORM -> com.projecthero.mod.allmight.AllMight.toggleForm(p);
				case LEAP -> com.projecthero.mod.allmight.AllMightAbilities.leap(p);
			}
		});

		// Wolverine: the H-key claw toggle.`],
]);

edit(M + 'ProjectHeroMod.java', [
[`		com.projecthero.mod.thorarmor.ThorArmorItems.initialize();`, `		com.projecthero.mod.thorarmor.ThorArmorItems.initialize();
		com.projecthero.mod.allmight.item.AllMightItems.initialize();`],
[`		com.projecthero.mod.titanshifter.TitanShifterDamage.initialize();`, `		com.projecthero.mod.titanshifter.TitanShifterDamage.initialize();
		com.projecthero.mod.allmight.AllMightDamage.initialize();`],
[`				// Thor: the conjured armour dies with its wearer`, `				// All Might: queued smashes end and the conjured costume never drops (the power itself is kept).
				com.projecthero.mod.allmight.AllMight.onDeath(sp);
				// Thor: the conjured armour dies with its wearer`],
[`			com.projecthero.mod.titanshifter.TitanShifter.onPlayerJoin(player);
		});`, `			com.projecthero.mod.titanshifter.TitanShifter.onPlayerJoin(player);
			com.projecthero.mod.allmight.AllMight.onPlayerJoin(player);
		});`],
[`			com.projecthero.mod.titanshifter.TitanShifter.onPlayerRespawn(newPlayer);
		});`, `			com.projecthero.mod.titanshifter.TitanShifter.onPlayerRespawn(newPlayer);
			com.projecthero.mod.allmight.AllMight.onPlayerRespawn(newPlayer);
		});`],
[`					com.projecthero.mod.titanshifter.TitanShifter.forceEnd(player, true);
				});`, `					com.projecthero.mod.titanshifter.TitanShifter.forceEnd(player, true);
					com.projecthero.mod.allmight.AllMight.clearTransient(player);
				});`],
[`			com.projecthero.mod.titanshifter.TitanShifter.clearTransient(handler.getPlayer());
		});`, `			com.projecthero.mod.titanshifter.TitanShifter.clearTransient(handler.getPlayer());
			com.projecthero.mod.allmight.AllMight.clearTransient(handler.getPlayer());
		});`],
]);

edit(M + 'diagnostics/ServerStateReset.java', [
[`		com.projecthero.mod.titanshifter.TitanShifterAbilityManager.clearSessionState();`, `		com.projecthero.mod.titanshifter.TitanShifterAbilityManager.clearSessionState();
		com.projecthero.mod.allmight.AllMightAbilityManager.clearSessionState();`],
]);

edit(M + 'item/ModCreativeTab.java', [
[`				// Thor: the H-conjured armour`, `				// All Might: the Vestige of One For All and the costume pieces.
				com.projecthero.mod.allmight.item.AllMightItems.addToCreativeTab(output);
				// Thor: the H-conjured armour`],
]);

edit(M + 'squad/HeroIdentity.java', [
[`		if (com.projecthero.mod.titanshifter.TitanShifter.isShifter(player)) {
			return "projecthero.squad.identity.titan_shifter";
		}`, `		if (com.projecthero.mod.titanshifter.TitanShifter.isShifter(player)) {
			return "projecthero.squad.identity.titan_shifter";
		}
		if (com.projecthero.mod.allmight.AllMightAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.all_might";
		}`],
]);

edit(C + 'gui/PowerInfoScreen.java', [
[`		if (com.projecthero.mod.titanshifter.TitanShifter.isShifter(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.titanShifterChapter();
		}`, `		if (com.projecthero.mod.titanshifter.TitanShifter.isShifter(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.titanShifterChapter();
		}
		if (com.projecthero.mod.allmight.AllMight.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.allMightChapter();
		}`],
]);

edit(M + 'hero/guide/HeroPackGuide.java', [
[`	public static Chapter titanShifterChapter() {
		return chapters().get(CH_TITAN_SHIFTER);
	}`, `	public static Chapter titanShifterChapter() {
		return chapters().get(CH_TITAN_SHIFTER);
	}

	public static Chapter allMightChapter() {
		return chapters().get(CH_ALL_MIGHT);
	}`],
[`	private static final int CHAPTER_POWER_BASE = 18;`, `	private static final int CH_ALL_MIGHT = 18;
	private static final int CHAPTER_POWER_BASE = 19;`],
[`		link(idx, "projecthero.guide.titan_shifter", CH_TITAN_SHIFTER);`, `		link(idx, "projecthero.guide.titan_shifter", CH_TITAN_SHIFTER);
		link(idx, "projecthero.guide.all_might", CH_ALL_MIGHT);`],
[`		// one chapter per power, in registration order (CHAPTER_POWER_BASE + i)`, `		// All Might (v0.12.33) -- Hero Tier. Appended after the Titan Shifter so every earlier index stays put.
		out.add(chapter("projecthero.guide.all_might", lines -> {
			lines.add(Component.translatable("projecthero.guide.all_might.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.all_might.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.progression");
			para(lines, "projecthero.guide.all_might.step.vestige");
			blank(lines);
			head(lines, "projecthero.guide.all_might.ofa");
			para(lines, "projecthero.guide.all_might.ofa.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.forms");
			para(lines, "projecthero.guide.all_might.forms.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.controls");
			for (String[] row : new String[][] {
					{ "H", "transform" }, { "R", "detroit_smash" }, { "G", "texas_smash" }, { "Z", "carolina_smash" },
					{ "X", "new_hampshire_smash" }, { "C", "full_cowl" }, { "V", "united_states_of_smash" }, { "N", "all_might_leap" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.all_might.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.all_might.ability." + row[1]);
			}
			blank(lines);
			head(lines, "projecthero.guide.all_might.passives");
			para(lines, "projecthero.guide.all_might.passives.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.commands");
			para(lines, "projecthero.guide.all_might.commands.body");
		}));

		// one chapter per power, in registration order (CHAPTER_POWER_BASE + i)`],
]);

// ---- client ----
edit(C + 'ProjectHeroModClient.java', [
[`		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.TitanShifterHud::render);`, `		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.TitanShifterHud::render);
		HudRenderCallback.EVENT.register(com.projecthero.mod.client.gui.AllMightHud::render);`],
[`			} else if (client.player != null && wearingAnyIronMan(client.player)) {`, `			} else if (client.player != null && com.projecthero.mod.allmight.AllMight.hasPower(client.player) && !Screen.hasShiftDown()) {
				// v0.12.33: H as All Might transforms / changes back (Shift+H still opens the power wheel).
				ClientPlayNetworking.send(new com.projecthero.mod.network.AllMightActionPayload(
						com.projecthero.mod.network.AllMightActionPayload.Action.TOGGLE_FORM));
			} else if (client.player != null && wearingAnyIronMan(client.player)) {`],
[`							|| com.projecthero.mod.wolverine.Wolverine.hasPower(client.player))) {
				// v0.12.32`, `							|| com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)
							|| com.projecthero.mod.allmight.AllMight.hasPower(client.player))) {
				// v0.12.32`],
[`		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)) {`, `		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.allmight.AllMight.hasPower(client.player)) {
			// v0.12.33: Utility 2 (N) as All Might is the All Might Leap.
			ClientPlayNetworking.send(new com.projecthero.mod.network.AllMightActionPayload(
					com.projecthero.mod.network.AllMightActionPayload.Action.LEAP));
		} else if (down && !maxSteelTransformWasDown && client.player != null
				&& com.projecthero.mod.wolverine.Wolverine.hasPower(client.player)) {`],
]);

edit(C + 'mixin/HumanoidModelMixin.java', [
[`	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$flightPose(`, `	/** All Might's Smash / transformation poses (v0.12.33): driven by the synced power state, so every viewer and the armour shell agree. */
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$allMightPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			com.projecthero.mod.client.allmight.AllMightPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$flightPose(`],
]);
console.log('wired');
