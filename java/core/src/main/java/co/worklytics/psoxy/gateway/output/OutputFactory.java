package co.worklytics.psoxy.gateway.output;

public interface OutputFactory<T extends Output> {

    T create(OutputLocation outputLocation);

    boolean supports(OutputLocation outputLocation);
}
