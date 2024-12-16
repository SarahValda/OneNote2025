package com.onenote.android

/**
 * Represents a single note with optional image and location data.
 * @param title The title of the note.
 * @param message The message content of the note.
 * @param id The unique identifier for the note (default 0, assigned by the database).
 * @param imagePath The optional path to an attached image.
 * @param latitude The optional latitude coordinate of the note’s location.
 * @param longitude The optional longitude coordinate of the note’s location.
 */

data class Note(var title: String, var message: String, var id: Long = 0, var imagePath: String? = null, var latitude: Double? = null,
                var longitude: Double? = null)
