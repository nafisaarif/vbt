package com.hirocross.vbt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "hirocross_vbt.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        createSessions(db)
        createAthletes(db)
        createVerticalJump(db)
    }

    private fun createSessions(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sessions (
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

    private fun createAthletes(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS athletes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                sex TEXT NOT NULL DEFAULT '',
                birth_year INTEGER NOT NULL DEFAULT 0,
                sport TEXT NOT NULL DEFAULT '',
                body_mass REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )
        """.trimIndent())
    }

    private fun createVerticalJump(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vertical_jump_tests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                athlete TEXT NOT NULL,
                test_type TEXT NOT NULL,
                flight_time_ms REAL NOT NULL,
                jump_height_cm REAL NOT NULL,
                body_mass REAL NOT NULL DEFAULT 0,
                peak_power_w REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createSessions(db)
        if (oldVersion < 4) {
            createAthletes(db)
            createVerticalJump(db)
        }
    }
}
