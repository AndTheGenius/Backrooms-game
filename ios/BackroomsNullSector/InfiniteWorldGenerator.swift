import Foundation

struct ChunkCoord: Hashable {
    let cx: Int
    let cy: Int
}

final class WorldChunk {
    let coord: ChunkCoord
    let biome: BiomeType
    var cells: [CellType]

    init(coord: ChunkCoord, biome: BiomeType, cells: [CellType]) {
        self.coord = coord
        self.biome = biome
        self.cells = cells
    }

    func get(_ lx: Int, _ ly: Int) -> CellType {
        cells[ly * InfiniteWorldGenerator.chunkSize + lx]
    }

    func set(_ lx: Int, _ ly: Int, _ type: CellType) {
        cells[ly * InfiniteWorldGenerator.chunkSize + lx] = type
    }
}

final class InfiniteWorldGenerator {
    static let chunkSize = 16
    static let metersPerCell: Float = 3.0

    private let worldSeed: UInt64
    private var chunks: [ChunkCoord: WorldChunk] = [:]

    init(worldSeed: UInt64) {
        self.worldSeed = worldSeed
    }

    func getOrGenerateChunk(cx: Int, cy: Int) -> WorldChunk {
        let coord = ChunkCoord(cx: cx, cy: cy)
        if let existing = chunks[coord] { return existing }
        let created = generateChunk(coord: coord)
        chunks[coord] = created
        return created
    }

    func getCell(wx: Int, wy: Int) -> CellType {
        let cx = Int(floor(Double(wx) / Double(Self.chunkSize)))
        let cy = Int(floor(Double(wy) / Double(Self.chunkSize)))
        let lx = ((wx % Self.chunkSize) + Self.chunkSize) % Self.chunkSize
        let ly = ((wy % Self.chunkSize) + Self.chunkSize) % Self.chunkSize
        return getOrGenerateChunk(cx: cx, cy: cy).get(lx, ly)
    }

    func getBiomeAt(wx: Int, wy: Int) -> BiomeType {
        let cx = Int(floor(Double(wx) / Double(Self.chunkSize)))
        let cy = Int(floor(Double(wy) / Double(Self.chunkSize)))
        return getOrGenerateChunk(cx: cx, cy: cy).biome
    }

    func isSolid(wx: Int, wy: Int) -> Bool {
        getCell(wx: wx, wy: wy) != .empty
    }

    private func setCellIfLoaded(wx: Int, wy: Int, type: CellType) {
        let cx = Int(floor(Double(wx) / Double(Self.chunkSize)))
        let cy = Int(floor(Double(wy) / Double(Self.chunkSize)))
        guard let chunk = chunks[ChunkCoord(cx: cx, cy: cy)] else { return }
        let lx = ((wx % Self.chunkSize) + Self.chunkSize) % Self.chunkSize
        let ly = ((wy % Self.chunkSize) + Self.chunkSize) % Self.chunkSize
        chunk.set(lx, ly, type)
    }

    private func hashChunk(cx: Int, cy: Int) -> UInt64 {
        var h = worldSeed
            ^ (UInt64(bitPattern: Int64(cx)) &* 0x9E3779B97F4A7C15)
            ^ (UInt64(bitPattern: Int64(cy)) &* 0xC2B2AE3D27D4EB4F)
        h = (h ^ (h >> 30)) &* 0xBF58476D1CE4E5B9
        h = (h ^ (h >> 27)) &* 0x94D049BB133111EB
        return h ^ (h >> 31)
    }

