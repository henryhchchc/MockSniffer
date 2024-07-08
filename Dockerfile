FROM "maven:3-amazoncorretto-11" as builder

COPY MockSniffer /mock_sniffer
RUN cd /mock_sniffer && mvn package

FROM "openjdk:8" as jdk8

FROM "openjdk:11-jdk"

COPY --from=jdk8 "/usr/local/openjdk-8" "/openjdk-8"
COPY --from=builder /mock_sniffer/target/mocksniffer.jar /mocksniffer.jar

ENTRYPOINT [ "java", "-jar", "mocksniffer.jar" ]
