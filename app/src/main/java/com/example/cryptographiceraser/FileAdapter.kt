package com.example.cryptographiceraser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * RecyclerView-Adapter zur Anzeige einer Liste von Dateien und Ordnern.
 * Jeder Eintrag zeigt den Dateinamen und optional einen Button zum
 * Ausführen des CryptoShred-Vorgangs auf Dateien.
 *
 * @param files                 Liste der anzuzeigenden File-Objekte
 * @param onDirClick            Callback, wenn auf einen Ordner geklickt wird
 * @param onCryptoShredClick    Callback, wenn auf den „CryptoShred“-Button geklickt wird
 */
class FileAdapter(
    private val files: List<File>,
    private val onDirClick: (File) -> Unit,
    private val onCryptoShredClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    /**
     * ViewHolder für einzelne Listeneinträge.
     * Referenziert die TextView für den Dateinamen und den CryptoShred-Button.
     */
    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtFileName: TextView   = view.findViewById(R.id.txtFileName)
        val btnCryptoShred: Button  = view.findViewById(R.id.btnCryptoShred)
    }

    /**
     * Erzeugt einen neuen ViewHolder, indem das Layout item_file.xml
     * in die RecyclerView eingebunden wird.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    /**
     * Gibt die Anzahl der Elemente in der Liste zurück.
     */
    override fun getItemCount(): Int = files.size

    /**
     * Bindet Daten an den ViewHolder:
     * - Zeigt den Dateinamen an (mit „(Ordner)“-Suffix bei Verzeichnissen).
     * - Klick auf den Namen navigiert in Verzeichnisse.
     * - Der CryptoShred-Button ist nur bei regulären Dateien sichtbar.
     * - Klick auf den Button löst den CryptoShred-Callback aus.
     */
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]

        // Dateiname anzeigen, bei Ordnern „(Ordner)“ anhängen
        holder.txtFileName.text = if (file.isDirectory) {
            "${file.name} (Ordner)"
        } else {
            file.name
        }

        // Klick auf Text: nur bei Ordnern Navigation auslösen
        holder.txtFileName.setOnClickListener {
            if (file.isDirectory) {
                onDirClick(file)
            }
        }

        // CryptoShred-Button nur bei Datei sichtbar machen
        if (file.isFile) {
            holder.btnCryptoShred.visibility = View.VISIBLE
            holder.btnCryptoShred.setOnClickListener {
                onCryptoShredClick(file)
            }
        } else {
            holder.btnCryptoShred.visibility = View.GONE
        }
    }
}
