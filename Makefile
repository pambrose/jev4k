.PHONY: default help stop clean clean-all build tests jvm-tests js-tests native-tests refresh tree depends kdocs \
        site site-build clean-site check-site upgrade-site lint format detekt detekt-baseline abi-check abi-update \
        coverage coverage-html coverage-xml coverage-log \
        coverage-verify coverage-open coverage-clean versions api-docs example live-tests \
        publish-local publish-local-snapshot publish-snapshot publish-maven-central \
        upgrade-wrapper test-jdk all-tests docker-linux-tests platform-tests _check-gpg-env _require-version \
        _require-gradle-version _require-jdk _require-macos _require-docker

VERSION := $(shell sed -n 's/^version=\(.*\)/\1/p' gradle.properties)
GRADLE_VERSION := $(shell sed -n 's/^gradle-wrapper = "\(.*\)"/\1/p' gradle/libs.versions.toml)
# The bytecode floor from the catalog, used as the default JDK for `make test-jdk`. The line carries a trailing
# comment, so the capture stops at the closing quote.
JDK ?= $(shell sed -n 's/^jvm-target = "\([^"]*\)".*/\1/p' gradle/libs.versions.toml)

GRADLE := ./gradlew

# The native test tasks this host can run: macOS and the iOS, tvOS and watchOS simulators on a Mac (Gradle skips a
# tvOS or watchOS one when no simulator device for it is installed), mingwX64 under Git Bash or MSYS on Windows, and
# linuxX64 on x86-64 Linux. linuxArm64 has no test task, so an arm64 Linux host runs none (docker-linux-tests covers
# both Linux targets on any host). The other hosts' tasks exist everywhere but are skipped, and CI runs each on its
# own runner. Every task gets --rerun, which re-executes that task alone: --rerun-tasks would also recompile all
# sixteen targets.
HOST_OS := $(shell uname -s)
HOST_ARCH := $(shell uname -m)
ifeq ($(HOST_OS),Darwin)
NATIVE_TESTS := macosArm64Test iosSimulatorArm64Test tvosSimulatorArm64Test watchosSimulatorArm64Test
else ifneq ($(filter MINGW% MSYS%,$(HOST_OS)),)
NATIVE_TESTS := mingwX64Test
else ifeq ($(HOST_ARCH),x86_64)
NATIVE_TESTS := linuxX64Test
else
NATIVE_TESTS :=
endif
JS_TESTS := jsNodeTest wasmJsNodeTest
# Every test task this host runs: the JVM, Node.js, and the native tasks above.
HOST_TESTS := jvmTest $(JS_TESTS) $(NATIVE_TESTS)
RERUN = $(foreach task,$(1),$(task) --rerun)
# Succeeds only when a Docker daemon is running. A recipe fragment, not $(shell): it runs when a recipe needs it.
DOCKER_UP = docker info >/dev/null 2>&1
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

# `build -x allTests` would still run each target's test task, so this names what `build` does besides testing.
# `assemble` compiles no test sources, so jvmTestClasses is named too: it compiles the documentation examples and
# JavaInterop.java, which break when the API they show changes.
build:  ## Clean build without tests: compile every target and the JVM test sources, lint, detekt, and the ABI check
	$(GRADLE) clean assemble jvmTestClasses lintKotlin detekt checkKotlinAbi

tests:  ## Run lint, detekt, the ABI check, and every test this host can run (forces the tests to re-run)
	$(GRADLE) check $(call RERUN,$(HOST_TESTS))

jvm-tests:  ## Run the JVM tests only, the quickest loop
	$(GRADLE) $(call RERUN,jvmTest)

js-tests:  ## Run the tests on Node.js, for the js and wasmJs targets
	$(GRADLE) $(call RERUN,$(JS_TESTS))

native-tests:  ## Run the native tests this host supports (macOS + Apple simulators on a Mac, linuxX64, mingwX64)
ifeq ($(NATIVE_TESTS),)
	@echo "No native test task runs on this host ($(HOST_OS) $(HOST_ARCH))"
else
	$(GRADLE) $(call RERUN,$(NATIVE_TESTS))
