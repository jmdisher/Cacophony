package com.jeffdisher.cacophony;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.commands.AddFavouriteCommand;
import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.CleanCacheCommand;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.DeleteChannelCommand;
import com.jeffdisher.cacophony.commands.EditPostCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.GetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.GetPublicKeyCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.ListChannelEntriesCommand;
import com.jeffdisher.cacophony.commands.ListChannelsCommand;
import com.jeffdisher.cacophony.commands.ListFavouritesCommand;
import com.jeffdisher.cacophony.commands.ListFolloweesCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.QuickstartCommand;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.RebroadcastCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.RefreshNextFolloweeCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.RemoveFavouriteCommand;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.RepublishCommand;
import com.jeffdisher.cacophony.commands.RunCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.ShowPostCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class CommandParser
{
	public static final String DEFAULT_KEY_NAME = "Cacophony";

	private static record PreParse(ParameterType type, String pre) {
		<T> T parse(Class<T> clazz) throws UsageException
		{
			return this.type.parse(clazz, this.pre);
		}
	};
	@java.lang.FunctionalInterface
	private static interface IParseFunction
	{
		public ICommand<?> apply(PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) throws UsageException;
	}
	private static enum ArgPattern
	{
		// Sub-element side args (these are always assumed to have one or more potential instances).
		ELEMENT(false, "--element"
				, new ArgParameter[] { new ArgParameter("--mime", ParameterType.MIME, "MIME type of the attachment")
					, new ArgParameter("--file", ParameterType.FILE, "The path to the file to upload")
				}
				, new ArgParameter[] { new ArgParameter("--height", ParameterType.INT
						, "The height, pixels (for videos only)"
					)
					, new ArgParameter("--width", ParameterType.INT, "The width, pixels (for videos only)")
					, new ArgParameter("--special", ParameterType.SPECIAL
						, "Set to \"image\" if this should be the thumbnail attachment"
					)
				}
				, "Attaches a file to the new post being made."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String mime = required[0].parse(String.class);
			File filePath = required[1].parse(File.class);
			if (!filePath.isFile())
			{
				throw new UsageException("File not found: " + filePath);
			}
			int height = _optionalInt(optional[0], 0);
			int width = _optionalInt(optional[1], 0);
			boolean isSpecialImage = false;
			if (null != optional[2])
			{
				isSpecialImage = optional[2].parse(Boolean.class);
			}
			return new ElementSubCommand(mime, filePath, height, width, isSpecialImage);
		}),
		
		// Methods to manage this channel.
		CREATE_NEW_CHANNEL(true, "--createNewChannel"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Creates a new home user channel.  Required before running most other operations."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new CreateChannelCommand();
		}),
		UPDATE_DESCRIPTION(true, "--updateDescription"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--name", ParameterType.STRING, "The channel name")
					, new ArgParameter("--description", ParameterType.STRING, "The channel description")
					, new ArgParameter("--pictureFile", ParameterType.FILE
						, "The path to the image to use as the user pic"
					)
					, new ArgParameter("--email", ParameterType.STRING, "Email address, if you want to share that")
					, new ArgParameter("--website", ParameterType.URL, "Website, if you have one you want to share")
				}
				, "Updates the user description of the home user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String name = _optionalString(optional[0]);
			String description = _optionalString(optional[1]);
			File picturePath = _optionalFile(optional[2]);
			String email = _optionalString(optional[3]);
			String website = _optionalString(optional[4]);
			FileInputStream pictureStream = null;
			if (null != picturePath)
			{
				try
				{
					pictureStream = new FileInputStream(picturePath);
				}
				catch (FileNotFoundException e)
				{
					throw new UsageException("File not found: " + picturePath);
				}
			}
			return new UpdateDescriptionCommand(name, description, pictureStream, email, website);
		}),
		READ_DESCRIPTION(true, "--readDescription"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, "Reads and displays the description of another user on the network, given their public key."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey channelPublicKey = _optionalKey(optional[0]);
			return new ReadDescriptionCommand(channelPublicKey);
		}),
		ADD_RECOMMENDATION(true, "--addRecommendation"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Adds the given public key to the list of users recommended by the home user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey channelPublicKey = required[0].parse(IpfsKey.class);
			return new AddRecommendationCommand(channelPublicKey);
		}),
		REMOVE_RECOMMENDATION(true, "--removeRecommendation"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Removes the given public key from the list of users recommended by the home user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey channelPublicKey = required[0].parse(IpfsKey.class);
			return new RemoveRecommendationCommand(channelPublicKey);
		}),
		LIST_RECOMMENDATIONS(true, "--listRecommendations"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, "Lists the public keys of the users recommended by the given user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey publicKey = _optionalKey(optional[0]);
			return new ListRecommendationsCommand(publicKey);
		}),
		PUBLISH_TO_THIS_CHANNEL(true, "--publishToThisChannel"
				, new ArgParameter[] { new ArgParameter("--name", ParameterType.STRING, "The name/title of the post")
					, new ArgParameter("--description", ParameterType.STRING, "The description of the post")
				}
				, new ArgParameter[] { new ArgParameter("--discussionUrl", ParameterType.URL
					, "A URL to a discussion or context, if there is one"
				) }
				, "Makes a new post to the home user's channel."
				, ELEMENT, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String name = required[0].parse(String.class);
			String description = required[1].parse(String.class);
			String discussionUrl = _optionalString(optional[0]);
			ElementSubCommand elements[] = new ElementSubCommand[subElements.size()];
			elements = subElements.toArray(elements);
			return new PublishCommand(name, description, discussionUrl, elements);
		}),
		LIST_THIS_CHANNEL(true, "--listChannel"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user (lists local channel if not provided)"
				) }
				, "Lists basic information about the posts made to the channel of the given user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey channelPublicKey = _optionalKey(optional[0]);
			return new ListChannelEntriesCommand(channelPublicKey);
		}),
		REMOVE_FROM_THIS_CHANNEL(true, "--removeFromThisChannel"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord element to delete"
				) }
				, new ArgParameter[0]
				, "Removes the given post (identified by CID) from the home user's channel."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			return new RemoveEntryFromThisChannelCommand(elementCid);
		}),
		START_FOLLOWING(true, "--startFollowing"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Starts following the user with the given public key.  Note that their posts will not all be"
						+ " synchronized until it is next refreshed."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey publicKey = required[0].parse(IpfsKey.class);
			return new StartFollowingCommand(publicKey);
		}),
		STOP_FOLLOWING(true, "--stopFollowing"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Stops following the user with the given public key.  This will drop all of their posts from your"
						+ " local cache, allowing IPFS to reclaim the storage (may require --cleanCache)."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey publicKey = required[0].parse(IpfsKey.class);
			return new StopFollowingCommand(publicKey);
		}),
		LIST_FOLLOWEES(true, "--listFollowees"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Lists the currently followed users."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new ListFolloweesCommand();
		}),
		LIST_CACHED_ELEMENTS_FOR_FOLLOWEE(true, "--listFollowee"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Lists all the elements locally cached for a given followed user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey followeeKey = required[0].parse(IpfsKey.class);
			return new ListCachedElementsForFolloweeCommand(followeeKey);
		}),
		REFRESH_FOLLOWEE(true, "--refreshFollowee"
				, new ArgParameter[] { new ArgParameter("--publicKey", ParameterType.PUBLIC_KEY
					, "The IPFS public key of the user"
				) }
				, new ArgParameter[0]
				, "Refreshes the cache for the given followee.  This will consider fetching new posts they have made."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsKey followeeKey = required[0].parse(IpfsKey.class);
			return new RefreshFolloweeCommand(followeeKey);
		}),
		REFRESH_NEXT_FOLLOWEE(true, "--refreshNextFollowee"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Refreshes the next followee due to a refresh.  This uses a round-robin strategy so the least recent"
						+ " refresh will be chosen."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new RefreshNextFolloweeCommand();
		}),
		REPUBLISH(true, "--republish"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Republishes the home user's channel to the IPFS network.  This must be done at least every 24 hours"
						+ " to remain discoverable (adding/removing posts, updating description, and adding/removing"
						+ " recommendations implicitly does this republish)."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new RepublishCommand();
		}),
		REBROADCAST(true, "--rebroadcast"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord element to rebroadcast to your stream"
				) }
				, new ArgParameter[0]
				, "Reposts an existing post, from another user, to the home user's stream.  This will cache all of the"
						+ " files attached to the post."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			return new RebroadcastCommand(elementCid);
		}),
		EDIT_POST(true, "--editPost"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord to modify"
				) }
				, new ArgParameter[] { new ArgParameter("--name", ParameterType.STRING
						, "The new name (unchanged if ommitted)"
					)
					, new ArgParameter("--description", ParameterType.STRING
						, "The new description (unchanged if ommitted)"
					)
					, new ArgParameter("--discussionUrl", ParameterType.URL
						, "The new discussionURL (unchanged if ommitted, deleted if empty)"
					)
				}
				, "Edits an existing post from the home user's stream."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			String name = _optionalString(optional[0]);
			String description = _optionalString(optional[1]);
			String discussionUrl = _optionalString(optional[2]);
			return new EditPostCommand(elementCid, name, description, discussionUrl);
		}),
		SHOW_POST(true, "--showPost"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord to show"
				) }
				, new ArgParameter[0]
				, "Shows an existing post from the network."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			return new ShowPostCommand(elementCid);
		}),
		ADD_FAVOURITE(true, "--addFavourite"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord element to favourite"
				) }
				, new ArgParameter[0]
				, "Marks the given StreamRecord as a favourite, keeping it locally cached forever."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			return new AddFavouriteCommand(elementCid);
		}),
		REMOVE_FAVOURITE(true, "--removeFavourite"
				, new ArgParameter[] { new ArgParameter("--elementCid", ParameterType.CID
					, "The CID of the StreamRecord element to unfavourite"
				) }
				, new ArgParameter[0]
				, "Releases a previously favourite StreamRecord from the local cache."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			IpfsFile elementCid = required[0].parse(IpfsFile.class);
			return new RemoveFavouriteCommand(elementCid);
		}),
		LIST_FAVOURITES(true, "--listFavourites"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "List the StreamRecords previously added as favourites."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new ListFavouritesCommand();
		}),
		
		// Methods to manage local state.
		SET_GLOBAL_PREFS(true, "--setGlobalPrefs"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--edgeMaxPixels", ParameterType.INT
						, "Will only fetch the largest video for each post which fits into a box with this edge"
							+ " size, in pixels"
					)
					, new ArgParameter("--followCacheTargetBytes", ParameterType.LONG_BYTES
						, "The target size of the follow cache (that is, how much space is used for caching videos"
							+ " and images of the users you follow) in bytes (accepts k, m, g suffixes)"
					)
					, new ArgParameter("--republishIntervalMillis", ParameterType.LONG_MILLIS, "How often, in"
						+ " milliseconds, to republish your channel to IPNS (should be less than 24 hours)"
					)
					, new ArgParameter("--followeeRefreshMillis", ParameterType.LONG_MILLIS, "How often, in"
						+ " milliseconds, to refresh the post lists of those you follow"
					)
					, new ArgParameter("--explicitCacheTargetBytes", ParameterType.LONG_BYTES
						, "The target size of the explicit cache (that is, how much space is used for caching videos"
							+ " and images for miscellaneous look-ups) in bytes (accepts k, m, g suffixes)"
					)
				}
				, "Updates preferences related to the Cacophony installation."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			int edgeMaxPixels = _optionalInt(optional[0], 0);
			long followCacheTargetBytes = _optionalLong(optional[1], 0L);
			long republishIntervalMillis = _optionalLong(optional[2], 0L);
			long followeeRefreshMillis = _optionalLong(optional[3], 0L);
			long explicitCacheTargetBytes = _optionalLong(optional[4], 0L);
			return new SetGlobalPrefsCommand(edgeMaxPixels
					, followCacheTargetBytes
					, republishIntervalMillis
					, followeeRefreshMillis
					, explicitCacheTargetBytes
			);
		}),
		CANONICALIZE_KEY(true, "--canonicalizeKey"
				, new ArgParameter[] { new ArgParameter("--key", ParameterType.PUBLIC_KEY
					, "The IPFS key to canonicalize in Base58"
				) }
				, new ArgParameter[0]
				, "Converts a given IPFS public key from whatever encoding it is using to the \"canonical\" Base58"
						+ " representation used by Cacophony."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			// This is just a utility function to make detecting keys in integration scripts more reliable.
			IpfsKey channelPublicKey = required[0].parse(IpfsKey.class);
			return new ICommand<None>()
			{
				@Override
				public None runInContext(Context context)
				{
					System.out.println(channelPublicKey.toPublicKey());
					return None.NONE;
				}
			};
		}),
		GET_PUBLIC_KEY(true, "--getPublicKey"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Outputs the public key of the home user."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new GetPublicKeyCommand();
		}),
		GET_GLOBAL_PREFS(true, "--getGlobalPrefs"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Outputs the current preferences of the Cacophony installation."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new GetGlobalPrefsCommand();
		}),
		RUN(true, "--run"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--overrideCommand", ParameterType.STRING
						, "The video preprocessing command to use for new drafts (must accept video as stdin and"
							+ " output video on stdout)"
					)
					, new ArgParameter("--commandSelection", ParameterType.STRING
						, "If set to DANGEROUS, will allow you to change the video preprocessing command from the"
							+ " web UI (default is STRICT)"
					)
					, new ArgParameter("--port", ParameterType.INT
						, "The port to use for the interactive web UI (default is 8000)"
					)
				}
				, "Starts the long-running interactive web server."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String overrideCommand = _optionalString(optional[0]);
			String commandSelection = _optionalString(optional[1]);
			// The default port is 8000.
			int port = _optionalInt(optional[2], 8000);
			RunCommand.CommandSelectionMode mode = RunCommand.CommandSelectionMode.STRICT;
			if (null != commandSelection)
			{
				try
				{
					mode = RunCommand.CommandSelectionMode.valueOf(commandSelection.toUpperCase());
					Assert.assertTrue(null != mode);
				}
				catch (IllegalArgumentException e)
				{
					// Happens if this isn't a valid name.
					throw new UsageException("--commandSelecton must be STRICT (default) or DANGEROUS");
				}
			}
			return new RunCommand(overrideCommand, mode, port);
		}),
		CLEAN_CACHE(true, "--cleanCache"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Removes entries from the cache if it is overflowing (for example, after a resize in prefs) and"
						+ " requests that IPFS run its storage garbage collector to reclaim space."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new CleanCacheCommand();
		}),
		LIST_CHANNELS(true, "--listChannels"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Lists all the local home channels."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new ListChannelsCommand();
		}),
		DELETE_CHANNEL(true, "--deleteChannel"
				, new ArgParameter[0]
				, new ArgParameter[0]
				, "Deletes the channel currently selected by key name, unpinning all of its data from the local node."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			return new DeleteChannelCommand();
		}),
		
		// High-level commands which aren't actually real commands, but are just convenient invocation idioms built on top of actual commands.
		PUBLISH_SINGLE_VIDEO(true, "--publishSingleVideo"
				, new ArgParameter[] { new ArgParameter("--name", ParameterType.STRING, "The name/title of the post")
					, new ArgParameter("--description", ParameterType.STRING, "The description of the post")
					, new ArgParameter("--thumbnailJpeg", ParameterType.FILE
						, "The path to the file to use as the post thumbnail"
					)
					, new ArgParameter("--videoFile", ParameterType.FILE
						, "The path to the file to use as the video attachment"
					)
					, new ArgParameter("--videoMime", ParameterType.MIME, "The MIME type of the video")
					, new ArgParameter("--videoHeight", ParameterType.INT, "The height of the video, in pixels")
					, new ArgParameter("--videoWidth", ParameterType.INT, "The width of the video, in pixels")
				}
				, new ArgParameter[] { new ArgParameter("--discussionUrl", ParameterType.URL
					, "A URL to a discussion or context, if there is one"
				) }
				, "Publishes a new post to the home user's stream including a single video and a thumbnail.  This is"
						+ " just a wrapper over --publishToThisChannel to cover a very common case."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String name = required[0].parse(String.class);
			String description = required[1].parse(String.class);
			File thumbnailJpeg = required[2].parse(File.class);
			File videoFile = required[3].parse(File.class);
			String videoMime = required[4].parse(String.class);
			int videoHeight = _optionalInt(required[5], -1);
			int videoWidth = _optionalInt(required[6], -1);
			String discussionUrl = _optionalString(optional[0]);
			ElementSubCommand elements[] = new ElementSubCommand[] {
					new ElementSubCommand("image/jpeg", thumbnailJpeg, 0, 0, true),
					new ElementSubCommand(videoMime, videoFile, videoHeight, videoWidth, false),
			};
			return new PublishCommand(name, description, discussionUrl, elements);
		}),
		QUICKSTART(true, "--quickstart"
				, new ArgParameter[0]
				, new ArgParameter[] { new ArgParameter("--name", ParameterType.STRING
					, "The name to put in the channel description"
				) }
				, "Creates a new home user channel with the given name as its description and sets it to follow the"
						+ " Cacophony demo channel.  This is just a combination of --createNewChannel,"
						+ " --updateDescription, and --startFollowing."
				, null, (PreParse[] required, PreParse[] optional, List<ICommand<?>> subElements) ->
		{
			String name = _optionalString(optional[0]);
			return new QuickstartCommand(name);
		}),
		;
		
		private static String _optionalString(PreParse num) throws UsageException
		{
			return (null != num)
					? num.parse(String.class)
					: null
			;
		}
		
		private static File _optionalFile(PreParse num) throws UsageException
		{
			return (null != num)
					? num.parse(File.class)
					: null
			;
		}
		
		private static int _optionalInt(PreParse num, int ifNull) throws UsageException
		{
			return (null != num)
					? num.parse(Integer.class)
					: ifNull
			;
		}
		
		private static long _optionalLong(PreParse num, long ifNull) throws UsageException
		{
			return (null != num)
					? num.parse(Long.class)
					: ifNull
			;
		}
		
		private static IpfsKey _optionalKey(PreParse num) throws UsageException
		{
			return (null != num)
					? num.parse(IpfsKey.class)
					: null
			;
		}
		
		
		private final boolean _topLevel;
		private final String _name;
		private final ArgParameter _params[];
		private final ArgParameter _optionalParams[];
		private final String _description;
		private final ArgPattern _subType;
		private final IParseFunction _factory;
		
		private ArgPattern(boolean topLevel, String name
				, ArgParameter params[]
				, ArgParameter optionalParams[]
				, String description
				, ArgPattern subType, IParseFunction factory)
		{
			_topLevel = topLevel;
			_name = name;
			_params = params;
			_optionalParams = optionalParams;
			_description = description;
			_subType = subType;
			_factory = factory;
		}
		
		private boolean isValid(String arg)
		{
			return _topLevel && (arg.equals(_name));
		}
		
		private ICommand<?> parse(String[] args, int[] startIndex) throws UsageException
		{
			// We need to convert the entire input to a command, update the startIndex in/out parameter once we have processed all of our known options.
			// We can return null if we fail at any point, and the top-level will fail.
			int scanIndex = startIndex[0];
			int consumedIndex = scanIndex + 1;
			Assert.assertTrue(args[scanIndex].equals(_name));
			scanIndex += 1;
			
			PreParse[] required = new PreParse[_params.length];
			PreParse[] optional = new PreParse[_optionalParams.length];
			List<ICommand<?>> subElements = new ArrayList<>();
			int requiredCount = 0;
			
			boolean keepRunning = true;
			boolean fail = false;
			while (!fail && keepRunning && ((scanIndex + 1) < args.length))
			{
				// We will set keepRunning to true when we match something in the argument stream.
				keepRunning = false;
				String next = args[scanIndex];
				String value = args[scanIndex + 1];
				// We check this against each required or optional parameter.
				for (int i = 0; i < _params.length; ++i)
				{
					String check = _params[i].name;
					if (next.equals(check))
					{
						if (null != required[i])
						{
							requiredCount -= 1;
						}
						required[i] = new PreParse(_params[i].type, value);
						requiredCount += 1;
						keepRunning = true;
						consumedIndex += 2;
						break;
					}
				}
				if (!keepRunning)
				{
					for (int i = 0; i < _optionalParams.length; ++i)
					{
						String check = _optionalParams[i].name;
						if (next.equals(check))
						{
							optional[i] = new PreParse(_optionalParams[i].type, value);
							keepRunning = true;
							consumedIndex += 2;
							break;
						}
					}
				}
				// See if this is a sub-element type.
				if (!keepRunning && (null != _subType) && _subType._name.equals(next))
				{
					int[] sub = new int[] {scanIndex};
					ICommand<?> subCommand = _subType.parse(args, sub);
					if (null != subCommand)
					{
						scanIndex = sub[0];
						consumedIndex = scanIndex;
						subElements.add(subCommand);
						keepRunning = true;
					}
					else
					{
						// We require that this can be parsed
						fail = true;
					}
				}
				else
				{
					scanIndex += 2;
				}
			}
			startIndex[0] = consumedIndex;
			
			return (!fail && (requiredCount == required.length))
					? _factory.apply(required, optional, subElements)
					: null
			;
		}
		
		private void printUsage(PrintStream stream)
		{
			stream.print(_name + " ");
			for(ArgParameter param : _params)
			{
				stream.print(param.shortDescription() + " ");
			}
			for(ArgParameter param : _optionalParams)
			{
				stream.print("[" + param.shortDescription() + "] ");
			}
			if (null != _subType)
			{
				stream.print("(");
				_subType.printUsage(stream);
				stream.print(")* ");
			}
		}
	}

	public static ICommand<?> parseArgs(String[] args, PrintStream errorStream) throws UsageException
	{
		// We assume that we only get this far is we have args.
		Assert.assertTrue(args.length > 0);
		
		ICommand<?> matched = null;
		for (ArgPattern pattern : ArgPattern.values())
		{
			if (pattern.isValid(args[0]))
			{
				// We use index as in/out parameter here, hence the array.
				int[] index = {0};
				matched = pattern.parse(args, index);
				// We are at the top-level, here, so we need to make sure that the input was completely consumed.
				if (args.length != index[0])
				{
					// There seems to be something we couldn't parse at the end of the line, meaning this is an error.
					errorStream.println("Extra data at end of command line.");
					// (in this case, we may still parse something but can't determine that isn't enough until we return
					// to here).
					matched = null;
					break;
				}
				else if (null != matched)
				{
					if (index[0] < args.length)
					{
						String unhandledArgs = "Unhandled args: ";
						for (int i = index[0]; i < args.length; ++i)
						{
							unhandledArgs += args[i] + ", ";
						}
						errorStream.println(unhandledArgs);
						matched = null;
					}
					break;
				}
				else
				{
					// This is a valid pattern, but didn't parse, meaning sub-args were missing.
					errorStream.println("Missing command sub-arguments.");
					break;
				}
			}
		}
		return matched;
	}

	public static void printUsage(PrintStream stream)
	{
		stream.println("Commands:");
		for (ArgPattern pattern : ArgPattern.values())
		{
			if (pattern._topLevel)
			{
				stream.print("\t");
				pattern.printUsage(stream);
				stream.println();
			}
		}
	}

	public static void printHelp(PrintStream stream)
	{
		// We want to walk all the arguments and generate a detailed description for each.
		for (ArgPattern pattern : ArgPattern.values())
		{
			// We will assume a 2-level system:  We will walk top-level and check for direct sub-commands.
			if (pattern._topLevel)
			{
				stream.println();
				_describePattern(stream, "", pattern);
				if (null != pattern._subType)
				{
					stream.println("\tThis sub-pattern can be specified 0 or any number of times:");
					_describePattern(stream, "\t", pattern._subType);
				}
			}
		}
	}


	private static void _describePattern(PrintStream stream, String prefix, ArgPattern pattern)
	{
		stream.println(prefix + pattern._name);
		stream.println(prefix + "\tDescription: " + pattern._description);
		stream.println(prefix + "\tRequired parameters:");
		_describeParameterList(stream, prefix + "\t\t", pattern._params);
		stream.println(prefix + "\tOptional parameters:");
		_describeParameterList(stream, prefix + "\t\t", pattern._optionalParams);
	}

	private static void _describeParameterList(PrintStream stream, String prefix, ArgParameter[] list)
	{
		if (0 == list.length)
		{
			stream.println(prefix + "(none)");
		}
		else
		{
			for (int i = 0; i < list.length; ++i)
			{
				stream.println(prefix + list[i].longDescription());
			}
		}
	}
}
