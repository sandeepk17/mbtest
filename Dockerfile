FROM centos:centos7

RUN yum update -y && \
    yum clean all
RUN yum install -y wget git nano glibc-langpack-en
ENV LANG en_US.UTF-8

ENV JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk" \
    JAVA="/usr/lib/java-1.8.0-openjdk/bin/java" \
    TIME_ZONE="Europe/Paris"

RUN set -x \
    && yum update -y \
    && yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel wget iputils nc vim libcurl\
    && ln -snf /usr/share/zoneinfo/$TIME_ZONE /etc/localtime && echo '$TIME_ZONE' > /etc/timezone \
    && yum clean all

# Set Timezone
ENV TZ=Europe/Paris
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

CMD [ "/bin/bash" ]