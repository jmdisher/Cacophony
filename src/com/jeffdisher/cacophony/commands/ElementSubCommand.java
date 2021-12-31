package com.jeffdisher.cacophony.commands;

import java.io.File;


public record ElementSubCommand(String mime, File filePath, String codec, int height, int width) implements ICommand
{
}
