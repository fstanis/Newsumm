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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.stanis.newsumm.data.Article
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class ArticleTTS @Inject constructor() {
    @Inject
    lateinit var ttsEngine: TTSEngine

    suspend fun isEligible(article: Article) =
        ttsEngine.isLanguageAvailable(article.language)
                && article.sentences.all { it.length <= ttsEngine.maxSpeechInputLength }

    suspend fun run(article: Article): File {
        val lines = article.sentences
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .asFlow()
        return mergeAudioFiles(lines.map {
            ttsEngine.synthesizeToTempFile(it, article.language)
        })
    }

    private suspend fun mergeAudioFiles(inputs: Flow<File>) = withContext(Dispatchers.IO) {
        val outfile = File.createTempFile("tts", ".mp4")
        val fos = FileOutputStream(outfile)
        val encoder = AudioEncoder(fos.fd)
        inputs.collect {
            with(FileInputStream(it)) {
                encoder.addInput(fd)
                close()
            }
            it.delete()
        }
        encoder.finish()
        fos.close()
        outfile
    }
}