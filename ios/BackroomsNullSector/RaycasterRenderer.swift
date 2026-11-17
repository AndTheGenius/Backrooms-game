import CoreGraphics
import Foundation

final class RaycasterRenderer {
    static let width = 240
    static let height = 135
    private static let halfH = height / 2
    private static let fovScale: Float = 0.66
    private static let maxRayDist: Float = 16.5

    private let textures: ProceduralTextures
    private var rawPixels = [UInt32](repeating: 0, count: width * height)
    private var postPixels = [UInt32](repeating: 0, count: width * height)
    private var zBuffer = [Float](repeating: 0, count: width)
    private let colorSpace = CGColorSpaceCreateDeviceRGB()

    init(textures: ProceduralTextures) {
        self.textures = textures
    }

    func renderFrame(
        world: InfiniteWorldGenerator,
        playerX: Float,
        playerY: Float,
        headingRad: Float,
        headBobOffsetPx: Float,
        flashlightOn: Bool,
        batteryPct: Float,
        healthPct: Float,
        sanityPct: Float,
        timeSec: Float,
        glitch: GlitchTelemetry,
        safetyMode: Bool,
        items: [WorldItem],
        anomalies: [AnomalyEntity]
    ) -> CGImage? {
        let dirX = cos(headingRad)
        let dirY = sin(headingRad)
        let planeX = -dirY * Self.fovScale
        let planeY = dirX * Self.fovScale

        let rayDirX0 = dirX - planeX
        let rayDirY0 = dirY - planeY
        let rayDirX1 = dirX + planeX
        let rayDirY1 = dirY + planeY

        let bobShift = max(-5, min(5, Int(headBobOffsetPx)))
        let horizonY = max(20, min(Self.height - 20, Self.halfH + bobShift))

        let flickerSeed = sin(timeSec * 37) * sin(timeSec * 19)
        let flashlightPower: Float
        if !flashlightOn || batteryPct <= 0.5 {
            flashlightPower = 0.18
        } else if batteryPct < 18 && flickerSeed > 0.55 {
            flashlightPower = 0.35
        } else if glitch.overallIntensity > 0.65 && flickerSeed > 0.72 {
            flashlightPower = 0.40
        } else {
            flashlightPower = 1.0
        }

        let currentBiome = world.getBiomeAt(wx: Int(floor(playerX)), wy: Int(floor(playerY)))
        var rng = SeededRNG(seed: UInt64(max(1, Int(timeSec * 1000))))

        // 1. Floor & Ceiling
        for y in 0..<Self.height {
            let isFloor = y >= horizonY
            let p = max(1, isFloor ? (y - horizonY) : (horizonY - y))
            let rowDistance = min(Self.maxRayDist, Float(Self.halfH) / Float(p))
            let stepX = rowDistance * (rayDirX1 - rayDirX0) / Float(Self.width)
            let stepY = rowDistance * (rayDirY1 - rayDirY0) / Float(Self.width)
            var fx = playerX + rowDistance * rayDirX0
            var fy = playerY + rowDistance * rayDirY0
            let distFog = max(0.03, min(1.0, 1.0 - rowDistance / Self.maxRayDist))
            let rowOff = y * Self.width

            for x in 0..<Self.width {
                let cx = Int(floor(fx))
                let cy = Int(floor(fy))
                let tx = Int((fx - Float(cx)) * 64) & 63
                let ty = Int((fy - Float(cy)) * 64) & 63
                let beamDist = abs(Float(x - Self.width / 2)) / Float(Self.width / 2)
                let spot = max(0, min(1, 1.0 - beamDist * 1.15)) * flashlightPower
                let light = max(0.03, min(1.0, distFog * (0.28 + 0.72 * spot)))
                let base = isFloor
                    ? sampleFloor(currentBiome, cx, cy, tx, ty, timeSec)
                    : sampleCeiling(currentBiome, cx, cy, tx, ty, timeSec)
                rawPixels[rowOff + x] = shadeRGBA(base, light, currentBiome == .redCorridor)
                fx += stepX
                fy += stepY
            }
        }

        // 2. Walls (DDA)
        for x in 0..<Self.width {
            let cameraX = 2.0 * Float(x) / Float(Self.width) - 1.0
            let rayDirX = dirX + planeX * cameraX
            let rayDirY = dirY + planeY * cameraX

            var mapX = Int(floor(playerX))
            var mapY = Int(floor(playerY))
            let deltaDistX: Float = rayDirX == 0 ? 1e30 : abs(1.0 / rayDirX)
            let deltaDistY: Float = rayDirY == 0 ? 1e30 : abs(1.0 / rayDirY)

            let stepX: Int
            var sideDistX: Float
            if rayDirX < 0 { stepX = -1; sideDistX = (playerX - Float(mapX)) * deltaDistX }
            else { stepX = 1; sideDistX = (Float(mapX) + 1.0 - playerX) * deltaDistX }

            let stepY: Int
            var sideDistY: Float
            if rayDirY < 0 { stepY = -1; sideDistY = (playerY - Float(mapY)) * deltaDistY }
            else { stepY = 1; sideDistY = (Float(mapY) + 1.0 - playerY) * deltaDistY }

            var hitCell: CellType = .empty
            var side = 0
            var steps = 0
            while hitCell == .empty && steps < 28 {
                if sideDistX < sideDistY {
                    sideDistX += deltaDistX; mapX += stepX; side = 0
                } else {
                    sideDistY += deltaDistY; mapY += stepY; side = 1
                }
                if sideDistX > Self.maxRayDist && sideDistY > Self.maxRayDist { break }
                hitCell = world.getCell(wx: mapX, wy: mapY)
                steps += 1
            }

            let perpDist = hitCell == .empty ? Self.maxRayDist : max(0.08, side == 0 ? (sideDistX - deltaDistX) : (sideDistY - deltaDistY))
            zBuffer[x] = perpDist

            if hitCell != .empty && perpDist < Self.maxRayDist {
                let lineHeight = Int(Float(Self.height) / perpDist)
                let drawStart = max(0, -lineHeight / 2 + horizonY)
                let drawEnd = min(Self.height - 1, lineHeight / 2 + horizonY)
                var wallX = side == 0 ? (playerY + perpDist * rayDirY) : (playerX + perpDist * rayDirX)
                wallX -= floor(wallX)
                var texX = Int(wallX * 64) & 63
                if side == 0 && rayDirX > 0 { texX = 63 - texX }
                if side == 1 && rayDirY < 0 { texX = 63 - texX }

                let wallBiome = world.getBiomeAt(wx: mapX, wy: mapY)
                let tex = hitCell == .glitchWall ? textures.glitchWallTexture : textures.wallTextures[wallBiome]!

                let beamDist = abs(Float(x - Self.width / 2)) / Float(Self.width / 2)
                let spot = max(0, min(1, 1.0 - beamDist * 1.1)) * flashlightPower
                let distFog = max(0.04, min(1.0, 1.0 - perpDist / Self.maxRayDist))
                let sideShade: Float = side == 1 ? 0.78 : 1.0
                let light = max(0.04, min(1.0, distFog * sideShade * (0.28 + 0.72 * spot)))

                let stepTex = Float(ProceduralTextures.texSize) / Float(max(1, lineHeight))
                var texPos = Float(drawStart - horizonY + lineHeight / 2) * stepTex

                for y in drawStart...drawEnd {
                    let texY = Int(texPos) & 63
                    texPos += stepTex
                    rawPixels[y * Self.width + x] = shadeRGBA(tex[texY * 64 + texX], light, wallBiome == .redCorridor)
                }
            }
        }

        // 3. Z-Sorted Billboards
        var sprites: [(isAnomaly: Bool, idx: Int, d2: Float)] = []
        for (i, item) in items.enumerated() where !item.collected {
            let dx = item.x - playerX, dy = item.y - playerY
            let d2 = dx * dx + dy * dy
            if d2 < Self.maxRayDist * Self.maxRayDist { sprites.append((false, i, d2)) }
        }
        for (i, a) in anomalies.enumerated() {
            let dx = a.x - playerX, dy = a.y - playerY
            let d2 = dx * dx + dy * dy
            if d2 < Self.maxRayDist * Self.maxRayDist { sprites.append((true, i, d2)) }
        }
        sprites.sort { $0.d2 > $1.d2 }

        let invDet = 1.0 / (planeX * dirY - dirX * planeY)
        for s in sprites {
            let sx: Float, sy: Float, scaleW: Float, scaleH: Float, vMovePx: Float
            let tex: [UInt32]
            let emissive: Bool

            if !s.isAnomaly {
                let item = items[s.idx]
                sx = item.x - playerX; sy = item.y - playerY
                tex = textures.itemTextures[item.type]!
                scaleW = 0.42; scaleH = 0.42; vMovePx = 42
                emissive = item.type == .almondWater
            } else {
                let a = anomalies[s.idx]
                sx = a.x - playerX; sy = a.y - playerY
                tex = (a.type == .mimic && !a.mimicRevealed)
                    ? textures.itemTextures[.tapeStabilizer]!
                    : textures.anomalyTextures[a.type]!
                scaleW = a.type == .stalker ? 0.95 : 0.78
                scaleH = a.type == .stalker ? 1.05 : 0.90
                vMovePx = a.type == .phantom ? (sin(timeSec * 6) * 8) : 8
                emissive = a.type == .watcher || a.type == .phantom
            }

            let transformX = invDet * (dirY * sx - dirX * sy)
            let transformY = invDet * (-planeY * sx + planeX * sy)
            guard transformY > 0.18 && transformY < Self.maxRayDist else { continue }

            let screenX = Int(Float(Self.width / 2) * (1.0 + transformX / transformY))
            let vOff = Int(vMovePx / transformY)
            let spriteH = min(Self.height * 2, Int(abs((Float(Self.height) / transformY) * scaleH)))
            let spriteW = min(Self.width * 2, Int(abs((Float(Self.height) / transformY) * scaleW)))
            let startY = max(0, -spriteH / 2 + horizonY + vOff)
            let endY = min(Self.height - 1, spriteH / 2 + horizonY + vOff)
            let startX = max(0, -spriteW / 2 + screenX)
            let endX = min(Self.width - 1, spriteW / 2 + screenX)

            let distFog = max(0.08, min(1.0, 1.0 - transformY / Self.maxRayDist))
            let spriteLight = emissive ? max(distFog, 0.55) : (distFog * max(0.15, 0.35 + 0.65 * flashlightPower))

            if startX <= endX && startY <= endY {
                for stripe in startX...endX where transformY < zBuffer[stripe] {
                    let texX = max(0, min(63, (stripe - (-spriteW / 2 + screenX)) * 64 / max(1, spriteW)))
                    for y in startY...endY {
                        let d = y - vOff - horizonY + spriteH / 2
                        let texY = max(0, min(63, d * 64 / max(1, spriteH)))
                        let c = tex[texY * 64 + texX]
                        if (c >> 24) & 0xFF > 40 {
                            rawPixels[y * Self.width + stripe] = shadeRGBA(c, spriteLight, false)
                        }
                    }
                }
            }
        }

        // 4. VHS Post-Process
        applyVhsPostProcess(glitch: glitch, healthPct: healthPct, sanityPct: sanityPct, timeSec: timeSec, safetyMode: safetyMode, rng: &rng)

        let data = Data(bytes: postPixels, count: postPixels.count * 4)
        guard let provider = CGDataProvider(data: data as CFData) else { return nil }
        return CGImage(
            width: Self.width,
            height: Self.height,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: Self.width * 4,
            space: colorSpace,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider,
            decode: nil,
            shouldInterpolate: false,
            intent: .defaultIntent
        )
    }

