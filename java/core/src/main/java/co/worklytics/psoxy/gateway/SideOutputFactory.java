package co.worklytics.psoxy.gateway;

public interface SideOutputFactory<T extends SideOutput> {

    // TODO: so this implemention is somewhat specific to S3 / GCS ... how to generalize?
    // OutputOptions imlementation?
    T create(String bucket, String pathPrefix);

}
