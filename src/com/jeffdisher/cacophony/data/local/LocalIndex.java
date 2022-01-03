package com.jeffdisher.cacophony.data.local;

import java.io.Serializable;


public record LocalIndex(String ipfsHost, String keyName) implements Serializable
{
}
