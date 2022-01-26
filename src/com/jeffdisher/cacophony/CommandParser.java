package com.jeffdisher.cacophony;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.ListChannelEntriesCommand;
import com.jeffdisher.cacophony.commands.ListFolloweesCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class CommandParser
{
	@java.lang.FunctionalInterface
	private static interface TriFunction<A, B, C, R>
	{
		public R apply(A a, B b, C c);
	}
	private static enum ArgPattern
	{
		// Sub-element side args (these are always assumed to have one or more potential instances).
		ELEMENT(false, "--element", new String[] {"--mime", "--file"}, new String[] {"--codec", "--height", "--width", "--special"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String mime = required[0];
			File filePath = new File(required[1]);
			String codec = optional[0];
			int height = (null != optional[1])
					? Integer.parseInt(optional[1])
					: 0
			;
			int width = (null != optional[2])
					? Integer.parseInt(optional[2])
					: 0
			;
			boolean isSpecialImage = (null != optional[3])
					? "image".equals(optional[3])
					: false
			;
			return new ElementSubCommand(mime, filePath, codec, height, width, isSpecialImage);
		}),
		
		// Methods to manage this channel.
		CREATE_NEW_CHANNEL(true, "--createNewChannel", new String[] {"--ipfs", "--keyName"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String ipfs = required[0];
			String keyName = required[1];
			return new CreateChannelCommand(ipfs, keyName);
		}),
		UPDATE_DESCRIPTION(true, "--updateDescription", new String[0], new String[] {"--name", "--description", "--pictureFile"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String name = optional[0];
			String description = optional[1];
			File picturePath = (null != optional[2])
					? new File(optional[2])
					: null
			;
			return new UpdateDescriptionCommand(name, description, picturePath);
		}),
		READ_DESCRIPTION(true, "--readDescription", new String[0], new String[] {"--publicKey"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String rawKey = optional[0];
			IpfsKey channelPublicKey = (null != rawKey)
					? IpfsKey.fromPublicKey(rawKey)
					: null
			;
			return new ReadDescriptionCommand(channelPublicKey);
		}),
		ADD_RECOMMENDATION(true, "--addRecommendation", new String[] {"--channelPublicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey channelPublicKey = IpfsKey.fromPublicKey(required[0]);
			return new AddRecommendationCommand(channelPublicKey);
		}),
		REMOVE_RECOMMENDATION(true, "--removeRecommendation", new String[] {"--channelPublicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey channelPublicKey = IpfsKey.fromPublicKey(required[0]);
			return new RemoveRecommendationCommand(channelPublicKey);
		}),
		LIST_RECOMMENDATIONS(true, "--listRecommendations", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new ListRecommendationsCommand();
		}),
		PUBLISH_TO_THIS_CHANNEL(true, "--publishToThisChannel", new String[] {"--name"}, new String[] {"--discussionUrl"}, ELEMENT, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String name = required[0];
			String discussionUrl = optional[0];
			ElementSubCommand elements[] = new ElementSubCommand[subElements.size()];
			elements = subElements.toArray(elements);
			return new PublishCommand(name, discussionUrl, elements);
		}),
		LIST_THIS_CHANNEL(true, "--listChannel", new String[0], new String[] {"--publicKey"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			String rawKey = optional[0];
			IpfsKey channelPublicKey = (null != rawKey)
					? IpfsKey.fromPublicKey(rawKey)
					: null
			;
			return new ListChannelEntriesCommand(channelPublicKey);
		}),
		REMOVE_FROM_THIS_CHANNEL(true, "--removeFromThisChannel", new String[] {"--elementCid"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsFile elementCid = IpfsFile.fromIpfsCid(required[0]);
			return new RemoveEntryFromThisChannelCommand(elementCid);
		}),
		START_FOLLOWING(true, "--startFollowing", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey publicKey = IpfsKey.fromPublicKey(required[0]);
			return new StartFollowingCommand(publicKey);
		}),
		STOP_FOLLOWING(true, "--stopFollowing", new String[] {"--publicKey"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			IpfsKey publicKey = IpfsKey.fromPublicKey(required[0]);
			return new StopFollowingCommand(publicKey);
		}),
		LIST_FOLLOWEES(true, "--listFollowees", new String[0], new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			return new ListFolloweesCommand();
		}),
		
		// Methods to manage local state.
		SET_GLOBAL_PREFS(true, "--setGlobalPrefs", new String[0], new String[] {"--cacheWidth", "--cacheHeight", "--cacheTotalBytes"}, null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			int cacheWidth = (null != optional[0])
					? Integer.parseInt(optional[0])
					: 0
			;
			int cacheHeight = (null != optional[1])
					? Integer.parseInt(optional[1])
					: 0
			;
			long cacheTotalBytes = (null != optional[2])
					? Long.parseLong(optional[2])
					: 0L
			;
			return new SetGlobalPrefsCommand(cacheWidth, cacheHeight, cacheTotalBytes);
		}),
		CANONICALIZE_KEY(true, "--canonicalizeKey", new String[] {"--key"}, new String[0], null, (String[] required, String[] optional, List<ICommand> subElements) ->
		{
			// This is just a utility function to make detecting keys in integration scripts more reliable.
			String key = required[0];
			return (Executor executor, LocalActions local) -> {
				System.out.println(IpfsKey.fromPublicKey(key).key());
			};
		}),
		
		;
		
		private final boolean _topLevel;
		private final String _name;
		private final String _params[];
		private final String _optionalParams[];
		private final ArgPattern _subType;
		private final TriFunction<String[], String[], List<ICommand>, ICommand> _factory;
		
		private ArgPattern(boolean topLevel, String name, String params[], String optionalParams[], ArgPattern subType, TriFunction<String[], String[], List<ICommand>, ICommand> factory)
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
		
		private ICommand parse(String[] args, int[] startIndex)
		{
			// We need to convert the entire input to a command, update the startIndex in/out parameter once we have processed all of our known options.
			// We can return null if we fail at any point, and the top-level will fail.
			int index = startIndex[0];
			assert args[index].equals(_name);
			index += 1;
			
			String[] required = new String[_params.length];
			String[] optional = new String[_optionalParams.length];
			List<ICommand> subElements = new ArrayList<>();
			int requiredCount = 0;
			
			boolean keepRunning = true;
			boolean fail = false;
			while (!fail && keepRunning && ((index + 1) < args.length))
			{
				keepRunning = false;
				String next = args[index];
				String value = args[index + 1];
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
						break;
					}
				}
				for (int i = 0; i < _optionalParams.length; ++i)
				{
					String check = _optionalParams[i];
					if (next.equals(check))
					{
						optional[i] = value;
						keepRunning = true;
						break;
					}
				}
				// See if this is a sub-element type.
				if (!keepRunning && (null != _subType) && _subType._name.equals(next))
				{
					int[] sub = new int[] {index};
					ICommand subCommand = _subType.parse(args, sub);
					if (null != subCommand)
					{
						keepRunning = true;
						index = sub[0];
						subElements.add(subCommand);
					}
					else
					{
						// We require that this can be parsed
						fail = true;
					}
				}
				else
				{
					index += 2;
				}
			}
			startIndex[0] = index;
			
			return (!fail && (requiredCount == required.length) && ((null == _subType) || !subElements.isEmpty()))
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

	public static ICommand parseArgs(String[] args, int startIndex, PrintStream errorStream)
	{
		// We assume that we only get this far is we have args.
		assert args.length > 0;
		
		ICommand matched = null;
		for (ArgPattern pattern : ArgPattern.values())
		{
			if (startIndex >= args.length)
			{
				errorStream.println("Missing command.");
				break;
			}
			else if (pattern.isValid(args[startIndex]))
			{
				// We use index as in/out parameter here, hence the array.
				int[] index = {startIndex};
				matched = pattern.parse(args, index);
				if (null != matched)
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
