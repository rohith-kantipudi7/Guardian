package com.guardian.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens WhatsApp on the chat with [phone] with the crash photos and [text] attached. WhatsApp always
 * needs the user to tap Send; fully automatic sending is only possible through the WhatsApp Business API.
 */
fun shareToWhatsApp(context: Context, phone: String, text: String, imagePaths: List<String>) {
    var digits = phone.filter { it.isDigit() }
    if (digits.length == 10) digits = "91$digits" // bare 10-digit numbers are assumed to be Indian
    val uris = imagePaths.map { FileProvider.getUriForFile(context, "${context.packageName}.files", File(it)) }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra("jid", "$digits@s.whatsapp.net")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent(intent).setPackage("com.whatsapp"))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(intent, "Send alert"))
    }
}
