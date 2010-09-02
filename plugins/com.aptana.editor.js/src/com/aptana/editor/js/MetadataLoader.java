package com.aptana.editor.js;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

import com.aptana.editor.common.contentassist.UserAgentManager;
import com.aptana.editor.common.contentassist.UserAgentManager.UserAgent;
import com.aptana.editor.js.contentassist.JSIndexQueryHelper;
import com.aptana.editor.js.contentassist.index.JSIndexConstants;
import com.aptana.editor.js.contentassist.index.JSIndexWriter;
import com.aptana.editor.js.contentassist.index.JSMetadataReader;
import com.aptana.editor.js.contentassist.index.ScriptDocException;
import com.aptana.editor.js.contentassist.model.TypeElement;
import com.aptana.editor.js.contentassist.model.UserAgentElement;
import com.aptana.editor.js.preferences.IPreferenceConstants;
import com.aptana.index.core.Index;
import com.aptana.index.core.IndexManager;
import com.aptana.index.core.IndexProjectJob;

public class MetadataLoader extends Job
{
	/**
	 * MetadataLoader
	 */
	public MetadataLoader()
	{
		super(Messages.Loading_Metadata);

		setPriority(Job.LONG);
	}

	/**
	 * loadMetadata
	 * 
	 * @param monitor
	 * @param resources
	 */
	private void loadMetadata(IProgressMonitor monitor, JSMetadataReader reader, String... resources)
	{
		SubMonitor subMonitor = SubMonitor.convert(monitor, resources.length);

		for (String resource : resources)
		{
			URL url = FileLocator.find(Activator.getDefault().getBundle(), new Path(resource), null);

			if (url != null)
			{
				InputStream stream = null;

				try
				{
					stream = url.openStream();

					reader.loadXML(stream);
				}
				catch (Throwable t)
				{
					Activator.logError(Messages.Activator_Error_Loading_Metadata + ":" + resource, t);
				}
				finally
				{
					if (stream != null)
					{
						try
						{
							stream.close();
						}
						catch (IOException e)
						{
						}
					}
				}
			}

			subMonitor.worked(1);
		}

		subMonitor.done();
	}

	/**
	 * rebuildMetadataIndex
	 * 
	 * @param monitor
	 */
	private void rebuildMetadataIndex(IProgressMonitor monitor)
	{
		// delete any existing metadata index
		IndexManager.getInstance().removeIndex(URI.create(JSIndexConstants.METADATA_INDEX_LOCATION));

		JSMetadataReader reader = new JSMetadataReader();

		this.loadMetadata(monitor, reader, "/metadata/js_core.xml", //$NON-NLS-1$
			"/metadata/dom_0.xml", //$NON-NLS-1$
			"/metadata/dom_2.xml", //$NON-NLS-1$
			"/metadata/dom_3.xml", //$NON-NLS-1$
			"/metadata/dom_5.xml" //$NON-NLS-1$
		);

		JSIndexWriter indexer = new JSIndexWriter();
		Index index = JSIndexQueryHelper.getIndex();

		// write user agents from user agent list in prefs
		for (UserAgent userAgent : UserAgentManager.getInstance().getAllUserAgents())
		{
			UserAgentElement ua = new UserAgentElement();

			ua.setPlatform(userAgent.ID);

			indexer.writeUserAgent(ua);
		}

		// write types
		for (TypeElement type : reader.getTypes())
		{
			indexer.writeType(index, type);
		}

		try
		{
			index.save();
		}
		catch (IOException e)
		{
			Activator.logError(e.getMessage(), e);
		}
	}

	/**
	 * rebuildProjectIndexes
	 */
	private void rebuildProjectIndexes()
	{
		IndexManager manager = IndexManager.getInstance();

		// TODO: We are temporarily deleting the entire project index
		// because it appears that Index#removeCategories doesn't clean up
		// the index's list of documents. This prevents the files from
		// being indexed during the timestamp comparison step in Index.
		boolean deleteIndex = true;

		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
		{
			if (deleteIndex)
			{
				manager.removeIndex(project.getLocationURI());
			}
			else
			{
				Index index = manager.getIndex(project.getLocationURI());

				if (index != null)
				{
					index.removeCategories(JSIndexConstants.ALL_CATEGORIES);
				}
			}

			// re-index
			new IndexProjectJob(project).schedule();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IStatus run(IProgressMonitor monitor)
	{
		double expectedVersion = Platform.getPreferencesService().getDouble(Activator.PLUGIN_ID, IPreferenceConstants.JS_INDEX_VERSION, 0.0, null);

		if (expectedVersion != JSIndexConstants.INDEX_VERSION)
		{
			// rebuild indexes
			this.rebuildMetadataIndex(monitor);
			this.rebuildProjectIndexes();

			// update version preference to latest version
			this.updateVersionPreference();
		}

		return Status.OK_STATUS;
	}

	/**
	 * updateVersionPreference
	 */
	private void updateVersionPreference()
	{
		IEclipsePreferences prefs = (new InstanceScope()).getNode(Activator.PLUGIN_ID);

		prefs.putDouble(IPreferenceConstants.JS_INDEX_VERSION, JSIndexConstants.INDEX_VERSION);

		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
		}
	}
}
