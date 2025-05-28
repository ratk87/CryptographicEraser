package com.example.cryptographiceraser

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * A simple file explorer fragment that allows users to browse the file system,
 * select files or directories, and trigger actions like encryption or deletion.
 */
class FileExplorer : Fragment() {

    private var listener: OnFileSelectedListener? = null

    // The RecyclerView that displays the list of files/folders
    private lateinit var recyclerView: RecyclerView
    // The currently displayed directory (default: external storage root)
    private var currentDir: File = Environment.getExternalStorageDirectory()
    // The callback interface to notify the host activity of selected files
    private var fileSelectedListener: OnFileSelectedListener? = null

    /**
     * This interface allows the host (Activity) to receive events when files are selected.
     */
    interface OnFileSelectedListener {
        fun onFilesSelected(selectedFiles: List<File>)
    }

    /**
     * Set the callback listener from the host activity.
     */
    fun setOnFileSelectedListener(listener: OnFileSelectedListener) {
        fileSelectedListener = listener
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Optional: Auto-attach if activity implements the interface
        if (context is OnFileSelectedListener) {
            fileSelectedListener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_explorer, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadFiles(currentDir)
        return view
    }

    /**
     * Loads and displays all files and folders in the given directory.
     * Files are sorted: folders first, then files (both alphabetically).
     */
    private fun loadFiles(dir: File) {
        currentDir = dir
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        recyclerView.adapter = FileAdapter(
            files,
            onDirClick = { loadFiles(it) },
            onEncryptClick = { file ->
                // Callback: Pass the file to the activity for encryption
                fileSelectedListener?.onFilesSelected(listOf(file))
            },
            onDeleteClick = { file ->
                if (file.delete()) {
                    Toast.makeText(requireContext(), "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                    loadFiles(currentDir) // Refresh list
                } else {
                    Toast.makeText(requireContext(), "Failed to delete ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Optional: Allows navigation up in the directory hierarchy (e.g., "Up" button in UI).
     */
    fun goUp() {
        val parent = currentDir.parentFile ?: return
        loadFiles(parent)
    }
}
