package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the user's public key as a plain-text string.
 */
public class GET_PublicKey implements IGetHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	
	public GET_PublicKey(IEnvironment environment, String xsrf)
	{
		_environment = environment;
		_xsrf = xsrf;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				response.setContentType("text/plain");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print(access.getPublicKey().toPublicKey());
			}
			catch (IpfsConnectionException e)
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				e.printStackTrace(response.getWriter());
			}
			catch (UsageException | VersionException e)
			{
				// Not expected after start-up.
				throw Assert.unexpected(e);
			}
		}
	}
}
