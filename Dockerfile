# IITM Explainable Slotbook: engine and CLI image.
#
#   docker build -t slotbook .
#   docker run --rm slotbook                       # solves data/feasible and prints the slotbook
#   docker run --rm slotbook --help                # all options (see also man/slotbook.1)
#
# Two stages: Maven builds the classes and collects the runtime libraries;
# the final image holds only a JRE, the application and the sample datasets.
# No credentials are needed at build or run time, and none are copied in.

# ---- build ------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

# Resolve dependencies first, so editing source code doesn't re-download them.
COPY pom.xml .
RUN mvn -B -ntp -q dependency:resolve

COPY src ./src
# Tests run in CI before this image is built; skip compiling them here.
RUN mvn -B -ntp -q -Dmaven.test.skip=true package \
 && mvn -B -ntp -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib \
 && cp target/explainable-slotbook-*.jar target/app.jar

# The image redistributes these libraries, so it carries their licences (CS5013 Module 7).
# The list is read from each dependency's own POM, and the licence texts are downloaded.
ARG LICENSE_PLUGIN=org.codehaus.mojo:license-maven-plugin:2.4.0
RUN mvn -B -ntp -q ${LICENSE_PLUGIN}:add-third-party ${LICENSE_PLUGIN}:download-licenses \
        -Dlicense.excludedScopes=test,provided,system -Dlicense.errorRemedy=warn \
 && mkdir -p target/licenses \
 && cp target/generated-sources/license/THIRD-PARTY.txt target/licenses/ \
 && cp -r target/generated-resources/licenses target/licenses/texts \
 && cp target/generated-resources/licenses.xml target/licenses/

# ---- runtime ----------------------------------------------------------------
# Ubuntu-based (glibc), because OR-Tools' native library does not run on Alpine.
FROM eclipse-temurin:17-jre-jammy

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="IITM Explainable Slotbook" \
      org.opencontainers.image.description="Builds and explains an IITM course slotbook (UniTime cpsolver, CP-SAT reference)" \
      org.opencontainers.image.source="https://github.com/lazarsalt1441/CS5013-PROJECT" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.authors="Nishanth S (EE23B049), Sparsh Gupta (EE23B117)"

# Unprivileged user. Pass -u "$(id -u):$(id -g)" when mounting an output folder.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin slotbook
WORKDIR /app

COPY --from=build /src/target/lib/ /app/lib/
COPY --from=build /src/target/app.jar /app/app.jar
COPY --from=build /src/target/licenses/ /app/licenses/
COPY THIRD_PARTY_NOTICES.md /app/licenses/
COPY data/ /app/data/
COPY man/ /app/man/
COPY README.md MANUAL.md /app/
COPY docker/entrypoint.sh /app/bin/slotbook
RUN chmod 755 /app/bin/slotbook

ENV GIT_SHA=${GIT_SHA}
USER slotbook

# CLI by default; "serve" (or SLOTBOOK_SERVE=1) starts the solver web service on $PORT (default 8080).
ENV SLOTBOOK_BASE=/app/data/feasible
EXPOSE 8080
ENTRYPOINT ["/app/bin/slotbook"]
# Default run: solve the main sample dataset and print the slotbook.
CMD ["--input", "data/feasible", "--output", "/tmp/slotbook-output"]
