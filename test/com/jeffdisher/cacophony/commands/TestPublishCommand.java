package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestPublishCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testUsage() throws IOException
	{
		String name = "entry name";
		String discussionUrl = null;
		String mime = "text/plain";
		File tempFile = FOLDER.newFile();
		String fileContents = "Testing\n";
		FileOutputStream stream = new FileOutputStream(tempFile);
		stream.write(fileContents.getBytes());
		stream.close();
		
		ElementSubCommand[] elements = { new ElementSubCommand(mime, tempFile, 0, 0, false) };
		PublishCommand command = new PublishCommand(name, discussionUrl, elements);
		Executor executor = new Executor(System.out);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(null);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism, null);
		MockLocalActions localActions = new MockLocalActions(null, null, sharedConnection, pinCache, pinMechanism, followIndex);
		
		// We need to create the channel first so we will just use the command to do that.
		new CreateChannelCommand(IPFS_HOST, KEY_NAME).scheduleActions(executor, localActions);
		
		// Now, run the publish command.
		command.scheduleActions(executor, localActions);
		
		// Verify the states that should have changed.
		IpfsFile root = sharedConnection.resolve(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(sharedConnection.loadData(root));
		Assert.assertEquals(1, index.getVersion());
		StreamRecords records = GlobalData.deserializeRecords(sharedConnection.loadData(IpfsFile.fromIpfsCid(index.getRecords())));
		List<String> recordCidList = records.getRecord();
		Assert.assertEquals(1, recordCidList.size());
		StreamRecord record = GlobalData.deserializeRecord(sharedConnection.loadData(IpfsFile.fromIpfsCid(recordCidList.get(0))));
		Assert.assertEquals(name, record.getName());
		Assert.assertNull(record.getDiscussion());
		List<DataElement> dataElements = record.getElements().getElement();
		Assert.assertEquals(1, dataElements.size());
		DataElement elt = dataElements.get(0);
		Assert.assertEquals(mime, elt.getMime());
		Assert.assertEquals(fileContents, new String(sharedConnection.loadData(IpfsFile.fromIpfsCid(elt.getCid()))));
	}
}
