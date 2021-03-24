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

package me.stanis.newsumm.util.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class TTSEngine(context: Context) {
    private val tts = GlobalScope.async(start = CoroutineStart.LAZY) {
        createTextToSpeech(context).also {
            it.setOnUtteranceProgressListener(progressListener)
        }
    }
    private val utterances = mutableMapOf<String, CompletableDeferred<Unit>>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            utteranceId ?: return
            utterances[utteranceId]?.complete(Unit)
            utterances.remove(utteranceId)
        }

        override fun onError(utteranceId: String?) {
            utteranceId ?: return
            utterances[utteranceId]?.completeExceptionally(Exception("error creating utterance"))
            utterances.remove(utteranceId)
        }
    }

    val maxSpeechInputLength get() = TextToSpeech.getMaxSpeechInputLength()

    suspend fun waitForLoad() {
        tts.await()
    }

    suspend fun isLanguageAvailable(language: String) =
        tts.await().isLanguageAvailable(Locale(language)) == TextToSpeech.LANG_AVAILABLE

    suspend fun synthesizeToFile(charSequence: CharSequence, language: String, file: File) =
        withContext(Dispatchers.IO) {
            setLanguage(language)
            val id = createUtterance()
            tts.await().synthesizeToFile(charSequence, null, file, id)
            utterances[id]!!.await()
        }

    suspend fun speak(charSequence: CharSequence, language: String) {
        setLanguage(language)
        val id = createUtterance()
        tts.await().speak(charSequence, TextToSpeech.QUEUE_ADD, null, id)
        utterances[id]!!.await()
    }

    suspend fun synthesizeToTempFile(charSequence: CharSequence, language: String): File =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("tts", ".wav")
            synthesizeToFile(charSequence, language, file)
            file
        }

    private fun createUtterance(): String {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        utterances[id] = deferred
        return id
    }

    private suspend fun setLanguage(language: String) {
        tts.await().language = Locale(language)
    }

    companion object {
        private suspend fun createTextToSpeech(context: Context): TextToSpeech {
            val status = CompletableDeferred<Int>()
            val tts = TextToSpeech(context) { status.complete(it) }
            check(status.await() == TextToSpeech.SUCCESS)
            return tts
        }
    }
}
