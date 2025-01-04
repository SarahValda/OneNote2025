package com.onenote.android

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


/**
 * Handles SQLite database operations for managing notes.
 * Supports creation, updates, deletion, and querying of notes with attributes like title, message, image path, and location.
 */

class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {

        private const val DATABASE_NAME = "onenote"
        private const val DATABASE_TABLE_NAME = "notes"
        private const val DATABASE_VERSION = 3

        // Columns for the notes table
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_IMAGEPATH = "imagePath"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"

        // SQL to create the table with all fields including imagePath, latitude, longitude
        private const val CREATE_TABLE = """CREATE TABLE $DATABASE_TABLE_NAME(
                $KEY_ID INTEGER PRIMARY KEY,
                $KEY_TITLE TEXT,
                $KEY_MESSAGE TEXT,
                $KEY_IMAGEPATH TEXT,
                $KEY_LATITUDE REAL,
                $KEY_LONGITUDE REAL
        )"""

        // Cursor array for columns
        private val CURSOR_ARRAY = arrayOf(
            KEY_ID,
            KEY_TITLE,
            KEY_MESSAGE,
            KEY_IMAGEPATH,
            KEY_LATITUDE,
            KEY_LONGITUDE
        )

        private const val SELECT_ALL = "SELECT * FROM $DATABASE_TABLE_NAME"
    }

    // Creates the initial table structure.
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    /**
     * Handles database upgrades, adding new columns if necessary.
     * oldVersion < 2: Add imagePath column.
     * oldVersion < 3: Add latitude and longitude columns.
     */

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $DATABASE_TABLE_NAME ADD COLUMN $KEY_IMAGEPATH TEXT")
        }
        if (oldVersion < 3) { // NEU: Latitude und Longitude Spalten
            db.execSQL("ALTER TABLE $DATABASE_TABLE_NAME ADD COLUMN $KEY_LATITUDE REAL")
            db.execSQL("ALTER TABLE $DATABASE_TABLE_NAME ADD COLUMN $KEY_LONGITUDE REAL")
        }
    }

    /**
     * Inserts a new note into the database.
     * @return The row ID of the newly inserted note.
     */

    fun insertNote(note: Note): Long {
        return writableDatabase.insert(DATABASE_TABLE_NAME, null, noteToContentValues(note))
    }

    /**
     * Retrieves a single note by its ID.
     * @return The note if found, otherwise null.
     */

    fun getNote(id: Long): Note? {
        val cursor = readableDatabase.query(
            DATABASE_TABLE_NAME, CURSOR_ARRAY, "$KEY_ID=?",
            arrayOf(id.toString()), null, null, null, null
        )

        val note = if (cursor.moveToFirst()) cursorToNote(cursor) else null
        cursor.close()
        return note
    }

    /**
     * Retrieves all notes in the database.
     * @return A list of all notes.
     */

    fun getAllNotes(): List<Note> {
        val notes = ArrayList<Note>()
        val cursor = readableDatabase.rawQuery(SELECT_ALL, null)

        if (cursor.moveToFirst()) {
            do {
                cursorToNote(cursor)?.let { notes.add(it) }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return notes
    }

    /**
     * Updates an existing note.
     * @return The number of rows affected.
     */

    fun updateNote(note: Note): Int {
        return writableDatabase.update(
            DATABASE_TABLE_NAME,
            noteToContentValues(note),
            "$KEY_ID=?",
            arrayOf(note.id.toString())
        )
    }

    // Deletes a note by its ID.
    fun deleteNote(id: Long) {
        writableDatabase.delete(
            DATABASE_TABLE_NAME,
            "$KEY_ID=?",
            arrayOf(id.toString())
        )
    }

    // Searches for notes in the database that match the given query.
    fun searchNotes(query: String): List<Note> {
        val filteredNotes = ArrayList<Note>()
        val cursor = readableDatabase.query(
            "notes", null, "title LIKE ? OR message LIKE ?",
            arrayOf("%$query%", "%$query%"), null, null, null
        )
        if (cursor.moveToFirst()) {
            do {
                cursorToNote(cursor)?.let { filteredNotes.add(it) }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return filteredNotes
    }

    // Converts a Note object into ContentValues for database insert/update.
    private fun noteToContentValues(note: Note): ContentValues {
        val values = ContentValues()
        values.put(KEY_TITLE, note.title)
        values.put(KEY_MESSAGE, note.message)
        values.put(KEY_IMAGEPATH, note.imagePath)
        note.latitude?.let { values.put(KEY_LATITUDE, it) }
        note.longitude?.let { values.put(KEY_LONGITUDE, it) }
        return values
    }

    // Converts a cursor row into a Note object.
    @SuppressLint("Range")
    private fun cursorToNote(cursor: Cursor): Note? {
        if (cursor.count == 0) return null

        val title = cursor.getString(cursor.getColumnIndex(KEY_TITLE))
        val message = cursor.getString(cursor.getColumnIndex(KEY_MESSAGE))
        val id = cursor.getLong(cursor.getColumnIndex(KEY_ID))
        val imagePath = cursor.getString(cursor.getColumnIndex(KEY_IMAGEPATH))
        val latitude = if (!cursor.isNull(cursor.getColumnIndex(KEY_LATITUDE))) cursor.getDouble(cursor.getColumnIndex(KEY_LATITUDE)) else null
        val longitude = if (!cursor.isNull(cursor.getColumnIndex(KEY_LONGITUDE))) cursor.getDouble(cursor.getColumnIndex(KEY_LONGITUDE)) else null

        return Note(title, message, id, imagePath, latitude, longitude)
    }
}