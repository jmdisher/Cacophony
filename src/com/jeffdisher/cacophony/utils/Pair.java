package com.jeffdisher.cacophony.utils;


/**
 * We often need short-lived pairs of objects so this type is provided as a basic utility.
 * 
 * @param <A> The first element type.
 * @param <B> The second element type.
 */
public record Pair<A, B>(A first, B second)
{
}
