import Foundation

struct SeededRNG {
    private var state: UInt64

    init(seed: UInt64) {
        self.state = seed == 0 ? 0x9E3779B97F4A7C15 : seed
    }

    mutating func nextUInt64() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }

    mutating func nextFloat() -> Float {
        return Float(nextUInt64() & 0xFFFFFF) / Float(0x1000000)
    }

    mutating func nextInt(_ lower: Int, _ upperExclusive: Int) -> Int {
        guard upperExclusive > lower else { return lower }
        let span = UInt64(upperExclusive - lower)
        return lower + Int(nextUInt64() % span)
    }

    mutating func nextBool() -> Bool {
        return (nextUInt64() & 1) == 0
    }
}

final class ProceduralTextures {
    static let texSize = 64

    let wallTextures: [BiomeType: [UInt32]]
    let glitchWallTexture: [UInt32]
    let itemTextures: [PickupType: [UInt32]]
    let anomalyTextures: [AnomalyType: [UInt32]]

    init() {
        var walls: [BiomeType: [UInt32]] = [:]
        for (idx, b) in BiomeType.allCases.enumerated() {
            walls[b] = Self.generateWallTexture(biome: b, seed: UInt64(idx * 9973 + 42))
        }
        self.wallTextures = walls
        self.glitchWallTexture = Self.generateGlitchWallTexture()

        var items: [PickupType: [UInt32]] = [:]
        for p in PickupType.allCases {
            items[p] = Self.generateItemTexture(type: p)
        }
        self.itemTextures = items

        var anomalies: [AnomalyType: [UInt32]] = [:]
        for (idx, a) in AnomalyType.allCases.enumerated() {
            anomalies[a] = Self.generateAnomalyTexture(type: a, seed: UInt64(idx * 4099 + 77))
        }
        self.anomalyTextures = anomalies
    }

    @inline(__always)
    static func packRGBA(_ a: Int, _ r: Int, _ g: Int, _ b: Int) -> UInt32 {
        let ca = UInt32(max(0, min(255, a)))
        let cr = UInt32(max(0, min(255, r)))
        let cg = UInt32(max(0, min(255, g)))
        let cb = UInt32(max(0, min(255, b)))
        return (ca << 24) | (cb << 16) | (cg << 8) | cr
    }

    private static func generateWallTexture(biome: BiomeType, seed: UInt64) -> [UInt32] {
        var pixels = [UInt32](repeating: 0, count: texSize * texSize)
        var rng = SeededRNG(seed: seed)
        for y in 0..<texSize {
            for x in 0..<texSize {
                let n = rng.nextInt(-8, 9)
                let color: UInt32
                switch biome {
                case .monoYellow:
                    let stripe = (x % 8 < 2) ? -12 : 6
                    let arrow = ((x + y) % 16 == 0 || (x - y + 64) % 16 == 0) ? -8 : 0
                    let damp = y > 45 ? -((y - 45) * 2) : 0
                    if y >= 57 {
                        color = packRGBA(255, 78 + n, 64 + n, 38 + n)
                    } else {
                        color = packRGBA(255, 182 + stripe + arrow + damp + n, 162 + stripe + arrow + damp + n, 84 + stripe / 2 + damp + n)
                    }
                case .poolrooms:
                    if (x % 16 == 0) || (y % 16 == 0) {
                        color = packRGBA(255, 42 + n, 72 + n, 74 + n)
                    } else if y > 50 {
                        color = packRGBA(255, 58 + n, 128 + n, 126 + n)
                    } else {
                        let sheen = ((x + y) % 32 < 4) ? 14 : 0
                        color = packRGBA(255, 92 + sheen + n, 168 + sheen + n, 160 + sheen + n)
                    }
                case .pipeworks:
                    if (20...29).contains(y) {
                        let cyl = 18 - abs(y - 24) * 3
                        color = packRGBA(255, 96 + cyl + n, 74 + cyl / 2 + n, 58 + n)
                    } else if x == 0 || x == 32 || y == 0 {
                        color = packRGBA(255, 48 + n, 52 + n, 54 + n)
                    } else {
                        let grime = y > 48 ? -18 : 0
                        color = packRGBA(255, 86 + grime + n, 90 + grime + n, 92 + grime + n)
                    }
                case .redCorridor:
                    let rib = (x % 16 < 3) ? -35 : 8
                    if (52...58).contains(y) && ((x / 8 + y / 4) % 2 == 0) {
                        color = packRGBA(255, 195 + n, 52 + n, 32 + n)
                    } else {
                        color = packRGBA(255, 138 + rib + n, 26 + rib / 3 + n, 24 + n)
                    }
                case .voidStatic:
                    if x < 2 || x >= 62 || y < 2 || y >= 62 || abs(x - y) < 2 || abs(x + y - 63) < 2 {
                        color = packRGBA(255, 190, 210, 175)
                    } else {
                        let s = rng.nextInt(10, 65)
                        color = packRGBA(255, s, s + 8, s)
                    }
                }
                pixels[y * texSize + x] = color
            }
        }
        return pixels
    }

    private static func generateGlitchWallTexture() -> [UInt32] {
        var pixels = [UInt32](repeating: 0, count: texSize * texSize)
        var rng = SeededRNG(seed: 1337)
        for y in 0..<texSize {
            for x in 0..<texSize {
                let checker = ((x / 8) + (y / 8)) % 2 == 0
                let noise = rng.nextInt(0, 45)
                if y % 7 == 0 {
                    pixels[y * texSize + x] = packRGBA(255, 220, 45, 90)
                } else if checker {
                    pixels[y * texSize + x] = packRGBA(255, 28 + noise, 16, 36 + noise)
                } else {
                    pixels[y * texSize + x] = packRGBA(255, 150 + noise, 190, 95)
                }
            }
        }
        return pixels
    }

