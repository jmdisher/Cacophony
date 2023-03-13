package com.jeffdisher.cacophony;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.CleanCacheCommand;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.GetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.GetPublicKeyCommand;
import com.jeffdisher.cacophony.commands.HtmlOutputCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ListCachedElementsForFolloweeCommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.ListChannelEntriesCommand;
import com.jeffdisher.cacophony.commands.ListFolloweesCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.RebroadcastCommand;
import com.jeffdisher.cacophony.commands.RefreshFolloweeCommand;
import com.jeffdisher.cacophony.commands.RefreshNextFolloweeCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.RepublishCommand;
import com.jeffdisher.cacophony.commands.RunCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class CommandParser
{
	public static final String DEFAULT_KEY_NAME = "Cacophony";

	@java.lang.FunctionalInterface
	private static interface IParseFunction
	{
		public ICommand apply(String[] required, String[] optional, List<ICommand> subElements) throws UsageException;
	}
	private static enum ArgPattern
	{
		// Sub-element side args (these are always assumed to have one or more potential instances).
		ELEMENT(false, "--element", new String[] {"--mime", "--file"}, new String[] {"--height", "--width", "--special"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String mime = required[0];
			File filePath = new File(required[1]);
			if (!filePath.isFile())
			{
				throw new UsageException("File not found: " + filePath);
			}
			int height = _parseAsInt(optional[0], 0);
			int width = _parseAsInt(optional[1], 0);
			boolean isSpecialImage = false;
			if (null != optional[2])
			{
				if (ElementSpecialType.IMAGE.value().equals(optional[2]))
				{
					isSpecialImage = true;
				}
				else
				{
					throw new UsageException("Unknown special file type: \"" + optional[2] + "\"");
				}
			}
			return new ElementSubCommand(mime, filePath, height, width, isSpecialImage);
		}),
		
		// Methods to manage this channel.
		CREATE_NEW_CHANNEL(true, "--createNewChannel", new String[] {"--ipfs"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String ipfs = required[0];
			String keyName = System.getenv(EnvVars.ENV_VAR_CACOPHONY_KEY_NAME);
			if (null == keyName)
			{
				keyName = DEFAULT_KEY_NAME;
			}
			return new CreateChannelCommand(ipfs, keyName);
		}),
		UPDATE_DESCRIPTION(true, "--updateDescription", new String[0], new String[] {"--name", "--description", "--pictureFile", "--email", "--website"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String name = optional[0];
			String description = optional[1];
			File picturePath = (null != optional[2])
					? new File(optional[2])
					: null
			;
			String email = optional[3];
			String website = optional[4];
			if ((null == name) && (null == description) && (null == picturePath) && (null == email) && (null == website))
			{
				throw new UsageException("--updateDescription requires at least a single subargument");
			}
			if ((null != picturePath) && !picturePath.isFile())
			{
				throw new UsageException("File not found: " + picturePath);
			}
			return new UpdateDescriptionCommand(name, description, picturePath, email, website);
		}),
		READ_DESCRIPTION(true, "--readDescription", new String[0], new String[] {"--publicKey"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String rawKey = optional[0];
			IpfsKey channelPublicKey = (null != rawKey)
					? _parseAsKey(rawKey)
					: null
			;
			return new ReadDescriptionCommand(channelPublicKey);
		}),
		ADD_RECOMMENDATION(true, "--addRecommendation", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey channelPublicKey = _parseAsKey(required[0]);
			return new AddRecommendationCommand(channelPublicKey);
		}),
		REMOVE_RECOMMENDATION(true, "--removeRecommendation", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey channelPublicKey = _parseAsKey(required[0]);
			return new RemoveRecommendationCommand(channelPublicKey);
		}),
		LIST_RECOMMENDATIONS(true, "--listRecommendations", new String[0], new String[] {"--publicKey"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey publicKey = (null != optional[0])
					? _parseAsKey(optional[0])
					: null;
			;
			return new ListRecommendationsCommand(publicKey);
		}),
		PUBLISH_TO_THIS_CHANNEL(true, "--publishToThisChannel", new String[] {"--name", "--description"}, new String[] {"--discussionUrl"}, ELEMENT, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String name = required[0];
			String description = required[1];
			String discussionUrl = optional[0];
			ElementSubCommand elements[] = new ElementSubCommand[subElements.size()];
			elements = subElements.toArray(elements);
			return new PublishCommand(name, description, discussionUrl, elements);
		}),
		LIST_THIS_CHANNEL(true, "--listChannel", new String[0], new String[] {"--publicKey"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String rawKey = optional[0];
			IpfsKey channelPublicKey = (null != rawKey)
					? _parseAsKey(rawKey)
					: null
			;
			return new ListChannelEntriesCommand(channelPublicKey);
		}),
		REMOVE_FROM_THIS_CHANNEL(true, "--removeFromThisChannel", new String[] {"--elementCid"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsFile elementCid = _parseAsFile(required[0]);
			return new RemoveEntryFromThisChannelCommand(elementCid);
		}),
		START_FOLLOWING(true, "--startFollowing", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey publicKey = _parseAsKey(required[0]);
			return new StartFollowingCommand(publicKey);
		}),
		STOP_FOLLOWING(true, "--stopFollowing", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey publicKey = _parseAsKey(required[0]);
			return new StopFollowingCommand(publicKey);
		}),
		LIST_FOLLOWEES(true, "--listFollowees", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new ListFolloweesCommand();
		}),
		LIST_CACHED_ELEMENTS_FOR_FOLLOWEE(true, "--listFollowee", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey followeeKey = _parseAsKey(required[0]);
			return new ListCachedElementsForFolloweeCommand(followeeKey);
		}),
		REFRESH_FOLLOWEE(true, "--refreshFollowee", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey followeeKey = _parseAsKey(required[0]);
			return new RefreshFolloweeCommand(followeeKey);
		}),
		REFRESH_NEXT_FOLLOWEE(true, "--refreshNextFollowee", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new RefreshNextFolloweeCommand();
		}),
		REPUBLISH(true, "--republish", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new RepublishCommand();
		}),
		HTML_OUTPUT(true, "--htmlOutput", new String[] {"--directory"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			File directory = new File(required[0]);
			return new HtmlOutputCommand(directory);
		}),
		REBROADCAST(true, "--rebroadcast", new String[] {"--elementCid"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsFile elementCid = _parseAsFile(required[0]);
			return new RebroadcastCommand(elementCid);
		}),
		
		// Methods to manage local state.
		SET_GLOBAL_PREFS(true, "--setGlobalPrefs", new String[0], new String[]
				{ "--edgeMaxPixels"
				, "--followCacheTargetBytes"
				, "--republishIntervalMillis"
				, "--followeeRefreshMillis"
				}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			int edgeMaxPixels = _parseAsInt(optional[0], 0);
			long followCacheTargetBytes = _parseAsStorageNumber(optional[1], 0L);
			long republishIntervalMillis = _parseAsLong(optional[2], 0L);
			long followeeRefreshMillis = _parseAsLong(optional[3], 0L);
			return new SetGlobalPrefsCommand(edgeMaxPixels
					, followCacheTargetBytes
					, republishIntervalMillis
					, followeeRefreshMillis
			);
		}),
		CANONICALIZE_KEY(true, "--canonicalizeKey", new String[] {"--key"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			// This is just a utility function to make detecting keys in integration scripts more reliable.
			IpfsKey channelPublicKey = _parseAsKey(required[0]);
			return (IEnvironment environment) -> {
				System.out.println(channelPublicKey.toPublicKey());
			};
		}),
		GET_PUBLIC_KEY(true, "--getPublicKey", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new GetPublicKeyCommand();
		}),
		GET_GLOBAL_PREFS(true, "--getGlobalPrefs", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new GetGlobalPrefsCommand();
		}),
		RUN(true, "--run", new String[0], new String[] {"--overrideCommand", "--commandSelection", "--port"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String overrideCommand = optional[0];
			String commandSelection = optional[1];
			// The default port is 8000.
			int port = _parseAsInt(optional[2], 8000);
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
		CLEAN_CACHE(true, "--cleanCache", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new CleanCacheCommand();
		}),
		
		// High-level commands which aren't actually real commands, but are just convenient invocation idioms built on top of actual commands.
		PUBLISH_SINGLE_VIDEO(true, "--publishSingleVideo"
				, new String[] {"--name", "--description", "--thumbnailJpeg", "--videoFile", "--videoMime", "--videoHeight", "--videoWidth"}
				, new String[] {"--discussionUrl"}
				, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String name = required[0];
			String description = required[1];
			File thumbnailJpeg = new File(required[2]);
			if (!thumbnailJpeg.isFile())
			{
				throw new UsageException("File not found: " + thumbnailJpeg);
			}
			File videoFile = new File(required[3]);
			if (!videoFile.isFile())
			{
				throw new UsageException("File not found: " + videoFile);
			}
			String videoMime = required[4];
			int videoHeight = _parseAsInt(required[5], -1);
			int videoWidth = _parseAsInt(required[6], -1);
			String discussionUrl = optional[0];
			ElementSubCommand elements[] = new ElementSubCommand[] {
					new ElementSubCommand("image/jpeg", thumbnailJpeg, 0, 0, true),
					new ElementSubCommand(videoMime, videoFile, videoHeight, videoWidth, false),
			};
			return new PublishCommand(name, description, discussionUrl, elements);
		}),
		;
		
		private static int _parseAsInt(String num, int ifNull) throws UsageException
		{
			try
			{
				int result = (null != num)
						? Integer.parseInt(num)
						: ifNull
				;
				if (result < 0)
				{
					throw new UsageException("Value cannot be negative: \"" + result + "\"");
				}
				return result;
			}
			catch (NumberFormatException e)
			{
				throw new UsageException("Not a number: \"" + num + "\"");
			}
		}
		
		private static long _parseAsLong(String num, long ifNull) throws UsageException
		{
			try
			{
				long result = (null != num)
						? Long.parseLong(num)
						: ifNull
				;
				if (result < 0L)
				{
					throw new UsageException("Value cannot be negative: \"" + result + "\"");
				}
				return result;
			}
			catch (NumberFormatException e)
			{
				throw new UsageException("Not a number: \"" + num + "\"");
			}
		}
		
		/**
		 * Parses the given number as a long, but also allows for suffix of "k"/"K" (thousands), "m"/"M" (millions),
		 * "g"/"G" (billions).
		 * 
		 * @param num The string to parse.
		 * @param ifNull The number to return if the num is null.
		 * @return The parsed number.
		 * @throws UsageException If the number was not null but could not be parsed.
		 */
		private static long _parseAsStorageNumber(String num, long ifNull) throws UsageException
		{
			long result;
			if (null != num)
			{
				// First, see if there is trailing magnitude suffix.
				long magnitude = 1L;
				char lastChar = num.charAt(num.length() - 1);
				switch (lastChar)
				{
					case 'k':
					case 'K':
						magnitude = 1_000L;
						break;
					case 'm':
					case 'M':
						magnitude = 1_000_000L;
						break;
					case 'g':
					case 'G':
						magnitude = 1_000_000_000L;
						break;
				}
				String toParse = (1L == magnitude)
						? num
						: num.substring(0, num.length() - 1)
				;
				try
				{
					long value = Long.parseLong(toParse);
					result = value * magnitude;
					if (result < 0L)
					{
						throw new UsageException("Value cannot be negative: \"" + result + "\"");
					}
				}
				catch (NumberFormatException e)
				{
					throw new UsageException("Not a number: \"" + num + "\"");
				}
			}
			else
			{
				result = ifNull;
			}
			return result;
		}
		
		private static IpfsKey _parseAsKey(String rawKey) throws UsageException
		{
			IpfsKey key = IpfsKey.fromPublicKey(rawKey);
			if (null == key)
			{
				throw new UsageException("Not a valid IPFS public key: \"" + rawKey + "\"");
			}
			return key;
		}
		
		private static IpfsFile _parseAsFile(String base58) throws UsageException
		{
			IpfsFile file = IpfsFile.fromIpfsCid(base58);
			if (null == file)
			{
				throw new UsageException("Not a valid IPFS base-58 CID: \"" + base58 + "\"");
			}
			return file;
		}
		
		
		private final boolean _topLevel;
		private final String _name;
		private final String _params[];
		private final String _optionalParams[];
		private final ArgPattern _subType;
		private final IParseFunction _factory;
		
		private ArgPattern(boolean topLevel, String name, String params[], String optionalParams[], ArgPattern subType, IParseFunction factory)
		{
			_topLevel = topLevel;
			_name = name;
			_params = params;
			_optionalParams = optionalParams;
			_subType = subType;
			_factory = factory;
		}
		
		private boolean isValid(String arg)
		{
			return _topLevel && (arg.equals(_name));
		}
		
		private ICommand parse(String[] args, int[] startIndex) throws UsageException
		{
			// We need to convert the entire input to a command, update the startIndex in/out parameter once we have processed all of our known options.
			// We can return null if we fail at any point, and the top-level will fail.
			int scanIndex = startIndex[0];
			int consumedIndex = scanIndex + 1;
			Assert.assertTrue(args[scanIndex].equals(_name));
			scanIndex += 1;
			
			String[] required = new String[_params.length];
			String[] optional = new String[_optionalParams.length];
			List<ICommand> subElements = new ArrayList<>();
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
					String check = _params[i];
					if (next.equals(check))
					{
						if (null != required[i])
						{
							requiredCount -= 1;
						}
						required[i] = value;
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
						String check = _optionalParams[i];
						if (next.equals(check))
						{
							optional[i] = value;
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
					ICommand subCommand = _subType.parse(args, sub);
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
			for(String param : _params)
			{
				stream.print(param + " value ");
			}
			for(String param : _optionalParams)
			{
				stream.print("[" + param + " value] ");
			}
			if (null != _subType)
			{
				stream.print("(");
				_subType.printUsage(stream);
				stream.print(")+ ");
			}
		}
	}

	public static ICommand parseArgs(String[] args, PrintStream errorStream) throws UsageException
	{
		// We assume that we only get this far is we have args.
		Assert.assertTrue(args.length > 0);
		
		ICommand matched = null;
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
}
