package com.jeffdisher.cacophony.commands;


public record PublishCommand(String name, String discussionUrl, ElementSubCommand[] elements) implements ICommand
{
}