    private func sampleFloor(_ biome: BiomeType, _ cx: Int, _ cy: Int, _ tx: Int, _ ty: Int, _ t: Float) -> UInt32 {
        let grain = ((tx * 17 + ty * 31) & 7) - 3
        switch biome {
        case .monoYellow: return ProceduralTextures.packRGBA(255, 138 + grain, 126 + grain, 76 + grain)
        case .poolrooms:
            let ripple = Int(sin((Float(tx) + t * 18) * 0.22) * 8)
            return (tx % 16 == 0 || ty % 16 == 0)
                ? ProceduralTextures.packRGBA(255, 36, 86, 90)
                : ProceduralTextures.packRGBA(255, 56 + ripple, 124 + ripple, 126 + ripple)
        case .pipeworks:
            return (tx % 8 < 2 || ty % 8 < 2)
                ? ProceduralTextures.packRGBA(255, 42, 44, 46)
                : ProceduralTextures.packRGBA(255, 64 + grain, 66 + grain, 68 + grain)
        case .redCorridor:
            return (28...35).contains(tx)
                ? ProceduralTextures.packRGBA(255, 96, 22, 20)
                : ProceduralTextures.packRGBA(255, 34 + grain, 18, 18)
        case .voidStatic:
            return (tx < 3 || ty < 3)
                ? ProceduralTextures.packRGBA(255, 120, 145, 110)
                : ProceduralTextures.packRGBA(255, 18 + grain, 20 + grain, 18 + grain)
        }
    }

