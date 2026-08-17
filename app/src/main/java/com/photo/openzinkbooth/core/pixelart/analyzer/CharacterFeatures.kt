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

package com.photo.openzinkbooth.core.pixelart.analyzer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceAttributeAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceParsingAnalyzer

/**
 * CharacterFeatures
 *
 * All detected visual features for a single person.
 * Replaces PhotoAnalyzer — no longer a separate object, just a data class.
 */
data class CharacterFeatures(
    // From FaceParsingAnalyzer (BiSeNet)
    val skinColor:    Int                              = Color.rgb(210, 180, 140),
    val hairColor:    Int                              = Color.rgb(50, 30, 10),
    val hairLength:   HairLength                      = HairLength.MEDIUM,
    val hasGlasses:   Boolean                         = false,
    val hasNecklace:  Boolean                         = false,
    val hasBeard:     Boolean                         = false,
    val clothingColor: Int                            = Color.rgb(70, 130, 180),
    val clothingStyle: ClothingStyle                  = ClothingStyle.CASUAL,
    val eyeColor:     FaceParsingAnalyzer.EyeColor    = FaceParsingAnalyzer.EyeColor.BROWN,
    val faceShape:    FaceAttributeAnalyzer.FaceShape = FaceAttributeAnalyzer.FaceShape.OVAL,
    // From FaceAttributeAnalyzer (InsightFace genderage)
    val gender:   FaceAttributeAnalyzer.Gender        = FaceAttributeAnalyzer.Gender.MALE,
    val ageGroup: FaceAttributeAnalyzer.AgeGroup      = FaceAttributeAnalyzer.AgeGroup.ADULT,
    val ageYears: Int                                 = 25,
    // Face detection info
    val faceRect:  RectF?         = null,
    val landmarks: List<PointF>   = emptyList(),
    // Cached sprite choices (set once, stable across equipment changes)
    val pickedHairPath: String?   = null,
    val pickedFeetPath: String?   = null,
)

enum class HairLength    { SHORT, MEDIUM, LONG }
enum class ClothingStyle { CASUAL, FORMAL, SPORTY }