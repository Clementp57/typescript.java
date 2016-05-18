/**
 *  Copyright (c) 2015-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.jsdt.internal.ui.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ISelectionValidator;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.wst.jsdt.ui.IContextMenuConstants;
import org.eclipse.wst.jsdt.ui.PreferenceConstants;

import ts.TypeScriptException;
import ts.TypeScriptNoContentAvailableException;
import ts.client.ICancellationToken;
import ts.client.ITypeScriptAsynchCollector;
import ts.client.Location;
import ts.client.navbar.NavigationBarItem;
import ts.client.occurrences.ITypeScriptOccurrencesCollector;
import ts.eclipse.ide.core.resources.IIDETypeScriptProject;
import ts.eclipse.ide.core.utils.TypeScriptResourceUtil;
import ts.eclipse.ide.jsdt.internal.ui.Trace;
import ts.eclipse.ide.jsdt.internal.ui.actions.CompositeActionGroup;
import ts.eclipse.ide.jsdt.internal.ui.actions.JavaSearchActionGroup;
import ts.eclipse.ide.jsdt.ui.actions.ITypeScriptEditorActionDefinitionIds;
import ts.eclipse.ide.ui.outline.TypeScriptContentOutlinePage;
import ts.eclipse.ide.ui.utils.EditorUtils;
import ts.resources.ITypeScriptFile;

/**
 * TypeScript editor.
 *
 */
public class TypeScriptEditor extends JavaScriptLightWeightEditor {

	protected CompositeActionGroup fActionGroups;
	private CompositeActionGroup fContextMenuGroup;

	private OccurrencesCollector occurrencesCollector;
	private OccurrencesFinderJob fOccurrencesFinderJob;
	/** The occurrences finder job canceler */
	private OccurrencesFinderJobCanceler fOccurrencesFinderJobCanceler;
	/**
	 * Holds the current occurrence annotations.
	 * 
	 */
	private Annotation[] fOccurrenceAnnotations = null;
	/**
	 * Tells whether all occurrences of the element at the current caret
	 * location are automatically marked in this editor.
	 * 
	 */
	private boolean fMarkOccurrenceAnnotations;
	/**
	 * The selection used when forcing occurrence marking through code.
	 * 
	 */
	private ISelection fForcedMarkOccurrencesSelection;
	/**
	 * The internal shell activation listener for updating occurrences.
	 * 
	 */
	private ActivationListener fActivationListener = new ActivationListener();

	/**
	 * Updates the Java outline page selection and this editor's range
	 * indicator.
	 *
	 * 
	 */
	private class EditorSelectionChangedListener extends AbstractSelectionChangedListener {

		/*
		 * @see
		 * org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(
		 * org.eclipse.jface.viewers.SelectionChangedEvent)
		 */
		public void selectionChanged(SelectionChangedEvent event) {
			// TypeScriptEditor.this.selectionChanged();

			ISelection selection = event.getSelection();
			if (selection instanceof ITextSelection) {
				ITextSelection textSelection = (ITextSelection) selection;
				updateOccurrenceAnnotations(textSelection);
			}
		}
	}

	/**
	 * Updates the selection in the editor's widget with the selection of the
	 * outline page.
	 */
	class OutlineSelectionChangedListener extends AbstractSelectionChangedListener {
		public void selectionChanged(SelectionChangedEvent event) {
			doSelectionChanged(event);
		}
	}

	/** The selection changed listener */
	protected AbstractSelectionChangedListener fOutlineSelectionChangedListener = new OutlineSelectionChangedListener();

	/**
	 * Outline page
	 */
	private TypeScriptContentOutlinePage contentOutlinePage;

	protected ActionGroup getActionGroup() {
		return fActionGroups;
	}

