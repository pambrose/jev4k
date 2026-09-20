.PHONY: default help stop clean clean-all build tests refresh tree depends kdocs site site-build clean-site \
        check-site upgrade-site lint format detekt detekt-baseline \
        coverage coverage-html coverage-xml coverage-log \
        coverage-verify coverage-open coverage-clean versions api-docs example live-tests \
        publish-local publish-local-snapshot publish-snapshot publish-maven-central \
        upgrade-wrapper _check-gpg-env _require-version _require-gradle-version

VERSION := $(shell sed -n 's/^version=\(.*\)/\1/p' gradle.properties)
GRADLE_VERSION := $(shell sed -n 's/^gradle-wrapper = "\(.*\)"/\1/p' gradle/libs.versions.toml)

GRADLE := ./gradlew
GRADLE_DIST_URL := https://services.gradle.org/distributions
WEBSITE_DIR := website
SITE_DIR := $(WEBSITE_DIR)/jev4k

# Signing credentials for Maven Central: the GPG key comes from the local keyring
# (GPG_SIGNING_KEY_ID) and its passphrase from the macOS keychain. Maven Central credentials are read by Gradle
# from ~/.gradle/gradle.properties (mavenCentralUsername / mavenCentralPassword).
GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys "$$GPG_SIGNING_KEY_ID")" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyId="$$GPG_SIGNING_KEY_ID" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)"

default: help

help:  ## Show this help (list of targets)
	@awk 'BEGIN {FS = ":.*?## "; printf "Usage: make <target>\n\nTargets:\n"} \
		/^[a-zA-Z0-9_-]+:.*?## / {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

stop:  ## Stop the running Gradle daemon
	$(GRADLE) --stop

clean:  ## Remove Gradle build outputs
	$(GRADLE) clean

clean-all: clean clean-site  ## clean + remove .gradle and .kotlin caches and the docs site
	rm -rf .gradle .kotlin

build:  ## Clean build without tests
	$(GRADLE) clean build -x test

tests:  ## Run all tests (forces re-execution)
	$(GRADLE) --rerun-tasks check

# --refresh-dependencies only applies to what the invocation resolves, so it needs a task. With none, Gradle
# runs `help` and re-resolves nothing but the build-script classpath. `dependencies` touches every
# configuration; the report itself is what `make depends` is for, so it is discarded here.
refresh:  ## Re-resolve every dependency, ignoring the cached versions
	$(GRADLE) --refresh-dependencies dependencies -q >/dev/null

tree:  ## Print Gradle dependency tree (quiet)
	$(GRADLE) -q dependencies

depends:  ## Print Gradle dependency report
	$(GRADLE) dependencies

kdocs:  ## Generate Dokka HTML site (build/dokka/html)
	$(GRADLE) dokkaGeneratePublicationHtml

site: clean-site  ## Serve the docs site locally with Zensical (http://localhost:8000)
	cd $(SITE_DIR) && uv run zensical serve

site-build: clean-site kdocs  ## Build the static docs site into website/jev4k/site, with KDocs under /kdocs
	cd $(SITE_DIR) && uv run --locked zensical build --clean
	cp -r build/dokka/html $(SITE_DIR)/site/kdocs

clean-site:  ## Remove the generated docs site and its cache
	rm -rf $(SITE_DIR)/site $(SITE_DIR)/.cache

check-site:  ## Check for outdated docs-site dependencies
	cd $(WEBSITE_DIR) && env -u VIRTUAL_ENV uv lock --upgrade --dry-run

upgrade-site:  ## Upgrade the docs-site dependencies
	cd $(WEBSITE_DIR) && env -u VIRTUAL_ENV uv lock --upgrade

lint:  ## Run kotlinter and detekt
	$(GRADLE) lintKotlin detekt

format:  ## Reformat Kotlin sources with kotlinter (ktlint)
	$(GRADLE) formatKotlin

detekt:  ## Run detekt static analysis
	$(GRADLE) detekt

detekt-baseline:  ## Refresh detekt baseline
	$(GRADLE) detektBaseline

coverage: coverage-html coverage-xml  ## Generate HTML and XML coverage reports

coverage-html:  ## Generate HTML coverage report
	$(GRADLE) koverHtmlReport

coverage-xml:  ## Generate XML coverage report (what CI uploads to Codecov)
	$(GRADLE) koverXmlReport

coverage-log:  ## Print coverage % to console
	$(GRADLE) koverLog

coverage-verify:  ## Check coverage against the line and branch floors in build.gradle.kts
	$(GRADLE) koverVerify

coverage-open: coverage-html  ## Open the HTML coverage report
	open build/reports/kover/html/index.html

coverage-clean:  ## Remove coverage reports and test outputs
	$(GRADLE) cleanTest
	rm -rf build/reports/kover build/kover

versions:  ## Check for newer dependency versions
	$(GRADLE) dependencyUpdates --no-configuration-cache --no-parallel

api-docs:  ## Re-download the cached TypeSafe docs into jev-docs/pages
	./jev-docs/refresh.sh

example:  ## Run the Triage example against the live API (needs TYPESAFE_API_KEY)
	$(GRADLE) runExample

live-tests:  ## Run the live API smoke tests (needs TYPESAFE_API_KEY)
	JEV4K_LIVE=1 $(GRADLE) test --rerun-tasks --tests "com.pambrose.jev4k.LiveSmokeTest"

publish-local: _require-version  ## Publish artifacts to the local Maven repository
	$(GRADLE) publishToMavenLocal

publish-local-snapshot: _require-version  ## Publish a -SNAPSHOT artifact to the local Maven repository
	$(GRADLE) -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

publish-snapshot: _require-version _check-gpg-env  ## Publish a -SNAPSHOT artifact to Maven Central
	$(GPG_ENV) $(GRADLE) -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: _require-version _check-gpg-env  ## Publish a release artifact to Maven Central
	$(GPG_ENV) $(GRADLE) publishAndReleaseToMavenCentral

# Gradle's documented upgrade procedure: the first run rewrites gradle-wrapper.properties using the *old*
# wrapper jar; the second run regenerates the wrapper itself with the new version. Both runs carry the
# checksum, so gradle-wrapper.properties keeps pinning the distribution that gradlew is allowed to unpack.
upgrade-wrapper: _require-gradle-version  ## Upgrade the Gradle wrapper to the catalog version
	@sha="$$(curl -fsSL $(GRADLE_DIST_URL)/gradle-$(GRADLE_VERSION)-bin.zip.sha256)" && \
	[ -n "$$sha" ] || { echo "ERROR: could not fetch the checksum for Gradle $(GRADLE_VERSION)" >&2; exit 1; }; \
	$(GRADLE) wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin \
		--gradle-distribution-sha256-sum="$$sha" && \
	$(GRADLE) wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin \
		--gradle-distribution-sha256-sum="$$sha"

_check-gpg-env:
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "ERROR: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "ERROR: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if [ -z "$$(security find-generic-password -a 'gpg-signing' -s 'gradle-signing-password' -w 2>/dev/null)" ]; then \
		echo "ERROR: keychain entry 'gradle-signing-password' (account 'gpg-signing') is missing or empty" >&2; exit 1; \
	fi

_require-version:
	@[ -n "$(VERSION)" ] || { echo "ERROR: Could not determine project version from gradle.properties" >&2; exit 1; }

_require-gradle-version:
	@[ -n "$(GRADLE_VERSION)" ] || { echo "ERROR: Could not determine gradle version from gradle/libs.versions.toml" >&2; exit 1; }
