package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Contains the logic for interpreting the meaning of the leaf elements in StreamRecord instances.
 */
public class LeafFinder
{
	/**
	 * We will parse the record in a factory method since doing heavy work in a constructor doesn't quite seem right.
	 * 
	 * @param record The record to parse.
	 * @return The populated LeafFinder.
	 */
	public static LeafFinder parseRecord(StreamRecord record)
	{
		// We want to find all the videos, a single thumbnail, and a single audio element.  There can be 0 of any of these.
		
		IpfsFile thumbnail = null;
		IpfsFile audio = null;
		List<VideoLeaf> videos = new ArrayList<>();
		
		for (DataElement elt : record.getElements().getElement())
		{
			IpfsFile eltCid = IpfsFile.fromIpfsCid(elt.getCid());
			String mime = elt.getMime();
			if (null != elt.getSpecial())
			{
				// There shouldn't be more than one of these, but the data could be untrusted so will just take the last.
				thumbnail = eltCid;
			}
			else if (mime.startsWith("video/"))
			{
				// We constrain video selection based on the maximum edge size so find that here.
				int biggestEdge = Math.max(elt.getWidth(), elt.getHeight());
				videos.add(new VideoLeaf(eltCid, mime, biggestEdge));
			}
			else if (mime.startsWith("audio/"))
			{
				// If there are multiple audio attachments, we will just grab the last one (since that use-case isn't currently defined).
				audio = eltCid;
			}
		}
		
		// We want to sort the videos and make them into an array.
		VideoLeaf[] sortedVideos = videos.stream()
				.sorted((VideoLeaf elt1, VideoLeaf elt2) -> (elt1.edgeSize > elt2.edgeSize) ? 1 : -1)
				.collect(Collectors.toList())
				.toArray((int size) -> new VideoLeaf[size])
		;
		
		return new LeafFinder(thumbnail, audio, sortedVideos);
	}


	public final IpfsFile thumbnail;
	public final IpfsFile audio;
	// NOTE:  This list is in ascending order, so larger videos are at the END of the list.
	public final VideoLeaf[] sortedVideos;

	private LeafFinder(IpfsFile thumbnail, IpfsFile audio, VideoLeaf[] sortedVideos)
	{
		this.thumbnail = thumbnail;
		this.audio = audio;
		this.sortedVideos = sortedVideos;
	}

	public VideoLeaf largestVideoWithLimit(int maxEdge)
	{
		// The larger videos are at the end so we will just grab the last one which matches our comparison.
		VideoLeaf match = null;
		for (VideoLeaf elt : this.sortedVideos)
		{
			if (elt.edgeSize <= maxEdge)
			{
				match = elt;
			}
		}
		return match;
	}


	public static record VideoLeaf(IpfsFile cid, String mime, int edgeSize) {}
}
