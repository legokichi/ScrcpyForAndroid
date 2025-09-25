# Docker image for building the ScrcpyForAndroid2 Android app
FROM eclipse-temurin:17-jdk-jammy

# Configure Android SDK paths
ARG ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT} \
    ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

# Install required packages
RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        wget \
        git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android command line tools
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip && \
    unzip commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip

# Install Android SDK components required for this project
RUN yes | sdkmanager --licenses && \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0"

WORKDIR /workspace

# Pre-copy Gradle wrapper metadata, then project files
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle build.gradle gradle.properties ./
COPY app app

# Ensure wrapper is executable and warm up dependencies
RUN chmod +x gradlew && ./gradlew --no-daemon help

CMD ["./gradlew", "--no-daemon", "assembleDebug"]
