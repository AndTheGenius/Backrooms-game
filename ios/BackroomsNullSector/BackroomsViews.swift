import SwiftUI

private enum LiminalPalette {
    static let voidBlack = Color(red: 0.043, green: 0.047, blue: 0.039)
    static let camcorderDark = Color(red: 0.078, green: 0.086, blue: 0.067)
    static let cassetteSlate = Color(red: 0.122, green: 0.133, blue: 0.102)
    static let mustard = Color(red: 0.847, green: 0.765, blue: 0.416)
    static let poolCyan = Color(red: 0.478, green: 0.659, blue: 0.600)
    static let recCrimson = Color(red: 0.788, green: 0.290, blue: 0.220)
    static let phosphorWhite = Color(red: 0.918, green: 0.902, blue: 0.824)
    static let phosphorMuted = Color(red: 0.655, green: 0.639, blue: 0.561)
    static let hairline = Color(red: 0.239, green: 0.259, blue: 0.200)
}

@main
struct BackroomsNullSectorApp: App {
    @StateObject private var store = BackroomsStore()

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $store.path) {
                TitleView(store: store)
                    .navigationDestination(for: BackroomsScreen.self) { screen in
                        switch screen {
                        case .title: TitleView(store: store)
                        case .play: PlayView(store: store)
                        case .debrief: DebriefView(store: store)
                        case .archive: ArchiveView(store: store)
                        case .codex: CodexView(store: store)
                        case .settings: SettingsView(store: store)
                        }
                    }
            }
            .preferredColorScheme(.dark)
            .tint(LiminalPalette.mustard)
        }
    }
}

struct TitleView: View {
    @ObservedObject var store: BackroomsStore

    var body: some View {
        ZStack {
            LiminalPalette.voidBlack.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Circle().fill(LiminalPalette.recCrimson).frame(width: 10, height: 10)
                    Text("HANDYCAM VH-98 // TAPE ARCHIVE")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(LiminalPalette.phosphorMuted)
                    Spacer()
                    Image(systemName: "video.fill").foregroundColor(LiminalPalette.mustard)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("TAPE // 04-NULL")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(LiminalPalette.mustard)
                    Text("BACKROOMS:\nNULL SECTOR")
                        .font(.system(size: 34, weight: .bold, design: .monospaced))
                        .foregroundColor(LiminalPalette.phosphorWhite)
                    Text("Venture infinitely beyond spawn. As distance grows, architecture warps, anomalies hunt relentlessly, and signal decay accelerates.")
                        .font(.footnote)
                        .foregroundColor(LiminalPalette.phosphorMuted)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(LiminalPalette.camcorderDark)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(LiminalPalette.hairline, lineWidth: 1))

                HStack(spacing: 12) {
                    StatBox(title: "FURTHEST DEPTH", value: String(format: "%.1fm", store.bestDistanceMeters), accent: LiminalPalette.mustard)
                    StatBox(title: "TAPES LOGGED", value: "\(store.runs.count)", accent: LiminalPalette.poolCyan)
                }

                Spacer()

                VStack(spacing: 10) {
                    Button {
                        store.startNewTape()
                    } label: {
                        Label("INSERT NEW TAPE", systemImage: "play.fill")
                            .font(.system(.headline, design: .monospaced))
                            .frame(maxWidth: .infinity, minHeight: 54)
                            .background(LiminalPalette.mustard)
                            .foregroundColor(.black)
                            .cornerRadius(4)
                    }

                    HStack(spacing: 10) {
                        Button { store.path.append(.archive) } label: {
                            Label("ARCHIVE", systemImage: "tray.full")
                                .font(.system(.subheadline, design: .monospaced))
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(LiminalPalette.camcorderDark)
                                .foregroundColor(LiminalPalette.phosphorWhite)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
                        }
                        Button { store.path.append(.codex) } label: {
                            Label("CODEX", systemImage: "book.closed")
                                .font(.system(.subheadline, design: .monospaced))
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(LiminalPalette.camcorderDark)
                                .foregroundColor(LiminalPalette.phosphorWhite)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
                        }
                    }

                    Button { store.path.append(.settings) } label: {
                        Label("CAMCORDER SETUP", systemImage: "gearshape")
                            .font(.system(.subheadline, design: .monospaced))
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(LiminalPalette.camcorderDark)
                            .foregroundColor(LiminalPalette.phosphorWhite)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
                    }
                }
            }
            .padding(20)
        }
        .navigationBarHidden(true)
    }
}

struct PlayView: View {
    @ObservedObject var store: BackroomsStore
    @State private var lastDragX: CGFloat?

