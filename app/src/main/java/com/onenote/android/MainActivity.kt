package com.onenote.android

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The MainActivity serves as the entry point of the app.
 * It displays a rotating icon and a login button.
 * After tapping the login button, the user is greeted and redirected to the NoteListActivity.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Retrieve the login button and the animated icon.
        val buttonLogin = findViewById<Button>(R.id.login)
        val imageViewIcon = findViewById<ImageView>(R.id.icon)

        // Load and start the rotation animation for the icon.
        val animationRotation = AnimationUtils.loadAnimation(this, R.anim.rotate)
        imageViewIcon.startAnimation(animationRotation)

        // Set the login button’s onClickListener.
        buttonLogin.setOnClickListener {
            // Show a toast message confirming sign-in.
            Toast.makeText(this, R.string.signed_in, Toast.LENGTH_LONG).show()

            // Navigate to the NoteListActivity to display all notes.
            val intent = Intent(this, NoteListActivity::class.java)
            startActivity(intent)

            // Close MainActivity so the user can’t return here with the back button.
            finish()
        }
    }
}