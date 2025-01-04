package com.onenote.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for creating, editing, and managing a note.
 *
 * Features include:
 * - Editing or creating notes with title, message, and optional image.
 * - Attaching images via camera or gallery.
 * - Capturing and displaying the current GPS location.
 * - Saving notes to a local SQLite database with map visualization.
 *
 * Handles necessary permissions and provides user feedback through sound, vibration, and toasts.
 */
class NoteEditActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 101
        private const val REQUEST_CAMERA_PERMISSION = 102
        private const val REQUEST_GALLERY_PERMISSION = 103
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_IMAGE_PICK = 2
    }

    // UI Elements: Define UI components for editing notes, including title, message, image, location display, and map.
    private lateinit var noteEditTitle: EditText
    private lateinit var noteEditMessage: EditText
    private lateinit var buttonSave: Button
    private lateinit var buttonLocation: ImageButton
    private lateinit var buttonCamera: ImageButton
    private lateinit var buttonGallery: ImageButton
    private lateinit var imagePreview: ImageView
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var map: MapView

    // Location & DB: Initialize fused location provider for GPS data and database for note storage.
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: Database

    // State: Manage current note ID, photo path, and GPS coordinates; store pending actions requiring permissions.
    private var noteId: Long = -1L
    private var currentPhotoPath: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure osmdroid, set layout, and initialize components and functionality.
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_note_edit)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = Database(this)

        setupToolbar()
        initializeViews()
        setupMapView()
        loadExistingNote()
        setupButtonListeners()
    }

    // Sets up the toolbar with a back button.
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
    }

    // Initializes the UI elements by finding them by their IDs.
    private fun initializeViews() {
        noteEditTitle = findViewById(R.id.noteEditTitle)
        noteEditMessage = findViewById(R.id.noteEditMessage)
        buttonSave = findViewById(R.id.buttonSave)
        buttonLocation = findViewById(R.id.buttonLocation)
        buttonCamera = findViewById(R.id.button_camera)
        buttonGallery = findViewById(R.id.button_gallery)
        imagePreview = findViewById(R.id.image_preview)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        map = findViewById(R.id.map)

        // Initially hide the image preview if no image is set.
        imagePreview.visibility = View.GONE
    }

    // Configures the osmdroid MapView.
    private fun setupMapView() {
        map.setMultiTouchControls(true)
        map.setTileSource(TileSourceFactory.MAPNIK)
    }

    // Loads an existing note if an ID is passed via intent.
    private fun loadExistingNote() {
        noteId = intent.getLongExtra("id", -1)
        if (noteId >= 0) {
            db.getNote(noteId)?.let { populateNoteDetails(it) }
        }
    }

    // Populates the UI with the details of the note.
    @SuppressLint("SetTextI18n")
    private fun populateNoteDetails(note: Note) {
        noteEditTitle.setText(note.title)
        noteEditMessage.setText(note.message)
        currentPhotoPath = note.imagePath
        currentLatitude = note.latitude
        currentLongitude = note.longitude

        latitudeTextView.text = "Latitude: ${note.latitude ?: "N/A"}"
        longitudeTextView.text = "Longitude: ${note.longitude ?: "N/A"}"

        // Update the image if available, otherwise hide the ImageView
        if (!currentPhotoPath.isNullOrEmpty()) {
            val file = File(currentPhotoPath!!)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                updateImagePreview(uri)
            } else {
                imagePreview.visibility = View.GONE
            }
        } else {
            imagePreview.visibility = View.GONE
        }

        // Place a marker if latitude/longitude exist
        if (note.latitude != null && note.longitude != null) {
            val geoPoint = GeoPoint(note.latitude!!, note.longitude!!)
            addMarkerToMap(geoPoint, "Your Location")
            map.controller.setZoom(15.0)
            map.controller.setCenter(geoPoint)
        }
    }

    // Adds a marker to the map at the specified GeoPoint.
    private fun addMarkerToMap(geoPoint: GeoPoint, title: String) {
        val marker = Marker(map).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
        }
        map.overlays.clear()
        map.overlays.add(marker)
    }

    // Sets up the listeners for all buttons.
    private fun setupButtonListeners() {
        buttonLocation.setOnClickListener { displayLocation() }
        buttonSave.setOnClickListener { saveNote() }
        buttonCamera.setOnClickListener {
            if (checkAndRequestCameraPermissions()) dispatchTakePictureIntent()
            else pendingAction = { dispatchTakePictureIntent() }
        }
        buttonGallery.setOnClickListener {
            if (checkAndRequestGalleryPermissions()) dispatchPickPictureIntent()
            else pendingAction = { dispatchPickPictureIntent() }
        }
    }

    // Saves the current note to the database.
    private fun saveNote() {
        val title = noteEditTitle.text.toString().trim()
        val message = noteEditMessage.text.toString().trim()

        if (title.isEmpty()) {
            noteEditTitle.error = "Title cannot be empty"
            return
        }
        if (message.isEmpty()) {
            noteEditMessage.error = "Message cannot be empty"
            return
        }

        val note = Note(
            id = noteId,
            title = title,
            message = message,
            imagePath = currentPhotoPath,
            latitude = currentLatitude,
            longitude = currentLongitude
        )

        CoroutineScope(Dispatchers.IO).launch {
            if (noteId >= 0) db.updateNote(note) else noteId = db.insertNote(note)
            runOnUiThread {
                playSoundAndVibrate()
                Toast.makeText(this@NoteEditActivity, R.string.saved, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // Plays a beep sound and vibrates the device.
    private fun playSoundAndVibrate() {
        MediaPlayer.create(this, R.raw.beep).start()
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
    }

    // Displays the current location on the map.
    private fun displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                latitudeTextView.text = "Latitude: ${location.latitude}"
                longitudeTextView.text = "Longitude: ${location.longitude}"

                val geoPoint = GeoPoint(location.latitude, location.longitude)
                addMarkerToMap(geoPoint, "Your Location")
                map.controller.setZoom(15.0)
                map.controller.setCenter(geoPoint)

                CoroutineScope(Dispatchers.IO).launch {
                    db.getNote(noteId)?.let {
                        it.latitude = currentLatitude
                        it.longitude = currentLongitude
                        db.updateNote(it)
                    }
                }
            } else {
                Toast.makeText(this, "No location data available", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Checks and requests necessary camera and gallery permissions.
    private fun checkAndRequestCameraPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQUEST_CAMERA_PERMISSION)
            false
        } else true
    }

    private fun checkAndRequestGalleryPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_GALLERY_PERMISSION)
            false
        } else true
    }

    // Launches the intent to pick an image from the gallery.
    private fun dispatchPickPictureIntent() {
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also {
            startActivityForResult(it, REQUEST_IMAGE_PICK)
        }
    }

    // Launches the intent to take a picture with the camera.
    @SuppressLint("QueryPermissionsNeeded")
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e("NoteEditActivity", "Error occurred while creating the file", ex)
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this, "com.onenote.android.fileprovider", it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            } ?: run {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Creates a temporary image file in the external storage.
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    // Handles the results from started activities.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    currentPhotoPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(this, "com.onenote.android.fileprovider", file)
                            updateImagePreview(uri)
                        } else {
                            Toast.makeText(this, "Error saving the image", Toast.LENGTH_LONG).show()
                            imagePreview.visibility = View.GONE
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        currentPhotoPath = getPathFromUri(uri)
                        updateImagePreview(uri)
                    }
                }
            }
        }
    }

    // Updates the image view based on the provided URI.
    private fun updateImagePreview(uri: Uri?) {
        if (uri != null) {
            imagePreview.setImageURI(uri)
            imagePreview.visibility = View.VISIBLE
        } else {
            imagePreview.visibility = View.GONE
        }
    }

    // Extracts the file path from a URI.
    private fun getPathFromUri(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            }
        }
        return path
    }

    // Creates the options menu.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        return true
    }

    // Handles the selection of menu items.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.delete -> {
                showDeleteDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Shows a dialog to confirm the deletion of the note.
    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteNote(noteId)
                finish()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    // Handles the results of permission requests.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    displayLocation()
                } else {
                    Toast.makeText(this, "Permissions are required to display location.", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    pendingAction?.invoke()
                    pendingAction = null
                } else {
                    Toast.makeText(this, "Permissions are required to use the camera.", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_GALLERY_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    pendingAction?.invoke()
                    pendingAction = null
                } else {
                    Toast.makeText(this, "Permissions are required to access the gallery.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    //Handles the pausing of the activity.
    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    // Handles the resuming of the activity.
    override fun onResume() {
        super.onResume()
        map.onResume()
    }
}
