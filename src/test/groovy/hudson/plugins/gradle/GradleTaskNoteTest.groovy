package hudson.plugins.gradle

import hudson.MarkupText
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class GradleTaskNoteTest extends Specification {

    def setup() {
        GradleTaskNote.ENABLED = true
    }

    def "annotate task '#consoleOutput'"() {
        expect:
        annotate(consoleOutput) == annotatedOutput

        where:
        consoleOutput             | annotatedOutput
        ':TASK'                   | ':<b class=gradle-task>TASK</b>'
        ':TASK UP-TO-DATE'        | ':<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>UP-TO-DATE</span>'
        ':TASK SKIPPED'           | ':<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>SKIPPED</span>'
        ':TASK FROM-CACHE'        | ':<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>FROM-CACHE</span>'
        ':TASK NO-SOURCE'         | ':<b class=gradle-task>TASK</b> <span class=gradle-task-progress-status>NO-SOURCE</span>'
        ':TASK DUMMY'             | ':<b class=gradle-task>TASK</b> DUMMY'
        ':::: ERRORS'             | ':::: ERRORS'
        ':PARENT:TASK'            | ':<b class=gradle-task>PARENT:TASK</b>'
        ':PARENT:TASK UP-TO-DATE' | ':<b class=gradle-task>PARENT:TASK</b> <span class=gradle-task-progress-status>UP-TO-DATE</span>'
    }

    void 'no annotation when disabled'() {
        when:
        GradleTaskNote.ENABLED = false
        then:
        annotate('TASK:') == 'TASK:'
    }

    private static String annotate(String text) {
        MarkupText markupText = new MarkupText(text)
        new GradleTaskNote().annotate(new Object(), markupText, 0)
        return markupText.toString(true)
    }

}
