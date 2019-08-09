package com.pushpushgo.sdk.network.data

internal data class ObjectResponse(
    var _id:String? = "",
    var message: String? = "",
    var messages: Array<String>? = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectResponse

        if (_id != other._id) return false
        if (message != other.message) return false
        if (messages != null) {
            if (other.messages == null) return false
        } else if (other.messages != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _id?.hashCode() ?: 0
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (messages?.contentHashCode() ?: 0)
        return result
    }
}

internal data class TokenRequest(
    var token:String=""
)