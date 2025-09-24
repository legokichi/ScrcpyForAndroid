FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jdk \
        wget \
        unzip \
        curl \
        git \
        zip \
        libglu1-mesa \
        libc6-dev \
        lib32stdc++6 \
        lib32z1 \
    && rm -rf /var/lib/apt/lists/*

# Install Android command line tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-11076708_latest.zip \
    && mv cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm commandlinetools-linux-11076708_latest.zip

# Accept licenses and install required SDK components
RUN yes | sdkmanager --licenses \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-31" \
        "build-tools;31.0.0"

WORKDIR /workspace

# Default command runs the Android app build
CMD ["./gradlew", "assembleDebug"]
