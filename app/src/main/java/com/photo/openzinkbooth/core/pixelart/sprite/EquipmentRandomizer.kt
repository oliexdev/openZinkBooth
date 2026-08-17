/*
 * openZinkBooth
 * Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.photo.openzinkbooth.core.pixelart.sprite

import com.photo.openzinkbooth.core.pixelart.analyzer.CharacterFeatures
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceAttributeAnalyzer
import kotlin.random.Random

/**
 * EquipmentRandomizer — slot-based random equipment, pflat-style.
 *
 * All sprites use walk.png (576x256) for consistent pose alignment.
 * Weapons and shields have fg+bg layers for correct hand placement:
 *   Weapon fg (z=140) = right hand, weapon bg (z=9) = behind body
 *   Shield fg (z=110) = left hand,  shield bg (z=3) = behind body
 */
object EquipmentRandomizer {

    enum class EquipmentSet { WARRIOR, MAGE, ARCHER, ROGUE, PALADIN, NONE }

    data class Equipment(
        val set:          EquipmentSet = EquipmentSet.NONE,
        val torsoPath:    String?      = null,
        val legsPath:     String?      = null,
        val feetPath:     String?      = null,
        val weaponFgPath: String?      = null,  // z=140 right hand
        val weaponBgPath: String?      = null,  // z=9   behind body
        val shieldFgPath: String?      = null,  // z=110 left hand
        val shieldBgPath: String?      = null,  // z=3   behind body
        val helmetPath:   String?      = null,  // z=130
        val beltPath:     String?      = null,  // z=30 waist over torso
        val capeFgPath:   String?      = null,  // z=135 cape front
        val capeBgPath:   String?      = null,  // z=1   cape behind
        val quiverPath:   String?      = null,  // z=8   back quiver (archer)
        val shoulderPath: String?      = null,  // z=65  pauldrons/epaulets over torso
        val helmAccFgPath: String?     = null,  // z=139 helmet accessory front (visor/plume/crest/horns)
        val helmAccBgPath: String?     = null,  // z=95  helmet accessory behind (horns bg)
        val facialPath:   String?      = null,  // z=116 facial accessory (eyepatch/mask)
    )

    // ── Torso (walk.png) ──────────────────────────────────────────────────────

    private val TORSO_CASUAL = listOf(
        "torso/clothes/shortsleeve/tshirt/male/walk.png"      to "torso/clothes/shortsleeve/tshirt/female/walk.png",
        "torso/clothes/shortsleeve/shortsleeve/male/walk.png" to "torso/clothes/shortsleeve/shortsleeve/female/walk.png",
        "torso/clothes/longsleeve/longsleeve/male/walk.png"   to "torso/clothes/longsleeve/longsleeve/female/walk.png",
        "torso/clothes/sleeveless/sleeveless1/male/walk.png"  to "torso/clothes/sleeveless/sleeveless1/female/walk.png",
    )
    private val TORSO_ARMOUR = listOf(
        "torso/armour/plate/male/walk.png"   to "torso/armour/plate/female/walk.png",
        "torso/chainmail/male/walk.png"      to "torso/chainmail/female/walk.png",
    )
    // Leather armour — Archer + Rogue
    private val TORSO_LEATHER = listOf(
        "torso/armour/leather/male/walk.png" to "torso/armour/leather/female/walk.png",
    )
    // Robes — Mage
    private val TORSO_ROBE_FEMALE = listOf(
        "torso/clothes/robe/female/walk/purple.png",
        "torso/clothes/robe/female/walk/black.png",
        "torso/clothes/robe/female/walk/blue.png",
        "torso/clothes/robe/female/walk/forest_green.png",
        "torso/clothes/robe/female/walk/dark_gray.png",
        "torso/clothes/robe/female/walk/red.png",
        "torso/clothes/robe/female/walk/white.png",
        "torso/clothes/robe/female/walk/brown.png",
        "torso/clothes/robe/female/walk/light_gray.png",
    )
    private val TORSO_ROBE_MALE = listOf(
        "torso/clothes/longsleeve/longsleeve/male/walk.png",  // no male robe → longsleeve
    )
    // Vest — Rogue
    private val TORSO_VEST_MALE = listOf(
        "torso/clothes/vest/male/walk/brown.png",
        "torso/clothes/vest/male/walk/leather.png",
        "torso/clothes/vest/male/walk/black.png",
        "torso/clothes/vest/male/walk/blue.png",
        "torso/clothes/vest/male/walk/green.png",
        "torso/clothes/vest/male/walk/gray.png",
        "torso/clothes/vest/male/walk/gray_striped.png",
        "torso/clothes/vest/male/walk/green_striped.png",
        "torso/clothes/vest_open/male/walk/brown.png",
        "torso/clothes/vest_open/male/walk/leather.png",
    )
    // Legion armour — Warrior variant
    private val TORSO_LEGION = listOf(
        "torso/armour/legion/male/walk.png" to "torso/armour/legion/female/walk.png",
    )
    // Waist belts (extra layer over torso)
    private val WAIST_MAGE   = "torso/waist/belt_mage/walk.png"
    private val WAIST_LEATHER_MALE   = listOf("torso/waist/belt_leather/male/walk/brown.png",
        "torso/waist/belt_leather/male/walk/leather.png")
    private val WAIST_LEATHER_FEMALE = listOf("torso/waist/belt_leather/female/walk/brown.png",
        "torso/waist/belt_leather/female/walk/leather.png")
    private val WAIST_SASH_MALE      = listOf("torso/waist/sash/male/walk/red.png",
        "torso/waist/sash/male/walk/blue.png",
        "torso/waist/sash/male/walk/green.png")
    private val WAIST_SASH_FEMALE    = listOf("torso/waist/sash/female/walk/red.png",
        "torso/waist/sash/female/walk/blue.png",
        "torso/waist/sash/female/walk/green.png")

