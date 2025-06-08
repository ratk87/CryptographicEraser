package com.example.cryptographiceraser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private val files: List<File>,
    private val onDirClick: (File) -> Unit,
    private val onCryptoShredClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtFileName: TextView = view.findViewById(R.id.txtFileName)
        val btnCryptoShred: Button = view.findViewById(R.id.btnCryptoShred)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.txtFileName.text = file.name + if (file.isDirectory) " (Ordner)" else ""
        holder.txtFileName.setOnClickListener {
            if (file.isDirectory) onDirClick(file)
        }

        // Verschlüssel-Button nur bei Dateien
        holder.btnCryptoShred.visibility = if (file.isFile) View.VISIBLE else View.GONE
        holder.btnCryptoShred.setOnClickListener {
            onCryptoShredClick(file)
        }
    }
}
