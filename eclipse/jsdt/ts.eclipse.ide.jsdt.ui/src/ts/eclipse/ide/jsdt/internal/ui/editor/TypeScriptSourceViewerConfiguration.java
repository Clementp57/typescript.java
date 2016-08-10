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

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.AbstractInformationControlManager;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.jsdt.internal.ui.JavaScriptPlugin;
import org.eclipse.wst.jsdt.internal.ui.text.ContentAssistPreference;
import org.eclipse.wst.jsdt.internal.ui.text.java.ContentAssistProcessor;
import org.eclipse.wst.jsdt.internal.ui.text.java.JavaStringAutoIndentStrategy;
import org.eclipse.wst.jsdt.internal.ui.text.java.SmartSemicolonAutoEditStrategy;
import org.eclipse.wst.jsdt.internal.ui.text.javadoc.JavaDocAutoIndentStrategy;
import org.eclipse.wst.jsdt.ui.text.IColorManager;
import org.eclipse.wst.jsdt.ui.text.IJavaScriptPartitions;
import org.eclipse.wst.jsdt.ui.text.JavaScriptSourceViewerConfiguration;

import ts.eclipse.ide.jsdt.internal.ui.editor.contentassist.TypeScriptCompletionProcessor;
import ts.eclipse.ide.jsdt.internal.ui.editor.contentassist.TypeScriptJavadocCompletionProcessor;
import ts.eclipse.ide.jsdt.internal.ui.editor.format.TypeScriptContentFormatter;
import ts.eclipse.ide.jsdt.ui.actions.ITypeScriptEditorActionDefinitionIds;
import ts.eclipse.ide.ui.outline.TypeScriptElementProvider;
import ts.eclipse.ide.ui.outline.TypeScriptQuickOutlineDialog;
import ts.eclipse.ide.ui.utils.EditorUtils;
import ts.resources.ITypeScriptFile;

/**
 * Extension of JSDT {@link JavaScriptSourceViewerConfiguration}
 *
 */
public class TypeScriptSourceViewerConfiguration extends JavaScriptSourceViewerConfiguration {

	public TypeScriptSourceViewerConfiguration(IColorManager colorManager, IPreferenceStore preferenceStore,
			ITextEditor editor, String partitioning) {
		super(colorManager, preferenceStore, editor, partitioning);
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		if (getEditor() != null) {

			ContentAssistant assistant = new ContentAssistant();
			assistant.enableColoredLabels(true);
			assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));

			assistant.setRestoreCompletionProposalSize(getSettings("completion_proposal_size")); //$NON-NLS-1$

			IContentAssistProcessor javaProcessor = new TypeScriptCompletionProcessor(getEditor(), assistant,
					IDocument.DEFAULT_CONTENT_TYPE);
			assistant.setContentAssistProcessor(javaProcessor, IDocument.DEFAULT_CONTENT_TYPE);

			ContentAssistProcessor singleLineProcessor = new TypeScriptCompletionProcessor(getEditor(), assistant,
					IJavaScriptPartitions.JAVA_SINGLE_LINE_COMMENT);
			assistant.setContentAssistProcessor(singleLineProcessor, IJavaScriptPartitions.JAVA_SINGLE_LINE_COMMENT);

			ContentAssistProcessor stringProcessor = new TypeScriptCompletionProcessor(getEditor(), assistant,
					IJavaScriptPartitions.JAVA_STRING);
			assistant.setContentAssistProcessor(stringProcessor, IJavaScriptPartitions.JAVA_STRING);

			assistant.setContentAssistProcessor(stringProcessor, IJavaScriptPartitions.JAVA_CHARACTER);

			ContentAssistProcessor multiLineProcessor = new TypeScriptCompletionProcessor(getEditor(), assistant,
					IJavaScriptPartitions.JAVA_MULTI_LINE_COMMENT);
			assistant.setContentAssistProcessor(multiLineProcessor, IJavaScriptPartitions.JAVA_MULTI_LINE_COMMENT);

