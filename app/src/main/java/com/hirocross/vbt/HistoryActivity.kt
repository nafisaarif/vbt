package com.hirocross.vbt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var content: TextView
    private var csvData = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        db = DatabaseHelper(this)
        content = findViewById(R.id.historyContent)
        loadHistory()
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportCsv() }
    }

    private fun loadHistory() {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT timestamp, athlete, exercise, load, reps, best_mean, best_peak, final_loss, estimated_1rm, total_volume FROM sessions ORDER BY id DESC",
            null
        )
        val display = StringBuilder()
        val csv = StringBuilder("Timestamp,Athlete,Exercise,Load,Reps,BestMean,BestPeak,FinalLoss,Estimated1RM,TotalVolume\n")
        while (cursor.moveToNext()) {
            val timestamp = cursor.getString(0)
            val athlete = cursor.getString(1)
            val exercise = cursor.getString(2)
            val load = cursor.getDouble(3)
            val reps = cursor.getInt(4)
            val bestMean = cursor.getDouble(5)
            val bestPeak = cursor.getDouble(6)
            val finalLoss = cursor.getDouble(7)
            val bestMeanPower = cursor.getDouble(8)
            val bestPeakPower = cursor.getDouble(9)
            val estimated1RM = cursor.getDouble(8)
            val totalVolume = cursor.getDouble(9)

            display.append("$timestamp\n$athlete • $exercise • ${load} kg\n")
            display.append("Reps $reps | Best mean %.2f | Best peak %.2f | Loss %.1f%% | 1RM %.0f | Volume %.0f\n\n"
                .format(bestMean, bestPeak, finalLoss, estimated1RM, totalVolume))

            csv.append("\"$timestamp\",\"$athlete\",\"$exercise\",$load,$reps,$bestMean,$bestPeak,$finalLoss,$estimated1RM,$totalVolume\n")
        }
        cursor.close()
        content.text = if (display.isEmpty()) "Belum ada sesi." else display.toString()
        csvData = csv.toString()
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "HiroCross VBT History")
            putExtra(Intent.EXTRA_TEXT, csvData)
        }
        startActivity(Intent.createChooser(intent, "Bagikan CSV"))
    }
}
