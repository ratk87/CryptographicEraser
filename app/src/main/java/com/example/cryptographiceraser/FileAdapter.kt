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
    private val onEncryptClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtFileName: TextView = view.findViewById(R.id.txtFileName)
        val btnEncrypt: Button = view.findViewById(R.id.btnEncrypt)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun getItemCount() = files.size

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.txtFileName.text = file.name + if (file.isDirectory) " (Folder)" else ""
        holder.btnEncrypt.visibility = if (file.isFile) View.VISIBLE else View.GONE
        holder.btnDelete.visibility = if (file.isFile) View.VISIBLE else View.GONE

        holder.txtFileName.setOnClickListener {
            if (file.isDirectory) onDirClick(file)
        }
        holder.btnEncrypt.setOnClickListener { onEncryptClick(file) }
        holder.btnDelete.setOnClickListener { onDeleteClick(file) }
    }
}
