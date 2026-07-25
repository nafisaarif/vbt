package com.hirocross.vbt

import android.content.ContentValues
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

class VerticalJumpActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var athleteInput: AutoCompleteTextView
    private lateinit var testTypeInput: Spinner
    private lateinit var flightTimeInput: EditText
    private lateinit var bodyMassInput: EditText
    private lateinit var resultText: TextView
    private lateinit var historyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vertical_jump)
        db = DatabaseHelper(this)

        athleteInput = findViewById(R.id.inputVjAthlete)
        testTypeInput = findViewById(R.id.spinnerVjType)
        flightTimeInput = findViewById(R.id.inputFlightTime)
        bodyMassInput = findViewById(R.id.inputVjBodyMass)
        resultText = findViewById(R.id.textVjResult)
        historyText = findViewById(R.id.textVjHistory)

        val testTypes = listOf("Countermovement Jump", "Squat Jump", "Drop Jump")
        testTypeInput.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, testTypes
        )

        loadAthleteNames()
        loadHistory()

        findViewById<Button>(R.id.btnCalculateVj).setOnClickListener { calculateAndSave() }
    }

    private fun loadAthleteNames() {
        val names = mutableListOf<String>()
        val cursor = db.readableDatabase.rawQuery("SELECT name FROM athletes ORDER BY name ASC", null)
        while (cursor.moveToNext()) names.add(cursor.getString(0))
        cursor.close()
        athleteInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        athleteInput.threshold = 0
        athleteInput.setOnClickListener { athleteInput.showDropDown() }
    }

    private fun calculateAndSave() {
        val athlete = athleteInput.text.toString().trim()
        val flightTimeMs = flightTimeInput.text.toString().replace(",", ".").toDoubleOrNull()
        val bodyMass = bodyMassInput.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0

        if (athlete.isBlank() || flightTimeMs == null || flightTimeMs <= 0) {
            Toast.makeText(this, "Isi atlet dan flight time dengan benar.", Toast.LENGTH_LONG).show()
            return
        }

        val flightTimeSeconds = flightTimeMs / 1000.0
        val jumpHeightM = 9.80665 * flightTimeSeconds.pow(2) / 8.0
        val jumpHeightCm = jumpHeightM * 100.0

        // Sayers equation; used only when body mass is available.
        val peakPower = if (bodyMass > 0)
            60.7 * jumpHeightCm + 45.3 * bodyMass - 2055.0
        else 0.0

        resultText.text = buildString {
            append("Jump Height\n%.1f cm\n".format(jumpHeightCm))
            append("Flight Time\n%.0f ms\n".format(flightTimeMs))
            if (peakPower > 0) append("Estimated Peak Power\n%.0f W".format(peakPower))
        }

        val values = ContentValues().apply {
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("athlete", athlete)
            put("test_type", testTypeInput.selectedItem.toString())
            put("flight_time_ms", flightTimeMs)
            put("jump_height_cm", jumpHeightCm)
            put("body_mass", bodyMass)
            put("peak_power_w", peakPower)
        }
        db.writableDatabase.insert("vertical_jump_tests", null, values)
        loadHistory()
    }

    private fun loadHistory() {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT timestamp, athlete, test_type, jump_height_cm, flight_time_ms, peak_power_w " +
                "FROM vertical_jump_tests ORDER BY id DESC LIMIT 30", null
        )
        val builder = StringBuilder()
        while (cursor.moveToNext()) {
            builder.append(cursor.getString(0)).append("\n")
            builder.append(cursor.getString(1)).append(" • ").append(cursor.getString(2)).append("\n")
            builder.append("%.1f cm • %.0f ms".format(cursor.getDouble(3), cursor.getDouble(4)))
            val power = cursor.getDouble(5)
            if (power > 0) builder.append(" • %.0f W".format(power))
            builder.append("\n\n")
        }
        cursor.close()
        historyText.text = if (builder.isEmpty()) "Belum ada hasil Vertical Jump." else builder.toString()
    }
}
