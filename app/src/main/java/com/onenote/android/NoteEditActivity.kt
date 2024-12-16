package com.onenote.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView // Import TextView!
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class NoteEditActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var preferences: Preferences
    private lateinit var db: Database

    private lateinit var noteEditTitle: EditText
    private lateinit var noteEditMessage: EditText
    private lateinit var buttonSave: Button
    private lateinit var buttonLocation: ImageButton
    private lateinit var buttonCamera: ImageButton
    private lateinit var buttonGallery: ImageButton
    private lateinit var imagePreview: ImageView

    // NEU: TextViews für Latitude und Longitude
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView

    private var id = -1L
    private var currentPhotoPath: String? = null

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preferences nur falls du sie brauchst
        preferences = Preferences(this)

        setContentView(R.layout.activity_note_edit)

        // Init FusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        // Init database
        db = Database(this)

        // Views initialisieren
        noteEditTitle = findViewById(R.id.noteEditTitle)
        noteEditMessage = findViewById(R.id.noteEditMessage)
        buttonSave = findViewById(R.id.buttonSave)
        buttonLocation = findViewById(R.id.buttonLocation)
        buttonCamera = findViewById(R.id.button_camera)
        buttonGallery = findViewById(R.id.button_gallery)
        imagePreview = findViewById(R.id.image_preview)

        // Hier TextViews für Latitude und Longitude initialisieren:
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)

        // Note laden falls vorhanden
        id = intent.getLongExtra("id", -1)
        if (id >= 0) {
            val note = db.getNote(id)
            note?.let {
                noteEditTitle.setText(it.title)
                noteEditMessage.setText(it.message)
                currentPhotoPath = it.imagePath
                if (!currentPhotoPath.isNullOrEmpty()) {
                    val file = File(currentPhotoPath)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        imagePreview.setImageURI(uri)
                    }
                }
            }
        }

        // Click Listener
        buttonLocation.setOnClickListener {
            displayLocation()
        }

        buttonSave.setOnClickListener {
            val title = noteEditTitle.editableText.toString()
            val message = noteEditMessage.editableText.toString()
            val note = Note(title, message, id, currentPhotoPath)

            if (id >= 0) {
                db.updateNote(note)
            } else {
                db.insertNote(note)
            }

            vibrate()
                        Toast.makeText(this, R.string.saved, Toast.LENGTH_LONG).show()
            finish()
        }

        buttonCamera.setOnClickListener {
            if (checkAndRequestPermissions()) {
                dispatchTakePictureIntent()
            }
        }

        buttonGallery.setOnClickListener {
            if (checkAndRequestPermissions()) {
                dispatchPickPictureIntent()
            }
        }
    }

    private fun displayLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Statt Toast nun direkt in die TextViews schreiben
                    latitudeTextView.text = "Latitude: ${location.latitude}"
                    longitudeTextView.text = "Longitude: ${location.longitude}"
                } else {
                    Toast.makeText(this, "Keine Standortdaten verfügbar", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            101
        )
    }

    /**
     * Kamera- und Galerie-Berechtigungen prüfen und anfordern
     */
    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        // Für neuere Android-Versionen Bilderlaubnis anpassen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val listPermissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), 102)
            false
        } else {
            true
        }
    }

    /**
     * Galerie Intent
     */
    private fun dispatchPickPictureIntent() {
        val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(pickPhotoIntent, REQUEST_IMAGE_PICK)
    }

    /**
     * Kamera Intent
     */
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                Log.e("NoteEditActivity", "Error occurred while creating the file", ex)
                null
            }
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.onenote.android.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Log.e("NoteEditActivity", "No activity found to handle the intent")
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(null)!!
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    // Bild von der Kamera
                    val file = File(currentPhotoPath)
                    if (file.exists()) {
                        val uri = Uri.fromFile(file)
                        imagePreview.setImageURI(uri)
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    // Bild aus Galerie
                    val imageUri = data?.data
                    imageUri?.let {
                        currentPhotoPath = getPathFromUri(it)
                        imagePreview.setImageURI(it)
                    }
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = cursor.getString(columnIndex)
            }
        }
        return path
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (id >= 0) {
            menuInflater.inflate(R.menu.menu_edit, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        } else if (item.itemId == R.id.delete) {
            showDeleteDialog()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteNote(id)
                finish()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    displayLocation()
                }
            }
            102 -> {
                // Kamera/Galerie Permissions
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Nun darfst du Kamera/Galerie nutzen.
                }
            }
        }
    }
}