    var body: some View {
        GeometryReader { geo in
            let isWide = geo.size.width > geo.size.height
            ZStack {
                LiminalPalette.voidBlack.ignoresSafeArea()
                if isWide {
                    viewfinder
                        .overlay(alignment: .bottom) {
                            HStack {
                                JoystickPad { f, r in store.setMoveInput(forward: f, right: r) }
                                    .frame(width: 124, height: 124)
                                Spacer()
                                ActionCluster(store: store)
                            }
                            .padding(20)
                        }
                } else {
                    VStack(spacing: 0) {
                        viewfinder.frame(height: geo.size.height * 0.60)
                        HStack(spacing: 14) {
                            JoystickPad { f, r in store.setMoveInput(forward: f, right: r) }
                                .frame(width: 136, height: 136)
                            VStack(spacing: 10) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(LiminalPalette.cassetteSlate)
                                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
                                    .overlay(
                                        Text("◀  DRAG TO TURN  ▶")
                                            .font(.system(.caption, design: .monospaced))
                                            .foregroundColor(LiminalPalette.phosphorMuted)
                                    )
                                    .gesture(
                                        DragGesture(minimumDistance: 0)
                                            .onChanged { val in
                                                let dx = val.location.x - (lastDragX ?? val.location.x)
                                                lastDragX = val.location.x
                                                store.addLookDeltaPx(Float(dx))
                                            }
                                            .onEnded { _ in lastDragX = nil }
                                    )
                                ActionCluster(store: store)
                            }
                        }
                        .padding(14)
                        .frame(maxHeight: .infinity)
                        .background(LiminalPalette.camcorderDark)
                    }
                }

                if store.hudState.isPaused {
                    PauseSheet(store: store)
                }
            }
        }
        .navigationBarBackButtonHidden(true)
    }

    private var viewfinder: some View {
        ZStack {
            Color.black
            if let img = store.frameImage {
                Image(decorative: img, scale: 1.0)
                    .interpolation(.none)
                    .resizable()
            }
            VStack {
                HStack {
                    Text(store.hudState.glitch.corruptTimestamp ? "REC // ERR-??:??" : "REC // \(formatDuration(Int(store.hudState.elapsedSeconds)))")
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundColor(LiminalPalette.phosphorWhite)
                    Spacer()
                    Text(String(format: "DIST %.1fm", store.hudState.distanceFromSpawnMeters))
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundColor(LiminalPalette.mustard)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.black.opacity(0.6))
                    Button { store.pauseGame() } label: {
                        Image(systemName: "pause.fill")
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.5))
                            .foregroundColor(LiminalPalette.phosphorWhite)
                    }
                }
                HStack {
                    Text("\(store.hudState.currentBiome.sectorCode) // \(store.hudState.currentBiome.displayName.uppercased())")
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundColor(LiminalPalette.phosphorWhite.opacity(0.85))
                    Spacer()
                    Text(store.hudState.threatTier.rawValue)
                        .font(.system(.caption2, design: .monospaced))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(LiminalPalette.recCrimson.opacity(0.3))
                        .foregroundColor(LiminalPalette.mustard)
                }
                Spacer()
                if let banner = store.hudState.glitch.warningBanner {
                    Text(banner)
                        .font(.system(.caption, design: .monospaced))
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(LiminalPalette.recCrimson.opacity(0.85))
                        .foregroundColor(.white)
                }
                Spacer()
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        HudMeter(label: "VIT", value: store.hudState.health / 100, color: LiminalPalette.recCrimson)
                        HudMeter(label: "SIG", value: store.hudState.sanity / 100, color: LiminalPalette.poolCyan)
                        HudMeter(label: "BAT", value: store.hudState.battery / 100, color: LiminalPalette.mustard)
                        HudMeter(label: "STM", value: store.hudState.stamina / 100, color: .green)
                    }
                    .frame(width: 156)
                    .padding(8)
                    .background(Color.black.opacity(0.55))
                    Spacer()
                    if let toast = store.hudState.statusToast {
                        Text(toast)
                            .font(.system(.caption2, design: .monospaced))
                            .foregroundColor(LiminalPalette.mustard)
                            .padding(6)
                            .background(Color.black.opacity(0.75))
                    }
                }
            }
            .padding(12)
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { val in
                    let dx = val.location.x - (lastDragX ?? val.location.x)
                    lastDragX = val.location.x
                    store.addLookDeltaPx(Float(dx))
                }
                .onEnded { _ in lastDragX = nil }
        )
    }
}

private struct JoystickPad: View {
    let onChange: (Float, Float) -> Void
    @State private var knob: CGSize = .zero

