package com.hirocross.vbt

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        val db = DatabaseHelper(this)
        val cursor = db.readableDatabase.rawQuery("SELECT reps,best_mean,best_peak,final_loss,estimated_1rm,total_volume FROM sessions ORDER BY id ASC", null)
        var sessions=0; var reps=0; var bestMean=0.0; var bestPeak=0.0; var lossSum=0.0; var volume=0.0
        val means=mutableListOf<Float>(); val oneRms=mutableListOf<Float>(); val losses=mutableListOf<Float>()
        while(cursor.moveToNext()) {
            sessions++; reps += cursor.getInt(0); bestMean=maxOf(bestMean,cursor.getDouble(1)); bestPeak=maxOf(bestPeak,cursor.getDouble(2)); lossSum+=cursor.getDouble(3); volume+=cursor.getDouble(5)
            means.add(cursor.getDouble(1).toFloat()); oneRms.add(cursor.getDouble(4).toFloat()); losses.add(cursor.getDouble(3).toFloat())
        }
        cursor.close()
        findViewById<TextView>(R.id.cardSessions).text="Sessions\n$sessions"
        findViewById<TextView>(R.id.cardReps).text="Total Reps\n$reps"
        findViewById<TextView>(R.id.cardBestMean).text="Best Mean\n%.2f m/s".format(bestMean)
        findViewById<TextView>(R.id.cardBestPeak).text="Best Peak\n%.2f m/s".format(bestPeak)
        findViewById<TextView>(R.id.cardVolume).text="Volume\n%.0f kg".format(volume)
        findViewById<TextView>(R.id.cardAvgLoss).text="Avg Loss\n%.1f%%".format(if(sessions>0) lossSum/sessions else 0.0)
        findViewById<VelocityChartView>(R.id.chartMean).setValues(means)
        findViewById<VelocityChartView>(R.id.chartOneRm).setValues(oneRms)
        findViewById<VelocityChartView>(R.id.chartLoss).setValues(losses)
        findViewById<TextView>(R.id.dashboardSummary).text = if(sessions==0) "Belum ada data latihan." else "Selama $sessions sesi tercatat $reps repetisi dengan volume total %.0f kg. Mean velocity terbaik %.2f m/s dan estimasi 1RM tertinggi %.0f kg.".format(volume,bestMean,oneRms.maxOrNull()?:0f)
    }
}
