extra["displayName"] = "S3D CODEGEN"

val properties = PropertyRetriever(rootProject, project)
val smithyVersion: String by project
val releaseMode = properties.get("s3d.release")?.toBoolean() ?: false
val runtimeConfig = if (releaseMode) {
    """{ "version": "DEFAULT" }"""
} else {
    """{ "relativePath": "../../../../../../rust-runtime" }"""
}

plugins {
    id("software.amazon.smithy").version("0.5.3")
}

dependencies {
    implementation(project(":codegen-server"))
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

tasks["assemble"].dependsOn("generateSmithyBuild")

task("generateSmithyBuild") {
    description = "generate smithy-build.json"
    doFirst {
        projectDir.resolve("smithy-build.json").writeText(
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
            """.trimIndent()
        )
    }
}
