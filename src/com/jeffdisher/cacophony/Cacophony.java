package com.jeffdisher.cacophony;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;


// XML Generation:  https://edwin.baculsoft.com/2019/11/java-generate-xml-from-xsd-using-xjc/
public class Cacophony {
	/**
	 * Argument modes:
	 * "--createNewChannel" Used to create a new empty channel for this local key.
	 * "--updateDescription" Changes the description of the local channel.
	 * "--readDescription" Reads the description of the named public key (local channel if not provided), writing it to stdout.
	 * "--addRecommendation" Adds a new channel key to the recommended list from the local channel.
	 * "--removeRecommendation" Removes a channel key from the recommended list from the local channel.
	 * "--listRecommendations" Lists the recommended channel keys from the local channel to stdout.
	 * "--publishToThisChannel" Adds and publishes a new entry to the local channel.
	 * "--listChannel" Lists all the entries published to the named public key (local channel if not provided) to stdout.
	 * "--removeFromThisChannel" Removes a given entry from the local channel.
	 * 
	 * "--setGlobalPrefs":
	 *  "--cacheWidth" Sets the maximum video width to cache (smallest larger size will be chosen if a smaller one isn't available).
	 *  "--cacheHeight" Sets the maximum video width to cache (smallest larger size will be chosen if a smaller one isn't available).
	 *  "--cacheTotalBytes" Sets the maximum number of bytes to allow for automatic caching storage (only counts leaf data elements, not intermediary meta-data).
	 * "--updateNextFollowing" Does the polling cycle on the next channel being followed and advances polling state to the next.
	 * "--startFollowing" Adds the given channel ID to the following set.
	 * "--stopFollowing" Removes the given channel ID from the following set.
	 * "--readRemoteRecommendations" Reads the recommended channel IDs of the given ID and prints them to stdout.
	 * "--readRemoteChannel" Reads the contents of a given channel ID and prints it to stdout.
	 * "--favouriteElement" Adds the given element to the favourites pool (removing it from other pools if already present).
	 * "--unfavouriteElement" Removes the given element from the favourites pool.
	 * "--explicitLoadElement" Adds the given element to the explicit pool (ignoring this already present in another pool).
	 * "--readToLocalFile" Reads the default data from the given element into a file on the local filesystem (implicitly adding the element to the explicit pool)
	 * 
	 * @param args
	 * @throws IOException
	 * @throws JAXBException
	 * @throws SAXException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length > 0)
		{
			ICommand command = CommandParser.parseArgs(args, 1, System.err);
			if (null != command)
			{
				File directory = new File(args[0]);
				if (!directory.exists() && !directory.mkdirs())
				{
					System.err.println("Failed to create directory at " + directory);
					System.exit(2);
				}
				Executor executor = new Executor();
				LocalActions local = new LocalActions(directory);
				command.scheduleActions(executor, local);
				executor.waitForCompletion();
				// Write-back updates.
				local.storeGlobalPinCache();
				local.storeFollowIndex();
			}
			else
			{
				errorStart();
			}
		}
		else
		{
			errorStart();
		}
		/*
		IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		Map<?, ?> map = ipfs.commands();
		for (Map.Entry<?, ?> elt : map.entrySet()) {
			System.out.println("KEY \"" + elt.getKey() + "\" -> VALUE \"" + elt.getValue() + "\"");
		}
		*/
		/*
		byte[] bytes = "Testing data".getBytes();
		NamedStreamable.ByteArrayWrapper wrapper = new NamedStreamable.ByteArrayWrapper(bytes);
		MerkleNode result = ipfs.add(wrapper).get(0);
		
		byte[] hash = result.hash.toBytes();
		String hexString = String.format("%0" + (hash.length * 2) + "x", new BigInteger(1, hash));
		System.out.println("Uploaded: " + result.toString() + " - " + hexString);
		*/
		/*
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		XMLEncoder encoder = new XMLEncoder(stream);
		encoder.writeObject("Testing");
		encoder.writeObject(555L);
		encoder.close();
		*/
	}

	private static void errorStart()
	{
		System.err.println("Usage:  Cacophony /ip4/127.0.0.1/tcp/5001 <command>");
		CommandParser.printUsage(System.err);
		System.exit(1);
	}
}
