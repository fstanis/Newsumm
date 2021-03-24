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
import android.webkit.WebSettings
import com.google.mlkit.nl.languageid.LanguageIdentifier
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import me.stanis.newsumm.data.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.*
import javax.inject.Inject

class WebContentLoader @Inject constructor(
    @ActivityContext context: Context,
    private val sumy: Sumy,
    private val languageIdentifier: LanguageIdentifier
) {
    companion object {
        private const val SENTENCE_COUNT = 7

        private val bannedTags = listOf("img", "picture", "figure", "script", "style")
        private val validLanguages = setOf("cs", "de", "en", "es", "fr", "it", "pt")
    }

    private val userAgent = WebSettings.getDefaultUserAgent(context)

    suspend fun parseURL(url: String, tryAMP: Boolean = true): Article {
        val doc = withContext(Dispatchers.IO) { Jsoup.connect(url).userAgent(userAgent).get() }
        if (tryAMP) {
            val ampURL = getAmpUrl(doc)
            if (ampURL.isNotEmpty()) {
                return parseURL(ampURL, false)
            }
        }
        val title = doc.title()
        val language = detectLanguage(doc)
        doc.select(bannedTags.joinToString(",")).remove()
        val sentences = sumy.summarizeHTML(
            doc.html(),
            language.takeIf { validLanguages.contains(it) } ?: "en",
            SENTENCE_COUNT)
        return Article(
            url = url,
            title = title,
            language = language,
            sentences = sentences
        )
    }

    private suspend fun detectLanguage(doc: Document): String {
        val htmlLang = doc.selectFirst("html[lang]")?.attr("lang")
        if (!htmlLang.isNullOrEmpty()) {
            return htmlLang.replace(Regex("-.*"), "").toLowerCase(Locale.ROOT)
        }
        val description = doc.selectFirst("meta[name=description]")?.attr("content")
            ?: return LanguageIdentifier.UNDETERMINED_LANGUAGE_TAG
        return languageIdentifier.identifyLanguage(description).await()
    }

    private fun getArticleImage(doc: Document): String {
        val fbImage = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
            ?.takeIf { it.isNotEmpty() }
        val twitterImage = doc.selectFirst("meta[property=\"twitter:image\"]")?.attr("content")
            ?.takeIf { it.isNotEmpty() }
        return fbImage ?: twitterImage ?: ""
    }

    private fun getAmpUrl(doc: Document) =
        doc.selectFirst("link[rel=amphtml][href]")?.attr("href") ?: ""
}
