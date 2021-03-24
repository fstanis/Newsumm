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

import android.net.Uri
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.stanis.newsumm.data.Article

class MainViewModel @ViewModelInject constructor() : ViewModel() {
    private val mutableState = MutableLiveData(State())
    val state: LiveData<State> get() = mutableState

    fun setArticle(article: Article) {
        mutableState.postValue(state.value!!.copy(articleStatus = ArticleStatus.Ready(article)))
    }

    fun clearArticle() {
        mutableState.postValue(State())
    }

    fun setArticleLoading() {
        mutableState.postValue(state.value!!.copy(articleStatus = ArticleStatus.Loading))
    }

    fun setArticleError(error: String) {
        mutableState.postValue(state.value!!.copy(articleStatus = ArticleStatus.Error(error)))
    }

    fun setSpeechLoading() {
        mutableState.postValue(state.value!!.copy(speechStatus = SpeechStatus.Loading))
    }

    fun setSpeechUri(uri: Uri) {
        mutableState.postValue(state.value!!.copy(speechStatus = SpeechStatus.Ready(uri)))
    }

    fun setSpeechUnavailable() {
        mutableState.postValue(state.value!!.copy(speechStatus = SpeechStatus.Unavailable))
    }
}

data class State(
    val speechStatus: SpeechStatus = SpeechStatus.None,
    val articleStatus: ArticleStatus = ArticleStatus.None
) {
    val isLoading = speechStatus is SpeechStatus.Loading || articleStatus is ArticleStatus.Loading
}

sealed class SpeechStatus {
    data class Ready(val uri: Uri) : SpeechStatus()
    object Loading : SpeechStatus()
    object None : SpeechStatus()
    object Unavailable : SpeechStatus()
}

sealed class ArticleStatus {
    data class Ready(val article: Article) : ArticleStatus()
    object Loading : ArticleStatus()
    object None : ArticleStatus()
    data class Error(val error: String) : ArticleStatus()
}
