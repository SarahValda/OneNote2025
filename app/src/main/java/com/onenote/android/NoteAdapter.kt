package com.onenote.android

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/**
 * A custom adapter for displaying a list of notes (title & message). Uses a ViewHolder pattern for efficient list rendering.
 */
class NoteAdapter(context: Context, var notes: List<Note>) : BaseAdapter() {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    /** Returns the total number of notes. */
    override fun getCount(): Int = notes.size

    /** Returns the note at the given position. */
    override fun getItem(position: Int): Any = notes[position]

    /** Returns the unique ID of the note at the given position. */
    override fun getItemId(position: Int): Long = notes[position].id

    /**
     * Inflates or reuses a view for a note item, sets title and message.
     * Uses ViewHolder to avoid repeated findViewById calls.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View; val holder: ViewHolder
        if (convertView == null) {
            view = inflater.inflate(R.layout.list_item_view, parent, false)
            holder = ViewHolder(view.findViewById(R.id.title), view.findViewById(R.id.message))
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }
        holder.title.text = notes[position].title
        holder.message.text = notes[position].message
        return view
    }

    /** Holds references to UI elements for each note item. */
    private class ViewHolder(var title: TextView, var message: TextView)
}