FROM centos:centos7

RUN yum update -y && \
    yum clean all
RUN yum install -y wget git nano glibc-langpack-en
ENV LANG en_US.UTF-8

CMD [ "/bin/bash" ]