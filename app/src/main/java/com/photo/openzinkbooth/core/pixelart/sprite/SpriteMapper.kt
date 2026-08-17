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

import android.graphics.Color as AndroidColor
import com.photo.openzinkbooth.core.pixelart.analyzer.CharacterFeatures
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceAttributeAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceParsingAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.HairLength
import com.photo.openzinkbooth.core.pixelart.analyzer.ClothingStyle

/**
 * SpriteMapper — maps CharacterFeatures to LPC sprite layers.
 *
 * Layer z-order follows the official Universal LPC Generator sheet_definitions.
 * All paths verified against assets/lpc/.
 *
 * Slot philosophy (inspired by pflat's CharGen):
 *   Each slot maps to exactly one item. Body/head/hair/nose/eyes are always
 *   filled from analysis. Equipment slots come from EquipmentRandomizer.
 */
object SpriteMapper {

    data class SpriteConfig(
        val layers: List<SpriteLayer>,
        val eyeColor: FaceParsingAnalyzer.EyeColor,
    )

    data class SpriteLayer(
        val path: String,
        val recolor: Int?,
        val zPos: Int,
    )

    // ── Verified hair pools (combat_idle confirmed) ───────────────────────────

    private val HAIR_SHORT_MALE = listOf(
        "hair/buzzcut/adult/walk.png",
        "hair/high_and_tight/adult/walk.png",
        "hair/flat_top_fade/adult/walk.png",
        "hair/bob_side_part/adult/walk.png",
    )
    private val HAIR_SHORT_FEMALE = listOf(
        "hair/pixie/adult/walk.png",
        "hair/bob/adult/walk.png",
        "hair/bob_side_part/adult/walk.png",
        "hair/bangs/adult/walk.png",
    )
    private val HAIR_MEDIUM_MALE = listOf(
        "hair/curtains/adult/walk.png",
        "hair/parted/adult/walk.png",
        "hair/halfmessy/adult/walk.png",
        "hair/bangs/adult/walk.png",
    )
    private val HAIR_MEDIUM_FEMALE = listOf(
        "hair/lob/adult/walk.png",
        "hair/half_up/adult/walk.png",
        "hair/curly_short/adult/walk.png",
        "hair/curly_short2/adult/walk.png",
    )
    private val HAIR_LONG_MALE = listOf(
        "hair/long/adult/walk.png",
        "hair/curly_long/adult/walk.png",
    )
    private val HAIR_LONG_FEMALE = listOf(
        "hair/xlong/adult/fg/walk.png",
        "hair/wavy/adult/fg/walk.png",
        "hair/braid/adult/fg/walk.png",
        "hair/high_ponytail/adult/fg/walk.png",
        "hair/curly_long/adult/walk.png",
    )

    // Hair bg layer (for fg/bg styles)
    private val HAIR_BG = mapOf(
        "hair/xlong/adult/fg/walk.png"         to "hair/xlong/adult/bg/walk.png",
        "hair/wavy/adult/fg/walk.png"          to "hair/wavy/adult/bg/walk.png",
        "hair/braid/adult/fg/walk.png"         to "hair/braid/adult/bg/walk.png",
        "hair/high_ponytail/adult/fg/walk.png" to "hair/high_ponytail/adult/bg/walk.png",
    )

    // ── Verified footwear pools ───────────────────────────────────────────────

