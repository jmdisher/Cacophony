package com.jeffdisher.cacophony.commands;

import java.io.File;


public record UpdateDescriptionCommand(String name, String description, File picturePath) implements ICommand
{
}
