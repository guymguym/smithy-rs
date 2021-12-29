# S3D Codegen
This module generates s3 server code for [s3d-rs](https://github.com/s3d-rs/s3d).

`build.gradle.kts` will generate a `smithy-build.json` file as part of the build. The Smithy build plugin then invokes our codegen machinery and generates Rust crates.

The `test` task will run `cargo check` and `cargo clippy` to validate that the generated Rust compiles and is idiomatic.

## Usage
```
../gradlew test
```
