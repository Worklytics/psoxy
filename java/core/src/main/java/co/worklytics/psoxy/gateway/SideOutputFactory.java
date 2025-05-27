package co.worklytics.psoxy.gateway;

public interface SideOutputFactory<T extends SideOutput> {

    T create(String bucket);

}
