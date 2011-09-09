package hudson.plugins.gradle;

import static org.junit.Assert.*;
import hudson.MarkupText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GradleTargetNoteTest {

	private boolean enabled;

	@Before
	public void setUp() {
		enabled = GradleTargetNote.ENABLED;
	}

	@After
	public void tearDown() {
		// Restore the original setting.
		GradleTargetNote.ENABLED = enabled;
	}

	@Test
	public void testAnnotateTarget() {
		assertEquals(":<b class=gradle-target>TARGET</b>", annotate(":TARGET"));
	}

	@Test
	public void testAnnotateTargetWithSuffixWords() {
		assertEquals(":<b class=gradle-target>TARGET</b> UP-TO-DATE",
				annotate(":TARGET UP-TO-DATE"));
	}

	@Test
	public void testDisabled() {
		GradleTargetNote.ENABLED = false;
		assertEquals("TARGET:", annotate("TARGET:"));
	}

	private String annotate(String text) {
		MarkupText markupText = new MarkupText(text);
		new GradleTargetNote().annotate(new Object(), markupText, 0);
		return markupText.toString(true);
	}

}
