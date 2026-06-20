package com.loansai.unassisted.service.voice

/**
 * Service interface for voice recognition
 */
interface VoiceRecognitionService {
    
    /**
     * Check if voice recognition is available on the device
     */
    fun isAvailable(): Boolean
    
    /**
     * Start listening for voice input
     * 
     * @param callback Callback to handle recognition events
     */
    fun startListening(callback: VoiceRecognitionCallback)
    
    /**
     * Start listening with specific language model and max results
     * 
     * @param languageModel The language model to use
     * @param maxResults Maximum number of results to return
     * @param callback Callback to handle recognition events
     */
    fun startListening(
        languageModel: String,
        maxResults: Int = 1,
        callback: VoiceRecognitionCallback
    )
    
    /**
     * Stop listening
     */
    fun stopListening()
    
    /**
     * Destroy the service (release resources)
     */
    fun destroy()
}

/**
 * Callback interface for voice recognition events
 */
interface VoiceRecognitionCallback {
    
    /**
     * Called when listening starts
     */
    fun onListeningStarted()
    
    /**
     * Called when results are available
     * 
     * @param results List of recognition results
     */
    fun onResults(results: List<String>)
    
    /**
     * Called when a recognition error occurs
     * 
     * @param error Error message
     */
    fun onError(error: String)
}