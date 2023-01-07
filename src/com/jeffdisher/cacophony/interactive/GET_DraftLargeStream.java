package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Function;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.MiscHelpers;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * A generic GET handler for reading the large files from drafts for the web interface.
 */
public class GET_DraftLargeStream implements IGetHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	private final Function<DraftWrapper, File> _fileLoader;
	private final Function<Draft, String> _mimeLoader;

	public GET_DraftLargeStream(String xsrf, DraftManager draftManager, Function<DraftWrapper, File> fileLoader, Function<Draft, String> mimeLoader)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
		_fileLoader = fileLoader;
		_mimeLoader = mimeLoader;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			int draftId = Integer.parseInt(variables[0]);
			try
			{
				ServletOutputStream output = response.getOutputStream();
				
				DraftWrapper wrapper = _draftManager.openExistingDraft(draftId);
				File file = _fileLoader.apply(wrapper);
				try (FileInputStream input = new FileInputStream(file))
				{
					String mime = _mimeLoader.apply(wrapper.loadDraft());
					long byteSize = file.length();
					
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
}