    private func sampleCeiling(_ biome: BiomeType, _ cx: Int, _ cy: Int, _ tx: Int, _ ty: Int, _ t: Float) -> UInt32 {
        let lightPanel = ((cx + cy) & 1) == 0 && (18...46).contains(tx) && (14...50).contains(ty)
        switch biome {
        case .monoYellow:
            if lightPanel { return ProceduralTextures.packRGBA(255, 245, 240, 195) }
            return (tx == 0 || tx == 32 || ty == 0 || ty == 32)
                ? ProceduralTextures.packRGBA(255, 74, 72, 54)
                : ProceduralTextures.packRGBA(255, 118, 114, 86)
        case .poolrooms:
            return lightPanel ? ProceduralTextures.packRGBA(255, 180, 224, 218) : ProceduralTextures.packRGBA(255, 72, 112, 110)
        case .pipeworks:
            return lightPanel ? ProceduralTextures.packRGBA(255, 220, 168, 92) : ProceduralTextures.packRGBA(255, 34, 36, 38)
        case .redCorridor:
            return (lightPanel && sin(t * 6) > 0) ? ProceduralTextures.packRGBA(255, 245, 65, 48) : ProceduralTextures.packRGBA(255, 28, 12, 12)
        case .voidStatic:
            return ((tx ^ ty) & 15 == 0) ? ProceduralTextures.packRGBA(255, 95, 115, 90) : ProceduralTextures.packRGBA(255, 10, 12, 10)
        }
    }

