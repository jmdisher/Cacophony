package com.jeffdisher.cacophony;


public class ArgParameter
{
	public final String name;
	public final ParameterType type;

	public ArgParameter(String name, ParameterType type)
	{
		this.name = name;
		this.type = type;
	}

	public String shortDescription()
	{
		return this.name + " <" + this.type.shortDescription + ">";
	}
}
