package com.jeffdisher.cacophony;


/**
 * The description of a single command-line parameter.
 */
public class ArgParameter
{
	public final String name;
	public final ParameterType type;
	private final String _description;

	/**
	 * Creates a new parameter instance.
	 * 
	 * @param name The parameter name (typically something like "--param").
	 * @param type The type into which the parameter value should be parsed.
	 * @param description The human-readable description of what the parameter does.
	 */
	public ArgParameter(String name, ParameterType type, String description)
	{
		this.name = name;
		this.type = type;
		_description = description;
	}

	/**
	 * @return A short description of the parameter, suitable for a usage string, showing the name and type.
	 */
	public String shortDescription()
	{
		return this.name + " <" + this.type.shortDescription + ">";
	}

	/**
	 * @return A long description of the parameter, suitable for a help string, showing the name, type, and description.
	 */
	public String longDescription()
	{
		return this.name + " <" + this.type.shortDescription + "> : " + _description;
	}
}