    var body: some View {
        GeometryReader { geo in
            let r = min(geo.size.width, geo.size.height) * 0.38
            ZStack {
                Circle().fill(LiminalPalette.cassetteSlate)
                Circle().stroke(LiminalPalette.hairline, lineWidth: 2)
                Circle().fill(LiminalPalette.mustard)
                    .frame(width: r * 0.9, height: r * 0.9)
                    .offset(knob)
            }
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { val in
                        let cx = geo.size.width / 2, cy = geo.size.height / 2
                        var dx = val.location.x - cx, dy = val.location.y - cy
                        let dist = hypot(dx, dy)
                        if dist > r { dx = dx / dist * r; dy = dy / dist * r }
                        knob = CGSize(width: dx, height: dy)
                        onChange(Float(-dy / r), Float(dx / r))
                    }
                    .onEnded { _ in
                        knob = .zero
                        onChange(0, 0)
                    }
            )
        }
    }
}

private struct ActionCluster: View {
    @ObservedObject var store: BackroomsStore

    var body: some View {
        HStack(spacing: 8) {
            Button { store.useAlmondWater() } label: {
                VStack {
                    Image(systemName: "drop.fill")
                    Text("x\(store.hudState.almondWaterCount)").font(.caption2)
                }
                .frame(width: 52, height: 52)
                .background(LiminalPalette.cassetteSlate)
                .foregroundColor(LiminalPalette.poolCyan)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.poolCyan))
            }
            .disabled(store.hudState.almondWaterCount <= 0)

            Button { store.toggleFlashlight() } label: {
                Image(systemName: store.hudState.flashlightOn ? "flashlight.on.fill" : "flashlight.off.fill")
                    .frame(width: 52, height: 52)
                    .background(store.hudState.flashlightOn ? LiminalPalette.mustard : LiminalPalette.cassetteSlate)
                    .foregroundColor(store.hudState.flashlightOn ? .black : LiminalPalette.phosphorWhite)
                    .cornerRadius(4)
            }

            Text("SPRINT")
                .font(.system(.caption, design: .monospaced))
                .frame(width: 68, height: 52)
                .background(LiminalPalette.cassetteSlate)
                .foregroundColor(LiminalPalette.phosphorWhite)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in store.setSprinting(true) }
                        .onEnded { _ in store.setSprinting(false) }
                )
        }
    }
}

private struct PauseSheet: View {
    @ObservedObject var store: BackroomsStore
    var body: some View {
        Color.black.opacity(0.75).ignoresSafeArea()
        VStack(spacing: 14) {
            Text("TAPE PAUSED").font(.system(.title2, design: .monospaced)).foregroundColor(LiminalPalette.mustard)
            Text(String(format: "Depth: %.1fm | Peak: %.1fm", store.hudState.distanceFromSpawnMeters, store.hudState.peakDistanceMeters))
                .font(.caption).foregroundColor(LiminalPalette.phosphorMuted)
            Button("RESUME TAPE") { store.resumeGame() }
                .frame(maxWidth: .infinity, minHeight: 48).background(LiminalPalette.mustard).foregroundColor(.black).cornerRadius(4)
            Button("EJECT TAPE") { store.ejectTapeEarly() }
                .frame(maxWidth: .infinity, minHeight: 48).foregroundColor(LiminalPalette.recCrimson)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.recCrimson))
        }
        .padding(22).background(LiminalPalette.camcorderDark)
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(LiminalPalette.mustard))
        .padding(24)
    }
}

