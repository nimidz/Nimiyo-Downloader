package com.nimiyo.nativeapp.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * NimiyoReverbFX Native DSP AudioProcessor (Edjing Mix Style):
 * Supports both PCM 16-bit and PCM Float so every Android device & audio decoder is covered.
 * 1. DJ Low-Cut 180 Hz Biquad HPF (keeps kick drum & sub-bass punchy without mud)
 * 2. Wide 6-tap Dattorro Stereo Early Reflections (8.9ms .. 74.9ms)
 * 3. 4-stage All-Pass input diffusers + 4-line Hadamard FDN Hall Tank (Y-axis scales ~1.5s .. ~5.1s RT60)
 * 4. Y-axis Dynamic Tone / Air Shimmer Biquad LPF (3400 Hz .. 12500 Hz)
 * 5. Loudness-preserving Equal-Power Dry/Wet curve + SoftLimiter
 */
@OptIn(UnstableApi::class)
class NimiyoDspAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetEnabled: Boolean = false

    @Volatile
    private var targetDryWet: Float = 0.5f

    @Volatile
    private var targetDepth: Float = 0.5f

    @Volatile
    private var targetMonoAudio: Boolean = false

    @Volatile
    private var targetNormalize: Boolean = false

    @Volatile
    private var targetGainLinear: Float = 1.0f

    private var smoothEnabled: Float = 0f
    private var smoothDryWet: Float = 0.5f
    private var smoothDepth: Float = 0.5f
    private var smoothMono: Float = 0f
    private var smoothNormGain: Float = 1.0f
    private var smoothManualGain: Float = 1.0f
    private var envSq: Float = 0.04f

    private var sampleRate: Int = 44100
    private var channels: Int = 2
    private var isFloatPcm: Boolean = false

    private val hpfL = BiquadFilter()
    private val hpfR = BiquadFilter()
    private val lpfL = BiquadFilter()
    private val lpfR = BiquadFilter()
    private var lastLpfCutoff = -1f

    // Early Reflections stereo delay line (up to ~120ms)
    private var erBufL = FloatArray(16384)
    private var erBufR = FloatArray(16384)
    private var erWrite = 0
    private val erTimesSec = floatArrayOf(0.0089f, 0.0143f, 0.0217f, 0.0341f, 0.0523f, 0.0749f)
    private val erGains = floatArrayOf(0.75f, 0.65f, 0.56f, 0.48f, 0.40f, 0.32f)
    private val erPan = floatArrayOf(-0.7f, 0.7f, -0.55f, 0.55f, -0.4f, 0.4f)
    private val erOffsets = IntArray(6)

    // 4 stereo input all-pass diffusers for silky transient smearing
    private val apLenL = intArrayOf(142, 107, 379, 277)
    private val apLenR = intArrayOf(151, 113, 397, 293)
    private val apBufL = Array(4) { FloatArray(1024) }
    private val apBufR = Array(4) { FloatArray(1024) }
    private val apIdxL = IntArray(4)
    private val apIdxR = IntArray(4)
    private val apGain = floatArrayOf(0.65f, 0.65f, 0.55f, 0.55f)

    // 4-line Hadamard FDN plate tank (dynamically scales RT60 ~1.5s Tight .. ~5.1s Wide)
    private val fdnSec = floatArrayOf(0.0473f, 0.0619f, 0.0791f, 0.0977f)
    private val fdnBuf = Array(4) { FloatArray(16384) }
    private val fdnWrite = IntArray(4)
    private val fdnLen = IntArray(4)
    private val fdnGain = floatArrayOf(0.84f, 0.80f, 0.75f, 0.70f)
    private val fdnLp = FloatArray(4)
    private var sampleTick = 0
    private var buffersClearedWhileBypassed = true

    fun setReverbParams(enabled: Boolean, dryWet: Float, depth: Float) {
        targetEnabled = enabled
        targetDryWet = dryWet.coerceIn(0f, 1f)
        targetDepth = depth.coerceIn(0f, 1f)
    }

    fun setMonoAudio(enabled: Boolean) {
        targetMonoAudio = enabled
    }

    fun setLoudnessParams(normalize: Boolean, gainDb: Int) {
        targetNormalize = normalize
        val clampedDb = gainDb.coerceIn(0, 12)
        targetGainLinear = if (clampedDb == 0) 1.0f else 10f.pow(clampedDb / 20f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) {
            return AudioFormat.NOT_SET
        }
        isFloatPcm = (enc == C.ENCODING_PCM_FLOAT)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channels = inputAudioFormat.channelCount
        initDspState()
        return inputAudioFormat
    }

    private fun initDspState() {
        hpfL.configureHighPass(sampleRate.toFloat(), 180f, 0.707f)
        hpfR.configureHighPass(sampleRate.toFloat(), 180f, 0.707f)
        lastLpfCutoff = -1f
        updateDynamicReverbParams(targetDepth)

        val neededEr = (sampleRate * 0.12f).toInt().coerceAtLeast(2048)
        if (erBufL.size < neededEr) {
            erBufL = FloatArray(neededEr)
            erBufR = FloatArray(neededEr)
        }
        for (i in 0 until 6) {
            erOffsets[i] = (erTimesSec[i] * sampleRate).toInt().coerceIn(1, erBufL.size - 1)
        }

        val dattorroScale = sampleRate.toFloat() / 29761f
        val baseL = intArrayOf(142, 107, 379, 277)
        val baseR = intArrayOf(151, 113, 397, 293)
        for (i in 0 until 4) {
            apLenL[i] = (baseL[i] * dattorroScale).toInt().coerceIn(32, apBufL[i].size - 1)
            apLenR[i] = (baseR[i] * dattorroScale).toInt().coerceIn(32, apBufR[i].size - 1)
        }

        for (k in 0 until 4) {
            fdnLen[k] = (fdnSec[k] * sampleRate).toInt().coerceIn(64, fdnBuf[k].size - 1)
        }
        updateFdnFeedback(targetDepth)

        resetBuffers()
    }

    private fun updateFdnFeedback(depth: Float) {
        // Y-axis (Tight -> Wide): scales decay from ~1.5s studio echo (4.6) to ~5.1s spacious hall (1.35)
        val decayRate = 4.6f - depth.coerceIn(0f, 1f) * 3.25f
        for (k in 0 until 4) {
            fdnGain[k] = exp(-decayRate * fdnSec[k]).coerceIn(0.1f, 0.96f)
        }
    }

    private fun updateDynamicReverbParams(depth: Float) {
        updateFdnFeedback(depth)
        // Opens brightness and shimmer from warm tight (3400Hz) to wide open hall (12500Hz)
        val cutoff = 3400f + (depth * 9100f)
        if (abs(cutoff - lastLpfCutoff) > 30f) {
            lpfL.configureLowPass(sampleRate.toFloat(), cutoff, 0.707f)
            lpfR.configureLowPass(sampleRate.toFloat(), cutoff, 0.707f)
            lastLpfCutoff = cutoff
        }
    }

    private fun resetBuffers() {
        erBufL.fill(0f)
        erBufR.fill(0f)
        erWrite = 0
        for (i in 0 until 4) {
            apBufL[i].fill(0f)
            apBufR[i].fill(0f)
            apIdxL[i] = 0
            apIdxR[i] = 0
            fdnBuf[i].fill(0f)
            fdnWrite[i] = 0
            fdnLp[i] = 0f
        }
        hpfL.reset()
        hpfR.reset()
        lpfL.reset()
        lpfR.reset()
        sampleTick = 0
    }

    override fun onFlush() = resetBuffers()
    override fun onReset() = resetBuffers()

    private fun processAllPassL(stage: Int, x: Float): Float {
        val buf = apBufL[stage]
        val len = apLenL[stage]
        val idx = apIdxL[stage]
        val delayed = buf[idx]
        val g = apGain[stage]
        val v = x + g * delayed
        buf[idx] = v
        apIdxL[stage] = if (idx + 1 >= len) 0 else idx + 1
        return delayed - g * v
    }

    private fun processAllPassR(stage: Int, x: Float): Float {
        val buf = apBufR[stage]
        val len = apLenR[stage]
        val idx = apIdxR[stage]
        val delayed = buf[idx]
        val g = apGain[stage]
        val v = x + g * delayed
        buf[idx] = v
        apIdxR[stage] = if (idx + 1 >= len) 0 else idx + 1
        return delayed - g * v
    }

    private fun readFdn(line: Int): Float {
        val buf = fdnBuf[line]
        var readIdx = fdnWrite[line] - fdnLen[line]
        if (readIdx < 0) readIdx += buf.size
        return buf[readIdx]
    }

    private fun processStereoSample(dryL: Float, dryR: Float): Pair<Float, Float> {
        buffersClearedWhileBypassed = false
        val safeLIn = if (dryL.isFinite()) dryL else 0f
        val safeRIn = if (dryR.isFinite()) dryR else 0f

        val tgtNormGain = if (targetNormalize) {
            val instSq = (safeLIn * safeLIn + safeRIn * safeRIn) * 0.5f
            envSq += (instSq - envSq) * 0.00015f
            val rms = sqrt(envSq.coerceAtLeast(0.0001f))
            (0.26f / rms.coerceAtLeast(0.03f)).coerceIn(0.85f, 2.8f)
        } else {
            1.0f
        }
        smoothNormGain += (tgtNormGain - smoothNormGain) * 0.0005f
        smoothManualGain += (targetGainLinear - smoothManualGain) * 0.002f
        val preGain = smoothNormGain * smoothManualGain
        val safeL = (safeLIn * preGain).coerceIn(-4f, 4f)
        val safeR = (safeRIn * preGain).coerceIn(-4f, 4f)

        val tgtEn = if (targetEnabled) 1f else 0f
        smoothEnabled += (tgtEn - smoothEnabled) * 0.002f
        smoothDryWet += (targetDryWet - smoothDryWet) * 0.002f
        smoothDepth += (targetDepth - smoothDepth) * 0.002f
        val tgtMono = if (targetMonoAudio) 1f else 0f
        smoothMono += (tgtMono - smoothMono) * 0.002f

        if ((sampleTick and 63) == 0) {
            updateDynamicReverbParams(smoothDepth)
        }
        sampleTick++

        // 1. 180 Hz DJ Low-Cut HPF on wet send
        val hL = hpfL.process(safeL) * smoothEnabled
        val hR = hpfR.process(safeR) * smoothEnabled

        // 2. Wider 6-tap stereo Early Reflections
        erBufL[erWrite] = hL
        erBufR[erWrite] = hR
        val erCap = erBufL.size
        var earlyL = 0f
        var earlyR = 0f
        for (t in 0 until 6) {
            var rIdx = erWrite - erOffsets[t]
            if (rIdx < 0) rIdx += erCap
            val tapMono = (erBufL[rIdx] + erBufR[rIdx]) * 0.5f
            earlyL += tapMono * erGains[t] * (1f - erPan[t]) * 0.5f
            earlyR += tapMono * erGains[t] * (1f + erPan[t]) * 0.5f
        }
        erWrite = if (erWrite + 1 >= erCap) 0 else erWrite + 1

        // 3. 4-stage All-Pass input diffusion
        var diffL = hL * 0.72f
        var diffR = hR * 0.72f
        for (st in 0 until 4) {
            diffL = processAllPassL(st, diffL)
            diffR = processAllPassR(st, diffR)
        }

        // 4. 4-line Hadamard FDN Plate Tank (less high-end damping as Depth widens)
        val damping = (0.28f - smoothDepth * 0.14f).coerceIn(0.10f, 0.35f)
        val s0 = readFdn(0) * fdnGain[0]
        val s1 = readFdn(1) * fdnGain[1]
        val s2 = readFdn(2) * fdnGain[2]
        val s3 = readFdn(3) * fdnGain[3]

        fdnLp[0] = fdnLp[0] * damping + s0 * (1f - damping)
        fdnLp[1] = fdnLp[1] * damping + s1 * (1f - damping)
        fdnLp[2] = fdnLp[2] * damping + s2 * (1f - damping)
        fdnLp[3] = fdnLp[3] * damping + s3 * (1f - damping)

        val h0 = 0.5f * (fdnLp[0] + fdnLp[1] + fdnLp[2] + fdnLp[3])
        val h1 = 0.5f * (fdnLp[0] - fdnLp[1] + fdnLp[2] - fdnLp[3])
        val h2 = 0.5f * (fdnLp[0] + fdnLp[1] - fdnLp[2] - fdnLp[3])
        val h3 = 0.5f * (fdnLp[0] - fdnLp[1] - fdnLp[2] + fdnLp[3])

        fdnBuf[0][fdnWrite[0]] = h0 + diffL * 0.50f
        fdnBuf[1][fdnWrite[1]] = h1 + diffR * 0.50f
        fdnBuf[2][fdnWrite[2]] = h2 + diffL * 0.46f
        fdnBuf[3][fdnWrite[3]] = h3 + diffR * 0.46f

        for (k in 0 until 4) {
            val nw = fdnWrite[k] + 1
            fdnWrite[k] = if (nw >= fdnBuf[k].size) 0 else nw
        }

        val tankL = (fdnLp[0] + fdnLp[2] - fdnLp[1] * 0.25f) * 0.44f + earlyL * 0.52f
        val tankR = (fdnLp[1] + fdnLp[3] - fdnLp[0] * 0.25f) * 0.44f + earlyR * 0.52f

        // 5. Dynamic Tone / Air Shimmer Lowpass (3400Hz .. 12500Hz)
        val wetL = lpfL.process(tankL)
        val wetR = lpfR.process(tankR)

        // 6. Loudness-preserving Equal-Power Dry / Wet curve (no volume drop when pushing Wide/Wet)
        val effectiveDry = 1.0f - (smoothDryWet * 0.25f * smoothEnabled)
        val effectiveWet = smoothDryWet.pow(0.75f) * (1.25f + smoothDepth * 0.65f) * smoothEnabled

        var outL = softLimit(safeL * effectiveDry + wetL * effectiveWet)
        var outR = softLimit(safeR * effectiveDry + wetR * effectiveWet)
        if (smoothMono > 0.001f) {
            val mono = (outL + outR) * 0.5f
            outL += (mono - outL) * smoothMono
            outR += (mono - outR) * smoothMono
        }
        return outL to outR
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outBuf = replaceOutputBuffer(remaining)

        if (!targetEnabled && smoothEnabled <= 0.0005f &&
            !targetMonoAudio && smoothMono <= 0.0005f &&
            !targetNormalize && targetGainLinear <= 1.005f &&
            abs(smoothNormGain - 1.0f) <= 0.005f && abs(smoothManualGain - 1.0f) <= 0.005f
        ) {
            smoothEnabled = 0f
            smoothMono = 0f
            smoothNormGain = 1.0f
            smoothManualGain = 1.0f
            if (!buffersClearedWhileBypassed) {
                resetBuffers()
                buffersClearedWhileBypassed = true
            }
            outBuf.put(inputBuffer)
            outBuf.flip()
            return
        }

        val invScale = 1f / 32768f

        if (isFloatPcm) {
            if (channels == 2) {
                while (inputBuffer.remaining() >= 8) {
                    val dryL = inputBuffer.float
                    val dryR = inputBuffer.float
                    val (outL, outR) = processStereoSample(dryL, dryR)
                    outBuf.putFloat(outL)
                    outBuf.putFloat(outR)
                }
            } else {
                while (inputBuffer.remaining() >= 4) {
                    val dry = inputBuffer.float
                    val (outL, _) = processStereoSample(dry, dry)
                    outBuf.putFloat(outL)
                }
            }
        } else {
            if (channels == 2) {
                while (inputBuffer.remaining() >= 4) {
                    val dryL = inputBuffer.short * invScale
                    val dryR = inputBuffer.short * invScale
                    val (outL, outR) = processStereoSample(dryL, dryR)
                    outBuf.putShort((outL * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                    outBuf.putShort((outR * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
            } else {
                while (inputBuffer.remaining() >= 2) {
                    val dry = inputBuffer.short * invScale
                    val (outL, _) = processStereoSample(dry, dry)
                    outBuf.putShort((outL * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }

        outBuf.flip()
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        if (x > 0.85f) {
            val d = x - 0.85f
            return 0.85f + 0.14f * (d / (0.14f + d))
        }
        if (x < -0.85f) {
            val d = -0.85f - x
            return -0.85f - 0.14f * (d / (0.14f + d))
        }
        return x
    }

    private class BiquadFilter {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }

        fun configureHighPass(fs: Float, fc: Float, q: Float) {
            val w0 = 2.0 * PI * fc.toDouble() / fs.toDouble()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
            b1 = (-(1.0 + cosW0) / a0).toFloat()
            b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun configureLowPass(fs: Float, fc: Float, q: Float) {
            val clampedFc = fc.coerceIn(100f, fs * 0.45f)
            val w0 = 2.0 * PI * clampedFc.toDouble() / fs.toDouble()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun process(x: Float): Float {
            val safeX = if (x.isFinite()) x else 0f
            val y = b0 * safeX + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            if (!y.isFinite()) {
                reset()
                return 0f
            }
            x2 = x1; x1 = safeX; y2 = y1; y1 = y
            return y
        }
    }
}
