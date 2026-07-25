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

    private lateinit var inputAthlete: AutoCompleteTextView
    private lateinit var inputExercise: AutoCompleteTextView
    private lateinit var inputLoad: EditText
    private lateinit var inputBodyMass: EditText
    private lateinit var inputEffectiveBodyMass: EditText
    private lateinit var switchTargetVelocity: Switch
    private lateinit var inputTargetMin: EditText
    private lateinit var inputTargetMax: EditText
    private lateinit var targetStatusText: TextView
    private lateinit var exerciseInfoText: TextView
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
    private var previousFilteredA = 0.0
    private var velocity = 0.0
    private var position = 0.0
    private var stillSinceMs = 0L
    private var stableSampleCount = 0
    private var sensorSampleCount = 0
    private var calibrationNoise = 0.0

    private var phase = "idle"
    private var repStartMs = 0L
    private var concentricStartMs = 0L
    private var lastValidRepMs = 0L
    private var candidateStartMs = 0L
    private var candidateSamples = 0
    private var directionChanged = false
    private var upwardSampleCount = 0
    private var downwardSampleCount = 0
    private var maximumJerk = 0.0
    private val repVelocities = mutableListOf<Double>()
    private val repPowerSamples = mutableListOf<Double>()
    private val repPositions = mutableListOf<Double>()
    private val reps = mutableListOf<RepResult>()

    // Anti-vibration filtering and repetition validation
    private val alpha = 0.16
    private var startThreshold = 0.70
    private var stillThreshold = 0.14
    private val minRepMs = 650L
    private val maxRepMs = 6000L
    private val minConcentricMs = 250L
    private val minRepRom = 0.12
    private val minPeakVelocity = 0.18
    private val minMeanVelocity = 0.08
    private val minimumRepGapMs = 700L
    private val movementConfirmMs = 140L
    private val movementConfirmSamples = 7
    private val minDirectionalSamples = 5
    private val stationarySamplesRequired = 10
    private val velocityDeadBand = 0.035
    private val accelerationDeadBand = 0.10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        inputAthlete = findViewById(R.id.inputAthlete)
        inputExercise = findViewById(R.id.inputExercise)
        inputLoad = findViewById(R.id.inputLoad)
        inputBodyMass = findViewById(R.id.inputBodyMass)
        inputEffectiveBodyMass = findViewById(R.id.inputEffectiveBodyMass)
        switchTargetVelocity = findViewById(R.id.switchTargetVelocity)
        inputTargetMin = findViewById(R.id.inputTargetMin)
        inputTargetMax = findViewById(R.id.inputTargetMax)
        targetStatusText = findViewById(R.id.textTargetStatus)
        exerciseInfoText = findViewById(R.id.textExerciseInfo)
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

        loadTargetVelocitySettings()
        updateTargetVelocityConfiguration()
        setupExerciseLibrary()

        switchTargetVelocity.setOnCheckedChangeListener { _, _ ->
            updateTargetVelocityConfiguration()
            saveTargetVelocitySettings()
        }
        inputTargetMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTargetVelocityConfiguration()
                saveTargetVelocitySettings()
            }
        }
        inputTargetMax.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTargetVelocityConfiguration()
                saveTargetVelocitySettings()
            }
        }

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
        findViewById<Button>(R.id.btnAthletes).setOnClickListener {
            startActivity(Intent(this, AthleteActivity::class.java))
        }
        findViewById<Button>(R.id.btnVerticalJump).setOnClickListener {
            startActivity(Intent(this, VerticalJumpActivity::class.java))
        }
        setupAthleteSelector()
    }

    override fun onResume() {
        super.onResume()
        setupAthleteSelector()
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

        for (axis in 0..2) {
            bias[axis] = calibrationSamples.map { it[axis] }.average().toFloat()
        }

        val yMean = bias[1].toDouble()
        calibrationNoise = kotlin.math.sqrt(
            calibrationSamples
                .map { sample ->
                    val delta = sample[1].toDouble() - yMean
                    delta * delta
                }
                .average()
        )

        // Thresholds adapt to the actual noise level of each smartphone.
        stillThreshold = max(0.12, calibrationNoise * 3.0)
        startThreshold = max(0.65, calibrationNoise * 7.0)

        calibrating = false
        calibrated = true
        status.text = "Status: KALIBRASI SELESAI • noise %.3f m/s²".format(calibrationNoise)
    }

    private fun startSet() {

        if (!validateTargetVelocity(showMessage = true)) return
        updateTargetVelocityConfiguration()
        saveTargetVelocitySettings()
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
        previousFilteredA = 0.0
        velocity = 0.0
        position = 0.0
        stableSampleCount = 0
        sensorSampleCount = 0
        maximumJerk = 0.0
        upwardSampleCount = 0
        downwardSampleCount = 0
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
        targetStatusText.text =
            if (switchTargetVelocity.isChecked)
                "Target aktif: %.2f–%.2f m/s".format(currentTargetMin(), currentTargetMax())
            else "Target velocity tidak digunakan"
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
        previousFilteredA = filteredA
        filteredA = alpha * rawA + (1.0 - alpha) * filteredA
        sensorSampleCount++

        val jerk = abs(filteredA - previousFilteredA) / dt
        maximumJerk = max(maximumJerk, jerk)

        val nowMs = SystemClock.elapsedRealtime()
        val accelerationIsStill = abs(filteredA) < max(stillThreshold, accelerationDeadBand)

        if (accelerationIsStill) {
            stableSampleCount++
            if (stillSinceMs == 0L) stillSinceMs = nowMs
        } else {
            stableSampleCount = 0
            stillSinceMs = 0L
        }

        // Integrate only meaningful acceleration. This reduces noise-driven drift.
        val integrationAcceleration =
            if (abs(filteredA) < accelerationDeadBand) 0.0 else filteredA

        velocity += integrationAcceleration * dt
        velocity = max(-3.5, min(3.5, velocity))

        // Zero-velocity update when the device is consistently stationary.
        if (stableSampleCount >= stationarySamplesRequired) {
            velocity = 0.0
            if (phase == "idle") position = 0.0
        } else if (abs(velocity) < velocityDeadBand) {
            velocity = 0.0
        }

        if (phase != "idle" || candidateStartMs != 0L) {
            position += velocity * dt
        }

        val displayVelocity =
            if (phase == "idle" && candidateStartMs == 0L) 0.0 else abs(velocity)

        velocityText.text = "%.2f".format(displayVelocity)
        directionText.text = when {
            velocity > 0.06 -> "Arah: NAIK"
            velocity < -0.06 -> "Arah: TURUN"
            else -> "Arah: DIAM"
        }

        updateLiveTargetStatus(displayVelocity)
        chart.addValue(displayVelocity.toFloat())
        detectRep(nowMs)
    }

    private fun detectRep(nowMs: Long) {
        if (nowMs - lastValidRepMs < minimumRepGapMs) return

        if (phase == "idle") {
            val strongMovement =
                abs(filteredA) >= startThreshold &&
                abs(velocity) >= 0.05

            if (strongMovement) {
                if (candidateStartMs == 0L) {
                    candidateStartMs = nowMs
                    candidateSamples = 0
                    upwardSampleCount = 0
                    downwardSampleCount = 0
                    maximumJerk = 0.0
                }

                candidateSamples++
                if (velocity > 0.05) upwardSampleCount++
                if (velocity < -0.05) downwardSampleCount++

                val confirmed =
                    nowMs - candidateStartMs >= movementConfirmMs &&
                    candidateSamples >= movementConfirmSamples &&
                    max(upwardSampleCount, downwardSampleCount) >= minDirectionalSamples

                if (confirmed) {
                    phase = if (downwardSampleCount > upwardSampleCount) "down" else "up"
                    repStartMs = candidateStartMs
                    concentricStartMs = if (phase == "up") nowMs else 0L
                    directionChanged = phase == "up"

                    repVelocities.clear()
                    repPowerSamples.clear()
                    repPositions.clear()
                    repPositions.add(position)

                    candidateStartMs = 0L
                    candidateSamples = 0
                    upwardSampleCount = 0
                    downwardSampleCount = 0
                }
            } else {
                candidateStartMs = 0L
                candidateSamples = 0
                upwardSampleCount = 0
                downwardSampleCount = 0
            }
            return
        }

        repPositions.add(position)

        if (phase == "down") {
            if (velocity < -0.05) downwardSampleCount++ else downwardSampleCount = 0

            if (velocity > 0.08) {
                upwardSampleCount++
                if (upwardSampleCount >= minDirectionalSamples) {
                    phase = "up"
                    directionChanged = true
                    concentricStartMs = nowMs
                    repVelocities.clear()
                    repPowerSamples.clear()
                }
            } else {
                upwardSampleCount = 0
            }
        }

        if (phase == "up") {
            if (velocity > 0.05) {
                upwardSampleCount++
                repVelocities.add(velocity)

                val movingMass =
                    max(0.0, currentLoadKg() + currentBodyMassKg() * currentEffectiveBodyMassFraction())
                val verticalForce = movingMass * max(0.0, 9.80665 + filteredA)
                repPowerSamples.add(max(0.0, verticalForce * velocity))
            }

            val totalDuration = nowMs - repStartMs
            val concentricDuration =
                if (concentricStartMs > 0L) nowMs - concentricStartMs else 0L
            val currentRom =
                if (repPositions.size > 1)
                    abs((repPositions.maxOrNull() ?: 0.0) - (repPositions.minOrNull() ?: 0.0))
                else 0.0

            val returnedToStill =
                stableSampleCount >= stationarySamplesRequired &&
                abs(velocity) <= velocityDeadBand

            if (returnedToStill) {
                val validCandidate =
                    totalDuration >= minRepMs &&
                    concentricDuration >= minConcentricMs &&
                    directionChanged &&
                    upwardSampleCount >= minDirectionalSamples &&
                    currentRom >= minRepRom

                if (validCandidate) {
                    finalizeRep(nowMs)
                } else {
                    rejectCandidate("Gerakan kecil diabaikan")
                }
            }

            if (totalDuration > maxRepMs) {
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
        upwardSampleCount = 0
        downwardSampleCount = 0
        maximumJerk = 0.0
        stableSampleCount = 0
        stillSinceMs = 0L
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
        val trim = max(0, (positives.size * 0.05).toInt())
        val stableVelocities =
            if (positives.size > trim * 2 + 4)
                positives.subList(trim, positives.size - trim)
            else positives

        val mean = stableVelocities.average()
        val peak = stableVelocities.maxOrNull() ?: 0.0

        val stablePowers =
            if (repPowerSamples.size > trim * 2 + 4)
                repPowerSamples.subList(trim, repPowerSamples.size - trim)
            else repPowerSamples

        val meanPower = if (stablePowers.isNotEmpty()) stablePowers.average() else 0.0
        val peakPower = stablePowers.maxOrNull() ?: 0.0
        val rom = if (repPositions.isNotEmpty()) abs(repPositions.maxOrNull()!! - repPositions.minOrNull()!!) else 0.0
        val duration = (nowMs - repStartMs) / 1000.0

        // Final gate: small shakes and incomplete movements are rejected.
        if (
            rom < minRepRom ||
            peak < minPeakVelocity ||
            mean < minMeanVelocity ||
            stableVelocities.size < minDirectionalSamples ||
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
        updateCompletedRepTargetStatus(mean)
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
        upwardSampleCount = 0
        downwardSampleCount = 0
        maximumJerk = 0.0
        stableSampleCount = 0
        stillSinceMs = 0L
        repVelocities.clear()
        repPowerSamples.clear()
        repPositions.clear()
        status.text = "Status: RUNNING — Rep ${reps.size} tersimpan"
    }





    private fun setupAthleteSelector() {
        val names = mutableListOf<String>()
        val cursor = db.readableDatabase.rawQuery(
            "SELECT name FROM athletes ORDER BY name ASC", null
        )
        while (cursor.moveToNext()) names.add(cursor.getString(0))
        cursor.close()

        inputAthlete.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        inputAthlete.threshold = 0
        inputAthlete.setOnClickListener { inputAthlete.showDropDown() }

        inputAthlete.setOnItemClickListener { _, _, _, _ ->
            val selected = inputAthlete.text.toString().trim()
            val athleteCursor = db.readableDatabase.rawQuery(
                "SELECT body_mass FROM athletes WHERE name=?", arrayOf(selected)
            )
            if (athleteCursor.moveToFirst()) {
                val mass = athleteCursor.getDouble(0)
                if (mass > 0) inputBodyMass.setText("%.1f".format(mass))
            }
            athleteCursor.close()
        }
    }

    private data class ExerciseProfile(
        val name: String,
        val category: String,
        val movementType: String,
        val recommendedRomMin: Double,
        val recommendedRomMax: Double,
        val note: String
    )

    private val exerciseProfiles = listOf(
        ExerciseProfile("Back Squat", "Lower Body", "Vertical", 0.35, 0.70, "Gunakan pemasangan HP yang stabil pada barbell."),
        ExerciseProfile("Front Squat", "Lower Body", "Vertical", 0.35, 0.65, "Posisi bar lebih anterior; jaga holder tidak bergeser."),
        ExerciseProfile("Box Squat", "Lower Body", "Vertical", 0.25, 0.55, "Pastikan jeda di box tidak dianggap akhir set."),
        ExerciseProfile("Bulgarian Split Squat", "Lower Body", "Vertical", 0.20, 0.50, "Gunakan sisi pemasangan yang konsisten."),
        ExerciseProfile("Walking Lunge", "Lower Body", "Vertical", 0.20, 0.50, "Analisis terbaik dilakukan per sisi atau per langkah."),
        ExerciseProfile("Deadlift", "Lower Body", "Vertical", 0.35, 0.75, "Mulai dari posisi diam sebelum setiap repetisi."),
        ExerciseProfile("Romanian Deadlift", "Lower Body", "Vertical", 0.30, 0.65, "Gerakan eksentrik dan konsentrik harus jelas."),
        ExerciseProfile("Trap Bar Deadlift", "Lower Body", "Vertical", 0.30, 0.70, "Tempatkan HP sedekat mungkin dengan pusat bar."),
        ExerciseProfile("Hip Thrust", "Lower Body", "Vertical", 0.15, 0.40, "ROM lebih pendek; pemasangan harus sangat stabil."),
        ExerciseProfile("Leg Press", "Lower Body", "Diagonal", 0.20, 0.55, "Sumbu sensor harus mengikuti arah lintasan sled."),
        ExerciseProfile("Bench Press", "Upper Body", "Vertical", 0.20, 0.45, "Gunakan holder pada bar, bukan pada tubuh."),
        ExerciseProfile("Incline Bench Press", "Upper Body", "Diagonal", 0.20, 0.45, "Sumbu sensor harus mengikuti arah bar."),
        ExerciseProfile("Close Grip Bench Press", "Upper Body", "Vertical", 0.20, 0.45, "Gunakan teknik dan ROM yang konsisten."),
        ExerciseProfile("Overhead Press", "Upper Body", "Vertical", 0.30, 0.65, "Pastikan lintasan bar tidak tertahan holder."),
        ExerciseProfile("Push Press", "Upper Body", "Vertical", 0.35, 0.75, "Gerakan eksplosif; holder harus terkunci kuat."),
        ExerciseProfile("Bent Over Row", "Upper Body", "Vertical", 0.15, 0.40, "Gunakan threshold lebih hati-hati karena ROM pendek."),
        ExerciseProfile("Pendlay Row", "Upper Body", "Vertical", 0.15, 0.45, "Setiap rep dimulai dari posisi bar diam."),
        ExerciseProfile("Power Clean", "Olympic Weightlifting", "Vertical", 0.60, 1.20, "Gerakan cepat; gunakan holder berpengaman."),
        ExerciseProfile("Hang Power Clean", "Olympic Weightlifting", "Vertical", 0.45, 1.00, "Mulai dari posisi hang yang konsisten."),
        ExerciseProfile("Clean Pull", "Olympic Weightlifting", "Vertical", 0.55, 1.10, "Fokus pada fase tarik tanpa catch."),
        ExerciseProfile("Power Snatch", "Olympic Weightlifting", "Vertical", 0.70, 1.30, "Gunakan hanya jika pemasangan HP benar-benar aman."),
        ExerciseProfile("Hang Power Snatch", "Olympic Weightlifting", "Vertical", 0.55, 1.10, "Pastikan start position konsisten."),
        ExerciseProfile("Snatch Pull", "Olympic Weightlifting", "Vertical", 0.65, 1.25, "Analisis fase tarik vertikal."),
        ExerciseProfile("Push Jerk", "Olympic Weightlifting", "Vertical", 0.45, 0.90, "Kecepatan tinggi; holder harus sangat kuat."),
        ExerciseProfile("Split Jerk", "Olympic Weightlifting", "Vertical", 0.45, 0.95, "Gunakan untuk bar path vertikal."),
        ExerciseProfile("Calf Raise", "Accessory", "Vertical", 0.05, 0.20, "ROM sangat pendek; hasil perlu diuji khusus."),
        ExerciseProfile("Step Up", "Accessory", "Vertical", 0.20, 0.50, "Gunakan tinggi box yang sama setiap sesi.")
    )

    private fun setupExerciseLibrary() {
        val names = exerciseProfiles.map { it.name }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            names
        )
        inputExercise.setAdapter(adapter)
        inputExercise.threshold = 0

        val savedExercise = getSharedPreferences(
            "hirocross_vbt_settings",
            Context.MODE_PRIVATE
        ).getString("selected_exercise", "Back Squat") ?: "Back Squat"

        inputExercise.setText(savedExercise, false)
        updateExerciseInformation(savedExercise)

        inputExercise.setOnClickListener {
            inputExercise.showDropDown()
        }

        inputExercise.setOnItemClickListener { _, _, position, _ ->
            val exerciseName = names[position]
            inputExercise.setText(exerciseName, false)
            updateExerciseInformation(exerciseName)
            saveSelectedExercise(exerciseName)
        }

        inputExercise.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val typedName = inputExercise.text.toString().trim()
                val matched = exerciseProfiles.firstOrNull {
                    it.name.equals(typedName, ignoreCase = true)
                }
                if (matched != null) {
                    inputExercise.setText(matched.name, false)
                    updateExerciseInformation(matched.name)
                    saveSelectedExercise(matched.name)
                } else {
                    exerciseInfoText.text =
                        "Exercise manual • parameter deteksi menggunakan pengaturan umum"
                    saveSelectedExercise(typedName.ifBlank { "Back Squat" })
                }
            }
        }
    }

    private fun updateExerciseInformation(exerciseName: String) {
        val profile = exerciseProfiles.firstOrNull { it.name == exerciseName }
        exerciseInfoText.text =
            if (profile != null) {
                "${profile.category} • ${profile.movementType} • ROM referensi %.2f–%.2f m\n%s"
                    .format(
                        profile.recommendedRomMin,
                        profile.recommendedRomMax,
                        profile.note
                    )
            } else {
                "Exercise manual • parameter deteksi menggunakan pengaturan umum"
            }
    }

    private fun saveSelectedExercise(exerciseName: String) {
        getSharedPreferences("hirocross_vbt_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_exercise", exerciseName)
            .apply()
    }

    private fun currentTargetMin(): Double =
        inputTargetMin.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.75

    private fun currentTargetMax(): Double =
        inputTargetMax.text.toString().replace(",", ".").toDoubleOrNull() ?: 1.00

    private fun validateTargetVelocity(showMessage: Boolean): Boolean {
        if (!switchTargetVelocity.isChecked) return true

        val minimum = inputTargetMin.text.toString().replace(",", ".").toDoubleOrNull()
        val maximum = inputTargetMax.text.toString().replace(",", ".").toDoubleOrNull()

        val valid = minimum != null &&
            maximum != null &&
            minimum > 0.0 &&
            maximum >= minimum

        if (!valid && showMessage) {
            Toast.makeText(
                this,
                "Target velocity tidak valid. Batas maksimum harus sama dengan atau lebih besar dari batas minimum.",
                Toast.LENGTH_LONG
            ).show()
        }
        return valid
    }

    private fun updateTargetVelocityConfiguration() {
        val enabled = switchTargetVelocity.isChecked
        inputTargetMin.isEnabled = enabled
        inputTargetMax.isEnabled = enabled
        inputTargetMin.alpha = if (enabled) 1.0f else 0.45f
        inputTargetMax.alpha = if (enabled) 1.0f else 0.45f

        if (enabled && validateTargetVelocity(showMessage = false)) {
            val minimum = currentTargetMin()
            val maximum = currentTargetMax()
            chart.setTargetVelocity(true, minimum.toFloat(), maximum.toFloat())
            targetStatusText.text = "Target aktif: %.2f–%.2f m/s".format(minimum, maximum)
        } else {
            chart.setTargetVelocity(false, 0f, 0f)
            targetStatusText.text = "Target velocity tidak digunakan"
        }
    }

    private fun updateLiveTargetStatus(currentVelocity: Double) {
        if (!switchTargetVelocity.isChecked || !validateTargetVelocity(false)) return

        val minimum = currentTargetMin()
        val maximum = currentTargetMax()

        targetStatusText.text = when {
            currentVelocity <= 0.0 ->
                "Target aktif: %.2f–%.2f m/s".format(minimum, maximum)
            currentVelocity < minimum ->
                "Di bawah target • %.2f m/s".format(currentVelocity)
            currentVelocity <= maximum ->
                "Target tercapai • %.2f m/s".format(currentVelocity)
            else ->
                "Di atas %.2f m/s • tanpa peringatan".format(maximum)
        }
    }

    private fun updateCompletedRepTargetStatus(meanVelocity: Double) {
        if (!switchTargetVelocity.isChecked || !validateTargetVelocity(false)) return

        val minimum = currentTargetMin()
        val maximum = currentTargetMax()
        targetStatusText.text = when {
            meanVelocity < minimum ->
                "Rep di bawah target • Mean %.2f m/s".format(meanVelocity)
            meanVelocity <= maximum ->
                "Rep sesuai target • Mean %.2f m/s".format(meanVelocity)
            else ->
                "Rep lebih cepat dari target • Mean %.2f m/s".format(meanVelocity)
        }
    }

    private fun saveTargetVelocitySettings() {
        getSharedPreferences("hirocross_vbt_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("target_enabled", switchTargetVelocity.isChecked)
            .putString("target_min", inputTargetMin.text.toString())
            .putString("target_max", inputTargetMax.text.toString())
            .apply()
    }

    private fun loadTargetVelocitySettings() {
        val preferences =
            getSharedPreferences("hirocross_vbt_settings", Context.MODE_PRIVATE)

        switchTargetVelocity.isChecked =
            preferences.getBoolean("target_enabled", false)
        inputTargetMin.setText(preferences.getString("target_min", "0.75"))
        inputTargetMax.setText(preferences.getString("target_max", "1.00"))
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
