package hudson.plugins.gradle;

import static org.junit.Assert.*;
import hudson.MarkupText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GradleTaskNoteTest {

	private boolean enabled;

	@Before
	public void setUp() {
		enabled = GradleTaskNote.ENABLED;
	}

	@After
	public void tearDown() {
		// Restore the original setting.
		GradleTaskNote.ENABLED = enabled;
	}

	@Test
	public void annotate() {
		assertEquals(":<b class=gradle-task>TASK</b>", annotate(":TASK"));
	}

	@Test
	public void annotateWithUpToDate() {
		assertEquals(
				":<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>UP-TO-DATE</span>",
				annotate(":TASK UP-TO-DATE"));
	}

	@Test
	public void annotateWithSkipped() {
		assertEquals(
				":<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>SKIPPED</span>",
				annotate(":TASK SKIPPED"));
	}

	@Test
	public void annotateWithNonProgressStatus() {
		assertEquals(":<b class=gradle-task>TASK</b> DUMMY",
				annotate(":TASK DUMMY"));
	}

	@Test
	public void annotateWithErrors() {
		assertEquals(":::: ERRORS", annotate(":::: ERRORS"));
	}

	@Test
	public void annotateWithMultiProject() {
		assertEquals(":<b class=gradle-task>PARENT:TASK</b>",
				annotate(":PARENT:TASK"));
	}

	@Test
	public void annotateWithProgressStatusAndMultiProject() {
		assertEquals(
				":<b class=gradle-task>PARENT:TASK</b> <span class=gradle-task-progress-status>UP-TO-DATE</span>",
				annotate(":PARENT:TASK UP-TO-DATE"));
	}

	@Test
	public void testDisabled() {
		GradleTaskNote.ENABLED = false;
		assertEquals("TASK:", annotate("TASK:"));
	}

	private String annotate(String text) {
		MarkupText markupText = new MarkupText(text);
		new GradleTaskNote().annotate(new Object(), markupText, 0);
		return markupText.toString(true);
	}

}
