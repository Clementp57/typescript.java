/**
 *  Copyright (c) 2013-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.internal.core.nodejs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.Platform;

import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.nodejs.IEmbeddedNodejs;
import ts.eclipse.ide.core.nodejs.INodejsInstallManager;
import ts.eclipse.ide.internal.core.Trace;

public class NodejsInstallManager implements INodejsInstallManager, IRegistryChangeListener {

	private static final String EXTENSION_NODEJS_INSTALLS = "nodeJSInstalls";

	private static final NodejsInstallManager INSTANCE = new NodejsInstallManager();

	// cached copy of all Nodejs install
	private List<IEmbeddedNodejs> nodeJSInstalls;

	private boolean registryListenerIntialized;

	public static NodejsInstallManager getManager() {
		return INSTANCE;
	}

	public NodejsInstallManager() {
		this.registryListenerIntialized = false;
	}

	@Override
	public void registryChanged(final IRegistryChangeEvent event) {
		IExtensionDelta[] deltas = event.getExtensionDeltas(TypeScriptCorePlugin.PLUGIN_ID, EXTENSION_NODEJS_INSTALLS);
		if (deltas != null) {
			for (IExtensionDelta delta : deltas)
				handleNodejsInstallDelta(delta);
		}
	}

	/**
	 * Returns an array of all known Nodejs installs.
	 * <p>
	 * A new array is returned on each call, so clients may store or modify the
	 * result.
	 * </p>
	 * 
	 * @return the array of Nodejs installs {@link IEmbeddedNodejs}
	 */
	public IEmbeddedNodejs[] getNodejsInstalls() {
		if (nodeJSInstalls == null)
			loadNodejsInstalls();

		IEmbeddedNodejs[] st = new IEmbeddedNodejs[nodeJSInstalls.size()];
		nodeJSInstalls.toArray(st);
		return st;
	}

	/**
	 * Returns the Nodejs install with the given id, or <code>null</code> if
	 * none. This convenience method searches the list of known Nodejs installs
	 * ({@link #getNodejsInstalls()}) for the one with a matching Nodejs install
	 * id ({@link IEmbeddedNodejs#getId()}). The id may not be null.
	 * 
	 * @param id
	 *            the Nodejs install id
	 * @return the Nodejs install, or <code>null</code> if there is no generator
	 *         type with the given id
	 */
	public IEmbeddedNodejs findNodejsInstall(String id) {
		if (id == null) {
			throw new IllegalArgumentException();
		}
		if (nodeJSInstalls == null) {
			loadNodejsInstalls();
		}
		Iterator<IEmbeddedNodejs> iterator = nodeJSInstalls.iterator();
		while (iterator.hasNext()) {
			IEmbeddedNodejs nodeJSInstall = (IEmbeddedNodejs) iterator.next();
			if (id.equals(nodeJSInstall.getId()))
				return nodeJSInstall;
		}
		return null;
	}

	/**
	 * Load the Nodejs installs.
	 */
	private synchronized void loadNodejsInstalls() {
		if (nodeJSInstalls != null)
			return;

		Trace.trace(Trace.EXTENSION_POINT, "->- Loading .nodeJSInstalls extension point ->-");

		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IConfigurationElement[] cf = registry.getConfigurationElementsFor(TypeScriptCorePlugin.PLUGIN_ID,
				EXTENSION_NODEJS_INSTALLS);
		List<IEmbeddedNodejs> list = new ArrayList<IEmbeddedNodejs>(cf.length);
		addNodejsInstalls(cf, list);
		addRegistryListenerIfNeeded();
		nodeJSInstalls = list;

		Trace.trace(Trace.EXTENSION_POINT, "-<- Done loading .nodeJSInstalls extension point -<-");
	}

	/**
	 * Load the Nodejs installs.
	 */
	private synchronized void addNodejsInstalls(IConfigurationElement[] cf, List<IEmbeddedNodejs> list) {
		for (IConfigurationElement ce : cf) {
			try {
				list.add(new NodejsInstall(ce));
				Trace.trace(Trace.EXTENSION_POINT, "  Loaded nodeJSInstall: " + ce.getAttribute("id"));
			} catch (Throwable t) {
				Trace.trace(Trace.SEVERE, "  Could not load nodeJSInstall: " + ce.getAttribute("id"), t);
			}
		}
	}

	protected void handleNodejsInstallDelta(IExtensionDelta delta) {
		if (nodeJSInstalls == null) // not loaded yet
			return;

		IConfigurationElement[] cf = delta.getExtension().getConfigurationElements();

		List<IEmbeddedNodejs> list = new ArrayList<IEmbeddedNodejs>(nodeJSInstalls);
		if (delta.getKind() == IExtensionDelta.ADDED) {
			addNodejsInstalls(cf, list);
		} else {
			int size = list.size();
			NodejsInstall[] st = new NodejsInstall[size];
			list.toArray(st);
			int size2 = cf.length;

			for (int i = 0; i < size; i++) {
				for (int j = 0; j < size2; j++) {
					if (st[i].getId().equals(cf[j].getAttribute("id"))) {
						st[i].dispose();
						list.remove(st[i]);
					}
				}
			}
		}
		nodeJSInstalls = list;
	}

	private void addRegistryListenerIfNeeded() {
		if (registryListenerIntialized)
			return;

		IExtensionRegistry registry = Platform.getExtensionRegistry();
		registry.addRegistryChangeListener(this, TypeScriptCorePlugin.PLUGIN_ID);
		registryListenerIntialized = true;
	}

	public void initialize() {

	}

	public void destroy() {
		Platform.getExtensionRegistry().removeRegistryChangeListener(this);
	}
}
