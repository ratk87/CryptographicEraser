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
 * Fragment zur Anzeige eines einfachen Datei-Explorers.
 * Erlaubt das Navigieren in Verzeichnissen und Auswahl von Dateien.
 */
class FileExplorer : Fragment() {

    /**
     * Interface für Callbacks, wenn der Nutzer Dateien ausgewählt hat.
     */
    interface OnFileSelectedListener {
        /**
         * Wird aufgerufen, wenn eine oder mehrere Dateien ausgewählt wurden.
         * @param selectedFiles Liste der ausgewählten Dateien
         */
        fun onFilesSelected(selectedFiles: List<File>)
    }

    /** Listener für Dateiauswahl, kann über setOnFileSelectedListener gesetzt werden */
    private var fileSelectedListener: OnFileSelectedListener? = null

    /** Setter für den externen Listener */
    fun setOnFileSelectedListener(listener: OnFileSelectedListener) {
        fileSelectedListener = listener
    }

    // UI-Komponenten
    private lateinit var btnInternal: Button
    private lateinit var btnGoUp: Button
    private lateinit var textCurrentPath: TextView
    private lateinit var recyclerView: RecyclerView

    // Wurzelverzeichnisse: interner Speicher und optional SD-Karte
    private val internalRoot = Environment.getExternalStorageDirectory()
    private var sdRoot: File? = null

    /** Aktuell angezeigtes Verzeichnis (initial internalRoot) */
    private var currentDir: File = internalRoot

    /**
     * Lifecycle-Callback: Wird aufgerufen, sobald das Fragment an die Activity angehängt ist.
     * Hier wird geprüft, ob die Activity den OnFileSelectedListener implementiert
     * und das SD-Karten-Rootverzeichnis ermittelt.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Activity als Listener setzen, falls implementiert
        if (context is OnFileSelectedListener) {
            fileSelectedListener = context
        }
        // SD-Card-Root suchen (vier Ebenen aufsteigen)
        val extDirs = requireContext().getExternalFilesDirs(null)
        if (extDirs.size > 1 && extDirs[1] != null) {
            var f = extDirs[1]
            repeat(4) { f = f.parentFile!! }
            sdRoot = f
        }
    }

    /**
     * Lifecycle-Callback: Erzeugt die View-Hierarchie des Fragments.
     * Initialisiert UI-Elemente und lädt das interne Root-Verzeichnis.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Layout für den FileExplorer
        val root = inflater.inflate(R.layout.file_explorer, container, false)

        // UI-Komponenten referenzieren
        btnInternal     = root.findViewById(R.id.btnInternal)
        btnGoUp         = root.findViewById(R.id.btnGoUp)
        textCurrentPath = root.findViewById(R.id.textCurrentPath)
        recyclerView    = root.findViewById(R.id.recyclerViewFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Button-Handler: interner Speicher oder nach oben navigieren
        btnInternal.setOnClickListener { loadFiles(internalRoot) }
        btnGoUp.setOnClickListener { goUp() }

        // Starte mit internem Root-Verzeichnis
        loadFiles(internalRoot)
        return root
    }

    /**
     * Lädt alle Dateien und Ordner in [dir], zeigt sie im RecyclerView an
     * und aktualisiert den Pfad-Text.
     *
     * @param dir Verzeichnis, dessen Inhalt angezeigt werden soll
     */
    private fun loadFiles(dir: File) {
        currentDir = dir
        textCurrentPath.text = "Pfad: ${currentDir.absolutePath}"

        // Dateien sortiert nach Ordnern zuerst, dann alphabetisch
        val list = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: emptyList()

        // Adapter für RecyclerView setzen
        recyclerView.adapter = FileAdapter(
            list,
            onDirClick = { loadFiles(it) },
            onCryptoShredClick = { file ->
                // Einzeldatei auswählen und Callback aufrufen
                fileSelectedListener?.onFilesSelected(listOf(file))
            }
        )

        // GoUp-Button nur anzeigen, wenn wir nicht im Root sind
        val isRoot = (currentDir == internalRoot)
                || (sdRoot != null && currentDir == sdRoot)
        btnGoUp.visibility = if (isRoot) View.GONE else View.VISIBLE
    }

    /**
     * Navigiert eine Ebene nach oben, sofern das übergeordnete Verzeichnis
     * noch zum internen oder SD-Root gehört.
     */
    private fun goUp() {
        currentDir.parentFile
            ?.takeIf { parent ->
                when {
                    currentDir.absolutePath.startsWith(internalRoot.absolutePath) ->
                        parent.absolutePath.startsWith(internalRoot.absolutePath)
                    sdRoot != null && currentDir.absolutePath.startsWith(sdRoot!!.absolutePath) ->
                        parent.absolutePath.startsWith(sdRoot!!.absolutePath)
                    else -> false
                }
            }
            ?.let { loadFiles(it) }
    }

    /**
     * Refresh-Funktion, um das aktuelle Verzeichnis neu zu laden
     * (z. B. nach dem Löschen einer Datei).
     */
    fun refreshCurrentDir() {
        loadFiles(currentDir)
    }
}
