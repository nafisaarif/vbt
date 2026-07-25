package com.hirocross.vbt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "hirocross_vbt.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                athlete TEXT NOT NULL,
                exercise TEXT NOT NULL,
                load REAL NOT NULL,
                reps INTEGER NOT NULL,
                best_mean REAL NOT NULL,
                best_peak REAL NOT NULL,
                final_loss REAL NOT NULL,
                best_mean_power REAL NOT NULL DEFAULT 0,
                best_peak_power REAL NOT NULL DEFAULT 0,
                estimated_1rm REAL NOT NULL,
                total_volume REAL NOT NULL
            )
        """.trimIndent())
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sessions")
        onCreate(db)
    }
}