    private static func generateItemTexture(type: PickupType) -> [UInt32] {
        var pixels = [UInt32](repeating: 0, count: texSize * texSize)
        for y in 0..<texSize {
            for x in 0..<texSize {
                var color: UInt32 = 0
                switch type {
                case .battery:
                    if (27...36).contains(x) && (16...19).contains(y) { color = packRGBA(255, 215, 215, 220) }
                    else if (23...40).contains(x) && (20...28).contains(y) { color = packRGBA(255, 218, 165, 50) }
                    else if (26...37).contains(x) && (36...44).contains(y) { color = packRGBA(255, 232, 200, 70) }
                    else if (23...40).contains(x) && (29...52).contains(y) { color = packRGBA(255, 36, 40, 44) }
                case .almondWater:
                    if (28...35).contains(x) && (15...20).contains(y) { color = packRGBA(255, 200, 195, 175) }
                    else if (29...34).contains(x) && (21...25).contains(y) { color = packRGBA(255, 130, 195, 165) }
                    else if (22...41).contains(x) && (34...44).contains(y) { color = packRGBA(255, 232, 226, 204) }
                    else if (22...41).contains(x) && (26...53).contains(y) { color = packRGBA(255, 102, 184, 148) }
                case .tapeStabilizer:
                    if (15...48).contains(x) && (22...46).contains(y) {
                        let r1 = (x - 24)*(x - 24) + (y - 34)*(y - 34) <= 20
                        let r2 = (x - 39)*(x - 39) + (y - 34)*(y - 34) <= 20
                        if r1 || r2 { color = packRGBA(255, 210, 215, 200) }
                        else if (24...28).contains(y) { color = packRGBA(255, 210, 74, 58) }
                        else { color = packRGBA(255, 34, 36, 40) }
                    }
                }
                let cx = x - 32
                let cy = y - 34
                if color != 0 {
                    pixels[y * texSize + x] = color
                } else if (190...260).contains(cx * cx + cy * cy) {
                    pixels[y * texSize + x] = packRGBA(90, 220, 210, 140)
                }
            }
        }
        return pixels
    }

    private static func generateAnomalyTexture(type: AnomalyType, seed: UInt64) -> [UInt32] {
        var pixels = [UInt32](repeating: 0, count: texSize * texSize)
        var rng = SeededRNG(seed: seed)
        for y in 0..<texSize {
            for x in 0..<texSize {
                let nx = Float(x - 32) / 32.0
                let ny = Float(y - 32) / 32.0
                var color: UInt32 = 0
                switch type {
                case .stalker:
                    let torso = abs(nx) < (0.13 + 0.04 * sin(Float(y) * 0.4)) && (-0.68...0.35).contains(ny)
                    let head = nx * nx + (ny + 0.72) * (ny + 0.72) < 0.025
                    let lArm = abs(nx + 0.25 + ny * 0.12) < 0.055 && (-0.5...0.6).contains(ny)
                    let rArm = abs(nx - 0.25 - ny * 0.12) < 0.055 && (-0.5...0.6).contains(ny)
                    let lLeg = abs(nx + 0.10) < 0.05 && (0.30...0.95).contains(ny)
                    let rLeg = abs(nx - 0.10) < 0.05 && (0.30...0.95).contains(ny)
                    if (x == 30 && y == 9) || (x == 34 && y == 9) {
                        color = packRGBA(255, 255, 95, 72)
                    } else if head || torso || lArm || rArm || lLeg || rLeg {
                        let v = rng.nextInt(6, 22)
                        color = packRGBA(255, v, v, v)
                    }
                case .watcher:
                    let body = abs(nx) < 0.20 * (1.0 - ny * 0.15) && (-0.55...0.92).contains(ny)
                    let head = nx * nx * 1.4 + (ny + 0.68) * (ny + 0.68) < 0.042
                    let eyes = ((28...29).contains(x) && (8...9).contains(y)) ||
                               ((34...35).contains(x) && (8...9).contains(y)) ||
                               ((31...32).contains(x) && (12...13).contains(y))
                    if eyes {
                        color = packRGBA(255, 240, 255, 225)
                    } else if head || body {
                        let shade = 145 - Int(abs(nx) * 180) + rng.nextInt(-10, 10)
                        color = packRGBA(255, shade, shade + 6, shade - 4)
                    }
                case .phantom:
                    let rowOffset = Int(sin(Float(y) * 0.65) * 6.0)
                    let gx = Float(x + rowOffset - 32) / 32.0
                    let inSil = abs(gx) < 0.24 && (-0.75...0.85).contains(ny) && (y % 3 != 0)
                    if inSil && (10...12).contains(y) && abs(gx) < 0.14 {
                        color = packRGBA(255, 210, 255, 245)
                    } else if inSil {
                        let c = rng.nextInt(110, 215)
                        color = packRGBA(215, c - 30, c, c + 15)
                    }
                case .mimic:
                    let hull = nx * nx + ny * ny < 0.42 && ny > -0.6
                    let teeth = (-0.05...0.18).contains(ny) && abs(nx) < 0.36 && ((x + y) % 4 == 0)
                    let eyes = ((25...27).contains(x) && (18...20).contains(y)) || ((37...39).contains(x) && (18...20).contains(y))
                    if eyes { color = packRGBA(255, 255, 215, 80) }
                    else if teeth { color = packRGBA(255, 235, 225, 200) }
                    else if hull { color = packRGBA(255, 42 + rng.nextInt(0, 25), 14, 16) }
                }
                pixels[y * texSize + x] = color
            }
        }
        return pixels
    }
}
