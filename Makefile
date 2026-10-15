# Development and release tasks. `make` or `make help` lists them.

GRADLE := ./gradlew
# Some Linux hosts crash the emulator's software renderer; override with EMULATOR_GPU=host.
EMULATOR_GPU ?= swiftshader_indirect

.DEFAULT_GOAL := help
.PHONY: help build apk e2e-release test www worker-test worker-deploy record-screenshots e2e format lint check clean version release-notes bump release

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

lint: ## Check formatting, run Android lint and check the changelog has the current version
	$(GRADLE) spotlessCheck :app:lintDebug
	@scripts/release-notes.sh > /dev/null

check: lint test worker-test ## CI's lint, test and worker jobs; commitlint runs in CI only

# Website
# -------

WWW_PORT ?= 8000

www: ## Serve the website in www/ at http://localhost:8000 (WWW_PORT=... for another port)
	@echo "Serving www/ at http://localhost:$(WWW_PORT); Ctrl+C stops it."
	@python3 -m http.server $(WWW_PORT) --bind 127.0.0.1 -d www

# Report service
# --------------

worker/node_modules: worker/package.json
	cd worker && npm install --no-audit --no-fund
	@touch $@

worker-test: worker/node_modules ## Type-check and test the report service in worker/
	cd worker && npx tsc --noEmit && npm test

worker-deploy: worker-test ## Deploy the report service to Cloudflare (needs `npx wrangler login`)
	cd worker && npx wrangler deploy

# Releasing
# ---------

version: ## Print the current version
	@cat VERSION

release-notes: ## Print the changelog section for VERSION, or V=x.y.z
	@scripts/release-notes.sh $(V)

bump: ## Print what PART=major|minor|patch would bump the version to
	@scripts/bump-version.sh $(PART)

release: ## Cut a release from main: PART=major|minor|patch, or current to tag VERSION as it is; DRY_RUN=1 to check
	@scripts/release.sh $(PART)