struct DebriefView: View {
    @ObservedObject var store: BackroomsStore
    var body: some View {
        let run = store.lastSavedRun
        VStack(alignment: .leading, spacing: 14) {
            Text("NO SIGNAL // TAPE SEVERED").font(.system(.headline, design: .monospaced)).foregroundColor(LiminalPalette.recCrimson)
            if store.hudState.isNewRecord {
                Text("NEW DEPTH RECORD").font(.system(.caption, design: .monospaced)).padding(6).background(LiminalPalette.mustard.opacity(0.2)).foregroundColor(LiminalPalette.mustard)
            }
            Text(String(format: "%.1f METERS", run?.peakDistanceMeters ?? store.hudState.peakDistanceMeters))
                .font(.system(size: 34, weight: .bold, design: .monospaced)).foregroundColor(LiminalPalette.mustard)
            Text(run?.causeOfDeath ?? store.hudState.causeOfDeath).foregroundColor(LiminalPalette.phosphorWhite)
            Spacer()
            Button("RECORD NEW TAPE") { store.startNewTape() }
                .frame(maxWidth: .infinity, minHeight: 52).background(LiminalPalette.mustard).foregroundColor(.black).cornerRadius(4)
            Button("RETURN TO DECK") { store.path.removeAll() }
                .frame(maxWidth: .infinity, minHeight: 48).foregroundColor(LiminalPalette.phosphorWhite)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
        }
        .padding(20).background(LiminalPalette.voidBlack.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}

struct ArchiveView: View {
    @ObservedObject var store: BackroomsStore
    var body: some View {
        List {
            if store.runs.isEmpty {
                Text("NO RECOVERED TAPES").foregroundColor(LiminalPalette.phosphorMuted)
            } else {
                ForEach(store.runs) { run in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(String(format: "%.1fm", run.peakDistanceMeters)).font(.system(.headline, design: .monospaced)).foregroundColor(LiminalPalette.mustard)
                        Text(run.causeOfDeath).font(.subheadline)
                        Text("TIME \(formatDuration(run.durationSeconds))  •  GLITCHES \(run.glitchesSurvived)  •  SHIFTS \(run.spatialShiftsEndured)")
                            .font(.caption2).foregroundColor(LiminalPalette.phosphorMuted)
                    }
                }
            }
        }
        .navigationTitle("Survival Archive")
        .toolbar {
            if !store.runs.isEmpty {
                Button("Wipe", role: .destructive) { store.clearArchive() }
            }
        }
    }
}

struct CodexView: View {
    @ObservedObject var store: BackroomsStore
    var body: some View {
        List {
            Section("Biome Forms") {
                ForEach(BiomeType.allCases, id: \.rawValue) { b in
                    let rec = store.discoveredEntries[b.rawValue]
                    VStack(alignment: .leading, spacing: 4) {
                        Text(rec != nil ? "\(b.sectorCode) // \(b.displayName)" : "\(b.sectorCode) // [SIGNAL UNVERIFIED]")
                            .font(.system(.subheadline, design: .monospaced))
                            .foregroundColor(rec != nil ? LiminalPalette.mustard : LiminalPalette.phosphorMuted)
                        if let rec {
                            Text(String(format: "Encounters: %d  •  Deepest: %.1fm", rec.encounterCount, rec.deepestMeters))
                                .font(.caption2).foregroundColor(LiminalPalette.poolCyan)
                        }
                    }
                }
            }
            Section("Anomalies") {
                ForEach(AnomalyType.allCases, id: \.rawValue) { a in
                    let rec = store.discoveredEntries[a.rawValue]
                    VStack(alignment: .leading, spacing: 4) {
                        Text(rec != nil ? a.displayName : "[SIGNAL UNVERIFIED]")
                            .font(.system(.subheadline, design: .monospaced))
                            .foregroundColor(rec != nil ? LiminalPalette.mustard : LiminalPalette.phosphorMuted)
                        if let rec {
                            Text(String(format: "Encounters: %d  •  Deepest: %.1fm", rec.encounterCount, rec.deepestMeters))
                                .font(.caption2).foregroundColor(LiminalPalette.poolCyan)
                        }
                    }
                }
            }
        }
        .navigationTitle("Anomaly Codex")
    }
}

struct SettingsView: View {
    @ObservedObject var store: BackroomsStore
    var body: some View {
        Form {
            Toggle("Fluorescent Hum & Static Audio", isOn: Binding(
                get: { store.settings.audioEnabled },
                set: { v in store.updateSettings { $0.audioEnabled = v } }
            ))
            Toggle("Glitch Safety Mode (Reduce Flashes)", isOn: Binding(
                get: { store.settings.glitchSafetyMode },
                set: { v in store.updateSettings { $0.glitchSafetyMode = v } }
            ))
            Toggle("Invert Horizontal Look", isOn: Binding(
                get: { store.settings.invertLook },
                set: { v in store.updateSettings { $0.invertLook = v } }
            ))
            Toggle("Spawn Bearing Indicator", isOn: Binding(
                get: { store.settings.showSignalCompass },
                set: { v in store.updateSettings { $0.showSignalCompass = v } }
            ))
            VStack(alignment: .leading) {
                Text(String(format: "Turn Sensitivity: %.2fx", store.settings.lookSensitivity))
                Slider(value: Binding(
                    get: { Double(store.settings.lookSensitivity) },
                    set: { v in store.updateSettings { $0.lookSensitivity = Float(v) } }
                ), in: 0.4...2.2)
            }
        }
        .navigationTitle("Camcorder Setup")
    }
}

private struct StatBox: View {
    let title: String; let value: String; let accent: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(.caption2, design: .monospaced)).foregroundColor(LiminalPalette.phosphorMuted)
            Text(value).font(.system(.title2, design: .monospaced)).foregroundColor(accent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12).background(LiminalPalette.camcorderDark)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(LiminalPalette.hairline))
    }
}

private struct HudMeter: View {
    let label: String; let value: Float; let color: Color
    var body: some View {
        HStack(spacing: 6) {
            Text(label).font(.system(size: 10, design: .monospaced)).foregroundColor(LiminalPalette.phosphorWhite).frame(width: 26, alignment: .leading)
            ProgressView(value: Double(max(0, min(1, value)))).tint(color)
        }
    }
}

private func formatDuration(_ sec: Int) -> String {
    String(format: "%02d:%02d", sec / 60, sec % 60)
}
