/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

extra["displayName"] = "Smithy :: Rust :: Codegen :: Server :: S3D"

extra["moduleName"] = "software.amazon.smithy.rust.kotlin.codegen.server.s3d"

tasks["jar"].enabled = false

plugins { id("software.amazon.smithy").version("0.5.3") }

val smithyVersion: String by project

dependencies {
    implementation(project(":codegen-server"))
    // implementation("software.amazon.smithy:smithy-aws-protocol-tests:$smithyVersion")
    // implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
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
                            "relativePath": "${rootProject.projectDir.absolutePath}/rust-runtime"
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

task("generateSmithyBuild") {
    description = "generate smithy-build.json"
    doFirst { projectDir.resolve("smithy-build.json").writeText(generateSmithyBuild()) }
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

tasks["smithyBuildJar"].dependsOn("generateSmithyBuild")

tasks["assemble"].dependsOn("smithyBuildJar")

tasks["assemble"].finalizedBy("generateCargoWorkspace")

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
