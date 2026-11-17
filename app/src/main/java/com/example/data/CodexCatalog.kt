package com.example.data

import com.example.engine.AnomalyType
import com.example.engine.BiomeType

data class CodexLoreItem(
    val id: String,
    val category: String,
    val code: String,
    val title: String,
    val subtitle: String,
    val fieldNotes: String,
    val survivalTip: String
)

object CodexCatalog {
    const val CATEGORY_BIOME = "BIOME"
    const val CATEGORY_ANOMALY = "ANOMALY"

    val biomes: List<CodexLoreItem> = listOf(
        CodexLoreItem(
            id = BiomeType.MONO_YELLOW.id,
            category = CATEGORY_BIOME,
            code = "SEC-00",
            title = "Level 0: Damp Office",
            subtitle = "0m+ // Mono-Yellow Partition Maze",
            fieldNotes = "Endless moist carpet, faded mustard wallpaper, and ceiling fluorescent panels humming at a constant 60Hz. Spatial layouts occasionally fold behind observers.",
            survivalTip = "Watch for AA Batteries along dead-end alcoves and keep track of your spawn bearing."
        ),
        CodexLoreItem(
            id = BiomeType.POOLROOMS.id,
            category = CATEGORY_BIOME,
            code = "SEC-37",
            title = "Sub-Flooded Poolrooms",
            subtitle = "45m+ // Aquamarine Ceramic Chambers",
            fieldNotes = "Echoing turquoise tile corridors submerged in ankle-deep lukewarm water. Visibility stretches further across open pillared halls, exposing wandering Watchers.",
            survivalTip = "Open chambers make spotting anomalies easier, but long sightlines work both ways."
        ),
        CodexLoreItem(
            id = BiomeType.PIPEWORKS.id,
            category = CATEGORY_BIOME,
            code = "SEC-02",
            title = "Concrete Pipeworks",
            subtitle = "90m+ // Industrial Maintenance Shafts",
            fieldNotes = "Tight brutalist concrete tunnels lined with rusted conduits and amber emergency lamps. Tight corners amplify ambush risks and acoustic reverberation.",
            survivalTip = "Avoid sprinting blindly around tight intersections; conserve stamina for sudden chases."
        ),
        CodexLoreItem(
            id = BiomeType.RED_CORRIDOR.id,
            category = CATEGORY_BIOME,
            code = "SEC-!",
            title = "Crimson Run Sector",
            subtitle = "180m+ // Emergency Alarm Hallways",
            fieldNotes = "Bathymetric crimson strobe lighting saturates long straightaways. Entities in this sector exhibit heightened aggression and relentless pursuit speeds.",
            survivalTip = "Sprint between safe alcoves and use Tape Stabilizers when chromatic tearing blinds the lens."
        ),
        CodexLoreItem(
            id = BiomeType.VOID_STATIC.id,
            category = CATEGORY_BIOME,
            code = "ERR-99",
            title = "Fractured Null Void",
            subtitle = "300m+ // Non-Euclidean Signal Collapse",
            fieldNotes = "Architecture degrades into corrupted wireframe geometry and missing-texture walls. Non-Euclidean spatial shifts occur frequently right behind the camera.",
            survivalTip = "Expect exits you just passed to seal shut. Trust your Signal Compass over visual memory."
        )
    )

    val anomalies: List<CodexLoreItem> = listOf(
        CodexLoreItem(
            id = AnomalyType.STALKER.id,
            category = CATEGORY_ANOMALY,
            code = "ENT-01",
            title = "Bacteria Stalker",
            subtitle = "CLASS III // Acoustic & Sight Pursuer",
            fieldNotes = "A tall, spindly silhouette of tangled dark filaments. Patrols quiet corridors until provoked by direct line-of-sight or sprinting footsteps.",
            survivalTip = "Break line-of-sight around multiple corners and walk quietly to slip its pursuit."
        ),
        CodexLoreItem(
            id = AnomalyType.WATCHER.id,
            category = CATEGORY_ANOMALY,
            code = "ENT-02",
            title = "The Watcher",
            subtitle = "CLASS IV // Photonic Gaze-Locked",
            fieldNotes = "Pale multi-eyed figure emitting faint bioluminescent static. Freezes completely while held in the active beam of your camcorder flashlight.",
            survivalTip = "Never turn your back without creating distance first—if your battery dies, it surges."
        ),
        CodexLoreItem(
            id = AnomalyType.PHANTOM.id,
            category = CATEGORY_ANOMALY,
            code = "ENT-03",
            title = "Static Phantom",
            subtitle = "CLASS IV // VHS Phase-Shift Entity",
            fieldNotes = "Exists between magnetic tape frames. Teleports along nearby hallways during VHS tracking spikes while draining Signal Sanity on sight.",
            survivalTip = "Drink Almond Water or trigger a Tape Stabilizer to suppress heavy interference."
        ),
        CodexLoreItem(
            id = AnomalyType.MIMIC.id,
            category = CATEGORY_ANOMALY,
            code = "ENT-04",
            title = "Lure Mimic",
            subtitle = "CLASS V // Deceptive Supply Ambush",
            fieldNotes = "Projects a distorted supply silhouette to bait desperate explorers deep into uncharted rooms before snapping into a high-speed lunge.",
            survivalTip = "Notice subtle erratic glitch jitter on distant pickups deep past 120 meters."
        )
    )
}
