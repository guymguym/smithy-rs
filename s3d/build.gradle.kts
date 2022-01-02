/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

extra["displayName"] = "Smithy :: Rust :: S3D"
extra["moduleName"] = "software.amazon.smithy.rust.kotlin.codegen.server.s3d"
tasks["jar"].enabled = false
plugins { id("software.amazon.smithy").version("0.5.3") }

val smithyVersion: String by project
val properties = PropertyRetriever(rootProject, project)
val runtimesOutputDir = buildDir.resolve("rust-runtime")

dependencies {
    implementation(project(":codegen-server"))
    // implementation("software.amazon.smithy:smithy-aws-protocol-tests:$smithyVersion")
    // implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

task("finalize") {
    dependsOn("assemble")
    outputs.upToDateWhen { false }
    finalizedBy(
        "relocateAllRuntimes",
        "fixManifests"
    )
}

tasks["assemble"].apply {
    dependsOn("smithyBuildJar")
    finalizedBy(
        "generateCargoWorkspace",
        "finalize"
    )
}

tasks["smithyBuildJar"].dependsOn("generateSmithyBuild")

task("generateSmithyBuild") {
    description = "generate smithy-build.json"
    doFirst { projectDir.resolve("smithy-build.json").writeText(generateSmithyBuild()) }
}

/**
 * `includeFluentClient` must be set to `false` as we are not generating all the supporting code for
 * it. TODO: Review how can we make this a default in the server so that customers don't
 * ```
 *       have to specify it.
 * ```
 */
fun generateSmithyBuild(): String {
    return """
    {
        "version": "1.0",
        "projections": {
            "s3": {
                "imports": ["../aws/sdk/aws-models/s3.json"],
                "plugins": {
                    "rust-server-codegen": {
                        "runtimeConfig": {
                            "relativePath": "../../../../rust-runtime/",
                            "version": "DEFAULT"
                        },
                        "service": "com.amazonaws.s3#AmazonS3",
                        "module": "s3-codegen-server",
                        "moduleVersion": "0.0.1",
                        "moduleDescription": "s3-codegen-server",
                        "moduleAuthors": ["protocoltest@example.com"]
                    }
                }
            }
        }
    }
    """.trimIndent()
}

tasks.register<Copy>("copyAllRuntimes") {
    from("$rootDir/aws/rust-runtime") {
        CrateSet.AWS_SDK_RUNTIME.forEach { include("$it/**") }
    }
    from("$rootDir/rust-runtime") {
        CrateSet.SERVER_SMITHY_RUNTIME.forEach { include("$it/**") }
    }
    exclude("**/target")
    exclude("**/Cargo.lock")
    exclude("**/node_modules")
    into(runtimesOutputDir)

    doLast {
        // sts is needed on runtime, so this should run after building:
        // $ ./gradlew -Paws.services=+sts :aws:sdk:assemble
        copy {
            from("$rootDir/aws/sdk/build/smithyprojections/sdk/sts/rust-codegen")
            into(runtimesOutputDir.resolve("sts"))
            include("**")
            exclude("target")
            exclude("Cargo.lock")
            exclude("node_modules")
        }
        copy {
            from("$rootDir/aws/sdk/build/smithyprojections/sdk/s3/rust-codegen")
            into(runtimesOutputDir.resolve("aws-sdk-s3"))
            include("**")
            exclude("target")
            exclude("Cargo.lock")
            exclude("node_modules")
        }
    }
}


tasks.register("relocateAllRuntimes") {
    dependsOn("copyAllRuntimes")
    doLast {
        // Patch the Cargo.toml files
        listOf("sts", "aws-sdk-s3").forEach { moduleName ->
            patchFile(runtimesOutputDir.resolve("$moduleName/Cargo.toml")) { line ->
                rewriteAwsSdkCrateVersion(properties, line.let(::rewritePathDependency))
            }
        }
        CrateSet.AWS_SDK_RUNTIME.forEach { moduleName ->
            patchFile(runtimesOutputDir.resolve("$moduleName/Cargo.toml")) { line ->
                rewriteAwsSdkCrateVersion(properties, line.let(::rewritePathDependency))
            }
        }
        CrateSet.SERVER_SMITHY_RUNTIME.forEach { moduleName ->
            patchFile(runtimesOutputDir.resolve("$moduleName/Cargo.toml")) { line ->
                rewriteSmithyRsCrateVersion(properties, line)
            }
        }
    }
}

/**
 * The aws/rust-runtime crates depend on local versions of the Smithy core runtime enabling local compilation. However,
 * those paths need to be replaced in the final build. We should probably fix this with some symlinking.
 */
fun rewritePathDependency(line: String): String {
    // some runtime crates are actually dependent on the generated bindings:
    return line.replace("../sdk/build/aws-sdk/sdk/", "")
        // others use relative dependencies::
        .replace("../../rust-runtime/", "")
}

tasks.register<Exec>("fixManifests") {
    description = "Run the publisher tool's `fix-manifests` sub-command on the generated services"

    dependsOn("assemble")
    dependsOn("relocateAllRuntimes")

    val publisherPath = rootProject.projectDir.resolve("tools/publisher")
    inputs.dir(publisherPath)
    outputs.dir(runtimesOutputDir)

    workingDir(publisherPath)

    environment(mapOf(
        "RUST_BACKTRACE" to "1",
        "RUST_LOG" to "debug"
    ))

    commandLine("cargo", "run", "--", "fix-manifests", "--location", runtimesOutputDir.absolutePath)
}

fun generateCargoWorkspace(): String {
    return """
    [workspace]
    members = ["s3/rust-server-codegen"]
    """.trimIndent()
}

task("generateCargoWorkspace") {
    description = "generate Cargo.toml workspace file"
    doFirst {
        buildDir.resolve("smithyprojections/s3d/Cargo.toml").writeText(generateCargoWorkspace())
    }
}

tasks.register<Exec>("cargoCheck") {
    workingDir("build/smithyprojections/s3d/")
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "check")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoTest") {
    workingDir("build/smithyprojections/s3d/")
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "test")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoDocs") {
    workingDir("build/smithyprojections/s3d/")
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "doc", "--no-deps")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoClippy") {
    workingDir("build/smithyprojections/s3d/")
    // disallow warnings
    commandLine("cargo", "clippy", "--", "-D", "warnings")
    dependsOn("assemble")
}

tasks["test"].finalizedBy("cargoCheck", "cargoClippy", "cargoTest", "cargoDocs")

tasks["clean"].doFirst { delete("smithy-build.json") }
