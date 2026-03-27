ARG ELASTICSEARCH_VERSION=8.17.2
FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}

ARG ELASTICSEARCH_VERSION

RUN elasticsearch-plugin install --batch "https://get.infini.cloud/elasticsearch/analysis-ik/${ELASTICSEARCH_VERSION}"
