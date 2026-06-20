package com.loansai.unassisted.data.model.camunda

import com.google.gson.annotations.SerializedName

/**
 * Request model for starting a process via message
 */
data class CamundaMessageRequest(
    @SerializedName("messageName")
    val messageName: String,
    
    @SerializedName("businessKey")
    val businessKey: String? = null,
    
    @SerializedName("processVariables")
    val processVariables: Map<String, CamundaVariable> = emptyMap(),
    
    @SerializedName("correlationKeys")
    val correlationKeys: Map<String, CamundaVariable>? = null
)

/**
 * Variable type for Camunda process variables
 */
data class CamundaVariable(
    @SerializedName("value")
    val value: Any,
    
    @SerializedName("type")
    val type: String
) {
    companion object {
        fun createString(value: String): CamundaVariable {
            return CamundaVariable(value, "String")
        }
        
        fun createInteger(value: Int): CamundaVariable {
            return CamundaVariable(value, "Integer")
        }
        
        fun createLong(value: Long): CamundaVariable {
            return CamundaVariable(value, "Long")
        }
        
        fun createDouble(value: Double): CamundaVariable {
            return CamundaVariable(value, "Double")
        }
        
        fun createBoolean(value: Boolean): CamundaVariable {
            return CamundaVariable(value, "Boolean")
        }
        
        fun createJson(value: String): CamundaVariable {
            return CamundaVariable(value, "Json")
        }
    }
}

/**
 * Response model for process instance creation
 */
data class ProcessInstanceResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("definitionId")
    val definitionId: String,
    
    @SerializedName("businessKey")
    val businessKey: String?,
    
    @SerializedName("ended")
    val ended: Boolean,
    
    @SerializedName("suspended")
    val suspended: Boolean
)