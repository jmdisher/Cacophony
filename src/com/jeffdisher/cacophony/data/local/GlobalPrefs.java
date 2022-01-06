package com.jeffdisher.cacophony.data.local;

import java.io.Serializable;


public record GlobalPrefs(int cacheWidth, int cacheHeight, long cacheTotalBytes) implements Serializable
{
}
