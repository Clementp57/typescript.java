package ts.eclipse.ide.jsdt.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.Position;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RenameProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import ts.client.TextSpan;
import ts.client.rename.RenameInfo;
import ts.client.rename.RenameResponseBody;
import ts.client.rename.SpanGroup;
import ts.eclipse.ide.core.utils.WorkbenchResourceUtil;
import ts.eclipse.ide.jsdt.core.JSDTTypeScriptCorePlugin;
import ts.eclipse.ide.ui.utils.EditorUtils;
import ts.resources.ITypeScriptFile;

public class TypeScriptRenameProcessor extends RenameProcessor {

	private static final String TEXT_TYPE = "ts";

	private static final String ID = "ts.eclipse.ide.core.refactoring.rename";

	private final ITypeScriptFile tsFile;
	private final int offset;
	private final String oldName;

	private String newName;
	private boolean findInComments;
	private boolean findInStrings;

	private RenameResponseBody rename;

	public TypeScriptRenameProcessor(ITypeScriptFile tsFile, int offset, String oldName) {
		this.tsFile = tsFile;
		this.offset = offset;
		this.oldName = oldName;
	}

	@Override
	public Object[] getElements() {
		return null;
	}

	@Override
	public String getIdentifier() {
		return ID;
	}

	@Override
	public String getProcessorName() {
		return RefactoringMessages.TypeScriptRenameProcessor_name;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return true;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		rename = null;
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		RefactoringStatus status = new RefactoringStatus();
		// Consume "rename" tsserver command.
		try {
			rename = tsFile.rename(offset, isFindInComments(), isFindInStrings()).get(1000, TimeUnit.MILLISECONDS);
			RenameInfo info = rename.getInfo();
			if (!info.isCanRename()) {
				// Refactoring cannot be done.
				status.addError(info.getLocalizedErrorMessage());
			}
		} catch (Exception e) {
			status.addError(e.getMessage());
		}
		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			if (rename == null) {
				throw new CoreException(new Status(IStatus.ERROR, JSDTTypeScriptCorePlugin.PLUGIN_ID,
						"TypeScript rename cannot be null"));
			}

			// Convert TypeScript changes to Eclipse changes.
			List<SpanGroup> locs = rename.getLocs();
			List<Change> fileChanges = new ArrayList<>();
			for (SpanGroup loc : locs) {
				IFile file = WorkbenchResourceUtil.findFileFromWorkspace(loc.getFile());
				TextFileChange change = new TextFileChange(file.getName(), file);
				change.setEdit(new MultiTextEdit());
				change.setTextType(TEXT_TYPE);

				List<TextSpan> spans = loc.getLocs();
				for (TextSpan textSpan : spans) {
					Position position = EditorUtils.getPosition(file, textSpan);
					ReplaceEdit edit = new ReplaceEdit(position.offset, position.length, this.newName);
					change.addEdit(edit);
				}
				fileChanges.add(change);
			}
			return new CompositeChange(RefactoringMessages.TypeScriptRenameProcessor_change_name,
					fileChanges.toArray(new Change[fileChanges.size()]));
		} catch (CoreException | OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(
					new Status(IStatus.ERROR, JSDTTypeScriptCorePlugin.PLUGIN_ID, "Error while rename", e));
		}
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants)
			throws CoreException {
		return null;
	}

	public String getOldName() {
		return this.oldName;
	}

	public void setNewName(String newName) {
		Assert.isNotNull(newName);
		this.newName = newName;
	}

	public int getSaveMode() {
		return RefactoringSaveHelper.SAVE_ALL_ALWAYS_ASK;
	}

	public boolean isFindInComments() {
		return findInComments;
	}

	public void setFindInComments(boolean findInComments) {
		this.findInComments = findInComments;
	}

	public boolean isFindInStrings() {
		return findInStrings;
	}

	public void setFindInStrings(boolean findInStrings) {
		this.findInStrings = findInStrings;
	}

}
