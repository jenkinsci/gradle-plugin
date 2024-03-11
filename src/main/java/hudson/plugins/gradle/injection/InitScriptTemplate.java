package hudson.plugins.gradle.injection;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InitScriptTemplate {

    private static final Map<String, String> REPLACEMENTS;

    static {
        Map<String, String> replacements = new HashMap<>();
        Arrays.stream(InitScriptVariables.values())
            .filter(v -> !v.getTemplateName().isEmpty())
            .forEach(v -> replacements.put(v.getTemplateName(), v.getTemplateTargetName()));
        REPLACEMENTS = Collections.unmodifiableMap(replacements);
    }

    public static String replacePlaceholders(String template, Map<String, String> replacements) {
        Pattern pattern = Pattern.compile("getInputParam\\([\"']([^\"']+)[\"']\\)");
        Matcher matcher = pattern.matcher(template);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String replacement = replacements.get(matcher.group(1));
            if (replacement == null) {
                throw new IllegalArgumentException("Placeholder not found: " + matcher.group(1));
            }
            matcher.appendReplacement(buffer, "getInputParam('" + replacement + "')");
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }
    public static String loadAndReplace(String initScriptName) {
        return replacePlaceholders(CopyUtil.readResource(initScriptName), REPLACEMENTS);
    }
}
