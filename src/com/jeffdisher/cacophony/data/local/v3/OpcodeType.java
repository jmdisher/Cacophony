package com.jeffdisher.cacophony.data.local.v3;


/**
 * The types of data opcodes in the stream where ordinals are used to identify the types in the serialized data.
 * NOTE:  This order cannot change while V3 data is live.
 */
public enum OpcodeType
{
	ERROR,

	DEFINE_CHANNEL,
	SET_FOLLOWEE_STATE,
	ADD_FOLLOWEE_ELEMENT,
	
	SET_PREFS_FIELD_INT,
	SET_PREFS_FIELD_LONG,
	
	END_OF_LIST,
}
