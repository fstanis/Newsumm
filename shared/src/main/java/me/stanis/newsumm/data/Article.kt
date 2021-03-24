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

package me.stanis.newsumm.data

import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.wearable.DataMap
import java.util.*
import kotlin.collections.ArrayList

data class Article(
    val url: String = UUID.randomUUID().toString(),
    val title: String = "",
    val sentences: List<String> = listOf(),
    val language: String = ""
) : Parcelable {
    fun writeToDataMap(dataMap: DataMap) = with(dataMap) {
        putString("url", url)
        putString("title", title)
        putStringArrayList("sentences", ArrayList(sentences))
        putString("language", language)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) = with(parcel) {
        writeString(url)
        writeString(title)
        writeStringList(sentences)
        writeString(language)
    }

    companion object CREATOR : Parcelable.Creator<Article> {
        override fun createFromParcel(parcel: Parcel) =
            Article(
                url = parcel.readString() ?: "",
                title = parcel.readString() ?: "",
                sentences = parcel.createStringArrayList() ?: listOf(),
                language = parcel.readString() ?: ""
            )

        fun createFromDataMap(dataMap: DataMap) =
            Article(
                url = dataMap.getString("url"),
                title = dataMap.getString("title"),
                sentences = dataMap.getStringArrayList("sentences"),
                language = dataMap.getString("language")
            )

        override fun newArray(size: Int): Array<Article?> {
            return arrayOfNulls(size)
        }
    }

    override fun describeContents() = 0
}
