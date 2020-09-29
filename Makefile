.PHONY: docker

package:
	./mvnw clean package
docker:
	cp -f target/*.jar docker/app
	cd docker/app && docker build .