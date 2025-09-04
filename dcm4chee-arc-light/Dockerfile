FROM dcm4che/dcm4chee-arc-psql:5.33.1

USER root

# remove the default deployments shipped in the base image
RUN rm -f /docker-entrypoint.d/deployments/dcm4chee-arc-ear-5.33.1-psql.ear \
    && rm -f /docker-entrypoint.d/deployments/dcm4chee-arc-ui2-5.33.1.war || true

# add YOUR custom ear
COPY --chown=wildfly:wildfly dcm4chee-arc-ear-5.34.1.ear \
     /docker-entrypoint.d/deployments/dcm4chee-arc-ear-5.34.1.ear

# (optional) if you have a custom UI:
COPY --chown=wildfly:wildfly dcm4chee-arc-ui2-5.33.1.war \
     /docker-entrypoint.d/deployments/dcm4chee-arc-ui2-5.33.1.war





