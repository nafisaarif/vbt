package com.hirocross.vbt

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RepResult(val number: Int, val meanVelocity: Double, val peakVelocity: Double, val rom: Double, val duration: Double, val velocityLoss: Double, val meanPower: Double, val peakPower: Double)

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private lateinit var db: DatabaseHelper

    private lateinit var inputAthlete: EditText
    private lateinit var inputExercise: EditText
    private lateinit var inputLoad: EditText
    private lateinit var inputBodyMass: EditText
    private lateinit var inputEffectiveBodyMass: EditText
    private lateinit var status: TextView
    private lateinit var velocityText: TextView
    private lateinit var directionText: TextView
    private lateinit var repText: TextView
    private lateinit var meanText: TextView
    private lateinit var peakText: TextView
    private lateinit var romText: TextView
    private lateinit var lossText: TextView
    private lateinit var zoneText: TextView
    private lateinit var estimated1RMText: TextView
    private lateinit var meanPowerText: TextView
    private lateinit var peakPowerText: TextView
    private lateinit var historyText: TextView
    private lateinit var chart: VelocityChartView

    private var calibrated = false
    private var running = false
    private var calibrating = false
    private val calibrationSamples = mutableListOf<FloatArray>()
    private val bias = FloatArray(3)

    private var lastTimestampNs = 0L
    private var filteredA = 0.0
    private var velocity = 0.0
    private var position = 0.0
    private var stillSinceMs = 0L

    private var phase = "idle"
    private var repStartMs = 0L
    private var concentricStartMs = 0L
    private var lastValidRepMs = 0L
    private var candidateStartMs = 0L
    private var candidateSamples = 0
    private var directionChanged = false
    private val repVelocities = mutableListOf<Double>()
    private val repPowerSamples = mutableListOf<Double>()
    private val repPositions = mutableListOf<Double>()
    private val reps = mutableListOf<RepResult>()

    // Anti-vibration filtering and repetition validation
    private val alpha = 0.18
    private val startThreshold = 0.70
    private val stillThreshold = 0.14
    private val minRepMs = 650L
    private val minConcentricMs = 250L
    private val minRepRom = 0.12
    private val minPeakVelocity = 0.18
    private val minMeanVelocity = 0.08
    private val minimumRepGapMs = 700L
    private val movementConfirmMs = 120L
    private val movementConfirmSamples = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        inputAthlete = findViewById(R.id.inputAthlete)
        inputExercise = findViewById(R.id.inputExercise)
        inputLoad = findViewById(R.id.inputLoad)
        inputBodyMass = findViewById(R.id.inputBodyMass)
        inputEffectiveBodyMass = findViewById(R.id.inputEffectiveBodyMass)
        status = findViewById(R.id.textStatus)
        velocityText = findViewById(R.id.textVelocity)
        directionText = findViewById(R.id.textDirection)
        repText = findViewById(R.id.textRep)
        meanText = findViewById(R.id.textMean)
        peakText = findViewById(R.id.textPeak)
        romText = findViewById(R.id.textRom)
        lossText = findViewById(R.id.textLoss)
        zoneText = findViewById(R.id.textZone)
        estimated1RMText = findViewById(R.id.textEstimated1RM)
        meanPowerText = findViewById(R.id.textMeanPower)
        peakPowerText = findViewById(R.id.textPeakPower)
        historyText = findViewById(R.id.textHistory)
        chart = findViewById(R.id.chart)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener { startCalibration() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startSet() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopSet(saveSession = true) }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetAll() }
        findViewById<Button>(R.id.btnDashboard).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        motionSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun startCalibration() {
        calibrationSamples.clear()
        calibrating = true
        calibrated = false
        status.text = "Status: KALIBRASI — HP diam dan vertikal"
    }

    private fun finishCalibration() {
        if (calibrationSamples.isEmpty()) return
        for (axis in 0..2) bias[axis] = calibrationSamples.map { it[axis] }.average().toFloat()
        calibrating = false
        calibrated = true
        status.text = "Status: KALIBRASI SELESAI"
    }

    private fun startSet() {
        if (!calibrated) {
            Toast.makeText(this, "Kalibrasi terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        if (inputAthlete.text.toString().isBlank()) {
            Toast.makeText(this, "Isi nama atlet.", Toast.LENGTH_SHORT).show()
            return
        }
        reps.clear()
        chart.clear()
        running = true
        lastTimestampNs = 0L
        filteredA = 0.0
        velocity = 0.0
        position = 0.0
        phase = "idle"
        candidateStartMs = 0L
        candidateSamples = 0
        directionChanged = false
        concentricStartMs = 0L
        lastValidRepMs = 0L
        status.text = "Status: RUNNING — anti-getaran aktif"
        updateRepHistory()
    }

    private fun stopSet(saveSession: Boolean) {
        if (saveSession && reps.isNotEmpty()) saveSession()
        running = false
        velocity = 0.0
        position = 0.0
        phase = "idle"
        velocityText.text = "0.00"
        directionText.text = "Arah: DIAM"
        status.text = "Status: FINISHED"
    }

    private fun resetAll() {
        stopSet(saveSession = false)
        reps.clear()
        chart.clear()
        repText.text = "Rep: 0"
        meanText.text = "Mean: 0.00 m/s"
        peakText.text = "Peak: 0.00 m/s"
        romText.text = "ROM: 0.00 m"
        lossText.text = "Loss: 0.0%"
        zoneText.text = "Zone: -"
        estimated1RMText.text = "Est. 1RM: 0 kg"
        meanPowerText.text = "Mean Power: 0 W"
        peakPowerText.text = "Peak Power: 0 W"
        historyText.text = "Riwayat rep akan muncul di sini."
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values.copyOf()

        if (calibrating) {
            calibrationSamples.add(values)
            status.text = "Status: KALIBRASI ${calibrationSamples.size}/180"
            if (calibrationSamples.size >= 180) finishCalibration()
            return
        }

        if (!running) {
            lastTimestampNs = event.timestamp
            return
        }
        if (lastTimestampNs == 0L) {
            lastTimestampNs = event.timestamp
            return
        }

        val dt = (event.timestamp - lastTimestampNs) / 1_000_000_000.0
        lastTimestampNs = event.timestamp
        if (dt <= 0.0 || dt > 0.1) return

        val rawA = values[1] - bias[1]
        filteredA = alpha * rawA + (1.0 - alpha) * filteredA

        val nowMs = SystemClock.elapsedRealtime()
        if (abs(filteredA) < stillThreshold) {
            if (stillSinceMs == 0L) stillSinceMs = nowMs
            if (nowMs - stillSinceMs > 170L) {
                velocity *= 0.35
                if (abs(velocity) < 0.025) velocity = 0.0
            }
        } else stillSinceMs = 0L

        velocity += filteredA * dt
        velocity = max(-3.5, min(3.5, velocity))
        position += velocity * dt

        velocityText.text = "%.2f".format(abs(velocity))
        directionText.text = when {
            velocity > 0.05 -> "Arah: NAIK"
            velocity < -0.05 -> "Arah: TURUN"
            else -> "Arah: DIAM"
        }

        chart.addValue(abs(velocity).toFloat())
        detectRep(nowMs)
    }

    private fun detectRep(nowMs: Long) {
        // Ignore all movement briefly after a valid repetition.
        if (nowMs - lastValidRepMs < minimumRepGapMs) return

        if (phase == "idle") {
            val strongMovement = abs(filteredA) >= startThreshold && abs(velocity) >= 0.04

            if (strongMovement) {
                if (candidateStartMs == 0L) candidateStartMs = nowMs
                candidateSamples++

                val confirmedByTime = nowMs - candidateStartMs >= movementConfirmMs
                val confirmedBySamples = candidateSamples >= movementConfirmSamples

                // A single acceleration spike or vibration cannot start a repetition.
                if (confirmedByTime && confirmedBySamples) {
                    phase = if (velocity >= 0.0) "up" else "down"
                    repStartMs = candidateStartMs
                    concentricStartMs = if (phase == "up") nowMs else 0L
                    directionChanged = phase == "up"
                    repVelocities.clear()
                repPowerSamples.clear()
                    repPowerSamples.clear()
                    repPositions.clear()
                    repPositions.add(position)
                    candidateStartMs = 0L
                    candidateSamples = 0
                }
            } else {
                candidateStartMs = 0L
                candidateSamples = 0
            }
            return
        }

        repPositions.add(position)

        if (phase == "down") {
            // A valid lift must reverse from eccentric/downward to concentric/upward.
            if (velocity > 0.08) {
                phase = "up"
                directionChanged = true
                concentricStartMs = nowMs
                repVelocities.clear()
            }
        }

        if (phase == "up") {
            if (velocity > 0.0) {
                repVelocities.add(velocity)

                // Estimated external mechanical power:
                // movingMass × (gravity + vertical acceleration) × concentric velocity.
                val externalLoad = currentLoadKg()
                val effectiveBodyMass = currentBodyMassKg() * currentEffectiveBodyMassFraction()
                val movingMass = max(0.0, externalLoad + effectiveBodyMass)
                val verticalForce = movingMass * max(0.0, 9.80665 + filteredA)
                val instantaneousPower = max(0.0, verticalForce * velocity)
                repPowerSamples.add(instantaneousPower)
            }

            val totalDuration = nowMs - repStartMs
            val concentricDuration = if (concentricStartMs > 0L) nowMs - concentricStartMs else 0L
            val currentRom = if (repPositions.size > 1) {
                abs((repPositions.maxOrNull() ?: 0.0) - (repPositions.minOrNull() ?: 0.0))
            } else 0.0

            val returnedToStill =
                abs(filteredA) < stillThreshold &&
                abs(velocity) < 0.06 &&
                stillSinceMs != 0L &&
                nowMs - stillSinceMs >= 180L

            if (returnedToStill) {
                val candidateIsLongEnough =
                    totalDuration >= minRepMs &&
                    concentricDuration >= minConcentricMs

                if (candidateIsLongEnough && directionChanged && currentRom >= minRepRom) {
                    finalizeRep(nowMs)
                } else {
                    rejectCandidate("Gerakan kecil diabaikan")
                }
            }

            // Cancel accidental movement that lasts too long without a valid finish.
            if (totalDuration > 6000L) {
                rejectCandidate("Gerakan tidak valid")
            }
        }
    }

    private fun rejectCandidate(message: String) {
        phase = "idle"
        velocity = 0.0
        position = 0.0
        candidateStartMs = 0L
        candidateSamples = 0
        directionChanged = false
        concentricStartMs = 0L
        repVelocities.clear()
        repPowerSamples.clear()
        repPositions.clear()
        status.text = "Status: RUNNING — $message"
    }

    private fun finalizeRep(nowMs: Long) {
        val positives = repVelocities.filter { it > 0.0 }
        if (positives.isEmpty()) {
            phase = "idle"
            return
        }
        val mean = positives.average()
        val peak = positives.maxOrNull() ?: 0.0
        val meanPower = if (repPowerSamples.isNotEmpty()) repPowerSamples.average() else 0.0
        val peakPower = repPowerSamples.maxOrNull() ?: 0.0
        val rom = if (repPositions.isNotEmpty()) abs(repPositions.maxOrNull()!! - repPositions.minOrNull()!!) else 0.0
        val duration = (nowMs - repStartMs) / 1000.0

        // Final gate: small shakes and incomplete movements are rejected.
        if (
            rom < minRepRom ||
            peak < minPeakVelocity ||
            mean < minMeanVelocity ||
            duration * 1000.0 < minRepMs
        ) {
            rejectCandidate("Getaran kecil diabaikan")
            return
        }

        val best = max(reps.maxOfOrNull { it.meanVelocity } ?: mean, mean)
        val loss = max(0.0, (best - mean) / best * 100.0)

        reps.add(RepResult(reps.size + 1, mean, peak, rom, duration, loss, meanPower, peakPower))
        updateRepHistory()

        val load = inputLoad.text.toString().toDoubleOrNull() ?: 0.0
        val estimated1RM = if (load > 0 && mean < 2.5) load / max(0.15, 1.0 - mean * 0.30) else 0.0

        repText.text = "Rep: ${reps.size}"
        meanText.text = "Mean: %.2f m/s".format(mean)
        peakText.text = "Peak: %.2f m/s".format(peak)
        romText.text = "ROM: %.2f m".format(rom)
        lossText.text = "Loss: %.1f%%".format(loss)
        zoneText.text = "Zone: ${velocityZone(mean)}"
        estimated1RMText.text = "Est. 1RM: %.0f kg".format(estimated1RM)
        meanPowerText.text = "Mean Power: %.0f W".format(meanPower)
        peakPowerText.text = "Peak Power: %.0f W".format(peakPower)

        lastValidRepMs = nowMs
        phase = "idle"
        velocity = 0.0
        position = 0.0
        candidateStartMs = 0L
        candidateSamples = 0
        directionChanged = false
        concentricStartMs = 0L
        repVelocities.clear()
        repPowerSamples.clear()
        repPositions.clear()
        status.text = "Status: RUNNING — Rep ${reps.size} tersimpan"
    }


    private fun currentLoadKg(): Double =
        findViewById<EditText>(R.id.inputLoad).text.toString().toDoubleOrNull() ?: 0.0

    private fun currentBodyMassKg(): Double =
        inputBodyMass.text.toString().toDoubleOrNull() ?: 0.0

    private fun currentEffectiveBodyMassFraction(): Double =
        ((inputEffectiveBodyMass.text.toString().toDoubleOrNull() ?: 0.0) / 100.0)
            .coerceIn(0.0, 1.0)

    private fun velocityZone(v: Double): String = when {
        v >= 1.30 -> "Speed Strength"
        v >= 1.00 -> "Strength-Speed"
        v >= 0.75 -> "Power"
        v >= 0.50 -> "Accelerative Strength"
        else -> "Max Strength"
    }

    private fun updateRepHistory() {
        historyText.text = if (reps.isEmpty()) "Belum ada rep." else reps.joinToString("\n") {
            "Rep ${it.number} — MV %.2f | PV %.2f | MP %.0f W | PP %.0f W | ROM %.2f | Loss %.1f%%"
                .format(it.meanVelocity, it.peakVelocity, it.meanPower, it.peakPower, it.rom, it.velocityLoss)
        }
    }

    private fun saveSession() {
        val bestMean = reps.maxOfOrNull { it.meanVelocity } ?: 0.0
        val bestPeak = reps.maxOfOrNull { it.peakVelocity } ?: 0.0
        val finalLoss = reps.lastOrNull()?.velocityLoss ?: 0.0
        val load = inputLoad.text.toString().toDoubleOrNull() ?: 0.0
        val estimated1RM = if (load > 0 && bestMean < 2.5) load / max(0.15, 1.0 - bestMean * 0.30) else 0.0
        val totalVolume = load * reps.size
        val values = ContentValues().apply {
            put("timestamp", java.time.LocalDateTime.now().toString())
            put("athlete", inputAthlete.text.toString())
            put("exercise", inputExercise.text.toString())
            put("load", load)
            put("reps", reps.size)
            put("best_mean", bestMean)
            put("best_peak", bestPeak)
            put("final_loss", finalLoss)
            put("best_mean_power", reps.maxOfOrNull { it.meanPower } ?: 0.0)
            put("best_peak_power", reps.maxOfOrNull { it.peakPower } ?: 0.0)
            put("estimated_1rm", estimated1RM)
            put("total_volume", totalVolume)
        }
        db.writableDatabase.insert("sessions", null, values)
        Toast.makeText(this, "Sesi tersimpan.", Toast.LENGTH_SHORT).show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
