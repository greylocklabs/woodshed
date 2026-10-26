# Development and release tasks. `make` or `make help` lists them.

GRADLE := ./gradlew
# Some Linux hosts crash the emulator's software renderer; override with EMULATOR_GPU=host.
EMULATOR_GPU ?= swiftshader_indirect

.DEFAULT_GOAL := help
.PHONY: help build apk e2e-release test www record-screenshots e2e format lint check clean

help: ## List the tasks
	@awk 'BEGIN { FS = ":.*## " } /^[a-z0-9-]+:.*## / { printf "  %-20s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

# Building
# --------

build: ## Build the debug app
	$(GRADLE) :app:assembleDebug

apk: build ## Build the debug app and print where the APK is
	@echo app/build/outputs/apk/debug/app-debug.apk

clean: ## Remove build output
	$(GRADLE) clean

# Testing
# -------

test: ## Run the unit, Room and Robolectric tests, and compare screenshots
	$(GRADLE) :app:verifyRoborazziDebug

record-screenshots: ## Rewrite the reference screenshots after a deliberate design change
	$(GRADLE) :app:recordRoborazziDebug

e2e: ## Run the end-to-end tests on an emulator Gradle manages (never a phone with real data)
	$(GRADLE) :app:pixel6api34DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=$(EMULATOR_GPU)

e2e-release: ## Run the end-to-end tests against the shrunk release build
	$(GRADLE) :app:pixel6api34ReleaseAndroidTest -Pe2eRelease -Pandroid.testoptions.manageddevices.emulator.gpu=$(EMULATOR_GPU)

# Formatting and linting
# ----------------------

format: ## Format Kotlin with ktlint
	$(GRADLE) spotlessApply

lint: ## Check formatting, run Android lint and check VERSION agrees with the changelog
	$(GRADLE) spotlessCheck :app:lintDebug
	@core/tooling/check-version.sh

check: lint test ## CI's lint and test jobs; commitlint runs in CI only

# Website
# -------

WWW_PORT ?= 8000

www: ## Serve the website in www/ at http://localhost:8000 (WWW_PORT=... for another port)
	@echo "Serving www/ at http://localhost:$(WWW_PORT); Ctrl+C stops it."
	@python3 -m http.server $(WWW_PORT) --bind 127.0.0.1 -d www

# Releasing
# ---------

# Woodshed's commits need a scope, so the release commit takes this one.
RELEASE_SCOPE := release
include core/tooling/release.mk