    private func applyVhsPostProcess(glitch: GlitchTelemetry, healthPct: Float, sanityPct: Float, timeSec: Float, safetyMode: Bool, rng: inout SeededRNG) {
        let tearAmp = safetyMode ? glitch.trackingTearIntensity * 4 : glitch.trackingTearIntensity * 11
        let chroma = safetyMode ? min(2, glitch.chromaticShiftPx) : min(6, glitch.chromaticShiftPx)
        let snowChance = safetyMode ? glitch.staticSnowAmount * 0.45 : glitch.staticSnowAmount
        let damagePulse = max(0, min(1, (100 - healthPct) / 100))
        let sanityLow = max(0, min(1, (55 - sanityPct) / 55))
        let tearBandY = Int((sin(timeSec * 3.1) * 0.5 + 0.5) * Float(Self.height))

        for y in 0..<Self.height {
            let rowOff = y * Self.width
            let scanMul: Float = (y & 1 == 1) ? 0.87 : 1.0
            let bandDist = abs(y - tearBandY)
            let rowShift: Int = (tearAmp > 0.4 && bandDist < 14)
                ? Int(sin(Float(y) * 0.45 + timeSec * 45) * tearAmp * (1.0 - Float(bandDist) / 14.0))
                : 0
            let vy = abs(Float(y - Self.halfH)) / Float(Self.halfH)

            for x in 0..<Self.width {
                let srcX = max(0, min(Self.width - 1, x + rowShift))
                let center = rawPixels[rowOff + srcX]
                var r: Int, g: Int, b: Int
                if chroma > 0 {
                    let lx = max(0, srcX - chroma)
                    let rx = min(Self.width - 1, srcX + chroma)
                    r = Int(rawPixels[rowOff + lx] & 0xFF)
                    g = Int((center >> 8) & 0xFF)
                    b = Int((rawPixels[rowOff + rx] >> 16) & 0xFF)
                } else {
                    r = Int(center & 0xFF)
                    g = Int((center >> 8) & 0xFF)
                    b = Int((center >> 16) & 0xFF)
                }

                let vx = abs(Float(x - Self.width / 2)) / Float(Self.width / 2)
                let vigDist = hypot(vx * 0.85, vy * 0.85)
                let vig = max(0.28, min(1.0, 1.06 - vigDist * (0.42 + sanityLow * 0.22))) * scanMul
                r = Int(Float(r) * vig); g = Int(Float(g) * vig); b = Int(Float(b) * vig)

                if damagePulse > 0.08 && vigDist > 0.55 {
                    r = min(255, r + Int(damagePulse * (vigDist - 0.55) * 260))
                }
                if rng.nextFloat() < snowChance {
                    let grain = rng.nextInt(140, 240)
                    r = grain; g = grain; b = grain - 10
                }
                postPixels[rowOff + x] = ProceduralTextures.packRGBA(255, r, g, b)
            }
        }
    }

    @inline(__always)
    private func shadeRGBA(_ rgba: UInt32, _ light: Float, _ crimson: Bool) -> UInt32 {
        var r = Int(Float(rgba & 0xFF) * light)
        let g = Int(Float((rgba >> 8) & 0xFF) * light)
        let b = Int(Float((rgba >> 16) & 0xFF) * light)
        if crimson { r = min(255, Int(Float(r) * 1.15)) }
        return ProceduralTextures.packRGBA(255, r, g, b)
    }
}
