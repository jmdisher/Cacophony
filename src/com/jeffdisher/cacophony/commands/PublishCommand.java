package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand<OnePost>
{
	@Override
	public OnePost runInContext(Context context) throws IpfsConnectionException, UsageException, SizeConstraintException
	{
		if (null == _name)
		{
			throw new UsageException("Name must be provided");
		}
		if (null == _description)
		{
			throw new UsageException("Description must be provided");
		}
		IpfsKey publicKey = context.getSelectedKey();
		if (null == publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		// We will expect that any entry-point will allocate this, so it isn't a user-facing error.
		Assert.assertTrue(null != _elements);
		
		ILogger log = context.logger.logStart("Publish: " + this);
		PublishElement[] openElements = openElementFiles(context.logger, _elements);
		Assert.assertTrue(null != openElements);
		IpfsFile newRoot;
		IpfsFile newElement;
		AbstractRecord newRecord;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// We expect that this context exists.
			Assert.assertTrue(null != access.getLastRootElement());
			
			// Upload the elements - we will just do this one at a time, for simplicity (and since we are talking to a local node).
			List<AbstractRecord.Leaf> array = new ArrayList<>();
			Thumbnail thumbnail = _uploadAttachments(array, access, log, openElements);
			
			// Assemble and upload the new StreamRecord.
			AbstractRecord record = _createRecord(thumbnail, array, publicKey);
			byte[] data = record.serializeV1();
			IpfsFile recordHash = access.uploadAndPin(new ByteArrayInputStream(data));
			
			// Now, update the channel data structure.
			newRoot = _modifyUserStream(access, recordHash);
			
			log.logVerbose("Saving and publishing new index");
			newElement = recordHash;
			newRecord = record;
		}
		finally
		{
			// No matter how this ended, close the files.
			closeElementFiles(openElements);
		}
		
		// Update the caches, if they exist.
		if (null != context.entryRegistry)
		{
			context.entryRegistry.addLocalElement(publicKey, newElement);
		}
		if (null != context.recordCache)
		{
			LocalRecordCacheBuilder.updateCacheWithNewUserPost(context.recordCache, newElement, newRecord);
		}
		
		log.logOperation("New element: " + newElement);
		log.logFinish("Publish completed!");
		return new OnePost(newRoot, newElement, newRecord);
	}


	private static PublishElement[] openElementFiles(ILogger logger, ElementSubCommand[] commands) throws UsageException
	{
		boolean error = false;
		PublishElement[] elements = new PublishElement[commands.length];
		for (int i = 0; !error && (i < commands.length); ++i)
		{
			ElementSubCommand command = commands[i];
			File file = command.filePath();
			try
			{
				FileInputStream stream = new FileInputStream(file);
				elements[i] = new PublishElement(command.mime(), stream, command.height(), command.width(), command.isSpecialImage());
			}
			catch (FileNotFoundException e)
			{
				logger.logError("File not found:  " + file.getAbsolutePath());
				error = true;
			}
		}
		if (error)
		{
			closeElementFiles(elements);
			throw new UsageException("Failed to open all files to upload");
		}
		return elements;
	}

	private Thumbnail _uploadAttachments(List<AbstractRecord.Leaf> out_array, IWritingAccess access, ILogger log, PublishElement[] openElements) throws IpfsConnectionException
	{
		Thumbnail thumbnail = null;
		for (PublishElement elt : openElements)
		{
			ILogger eltLog = log.logStart("-Element: " + elt);
			// Note that this call will close the file (since it is intended to drain it) but we will still close it in
			// the caller, just to cover error cases before getting this far.
			IpfsFile uploaded = access.uploadAndPin(elt.fileData);
			
			if (elt.isSpecialImage())
			{
				Assert.assertTrue(null == thumbnail);
				thumbnail = new Thumbnail(elt.mime, uploaded);
			}
			else
			{
				AbstractRecord.Leaf oneLeaf = new AbstractRecord.Leaf(uploaded
						, elt.mime()
						, elt.height()
						, elt.width()
				);
				out_array.add(oneLeaf);
			}
			eltLog.logFinish("-Done!");
		}
		return thumbnail;
	}

	private AbstractRecord _createRecord(Thumbnail thumbnail, List<AbstractRecord.Leaf> attachmentArray, IpfsKey publicKey)
	{
		AbstractRecord record = AbstractRecord.createNew();
		record.setName(_name);
		record.setDescription(_description);
		if (null != _discussionUrl)
		{
			record.setDiscussionUrl(_discussionUrl);
		}
		if (null != thumbnail)
		{
			record.setThumbnail(thumbnail.mime, thumbnail.cid);
		}
		if (!attachmentArray.isEmpty())
		{
			record.setVideoExtension(attachmentArray);
		}
		record.setPublisherKey(publicKey);
		// The published time is in seconds since the Epoch, in UTC.
		record.setPublishedSecondsUtc(_currentUtcEpochSeconds());
		return record;
	}

	private IpfsFile _modifyUserStream(IWritingAccess access, IpfsFile recordHash) throws IpfsConnectionException
	{
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractRecords records = modifier.loadRecords();
		List<IpfsFile> recordCids = records.getRecordList();
		// This assertion is just to avoid some corner-cases which can happen in testing but have no obvious meaning in real usage.
		// This can happen when 2 posts are made before the time has advanced.
		// While the record list is allowed to contain duplicates, this is usually just the result of a test running too quickly or being otherwise incorrect.
		// (We could make this into an actual error case if it were meaningful).
		Assert.assertTrue(!recordCids.contains(recordHash));
		records.addRecord(recordHash);
		
		// Save the updated records and index.
		modifier.storeRecords(records);
		return modifier.commitNewRoot();
	}

	private static void closeElementFiles(PublishElement[] elements)
	{
		for (PublishElement element : elements)
		{
			if (null != element)
			{
				InputStream file = element.fileData();
				try
				{
					file.close();
				}
				catch (IOException e)
				{
					// We don't know how this fails on close.
					throw Assert.unexpected(e);
				}
			}
		}
	}

	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}


	private static record PublishElement(String mime, InputStream fileData, int height, int width, boolean isSpecialImage) {}
	private static record Thumbnail(String mime, IpfsFile cid) {}
}