    // ── Legs (walk.png) ──────────────────────────────────────────────────────

    private val LEGS_PANTS      = "legs/pants/male/walk.png"      to "legs/pants/thin/walk.png"
    private val LEGS_LEGGINGS   = "legs/leggings/male/walk.png"   to "legs/leggings/thin/walk.png"
    private val LEGS_PANTALOONS = "legs/pantaloons/male/walk.png" to "legs/pantaloons/thin/walk.png"
    private val LEGS_SKIRT      = "legs/skirts/plain/male/walk.png" to "legs/skirts/plain/thin/walk.png"
    private val LEGS_HOSE       = "legs/hose/male/walk.png"       to "legs/hose/thin/walk.png"
    private val LEGS_ARMOUR     = "legs/armour/plate/male/walk.png" to "legs/armour/plate/thin/walk.png"
    private val LEGS_SHORTS     = "legs/shorts/shorts/male/walk.png" to "legs/shorts/shorts/thin/walk.png"
    private val LEGS_LEGION     = "legs/skirts/legion/male/walk.png" to "legs/skirts/legion/thin/walk.png"

    private val LEGS = LEGS_PANTS  // default

    private val FEET_POOL = listOf(
        "feet/boots/basic/male/walk/steel.png"    to "feet/boots/basic/thin/walk/steel.png",
        "feet/boots/fold/male/walk/steel.png"     to "feet/boots/fold/thin/walk/steel.png",
        "feet/boots/revised/male/walk/steel.png"  to "feet/boots/revised/thin/walk/steel.png",
        "feet/boots/rimmed/male/walk/steel.png"   to "feet/boots/rimmed/thin/walk/steel.png",
        "feet/shoes/basic/male/walk.png"          to "feet/shoes/basic/thin/walk.png",
        "feet/shoes/revised/male/walk.png"        to "feet/shoes/revised/thin/walk.png",
        "feet/shoes/ghillies/male/walk.png"       to "feet/shoes/ghillies/thin/walk.png",
        "feet/shoes/sara/male/walk.png"           to "feet/shoes/sara/thin/walk.png",
        "feet/sandals/male/walk.png"              to "feet/sandals/thin/walk.png",
        "feet/slippers/male/walk.png"             to "feet/slippers/thin/walk.png",
    )

    // ── Weapons — fg+bg pairs (walk animation) ────────────────────────────────

    data class WeaponPair(val fg: String, val bg: String? = null, val twoHanded: Boolean = false)

    private val WEAPONS_SWORD = listOf(
        WeaponPair("weapon/sword/longsword/walk/longsword.png",         "weapon/sword/longsword/universal_behind/walk/longsword.png"),
        WeaponPair("weapon/sword/longsword/walk/longsword.png",         "weapon/sword/longsword/universal_behind/walk/longsword.png"),
        WeaponPair("weapon/sword/arming/universal/fg/walk/steel.png",   "weapon/sword/arming/universal/bg/walk/steel.png"),
        WeaponPair("weapon/sword/rapier/walk/rapier.png",               "weapon/sword/rapier/universal_behind/walk/rapier.png"),
        WeaponPair("weapon/sword/saber/walk/saber.png",                 "weapon/sword/saber/universal_behind/walk/saber.png"),
        WeaponPair("weapon/sword/rapier/walk/rapier.png",               "weapon/sword/rapier/universal_behind/walk/rapier.png"),  // katana removed
        WeaponPair("weapon/sword/saber/walk/saber.png",                 "weapon/sword/saber/universal_behind/walk/saber.png"),  // scimitar removed
        WeaponPair("weapon/sword/glowsword/walk/blue.png",              "weapon/sword/glowsword/universal_behind/walk/blue.png"),
        WeaponPair("weapon/sword/glowsword/walk/red.png",               "weapon/sword/glowsword/universal_behind/walk/red.png"),
    )
    private val WEAPONS_DAGGER = listOf(
        WeaponPair("weapon/sword/dagger/walk/dagger.png", "weapon/sword/dagger/behind/walk/dagger.png"),
    )
    private val WEAPONS_BLUNT = listOf(
        WeaponPair("weapon/blunt/mace/walk/mace.png",    "weapon/blunt/mace/universal_behind/walk/mace.png"),
        WeaponPair("weapon/blunt/waraxe/walk/waraxe.png","weapon/blunt/waraxe/behind/walk/waraxe.png"),
        WeaponPair("weapon/blunt/flail/walk/flail.png",  "weapon/blunt/flail/behind/walk/flail.png"),
    )
    private val WEAPONS_POLEARM = listOf(
        WeaponPair("weapon/polearm/spear/foreground/walk/steel.png",     "weapon/polearm/spear/background/walk/steel.png"),
        WeaponPair("weapon/polearm/longspear/foreground/walk/steel.png", "weapon/polearm/longspear/background/walk/steel.png"),
        WeaponPair("weapon/polearm/halberd/walk/halberd.png",            "weapon/polearm/halberd/behind/walk/halberd.png"),
        WeaponPair("weapon/polearm/scythe/walk/scythe.png",              "weapon/polearm/scythe/universal_behind/walk/scythe.png"),
        WeaponPair("weapon/polearm/spear/foreground/walk/steel.png",     "weapon/polearm/spear/background/walk/steel.png"),  // duplicate spear, dragonspear/trident use walk_128
    )
    private val WEAPONS_RANGED = listOf(
        WeaponPair("weapon/ranged/bow/normal/universal/foreground/shoot/normal.png",   "weapon/ranged/bow/normal/universal/background/shoot/normal.png"),
        WeaponPair("weapon/ranged/bow/recurve/universal/foreground/shoot/recurve.png", "weapon/ranged/bow/recurve/universal/background/shoot/recurve.png"),
        WeaponPair("weapon/ranged/crossbow/foreground/walk/crossbow.png",              "weapon/ranged/crossbow/background/walk/crossbow.png"),
        WeaponPair("weapon/ranged/slingshot/foreground/walk/slingshot.png",            "weapon/ranged/slingshot/background/walk/slingshot.png"),
    )
    private val WEAPONS_MAGIC = listOf(
        WeaponPair("weapon/magic/crystal/universal/foreground/walk/crystal.png", "weapon/magic/crystal/universal/background/walk/crystal.png"),
        WeaponPair("weapon/magic/simple/foreground/walk/simple.png",             "weapon/magic/simple/background/walk/simple.png"),
        WeaponPair("weapon/magic/wand/male/slash/wand.png",                       null),
    )

