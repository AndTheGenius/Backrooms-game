import AVFoundation
import Foundation

final class AudioSynthEngine {
    private var engine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?

    private var enabled: Bool = true
    private var masterVolume: Float = 0.75
    private var glitchIntensity: Float = 0.05
    private var anomalyProximity: Float = 0
    private var burstImpulse: Float = 0

    private var phase60: Double = 0
    private var phase120: Double = 0
    private var phaseDrone: Double = 0
    private var heartPhase: Double = 0
    private var rng = SeededRNG(seed: 42)

    func updateParameters(audioEnabled: Bool, volume: Float, glitch: Float, nearestAnomalyMeters: Float) {
        enabled = audioEnabled
        masterVolume = max(0, min(1, volume))
        glitchIntensity = max(0, min(1, glitch))
        anomalyProximity = max(0, min(1, (18 - nearestAnomalyMeters) / 18))
    }

    func triggerGlitchBurst(strength: Float = 0.8) {
        burstImpulse = max(burstImpulse, max(0, min(1, strength)))
    }

    func start() {
        guard engine == nil else { return }
        let audioEngine = AVAudioEngine()
        let sampleRate = 22050.0
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return }

        let node = AVAudioSourceNode { [weak self] _, _, frameCount, audioBufferList -> OSStatus in
            guard let self else { return noErr }
            let abl = UnsafeMutableAudioBufferListPointer(audioBufferList)
            let ptr = abl[0].mData?.assumingMemoryBound(to: Float.self)

            if !self.enabled || self.masterVolume <= 0.01 {
                for i in 0..<Int(frameCount) { ptr?[i] = 0 }
                return noErr
            }

            let g = Double(self.glitchIntensity)
            let prox = Double(self.anomalyProximity)
            let burst = Double(self.burstImpulse)
            self.burstImpulse *= 0.92
            if self.burstImpulse < 0.01 { self.burstImpulse = 0 }

            let humFreq = 60.0 + sin(self.phaseDrone * 0.4) * (1.2 + g * 4.5)
            let heartHz = 1.0 + prox * 1.4

            for i in 0..<Int(frameCount) {
                self.phase60 += 2.0 * .pi * humFreq / sampleRate
                self.phase120 += 2.0 * .pi * (humFreq * 2.0) / sampleRate
                self.phaseDrone += 2.0 * .pi * 38.0 / sampleRate
                self.heartPhase += heartHz / sampleRate
                if self.heartPhase >= 1.0 { self.heartPhase -= 1.0 }

                let hum = sin(self.phase60) * 0.11 + sin(self.phase120) * 0.045
                let drone = sin(self.phaseDrone) * (0.04 + prox * 0.14)
                let beat: Double = (prox > 0.15 && self.heartPhase < 0.14)
                    ? sin((self.heartPhase / 0.14) * .pi) * prox * 0.35
                    : 0.0
                let noiseAmp = 0.02 + g * 0.15 + burst * 0.32
                let noise = (Double(self.rng.nextFloat()) * 2.0 - 1.0) * noiseAmp
                let mixed = (hum + drone + beat + noise) * Double(self.masterVolume)
                ptr?[i] = Float(max(-0.95, min(0.95, mixed)))
            }
            return noErr
        }

        audioEngine.attach(node)
        audioEngine.connect(node, to: audioEngine.mainMixerNode, format: format)
        try? audioEngine.start()
        self.sourceNode = node
        self.engine = audioEngine
    }

    func stop() {
        engine?.stop()
        engine = nil
        sourceNode = nil
    }
}