    private val FEET_CASUAL_MALE = listOf(
        "feet/boots/basic/male/walk/steel.png",
        "feet/boots/fold/male/walk/steel.png",
        "feet/boots/revised/male/walk/steel.png",
        "feet/boots/rimmed/male/walk/steel.png",
        "feet/shoes/basic/male/walk.png",
        "feet/shoes/revised/male/walk.png",
        "feet/shoes/ghillies/male/walk.png",
        "feet/shoes/sara/male/walk.png",
        "feet/sandals/male/walk.png",
        "feet/slippers/male/walk.png",
    )
    private val FEET_CASUAL_FEMALE = listOf(
        "feet/boots/basic/thin/walk/steel.png",
        "feet/boots/fold/thin/walk/steel.png",
        "feet/boots/revised/thin/walk/steel.png",
        "feet/boots/rimmed/thin/walk/steel.png",
        "feet/shoes/basic/thin/walk.png",
        "feet/shoes/revised/thin/walk.png",
        "feet/shoes/ghillies/thin/walk.png",
        "feet/shoes/sara/thin/walk.png",
        "feet/sandals/thin/walk.png",
        "feet/slippers/thin/walk.png",
    )
    private val FEET_FORMAL_MALE = listOf(
        "feet/boots/revised/male/walk/steel.png",
        "feet/boots/rimmed/male/walk/steel.png",
    )
    private val FEET_FORMAL_FEMALE = listOf(
        "feet/boots/revised/thin/walk/steel.png",
        "feet/boots/rimmed/thin/walk/steel.png",
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Maps features to sprite config. Returns both the config and updated features
     * with stable cached choices (hair, feet) so re-renders stay consistent.
     */
    fun map(features: CharacterFeatures, equipment: EquipmentRandomizer.Equipment? = null): SpriteConfig {
        // Resolve stable choices — pick once, reuse on equipment changes
        val resolvedFeatures = resolveStableChoices(features)
        return buildConfig(resolvedFeatures, equipment)
    }

    /** Returns features with pickedHairPath and pickedFeetPath filled in. */
    fun resolveStableChoices(features: CharacterFeatures): CharacterFeatures {
        val isMale = features.gender == FaceAttributeAnalyzer.Gender.MALE
        val seed = (features.skinColor xor features.hairColor)
        return features.copy(
            pickedHairPath = features.pickedHairPath ?: pickHair(features.hairLength, isMale, seed),
        )
    }

    private fun buildConfig(features: CharacterFeatures, equipment: EquipmentRandomizer.Equipment?): SpriteConfig {
        val isMale   = features.gender == FaceAttributeAnalyzer.Gender.MALE
        val isSenior = features.ageGroup == FaceAttributeAnalyzer.AgeGroup.SENIOR
        val isChild  = features.ageGroup == FaceAttributeAnalyzer.AgeGroup.CHILD
        val isTeen   = features.ageGroup == FaceAttributeAnalyzer.AgeGroup.TEEN

        // Mages wear the (female) robe, which only fits the slim body — render every mage
        // on the slim body regardless of gender. Head/face/hair/beard below still follow
        // the real gender, giving a classic slender wizard.
        val isMage = equipment?.set == EquipmentRandomizer.EquipmentSet.MAGE
        val body = when { isTeen -> "teen"; isMage -> "female"; isMale -> "male"; else -> "female" }
        val thin = when { isTeen -> "thin"; isMage -> "thin"; isMale -> "male"; else -> "thin" }

        val skin       = features.skinColor
        val clothColor = features.clothingColor
        val eyeColor   = features.eyeColor ?: FaceParsingAnalyzer.EyeColor.BROWN

        // Use stable cached choices
        val hairPath = features.pickedHairPath ?: pickHair(features.hairLength, isMale)
        val defaultFeet = if (isMale) "feet/shoes/revised/male/walk/black.png"
        else        "feet/shoes/revised/thin/walk/black.png"

        val layers = mutableListOf<SpriteLayer>()

        // z=1   Cape bg (behind everything, behind body)
        equipment?.capeBgPath?.let { add(layers, it, null, 1) }

        // z=0  Shadow
        add(layers, "shadow/adult/slash/shadow.png", null, 0)

        // z=9  Hair bg (long styles)
        HAIR_BG[hairPath]?.let { add(layers, it, features.hairColor, 9) }

        // z=10 Body
        add(layers, "body/bodies/$body/walk.png", skin, 10)

        // z=20 Legs
        val legsPath = equipment?.legsPath ?: "legs/pants/$thin/walk.png"
        add(layers, legsPath, darken(clothColor, 0.7f), 20)

        // z=25 Feet
        val feetPath = equipment?.feetPath ?: defaultFeet
        add(layers, feetPath, null, 25)

        // z=30 Belt/waist (over torso clothes, under armour)
        equipment?.beltPath?.let { add(layers, it, null, 30) }

        // z=35 Torso (clothes) — only if no armour from equipment
        if (equipment?.torsoPath == null) {
            val torsoPath = pickTorso(features.clothingStyle, body)
            add(layers, torsoPath, clothColor, 35)
        }

        // z=60 Torso armour (from equipment)
        equipment?.torsoPath?.let { add(layers, it, null, 60) }

        // z=65 Shoulders (pauldrons/epaulets, over torso)
        equipment?.shoulderPath?.let { add(layers, it, null, 65) }

        // z=95 Helmet accessory behind (e.g. horns bg, behind head)
        equipment?.helmAccBgPath?.let { add(layers, it, null, 95) }

        // z=80 Necklace
        if (features.hasNecklace) {
            val ng = if (isMale) "male" else "female"
            add(layers, "neck/necklace/chain/$ng/slash/gold.png", null, 80)
        }

        // z=90 Neck accessory (formal only, no equipment set)
        if (features.clothingStyle == ClothingStyle.FORMAL && equipment == null) {
            val neckPath = if (isMale) "neck/tie/necktie/male/walk.png"
            else        "neck/capetie/female/walk/white.png"
            val neckColor = if (isMale) AndroidColor.argb(255, 20, 20, 20) else null
            add(layers, neckPath, neckColor, 90)
        }

        // z=100 Head
        val headType = pickHeadType(isMale, isSenior, features)
        add(layers, "head/heads/human/$headType/walk.png", skin, 100)

        // z=101 Face expression
        val faceGender = when { isSenior -> "elderly"; isMale -> "male"; else -> "female" }
        add(layers, "head/faces/$faceGender/happy/walk.png", skin, 101)

        // z=102 Wrinkles
        if (isSenior || features.ageYears > 65) {
            add(layers, "head/wrinkles/walk.png", skin, 102)
        }

        // z=105 Nose
        val nosePath = if (isSenior) "head/nose/elderly/adult/walk.png"
        else          "head/nose/straight/adult/walk.png"
        add(layers, nosePath, skin, 105)

        // z=106 Eyes
        add(layers, "eyes/human/adult/neutral/walk/${eyeColor.folderName}.png", null, 106)

        // z=110 Beard
        if (features.hasBeard && isMale && !isChild) {
            val beardPath = if (isSenior) "beards/beard/medium/walk.png"
            else          "beards/beard/5oclock_shadow/walk.png"
            add(layers, beardPath, features.hairColor, 110)
        }

        // z=110 Shield (equipment)
        equipment?.shieldFgPath?.let { add(layers, it, null, 110) }

        // z=115 Glasses
        if (features.hasGlasses) {
            add(layers, "facial/glasses/glasses/adult/walk.png", null, 115)
        }

        // z=116 Facial accessory (eyepatch/mask from equipment)
        equipment?.facialPath?.let { add(layers, it, null, 116) }

        // z=120 Hair fg
        add(layers, hairPath, features.hairColor, 120)

        // z=130 Hat (from analysis) OR helmet (from equipment)
        when {
            equipment?.helmetPath != null ->
                add(layers, equipment.helmetPath, null, 130)

        }

        // z=3   Shield bg (behind body)
        equipment?.shieldBgPath?.let { add(layers, it, null, 3) }

        // z=8   Quiver (back, behind body — archer)
        equipment?.quiverPath?.let { add(layers, it, null, 8) }

        // z=9  Weapon bg (behind body)
        equipment?.weaponBgPath?.let { add(layers, it, null, 9) }

        // z=135 Cape fg (in front, over hat)
        equipment?.capeFgPath?.let { add(layers, it, null, 135) }

        // z=139 Helmet accessory front (visor/plume/crest/horns fg, over helmet)
        equipment?.helmAccFgPath?.let { add(layers, it, null, 139) }

        // z=140 Weapon fg (in front of body)
        equipment?.weaponFgPath?.let { add(layers, it, null, 140) }

        layers.sortBy { it.zPos }
        return SpriteConfig(layers, eyeColor)
    }

    // ── Selectors ─────────────────────────────────────────────────────────────

    private fun pickHair(length: HairLength, isMale: Boolean, seed: Int = 0): String {
        // Fixed style per length/gender — same as PersonaLens approach
        val path = when (length) {
            HairLength.SHORT  -> if (isMale) "hair/buzzcut/adult/walk.png"
            else        "hair/pixie/adult/walk.png"
            HairLength.MEDIUM -> if (isMale) "hair/curtains/adult/walk.png"
            else        "hair/lob/adult/walk.png"
            HairLength.LONG   -> if (isMale) "hair/long/adult/walk.png"
            else        "hair/xlong/adult/fg/walk.png"
        }
        return path
    }

    private fun pickFeet(style: ClothingStyle, isMale: Boolean): String = when {
        style == ClothingStyle.FORMAL && isMale  -> FEET_FORMAL_MALE.random()
        style == ClothingStyle.FORMAL && !isMale -> FEET_FORMAL_FEMALE.random()
        isMale  -> FEET_CASUAL_MALE.random()
        else    -> FEET_CASUAL_FEMALE.random()
    }

    private fun pickTorso(style: ClothingStyle, body: String): String = when (style) {
        ClothingStyle.FORMAL -> "torso/clothes/longsleeve/longsleeve/$body/walk.png"
        ClothingStyle.SPORTY -> "torso/clothes/shortsleeve/shortsleeve/$body/walk.png"
        ClothingStyle.CASUAL -> listOf(
            "torso/clothes/shortsleeve/tshirt/$body/walk.png",
            "torso/clothes/shortsleeve/shortsleeve/$body/walk.png",
        ).random()
    }

    private fun pickHeadType(isMale: Boolean, isSenior: Boolean, features: CharacterFeatures) = when {
        isSenior && isMale   -> "male_elderly"
        isSenior && !isMale  -> "female_elderly"
        isMale && features.faceShape == FaceAttributeAnalyzer.FaceShape.ROUND -> "male_plump"
        isMale && features.faceShape == FaceAttributeAnalyzer.FaceShape.OVAL  -> "male_gaunt"
        isMale               -> "male"
        else                 -> "female"
    }

    private fun add(layers: MutableList<SpriteLayer>, path: String, recolor: Int?, zPos: Int) {
        layers += SpriteLayer(path, recolor, zPos)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr  8) and 0xFF) * factor
        val b = ( color         and 0xFF) * factor
        return AndroidColor.rgb(r.toInt(), g.toInt(), b.toInt())
    }
}