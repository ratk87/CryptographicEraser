package com.example.cryptographiceraser

import android.content.Context
import android.widget.Toast

/**
 * Utility-Objekt für einfache Dialog- und Toast-Hilfsfunktionen.
 */
object DialogUtils {

    /**
     * Zeigt einen kurzen Toast mit der übergebenen Nachricht an.
     *
     * @param context Context, in dem der Toast angezeigt wird (z. B. Activity)
     * @param msg     Textnachricht, die im Toast dargestellt wird
     */
    fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
