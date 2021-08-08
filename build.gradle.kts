import org.jetbrains.changelog.date
import org.jetbrains.changelog.markdownToHTML

plugins {
    java
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.intellij") version "1.1.4"
    id("org.jetbrains.changelog") version "1.2.1"
}

group = "top.shenluw.intellij"
version = "0.2.0"

val dubboVersion = "3.0.1"

repositories {
    maven { setUrl("https://maven.aliyun.com/nexus/content/groups/public/") }
    mavenCentral()
}
fun properties(key: String) = project.findProperty(key).toString()

dependencies {
//    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
//    implementation "commons-collections:commons-collections:3.2.2"
//    implementation "commons-net:commons-net:3.6"

    implementation("org.apache.dubbo:dubbo:$dubboVersion") {
        exclude(group = "org.springframework")
        exclude(module = "gson")
        exclude(group = "org.yaml")
        // 不能排除 ClassLoader会出现问题
        // exclude module: "javassist"
    }
    implementation("redis.clients:jedis:3.1.+") {
        exclude(group = "org.slf4j")
    }

    implementation("com.ecwid.consul:consul-api:1.4.+") {
        exclude(module = "gson")
        exclude(module = "httpclient")
        exclude(module = "httpcore")
    }
//    implementation("org.apache.dubbo:dubbo-registry-etcd3:$dubboVersion")
//    implementation("org.apache.dubbo:dubbo-registry-nacos:$dubboVersion")
//    implementation("org.apache.dubbo:dubbo-registry-sofa:$dubboVersion")

    implementation("org.apache.dubbo:dubbo-dependencies-zookeeper:$dubboVersion") {
        exclude(module = "guava")
        exclude(group = "org.slf4j")
        exclude(group = "log4j")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.5.+") {
        exclude(module = "kotlin-stdlib")
        exclude(module = "kotlinx-coroutines-core")
    }

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.5.+")
}

intellij {
    version.set("2021.2")
    pluginName.set("dubbo-plugin")
    type.set("IC")
    plugins.set(listOf("java", "yaml"))
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
}

changelog {
    headerParserRegex.set("\\[?\\d(\\.\\d+)+\\]?.*".toRegex())
    header.set(provider { "[${version.get()}](https://github.com/shenluw/intellij-dubbo-plugin/tree/${version.get()}) - ${date()}" })
    path.set("${project.projectDir}/CHANGELOG.md")
    itemPrefix.set("-")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}
val javaVersion = "11"
tasks {
    compileJava {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion

        options.encoding = "UTF-8"
    }
    compileKotlin {
        kotlinOptions {
            jvmTarget = javaVersion
            apiVersion = "1.3"
//            allWarningsAsErrors = true
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = javaVersion
            apiVersion = "1.3"
//            allWarningsAsErrors = true
        }
    }

    patchPluginXml {
        sinceBuild.set("211")
        untilBuild.set("213.*")

        pluginDescription.set(
            File(projectDir, "README.md").readText().run { markdownToHTML(this) }
        )

        changeNotes.set(provider { changelog.get(version.get()).toHTML() })
    }


    publishPlugin {
        dependsOn("patchChangelog")
        token.set(properties("intellijPublishToken"))
    }
}