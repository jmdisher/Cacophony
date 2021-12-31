package com.jeffdisher.cacophony.commands;

import io.ipfs.multihash.Multihash;


public record RemoveRecommendationCommand(Multihash channelPublicKey) implements ICommand
{
}