    private func selectBiome(cx: Int, cy: Int, rng: inout SeededRNG) -> BiomeType {
        if cx == 0 && cy == 0 { return .monoYellow }
        let distMeters = hypot(Float(cx * Self.chunkSize + 8), Float(cy * Self.chunkSize + 8)) * Self.metersPerCell
        let roll = rng.nextFloat()
        if distMeters < 55 { return roll < 0.82 ? .monoYellow : .poolrooms }
        if distMeters < 115 { return roll < 0.35 ? .monoYellow : (roll < 0.72 ? .poolrooms : .pipeworks) }
        if distMeters < 200 {
            if roll < 0.22 { return .poolrooms }
            if roll < 0.62 { return .pipeworks }
            return roll < 0.90 ? .redCorridor : .monoYellow
        }
        if distMeters < 320 { return roll < 0.25 ? .pipeworks : (roll < 0.68 ? .redCorridor : .voidStatic) }
        return roll < 0.35 ? .redCorridor : (roll < 0.82 ? .voidStatic : .pipeworks)
    }

    private func generateChunk(coord: ChunkCoord) -> WorldChunk {
        var rng = SeededRNG(seed: hashChunk(cx: coord.cx, cy: coord.cy))
        let biome = selectBiome(cx: coord.cx, cy: coord.cy, rng: &rng)
        var cells = [CellType](repeating: .wall, count: Self.chunkSize * Self.chunkSize)

        func setLocal(_ x: Int, _ y: Int, _ t: CellType) {
            if (0..<Self.chunkSize).contains(x) && (0..<Self.chunkSize).contains(y) {
                cells[y * Self.chunkSize + x] = t
            }
        }

        switch biome {
        case .monoYellow:
            for y in 1..<(Self.chunkSize - 1) {
                for x in 1..<(Self.chunkSize - 1) { setLocal(x, y, .empty) }
            }
            for _ in 0..<rng.nextInt(5, 10) {
                let horiz = rng.nextBool()
                let sx = rng.nextInt(2, Self.chunkSize - 3)
                let sy = rng.nextInt(2, Self.chunkSize - 3)
                let len = rng.nextInt(3, 7)
                for i in 0..<len where i != len / 2 {
                    let px = horiz ? min(sx + i, Self.chunkSize - 2) : sx
                    let py = horiz ? sy : min(sy + i, Self.chunkSize - 2)
                    setLocal(px, py, .wall)
                }
            }
            if rng.nextFloat() < 0.5 {
                for py in stride(from: 3, to: Self.chunkSize - 2, by: 4) {
                    for px in stride(from: 3, to: Self.chunkSize - 2, by: 4) {
                        setLocal(px, py, .pillar)
                    }
                }
            }
        case .poolrooms:
            for y in 1..<(Self.chunkSize - 1) {
                for x in 1..<(Self.chunkSize - 1) { setLocal(x, y, .empty) }
            }
            for py in stride(from: 3, to: Self.chunkSize - 2, by: 3) {
                for px in stride(from: 3, to: Self.chunkSize - 2, by: 3) where rng.nextFloat() < 0.72 {
                    setLocal(px, py, .pillar)
                }
            }
        case .pipeworks:
            for y in stride(from: 1, to: Self.chunkSize - 1, by: 3) {
                for x in 1..<(Self.chunkSize - 1) { setLocal(x, y, .empty) }
            }
            for x in stride(from: 1, to: Self.chunkSize - 1, by: 3) {
                for y in 1..<(Self.chunkSize - 1) { setLocal(x, y, .empty) }
            }
        case .redCorridor:
            for x in 1..<(Self.chunkSize - 1) {
                setLocal(x, 7, .empty); setLocal(x, 8, .empty)
            }
            for y in 1..<(Self.chunkSize - 1) {
                setLocal(7, y, .empty); setLocal(8, y, .empty)
            }
            for i in stride(from: 2, to: Self.chunkSize - 2, by: 3) {
                for y in 2...5 { setLocal(i, y, .empty) }
                for y in 10...13 { setLocal(i, y, .empty) }
            }
        case .voidStatic:
            for y in 1..<(Self.chunkSize - 1) {
                for x in 1..<(Self.chunkSize - 1) {
                    let r = rng.nextFloat()
                    setLocal(x, y, r < 0.72 ? .empty : (r < 0.88 ? .glitchWall : .pillar))
                }
            }
        }

        let nGate = gate(coord.cx, coord.cy, horizontal: true)
        let sGate = gate(coord.cx, coord.cy + 1, horizontal: true)
        let wGate = gate(coord.cx, coord.cy, horizontal: false)
        let eGate = gate(coord.cx + 1, coord.cy, horizontal: false)

        setLocal(nGate, 0, .empty); setLocal(min(nGate + 1, Self.chunkSize - 2), 0, .empty)
        setLocal(sGate, Self.chunkSize - 1, .empty); setLocal(min(sGate + 1, Self.chunkSize - 2), Self.chunkSize - 1, .empty)
        setLocal(0, wGate, .empty); setLocal(0, min(wGate + 1, Self.chunkSize - 2), .empty)
        setLocal(Self.chunkSize - 1, eGate, .empty); setLocal(Self.chunkSize - 1, min(eGate + 1, Self.chunkSize - 2), .empty)

        carvePath(&cells, nGate, 1, 8, 8)
        carvePath(&cells, sGate, Self.chunkSize - 2, 8, 8)
        carvePath(&cells, 1, wGate, 8, 8)
        carvePath(&cells, Self.chunkSize - 2, eGate, 8, 8)

        if coord.cx == 0 && coord.cy == 0 {
            for y in 0...4 { for x in 0...4 { setLocal(x, y, .empty) } }
            carvePath(&cells, 2, 2, 8, 8)
        }

        return WorldChunk(coord: coord, biome: biome, cells: cells)
    }

