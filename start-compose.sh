#!/bin/bash

# Compilation maven
mvn clean install -DskipTests

# Charger .env si présent (pour variables comme DB_CONTAINER_NAME)
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Définir des variables par défaut si non définies dans .env
NETWORK_NAME=${NETWORK_NAME:-cardmanager-network}
DB_CONTAINER_NAME=${DB_CONTAINER_NAME:-cardmanager-db-test}  # Valeur par défaut si non définie

# Lancer Docker Compose
docker compose up --build -d

# Vérifier si le conteneur est déjà connecté au réseau
if docker network inspect $NETWORK_NAME | grep -q '"Name": "'$DB_CONTAINER_NAME'"'; then
  echo "Le conteneur $DB_CONTAINER_NAME est déjà connecté au réseau $NETWORK_NAME. Pas de reconnexion nécessaire."
else
  # Connecter le conteneur au réseau
  docker network connect $NETWORK_NAME $DB_CONTAINER_NAME
  echo "Conteneur $DB_CONTAINER_NAME connecté au réseau $NETWORK_NAME."
fi

echo "Setup terminé !"