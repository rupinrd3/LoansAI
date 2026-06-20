package com.loansai.unassisted.service.email

import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Service for sending emails using Brevo (formerly SendinBlue) API
 */
@Singleton
class BrevoEmailService @Inject constructor(
    @Named("brevoClient") private val client: OkHttpClient
) {
    // Brevo API key from BuildConfig
    private val BREVO_API_KEY = BuildConfig.BREVO_API_KEY
    
    // IMPORTANT: Change this to your verified email from Brevo account
    private val VERIFIED_SENDER_EMAIL = "rupinrd3@gmail.com" // Replace with YOUR verified email
    private val SENDER_NAME = "LoansAI App"
    
    /**
     * Send OTP email via Brevo API
     */
    suspend fun sendOtpEmail(email: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("BrevoEmailService: Preparing to send OTP email to $email using sender $VERIFIED_SENDER_EMAIL")
            
            // Create JSON payload using JSONObject for better formatting and error checking
            val jsonPayload = JSONObject()
            
            // Set sender (use your verified email)
            val senderJson = JSONObject()
            senderJson.put("name", SENDER_NAME)
            senderJson.put("email", VERIFIED_SENDER_EMAIL)
            jsonPayload.put("sender", senderJson)
            
            // Set recipient
            val recipientArray = JSONArray()
            val recipientJson = JSONObject()
            recipientJson.put("email", email)
            recipientArray.put(recipientJson)
            jsonPayload.put("to", recipientArray)
            
            // Set subject and content
            jsonPayload.put("subject", "Your LoansAI Verification Code")
            jsonPayload.put("htmlContent", """
                <html><body>
                <div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px;'>
                    <h2 style='color: #4a6ee0;'>LoansAI Email Verification</h2>
                    <p>Your verification code is:</p>
                    <h1 style='font-size: 32px; letter-spacing: 5px; background-color: #f0f0f0; padding: 10px; text-align: center; border-radius: 5px;'>$otp</h1>
                    <p>This code will expire in 15 minutes.</p>
                    <p>If you didn't request this code, please ignore this email.</p>
                    <hr>
                    <p style='font-size: 12px; color: #666;'>© 2025 LoansAI. All rights reserved.</p>
                </div>
                </body></html>
            """.trimIndent())
            
            // Log the JSON payload for debugging
            AppLogger.d("BrevoEmailService: Request JSON: ${jsonPayload.toString(2)}")
            
            // Convert JSON to request body
            val mediaType = "application/json".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)
            
            // Create and execute request
            val request = Request.Builder()
                .url("https://api.brevo.com/v3/smtp/email")
                .addHeader("accept", "application/json")
                .addHeader("api-key", BREVO_API_KEY)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()
                
            // Log request headers (except API key for security)
            AppLogger.d("BrevoEmailService: Request headers: accept=${request.header("accept")}, content-type=${request.header("content-type")}")
            
            // Execute the request
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No response body"
            val success = response.isSuccessful
            
            // Log detailed result
            AppLogger.d("BrevoEmailService: Response code: ${response.code}")
            AppLogger.d("BrevoEmailService: Response body: $responseBody")
            
            if (success) {
                AppLogger.i("BrevoEmailService: OTP email sent successfully to: $email")
            } else {
                AppLogger.e("BrevoEmailService: Email failed: ${response.code} - $responseBody")
            }
            
            return@withContext success
        } catch (e: Exception) {
            AppLogger.e("BrevoEmailService: Error sending email: ${e.message}")
            e.printStackTrace() // Print stack trace for debugging
            return@withContext false
        }
    }
}