	@Override
	protected void createActions() {
		super.createActions();

		ActionGroup oeg, ovg, jsg;
		fActionGroups = new CompositeActionGroup(
				new ActionGroup[] { /*
									 * oeg = new OpenEditorActionGroup(this),
									 * ovg = new OpenViewActionGroup(this),
									 */ jsg = new JavaSearchActionGroup(this) });
		fContextMenuGroup = new CompositeActionGroup(
				new ActionGroup[] { /* oeg, ovg, */ jsg });

		// Format Action
		IAction action = new TextOperationAction(TypeScriptEditorMessages.getResourceBundle(), "Format.", this, //$NON-NLS-1$
				ISourceViewer.FORMAT);
		action.setActionDefinitionId(ITypeScriptEditorActionDefinitionIds.FORMAT);
		setAction("Format", action); //$NON-NLS-1$
		markAsStateDependentAction("Format", true); //$NON-NLS-1$
		markAsSelectionDependentAction("Format", true); //$NON-NLS-1$
		// PlatformUI.getWorkbench().getHelpSystem().setHelp(action,
		// IJavaHelpContextIds.FORMAT_ACTION);

		action = new TextOperationAction(TypeScriptEditorMessages.getResourceBundle(), "ShowOutline.", this, //$NON-NLS-1$
				TypeScriptSourceViewer.SHOW_OUTLINE, true);
		action.setActionDefinitionId(ITypeScriptEditorActionDefinitionIds.SHOW_OUTLINE);
		setAction(ITypeScriptEditorActionDefinitionIds.SHOW_OUTLINE, action);
		// PlatformUI.getWorkbench().getHelpSystem().setHelp(action,
		// IJavaHelpContextIds.SHOW_OUTLINE_ACTION);
	}

	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { "ts.eclipse.ide.jsdt.ui.typeScriptViewScope" }); //$NON-NLS-1$
	}

	@Override
	public void editorContextMenuAboutToShow(IMenuManager menu) {

		super.editorContextMenuAboutToShow(menu);
		menu.insertAfter(IContextMenuConstants.GROUP_OPEN, new GroupMarker(IContextMenuConstants.GROUP_SHOW));

		ActionContext context = new ActionContext(getSelectionProvider().getSelection());
		fContextMenuGroup.setContext(context);
		fContextMenuGroup.fillContextMenu(menu);
		fContextMenuGroup.setContext(null);

		// Quick views
		IAction action = getAction(ITypeScriptEditorActionDefinitionIds.SHOW_OUTLINE);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);
		// action= getAction(IJavaEditorActionDefinitionIds.OPEN_HIERARCHY);
		// menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);

	}

	@Override
	public void dispose() {
		super.dispose();

		if (fActionGroups != null) {
			fActionGroups.dispose();
			fActionGroups = null;
		}

		if (editorSelectionChangedListener != null) {
			editorSelectionChangedListener.uninstall(getSelectionProvider());
			editorSelectionChangedListener = null;
		}
		uninstallOccurrencesFinder();

		if (fActivationListener != null) {
			PlatformUI.getWorkbench().removeWindowListener(fActivationListener);
			fActivationListener = null;
		}
	}

	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		IPreferenceStore store = getPreferenceStore();
		fMarkOccurrenceAnnotations = store.getBoolean(PreferenceConstants.EDITOR_MARK_OCCURRENCES);
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);

		if (isMarkingOccurrences()) {
			installOccurrencesFinder(false);
		}
		PlatformUI.getWorkbench().addWindowListener(fActivationListener);
		editorSelectionChangedListener = new EditorSelectionChangedListener();
		editorSelectionChangedListener.install(getSelectionProvider());
	}

	/*
	 * @see AbstractTextEditor#handlePreferenceStoreChanged(PropertyChangeEvent)
	 */
	protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {

		String property = event.getProperty();

		if (AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH.equals(property)) {
			/*
			 * Ignore tab setting since we rely on the formatter preferences. We
			 * do this outside the try-finally block to avoid that
			 * EDITOR_TAB_WIDTH is handled by the sub-class
			 * (AbstractDecoratedTextEditor).
			 */
			return;
		}

		try {

			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer == null)
				return;

			boolean newBooleanValue = false;
			Object newValue = event.getNewValue();
			if (newValue != null)
				newBooleanValue = Boolean.valueOf(newValue.toString()).booleanValue();

			if (PreferenceConstants.EDITOR_MARK_OCCURRENCES.equals(property)) {
				if (newBooleanValue != fMarkOccurrenceAnnotations) {
					fMarkOccurrenceAnnotations = newBooleanValue;
					if (!fMarkOccurrenceAnnotations)
						uninstallOccurrencesFinder();
					else
						installOccurrencesFinder(true);
				}
				return;
			}
		} finally {
			super.handlePreferenceStoreChanged(event);
		}
	}

	// ---------------------- Occurrences

	private EditorSelectionChangedListener editorSelectionChangedListener;

	/**
	 * Internal activation listener.
	 * 
	 */
	private class ActivationListener implements IWindowListener {

		/*
		 * @see org.eclipse.ui.IWindowListener#windowActivated(org.eclipse.ui.
		 * IWorkbenchWindow)
		 * 
		 */
		public void windowActivated(IWorkbenchWindow window) {
			if (window == getEditorSite().getWorkbenchWindow() && fMarkOccurrenceAnnotations && isActivePart()) {
				fForcedMarkOccurrencesSelection = getSelectionProvider().getSelection();
				updateOccurrenceAnnotations((ITextSelection) fForcedMarkOccurrencesSelection);
			}
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowDeactivated(org.eclipse.ui.
		 * IWorkbenchWindow)
		 * 
		 */
		public void windowDeactivated(IWorkbenchWindow window) {
			if (window == getEditorSite().getWorkbenchWindow() && fMarkOccurrenceAnnotations && isActivePart())
				removeOccurrenceAnnotations();
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowClosed(org.eclipse.ui.
		 * IWorkbenchWindow)
		 * 
		 */
		public void windowClosed(IWorkbenchWindow window) {
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowOpened(org.eclipse.ui.
		 * IWorkbenchWindow)
		 * 
		 */
		public void windowOpened(IWorkbenchWindow window) {
		}
	}

	/**
	 * Finds and marks occurrence annotations.
	 *
	 * 
	 */
	class OccurrencesFinderJob extends Job {

		private IDocument fDocument;
		private ISelection fSelection;
		private ISelectionValidator fPostSelectionValidator;
		private boolean fCanceled = false;
		private IProgressMonitor fProgressMonitor;
		private Position[] fPositions;

		public OccurrencesFinderJob(IDocument document, Position[] positions, ISelection selection) {
			super(TypeScriptEditorMessages.TypeScriptEditor_markOccurrences_job_name);
			fDocument = document;
			fSelection = selection;
			fPositions = positions;

			if (getSelectionProvider() instanceof ISelectionValidator)
				fPostSelectionValidator = (ISelectionValidator) getSelectionProvider();
		}

		// cannot use cancel() because it is declared final
		void doCancel() {
			fCanceled = true;
			cancel();
		}

		private boolean isCanceled() {
			return fCanceled || fProgressMonitor.isCanceled()
					|| fPostSelectionValidator != null && !(fPostSelectionValidator.isValid(fSelection)
							|| fForcedMarkOccurrencesSelection == fSelection)
					|| LinkedModeModel.hasInstalledModel(fDocument);
		}

		/*
		 * @see Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		public IStatus run(IProgressMonitor progressMonitor) {

			fProgressMonitor = progressMonitor;

			if (isCanceled())
				return Status.CANCEL_STATUS;

			ITextViewer textViewer = getViewer();
			if (textViewer == null)
				return Status.CANCEL_STATUS;

			IDocument document = textViewer.getDocument();
			if (document == null)
				return Status.CANCEL_STATUS;

			IDocumentProvider documentProvider = getDocumentProvider();
			if (documentProvider == null)
				return Status.CANCEL_STATUS;

			IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
			if (annotationModel == null)
				return Status.CANCEL_STATUS;

			// Add occurrence annotations
			int length = fPositions.length;
			Map annotationMap = new HashMap(length);
			for (int i = 0; i < length; i++) {

				if (isCanceled())
					return Status.CANCEL_STATUS;

				String message;
				Position position = fPositions[i];

				// Create & add annotation
				try {
					message = document.get(position.offset, position.length);
				} catch (BadLocationException ex) {
					// Skip this match
					continue;
				}
				annotationMap.put(new Annotation("org.eclipse.wst.jsdt.ui.occurrences", false, message), //$NON-NLS-1$
						position);
			}

			if (isCanceled())
				return Status.CANCEL_STATUS;

			synchronized (getLockObject(annotationModel)) {
				if (annotationModel instanceof IAnnotationModelExtension) {
					((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations,
							annotationMap);
				} else {
					removeOccurrenceAnnotations();
					Iterator iter = annotationMap.entrySet().iterator();
					while (iter.hasNext()) {
						Map.Entry mapEntry = (Map.Entry) iter.next();
						annotationModel.addAnnotation((Annotation) mapEntry.getKey(), (Position) mapEntry.getValue());
					}
				}
				fOccurrenceAnnotations = (Annotation[]) annotationMap.keySet()
						.toArray(new Annotation[annotationMap.keySet().size()]);
			}

			return Status.OK_STATUS;
		}
	}

	/**
	 * Cancels the occurrences finder job upon document changes.
	 *
	 * 
	 */
	class OccurrencesFinderJobCanceler implements IDocumentListener, ITextInputListener {

		public void install() {
			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer == null)
				return;

			StyledText text = sourceViewer.getTextWidget();
			if (text == null || text.isDisposed())
				return;

			sourceViewer.addTextInputListener(this);

			IDocument document = sourceViewer.getDocument();
			if (document != null)
				document.addDocumentListener(this);
		}

		public void uninstall() {
			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer != null)
				sourceViewer.removeTextInputListener(this);

			IDocumentProvider documentProvider = getDocumentProvider();
			if (documentProvider != null) {
				IDocument document = documentProvider.getDocument(getEditorInput());
				if (document != null)
					document.removeDocumentListener(this);
			}
		}

		/*
		 * @see
		 * org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged(org
		 * .eclipse.jface.text.DocumentEvent)
		 */
		public void documentAboutToBeChanged(DocumentEvent event) {
			if (fOccurrencesFinderJob != null)
				fOccurrencesFinderJob.doCancel();
		}

		/*
		 * @see
		 * org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.
		 * jface.text.DocumentEvent)
		 */
		public void documentChanged(DocumentEvent event) {
		}

		/*
		 * @see org.eclipse.jface.text.ITextInputListener#
		 * inputDocumentAboutToBeChanged(org.eclipse.jface.text.IDocument,
		 * org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {
			if (oldInput == null)
				return;

			oldInput.removeDocumentListener(this);
		}

		/*
		 * @see
		 * org.eclipse.jface.text.ITextInputListener#inputDocumentChanged(org.
		 * eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
			if (newInput == null)
				return;
			newInput.addDocumentListener(this);
		}
	}

	class OccurrencesCollector
			implements ITypeScriptOccurrencesCollector, ITypeScriptAsynchCollector, ICancellationToken {

		private IDocument document;
		private List<Position> positions;
		private ITextSelection selection;
		private boolean canceled;

		public OccurrencesCollector() {
			this.positions = new ArrayList<Position>();
		}

		public void setDocument(IDocument document) {
			this.document = document;
		}

		@Override
		public void startCollect() {
			this.positions.clear();
		}

		@Override
		public void endCollect() {
			fOccurrencesFinderJob = new OccurrencesFinderJob(document, positions.toArray(new Position[0]), selection);
			fOccurrencesFinderJob.run(new NullProgressMonitor());
		}

		@Override
		public void addOccurrence(String file, int startLine, int startOffset, int endLine, int endOffset,
				boolean isWriteAccess) throws TypeScriptException {
			try {
				int start = document.getLineOffset(startLine - 1) + startOffset - 1;
				int end = document.getLineOffset(endLine - 1) + endOffset - 1;
				int offset = start;
				int length = end - start;
				positions.add(new Position(offset, length));
			} catch (BadLocationException e) {
				Trace.trace(Trace.SEVERE, "Error while getting TypeScript occurrences.", e);
			}
		}

		public void setSelection(ITextSelection selection) {
			this.selection = selection;
		}

		@Override
		public boolean isCancellationRequested() {
			return canceled;
		}

		@Override
		public void onError(TypeScriptException e) {
//			if (e instanceof TypeScriptNoContentAvailableException) {
//				// tsserver throws this error when the tsserver returns nothing
//				// Ignore this error
//			} else {
				Trace.trace(Trace.SEVERE, "Error while getting TypeScript occurrences.", e);
			//}
		}
	}

	/**
	 * Updates the occurrences annotations based on the current selection.
	 *
	 * @param selection
	 *            the text selection
	 * 
	 */
	private void updateOccurrenceAnnotations(ITextSelection selection) {
		if (fOccurrencesFinderJob != null)
			fOccurrencesFinderJob.cancel();

		if (!fMarkOccurrenceAnnotations) {
			return;
		}

		if (selection == null) {
			return;
		}

		final IDocument document = getSourceViewer().getDocument();
		if (document == null)
			return;

		if (occurrencesCollector == null) {
			occurrencesCollector = new OccurrencesCollector();
		}
		occurrencesCollector.setDocument(document);
		try {
			ITypeScriptFile tsFile = getTypeScriptFile(document);
			occurrencesCollector.setSelection(selection);
			tsFile.occurrences(selection.getOffset(), occurrencesCollector);
		} catch (Exception e) {
			Trace.trace(Trace.SEVERE, "Error while getting TypeScript occurrences.", e);
		}

	}

	private ITypeScriptFile getTypeScriptFile(IDocument document) throws CoreException, TypeScriptException {
		IResource file = EditorUtils.getResource(this);
		if (file != null) {
			IIDETypeScriptProject tsProject = TypeScriptResourceUtil.getTypeScriptProject(file.getProject());
			return tsProject.openFile(file, document);
		}
		IFileStore fs = EditorUtils.getFileStore(this);
		if (fs != null) {
			// TODO
		}
		return null;
	}

	public ITypeScriptFile getTypeScriptFile() throws CoreException, TypeScriptException {
		final IDocument document = getSourceViewer().getDocument();
		if (document == null) {
			return null;
		}
		return getTypeScriptFile(document);
	}

	protected void installOccurrencesFinder(boolean forceUpdate) {
		fMarkOccurrenceAnnotations = true;

		// fPostSelectionListenerWithAST= new ISelectionListenerWithAST() {
		// public void selectionChanged(IEditorPart part, ITextSelection
		// selection, JavaScriptUnit astRoot) {
		// updateOccurrenceAnnotations(selection, astRoot);
		// }
		// };
		// SelectionListenerWithASTManager.getDefault().addListener(this,
		// fPostSelectionListenerWithAST);
		if (forceUpdate && getSelectionProvider() != null) {
			fForcedMarkOccurrencesSelection = getSelectionProvider().getSelection();
			updateOccurrenceAnnotations((ITextSelection) fForcedMarkOccurrencesSelection);
		}

		if (fOccurrencesFinderJobCanceler == null) {
			fOccurrencesFinderJobCanceler = new OccurrencesFinderJobCanceler();
			fOccurrencesFinderJobCanceler.install();
		}
	}

	protected void uninstallOccurrencesFinder() {
		fMarkOccurrenceAnnotations = false;

		if (fOccurrencesFinderJob != null) {
			fOccurrencesFinderJob.cancel();
			fOccurrencesFinderJob = null;
		}

		if (fOccurrencesFinderJobCanceler != null) {
			fOccurrencesFinderJobCanceler.uninstall();
			fOccurrencesFinderJobCanceler = null;
		}

		occurrencesCollector = null;

		// if (fPostSelectionListenerWithAST != null) {
		// SelectionListenerWithASTManager.getDefault().removeListener(this,
		// fPostSelectionListenerWithAST);
		// fPostSelectionListenerWithAST= null;
		// }

		removeOccurrenceAnnotations();
	}

	private void removeOccurrenceAnnotations() {
		// fMarkOccurrenceModificationStamp=
		// IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
		// fMarkOccurrenceTargetRegion= null;

		IDocumentProvider documentProvider = getDocumentProvider();
		if (documentProvider == null)
			return;

		IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
		if (annotationModel == null || fOccurrenceAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (int i = 0, length = fOccurrenceAnnotations.length; i < length; i++)
					annotationModel.removeAnnotation(fOccurrenceAnnotations[i]);
			}
			fOccurrenceAnnotations = null;
		}
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel
	 *            the annotation model
	 * @return the annotation model's lock object
	 * 
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock = ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);

		// try {
		// //IDocument document = getSourceViewer().getDocument();
		// //setOutlinePageInput(getTypeScriptFile(document));
		// } catch (TypeScriptException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

	}
	// -------------- Outline

	@Override
	public Object getAdapter(@SuppressWarnings("rawtypes") Class key) {
		if (key.equals(IContentOutlinePage.class)) {
			return getOutlinePage();
		} else {
			return super.getAdapter(key);
		}
	}

	/**
	 * Gets an outline page
	 * 
	 * @return an outline page
	 */
	public TypeScriptContentOutlinePage getOutlinePage() {
		if (contentOutlinePage == null) {
			contentOutlinePage = new TypeScriptContentOutlinePage();
			fOutlineSelectionChangedListener.install(contentOutlinePage);
			IDocument document = getSourceViewer().getDocument();
			try {
				setOutlinePageInput(getTypeScriptFile(document));
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TypeScriptException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return contentOutlinePage;
	}

	private void setOutlinePageInput(ITypeScriptFile tsFile) {
		// try {
		contentOutlinePage.setInput(tsFile);
		// } catch (TypeScriptException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

	}

	/**
	 * React to changed selection.
	 *
	 * 
	 */
	protected void selectionChanged() {
		if (getSelectionProvider() == null) {
			return;
		}
	}

	protected void doSelectionChanged(SelectionChangedEvent event) {
		ISelection selection = event.getSelection();
		NavigationBarItem item = null;
		Iterator iter = ((IStructuredSelection) selection).iterator();
		while (iter.hasNext()) {
			Object o = iter.next();
			if (o instanceof NavigationBarItem) {
				item = (NavigationBarItem) o;
				break;
			}
		}

		setSelection(item, !isActivePart());

		ISelectionProvider selectionProvider = getSelectionProvider();
		if (selectionProvider == null)
			return;

		ISelection textSelection = selectionProvider.getSelection();
		if (!(textSelection instanceof ITextSelection))
			return;

		fForcedMarkOccurrencesSelection = textSelection;
		updateOccurrenceAnnotations((ITextSelection) textSelection);

	}

	/**
	 * Highlights and moves to a corresponding element in editor
	 * 
	 * @param reference
	 *            corresponding entity in editor
	 * @param moveCursor
	 *            if true, moves cursor to the reference
	 */
	private void setSelection(NavigationBarItem reference, boolean moveCursor) {
		if (reference == null) {
			return;
		}

		if (moveCursor) {
			markInNavigationHistory();
		}

		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null) {
			return;
		}
		StyledText textWidget = sourceViewer.getTextWidget();
		if (textWidget == null) {
			return;
		}
		try {
			Location start = reference.getSpans().get(0).getStart();
			Location end = reference.getSpans().get(0).getEnd();

			if (start == null || end == null)
				return;

			ITypeScriptFile tsFile = getTypeScriptFile();

			int offset = tsFile.getPosition(start);
			int length = tsFile.getPosition(end) - offset;

			if (offset < 0 || length < 0 || length > sourceViewer.getDocument().getLength()) {
				return;
			}
			textWidget.setRedraw(false);

			// Uncomment that if we wish to select only variable and not the
			// whole block.
			// but there is a bug with this code with
			// private a: string. it's the first 'a' (of private) which is
			// selected and not the second.
			// String documentPart = sourceViewer.getDocument().get(offset,
			// length);
			//
			// // Try to find name because position returns for whole block
			// String name = reference.getText();
			// if (name != null) {
			// int nameoffset = documentPart.indexOf(name);
			// if (nameoffset != -1) {
			// offset += nameoffset;
			// length = name.length();
			// }
			// }
			if (length > 0) {
				setHighlightRange(offset, length, moveCursor);
			}

			if (!moveCursor) {
				return;
			}

			if (offset > -1 && length > 0) {
				sourceViewer.revealRange(offset, length);
				// Selected region begins one index after offset
				sourceViewer.setSelectedRange(offset, length);
				markInNavigationHistory();
			}
		} catch (Exception e) {

		} finally {
			textWidget.setRedraw(true);
		}
	}

}
