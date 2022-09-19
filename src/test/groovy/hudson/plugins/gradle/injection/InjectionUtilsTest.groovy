package hudson.plugins.gradle.injection

import com.google.common.collect.Sets
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class InjectionUtilsTest extends Specification {

    def 'isInjectionEnabledForNode - #labels - #disabledNodes - #enabledNodes'(List<String> labels, String disabledNodes, String enabledNodes, boolean shouldInject) {
        expect:
        InjectionUtils.isInjectionEnabledForNode(Sets.newHashSet(labels), disabledNodes, enabledNodes) == shouldInject

        where:
        labels                | disabledNodes | enabledNodes | shouldInject
        ['foo']               | null          | null         | true
        []                    | null          | null         | true
        []                    | ''            | ''           | true
        []                    | null          | null         | true
        ['foo', 'daz']        | 'bar'         | null         | true
        ['foo', 'bar']        | 'foo'         | null         | false
        ['foo', 'bar']        | 'daz,foo'     | null         | false
        ['foo', 'bar']        | ''            | 'daz'        | false
        ['foo', 'bar', 'daz'] | ''            | 'daz'        | true
        []                    | 'bar'         | ''           | true
        []                    | ''            | 'bar'        | false
        ['bar']               | null          | 'foo,bar'    | true
        ['daz']               | ''            | 'foo,bar'    | false
    }
}
