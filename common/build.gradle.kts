// Minecraft に依存しないコアロジック。
// 1.21.1 (Java 21) から 26.x (Java 25) まで同じバイトコードを同梱するため、Java 21 でコンパイルする。
// Gson は全対象バージョンの Minecraft が同梱しているので compileOnly で参照し、テストでは実体を使う。
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
