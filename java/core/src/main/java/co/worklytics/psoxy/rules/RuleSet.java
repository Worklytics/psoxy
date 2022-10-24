package co.worklytics.psoxy.rules;


public interface RuleSet extends com.avaulta.gateway.rules.RuleSet {

    @Deprecated // migrate to use transform-level ids
    String getDefaultScopeIdForSource();
}
