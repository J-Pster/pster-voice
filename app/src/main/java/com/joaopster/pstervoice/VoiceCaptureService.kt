package com.joaopster.pstervoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class VoiceCaptureService : LifecycleService() {

    companion object {
        private const val TAG = "PsterVoice"
        private const val CHANNEL_ID = "pster_voice_capture_channel"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
        private const val INPUT_DEVICE_RETRY_COUNT = 4
        private const val INPUT_DEVICE_RETRY_INTERVAL_MS = 400L
        private const val ROUTED_DEVICE_SETTLE_DELAY_MS = 250L
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceCaptureService = this@VoiceCaptureService
    }

    private val binder = LocalBinder()

    private lateinit var audioManager: AudioManager
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var pcmBuffer: ByteArrayOutputStream? = null

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    fun startRecording(onRecordingStarted: () -> Unit) {
        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(this@VoiceCaptureService, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "RECORD_AUDIO nao concedida, abortando gravacao")
                withContext(Dispatchers.Main) { onRecordingStarted() }
                return@launch
            }

            val selectedDevice = resolvePreferredInputDevice()

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
            if (minBufferSize <= 0) {
                Log.e(TAG, "getMinBufferSize invalido: $minBufferSize")
                withContext(Dispatchers.Main) { onRecordingStarted() }
                return@launch
            }
            val bufferSizeInBytes = minBufferSize * 2

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    channelConfig,
                    audioFormat,
                    bufferSizeInBytes
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Erro de seguranca ao criar AudioRecord", e)
                withContext(Dispatchers.Main) { onRecordingStarted() }
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord nao inicializou")
                record.release()
                withContext(Dispatchers.Main) { onRecordingStarted() }
                return@launch
            }

            if (selectedDevice != null) {
                val preferredSet = record.setPreferredDevice(selectedDevice)
                Log.d(TAG, "AudioRecord.setPreferredDevice(${deviceTypeName(selectedDevice.type)}): $preferredSet")
            }

            record.startRecording()
            audioRecord = record
            pcmBuffer = ByteArrayOutputStream()

            delay(ROUTED_DEVICE_SETTLE_DELAY_MS)
            Log.d(TAG, "Gravacao iniciada, routedDevice(AudioRecord)=${record.routedDevice}")
            withContext(Dispatchers.Main) { onRecordingStarted() }

            val buffer = ByteArray(bufferSizeInBytes)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcmBuffer?.write(buffer, 0, read)
                    _audioLevel.value = calculateRms(buffer, read)
                }
            }
        }
    }

    fun stopRecordingAndTranscribe(onResult: (ElevenLabsClient.TranscriptionResult) -> Unit) {
        val job = captureJob
        captureJob = null

        lifecycleScope.launch {
            job?.cancelAndJoin()

            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
            }
            audioRecord?.release()
            audioRecord = null
            _audioLevel.value = 0f

            val pcmData = pcmBuffer?.toByteArray()
            pcmBuffer = null

            if (pcmData == null || pcmData.isEmpty()) {
                Log.d(TAG, "Gravacao finalizada, bytes lidos=0, arquivo=null")
                onResult(ElevenLabsClient.TranscriptionResult.Failure)
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                val wavFile = WavUtils.writeWavFile(cacheDir, pcmData, SAMPLE_RATE)
                Log.d(TAG, "Gravacao finalizada, bytes lidos=${pcmData.size}, arquivo=${wavFile.absolutePath}")
                ElevenLabsClient.transcribe(wavFile)
            }
            onResult(result)
        }
    }

    fun cancelRecording() {
        val job = captureJob
        captureJob = null

        lifecycleScope.launch {
            job?.cancelAndJoin()

            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
            }
            audioRecord?.release()
            audioRecord = null
            pcmBuffer = null
            _audioLevel.value = 0f
            Log.d(TAG, "Gravacao cancelada pelo usuario, audio descartado")
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumOfSquares = 0.0
        var sampleCount = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sumOfSquares += sample * sample
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return 0f
        val rms = sqrt(sumOfSquares / sampleCount)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    private suspend fun resolvePreferredInputDevice(): AudioDeviceInfo? {
        repeat(INPUT_DEVICE_RETRY_COUNT) { attempt ->
            val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            if (attempt == 0) {
                Log.d(TAG, "Devices de entrada disponiveis: ${describeDevices(inputDevices)}")
            }

            val target = inputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: inputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

            if (target != null) {
                Log.d(TAG, "Device de entrada bluetooth escolhido: ${deviceTypeName(target.type)}(${target.productName})")
                return target
            }

            if (attempt < INPUT_DEVICE_RETRY_COUNT - 1) {
                delay(INPUT_DEVICE_RETRY_INTERVAL_MS)
            }
        }

        Log.d(TAG, "Nenhum device de entrada bluetooth encontrado, gravando com o device default")
        return null
    }

    private fun describeDevices(devices: List<AudioDeviceInfo>): String {
        if (devices.isEmpty()) return "nenhum"
        return devices.joinToString { "${deviceTypeName(it.type)}(${it.productName})" }
    }

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        else -> "TYPE_$type"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_listening))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        captureJob?.cancel()
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
        }
        audioRecord?.release()
        audioRecord = null
        _audioLevel.value = 0f
        super.onDestroy()
    }
}
