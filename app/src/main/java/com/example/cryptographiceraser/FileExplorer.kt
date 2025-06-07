package com.example.cryptographiceraser

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * A simple file explorer fragment that allows users to browse the file system,
 * select files or directories, and trigger actions like CryptoShred.
 */
class FileExplorer : Fragment() {

    private var fileSelectedListener: OnFileSelectedListener? = null
    private lateinit var textCurrentPath: TextView


    // RecyclerView for displaying files and folders
    private lateinit var recyclerView: RecyclerView
    // The directory currently displayed (starts with external storage root)
    private var currentDir: File = Environment.getExternalStorageDirectory()

    /**
     * Interface for callback to the host (usually MainActivity) when files are selected.
     */
    interface OnFileSelectedListener {
        fun onFilesSelected(selectedFiles: List<File>)
    }

    /**
     * Set the listener for file selection events.
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
        val view = inflater.inflate(R.layout.file_explorer, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        textCurrentPath = view.findViewById(R.id.textCurrentPath)
        loadFiles(currentDir)

        // Button nach oben
        val btnGoUp = view.findViewById<Button>(R.id.btnGoUp)
        btnGoUp.setOnClickListener {
            goUp()
        }
        btnGoUp.visibility = if (currentDir.parentFile != null) View.VISIBLE else View.GONE

        return view
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        recyclerView.adapter = FileAdapter(
            files,
            onDirClick = { loadFiles(it) },
            onCryptoShredClick = { file ->
                fileSelectedListener?.onFilesSelected(listOf(file))
            }
        )
        // Button anzeigen/ausblenden
        view?.findViewById<Button>(R.id.btnGoUp)?.visibility = if (currentDir.parentFile != null) View.VISIBLE else View.GONE
        // **Aktuellen Pfad anzeigen**
        textCurrentPath.text = "Pfad: ${currentDir.absolutePath}"
    }


    /**
     * Allows navigation one directory up.
     */
    fun goUp() {
        val parent = currentDir.parentFile ?: return
        // Verhindere Navigation über das User-Storage-Root hinaus
        val storageRoot = Environment.getExternalStorageDirectory()
        if (parent.absolutePath.length < storageRoot.absolutePath.length) return
        loadFiles(parent)
    }

    /**
     * Refresh of the view. Needed to ensure that after a deletion, user gets to see the latest state of the folder content.
     */
    fun refreshCurrentDir() {
        loadFiles(currentDir)
    }

}
