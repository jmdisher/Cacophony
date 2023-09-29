package com.jeffdisher.cacophony.types;


/**
 * This interface is similar to Function but allows us to declare our decoder exception type.
 */
public interface DataDeserializer<T>
{
	/**
	 * Decodes the given data and returns the high-level type, throwing if the deserialization failed.
	 * 
	 * @param data The input data (never null).
	 * @return The deserialized instance.
	 * @throws FailedDeserializationException If the data couldn't be decoded as the expected type.
	 */
	T apply(byte[] data) throws FailedDeserializationException;
}