    private val WEAPONS_MELEE = WEAPONS_SWORD + WEAPONS_BLUNT + WEAPONS_POLEARM + WEAPONS_DAGGER

    // ── Shields — fg+bg pairs (revised heater has full walk fg+bg support) ────

    data class ShieldPair(val fg: String, val bg: String? = null)

    // Heater shields only — the round shield used a slash-pose sheet that floats by the
    // hand instead of sitting on the arm, so it was dropped.
    private val SHIELDS = listOf(
        ShieldPair("shield/heater/revised/paint/fg/walk/aegean.png",  "shield/heater/revised/paint/bg/walk/aegean.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/red.png",     "shield/heater/revised/paint/bg/walk/red.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/black.png",   "shield/heater/revised/paint/bg/walk/black.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/azure.png",   "shield/heater/revised/paint/bg/walk/azure.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/forest.png",  "shield/heater/revised/paint/bg/walk/forest.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/silver.png",  "shield/heater/revised/paint/bg/walk/silver.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/garnet.png",  "shield/heater/revised/paint/bg/walk/garnet.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/amber.png",   "shield/heater/revised/paint/bg/walk/amber.png"),
        ShieldPair("shield/heater/revised/paint/fg/walk/purple.png",  "shield/heater/revised/paint/bg/walk/purple.png"),
    )

    // ── Helmets (walk.png) ────────────────────────────────────────────────────

    private val HELMETS_LIGHT = listOf(
        "hat/helmet/barbarian/adult/walk.png",
        "hat/helmet/barbarian_nasal/adult/walk.png",
        "hat/helmet/barbarian_viking/adult/walk.png",
        "hat/helmet/kettle/adult/walk.png",
        "hat/helmet/mail/adult/walk.png",
        "hat/helmet/nasal/adult/walk.png",
        "hat/helmet/norman/adult/walk.png",
        "hat/helmet/spangenhelm/adult/walk.png",
        "hat/helmet/morion/adult/walk.png",
        "hat/helmet/pointed/adult/walk.png",
        "hat/helmet/legion/adult/walk.png",
    )
    private val HELMETS_HEAVY = listOf(
        "hat/helmet/armet/adult/walk.png",
        "hat/helmet/armet_simple/adult/walk.png",
        "hat/helmet/barbuta/male/walk.png",
        "hat/helmet/barbuta_simple/adult/walk.png",
        "hat/helmet/bascinet/adult/walk.png",
        "hat/helmet/bascinet_round/adult/walk.png",
        "hat/helmet/close/male/walk.png",
        "hat/helmet/greathelm/male/walk.png",
        "hat/helmet/sugarloaf/male/walk.png",
    )
    private val HELMETS_ALL = HELMETS_LIGHT + HELMETS_HEAVY

    // ── Capes — fg+bg pairs (walk color variants) ─────────────────────────────
    data class CapePair(val fg: String, val bg: String)