			ContentAssistProcessor javadocProcessor = new TypeScriptJavadocCompletionProcessor(getEditor(), assistant);
			assistant.setContentAssistProcessor(javadocProcessor, IJavaScriptPartitions.JAVA_DOC);

			ContentAssistPreference.configure(assistant, fPreferenceStore);

			assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
			assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));

			return assistant;
		}
		return null;
	}

	/**
	 * Returns the settings for the given section.
	 *
	 * @param sectionName
	 *            the section name
	 * @return the settings
	 *
	 */
	private IDialogSettings getSettings(final String sectionName) {
		IDialogSettings settings = JavaScriptPlugin.getDefault().getDialogSettings().getSection(sectionName);
		if (settings == null)
			settings = JavaScriptPlugin.getDefault().getDialogSettings().addNewSection(sectionName);

		return settings;
	}

	@Override
	public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
		return new TypeScriptContentFormatter(EditorUtils.getResource(getEditor()));
	}

	/**
	 * Returns the outline presenter which will determine and shown information
	 * requested for the current cursor position.
	 *
	 * @param sourceViewer
	 *            the source viewer to be configured by this configuration
	 * @param doCodeResolve
	 *            a boolean which specifies whether code resolve should be used
	 *            to compute the JavaScript element
	 * @return an information presenter
	 *
	 */
	@Override
	public IInformationPresenter getOutlinePresenter(final ISourceViewer sourceViewer, final boolean doCodeResolve) {
		InformationPresenter presenter = null;
		if (doCodeResolve) {
			// presenter = new
			// InformationPresenter(getOutlinePresenterControlCreator(sourceViewer,
			// ITypeScriptEditorActionDefinitionIds.OPEN_STRUCTURE));
			return null;
		} else {
			presenter = new InformationPresenter(
					getOutlinePresenterControlCreator(sourceViewer, ITypeScriptEditorActionDefinitionIds.SHOW_OUTLINE));
		}
		presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
		presenter.setAnchor(AbstractInformationControlManager.ANCHOR_GLOBAL);
		IInformationProvider provider = new TypeScriptElementProvider(getEditor());
		presenter.setInformationProvider(provider, IDocument.DEFAULT_CONTENT_TYPE);
		presenter.setInformationProvider(provider, IJavaScriptPartitions.JAVA_DOC);
		presenter.setInformationProvider(provider, IJavaScriptPartitions.JAVA_MULTI_LINE_COMMENT);
		presenter.setInformationProvider(provider, IJavaScriptPartitions.JAVA_SINGLE_LINE_COMMENT);
		presenter.setInformationProvider(provider, IJavaScriptPartitions.JAVA_STRING);
		presenter.setInformationProvider(provider, IJavaScriptPartitions.JAVA_CHARACTER);
		presenter.setSizeConstraints(50, 20, true, false);
		return presenter;
	}

	/**
	 * Returns the outline presenter control creator. The creator is a factory
	 * creating outline presenter controls for the given source viewer. This
	 * implementation always returns a creator for
	 * <code>JavaOutlineInformationControl</code> instances.
	 *
	 * @param sourceViewer
	 *            the source viewer to be configured by this configuration
	 * @param commandId
	 *            the ID of the command that opens this control
	 * @return an information control creator
	 *
	 */
	private IInformationControlCreator getOutlinePresenterControlCreator(final ISourceViewer sourceViewer,
			final String commandId) {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(final Shell parent) {
				int shellStyle = SWT.RESIZE;
				try {
					return new TypeScriptQuickOutlineDialog(parent, shellStyle, getTypeScriptFile());
				} catch (Exception e) {
					return null;
				}
			}
		};
	}

	@Override
	public int getTabWidth(ISourceViewer sourceViewer) {
		ITypeScriptFile tsFile = getTypeScriptFile();
		if (tsFile == null) {
			return super.getTabWidth(sourceViewer);
		}
		boolean convertTabsToSpaces = tsFile.getFormatOptions().getConvertTabsToSpaces();
		if (convertTabsToSpaces) {
			// indentSize
			return tsFile.getFormatOptions().getIndentSize();
		}
		// tabSize
		return tsFile.getFormatOptions().getTabSize();

	}

	private ITypeScriptFile getTypeScriptFile() {
		try {
			return ((TypeScriptEditor) getEditor()).getTypeScriptFile();
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String[] getIndentPrefixes(final ISourceViewer sourceViewer, final String contentType) {
		ITypeScriptFile tsFile = getTypeScriptFile();
		if (tsFile != null) {

			final int tabWidth = tsFile.getFormatOptions().getTabSize();
			final int indentWidth = tsFile.getFormatOptions().getIndentSize();
			boolean allowTabs = tabWidth <= indentWidth;

			boolean useSpaces = tsFile.getFormatOptions().getConvertTabsToSpaces();

			Assert.isLegal(allowTabs || useSpaces);

			if (!allowTabs) {
				char[] spaces = new char[indentWidth];
				Arrays.fill(spaces, ' ');
				return new String[] { new String(spaces), "" }; //$NON-NLS-1$
			} else if (!useSpaces)
				return getIndentPrefixesForTab(tabWidth);
			else
				return getIndentPrefixesForSpaces(tabWidth);
		}
		return super.getIndentPrefixes(sourceViewer, contentType);

	}

	/**
	 * Computes and returns the indent prefixes for space indentation and the
	 * given <code>tabWidth</code>.
	 *
	 * @param tabWidth
	 *            the display tab width
	 * @return the indent prefixes
	 * @see #getIndentPrefixes(ISourceViewer, String)
	 *
	 */
	private String[] getIndentPrefixesForSpaces(final int tabWidth) {
		String[] indentPrefixes = new String[tabWidth + 2];
		indentPrefixes[0] = getStringWithSpaces(tabWidth);

		for (int i = 0; i < tabWidth; i++) {
			String spaces = getStringWithSpaces(i);
			if (i < tabWidth)
				indentPrefixes[i + 1] = spaces + '\t';
			else
				indentPrefixes[i + 1] = new String(spaces);
		}

		indentPrefixes[tabWidth + 1] = ""; //$NON-NLS-1$

		return indentPrefixes;
	}

	/**
	 * Creates and returns a String with <code>count</code> spaces.
	 *
	 * @param count
	 *            the space count
	 * @return the string with the spaces
	 *
	 */
	private String getStringWithSpaces(final int count) {
		char[] spaceChars = new char[count];
		Arrays.fill(spaceChars, ' ');
		return new String(spaceChars);
	}
	
	@Override
	public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
		String partitioning = getConfiguredDocumentPartitioning(sourceViewer);
		if (IJavaScriptPartitions.JAVA_DOC.equals(contentType) 
					|| IJavaScriptPartitions.JAVA_MULTI_LINE_COMMENT.equals(contentType) 
					|| IJavaScriptPartitions.JAVASCRIPT_TEMPLATE_LITERAL.equals(contentType))
		{
			return new IAutoEditStrategy[]{new JavaDocAutoIndentStrategy(partitioning)};
		}
		else if (IJavaScriptPartitions.JAVA_STRING.equals(contentType))
			return new IAutoEditStrategy[]{new SmartSemicolonAutoEditStrategy(partitioning), new JavaStringAutoIndentStrategy(partitioning)};
		else if (IJavaScriptPartitions.JAVA_CHARACTER.equals(contentType) || IDocument.DEFAULT_CONTENT_TYPE.equals(contentType))
			return new IAutoEditStrategy[]{new SmartSemicolonAutoEditStrategy(partitioning), new TypeScriptAutoIndentStrategy(partitioning, getTypeScriptFile(), sourceViewer)};
		else
			return new IAutoEditStrategy[]{new TypeScriptAutoIndentStrategy(partitioning, getTypeScriptFile(), sourceViewer)};
	}
}
