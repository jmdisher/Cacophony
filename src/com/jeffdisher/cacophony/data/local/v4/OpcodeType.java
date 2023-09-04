package com.jeffdisher.cacophony.data.local.v4;


/**
 * The types of data opcodes in the stream where ordinals are used to identify the types in the serialized data.
 * These ordinals are common across V3 and V4 data types, since there is a high degree of overlap.
 * NOTE:  This order cannot change while V3 data is live or V4.
 */
public enum OpcodeType
{
	ERROR,
	DEFINE_CHANNEL,
	DEPRECATED_V3_SET_FOLLOWEE_STATE,
	ADD_FOLLOWEE_ELEMENT,
	
	SET_PREFS_FIELD_INT,
	SET_PREFS_FIELD_LONG,
	
	DEPRECATED_V3_EXPLICIT_USER_INFO,
	DEPRECATED_V3_EXPLICIT_STREAM_RECORD,
	
	FAVOURITE_STREAM_RECORD,
	
	// Data types added in V4.
	EXPLICIT_USER_INFO,
	EXPLICIT_STREAM_RECORD,
	
	SET_FOLLOWEE_STATE,
	
	END_OF_LIST,
}
