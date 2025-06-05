package co.worklytics.psoxy.gateway.output;

public interface SideOutputFactory<T extends SideOutput> {

    T create(OutputLocation location);

}