    private val CAPES_MALE = listOf(
        CapePair("cape/solid/male/walk/black.png",   "cape/solid_behind/walk/black.png"),
        CapePair("cape/solid/male/walk/maroon.png",  "cape/solid_behind/walk/maroon.png"),
        CapePair("cape/solid/male/walk/green.png",   "cape/solid_behind/walk/green.png"),
        CapePair("cape/solid/male/walk/brown.png",   "cape/solid_behind/walk/brown.png"),
        CapePair("cape/solid/male/walk/blue.png",    "cape/solid_behind/walk/blue.png"),
        CapePair("cape/tattered/female/walk/black.png", "cape/tattered_behind/walk/black.png"),
        CapePair("cape/tattered/female/walk/blue.png",  "cape/tattered_behind/walk/black.png"),
        CapePair("cape/tattered/female/walk/brown.png", "cape/tattered_behind/walk/black.png"),
        CapePair("cape/tattered/female/walk/forest.png","cape/tattered_behind/walk/black.png"),
    )
    private val CAPES_FEMALE = listOf(
        CapePair("cape/solid/female/walk/black.png",   "cape/solid_behind/walk/black.png"),
        CapePair("cape/solid/female/walk/maroon.png",  "cape/solid_behind/walk/maroon.png"),
        CapePair("cape/solid/female/walk/green.png",   "cape/solid_behind/walk/green.png"),
        CapePair("cape/solid/female/walk/lavender.png","cape/solid_behind/walk/lavender.png"),
        CapePair("cape/solid/female/walk/blue.png",    "cape/solid_behind/walk/blue.png"),
        CapePair("cape/tattered/female/walk/black.png","cape/tattered_behind/walk/black.png"),
    )

    // ── Mage hats ─────────────────────────────────────────────────────────────
    private val MAGE_HATS = listOf(
        "hat/cloth/hood/adult/walk.png",
        "hat/cloth/hood/adult/walk.png",      // weighted double for hood
        "hat/cloth/hood_sack/adult/walk.png",
        "hat/magic/wizard/base/adult/walk.png",
        "hat/magic/large/adult/walk/brown.png",
    )


    // ── Class loadout pools (archetype-coherent, internally varied) ───────────
    //
    // These group the bundled + imported assets into per-class weapon/gear pools.
    // buildForSet() draws each slot from these with weighted-optional extras so every
    // re-roll yields a different but on-archetype loadout.

    // Body-type torso pairs (male, female)
    private val P_PLATE   = "torso/armour/plate/male/walk.png"   to "torso/armour/plate/female/walk.png"
    private val P_CHAIN   = "torso/chainmail/male/walk.png"      to "torso/chainmail/female/walk.png"
    private val P_LEGION  = "torso/armour/legion/male/walk.png"  to "torso/armour/legion/female/walk.png"
    private val P_LEATHER = "torso/armour/leather/male/walk.png" to "torso/armour/leather/female/walk.png"

    // Footwear subsets (male, female)
    private val FEET_BOOTS = listOf(
        "feet/boots/basic/male/walk/steel.png"   to "feet/boots/basic/thin/walk/steel.png",
        "feet/boots/fold/male/walk/steel.png"    to "feet/boots/fold/thin/walk/steel.png",
        "feet/boots/revised/male/walk/steel.png" to "feet/boots/revised/thin/walk/steel.png",
        "feet/boots/rimmed/male/walk/steel.png"  to "feet/boots/rimmed/thin/walk/steel.png",
    )
    private val FEET_LIGHT = listOf(
        "feet/shoes/sara/male/walk.png"     to "feet/shoes/sara/thin/walk.png",
        "feet/shoes/ghillies/male/walk.png" to "feet/shoes/ghillies/thin/walk.png",
        "feet/shoes/basic/male/walk.png"    to "feet/shoes/basic/thin/walk.png",
    )
    private val FEET_MAGE = listOf(
        "feet/slippers/male/walk.png"    to "feet/slippers/thin/walk.png",
        "feet/sandals/male/walk.png"     to "feet/sandals/thin/walk.png",
        "feet/shoes/basic/male/walk.png" to "feet/shoes/basic/thin/walk.png",
    )

    // Weapon groups (fg+bg; twoHanded weapons get no shield)
    private val SWORDS = listOf(
        WeaponPair("weapon/sword/longsword/walk/longsword.png",       "weapon/sword/longsword/universal_behind/walk/longsword.png"),
        WeaponPair("weapon/sword/arming/universal/fg/walk/steel.png", "weapon/sword/arming/universal/bg/walk/steel.png"),
        WeaponPair("weapon/sword/saber/walk/saber.png",              "weapon/sword/saber/universal_behind/walk/saber.png"),
        WeaponPair("weapon/sword/rapier/walk/rapier.png",            "weapon/sword/rapier/universal_behind/walk/rapier.png"),
    )
    private val GLOWSWORDS = listOf(
        WeaponPair("weapon/sword/glowsword/walk/blue.png", "weapon/sword/glowsword/universal_behind/walk/blue.png"),
        WeaponPair("weapon/sword/glowsword/walk/red.png",  "weapon/sword/glowsword/universal_behind/walk/red.png"),
    )
    private val BLUNT_1H = listOf(
        WeaponPair("weapon/blunt/mace/walk/mace.png", "weapon/blunt/mace/universal_behind/walk/mace.png"),
    )
    private val BLUNT_2H = listOf(
        WeaponPair("weapon/blunt/waraxe/walk/waraxe.png", "weapon/blunt/waraxe/behind/walk/waraxe.png", twoHanded = true),
        WeaponPair("weapon/blunt/flail/walk/flail.png",   "weapon/blunt/flail/behind/walk/flail.png",   twoHanded = true),
    )
    private val POLEARMS = listOf(
        WeaponPair("weapon/polearm/spear/foreground/walk/steel.png",     "weapon/polearm/spear/background/walk/steel.png",     twoHanded = true),
        WeaponPair("weapon/polearm/longspear/foreground/walk/steel.png", "weapon/polearm/longspear/background/walk/steel.png", twoHanded = true),
        WeaponPair("weapon/polearm/halberd/walk/halberd.png",            "weapon/polearm/halberd/behind/walk/halberd.png",     twoHanded = true),
        WeaponPair("weapon/polearm/scythe/walk/scythe.png",              "weapon/polearm/scythe/universal_behind/walk/scythe.png", twoHanded = true),
    )
    private val BLADES_LIGHT = WEAPONS_DAGGER + listOf(
        WeaponPair("weapon/sword/rapier/walk/rapier.png", "weapon/sword/rapier/universal_behind/walk/rapier.png"),
        WeaponPair("weapon/sword/saber/walk/saber.png",   "weapon/sword/saber/universal_behind/walk/saber.png"),
    )

