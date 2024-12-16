package com.onenote.android

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {

        // Database properties
        private const val DATABASE_NAME = "onenote"
        private const val DATABASE_TABLE_NAME = "notes"

        // Version auf 2 erhöht, um die neue Spalte hinzuzufügen
        private const val DATABASE_VERSION = 2

        // Database table column names
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_IMAGEPATH = "imagePath"  // Neue Spalte

        // Create table statement (mit imagePath)
        private const val CREATE_TABLE = """CREATE TABLE $DATABASE_TABLE_NAME(
                $KEY_ID INTEGER PRIMARY KEY,
                $KEY_TITLE TEXT,
                $KEY_MESSAGE TEXT,
                $KEY_IMAGEPATH TEXT
        )"""

        // Database cursor array (imagePath hinzugefügt)
        private val CURSOR_ARRAY = arrayOf(
            KEY_ID,
            KEY_TITLE,
            KEY_MESSAGE,
            KEY_IMAGEPATH
        )

        // Select all statement
        private const val SELECT_ALL = "SELECT * FROM $DATABASE_TABLE_NAME"
    }

    // Insert note into database
    fun insertNote(note: Note): Long {
        val values = noteToContentValues(note)
        return writableDatabase.insert(DATABASE_TABLE_NAME, null, values)
    }

    // Create new ContentValues object note
    private fun noteToContentValues(note: Note): ContentValues {
        val values = ContentValues()
        values.put(KEY_TITLE, note.title)
        values.put(KEY_MESSAGE, note.message)
        values.put(KEY_IMAGEPATH, note.imagePath) // imagePath hinzufügen
        return values
    }

    // Get single note from database
    fun getNote(id: Long): Note? {
        val cursor = readableDatabase.query(
            DATABASE_TABLE_NAME, CURSOR_ARRAY, "$KEY_ID=?",
            arrayOf(id.toString()), null, null, null, null
        )

        val note = if (cursor.moveToFirst()) {
            cursorToNote(cursor)
        } else {
            null
        }
        cursor.close()

        return note
    }

    // Get all notes from database
    fun getAllNotes(): List<Note> {
        val notes = ArrayList<Note>()
        val cursor = readableDatabase.rawQuery(SELECT_ALL, null)

        if (cursor.moveToFirst()) {
            do {
                cursorToNote(cursor)?.let {
                    notes.add(it)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        return notes
    }

    @SuppressLint("Range")
    private fun cursorToNote(cursor: Cursor): Note? {
        if (cursor.count == 0) return null
        return Note(
            title = cursor.getString(cursor.getColumnIndex(KEY_TITLE)),
            message = cursor.getString(cursor.getColumnIndex(KEY_MESSAGE)),
            id = cursor.getLong(cursor.getColumnIndex(KEY_ID)),
            imagePath = cursor.getString(cursor.getColumnIndex(KEY_IMAGEPATH)) // imagePath auslesen
        )
    }

    // Update single note
    fun updateNote(note: Note): Int {
        return writableDatabase.update(
            DATABASE_TABLE_NAME,
            noteToContentValues(note),
            "$KEY_ID=?",
            arrayOf(note.id.toString())
        )
    }

    // Delete single note
    fun deleteNote(id: Long) {
        writableDatabase.delete(
            DATABASE_TABLE_NAME,
            "$KEY_ID=?",
            arrayOf(id.toString())
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Wenn die Datenbank von Version 1 auf 2 angehoben wird, fügen wir die imagePath-Spalte hinzu
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $DATABASE_TABLE_NAME ADD COLUMN $KEY_IMAGEPATH TEXT")
        }
    }
}