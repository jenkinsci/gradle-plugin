package hudson.plugins.gradle.injection

import hudson.model.labels.LabelAtom
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class InjectionUtilTest extends Specification {

    def 'inject based on node labels - #labels - #disabledNodes - #enabledNodes'(List<String> labels,
                                                                                 List<String> disabledNodes,
                                                                                 List<String> enabledNodes,
                                                                                 boolean shouldInject) {
        given:
        def assignedLabels = { labels?.collect { new LabelAtom(it) }?.toSet() }

        expect:
        InjectionUtil.isInjectionEnabledForNode(assignedLabels, disabledNodes?.toSet(), enabledNodes?.toSet()) == shouldInject

        where:
        labels                | disabledNodes  | enabledNodes   || shouldInject
        ['foo']               | null           | null           || true
        []                    | null           | null           || true
        null                  | null           | null           || true
        ['foo', 'daz']        | ['bar']        | null           || true
        ['foo', 'bar']        | ['foo']        | null           || false
        ['foo', 'bar']        | ['daz', 'foo'] | null           || false
        ['foo', 'bar']        | null           | ['daz']        || false
        ['foo', 'bar', 'daz'] | null           | ['daz']        || true
        []                    | ['bar']        | null           || true
        []                    | null           | ['bar']        || false
        ['bar']               | null           | ['foo', 'bar'] || true
        ['daz']               | null           | ['foo', 'bar'] || false
    }
}
