package com.loansai.unassisted.util.extensions

import java.util.regex.Pattern

/**
 * Extension functions for String operations
 */

/**
 * Validates if the string is a valid mobile number (10 digits)
 */
fun String.isValidMobileNumber(): Boolean {
    val pattern = Pattern.compile("^[6-9]\\d{9}$")
    return pattern.matcher(this).matches()
}

/**
 * Validates if the string is a valid PAN number (AAAAA0000A format)
 */
fun String.isValidPAN(): Boolean {
    val pattern = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")
    return pattern.matcher(this).matches()
}

/**
 * Validates if the string is a valid email address
 */
fun String.isValidEmail(): Boolean {
    val pattern = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}" +
                "[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    )
    return pattern.matcher(this).matches()
}

/**
 * Formats a phone number with spaces for better readability
 * E.g., "9876543210" becomes "9876 543 210"
 */
fun String.formatPhoneNumber(): String {
    if (!this.isValidMobileNumber()) return this
    
    return buildString {
        append(this@formatPhoneNumber.substring(0, 4))
        append(" ")
        append(this@formatPhoneNumber.substring(4, 7))
        append(" ")
        append(this@formatPhoneNumber.substring(7, 10))
    }
}

/**
 * Formats currency amount with commas and the rupee symbol
 * E.g., "100000" becomes "₹1,00,000"
 */
fun String.formatCurrency(): String {
    val amount = this.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return "₹0"
    
    val formatter = java.text.DecimalFormat("##,##,##0")
    return "₹${formatter.format(amount)}"
}

/**
 * Formats a PAN number with spaces for better readability
 * E.g., "ABCDE1234F" becomes "ABCDE 1234F"
 */
fun String.formatPAN(): String {
    if (!this.isValidPAN()) return this
    
    return "${this.substring(0, 5)} ${this.substring(5)}"
}

/**
 * Masks the middle part of a phone number for privacy
 * E.g., "9876543210" becomes "987XXX3210"
 */
fun String.maskPhone(): String {
    if (this.length != 10) return this
    
    return "${this.substring(0, 3)}XXX${this.substring(6)}"
}

/**
 * Masks the middle part of a PAN for privacy
 * E.g., "ABCDE1234F" becomes "ABC**1234F"
 */
fun String.maskPAN(): String {
    if (!this.isValidPAN()) return this
    
    return "${this.substring(0, 3)}**${this.substring(5)}"
}

/**
 * Capitalizes each word in a string
 * E.g., "john doe" becomes "John Doe"
 */
fun String.capitalizeWords(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
    }
}