    // The bare "crystal" sprite is only a floating gem (no shaft), so we ship a
    // pre-composited crystal STAFF (simple shaft + coloured crystal head) in 7 colours
    // under weapon/magic/crystal_staff/.  MAGIC_STAVES = plain "simple" + the crystal staves.
    private val CRYSTAL_COLORS = listOf("blue", "crystal", "green", "orange", "purple", "red", "yellow")
    private val MAGIC_STAVES = listOf(
        WeaponPair("weapon/magic/simple/foreground/walk/simple.png", "weapon/magic/simple/background/walk/simple.png", twoHanded = true),
    ) + CRYSTAL_COLORS.map {
        WeaponPair("weapon/magic/crystal_staff/foreground/walk/$it.png",
                   "weapon/magic/crystal_staff/background/walk/$it.png", twoHanded = true)
    }
    private val WAND = WeaponPair("weapon/magic/wand/male/slash/wand.png", null)

    // Kite shields (imported, fg-only) appended per gender to the heater pool
    // Kite shields only — the heater shield's face sits in the background layer, so from
    // the front you only see its coloured edge; kite shields show the full heraldic face.
    private val KITE_COLORS = listOf("kite_gray", "kite_blue_gray", "kite_gray_green", "kite_gray_orange", "kite_red_gray")
    // Full-faced shields (face in the fg layer → fully visible from the front, unlike heater).
    private fun fullShields(isMale: Boolean): List<ShieldPair> {
        val g = if (isMale) "male" else "female"
        return listOf(
            ShieldPair("shield/crusader/fg/$g/walk/crusader.png", "shield/crusader/bg/walk/crusader.png"),
            ShieldPair("shield/scutum/paint/fg/$g/walk/scutum.png", "shield/scutum/paint/bg/walk/scutum.png"),
            ShieldPair("shield/plus/fg/$g/walk/plus.png", "shield/plus/bg/walk/plus.png"),
            ShieldPair("shield/two_engrailed/paint/fg/$g/walk/two_engrailed.png", "shield/two_engrailed/paint/bg/walk/two_engrailed.png"),
            ShieldPair("shield/spartan/fg/walk/spartan.png", "shield/spartan/bg/walk/spartan.png"),
        )
    }
    private fun shieldsFor(isMale: Boolean): List<ShieldPair> {
        val gd = if (isMale) "male" else "female"
        return KITE_COLORS.map { ShieldPair("shield/kite/$gd/walk/$it.png") } + fullShields(isMale)
    }
    // Facial accessories (eyepatch/mask) — rogue/archer flair
    private val FACIAL = listOf("black", "brown", "leather").flatMap {
        listOf("facial/patches/eyepatch/right/adult/walk/$it.png",
               "facial/patches/eyepatch/left/adult/walk/$it.png")
    } + listOf("black", "brown", "red", "navy").map { "facial/masks/plain/adult/walk/$it.png" }

    // Imported menacing helmets + golden crown, and cloth hoods
    private val HELMS_MENACING = listOf("horned", "maximus", "xeon", "spangenhelm_viking")
        .map { "hat/helmet/$it/adult/walk.png" }
    private const val CROWN_GOLD = "hat/formal/crown/adult/walk/crown_gold.png"
    private val HOODS = listOf("hat/cloth/hood/adult/walk.png", "hat/cloth/hood_sack/adult/walk.png")
    private val CAPES_TATTERED = listOf("black", "blue", "brown", "forest")
        .map { CapePair("cape/tattered/female/walk/$it.png", "cape/tattered_behind/walk/black.png") }
    private const val QUIVER = "quiver/walk/quiver.png"

    // ── Imported via the LPC JSON manifest (variants + walk-usable verified on disk) ──

