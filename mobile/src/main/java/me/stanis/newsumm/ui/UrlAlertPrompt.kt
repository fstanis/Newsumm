/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.stanis.newsumm.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.webkit.URLUtil
import androidx.appcompat.app.AlertDialog
import me.stanis.newsumm.R
import me.stanis.newsumm.databinding.DialogUrlBinding

class UrlAlertPrompt(context: Context) {
    private var callback: ((String) -> Unit)? = null
    private val binding = DialogUrlBinding.inflate(LayoutInflater.from(context))
    private val dialog = AlertDialog.Builder(context)
        .setView(binding.root)
        .setMessage(R.string.article_url)
        .setPositiveButton(R.string.load) { _: DialogInterface, _: Int -> onLoad() }
        .create()

    private val errorDialog = AlertDialog.Builder(context)
        .setOnDismissListener { show() }
        .setMessage(R.string.invalid_url)
        .create()

    fun show(): Unit = dialog.show()

    var text: CharSequence
        get() = binding.url.text
        set(value) {
            binding.url.setText(value)
        }

    fun setIsOnline(isOnline: Boolean) {
        binding.appearOffline.visibility = if (isOnline) View.GONE else View.VISIBLE
    }

    fun setCallback(cb: (String) -> Unit) {
        callback = cb
    }

    fun clearCallback() {
        callback = null
    }

    private fun onLoad() {
        val url = text.toString()
        if (!URLUtil.isNetworkUrl(url)) {
            errorDialog.show()
        } else {
            callback?.invoke(text.toString())
            text = ""
        }
    }
}
