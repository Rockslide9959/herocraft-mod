const fs = require('fs');
function edit(f, fn) { const s = fs.readFileSync(f, 'utf8'); const o = fn(s); if (o === s) throw new Error('nochange ' + f); fs.writeFileSync(f, o); }
const rep = (s, a, b) => { if (!s.includes(a)) throw new Error('missing ' + a.slice(0, 50)); return s.replace(a, b); };

edit('src/main/java/com/projecthero/mod/wolverine/Wolverine.java', s => {
  const a = s.indexOf('\t/** Bone Claw Serum: NONE -> BONE.');
  const b = s.indexOf('\t/** Adamantium Serum: BONE');
  s = s.slice(0, a) + s.slice(b);
  s = rep(s, 's.clawTier = ClawTier.NONE; // the claws come from the Bone Claw Serum', 's.clawTier = ClawTier.BONE; // v0.12.26: the Bone Claw Serum ascends Super Regeneration into bone-claw Wolverine');
  return s;
});
edit('src/main/java/com/projecthero/mod/wolverine/item/WolverineItems.java', s => {
  s = rep(s, '\t/** Ascends Super Regeneration into Wolverine (was the "Adamantium Serum" before v0.12.25). */\n\tpublic static Item WOLVERINE_SERUM;\n\t/** Wolverine only: unlocks the Bone Claws. */', '\t/** Ascends Super Regeneration into Wolverine with Bone Claws. */');
  s = rep(s, '\t\tWOLVERINE_SERUM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("wolverine_serum"),\n\t\t\t\tnew WolverineSerumItem(new Item.Properties().rarity(Rarity.EPIC)));\n', '');
  s = rep(s, '\t\toutput.accept(WOLVERINE_SERUM);\n', '');
  return s;
});
edit('src/main/java/com/projecthero/mod/wolverine/item/BoneClawSerumItem.java', s => {
  s = rep(s, `		if (!Wolverine.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.not_wolverine")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (Wolverine.clawTier(sp) != ClawTier.NONE) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.unlockBoneClaws(sp)) {
			return InteractionResultHolder.fail(held);
		}
		fx(sp);`, `		if (Wolverine.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.hasSuperRegeneration(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.bone_serum.no_regeneration")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Wolverine.ascendFromSuperRegeneration(sp)) {
			return InteractionResultHolder.fail(held);
		}
		fx(sp);`);
  s = rep(s, 'import com.projecthero.mod.wolverine.data.ClawTier;\n', '');
  s = rep(s, "Bone Claw Serum: unlocks a Wolverine's natural bone claws (NONE -> BONE). Only works for a Wolverine\n * who has no claws yet; anything else consumes nothing.", 'Bone Claw Serum: ascends Super Regeneration into Wolverine with natural bone claws (stage 1 of the\n * progression). Needs Super Regeneration and no Wolverine power yet; anything else consumes nothing.');
  return s;
});
edit('src/gametest/java/com/projecthero/mod/gametest/WolverineGameTests.java', s => {
  s = rep(s, 'helper.assertTrue(Wolverine.clawTier(p) == none, "a fresh Wolverine has no claws");\n\t\thelper.assertFalse(Wolverine.setClaws(p, true), "no claws to deploy");\n\t\thelper.assertFalse(Wolverine.upgradeToAdamantium(p), "cannot skip the bone claws");\n\t\thelper.assertTrue(Wolverine.unlockBoneClaws(p), "bone serum unlocks bone claws");\n\t\thelper.assertFalse(Wolverine.unlockBoneClaws(p), "bone serum only works once");\n',
    'helper.assertTrue(Wolverine.clawTier(p) == com.projecthero.mod.wolverine.data.ClawTier.BONE, "ascension yields a bone-claw Wolverine");\n\t\thelper.assertFalse(none == Wolverine.clawTier(p), "not clawless");\n');
  return s;
});
edit('src/main/resources/assets/projecthero/lang/en_us.json', s => {
  const j = JSON.parse(s);
  for (const k of Object.keys(j)) if (k.includes('wolverine_serum')) delete j[k];
  delete j['message.projecthero.bone_serum.not_wolverine'];
  j['message.projecthero.bone_serum.no_regeneration'] = 'Your body has no regenerative mutation for the serum to build on.';
  j['message.projecthero.bone_serum.already'] = 'You are already Wolverine.';
  j['message.projecthero.bone_serum.success'] = 'Your healing factor tears itself apart and rebuilds harder. Bone bursts through your knuckles -- you are WOLVERINE, with BONE CLAWS. Press H to deploy them.';
  j['item.projecthero.bone_claw_serum.desc0'] = 'Wolverine Ascension - Stage 1';
  j['item.projecthero.bone_claw_serum.desc1'] = 'Ascends Super Regeneration into Wolverine with Bone Claws.';
  j['item.projecthero.bone_claw_serum.desc2'] = 'Required: Super Regeneration';
  j['projecthero.power.power_12_super_regeneration.desc'] = j['projecthero.power.power_12_super_regeneration.desc'].replace('a Wolverine Serum', 'a Bone Claw Serum');
  j['projecthero.guide.wolverine.step.serum'] = '2. Craft a Bone Claw Serum (4 bone blocks, netherite scrap, 2 gold ingots, a glass bottle, a ghast tear) and use it. It consumes Super Regeneration, replaces your mutations like any Primary power, and makes you a BONE CLAW Wolverine. Admins: /wolverine power grant.';
  j['projecthero.guide.wolverine.claws.body'] = j['projecthero.guide.wolverine.claws.body']
    .replace('You do not start with claws. 3. Craft a Bone Claw Serum (4 bone blocks, netherite scrap, 2 gold ingots, a glass bottle, a ghast tear) and use it: six natural BONE CLAWS (three a hand, ivory and curved) grow through your knuckles. Bone claws', 'The Bone Claw Serum gives you six natural BONE CLAWS (three a hand, ivory and curved) that grow through your knuckles. Bone claws')
    .replace('4. Craft an Adamantium Serum', 'To reach the final stage, craft an Adamantium Serum')
    .replace('("Bone Claws required" until you have them; ', '(');
  j['projecthero.guide.wolverine.tier'] = 'Primary Power - Ascension of Super Regeneration (Bone Claws -> Adamantium)';
  return JSON.stringify(j, null, 2) + '\n';
});
edit('docs/WOLVERINE_REFERENCE.md', s => s + '\n## v0.12.26 - progression simplified\n\nSuper Regeneration -> **Bone Claw Serum** (consumes SR, makes a bone-claw Wolverine directly) -> **Adamantium Serum** (hold 3 s) -> adamantium Wolverine. The separate Wolverine Serum was removed; `Wolverine.ascendFromSuperRegeneration` now starts at `ClawTier.BONE`. `/wolverine power grant` also yields a bone-claw Wolverine.\n');
edit('docs/CURSEFORGE_DESCRIPTION.md', s => rep(s, '**Wolverine Serum** (4 diamonds, 2 netherite ingots, 2 blaze powder, a golden apple) and use it: your\nregenerative mutation is consumed and rebuilt as Wolverine — with no claws yet. Craft a **Bone Claw Serum** for\nthree natural bone claws a hand (weaker: 9 bare-handed), then an **Adamantium Serum**', '**Bone Claw Serum** (bone blocks, netherite scrap, gold, glass bottle, ghast tear) and use it: your\nregenerative mutation is consumed and rebuilt as a bone-claw Wolverine (three natural claws a hand, weaker: 9 bare-handed). Then an **Adamantium Serum**'));
edit('docs/curseforge_description.html', s => rep(s, 'craft a <strong>Wolverine Serum</strong> (4 diamonds, 2 netherite ingots, 2 blaze powder, a\ngolden apple) and use it: the mutation is consumed and rebuilt as Wolverine &mdash; with no claws yet. A\n<strong>Bone Claw Serum</strong> grows natural bone claws (weaker); a hold-to-use', 'craft a <strong>Bone Claw Serum</strong> and use it: the mutation is consumed and rebuilt as a bone-claw\nWolverine (weaker natural claws); a hold-to-use'));
