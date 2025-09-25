# syntax=docker/dockerfile:1.7

ARG BASE_IMAGE=ubuntu:22.04
FROM ${BASE_IMAGE}

ARG DEBIAN_FRONTEND=noninteractive
ARG ANDROID_SDK_ROOT=/opt/android-sdk
ARG CMDLINE_TOOLS_VERSION=13114758
ARG BUILD_TOOLS_VERSION=35.0.0
ARG PLATFORM_VERSION=android-35

ENV ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    ANDROID_HOME=${ANDROID_SDK_ROOT} \
    PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    GRADLE_USER_HOME=/opt/gradle

RUN apt-get update && \
    apt-get install --yes --no-install-recommends \
        curl \
        unzip \
        zip \
        openjdk-17-jdk \
        git \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT} ${GRADLE_USER_HOME}

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -o /tmp/cmdline-tools.zip -L https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses && \
    sdkmanager --install \
        "platform-tools" \
        "build-tools;${BUILD_TOOLS_VERSION}" \
        "platforms;${PLATFORM_VERSION}"

WORKDIR /workspace

COPY . /workspace

RUN ./gradlew --version

RUN ./gradlew assembleDebug

CMD ["/bin/bash"]
