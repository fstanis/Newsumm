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

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.URLUtil
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.stanis.newsumm.R
import me.stanis.newsumm.data.Article
import me.stanis.newsumm.databinding.ActivityMainBinding
import me.stanis.newsumm.util.audio.ArticleTTS
import me.stanis.newsumm.util.audio.AudioPlayer
import me.stanis.newsumm.util.network.NetworkChecker
import me.stanis.newsumm.util.webcontent.WebContentLoader
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var menu: Menu? = null

    private val viewModel: MainViewModel by viewModels()
    private val ttsMenuItem get() = menu?.findItem(R.id.action_tts)
    private val alertPrompt by lazy { UrlAlertPrompt(this) }
    private val iconSpeech by lazy { ContextCompat.getDrawable(this, R.drawable.ic_speech) }
    private val iconSpeechUnavailable by lazy {
        ContextCompat.getDrawable(this, R.drawable.ic_speech_unavailable)
    }

    @Inject
    lateinit var webContentLoader: WebContentLoader

    @Inject
    lateinit var articleTTS: ArticleTTS

    @Inject
    lateinit var audioPlayer: AudioPlayer

    @Inject
    lateinit var networkChecker: NetworkChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) processIntent(intent)
        viewModel.state.observe(this) {
            binding.progressBar.visibility = if (it.isLoading) View.VISIBLE else View.GONE
            when (it.articleStatus) {
                is ArticleStatus.Ready -> {
                    binding.errorStatus.visibility = View.GONE
                    binding.articleTitle.text = it.articleStatus.article.title
                    binding.articleContent.text =
                        it.articleStatus.article.sentences.joinToString("\n\n")
                }
                is ArticleStatus.None -> {
                    binding.errorStatus.visibility = View.GONE
                    binding.articleTitle.text = ""
                    binding.articleContent.text = getString(R.string.no_article_loaded)
                }
                is ArticleStatus.Error -> {
                    binding.errorStatus.visibility = View.VISIBLE
                    binding.errorStatus.text = it.articleStatus.error
                }
            }
            when (it.speechStatus) {
                is SpeechStatus.Ready ->
                    ttsMenuItem?.apply {
                        isEnabled = true
                        icon = iconSpeech
                    }
                is SpeechStatus.None ->
                    ttsMenuItem?.apply {
                        isEnabled = false
                        icon = iconSpeech
                    }
                is SpeechStatus.Unavailable ->
                    ttsMenuItem?.apply {
                        isEnabled = false
                        icon = iconSpeechUnavailable
                    }
            }
            val a = DialogFragment()
            a.show(supportFragmentManager, "test")
        }
        alertPrompt.setCallback {
            load(it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_load -> {
                openUrlPrompt()
                true
            }
            R.id.action_tts -> {
                viewModel.state.value?.speechStatus?.let {
                    if (it is SpeechStatus.Ready) {
                        audioPlayer.play(it.uri)
                    }
                }
                true
            }
            R.id.action_send -> TODO()
            else -> super.onOptionsItemSelected(item)
        }

    private fun load(url: String) = lifecycleScope.launch(Dispatchers.Default) {
        viewModel.setArticleLoading()
        viewModel.setSpeechLoading()
        try {
            val content = webContentLoader.parseURL(url)
            viewModel.setArticle(content)
            runTTS(content)
        } catch (e: IOException) {
            viewModel.setArticleError("Error loading article")
        }
    }

    private suspend fun runTTS(article: Article) {
        val langAvailable =
            withTimeoutOrNull(1000L) { articleTTS.isEligible(article) } ?: false
        if (!langAvailable) {
            viewModel.setSpeechUnavailable()
            return
        }
        viewModel.setSpeechUri(articleTTS.run(article).toUri())
    }

    private fun openUrlPrompt(text: String = "") {
        if (text.isNotEmpty()) {
            alertPrompt.text = text
        }
        alertPrompt.setIsOnline(networkChecker.isOnline())
        alertPrompt.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        this.menu = menu
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        intent
            ?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.also {
                if (URLUtil.isNetworkUrl(it)) {
                    openUrlPrompt(it)
                } else if (!URLUtil.isValidUrl(it)) {
                    viewModel.setArticle(
                        Article(title = getString(R.string.pasted_article), sentences = listOf(it))
                    )
                }
            }
    }
}
