package com.jeffdisher.cacophony;


public class ArgParameter
{
	public final String name;
	public final ParameterType type;
	private final String _description;

	public ArgParameter(String name, ParameterType type, String description)
	{
		this.name = name;
		this.type = type;
		_description = description;
	}

	public String shortDescription()
	{
		return this.name + " <" + this.type.shortDescription + ">";
	}

	public String longDescription()
	{
		return this.name + " <" + this.type.shortDescription + "> : " + _description;
	}
}
