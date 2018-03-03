run:
	sbt 'project main' '~run' -mem 2048 -jvm-debug 5005

image/build:
	sbt 'project main' 'docker:publishLocal'

image/push:
	docker tag petitviolet/graphql-prac:1.0 petitviolet.azurecr.io/petitviolet/graphql-prac:latest
	docker push petitviolet.azurecr.io/petitviolet/graphql-prac:latest
