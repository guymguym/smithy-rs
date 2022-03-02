// ---------------------------- //
// build gradle for s3d codegen //
// ---------------------------- //

description = "S3D Customizations for Smithy code generation"
group = "s3d.rs.codegen"
version = "0.0.1"
extra["displayName"] = "S3D CODEGEN"
extra["moduleName"] = "s3d.rs.codegen"

val smithyVersion: String by project
val properties = PropertyRetriever(rootProject, project)
val releaseMode = properties.get("s3d.release")?.toBoolean() ?: false
val runtimeConfig = if (releaseMode) {
    """{ "version": "DEFAULT" }"""
} else {
    """{ "relativePath": "../" }"""
}

plugins {
    id("software.amazon.smithy")
}

buildscript {
    val smithyVersion: String by project
    dependencies {
        classpath("software.amazon.smithy:smithy-cli:$smithyVersion")
    }
}

dependencies {
    // implementation(project(":codegen"))
    implementation(project(":codegen-server"))
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

// -------//
// tasks //
// -------//

// tasks.compileKotlin { kotlinOptions.jvmTarget = "1.8" }
// tasks.compileTestKotlin { kotlinOptions.jvmTarget = "1.8" }

// tasks.jar {
//     inputs.property("moduleName", project.name)
//     manifest { attributes["Automatic-Module-Name"] = project.name }
// }

tasks["smithyBuildJar"].dependsOn("generateSmithyBuild")

task("generateSmithyBuild") {
    description = "generate smithy-build.json"
    val buildFile = projectDir.resolve("smithy-build.json")
    outputs.file(buildFile)
    doFirst {
        buildFile.writeText(
            """
            {
                "version": "1.0",
                "projections": {
                    "s3": {
                        "imports": ["../aws/sdk/aws-models/s3.json"],
                        "plugins": {
                            "rust-server-codegen": {
                                "module": "s3d-smithy-codegen-server-s3",
                                "moduleDescription": "s3d-smithy-codegen-server-s3",
                                "moduleAuthors": ["s3d@s3d.rs"],
                                "moduleVersion": "0.0.1",
                                "service": "com.amazonaws.s3#AmazonS3",
                                "runtimeConfig": $runtimeConfig
                            }
                        }
                    }
                }
            }

            """.trimIndent(),
        )
    }
}
