package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestLocalIntegrity
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testInitialCreation() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		node.addNewKey(KEY_NAME1, PUBLIC_KEY1);
		IEnvironment env = _createSingleNode(node);
		SilentLogger logger = new SilentLogger();
		
		StandardAccess.createNewChannelConfig(env, "ipfs", KEY_NAME1);
		CreateChannelCommand createChannel = new CreateChannelCommand(KEY_NAME1);
		createChannel.runInContext(new ICommand.Context(env, logger, null, null, null));
		
		// We expect 5 keys in the storage:
		// -index
		// -recommendations
		// -records
		// -description
		// -description image
		Assert.assertEquals(5, node.getStoredFileSet().size());
	}

	@Test
	public void testPublishAndRemove() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		node.addNewKey(KEY_NAME1, PUBLIC_KEY1);
		IEnvironment env = _createSingleNode(node);
		SilentLogger logger = new SilentLogger();
		
		StandardAccess.createNewChannelConfig(env, "ipfs", KEY_NAME1);
		CreateChannelCommand createChannel = new CreateChannelCommand(KEY_NAME1);
		createChannel.runInContext(new ICommand.Context(env, logger, null, null, null));
		
		// We expect the normal 5.
		Set<IpfsFile> initialFiles = node.getStoredFileSet();
		Assert.assertEquals(5, initialFiles.size());
		
		// Now, publish an entry.
		File tempFile = FOLDER.newFile();
		byte[] imageFile = new byte[] { 1,2,3,4,5 };
		try (FileOutputStream stream = new FileOutputStream(tempFile))
		{
			stream.write(imageFile);
		}
		PublishCommand publish = new PublishCommand("name", "description", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", tempFile, 100, 100, true),
		});
		publish.runInContext(new ICommand.Context(env, logger, null, null, null));
		// We expect 7 keys in the storage:
		// -index
		// -recommendations
		// -records
		// -record
		// -record image
		// -description
		// -description image
		Set<IpfsFile> afterPublishFiles = node.getStoredFileSet();
		Assert.assertEquals(7, afterPublishFiles.size());
		
		// Find the entry.
		IpfsFile entry = null;
		for (IpfsFile file : afterPublishFiles)
		{
			if (new String(node.loadData(file)).contains("<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/global/record.xsd\">"))
			{
				Assert.assertNull(entry);
				entry = file;
			}
		}
		Assert.assertNotNull(entry);
		
		// Now, remove this entry.
		RemoveEntryFromThisChannelCommand remove = new RemoveEntryFromThisChannelCommand(entry);
		remove.runInContext(new ICommand.Context(env, logger, null, null, null));
		
		// We should see the same files from the original post.
		Set<IpfsFile> afterRemoveFiles = node.getStoredFileSet();
		Assert.assertEquals(5, afterRemoveFiles.size());
		Assert.assertEquals(initialFiles, afterRemoveFiles);
	}


	private static IEnvironment _createSingleNode(IConnection serverData)
	{
		return new StandardEnvironment(new MemoryConfigFileSystem(null), serverData, KEY_NAME1, PUBLIC_KEY1);
	}
}
