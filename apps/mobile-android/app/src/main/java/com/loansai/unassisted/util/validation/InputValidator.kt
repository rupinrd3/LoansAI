package com.loansai.unassisted.util.validation

import com.loansai.unassisted.util.extensions.isValidEmail
import com.loansai.unassisted.util.extensions.isValidMobileNumber
import com.loansai.unassisted.util.extensions.isValidPAN
import java.time.LocalDate

/**
 * Utility class for validating user input
 */
object InputValidator {

    /**
     * Validates a mobile number
     * @param mobileNumber The mobile number to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateMobileNumber(mobileNumber: String): ValidationResult {
        return when {
            mobileNumber.isBlank() -> {
                ValidationResult.Error("Mobile number cannot be empty")
            }
            !mobileNumber.isValidMobileNumber() -> {
                ValidationResult.Error("Please enter a valid 10-digit mobile number")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates a PAN number
     * @param pan The PAN number to validate
     * @return ValidationResult with success/error status and message
     */
    fun validatePAN(pan: String): ValidationResult {
        return when {
            pan.isBlank() -> {
                ValidationResult.Error("PAN number cannot be empty")
            }
            !pan.isValidPAN() -> {
                ValidationResult.Error("Please enter a valid PAN number (AAAAA0000A)")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates an email address
     * @param email The email address to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> {
                ValidationResult.Error("Email address cannot be empty")
            }
            !email.isValidEmail() -> {
                ValidationResult.Error("Please enter a valid email address")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates a name
     * @param name The name to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateName(name: String): ValidationResult {
        return when {
            name.isBlank() -> {
                ValidationResult.Error("Name cannot be empty")
            }
            name.length < 3 -> {
                ValidationResult.Error("Name should be at least 3 characters")
            }
            name.contains(Regex("[^a-zA-Z\\s.]")) -> {
                ValidationResult.Error("Name should contain only letters, spaces, and dots")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates a date of birth
     * @param dob The date of birth to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateDateOfBirth(dob: LocalDate): ValidationResult {
        val now = LocalDate.now()
        val minAge = 18
        val maxAge = 65
        
        val age = now.year - dob.year - if (now.dayOfYear < dob.dayOfYear) 1 else 0
        
        return when {
            age < minAge -> {
                ValidationResult.Error("You must be at least $minAge years old")
            }
            age > maxAge -> {
                ValidationResult.Error("Age cannot be greater than $maxAge years")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates a monthly income
     * @param income The income amount to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateMonthlyIncome(income: String): ValidationResult {
        val incomeValue = income.toDoubleOrNull()
        
        return when {
            income.isBlank() -> {
                ValidationResult.Error("Monthly income cannot be empty")
            }
            incomeValue == null -> {
                ValidationResult.Error("Please enter a valid income amount")
            }
            incomeValue < 10000 -> {
                ValidationResult.Error("Income should be at least ₹10,000")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates years of experience
     * @param experience The years of experience to validate
     * @return ValidationResult with success/error status and message
     */
    fun validateExperience(experience: String): ValidationResult {
        val experienceValue = experience.toFloatOrNull()
        
        return when {
            experience.isBlank() -> {
                ValidationResult.Error("Years of experience cannot be empty")
            }
            experienceValue == null -> {
                ValidationResult.Error("Please enter a valid number")
            }
            experienceValue < 0 -> {
                ValidationResult.Error("Experience cannot be negative")
            }
            experienceValue > 45 -> {
                ValidationResult.Error("Please verify your years of experience")
            }
            else -> {
                ValidationResult.Success
            }
        }
    }

    /**
     * Validates a work email domain against employer name
     * @param email The work email to validate
     * @param employerName The employer name to check against
     * @return ValidationResult with success/error status and message
     */
    fun validateWorkEmail(email: String, employerName: String): ValidationResult {
        if (!email.isValidEmail()) {
            return ValidationResult.Error("Please enter a valid email address")
        }
        
        // Extract domain from email
        val domain = email.substringAfterLast("@", "")
        
        // Check if domain contains any part of employer name (simplified check)
        val employerWords = employerName.lowercase().split(" ", "-", ".")
            .filter { it.length > 2 } // only consider words with length > 2
        
        val isDomainRelated = employerWords.any { domain.contains(it.lowercase()) }
        
        return if (isDomainRelated || domain.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Warning("The email domain doesn't seem to match your employer. Please verify.")
        }
    }
}

/**
 * Represents the result of a validation check
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
}