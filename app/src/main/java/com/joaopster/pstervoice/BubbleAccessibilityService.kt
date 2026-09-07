package com.joaopster.pstervoice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class BubbleAccessibilityService : AccessibilityService() {

    private enum class BubbleState { IDLE, LISTENING, PROCESSING }

    companion object {
        private const val TAG = "PsterVoice"
        private const val BUBBLE_SIZE_DP = 38
        private const val PILL_WIDTH_DP = 140
        private const val ACTIVE_SIZE_MULTIPLIER = 1.10f
        // aumento de 20% em cima do que ja estava (ocioso e ativo, os dois)
        private const val SIZE_BOOST_MULTIPLIER = 1.20f
        private const val EDGE_PADDING_DP = 16
        private const val DRAG_THRESHOLD_PX = 20
        private const val HOLD_THRESHOLD_MS = 450L

        private const val PREFS_NAME = "pster_voice_prefs"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val DEFAULT_Y = 300
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: BubbleView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var compactSizePx = 0
    private var expandedWidthPx = 0
    private var expandedHeightPx = 0

    private var voiceCaptureService: VoiceCaptureService? = null
    private var isBound = false
    private var state = BubbleState.IDLE
    private var lastFocusedEditableNode: AccessibilityNodeInfo? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioLevelJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as VoiceCaptureService.LocalBinder).getService()
            voiceCaptureService = service
            isBound = true
            if (state == BubbleState.LISTENING) {
                service.startRecording {
                    if (state == BubbleState.LISTENING) {
                        bubbleView.setState(BubbleView.State.LISTENING)
                    }
                }
            }
            audioLevelJob = serviceScope.launch {
                service.audioLevel.collect { level -> bubbleView.setAudioLevel(level) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceCaptureService = null
            isBound = false
            audioLevelJob?.cancel()
            audioLevelJob = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupBubble()
    }

    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            return
        }

        val source = event.source ?: return
        if (isEditableCandidate(source)) {
            lastFocusedEditableNode?.recycle()
            lastFocusedEditableNode = source
        }
    }

    override fun onInterrupt() {}

    private fun setupBubble() {
        bubbleView = BubbleView(this)

        compactSizePx = (BUBBLE_SIZE_DP * SIZE_BOOST_MULTIPLIER * resources.displayMetrics.density).toInt()
        expandedWidthPx = (PILL_WIDTH_DP * ACTIVE_SIZE_MULTIPLIER * SIZE_BOOST_MULTIPLIER * resources.displayMetrics.density).toInt()
        expandedHeightPx = (BUBBLE_SIZE_DP * ACTIVE_SIZE_MULTIPLIER * SIZE_BOOST_MULTIPLIER * resources.displayMetrics.density).toInt()

        val (savedX, savedY) = loadSavedPosition()
        layoutParams = WindowManager.LayoutParams(
            compactSizePx,
            compactSizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var holdTriggered = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    holdTriggered = false

                    if (state == BubbleState.IDLE) {
                        val runnable = Runnable {
                            if (!isDragging) {
                                holdTriggered = true
                                startListening()
                            }
                        }
                        holdRunnable = runnable
                        handler.postDelayed(runnable, HOLD_THRESHOLD_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > DRAG_THRESHOLD_PX || abs(dy) > DRAG_THRESHOLD_PX)) {
                        isDragging = true
                        holdRunnable?.let { handler.removeCallbacks(it) }
                    }
                    if (isDragging && state == BubbleState.IDLE) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    holdRunnable?.let { handler.removeCallbacks(it) }
                    holdRunnable = null

                    when {
                        isDragging -> {
                            if (state == BubbleState.IDLE) {
                                layoutParams.x = snapToNearestEdge(layoutParams.x)
                                windowManager.updateViewLayout(bubbleView, layoutParams)
                                savePosition(layoutParams.x, layoutParams.y)
                            }
                        }
                        holdTriggered -> stopListeningAndTranscribe()
                        else -> handleTap(event.x)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, layoutParams)
    }

    private fun handleTap(touchX: Float) {
        when (state) {
            BubbleState.IDLE -> startListening()
            BubbleState.LISTENING -> {
                when (bubbleView.hitTestZone(touchX)) {
                    BubbleView.Zone.CONFIRM -> stopListeningAndTranscribe()
                    BubbleView.Zone.CANCEL -> cancelListening()
                    BubbleView.Zone.WAVEFORM -> {}
                }
            }
            BubbleState.PROCESSING -> {}
        }
    }

    private fun startListening() {
        state = BubbleState.LISTENING
        expandBubble()
        bubbleView.setState(BubbleView.State.CONNECTING)

        val intent = Intent(this, VoiceCaptureService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopListeningAndTranscribe() {
        state = BubbleState.PROCESSING
        bubbleView.setState(BubbleView.State.PROCESSING)

        val service = voiceCaptureService
        if (service == null) {
            finishCycle()
            return
        }

        service.stopRecordingAndTranscribe { result ->
            when (result) {
                is ElevenLabsClient.TranscriptionResult.Success -> pasteOrCopyText(result.text)
                ElevenLabsClient.TranscriptionResult.NoSpeech -> {
                    Toast.makeText(this, getString(R.string.toast_no_speech_detected), Toast.LENGTH_SHORT).show()
                }
                ElevenLabsClient.TranscriptionResult.Failure -> {
                    Toast.makeText(this, getString(R.string.toast_transcription_failed), Toast.LENGTH_SHORT).show()
                }
            }
            finishCycle()
        }
    }

    private fun cancelListening() {
        voiceCaptureService?.cancelRecording()
        teardownServiceConnection()
        resetToIdle()
    }

    private fun finishCycle() {
        teardownServiceConnection()
        resetToIdle()
    }

    private fun teardownServiceConnection() {
        audioLevelJob?.cancel()
        audioLevelJob = null
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        stopService(Intent(this, VoiceCaptureService::class.java))
        voiceCaptureService = null
    }

    private fun resetToIdle() {
        state = BubbleState.IDLE
        bubbleView.setState(BubbleView.State.IDLE)
        collapseBubble()
    }

    private fun expandBubble() {
        layoutParams.x = recalculateAnchoredX(expandedWidthPx)
        layoutParams.width = expandedWidthPx
        layoutParams.height = expandedHeightPx
        windowManager.updateViewLayout(bubbleView, layoutParams)
    }

    private fun collapseBubble() {
        layoutParams.x = recalculateAnchoredX(compactSizePx)
        layoutParams.width = compactSizePx
        layoutParams.height = compactSizePx
        windowManager.updateViewLayout(bubbleView, layoutParams)
    }

    private fun recalculateAnchoredX(newWidth: Int): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val paddingPx = (EDGE_PADDING_DP * resources.displayMetrics.density).toInt()
        val anchoredRight = layoutParams.x > screenWidth / 2
        return if (anchoredRight) {
            screenWidth - newWidth - paddingPx
        } else {
            paddingPx
        }
    }

    private fun snapToNearestEdge(currentX: Int): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val paddingPx = (EDGE_PADDING_DP * resources.displayMetrics.density).toInt()
        val bubbleCenterX = currentX + compactSizePx / 2
        return if (bubbleCenterX < screenWidth / 2) {
            paddingPx
        } else {
            screenWidth - compactSizePx - paddingPx
        }
    }

    private fun loadSavedPosition(): Pair<Int, Int> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BUBBLE_X, 0) to prefs.getInt(KEY_BUBBLE_Y, DEFAULT_Y)
    }

    private fun savePosition(x: Int, y: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .apply()
    }

    private fun isEditableCandidate(node: AccessibilityNodeInfo): Boolean =
        node.isEditable || node.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SET_TEXT || it.id == AccessibilityNodeInfo.ACTION_PASTE
        }

    @Suppress("DEPRECATION")
    private fun resolveTargetNode(): AccessibilityNodeInfo? {
        val tracked = lastFocusedEditableNode
        if (tracked != null) {
            if (tracked.refresh() && isEditableCandidate(tracked)) {
                Log.d(TAG, "No alvo: usando ultimo campo editavel rastreado via evento")
                return tracked
            }
            tracked.recycle()
            lastFocusedEditableNode = null
        }

        val focusedViaApi = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedViaApi != null && isEditableCandidate(focusedViaApi)) {
            Log.d(TAG, "No alvo: usando findFocus(FOCUS_INPUT)")
            return focusedViaApi
        }

        Log.w(TAG, "No alvo: nenhum campo rastreado ou focado valido, usando heuristica de arvore inteira (pode estar errado)")
        return findEditableNode(rootInActiveWindow)
    }

    private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var bestCandidate: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (isEditableCandidate(node)) {
                if (node.isFocused) return node
                if (bestCandidate == null) bestCandidate = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return bestCandidate
    }

    private fun pasteOrCopyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("pster-voice", text))

        val targetNode = resolveTargetNode()
        if (targetNode != null) {
            Log.d(
                TAG,
                "No focado: className=${targetNode.className}, packageName=${targetNode.packageName}, " +
                    "isEditable=${targetNode.isEditable}, isFocused=${targetNode.isFocused}, " +
                    "actions=${targetNode.actionList.map { it.id }}"
            )

            val pasteResult = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d(TAG, "Colagem: no encontrado=true, resultado ACTION_PASTE=$pasteResult")
            if (!pasteResult) {
                val fallbackResult = pasteViaSetText(targetNode, text)
                Log.d(TAG, "Colagem: fallback ACTION_SET_TEXT usado, resultado=$fallbackResult")
            }
        } else {
            Log.d(TAG, "Colagem: no encontrado=false, resultado ACTION_PASTE=n/a")
            Toast.makeText(this, getString(R.string.toast_no_focused_field), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteViaSetText(node: AccessibilityNodeInfo, insertedText: String): Boolean {
        val currentText = node.text?.toString() ?: ""
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd

        val newText = if (selectionStart in 0..currentText.length && selectionEnd in selectionStart..currentText.length) {
            currentText.substring(0, selectionStart) + insertedText + currentText.substring(selectionEnd)
        } else {
            currentText + insertedText
        }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        if (::windowManager.isInitialized && ::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        serviceScope.cancel()
        lastFocusedEditableNode?.recycle()
        lastFocusedEditableNode = null
        super.onDestroy()
    }
}
