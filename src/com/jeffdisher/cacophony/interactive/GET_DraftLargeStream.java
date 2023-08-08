package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v3.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.utils.MiscHelpers;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * A generic GET handler for reading the large files from drafts for the web interface.
 */
public class GET_DraftLargeStream implements ValidatedEntryPoints.GET
{
	private final DraftManager _draftManager;
	private final Function<IDraftWrapper, InputStream> _fileLoader;
	private final Function<Draft, String> _mimeLoader;
	private final Function<Draft, Long> _sizeLoader;

	public GET_DraftLargeStream(DraftManager draftManager, Function<IDraftWrapper, InputStream> fileLoader, Function<Draft, String> mimeLoader, Function<Draft, Long> sizeLoader)
	{
		_draftManager = draftManager;
		_fileLoader = fileLoader;
		_mimeLoader = mimeLoader;
		_sizeLoader = sizeLoader;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		int draftId = Integer.parseInt((String)path[2]);
		try
		{
			ServletOutputStream output = response.getOutputStream();
			
			IDraftWrapper wrapper = _draftManager.openExistingDraft(draftId);
			if (null == wrapper)
			{
				throw new FileNotFoundException();
			}
			try (InputStream input = _fileLoader.apply(wrapper))
			{
				if (null == input)
				{
					throw new FileNotFoundException();
				}
				Draft draft = wrapper.loadDraft();
				String mime = _mimeLoader.apply(draft);
				long byteSize = _sizeLoader.apply(draft);
				
				response.setContentType(mime);
				response.setContentLengthLong(byteSize);
				response.setStatus(HttpServletResponse.SC_OK);
				
				MiscHelpers.copyToEndOfFile(input, output);
			}
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
		
	}
}
