package com.jeffdisher.cacophony.scenarios;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestLocalIntegrity
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String KEY_NAME1 = "keyName1";

	@Test
	public void testInitialCreation() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		node.addNewKey(KEY_NAME1, MockKeys.K1);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, fileSystem, null);
		Context context = _createSingleNode(model, node, scheduler, null);
		
		CreateChannelCommand createChannel = new CreateChannelCommand(KEY_NAME1);
		createChannel.runInContext(context);
		
		// We expect 4 keys in the storage:
		// -index
		// -recommendations
		// -records
		// -description
		Assert.assertEquals(4, node.getStoredFileSet().size());
		scheduler.shutdown();
	}

	@Test
	public void testPublishAndRemove() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode node = new MockSingleNode(swarm);
		node.addNewKey(KEY_NAME1, MockKeys.K1);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(node, 1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, fileSystem, null);
		Context context = _createSingleNode(model, node, scheduler, null);
		
		CreateChannelCommand createChannel = new CreateChannelCommand(KEY_NAME1);
		createChannel.runInContext(context);
		
		// We expect the normal 4.
		Set<IpfsFile> initialFiles = node.getStoredFileSet();
		Assert.assertEquals(4, initialFiles.size());
		
		// Now, publish an entry.
		File tempFile = FOLDER.newFile();
		byte[] imageFile = new byte[] { 1,2,3,4,5 };
		try (FileOutputStream stream = new FileOutputStream(tempFile))
		{
			stream.write(imageFile);
		}
		PublishCommand publish = new PublishCommand("name", "description", null, null, "image/jpeg", tempFile, new ElementSubCommand[0]);
		OnePost result = publish.runInContext(_createSingleNode(model, node, scheduler, MockKeys.K1));
		// We expect 7 keys in the storage:
		// -index
		// -recommendations
		// -records
		// -record
		// -record image
		// -description
		Set<IpfsFile> afterPublishFiles = node.getStoredFileSet();
		Assert.assertEquals(6, afterPublishFiles.size());
		
		// Find the entry.
		IpfsFile entry = null;
		for (IpfsFile file : afterPublishFiles)
		{
			if (new String(node.loadData(file)).contains("<record xmlns=\"https://raw.githubusercontent.com/jmdisher/Cacophony/master/xsd/record2.xsd\">"))
			{
				Assert.assertNull(entry);
				entry = file;
			}
		}
		Assert.assertNotNull(entry);
		// This should be the returned CID.
		Assert.assertEquals(result.recordCid, entry);
		
		// Now, remove this entry.
		RemoveEntryFromThisChannelCommand remove = new RemoveEntryFromThisChannelCommand(entry);
		remove.runInContext(_createSingleNode(model, node, scheduler, MockKeys.K1));
		
		// We should see the same files from the original post.
		Set<IpfsFile> afterRemoveFiles = node.getStoredFileSet();
		Assert.assertEquals(4, afterRemoveFiles.size());
		Assert.assertEquals(initialFiles, afterRemoveFiles);
		scheduler.shutdown();
	}


	private static Context _createSingleNode(LocalDataModel model, IConnection serverData, MultiThreadedScheduler scheduler, IpfsKey selectedKey) throws UsageException, IOException
	{
		SilentLogger logger = new SilentLogger();
		return new Context(null
				, model
				, serverData
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, new CacheUpdater(null, null, null, null, null)
				, null
				, selectedKey
		);
	}
}
