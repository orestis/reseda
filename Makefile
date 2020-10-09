.PHONY: test
test:
	@echo "Installing dependencies"
	npm install
	npm install --only=dev
	@echo "Compiling for tests"
	clojure -A:shadow-cljs compile ci
	@echo "Running karma"
	./node_modules/.bin/karma start --single-run
