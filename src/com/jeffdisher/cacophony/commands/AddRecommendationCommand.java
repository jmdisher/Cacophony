package com.jeffdisher.cacophony.commands;

import io.ipfs.multihash.Multihash;


public record AddRecommendationCommand(Multihash channelPublicKey) implements ICommand
{
}
