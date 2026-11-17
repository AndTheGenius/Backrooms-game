package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioSynthEngine {

    @Volatile
    private var running = false

    @Volatile
    private var enabled = true

    @Volatile
    private var masterVolume = 0.75f

    @Volatile
    private var glitchIntensity = 0.05f

    @Volatile
    private var anomalyProximity = 0f // 0 = far, 1 = right next to player

    @Volatile
    private var burstImpulse = 0f

    private var workerThread: Thread? = null

    fun updateParameters(
        audioEnabled: Boolean,
        volume: Float,
        glitch: Float,
        nearestAnomalyMeters: Float
    ) {
        enabled = audioEnabled
        masterVolume = volume.coerceIn(0f, 1f)
        glitchIntensity = glitch.coerceIn(0f, 1f)
        anomalyProximity = ((18f - nearestAnomalyMeters) / 18f).coerceIn(0f, 1f)
    }

    fun triggerGlitchBurst(strength: Float = 0.8f) {
        burstImpulse = maxOf(burstImpulse, strength.coerceIn(0f, 1f))
    }

    fun start() {
        if (running) return
        running = true
        workerThread = thread(start = true, isDaemon = true, name = "BackroomsAudioSynth") {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                running = false
                return@thread
            }

            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf.coerceAtLeast(2048))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                val buffer = ShortArray(512)
                var phase60 = 0.0
                var phase120 = 0.0
                var phaseDrone = 0.0
                var heartPhase = 0.0
                val rng = Random(42L)

                while (running) {
                    if (!enabled || masterVolume <= 0.01f) {
                        buffer.fill(0)
                        track.write(buffer, 0, buffer.size)
                        Thread.sleep(20)
                        continue
                    }

                    val g = glitchIntensity
                    val prox = anomalyProximity
                    val burst = burstImpulse
                    burstImpulse = (burstImpulse * 0.92f).let { if (it < 0.01f) 0f else it }

                    val humFreq = 60.0 + sin(phaseDrone * 0.4) * (1.2 + g * 4.5)
                    val heartRateHz = 1.0 + prox * 1.4

                    for (i in buffer.indices) {
                        phase60 += 2.0 * PI * humFreq / sampleRate
                        phase120 += 2.0 * PI * (humFreq * 2.0) / sampleRate
                        phaseDrone += 2.0 * PI * 38.0 / sampleRate
                        heartPhase += heartRateHz / sampleRate
                        if (heartPhase >= 1.0) heartPhase -= 1.0

                        // Classic 60Hz fluorescent lamp buzz + eerie sub drone
                        val hum = sin(phase60) * 0.11 + sin(phase120) * 0.045
                        val drone = sin(phaseDrone) * (0.04 + prox * 0.14)

                        // Heartbeat thump when anomaly is close
                        val beatEnv = if (prox > 0.15f && heartPhase < 0.14) {
                            sin((heartPhase / 0.14) * PI) * prox * 0.35
                        } else {
                            0.0
                        }

                        // VHS tape hiss & static crackle
                        val noiseAmp = 0.02 + g * 0.15 + burst * 0.32
                        val staticNoise = (rng.nextDouble() * 2.0 - 1.0) * noiseAmp

                        val mixed = (hum + drone + beatEnv + staticNoise) * masterVolume
                        val sample = (mixed.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                        buffer[i] = sample
                    }

                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Throwable) {
                // Gracefully degrade if device/emulator audio sink is unavailable
            } finally {
                try {
                    track?.pause()
                    track?.flush()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        running = false
        workerThread?.interrupt()
        workerThread = null
    }
}
