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

package me.stanis.newsumm.util.webcontent

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sumy @Inject constructor(@ApplicationContext context: Context) {
    private val module = GlobalScope.async(start = CoroutineStart.LAZY) {
        Python.start(AndroidPlatform(context))
        Python.getInstance().getModule("sumy_wrapper")
    }

    // TODO: This can fail and needs a way to retry
    private var punktStatus = GlobalScope.async(start = CoroutineStart.LAZY) { downloadPunkt() }

    private suspend fun downloadPunkt() = withContext(Dispatchers.IO) {
        try {
            module.await().callAttr("download_punkt").toBoolean()
        } catch (e: PyException) {
            val error = e.message?.removePrefix("ValueError: ") ?: ""
            Log.e("Download error", error)
            false
        }
    }

    suspend fun summarizeHTML(html: String, language: String, sentencesCount: Int) =
        withContext(Dispatchers.Default) {
            check(punktStatus.await())
            module.await()
                .callAttr("summarize_html", html, language, sentencesCount)
                .toJava(Array<String>::class.java)
                .toList()
        }

    suspend fun summarizeText(text: String, language: String, sentencesCount: Int) =
        withContext(Dispatchers.Default) {
            check(punktStatus.await())
            module.await()
                .callAttr("summarize_text", text, language, sentencesCount)
                .toJava(Array<String>::class.java)
                .toList()
        }
}
