package co.worklytics.psoxy.rules;

public interface RuleSet {

    @Deprecated // migrate to use transform-level ids
    String getDefaultScopeIdForSource();
}
