package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object for Application Decision
 */
data class DecisionDto(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("reason")
    val reason: String? = null,
    
    @SerializedName("comments")
    val comments: String? = null
)