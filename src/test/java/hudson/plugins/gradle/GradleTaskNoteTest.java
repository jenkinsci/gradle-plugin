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
	public void testAnnotateTask() {
		assertEquals(":<b class=gradle-task>TASK</b>", annotate(":TASK"));
	}

	@Test
	public void testAnnotateTaskWithSuffixWords() {
		assertEquals(":<b class=gradle-task>TASK</b> UP-TO-DATE",
				annotate(":TASK UP-TO-DATE"));
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
