package co.worklytics.psoxy.rules;

public interface Rules {

    @Deprecated // migrate to use transform-level ids
    String getDefaultScopeIdForSource();
}
