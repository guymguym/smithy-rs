DIR="s3d/build/smithyprojections/s3d/s3/rust-server-codegen"

.PHONY: gen
all: gen build test

.PHONY: gen
gen:
	@echo "\n\n### Generating ###\n\n"
	./gradlew :s3d:assemble
	./gradlew -Paws.services=+sts,+sso,+s3 :aws:sdk:assemble
	# ./gradlew -Ps3d.release=true :s3d:assemble

.PHONY: build
build:
	@echo "\n\n### Building ###\n\n"
	cd $(DIR) && cargo build --release

.PHONY: test
test:
	@echo "\n\n### Testing ###\n\n"
	cd $(DIR) && cargo test --release

.PHONY: publish
publish:
	@echo "\n\n### Publishing ###\n\n"
	cd $(DIR) && cargo publish --dry-run

.PHONY: clean
clean:
	@echo "\n\n### Cleaning ###\n\n"
	./gradlew clean

.PHONY: help
help:
	@echo ""
	@echo "Usage: make -f s3d/Makefile <target>"
	@echo ""
	@echo "Targets:"
	@echo "    all (default) - gen + build + test"
	@echo "    gen"
	@echo "    build"
	@echo "    test"
	@echo "    publish"
	@echo "    clean"
	@echo "    help"
	@echo ""
