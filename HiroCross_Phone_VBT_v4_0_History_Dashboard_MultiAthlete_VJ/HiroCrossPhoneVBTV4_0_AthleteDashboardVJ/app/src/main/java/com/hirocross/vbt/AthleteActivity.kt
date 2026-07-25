package com.hirocross.vbt

import android.content.ContentValues
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AthleteActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var nameInput: EditText
    private lateinit var sexInput: EditText
    private lateinit var yearInput: EditText
    private lateinit var sportInput: EditText
    private lateinit var massInput: EditText
    private lateinit var listText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_athletes)
        db = DatabaseHelper(this)

        nameInput = findViewById(R.id.inputAthleteName)
        sexInput = findViewById(R.id.inputAthleteSex)
        yearInput = findViewById(R.id.inputBirthYear)
        sportInput = findViewById(R.id.inputSport)
        massInput = findViewById(R.id.inputAthleteMass)
        listText = findViewById(R.id.athleteList)

        findViewById<Button>(R.id.btnSaveAthlete).setOnClickListener { saveAthlete() }
        findViewById<Button>(R.id.btnDeleteAthlete).setOnClickListener { deleteAthlete() }
        loadAthletes()
    }

    private fun saveAthlete() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Nama atlet wajib diisi.", Toast.LENGTH_SHORT).show()
            return
        }
        val values = ContentValues().apply {
            put("name", name)
            put("sex", sexInput.text.toString().trim())
            put("birth_year", yearInput.text.toString().toIntOrNull() ?: 0)
            put("sport", sportInput.text.toString().trim())
            put("body_mass", massInput.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0)
            put("created_at", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        }
        db.writableDatabase.insertWithOnConflict(
            "athletes", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        Toast.makeText(this, "Profil atlet tersimpan.", Toast.LENGTH_SHORT).show()
        loadAthletes()
    }

    private fun deleteAthlete() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) return
        db.writableDatabase.delete("athletes", "name=?", arrayOf(name))
        Toast.makeText(this, "Profil atlet dihapus.", Toast.LENGTH_SHORT).show()
        loadAthletes()
    }

    private fun loadAthletes() {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT name, sex, birth_year, sport, body_mass FROM athletes ORDER BY name ASC", null
        )
        val builder = StringBuilder()
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val sex = cursor.getString(1)
            val year = cursor.getInt(2)
            val sport = cursor.getString(3)
            val mass = cursor.getDouble(4)
            builder.append(name).append("\n")
            builder.append("$sex • $sport • Tahun lahir $year • %.1f kg\n\n".format(mass))
        }
        cursor.close()
        listText.text = if (builder.isEmpty()) "Belum ada profil atlet." else builder.toString()
    }
}
