CRATES_DIR="s3d/build/crates"
CRATE_CODEGEN_SERVER_S3="$(CRATES_DIR)/s3d-smithy-codegen-server-s3"

all: gen build test
.PHONY: all

gen:
	@echo "\n\n### Generating ###\n\n"
	./gradlew -Paws.services=+sts,+sso,+s3 :aws:sdk:assemble
	./gradlew :rust-runtime:assemble
	./gradlew :s3d:assemble
	# ./gradlew -Ps3d.release=true :s3d:assemble
	rm -rf $(CRATES_DIR)
	mkdir -p $(CRATES_DIR)
	mv rust-runtime/build/smithy-rs/rust-runtime/* $(CRATES_DIR)/
	mv aws/sdk/build/aws-sdk/sdk/{aws-config,aws-endpoint,aws-http,aws-sig-auth,aws-sigv4,aws-types,s3,sso,sts} $(CRATES_DIR)/
	mv s3d/build/smithyprojections/s3d/s3/rust-server-codegen $(CRATE_CODEGEN_SERVER_S3)
	@echo "✅ gen"
.PHONY: gen

build:
	@echo "\n\n### Building ###\n\n"
	cd $(CRATE_CODEGEN_SERVER_S3) && cargo build
	@echo "✅ build"
.PHONY: build

test:
	@echo "\n\n### Testing ###\n\n"
	cd $(CRATE_CODEGEN_SERVER_S3) && cargo test
	@echo "✅ test"
.PHONY: test

publish:
	@echo "\n\n### Publishing ###\n\n"
	cd $(CRATE_CODEGEN_SERVER_S3) && cargo publish --dry-run
	@echo "✅ publish"
.PHONY: publish

rebase:
	@git diff --exit-code || { \
		echo "\n\n🟥 🟥 Please commit/stash these diffs ^^ before rebasing 🟥 🟥 \n\n"; \
		exit 1; \
	}
	git switch main
	git fetch --all
	git rebase upstream/main
	@echo "✅ rebase"
.PHONY: rebase

clean:
	@echo "\n\n### Cleaning ###\n\n"
	./gradlew clean
	@echo "✅ clean"
.PHONY: clean

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
.PHONY: help
