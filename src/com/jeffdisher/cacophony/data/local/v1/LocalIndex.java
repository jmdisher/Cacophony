package com.jeffdisher.cacophony.data.local.v1;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;


public record LocalIndex(String ipfsHost, String keyName, IpfsFile lastPublishedIndex) implements Serializable
{
}
