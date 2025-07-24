package com.mike.domain.model.mpesa

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class StkCallbackResponse(
    @SerialName("Body") val body: StkCallbackBody
)

@Serializable
data class StkCallbackBody(
    @SerialName("stkCallback") val stkCallback: StkCallback
)

@Serializable
data class StkCallback(
    @SerialName("MerchantRequestID") val merchantRequestID: String? = null,
    @SerialName("CheckoutRequestID") val checkoutRequestID: String? = null,
    @SerialName("ResultCode") val resultCode: Int? = null,
    @SerialName("ResultDesc") val resultDesc: String? = null,
    @SerialName("CallbackMetadata") val callbackMetadata: CallbackMetadata? = null
)

@Serializable
data class CallbackMetadata(
    @SerialName("Item") val item: List<CallbackItem>? = null
)

@Serializable
data class CallbackItem(
    @SerialName("Name") val name: String,
    @SerialName("Value") @Serializable(with = AnyValueSerializer::class) val value: Any? = null
)

/**
 * Custom serializer to handle different value types in M-Pesa callback
 */
@OptIn(ExperimentalSerializationApi::class)
object AnyValueSerializer : KSerializer<Any?> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("AnyValue", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Any?) {
        // Not implemented as we only care about deserialization
        throw NotImplementedError("Serialization of callback values not implemented")
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("This serializer can only be used with JSON")
        val jsonElement = jsonDecoder.decodeJsonElement()

        return when (jsonElement) {
            is JsonPrimitive -> {
                when {
                    jsonElement.isString -> jsonElement.content
                    jsonElement.intOrNull != null -> jsonElement.int
                    jsonElement.longOrNull != null -> jsonElement.long
                    jsonElement.doubleOrNull != null -> jsonElement.double
                    else -> jsonElement.content
                }
            }
            else -> null
        }
    }
}
