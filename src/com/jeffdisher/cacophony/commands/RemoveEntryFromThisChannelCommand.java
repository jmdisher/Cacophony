package com.jeffdisher.cacophony.commands;

import io.ipfs.multihash.Multihash;


public record RemoveEntryFromThisChannelCommand(Multihash elementCid) implements ICommand
{
}
