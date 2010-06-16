package com.aptana.ui.internal.commands;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
	private static final String BUNDLE_NAME = "com.aptana.ui.internal.commands.messages"; //$NON-NLS-1$

	public static String ClearLogConfirmTitle;
	public static String ClearLogConfirmDescription;

	static
	{
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages()
	{
	}
}