    // Gem staves — diamond/gnarled/loop are settings that hold a coloured crystal (composited
    // metal + gem, file "<metal>_<gem>" under weapon/magic/<shape>_gem/). The "s" staff is a
    // complete ornate S-scroll top (taller than the gem slot), so it stays plain (no stone).
    private val GEM_STAVES = listOf(
        "diamond" to listOf("gold_red", "silver_blue", "copper_green", "gold_purple"),
        "gnarled" to listOf("gold_red", "silver_purple", "copper_orange", "dark_blue"),
        "loop"    to listOf("silver_blue", "gold_red", "copper_green", "silver_purple"),
    ).flatMap { (shape, combos) ->
        combos.map { c ->
            WeaponPair("weapon/magic/${shape}_gem/foreground/walk/$c.png",
                       "weapon/magic/${shape}_gem/background/walk/$c.png", twoHanded = true)
        }
    } + listOf("copper", "gold", "silver", "bronze").map { c ->
        WeaponPair("weapon/magic/s/universal/foreground/walk/$c.png",
                   "weapon/magic/s/universal/background/walk/$c.png", twoHanded = true)
    }
    // Coloured spears (replaces the single steel spear)
    private val SPEARS = listOf("steel", "copper", "gold", "silver", "bronze", "iron").map { c ->
        WeaponPair("weapon/polearm/spear/foreground/walk/$c.png",
                   "weapon/polearm/spear/background/walk/$c.png", twoHanded = true)
    }
    // Mage headwear (hood reads as the cloak from the front; wizard/celestial add flair)
    private val WIZARD_HATS = listOf("base_black", "base_brown", "base_gray", "blue", "red",
        "forest", "purple", "white").map { "hat/magic/wizard/base/adult/walk/$it.png" }
    private val CELESTIAL_HATS = listOf("blue", "purple", "black", "navy")
        .map { "hat/magic/celestial/adult/walk/$it.png" }
    // Kimono as an alternative mage robe (female-cut, fits the slim mage body)
    private val KIMONO = listOf("blue", "purple", "red", "forest", "black", "navy")
        .map { "dress/kimono/normal/universal/female/walk/$it.png" }
    // Rogue/archer headwear
    private val BANDANAS = listOf("hat/cloth/bandana/adult/walk.png",
        "hat/pirate/bandana/adult/walk.png", "hat/pirate/bandana/skull/adult/walk.png")
    private val CAVALIER = listOf("brown", "black", "forest", "navy")
        .map { "hat/pirate/cavalier/adult/walk/$it.png" }
    private val LEATHER_CAP = listOf("brown", "black", "forest")
        .map { "hat/cloth/leather_cap/adult/walk/$it.png" }
    private val TRICORNE = listOf("black", "brown", "navy", "forest")
        .map { "hat/pirate/tricorne/basic/adult/walk/$it.png" }
    // Visors only look right over an open/closed helm — pair with these helmet bases.
    private val VISORS = listOf("round", "slit", "grated", "pigface")
        .map { "hat/visor/$it/adult/walk.png" }
    // Must match the actual helmet pool paths exactly (close/greathelm live under male/, not adult/),
    // otherwise the `helmet in VISOR_HELMS` check never matches and visors aren't applied.
    private val VISOR_HELMS = listOf(
        "hat/helmet/bascinet/adult/walk.png", "hat/helmet/bascinet_round/adult/walk.png",
        "hat/helmet/armet/adult/walk.png", "hat/helmet/armet_simple/adult/walk.png",
        "hat/helmet/close/male/walk.png", "hat/helmet/greathelm/male/walk.png")
    // Helmet accessories: single-layer (plumage/crest) + fg/bg pairs (horns)
    private val ACC_SINGLE = listOf("hat/accessory/plumage/adult/walk.png",
        "hat/accessory/crest/adult/walk.png")
    private val ACC_HORNS = listOf("upward", "downward", "short").map {
        "hat/accessory/horns_$it/fg/adult/walk.png" to "hat/accessory/horns_$it/bg/adult/walk.png"
    }
    // Shoulders (pauldrons/epaulets/mantal use male|thin; legion uses male|female)
    private fun shoulder(name: String, isMale: Boolean): String {
        val b = if (name == "legion") (if (isMale) "male" else "female") else (if (isMale) "male" else "thin")
        return "shoulders/$name/$b/walk.png"
    }
    private val SHOULDER_TYPES = listOf("pauldrons", "epaulets", "legion", "mantal")
    // Coloured leather footwear (for non-armoured classes)
    private val FEET_LEATHER = listOf(
        "feet/boots/basic/male/walk/brown.png"  to "feet/boots/basic/thin/walk/brown.png",
        "feet/boots/basic/male/walk/tan.png"    to "feet/boots/basic/thin/walk/tan.png",
        "feet/shoes/basic/male/walk/brown.png"  to "feet/shoes/basic/thin/walk/brown.png",
        "feet/shoes/basic/male/walk/black.png"  to "feet/shoes/basic/thin/walk/black.png",
    )

    /** Warrior/paladin head: optional helmet + optional accessory (visor only on a visor-helm). */
    private fun knightHead(heavy: Boolean): Triple<String?, String?, String?> {
        if (!roll(0.7f)) return Triple(null, null, null)
        val pool = if (heavy) HELMETS_HEAVY + HELMS_MENACING + listOf(CROWN_GOLD)
                   else HELMETS_LIGHT + HELMETS_HEAVY + HELMS_MENACING
        val helmet = pool.random()
        var accFg: String? = null; var accBg: String? = null
        when {
            helmet in VISOR_HELMS && roll(0.5f) -> accFg = VISORS.random()
            roll(0.45f) -> if (roll(0.5f)) ACC_HORNS.random().let { accFg = it.first; accBg = it.second }
                           else accFg = ACC_SINGLE.random()
        }
        return Triple(helmet, accFg, accBg)
    }
    private fun shoulders(isMale: Boolean) = shoulder(SHOULDER_TYPES.random(), isMale)

    // ── Probabilities ─────────────────────────────────────────────────────────

