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

package com.photo.openzinkbooth.core.pixelart.stats

import com.photo.openzinkbooth.core.pixelart.analyzer.CharacterFeatures
import com.photo.openzinkbooth.core.pixelart.analyzer.ClothingStyle
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceAttributeAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceParsingAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.HairLength
import com.photo.openzinkbooth.core.pixelart.PixelArtAnalyzer
import com.photo.openzinkbooth.core.pixelart.sprite.EquipmentRandomizer

import kotlin.math.sqrt

/**
 * StatCalculator
 *
 * Base stats derived from face features.
 * Class bonuses applied on top when an equipment set is active.
 *
 * HP  — age based (always, regardless of class)
 * STR — strength: face shape, beard, gender
 * DEF — defense: accessories, clothing
 * MAG — magic: hair color, eye color, gender
 * END — endurance (spd field): age, sporty style
 *
 * Class bonuses redistribute stats to fit the archetype.
 */
object StatCalculator {

    fun calculate(
        f: CharacterFeatures,
        equipmentSet: EquipmentRandomizer.EquipmentSet = EquipmentRandomizer.EquipmentSet.NONE,
    ): PixelArtAnalyzer.CharacterStats {
        val isMale   = f.gender == FaceAttributeAnalyzer.Gender.MALE
        val isFemale = !isMale
        val age      = f.ageYears.coerceIn(1, 100)

        // ── HP: always age-based, class-independent ───────────────────────
        val hp = (when {
            age < 15 -> 70
            age < 20 -> 90
            age < 30 -> 80
            age < 40 -> 70
            age < 50 -> 60
            age < 60 -> 50
            else     -> 35
        } + randomVariance(5)).coerceIn(10, 100)

        // ── Base stats from face features ─────────────────────────────────
        var str = 35
        if (isMale)   str += 15
        if (f.hasBeard) str += 10
        if (f.faceShape == FaceAttributeAnalyzer.FaceShape.SQUARE) str += 15
        if (f.faceShape == FaceAttributeAnalyzer.FaceShape.ROUND)  str += 5
        if (age in 20..45) str += 10
        str += randomVariance(8)

        var def = 25
        if (f.hasGlasses)  def += 12
        if (f.hasNecklace) def += 8
        if (f.clothingStyle == ClothingStyle.FORMAL) def += 15
        if (f.clothingStyle == ClothingStyle.SPORTY) def += 5
        def += randomVariance(8)

        var mag = 25
        if (isFemale) mag += 15
        if (f.hairLength == HairLength.LONG)   mag += 12
        if (f.hairLength == HairLength.MEDIUM) mag += 5
        mag += hairColorMagicBonus(f.hairColor)
        if (f.eyeColor == FaceParsingAnalyzer.EyeColor.BLUE)  mag += 10
        if (f.eyeColor == FaceParsingAnalyzer.EyeColor.GREEN) mag += 8
        if (f.hasNecklace) mag += 8
        mag += randomVariance(8)

        var end = 35
        if (age < 25) end += 20
        else if (age < 35) end += 10
        if (f.hairLength == HairLength.SHORT)     end += 10
        if (f.clothingStyle == ClothingStyle.SPORTY) end += 15
        if (isMale) end += 5
        end += randomVariance(8)

        // ── Class bonuses ─────────────────────────────────────────────────
        // Each class boosts its key stats and reduces others
        // Total budget stays roughly the same (zero-sum redistribution)
        val (strF, defF, magF, endF) = when (equipmentSet) {
            EquipmentRandomizer.EquipmentSet.WARRIOR ->
                // High STR+END, medium DEF, low MAG
                listOf(1.45f, 1.10f, 0.40f, 1.35f)
            EquipmentRandomizer.EquipmentSet.PALADIN ->
                // High DEF+STR, medium END, low MAG
                listOf(1.25f, 1.50f, 0.55f, 1.10f)
            EquipmentRandomizer.EquipmentSet.ARCHER ->
                // High END+STR, medium DEF, low MAG
                listOf(1.20f, 0.90f, 0.50f, 1.55f)
            EquipmentRandomizer.EquipmentSet.ROGUE ->
                // High END, medium STR+MAG, low DEF
                listOf(1.10f, 0.70f, 0.85f, 1.60f)
            EquipmentRandomizer.EquipmentSet.MAGE ->
                // Very high MAG, medium DEF, low STR+END
                listOf(0.40f, 1.05f, 1.80f, 0.65f)
            else ->
                listOf(1.00f, 1.00f, 1.00f, 1.00f)
        }

        return PixelArtAnalyzer.CharacterStats(
            hp  = hp,
            str = (str  * strF).toInt().coerceIn(5, 100),
            def = (def  * defF).toInt().coerceIn(5, 100),
            mag = (mag  * magF).toInt().coerceIn(5, 100),
            spd = (end  * endF).toInt().coerceIn(5, 100),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun hairColorMagicBonus(hairColor: Int): Int {
        val r = (hairColor shr 16) and 0xFF
        val g = (hairColor shr  8) and 0xFF
        val b =  hairColor         and 0xFF
        val dist = sqrt(
            ((r - 80).toDouble().let { it * it } +
                    (g - 50).toDouble().let { it * it } +
                    (b - 30).toDouble().let { it * it })
        )
        return (dist / 15.0).toInt().coerceIn(0, 20)
    }

    private fun randomVariance(range: Int): Int = (-range..range).random()
}