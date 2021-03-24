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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.FileDescriptor

class AudioEncoder(
    fdOut: FileDescriptor,
    private val bitrate: Int = DEFAULT_BITRATE
) {
    suspend fun addInput(fd: FileDescriptor) = withContext(Dispatchers.IO) {
        processInputFile(fd)
    }

    suspend fun finish() = withContext(Dispatchers.IO) {
        check(::encoder.isInitialized) { "finish() called before any inputs were added" }
        endInput()
        outputTask.await()
        muxer.stop()
        muxer.release()
    }

    private val muxer = MediaMuxer(fdOut, OUTPUT_FORMAT)
    private lateinit var encoder: MediaCodec
    private lateinit var outputTask: Deferred<Unit>
    private var offsetTime = 0L
    private var muxerTrack = -1

    private fun initEncoder(inputFormat: MediaFormat) {
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (::encoder.isInitialized) {
            check(encoder.inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) == sampleRate) { "all inputs must have the same sample rate" }
            check(encoder.inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channelCount) { "all inputs must have the same number of channels" }
            return
        }
        encoder = MediaCodec.createEncoderByType(OUTPUT_AUDIO_FORMAT)
        encoder.configure(MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, OUTPUT_AUDIO_FORMAT)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
        }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        outputTask = CoroutineScope(Dispatchers.IO).async { processOutput() }
    }

    private fun processInputFile(fd: FileDescriptor) {
        val extractor = MediaExtractor()
        extractor.setDataSource(fd)
        require(extractor.trackCount == 1) { "more than one track found in input file" }
        extractor.selectTrack(0)
        val trackFormat = extractor.getTrackFormat(0)
        require(isWavFormat(trackFormat)) { "input file is not in WAVE format" }
        initEncoder(trackFormat)
        while (true) {
            val inputBufferId = encoder.dequeueInputBuffer(-1)
            check(inputBufferId > -1)
            val read = extractor.readSampleData(encoder.getInputBuffer(inputBufferId)!!, 0)
            if (read < 0) {
                encoder.queueInputBuffer(inputBufferId, 0, 0, 0, 0)
                break
            }
            val sampleTimeUs = extractor.sampleTime + offsetTime
            encoder.queueInputBuffer(inputBufferId, 0, read, sampleTimeUs, 0)
            extractor.advance()
        }
        offsetTime += trackFormat.getLong(MediaFormat.KEY_DURATION)
        extractor.release()
    }

    private fun endInput() {
        encoder.queueInputBuffer(
            encoder.dequeueInputBuffer(-1),
            0,
            0,
            offsetTime,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM
        )
    }

    private fun processOutput() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (!isEndOfStream(bufferInfo)) {
            val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, -1)
            if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                check(muxerTrack == -1)
                muxerTrack = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                continue
            }
            check(outputBufferId > -1)
            if (!isCodecConfig(bufferInfo)) {
                muxer.writeSampleData(
                    muxerTrack,
                    encoder.getOutputBuffer(outputBufferId)!!,
                    bufferInfo
                )
            }
            encoder.releaseOutputBuffer(outputBufferId, false)
        }
    }

    private fun isWavFormat(trackFormat: MediaFormat) =
        trackFormat.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_RAW

    private fun isEndOfStream(bufferInfo: MediaCodec.BufferInfo) =
        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

    private fun isCodecConfig(bufferInfo: MediaCodec.BufferInfo) =
        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

    companion object {
        const val OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        const val OUTPUT_AUDIO_FORMAT = MediaFormat.MIMETYPE_AUDIO_AAC
        const val AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        const val MAX_INPUT_SIZE = 64 * 1024
        const val DEFAULT_BITRATE = 64000
    }
}
