package com.hirocross.vbt

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var athleteFilter: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        db = DatabaseHelper(this)
        athleteFilter = findViewById(R.id.filterDashboardAthlete)
        loadAthleteFilter()
        renderDashboard("")

        athleteFilter.setOnItemClickListener { _, _, _, _ ->
            renderDashboard(athleteFilter.text.toString().trim())
        }
        findViewById<Button>(R.id.btnDashboardAll).setOnClickListener {
            athleteFilter.setText("")
            renderDashboard("")
        }
    }

    private fun loadAthleteFilter() {
        val names = mutableListOf<String>()
        val c = db.readableDatabase.rawQuery(
            "SELECT DISTINCT athlete FROM sessions ORDER BY athlete ASC", null
        )
        while (c.moveToNext()) names.add(c.getString(0))
        c.close()
        athleteFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        athleteFilter.threshold = 0
        athleteFilter.setOnClickListener { athleteFilter.showDropDown() }
    }

    private fun renderDashboard(athlete: String) {
        val where = if (athlete.isBlank()) "" else " WHERE athlete=?"
        val args = if (athlete.isBlank()) null else arrayOf(athlete)
        val cursor = db.readableDatabase.rawQuery(
            "SELECT reps,best_mean,best_peak,final_loss,estimated_1rm,total_volume," +
                "best_mean_power,best_peak_power FROM sessions$where ORDER BY id ASC", args
        )

        var sessions = 0
        var reps = 0
        var bestMean = 0.0
        var bestPeak = 0.0
        var lossSum = 0.0
        var volume = 0.0
        var bestMeanPower = 0.0
        var bestPeakPower = 0.0
        val means = mutableListOf<Float>()
        val oneRms = mutableListOf<Float>()
        val losses = mutableListOf<Float>()

        while (cursor.moveToNext()) {
            sessions++
            reps += cursor.getInt(0)
            bestMean = maxOf(bestMean, cursor.getDouble(1))
            bestPeak = maxOf(bestPeak, cursor.getDouble(2))
            lossSum += cursor.getDouble(3)
            oneRms.add(cursor.getDouble(4).toFloat())
            volume += cursor.getDouble(5)
            bestMeanPower = maxOf(bestMeanPower, cursor.getDouble(6))
            bestPeakPower = maxOf(bestPeakPower, cursor.getDouble(7))
            means.add(cursor.getDouble(1).toFloat())
            losses.add(cursor.getDouble(3).toFloat())
        }
        cursor.close()

        val vjCursor = db.readableDatabase.rawQuery(
            if (athlete.isBlank())
                "SELECT MAX(jump_height_cm) FROM vertical_jump_tests"
            else
                "SELECT MAX(jump_height_cm) FROM vertical_jump_tests WHERE athlete=?",
            args
        )
        var bestVj = 0.0
        if (vjCursor.moveToFirst() && !vjCursor.isNull(0)) bestVj = vjCursor.getDouble(0)
        vjCursor.close()

        findViewById<TextView>(R.id.cardSessions).text = "Sessions\n$sessions"
        findViewById<TextView>(R.id.cardReps).text = "Total Reps\n$reps"
        findViewById<TextView>(R.id.cardBestMean).text = "Best Mean\n%.2f m/s".format(bestMean)
        findViewById<TextView>(R.id.cardBestPeak).text = "Best Peak\n%.2f m/s".format(bestPeak)
        findViewById<TextView>(R.id.cardVolume).text = "Volume\n%.0f kg".format(volume)
        findViewById<TextView>(R.id.cardAvgLoss).text =
            "Avg Loss\n%.1f%%".format(if (sessions > 0) lossSum / sessions else 0.0)
        findViewById<TextView>(R.id.cardPower).text =
            "Best Power\n%.0f W".format(bestPeakPower)
        findViewById<TextView>(R.id.cardVerticalJump).text =
            "Best VJ\n%.1f cm".format(bestVj)

        findViewById<VelocityChartView>(R.id.chartMean).setValues(means)
        findViewById<VelocityChartView>(R.id.chartOneRm).setValues(oneRms)
        findViewById<VelocityChartView>(R.id.chartLoss).setValues(losses)

        val subject = if (athlete.isBlank()) "semua atlet" else athlete
        findViewById<TextView>(R.id.dashboardSummary).text =
            if (sessions == 0)
                "Belum ada data latihan untuk $subject."
            else
                "Ringkasan $subject: $sessions sesi, $reps repetisi, volume %.0f kg, " +
                    "mean terbaik %.2f m/s, peak power %.0f W, dan Vertical Jump terbaik %.1f cm."
                    .format(volume, bestMean, bestPeakPower, bestVj)
    }
}
