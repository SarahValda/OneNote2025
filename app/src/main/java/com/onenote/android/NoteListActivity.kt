package com.onenote.android

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity for displaying a list of notes.
 * Allows users to:
 * - View a list of all notes.
 * - Add a new note using a "+" button.
 * - Search notes by title or message using a search bar.
 * - Edit or view details of a selected note.
 */
class NoteListActivity : AppCompatActivity(), AdapterView.OnItemClickListener {

    private lateinit var listView: ListView // List view to display notes
    private lateinit var adapter: NoteAdapter // Adapter to manage note data in the list
    private lateinit var db: Database // Database instance for data operations
    private var searchField: EditText? = null // Optional search field dynamically created

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        // Set up the toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // Initialize list view
        listView = findViewById(R.id.listView)

        // Initialize database
        db = Database(this)

        // Set up adapter with all notes from the database
        adapter = NoteAdapter(this, db.getAllNotes())
        listView.adapter = adapter
        listView.onItemClickListener = this
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list of notes when returning to the activity
        adapter.notes = db.getAllNotes()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu with add and search options
        menuInflater.inflate(R.menu.menu_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add -> {
                // Open NoteEditActivity to create a new note
                val intent = Intent(this, NoteEditActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.search -> {
                // Toggle the visibility of the search bar
                toggleSearchBar()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Handles clicks on list items.
     * Opens NoteEditActivity to view or edit the selected note.
     *
     * @param p0 The parent AdapterView where the click happened.
     * @param p1 The view that was clicked.
     * @param p2 The position of the clicked item.
     * @param id The ID of the clicked item.
     */
    override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, id: Long) {
        val intent = Intent(this, NoteEditActivity::class.java)
        intent.putExtra("id", id)
        startActivity(intent)
    }

    /**
     * Toggles the visibility of the search bar.
     * Creates the search bar dynamically if it doesn't exist.
     */
    private fun toggleSearchBar() {
        if (searchField == null) {
            // Create the search field dynamically
            searchField = EditText(this).apply {
                hint = "Search Notes..."
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        // Perform search when the text changes
                        performSearch(s.toString())
                    }
                })
            }
            // Add the search field to the toolbar container
            findViewById<LinearLayout>(R.id.toolbarContainer).addView(searchField)
        } else {
            // Toggle the visibility of the search field
            searchField?.visibility = if (searchField?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    /**
     * Filters the list of notes based on the search query.
     * Updates the adapter with matching notes.
     *
     * @param query The search query entered by the user.
     */
    private fun performSearch(query: String) {
        adapter.notes = if (query.isEmpty()) {
            db.getAllNotes() // Show all notes if the query is empty
        } else {
            db.searchNotes(query) // Show only matching notes
        }
        adapter.notifyDataSetChanged()
    }
}