endif

# The Linux tests without a Linux machine. Both Linux test binaries are linked here (the build wires in Kotest's
# entry-point generator, which the plugin skips for targets the host can't run), then each runs in a container of
# its own architecture; Docker Desktop on Apple Silicon runs linux/amd64 under emulation. A Kotest binary exits 0
# even when a test fails, because Gradle reads the verdict from its TeamCity messages, so this does the same: no
# Kotest summary (a crash, or a binary with no specs) or any failed test fails the target. The image is Ubuntu plus
# ca-certificates, which the Curl engine needs for TLS; `JEV4K_LIVE=1 make docker-linux-tests` runs the probes.
LINUX_TEST_IMAGE := buildpack-deps:noble-curl
LINUX_TEST_LOGS := build/docker-linux-tests

# The targets to run, as target:architecture pairs. CI's build job has already run linuxX64's tests, so it passes
# LINUX_TEST_TARGETS=linuxArm64:arm64.
LINUX_TEST_TARGETS ?= linuxX64:amd64 linuxArm64:arm64
LINUX_TEST_LINKS = $(foreach pair,$(LINUX_TEST_TARGETS),linkDebugTest$(subst linux,Linux,$(firstword $(subst :, ,$(pair)))))

docker-linux-tests: _require-docker  ## Run the linuxX64 and linuxArm64 tests in Docker containers
	$(GRADLE) $(LINUX_TEST_LINKS)
	@mkdir -p $(LINUX_TEST_LOGS)
	@status=0; \
	for pair in $(LINUX_TEST_TARGETS); do \
		target=$${pair%%:*}; arch=$${pair##*:}; log=$(LINUX_TEST_LOGS)/$$target.log; \
		echo "== $$target on linux/$$arch ($(LINUX_TEST_IMAGE))"; \
		docker run --rm --platform linux/$$arch -e JEV4K_LIVE \
			-v "$(CURDIR)/build/bin/$$target/debugTest:/tests:ro" $(LINUX_TEST_IMAGE) /tests/test.kexe > $$log 2>&1; \
		code=$$?; \
		grep -v -E '^(##teamcity|\[(=+|-+| +PASSED +)\])' $$log; \
		if [ $$code -ne 0 ] || ! grep -q 'Specs:' $$log || grep -q '^##teamcity\[testFailed' $$log; then \
			echo "FAILED: $$target (exit code $$code, full log in $$log)" >&2; status=1; \
		fi; \
	done; \
	exit $$status

# The suite on every platform this host can reach, and nothing else: no lint, ABI check, JDK matrix or coverage.
# That is the JVM, Node.js (js and wasmJs), this host's native tasks (the Apple ones on a Mac), and both Linux
# targets in Docker, which is checked first rather than after the Gradle run. --continue lets every Gradle platform
# report before the first failure stops the run; the Docker step runs only if they all pass.
platform-tests: _require-docker  ## Run the tests on every platform: JVM, Node.js, this host's native, and Docker Linux
	$(GRADLE) --continue $(call RERUN,$(HOST_TESTS))
	$(MAKE) docker-linux-tests

# Reproduce one row of CI's JDK matrix. The tests normally run on the build toolchain, so this is the only way
# to exercise the bytecode floor locally. JDK defaults to that floor, the row most likely to catch something.
test-jdk: _require-jdk  ## Run the tests on a specific JDK, e.g. make test-jdk JDK=21
	$(GRADLE) $(call RERUN,jvmTest) -PtestJavaVersion=$(JDK)

# Every test target in one run, in the order that fails cheapest first. The JDK list mirrors the matrix in
# .github/workflows/ci.yml, including the toolchain row that `tests` has already covered, so a green run here
# means the same thing a green CI run does. The Linux tests (which CI runs on its ubuntu runner) and the live tests
# need something this host may not have, Docker or an API key, so each runs only when it is available and is
# reported as skipped when it isn't, rather than failing the whole target.
TEST_JDKS ?= 17 21 25

all-tests:  ## Run every test target: tests, the CI JDK matrix, coverage floors, Docker Linux tests, live tests
	$(MAKE) tests
	@for jdk in $(TEST_JDKS); do \
		$(MAKE) test-jdk JDK=$$jdk || exit 1; \
	done
	$(MAKE) coverage-verify
	@if $(DOCKER_UP); then \
		$(MAKE) docker-linux-tests; \
	else \
		echo "SKIP: docker-linux-tests (no running Docker daemon)"; \
	fi
	@if [ -n "$$TYPESAFE_API_KEY" ] || grep -qs '^TYPESAFE_API_KEY=..*' .env; then \
		$(MAKE) live-tests; \
	else \
		echo "SKIP: live-tests (no TYPESAFE_API_KEY in the environment or .env)"; \
	fi

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
	cd $(SITE_DIR) && uv run --locked zensical build --clean --strict
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

abi-check:  ## Check the public API against the dumps in api/ (also part of `make tests`)
	$(GRADLE) checkKotlinAbi

# Only a Mac compiles every target, so only a Mac writes a complete klib dump.
abi-update: _require-macos  ## Rewrite the API dumps in api/ after an intended API change
	$(GRADLE) updateKotlinAbi

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
	$(GRADLE) cleanJvmTest
	rm -rf build/reports/kover build/kover

versions:  ## Check for newer dependency versions
	$(GRADLE) dependencyUpdates --no-configuration-cache --no-parallel

api-docs:  ## Re-download the cached TypeSafe docs into jev-docs/pages
	./jev-docs/refresh.sh

example:  ## Run the Triage example against the live API (needs TYPESAFE_API_KEY)
	$(GRADLE) runExample

# The JVM runs LiveSmokeTest, which spends tokens, and LiveProbeTest; the other platforms run LiveProbeTest, whose
# two calls spend none. Each task is filtered to those specs; wasmJsNodeTest ignores the filter and runs its whole
# suite, which is harmless. The simulators only see variables prefixed SIMCTL_CHILD_, so their probes stay skipped:
# a test binary spawned by simctl can't validate any TLS certificate, so they would fail there regardless.
LIVE_PROBE_TESTS = $(foreach task,$(1),$(task) --rerun --tests "com.pambrose.jev4k.LiveProbeTest")
live-tests:  ## Run the live API tests: the JVM smoke tests (needs TYPESAFE_API_KEY) and each platform's probes
	JEV4K_LIVE=1 $(GRADLE) jvmTest --rerun --tests "com.pambrose.jev4k.LiveSmokeTest" \
		--tests "com.pambrose.jev4k.LiveProbeTest" $(call LIVE_PROBE_TESTS,$(JS_TESTS) $(NATIVE_TESTS))

publish-local: _require-version  ## Publish artifacts to the local Maven repository
	$(GRADLE) publishToMavenLocal

publish-local-snapshot: _require-version  ## Publish a -SNAPSHOT artifact to the local Maven repository
	$(GRADLE) -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

# A release carries every target, and the Apple ones can only be built on a Mac.
publish-snapshot: _require-version _require-macos _check-gpg-env  ## Publish a -SNAPSHOT artifact to Maven Central
	$(GPG_ENV) $(GRADLE) -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: _require-version _require-macos _check-gpg-env  ## Publish a release artifact to Maven Central
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

_require-macos:
	@[ "$(HOST_OS)" = Darwin ] || { echo "ERROR: this target needs macOS, the only host that builds every target" >&2; exit 1; }

_require-docker:
	@$(DOCKER_UP) || { echo "ERROR: this target needs a running Docker daemon" >&2; exit 1; }

_require-version:
	@[ -n "$(VERSION)" ] || { echo "ERROR: Could not determine project version from gradle.properties" >&2; exit 1; }

_require-jdk:
	@case "$(JDK)" in \
		''|*[!0-9]*) echo "ERROR: JDK must be a major version number, e.g. make test-jdk JDK=17" >&2; exit 1 ;; \
	esac

_require-gradle-version:
	@[ -n "$(GRADLE_VERSION)" ] || { echo "ERROR: Could not determine gradle version from gradle/libs.versions.toml" >&2; exit 1; }
