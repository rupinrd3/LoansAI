package com.loansai.unassisted.service.voice.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.loansai.unassisted.service.voice.VoiceRecognitionCallback
import com.loansai.unassisted.service.voice.VoiceRecognitionService
import com.loansai.unassisted.util.logger.AppLogger
import java.util.Locale
import javax.inject.Inject

/**
 * Google Speech Recognition implementation
 */
class GoogleVoiceRecognitionService @Inject constructor(
    private val context: Context
) : VoiceRecognitionService {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceRecognitionCallback? = null
    
    /**
     * Check if voice recognition is available on the device
     */
    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Start listening for voice input
     */
    override fun startListening(callback: VoiceRecognitionCallback) {
        startListening(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            1,
            callback
        )
    }
    
    /**
     * Start listening with specific language model and max results
     */
    override fun startListening(
        languageModel: String,
        maxResults: Int,
        callback: VoiceRecognitionCallback
    ) {
        this.callback = callback
        
        // Initialize speech recognizer if needed
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                languageModel
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("en", "IN").toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                30000L  // 30 seconds max
            )
        }
        
        try {
            speechRecognizer?.startListening(intent)
            callback.onListeningStarted()
        } catch (e: Exception) {
            AppLogger.e("Error starting speech recognition", e)
            callback.onError(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Stop listening
     */
    override fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    /**
     * Destroy the service (release resources)
     */
    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        callback = null
    }
    
    /**
     * Initialize the speech recognizer
     */
    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Ready to receive speech
                }
                
                override fun onBeginningOfSpeech() {
                    // User has started speaking
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Sound level changed
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // More sound has been received
                }
                
                override fun onEndOfSpeech() {
                    // User has stopped speaking
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    
                    AppLogger.e("Speech recognition error: $errorMessage")
                    callback?.onError(errorMessage)
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        callback?.onResults(matches)
                    } else {
                        callback?.onError("No recognition results")
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial recognition results are available
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Reserved for future events
                }
            })
        }
    }
}