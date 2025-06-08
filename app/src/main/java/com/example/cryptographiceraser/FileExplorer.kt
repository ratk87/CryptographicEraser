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

class FileExplorer : Fragment() {

    interface OnFileSelectedListener {
        fun onFilesSelected(selectedFiles: List<File>)
    }

    private var fileSelectedListener: OnFileSelectedListener? = null

    fun setOnFileSelectedListener(listener: OnFileSelectedListener) {
        fileSelectedListener = listener
    }

    private lateinit var btnInternal: Button
    private lateinit var btnSdCard: Button
    private lateinit var btnGoUp: Button
    private lateinit var textCurrentPath: TextView
    private lateinit var recyclerView: RecyclerView

    // unsere beiden Roots
    private val internalRoot = Environment.getExternalStorageDirectory()
    private var sdRoot: File? = null

    // aktuell angezeigtes Verzeichnis
    private var currentDir: File = internalRoot

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFileSelectedListener) {
            fileSelectedListener = context
        }
        // SD-Card-Root suchen:
        val extDirs = requireContext().getExternalFilesDirs(null)
        if (extDirs.size > 1 && extDirs[1] != null) {
            // extDirs[1] ist z.B. /storage/XXXX-XXXX/Android/data/...
            // wir steigen 4 Ebenen hinauf bis /storage/XXXX-XXXX
            var f = extDirs[1]
            repeat(4) { f = f.parentFile!! }
            sdRoot = f
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.file_explorer, container, false)

        btnInternal    = root.findViewById(R.id.btnInternal)
        btnSdCard      = root.findViewById(R.id.btnSdCard)
        btnGoUp        = root.findViewById(R.id.btnGoUp)
        textCurrentPath = root.findViewById(R.id.textCurrentPath)
        recyclerView   = root.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Button-Click-Handler setzen
        btnInternal.setOnClickListener { loadFiles(internalRoot) }
        btnSdCard.setOnClickListener { sdRoot?.let { loadFiles(it) } }
        btnGoUp.setOnClickListener { goUp() }

        // SD-Button nur sichtbar, wenn wirklich eine SD-Card existiert
        btnSdCard.visibility = if (sdRoot != null) View.VISIBLE else View.GONE

        // initial intern laden
        loadFiles(internalRoot)
        return root
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        textCurrentPath.text = "Pfad: ${currentDir.absolutePath}"

        val list = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()

        recyclerView.adapter = FileAdapter(
            list,
            onDirClick = { loadFiles(it) },
            onCryptoShredClick = { file ->
                fileSelectedListener?.onFilesSelected(listOf(file))
            }
        )

        // GoUp-Button nur sichtbar, wenn nicht im Root (intern oder SD)
        val isRoot = (currentDir == internalRoot) || (sdRoot != null && currentDir == sdRoot)
        btnGoUp.visibility = if (isRoot) View.GONE else View.VISIBLE
    }

    private fun goUp() {
        currentDir.parentFile
            ?.takeIf { parent ->
                // Eltern‐Pfad noch unterhalb des gewählten Roots
                when {
                    currentDir.absolutePath.startsWith(internalRoot.absolutePath) -> parent.absolutePath.startsWith(internalRoot.absolutePath)
                    sdRoot != null && currentDir.absolutePath.startsWith(sdRoot!!.absolutePath) -> parent.absolutePath.startsWith(sdRoot!!.absolutePath)
                    else -> false
                }
            }
            ?.let { loadFiles(it) }
    }

    /** zum Refresh nach Löschung etc. */
    fun refreshCurrentDir() {
        loadFiles(currentDir)
    }
}