    private const val CHANCE_ARMOUR  = 0.35f
    private const val CHANCE_SHIELD  = 0.55f
    private const val CHANCE_HELMET  = 0.55f
    private const val CHANCE_RANGED  = 0.25f
    private const val CHANCE_MAGIC   = 0.20f
    private const val CHANCE_POLEARM = 0.20f
    private const val CHANCE_BLUNT   = 0.20f

    // ── Public API ────────────────────────────────────────────────────────────

    fun clear() = Equipment()

    fun randomize(features: CharacterFeatures): Equipment {
        val isMale  = features.gender == FaceAttributeAnalyzer.Gender.MALE
        val isChild = features.ageGroup == FaceAttributeAnalyzer.AgeGroup.CHILD

        val wearArmour = !isChild && Random.nextFloat() < CHANCE_ARMOUR
        val torso = when {
            wearArmour -> when (Random.nextFloat()) {
                in 0f..0.5f -> pick(TORSO_ARMOUR, isMale)
                in 0.5f..0.8f -> pick(TORSO_LEATHER, isMale)
                else -> pick(TORSO_LEGION, isMale)
            }
            else -> pick(TORSO_CASUAL, isMale)
        }
        val legs = when (Random.nextFloat()) {
            in 0f..0.5f -> get(LEGS_PANTS, isMale)
            in 0.5f..0.7f -> get(LEGS_LEGGINGS, isMale)
            in 0.7f..0.85f -> get(LEGS_PANTALOONS, isMale)
            else -> if (isMale) get(LEGS_PANTS, true) else get(LEGS_SKIRT, false)
        }
        val belt = if (Random.nextFloat() < 0.4f) {
            if (isMale) WAIST_LEATHER_MALE.random() else WAIST_LEATHER_FEMALE.random()
        } else null
        val feet   = pick(FEET_POOL, isMale)

        val weapon = if (!isChild) pickWeapon() else null
        val isMelee = weapon != null && WEAPONS_MELEE.contains(weapon)
        val shield = if (isMelee && Random.nextFloat() > CHANCE_SHIELD) SHIELDS.random() else null
        val helmetChance = if (wearArmour) 0.30f else CHANCE_HELMET
        val helmet = if (!isChild && Random.nextFloat() > helmetChance) HELMETS_ALL.random() else null

        return Equipment(
            set          = EquipmentSet.NONE,
            torsoPath    = torso,
            legsPath     = legs,
            feetPath     = feet,
            weaponFgPath = weapon?.fg,
            weaponBgPath = weapon?.bg,
            shieldFgPath = shield?.fg,
            shieldBgPath = shield?.bg,
            helmetPath   = helmet,
            beltPath     = belt,
        )
    }

