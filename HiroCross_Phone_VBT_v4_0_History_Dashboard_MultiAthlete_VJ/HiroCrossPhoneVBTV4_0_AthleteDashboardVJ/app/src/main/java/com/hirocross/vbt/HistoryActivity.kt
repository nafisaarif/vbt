package com.hirocross.vbt

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var athleteFilter: AutoCompleteTextView
    private lateinit var content: TextView
    private var csvData = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        db = DatabaseHelper(this)
        athleteFilter = findViewById(R.id.filterHistoryAthlete)
        content = findViewById(R.id.historyContent)

        loadAthleteFilter()
        loadHistory("")

        athleteFilter.setOnItemClickListener { _, _, _, _ ->
            loadHistory(athleteFilter.text.toString().trim())
        }
        findViewById<Button>(R.id.btnShowAllHistory).setOnClickListener {
            athleteFilter.setText("")
            loadHistory("")
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportCsv() }
    }

    private fun loadAthleteFilter() {
        val names = mutableListOf<String>()
        val cursor = db.readableDatabase.rawQuery(
            "SELECT DISTINCT athlete FROM sessions ORDER BY athlete ASC", null
        )
        while (cursor.moveToNext()) names.add(cursor.getString(0))
        cursor.close()
        athleteFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        athleteFilter.threshold = 0
        athleteFilter.setOnClickListener { athleteFilter.showDropDown() }
    }

    private fun loadHistory(athlete: String) {
        val query = if (athlete.isBlank())
            "SELECT timestamp,athlete,exercise,load,reps,best_mean,best_peak,final_loss," +
                "best_mean_power,best_peak_power,estimated_1rm,total_volume FROM sessions ORDER BY id DESC"
        else
            "SELECT timestamp,athlete,exercise,load,reps,best_mean,best_peak,final_loss," +
                "best_mean_power,best_peak_power,estimated_1rm,total_volume FROM sessions " +
                "WHERE athlete=? ORDER BY id DESC"

        val args = if (athlete.isBlank()) null else arrayOf(athlete)
        val cursor = db.readableDatabase.rawQuery(query, args)
        val display = StringBuilder()
        val csv = StringBuilder(
            "Timestamp,Athlete,Exercise,Load,Reps,BestMean,BestPeak,FinalLoss," +
                "BestMeanPower,BestPeakPower,Estimated1RM,TotalVolume\n"
        )

        while (cursor.moveToNext()) {
            val timestamp = cursor.getString(0)
            val athleteName = cursor.getString(1)
            val exercise = cursor.getString(2)
            val load = cursor.getDouble(3)
            val reps = cursor.getInt(4)
            val bestMean = cursor.getDouble(5)
            val bestPeak = cursor.getDouble(6)
            val finalLoss = cursor.getDouble(7)
            val meanPower = cursor.getDouble(8)
            val peakPower = cursor.getDouble(9)
            val estimated1RM = cursor.getDouble(10)
            val totalVolume = cursor.getDouble(11)

            display.append("$timestamp\n$athleteName • $exercise • %.1f kg\n".format(load))
            display.append(
                "Reps $reps | Mean %.2f | Peak %.2f | Loss %.1f%%\n" +
                    "Power %.0f/%.0f W | Est. 1RM %.0f kg | Volume %.0f kg\n\n"
                    .format(bestMean, bestPeak, finalLoss, meanPower, peakPower, estimated1RM, totalVolume)
            )
            csv.append(
                "\"$timestamp\",\"$athleteName\",\"$exercise\",$load,$reps,$bestMean,$bestPeak," +
                    "$finalLoss,$meanPower,$peakPower,$estimated1RM,$totalVolume\n"
            )
        }
        cursor.close()
        content.text = if (display.isEmpty()) "Belum ada sesi." else display.toString()
        csvData = csv.toString()
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "HiroCross VBT Session History")
            putExtra(Intent.EXTRA_TEXT, csvData)
        }
        startActivity(Intent.createChooser(intent, "Bagikan CSV"))
    }
}