    private func gate(_ cx: Int, _ cy: Int, horizontal: Bool) -> Int {
        let tag: UInt64 = horizontal ? 0x517CC1B7 : 0xA24BAED4
        let h = hashChunk(cx: cx, cy: cy) ^ tag
        return 2 + Int((h >> 8) % UInt64(Self.chunkSize - 5))
    }

    private func carvePath(_ cells: inout [CellType], _ sx: Int, _ sy: Int, _ tx: Int, _ ty: Int) {
        var x = max(1, min(Self.chunkSize - 2, sx))
        var y = max(1, min(Self.chunkSize - 2, sy))
        while x != tx {
            cells[y * Self.chunkSize + x] = .empty
            x += (tx > x) ? 1 : -1
        }
        while y != ty {
            cells[y * Self.chunkSize + x] = .empty
            y += (ty > y) ? 1 : -1
        }
        cells[ty * Self.chunkSize + tx] = .empty
    }

    func shiftArchitectureBehindPlayer(
        playerX: Float,
        playerY: Float,
        dirX: Float,
        dirY: Float,
        anomalies: [AnomalyEntity],
        rng: inout SeededRNG
    ) -> Bool {
        let px = Int(floor(playerX))
        let py = Int(floor(playerY))
        var shifted = false
        for _ in 0..<10 {
            let dx = rng.nextInt(-9, 10)
            let dy = rng.nextInt(-9, 10)
            let dist = hypot(Float(dx), Float(dy))
            guard dist >= 3.5 && dist <= 9.5 else { continue }
            let dot = (Float(dx) * dirX + Float(dy) * dirY) / dist
            guard dot <= -0.35 else { continue }
            let wx = px + dx
            let wy = py + dy
            if (-2...2).contains(wx) && (-2...2).contains(wy) { continue }
            if anomalies.contains(where: { Int(floor($0.x)) == wx && Int(floor($0.y)) == wy }) { continue }
            let current = getCell(wx: wx, wy: wy)
            let next: CellType = current == .empty ? (rng.nextFloat() < 0.65 ? .glitchWall : .wall) : .empty
            setCellIfLoaded(wx: wx, wy: wy, type: next)
            shifted = true
            if rng.nextFloat() < 0.6 { return true }
        }
        return shifted
    }

    func pruneFarChunks(playerX: Float, playerY: Float, keepRadius: Int = 4) {
        let pcx = Int(floor(playerX / Float(Self.chunkSize)))
        let pcy = Int(floor(playerY / Float(Self.chunkSize)))
        chunks = chunks.filter { abs($0.key.cx - pcx) <= keepRadius && abs($0.key.cy - pcy) <= keepRadius }
    }
}
