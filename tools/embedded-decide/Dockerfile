# Image for CLASSIFY_IMPLEMENTATION=embedded-decide only.
# Weights are not copied here. The entrypoint pulls
# {SHARED_RESOURCE_PATH}/decide/strands-decider-2B-qwen3.5-v1-2610/<revision>/
# from REMOTE_RESOURCE_BUCKET and loads it with HF_HUB_OFFLINE=1.
#
# Build from the repo root:
#   docker build -f tools/embedded-decide/Dockerfile .

FROM public.ecr.aws/lambda/java:25

USER root
RUN dnf install -y python3 python3-pip awscli \
    && python3 -m pip install --no-cache-dir strands-decider \
    && dnf clean all

COPY tools/embedded-decide/entrypoint.sh /opt/decide/entrypoint.sh
RUN chmod 755 /opt/decide/entrypoint.sh

ENTRYPOINT ["/opt/decide/entrypoint.sh"]
CMD ["/lambda-entrypoint.sh"]