    fun buildForSet(set: EquipmentSet, features: CharacterFeatures): Equipment {
        val isMale  = features.gender == FaceAttributeAnalyzer.Gender.MALE
        val isChild = features.ageGroup == FaceAttributeAnalyzer.AgeGroup.CHILD

        if (set == EquipmentSet.NONE) return Equipment(
            set      = set,
            feetPath = if (isMale) "feet/shoes/revised/male/walk/black.png"
            else        "feet/shoes/revised/thin/walk/black.png",
        )

        // Children get a costume only — no real weapons / armour / helmets.
        if (isChild) return Equipment(
            set      = set,
            torsoPath = pick(TORSO_CASUAL, isMale),
            legsPath  = get(LEGS_PANTS, isMale),
            feetPath  = feet(FEET_LIGHT, isMale),
        )

        fun cape() = (if (isMale) CAPES_MALE else CAPES_FEMALE).random()
        fun sash() = (if (isMale) WAIST_SASH_MALE else WAIST_SASH_FEMALE).random()

        return when (set) {
            EquipmentSet.WARRIOR -> {
                val w = (SWORDS.repeated(3) + BLUNT_1H.repeated(2) + BLUNT_2H + POLEARMS + SPEARS + GLOWSWORDS).random()
                val torso = weighted(
                    get(P_PLATE, isMale)   to 3, get(P_CHAIN, isMale)  to 3,
                    get(P_LEGION, isMale)  to 2, get(P_LEATHER, isMale) to 1,
                )
                val shield = if (!w.twoHanded && roll(0.6f)) shieldsFor(isMale).random() else null
                val (helmet, accFg, accBg) = knightHead(heavy = false)
                val cp = if (roll(0.3f)) cape() else null
                Equipment(set, torso,
                    if (roll(0.7f)) get(LEGS_ARMOUR, isMale) else get(LEGS_PANTS, isMale),
                    feet(FEET_BOOTS, isMale),
                    w.fg, w.bg, shield?.fg, shield?.bg, helmet,
                    if (roll(0.3f)) sash() else null, cp?.fg, cp?.bg,
                    shoulderPath = if (roll(0.4f)) shoulders(isMale) else null,
                    helmAccFgPath = accFg, helmAccBgPath = accBg)
            }
            EquipmentSet.PALADIN -> {
                val w = (SWORDS.repeated(3) + BLUNT_1H.repeated(2) + GLOWSWORDS).random()
                val torso = weighted(
                    get(P_PLATE, isMale) to 4, get(P_CHAIN, isMale) to 3, get(P_LEGION, isMale) to 1,
                )
                val shield = if (roll(0.8f)) shieldsFor(isMale).random() else null
                val (helmet, accFg, accBg) = knightHead(heavy = true)
                val cp = if (roll(0.55f)) cape() else null
                Equipment(set, torso, get(LEGS_ARMOUR, isMale), feet(FEET_BOOTS, isMale),
                    w.fg, w.bg, shield?.fg, shield?.bg, helmet,
                    if (roll(0.4f)) sash() else null, cp?.fg, cp?.bg,
                    shoulderPath = if (roll(0.5f)) shoulders(isMale) else null,
                    helmAccFgPath = accFg, helmAccBgPath = accBg)
            }
            EquipmentSet.ARCHER -> {
                val w = WEAPONS_RANGED.random()
                val torso = weighted(
                    get(P_LEATHER, isMale) to 5,
                    (if (isMale) TORSO_VEST_MALE.random() else get(P_LEATHER, false)) to 2,
                    pick(TORSO_CASUAL, isMale) to 3,
                )
                val legsPath = weighted(
                    get(LEGS_LEGGINGS, isMale) to 5, get(LEGS_PANTS, isMale) to 3, get(LEGS_HOSE, isMale) to 2,
                )
                val belt = if (roll(0.6f)) (if (isMale) WAIST_LEATHER_MALE else WAIST_LEATHER_FEMALE).random() else null
                val helmet = if (roll(0.45f))
                    (HOODS + LEATHER_CAP + TRICORNE + CAVALIER + BANDANAS + HELMETS_LIGHT.take(2)).random() else null
                Equipment(set, torso, legsPath, feet(FEET_LIGHT + FEET_BOOTS + FEET_LEATHER, isMale),
                    w.fg, w.bg, null, null, helmet, belt, null, null, QUIVER,
                    facialPath = if (roll(0.2f)) FACIAL.random() else null)
            }
            EquipmentSet.ROGUE -> {
                val w = (WEAPONS_DAGGER.repeated(2) + BLADES_LIGHT).random()
                val torso = if (roll(0.6f)) (if (isMale) TORSO_VEST_MALE.random() else get(P_LEATHER, false))
                else pick(TORSO_CASUAL, isMale)
                val legsPath = weighted(
                    get(LEGS_LEGGINGS, isMale) to 5, get(LEGS_PANTS, isMale) to 3, get(LEGS_HOSE, isMale) to 2,
                )
                val cp = if (roll(0.2f)) CAPES_TATTERED.random() else null
                Equipment(set, torso, legsPath, feet(FEET_LIGHT + FEET_LEATHER, isMale),
                    w.fg, w.bg, null, null,
                    if (roll(0.4f)) (HOODS + BANDANAS + CAVALIER).random() else null,
                    if (roll(0.4f)) sash() else null, cp?.fg, cp?.bg,
                    facialPath = if (roll(0.3f)) FACIAL.random() else null)
            }
            EquipmentSet.MAGE -> {
                // SpriteMapper renders mages on the slim body, so use the thin leg/feet
                // variants; the robe covers most of the legs anyway.
                val w = (MAGIC_STAVES + GEM_STAVES + (if (isMale) listOf(WAND) else emptyList())).random()
                val torso = (TORSO_ROBE_FEMALE + KIMONO).random()   // robe/kimono + slim body = wizard
                val cp = if (roll(0.6f)) cape() else null
                // Hood reads as a hooded cloak from the front (a back-cape barely shows); wizard
                // and celestial hats add flair. Headwear common so the cloak/hat is visible.
                Equipment(set, torso, get(LEGS_PANTALOONS, false), feet(FEET_MAGE, false),
                    w.fg, w.bg, null, null,
                    if (roll(0.85f)) (HOODS + HOODS + WIZARD_HATS + CELESTIAL_HATS).random() else null,
                    WAIST_MAGE, cp?.fg, cp?.bg)
            }
            EquipmentSet.NONE -> Equipment(set)  // unreachable (handled above)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pick(pool: List<Pair<String, String>>, isMale: Boolean) =
        pool.random().let { if (isMale) it.first else it.second }

    private fun get(pair: Pair<String, String>, isMale: Boolean) =
        if (isMale) pair.first else pair.second

    /** Random (male, female) entry from a footwear pool, resolved to the body type. */
    private fun feet(pool: List<Pair<String, String>>, isMale: Boolean) = get(pool.random(), isMale)

    private fun roll(p: Float) = Random.nextFloat() < p

    /** Repeat a list n times (weight-by-duplication for `.random()`). */
    private fun <T> List<T>.repeated(n: Int): List<T> = List(n) { this }.flatten()

    /** Weighted random pick from (value, weight) pairs. */
    private fun <T> weighted(vararg options: Pair<T, Int>): T {
        val total = options.sumOf { it.second }
        var r = Random.nextInt(total)
        for ((v, w) in options) { if (r < w) return v; r -= w }
        return options.last().first
    }

    private fun pickWeapon(): WeaponPair {
        val r = Random.nextFloat()
        return when {
            r < CHANCE_MAGIC                     -> WEAPONS_MAGIC.random()
            r < CHANCE_MAGIC + CHANCE_RANGED     -> WEAPONS_RANGED.random()
            else -> {
                val r2 = Random.nextFloat()
                when {
                    r2 < CHANCE_POLEARM                  -> WEAPONS_POLEARM.random()
                    r2 < CHANCE_POLEARM + CHANCE_BLUNT   -> WEAPONS_BLUNT.random()
                    else                                  -> WEAPONS_SWORD.random()
                }
            }
        }
    }
}